package com.example.data

import com.example.network.BakkalSyncApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ProductRepository(private val productDao: ProductDao) {

    val allProductsFlow: Flow<List<Product>> = productDao.getAllProductsFlow()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .run {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            addInterceptor(logging)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://jsonblob.com/api/jsonBlob/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val syncApi = retrofit.create(BakkalSyncApi::class.java)

    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)
    }

    suspend fun saveProduct(product: Product) {
        productDao.insertProduct(product)
    }

    suspend fun deleteProduct(barcode: String) {
        productDao.deleteProduct(barcode)
    }

    suspend fun clearLocal() {
        productDao.clearAll()
    }

    suspend fun getAllProductsDirect(): List<Product> {
        return productDao.getAllProducts()
    }

    suspend fun insertProductsDirect(products: List<Product>) {
        productDao.insertProducts(products)
    }

    /**
     * Real-time master-to-master synchronization with jsonblob.com using registry lookups.
     * Combines products from both sources and retains the most recently updated items.
     */
    suspend fun syncWithCloud(shopCode: String): Result<Unit> {
        val cleanedShopCode = shopCode.trim().uppercase()
        if (cleanedShopCode.isBlank()) return Result.failure(Exception("Bakkal kodu boş olamaz"))

        return try {
            val localProducts = productDao.getAllProducts()

            // 1. Fetch registry directory blob
            val registryId = "019ef4ef-b4fe-7da9-aed1-fffba88df67e"
            val registryResponse = syncApi.getRegistry(registryId)
            if (!registryResponse.isSuccessful) {
                return Result.failure(Exception("Registry hatası: ${registryResponse.code()}"))
            }

            val registryMap = registryResponse.body()?.toMutableMap() ?: mutableMapOf()
            var blobId = registryMap[cleanedShopCode]

            if (blobId.isNullOrBlank()) {
                // New shop code. Create a brand new products blob on-the-fly!
                val createResponse = syncApi.createNewBlob(emptyList())
                if (!createResponse.isSuccessful) {
                    return Result.failure(Exception("Yeni bulut kaydı oluşturulamadı: ${createResponse.code()}"))
                }
                val locationHeader = createResponse.headers()["Location"]
                val parsedBlobId = locationHeader?.substringAfterLast("/") ?: ""
                if (parsedBlobId.isBlank()) {
                    return Result.failure(Exception("Bulut adres bilgisi alınamadı"))
                }
                blobId = parsedBlobId
                
                // Update registry with the new mapping
                registryMap[cleanedShopCode] = blobId
                val updateRegResponse = syncApi.updateRegistry(registryId, registryMap)
                if (!updateRegResponse.isSuccessful) {
                    return Result.failure(Exception("Mağaza tescil edilemedi: ${updateRegResponse.code()}"))
                }
            }

            // 2. Fetch products associated with blobId
            val response = syncApi.getProducts(blobId)
            val cloudProducts = if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else if (response.code() == 404) {
                // If by any chance it returned 404, fallback to empty list
                emptyList()
            } else {
                return Result.failure(Exception("Bulut sunucu hatası: ${response.code()}"))
            }

            // Sync Merge Algorithm
            val mergedMap = HashMap<String, Product>()
            
            // A. Load local items
            localProducts.forEach { mergedMap[it.barcode] = it }

            // B. Load cloud items and compare timestamps
            cloudProducts.forEach { cloudProduct ->
                val localProduct = mergedMap[cloudProduct.barcode]
                if (localProduct == null) {
                    // New item from cloud
                    mergedMap[cloudProduct.barcode] = cloudProduct
                } else {
                    // Item exists in both places, keep the newer one
                    if (cloudProduct.lastUpdated > localProduct.lastUpdated) {
                        mergedMap[cloudProduct.barcode] = cloudProduct
                    }
                }
            }

            val mergedList = mergedMap.values.toList()

            // 3. Save merged results back to local database
            productDao.clearAll()
            productDao.insertProducts(mergedList)

            // 4. Push merged results back to the associated cloud blob
            val uploadResponse = syncApi.updateProducts(blobId, mergedList)
            if (uploadResponse.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gönderme hatası: ${uploadResponse.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun pushToCloud(shopCode: String): Result<Unit> {
        val cleanedShopCode = shopCode.trim().uppercase()
        if (cleanedShopCode.isBlank()) return Result.failure(Exception("Bakkal kodu boş olamaz"))

        return try {
            val localProducts = productDao.getAllProducts()

            // 1. Fetch registry directory blob
            val registryId = "019ef4ef-b4fe-7da9-aed1-fffba88df67e"
            val registryResponse = syncApi.getRegistry(registryId)
            if (!registryResponse.isSuccessful) {
                return Result.failure(Exception("Registry hatası: ${registryResponse.code()}"))
            }

            val registryMap = registryResponse.body()?.toMutableMap() ?: mutableMapOf()
            var blobId = registryMap[cleanedShopCode]

            if (blobId.isNullOrBlank()) {
                // New shop code. Create a brand new products blob on-the-fly!
                val createResponse = syncApi.createNewBlob(emptyList())
                if (!createResponse.isSuccessful) {
                    return Result.failure(Exception("Yeni bulut kaydı oluşturulamadı: ${createResponse.code()}"))
                }
                val locationHeader = createResponse.headers()["Location"]
                val parsedBlobId = locationHeader?.substringAfterLast("/") ?: ""
                if (parsedBlobId.isBlank()) {
                    return Result.failure(Exception("Bulut adres bilgisi alınamadı"))
                }
                blobId = parsedBlobId
                
                // Update registry with the new mapping
                registryMap[cleanedShopCode] = blobId
                val updateRegResponse = syncApi.updateRegistry(registryId, registryMap)
                if (!updateRegResponse.isSuccessful) {
                    return Result.failure(Exception("Mağaza tescil edilemedi: ${updateRegResponse.code()}"))
                }
            }

            // 4. Push local results back to the associated cloud blob (Overwrite)
            val uploadResponse = syncApi.updateProducts(blobId, localProducts)
            if (uploadResponse.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gönderme hatası: ${uploadResponse.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun pullFromCloud(shopCode: String): Result<Unit> {
        val cleanedShopCode = shopCode.trim().uppercase()
        if (cleanedShopCode.isBlank()) return Result.failure(Exception("Bakkal kodu boş olamaz"))

        return try {
            // 1. Fetch registry directory blob
            val registryId = "019ef4ef-b4fe-7da9-aed1-fffba88df67e"
            val registryResponse = syncApi.getRegistry(registryId)
            if (!registryResponse.isSuccessful) {
                return Result.failure(Exception("Registry hatası: ${registryResponse.code()}"))
            }

            val registryMap = registryResponse.body()?.toMutableMap() ?: mutableMapOf()
            val blobId = registryMap[cleanedShopCode]

            if (blobId.isNullOrBlank()) {
                return Result.failure(Exception("Bu bakkal koduna ait bulut verisi bulunamadı"))
            }

            // 2. Fetch products associated with blobId
            val response = syncApi.getProducts(blobId)
            val cloudProducts = if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else if (response.code() == 404) {
                emptyList()
            } else {
                return Result.failure(Exception("Bulut sunucu hatası: ${response.code()}"))
            }

            // Overwrite local database
            productDao.clearAll()
            productDao.insertProducts(cloudProducts)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
