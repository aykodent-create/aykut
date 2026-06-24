package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Product
import com.example.data.ProductRepository
import com.example.speech.SpeechHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BakkalViewModel(
    private val repository: ProductRepository,
    application: Application
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("bakkal_prefs", Context.MODE_PRIVATE)

    var shopCode by mutableStateOf("")
        private set

    init {
        // Read saved shop code, or generate a random one if none exists
        val savedCode = sharedPrefs.getString("shop_code", "") ?: ""
        if (savedCode.isBlank()) {
            val randomCode = "BAK" + (1000..9999).random().toString()
            sharedPrefs.edit().putString("shop_code", randomCode).apply()
            shopCode = randomCode
        } else {
            shopCode = savedCode
        }
    }

    private var speechHelper: SpeechHelper? = null

    // Setup speech helper
    fun setSpeechHelper(helper: SpeechHelper) {
        speechHelper = helper
    }

    // State indicators
    var syncing by mutableStateOf(false)
        private set

    var syncSuccessMessage by mutableStateOf<String?>(null)
        private set

    var syncErrorMessage by mutableStateOf<String?>(null)
        private set

    // Active Scanning State
    var scannedBarcode by mutableStateOf<String?>(null)
        private set

    var scannedProduct by mutableStateOf<Product?>(null)
        private set

    // Basket list for continuous scan summation
    val basketItems = mutableStateListOf<Product>()

    fun addToBasket(product: Product) {
        basketItems.add(product)
    }

    fun removeFromBasket(product: Product) {
        basketItems.remove(product)
    }

    fun clearBasket() {
        basketItems.clear()
    }

    // Edit Modal State
    var currentBarcodeInEdit by mutableStateOf("")
    var currentNameInEdit by mutableStateOf("")
    var currentPriceInEdit by mutableStateOf("")
    var currentDescriptionInEdit by mutableStateOf("")

    var showEditDialog by mutableStateOf(false)

    // Search query for inventory listing
    var searchQuery by mutableStateOf("")

    // Raw flow of all items
    val rawProducts: StateFlow<List<Product>> = repository.allProductsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filter flow based on the search queries
    var filteredProducts = combine(
        repository.allProductsFlow,
        MutableStateFlow("") // we can bind VM property manually or use combined flows
    ) { list, query ->
        list
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    init {
        // No automatic sync on launch to give user complete manual control
    }

    fun updateShopCode(newCode: String) {
        val formattedCode = newCode.trim().uppercase()
        if (formattedCode.isNotBlank()) {
            sharedPrefs.edit().putString("shop_code", formattedCode).apply()
            shopCode = formattedCode
        }
    }

    private fun playBeep() {
        try {
            val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            toneG.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 150)
        } catch (e: Exception) {
            android.util.Log.e("BakkalViewModel", "Error playing beep", e)
        }
    }

    fun onBarcodeScanned(barcode: String) {
        val cleanedBarcode = barcode.trim()
        if (cleanedBarcode.isBlank()) return

        playBeep()
        scannedBarcode = cleanedBarcode
        
        viewModelScope.launch {
            val product = repository.getProductByBarcode(cleanedBarcode)
            if (product != null) {
                // Product exists! Speak name and price
                scannedProduct = product
                val message = if (product.name.isNotBlank()) {
                    "Dıt! ${product.name}, ${formatPriceForSpeech(product.price)}"
                } else {
                    "Dıt! Ürün bulundu, fiyati ${formatPriceForSpeech(product.price)}"
                }
                speechHelper?.speak(message)
                addToBasket(product)
            } else {
                // New product! Speak "New product price required"
                scannedProduct = null
                speechHelper?.speak("Dıt! Yeni ürün, fiyati giriniz")
                
                // Prefill details and show edit sheet
                startNewProductCreation(cleanedBarcode)
            }
        }
    }

    fun speakProductPrice(product: Product) {
        playBeep()
        val text = "Dıt! ${product.name}, fiyati ${formatPriceForSpeech(product.price)}"
        speechHelper?.speak(text)
    }

    private fun formatPriceForSpeech(price: Double): String {
        // Formats price into spoken words (e.g., "ve elli kurus")
        val liras = price.toInt()
        val kurus = ((price - liras) * 100).toInt()
        
        return when {
            liras > 0 && kurus > 0 -> "$liras lira, $kurus kurus"
            liras > 0 -> "$liras lira"
            kurus > 0 -> "$kurus kurus"
            else -> "0 lira"
        }
    }

    fun startNewProductCreation(barcode: String) {
        currentBarcodeInEdit = barcode
        currentNameInEdit = ""
        currentPriceInEdit = ""
        currentDescriptionInEdit = ""
        showEditDialog = true
    }

    fun startProductEditing(product: Product) {
        currentBarcodeInEdit = product.barcode
        currentNameInEdit = product.name
        currentPriceInEdit = if (product.price == 0.0) "" else product.price.toString()
        currentDescriptionInEdit = product.description
        showEditDialog = true
    }

    fun cancelScanAndEdit() {
        scannedBarcode = null
        scannedProduct = null
        showEditDialog = false
    }

    fun saveEditingProduct() {
        if (currentBarcodeInEdit.isBlank()) return
        
        val price = currentPriceInEdit.toDoubleOrNull() ?: 0.0
        val product = Product(
            barcode = currentBarcodeInEdit.trim(),
            name = currentNameInEdit.trim(),
            price = price,
            description = currentDescriptionInEdit.trim(),
            lastUpdated = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.saveProduct(product)
            showEditDialog = false
            scannedBarcode = null
            scannedProduct = null
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product.barcode)
        }
    }

    fun pushToCloud() {
        if (syncing) return
        syncing = true
        syncSuccessMessage = null
        syncErrorMessage = null

        viewModelScope.launch {
            val result = repository.pushToCloud(shopCode)
            syncing = false
            result.fold(
                onSuccess = {
                    syncSuccessMessage = "Veriler Buluta Gönderildi"
                },
                onFailure = { error ->
                    syncErrorMessage = "Hata: ${error.localizedMessage ?: "Bağlantı sorunu"}"
                }
            )
        }
    }

    fun pullFromCloud() {
        if (syncing) return
        syncing = true
        syncSuccessMessage = null
        syncErrorMessage = null

        viewModelScope.launch {
            val result = repository.pullFromCloud(shopCode)
            syncing = false
            result.fold(
                onSuccess = {
                    syncSuccessMessage = "Veriler Buluttan Güncellendi"
                },
                onFailure = { error ->
                    syncErrorMessage = "Hata: ${error.localizedMessage ?: "Bağlantı sorunu"}"
                }
            )
        }
    }

    fun syncWithCloud() {
        if (syncing) return
        syncing = true
        syncSuccessMessage = null
        syncErrorMessage = null

        viewModelScope.launch {
            val result = repository.syncWithCloud(shopCode)
            syncing = false
            result.fold(
                onSuccess = {
                    syncSuccessMessage = "Senkronizasyon Başarılı"
                },
                onFailure = { error ->
                    syncErrorMessage = "Hata: ${error.localizedMessage ?: "Bağlantı sorunu"}"
                }
            )
        }
    }

    fun dismissMessages() {
        syncSuccessMessage = null
        syncErrorMessage = null
    }

    fun clearAllInventory() {
        viewModelScope.launch {
            repository.clearLocal()
        }
    }

    // JSON Backup / Restore Serialization
    private val moshiSerializer = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, Product::class.java)
    private val jsonAdapter = moshiSerializer.adapter<List<Product>>(listType)

    private fun exportToJsonString(products: List<Product>): String {
        return jsonAdapter.toJson(products)
    }

    private fun importFromJsonString(json: String): List<Product>? {
        return try {
            jsonAdapter.fromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Backs up the product database directly to Environment.DIRECTORY_DOWNLOADS with a date and time stamp
     */
    fun backupToDownloadsDirect(context: Context): Result<String> {
        return try {
            val localProducts = rawProducts.value
            val json = exportToJsonString(localProducts)
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val sdf = java.text.SimpleDateFormat("dd_MM_yyyy_HHmmss", java.util.Locale.getDefault())
            val timestamp = sdf.format(java.util.Date())
            val fileName = "aykut_$timestamp.txt"
            val file = File(downloadDir, fileName)
            file.writeText(json)
            Result.success(file.name)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Restores products directly from the latest aykut_*.txt or aykut.txt file in Environment.DIRECTORY_DOWNLOADS
     */
    fun restoreFromDownloadsDirect(context: Context): Result<Int> {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                return Result.failure(Exception("İndirilenler klasörü bulunamadı."))
            }
            val files = downloadDir.listFiles { file ->
                file.isFile && (file.name == "aykut.txt" || (file.name.startsWith("aykut_") && file.name.endsWith(".txt")))
            }
            if (files.isNullOrEmpty()) {
                return Result.failure(Exception("İndirilenler klasöründe aykut yedek dosyası bulunamadı."))
            }
            // Find the latest modified file
            val latestFile = files.maxByOrNull { it.lastModified() }
                ?: return Result.failure(Exception("Yedek dosyası seçilemedi."))
            
            val json = latestFile.readText()
            val products = importFromJsonString(json) ?: return Result.failure(Exception("Dosya formatı geçersiz (${latestFile.name})."))
            
            viewModelScope.launch {
                repository.insertProductsDirect(products)
            }
            Result.success(products.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Backs up current products to a custom output stream (e.g., from CreateDocument activity launcher)
     */
    fun backupToStream(outputStream: OutputStream): Result<Unit> {
        return try {
            val localProducts = rawProducts.value
            val json = exportToJsonString(localProducts)
            outputStream.use { out ->
                out.write(json.toByteArray())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Restores products from a custom input stream (e.g., from OpenDocument activity launcher)
     */
    fun restoreFromStream(inputStream: java.io.InputStream): Result<Int> {
        return try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val products = importFromJsonString(json) ?: return Result.failure(Exception("Dosya formatı geçersiz."))
            
            viewModelScope.launch {
                repository.insertProductsDirect(products)
            }
            Result.success(products.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

class BakkalViewModelFactory(
    private val repository: ProductRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BakkalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BakkalViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
