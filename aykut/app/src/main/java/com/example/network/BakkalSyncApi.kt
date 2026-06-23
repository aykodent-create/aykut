package com.example.network

import com.example.data.Product
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface BakkalSyncApi {
    @GET("{blobId}")
    suspend fun getProducts(@Path("blobId") blobId: String): Response<List<Product>>

    @PUT("{blobId}")
    suspend fun updateProducts(
        @Path("blobId") blobId: String,
        @Body products: List<Product>
    ): Response<ResponseBody>

    @GET("{registryId}")
    suspend fun getRegistry(@Path("registryId") registryId: String): Response<Map<String, String>>

    @PUT("{registryId}")
    suspend fun updateRegistry(
        @Path("registryId") registryId: String,
        @Body registry: Map<String, String>
    ): Response<ResponseBody>

    @POST("https://jsonblob.com/api/jsonBlob")
    suspend fun createNewBlob(@Body products: List<Product>): Response<ResponseBody>
}

