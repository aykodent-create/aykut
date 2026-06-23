package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Product
import com.example.data.ProductRepository
import com.example.speech.SpeechHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        // Automatically sync on launch
        viewModelScope.launch {
            syncWithCloud()
        }
    }

    fun updateShopCode(newCode: String) {
        val formattedCode = newCode.trim().uppercase()
        if (formattedCode.isNotBlank()) {
            sharedPrefs.edit().putString("shop_code", formattedCode).apply()
            shopCode = formattedCode
            // Immediately sync with the newly joined shop
            viewModelScope.launch {
                syncWithCloud()
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        val cleanedBarcode = barcode.trim()
        if (cleanedBarcode.isBlank()) return

        scannedBarcode = cleanedBarcode
        
        viewModelScope.launch {
            val product = repository.getProductByBarcode(cleanedBarcode)
            if (product != null) {
                // Product exists! Speak name and price
                scannedProduct = product
                val message = if (product.name.isNotBlank()) {
                    "${product.name}, fiyati ${formatPriceForSpeech(product.price)}"
                } else {
                    "Urun bulundu, fiyati ${formatPriceForSpeech(product.price)}"
                }
                speechHelper?.speak(message)
            } else {
                // New product! Speak "New product price required"
                scannedProduct = null
                speechHelper?.speak("Yeni urun, fiyati giriniz")
                
                // Prefill details and show edit sheet
                startNewProductCreation(cleanedBarcode)
            }
        }
    }

    fun speakProductPrice(product: Product) {
        val text = "${product.name}, fiyati ${formatPriceForSpeech(product.price)}"
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
            
            // Auto - Sync immediately
            syncWithCloud()
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product.barcode)
            // Auto - Sync immediately
            syncWithCloud()
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
            syncWithCloud()
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
