package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Product
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BakkalApp(viewModel: BakkalViewModel) {
    var isUnlocked by rememberSaveable { mutableStateOf(false) }

    if (!isUnlocked) {
        PasscodeScreen(
            onCorrectPasscode = { isUnlocked = true }
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by viewModel.rawProducts.collectAsState()
    
    var activeTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Inventory List, 2: Profil
    var showCameraScanner by remember { mutableStateOf(false) }
    var showCameraForEditDialog by remember { mutableStateOf(false) }

    // SnackBar Host
    val snackbarHostState = remember { SnackbarHostState() }

    // Create Document Launcher for backup export
    val createDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = viewModel.backupToStream(outputStream)
                    scope.launch {
                        result.fold(
                            onSuccess = {
                                snackbarHostState.showSnackbar("Veriler başarıyla yedeklendi.")
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar("Yedekleme hatası: ${error.localizedMessage}")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Dosya yazılamadı: ${e.localizedMessage}")
                }
            }
        }
    }

    // Open Document Launcher for restore import
    val openDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val result = viewModel.restoreFromStream(inputStream)
                    scope.launch {
                        result.fold(
                            onSuccess = { count ->
                                snackbarHostState.showSnackbar("Yedek başarıyla yüklendi: $count ürün geri yüklendi.")
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar("Yükleme hatası: ${error.localizedMessage}")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Dosya okunamadı: ${e.localizedMessage}")
                }
            }
        }
    }

    // Synchronize snackbar triggers on messages
    LaunchedEffect(viewModel.syncSuccessMessage) {
        viewModel.syncSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }
    LaunchedEffect(viewModel.syncErrorMessage) {
        viewModel.syncErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    if (showCameraScanner) {
        CameraBarcodeScanner(
            onBarcodeScanned = { barcode ->
                viewModel.onBarcodeScanned(barcode)
            },
            onDismiss = { showCameraScanner = false },
            products = products,
            scannedProduct = viewModel.scannedProduct,
            scannedBarcode = viewModel.scannedBarcode,
            basketItems = viewModel.basketItems,
            onRemoveFromBasket = { viewModel.removeFromBasket(it) },
            onClearBasket = { viewModel.clearBasket() }
        )
        return
    }

    if (showCameraForEditDialog) {
        CameraBarcodeScanner(
            onBarcodeScanned = { barcode ->
                viewModel.onBarcodeChangeInEdit(barcode)
            },
            onDismiss = { showCameraForEditDialog = false },
            products = products,
            scannedProduct = null,
            scannedBarcode = null,
            basketItems = emptyList(),
            onRemoveFromBasket = {},
            onClearBasket = {},
            isRegistrationMode = true
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Bakkalim") },
                    label = { Text("Bakkalım") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Katalog") },
                    label = { Text("Ürün Kataloğu") }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                    label = { Text("Profil") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> DashboardTab(
                    viewModel = viewModel,
                    onScanClick = { showCameraScanner = true }
                )
                1 -> InventoryListTab(viewModel = viewModel)
                2 -> ProfileTab(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onBackupClick = {
                        val timestamp = java.text.SimpleDateFormat("dd_MM_yyyy_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        createDocLauncher.launch("aykut_$timestamp.txt")
                    },
                    onRestoreClick = { openDocLauncher.launch(arrayOf("text/plain")) }
                )
            }
        }
    }

    // Product Add/Edit Dialog modal
    if (viewModel.showEditDialog) {
        ProductEditDialog(
            barcode = viewModel.currentBarcodeInEdit,
            name = viewModel.currentNameInEdit,
            price = viewModel.currentPriceInEdit,
            description = viewModel.currentDescriptionInEdit,
            onBarcodeChange = { viewModel.onBarcodeChangeInEdit(it) },
            onScanClick = { showCameraForEditDialog = true },
            onNameChange = { viewModel.currentNameInEdit = it },
            onPriceChange = { viewModel.currentPriceInEdit = it },
            onDescriptionChange = { viewModel.currentDescriptionInEdit = it },
            onDismiss = { viewModel.cancelScanAndEdit() },
            onSave = { viewModel.saveEditingProduct() }
        )
    }

    // Duplicate barcode detection / update confirmation dialog
    if (viewModel.showAlreadyExistsPrompt && viewModel.existingProductToLoad != null) {
        val existing = viewModel.existingProductToLoad!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlreadyExistsPrompt() },
            title = {
                Text(
                    text = "Ürün Zaten Kayıtlı",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "\"${existing.name}\" ürünü zaten bu barkodla (${existing.barcode}) kayıtlıdır.\n\nMevcut Fiyatı: ${existing.price} TL\n\nBu ürünün mevcut bilgilerini yükleyip fiyatını güncellemek ister misiniz?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.loadExistingProductInfo() }
                ) {
                    Text("Evet, Güncelle")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAlreadyExistsPrompt() }
                ) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun DashboardTab(
    viewModel: BakkalViewModel,
    onScanClick: () -> Unit
) {
    var isEditingShopCode by remember { mutableStateOf(false) }
    var isShopCodeVisible by remember { mutableStateOf(false) }
    var shopCodeInput by remember { mutableStateOf(viewModel.shopCode) }
    val products by viewModel.rawProducts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Clean, simple header instead of image banner (Sade tasarım)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Aykut Hızlı Barkod",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pratik fiyat seslendirici ve eş zamanlı bakkal sistemi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Synchronized Shop Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bakkal Mağaza Kodu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Diğer cihazlara bu kodu girerek senkronize kalın",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isEditingShopCode) {
                                    viewModel.updateShopCode(shopCodeInput)
                                } else {
                                    shopCodeInput = viewModel.shopCode
                                }
                                isEditingShopCode = !isEditingShopCode
                            },
                            modifier = Modifier.testTag("edit_shop_code_button")
                        ) {
                            Icon(
                                imageVector = if (isEditingShopCode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Kodu Değiştir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isEditingShopCode) {
                        OutlinedTextField(
                            value = shopCodeInput,
                            onValueChange = { shopCodeInput = it.uppercase() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shop_code_input"),
                            label = { Text("Yeni Kod Girişi") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Sync",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isShopCodeVisible) viewModel.shopCode else viewModel.shopCode.map { '•' }.joinToString(""),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            IconButton(
                                onClick = { isShopCodeVisible = !isShopCodeVisible },
                                modifier = Modifier.testTag("toggle_shop_code_visibility")
                            ) {
                                Icon(
                                    imageVector = if (isShopCodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Kodu Göster/Gizle",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Big Trigger Scan Button
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("scan_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut, // Placeholder visual for barcode scanner or vertical laser
                        contentDescription = "Scan",
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Barkod Okut (Kamera)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ürünün fiyatını öğrenmek veya girmek için dokun",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Manual Barcode Entry/Simulation Field
            var manualBarcode by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_barcode_input"),
                    placeholder = { Text("Barkod no el ile gir...") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        if (manualBarcode.isNotBlank()) {
                            viewModel.onBarcodeScanned(manualBarcode)
                            manualBarcode = ""
                        }
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("manual_scan_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sorgula")
                }
            }

            // Active Scan Result Card (Last Scanned Product)
            val scannedProduct = viewModel.scannedProduct
            val scannedBarcode = viewModel.scannedBarcode

            if (scannedBarcode != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanned_product_card")
                        .animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Okundu",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Son Okutulan Ürün",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            IconButton(
                                onClick = { viewModel.cancelScanAndEdit() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (scannedProduct != null) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = scannedProduct.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = scannedProduct.barcode,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                if (scannedProduct.description.isNotBlank()) {
                                    Text(
                                        text = scannedProduct.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%.2f TL", scannedProduct.price),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Re-speak button
                                    Button(
                                        onClick = { viewModel.speakProductPrice(scannedProduct) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Tekrar Söyle"
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fiyat Söyle")
                                    }

                                    // Quick edit button
                                    FilledIconButton(
                                        onClick = { viewModel.startProductEditing(scannedProduct) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                                    }
                                }
                            }
                        } else {
                            // Scanned but not found
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Barkod: $scannedBarcode",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Bu ürün henüz kayıtlı değil!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(
                                    onClick = { viewModel.startNewProductCreation(scannedBarcode) }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ürünü Kaydet")
                                }
                            }
                        }
                    }
                }
            }

            // Cloud synchronization Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bulut Senkronizasyonu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Kayıtlı Ürün Sayısı: ${products.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (viewModel.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Senkronizasyona Gönder (Upload)
                        Button(
                            onClick = { viewModel.pushToCloud() },
                            enabled = !viewModel.syncing,
                            modifier = Modifier.weight(1f).testTag("push_to_cloud_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Senkronizasyona Gönder", fontSize = 11.sp, maxLines = 1)
                        }

                        // Senkronizasyondan Güncelle (Download)
                        Button(
                            onClick = { viewModel.pullFromCloud() },
                            enabled = !viewModel.syncing,
                            modifier = Modifier.weight(1f).testTag("pull_from_cloud_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Senkronizasyondan Güncelle", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryListTab(viewModel: BakkalViewModel) {
    val products by viewModel.rawProducts.collectAsState()
    
    // Client-side dynamic search filter
    val filteredList = remember(viewModel.searchQuery, products) {
        if (viewModel.searchQuery.isBlank()) {
            products
        } else {
            val q = viewModel.searchQuery.trim().lowercase()
            products.filter {
                it.name.lowercase().contains(q) || it.barcode.contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input"),
                placeholder = { Text("Ürün adı veya barkod ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Temizle")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Add Product Manually button
            IconButton(
                onClick = { viewModel.startNewProductCreation("") },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .testTag("add_manual_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Manuel Ekle",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (filteredList.isEmpty()) {
            // Empty state placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = "Bos",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = if (viewModel.searchQuery.isNotBlank()) "Arama sonucu bulunamadı" else "Kayıtlı ürün bulunmuyor",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (viewModel.searchQuery.isNotBlank()) "Farklı bir arama kelimesi yazmayı deneyin." else "Bakkal ürünlerinizi girmek için turlayın veya manuel ekleme yapın.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Item list scrollable view
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("products_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.barcode }) { product ->
                    ProductItemRow(
                        product = product,
                        onSpeakerClick = { viewModel.speakProductPrice(product) },
                        onEditClick = { viewModel.startProductEditing(product) },
                        onDeleteClick = { viewModel.deleteProduct(product) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductItemRow(
    product: Product,
    onSpeakerClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_row_${product.barcode}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (product.name.isNotBlank()) product.name else "Tanımsız Ürün",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Barkod",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = product.barcode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (product.description.isNotBlank()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Price column & Trigger Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Highly Visible Price Element
                Text(
                    text = String.format("%.2f TL", product.price),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Voice Pronouncer Button
                IconButton(
                    onClick = onSpeakerClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Fiyat Söyle",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Edit Button
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Duzenle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete Button
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    barcode: String,
    name: String,
    price: String,
    description: String,
    onBarcodeChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ürün Fiyat Girişi",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Barcode Reader Display & Manual Entry Field
                OutlinedTextField(
                    value = barcode,
                    onValueChange = onBarcodeChange,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_barcode_input"),
                    label = { Text("Barkod No") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onScanClick) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Kamerayla Tara",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true
                )

                // Name TextField
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_name_input"),
                    label = { Text("Ürün Adı") },
                    placeholder = { Text("Örn: Domates 1 kg") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.ShoppingBasket, contentDescription = null) }
                )

                // Price TextField (With Numeric Keyboard Mode)
                OutlinedTextField(
                    value = price,
                    onValueChange = onPriceChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_price_input"),
                    label = { Text("Fiyat (TL)") },
                    placeholder = { Text("Örn: 24.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                )

                // Description TextField
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Not / Açıklama (İsteğe Bağlı)") },
                    placeholder = { Text("Örn: Manav reyonu taze") },
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = price.toDoubleOrNull() != null,
                modifier = Modifier.testTag("dialog_save_button")
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
fun PasscodeScreen(
    onCorrectPasscode: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C1B1B), // Warm dark ambient
                        Color(0xFF140F0F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Elegant Lock Ring Visual
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFB13B).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kilit",
                    tint = Color(0xFFFFB13B),
                    modifier = Modifier.size(45.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header text matching Bakkal theme
            Text(
                text = "Bakkal Kontrol Paneli",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            
            Text(
                text = "Yetkisiz erişimi engellemek için şifre giriniz",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.6f)
                ),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(36.dp))
            
            // Error Warning with soft styling
            Text(
                text = if (errorMessage.isNotEmpty()) errorMessage else "Uygulama Giriş Şifresi",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (errorMessage.isNotEmpty()) Color(0xFFF38BA8) else Color.White.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Four-Dot Passcode Dots Indicator representation
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (isFilled) Color(0xFFFFB13B) else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isFilled) Color(0xFFFFB13B) else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Responsive standalone input layout
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Temizle", "0", "Sil")
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        for (key in row) {
                            KeyButton(
                                text = key,
                                onClick = {
                                    errorMessage = ""
                                    when (key) {
                                        "Temizle" -> {
                                            pin = ""
                                        }
                                        "Sil" -> {
                                            if (pin.isNotEmpty()) {
                                                pin = pin.substring(0, pin.length - 1)
                                            }
                                        }
                                        else -> {
                                            if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) {
                                                    if (pin == "1986") {
                                                        onCorrectPasscode()
                                                    } else {
                                                        errorMessage = "Hatalı Şifre! Tekrar giriniz."
                                                        pin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit
) {
    val isAction = text == "Temizle" || text == "Sil"
    
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(
                if (isAction) Color.White.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isAction) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (text == "Sil") {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Bir karakter sil",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (text == "Temizle") Color(0xFFF38BA8) else Color.White
                )
            )
        }
    }
}

@Composable
fun ProfileTab(
    viewModel: BakkalViewModel,
    snackbarHostState: SnackbarHostState,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Info Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column {
                    Text(
                        text = "Bakkal Yöneticisi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mağaza Kodu: ${viewModel.shopCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sesli Okuma Ayarları Section
        Text(
            text = "Sesli Okuma Ayarları",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Toggle 1: Sesli Fiyat Okuma
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Sesli Fiyat Okuma",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Barkod okununca fiyatı sesli oku",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = viewModel.speakProductEnabled,
                        onCheckedChange = { viewModel.updateSpeakProductEnabled(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Toggle 2: Ürün İsmini Oku
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = "Ürün İsmini Oku",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fiyatın yanında ürün adını da seslendir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = viewModel.speakProductName,
                        onCheckedChange = { viewModel.updateSpeakProductName(it) },
                        enabled = viewModel.speakProductEnabled
                    )
                }
            }
        }

        // Bulut Senkronizasyonu Section
        Text(
            text = "Bulut Senkronizasyonu",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bulut senkronizasyonu manuel olarak yönetilir. Sildiğiniz veya eklediğiniz ürünleri bulut ile eşitlemek için aşağıdaki seçenekleri kullanın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Senkronizasyona Gönder (Upload)
                    Button(
                        onClick = { viewModel.pushToCloud() },
                        enabled = !viewModel.syncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (viewModel.syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buluta Gönder", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    // Senkronizasyondan Güncelle (Download)
                    Button(
                        onClick = { viewModel.pullFromCloud() },
                        enabled = !viewModel.syncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (viewModel.syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buluttan Güncelle", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Backup and Restore Section
        Text(
            text = "Yedekleme ve Geri Yükleme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Method 1: Save to local Downloads folder (Direct as aykut_tarih_saat.txt)
                Text(
                    text = "Yöntem 1: Doğrudan Telefona (aykut_tarih_saat.txt)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val result = viewModel.backupToDownloadsDirect(context)
                            scope.launch {
                                result.fold(
                                    onSuccess = { fileName ->
                                        snackbarHostState.showSnackbar("İndirilenler klasörüne $fileName olarak yedeklendi.")
                                    },
                                    onFailure = { error ->
                                        snackbarHostState.showSnackbar("Yedekleme hatası: ${error.localizedMessage}")
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yedekle")
                    }

                    Button(
                        onClick = {
                            val result = viewModel.restoreFromDownloadsDirect(context)
                            scope.launch {
                                result.fold(
                                    onSuccess = { count ->
                                        snackbarHostState.showSnackbar("Yedek yüklendi: $count ürün geri yüklendi.")
                                    },
                                    onFailure = { error ->
                                        snackbarHostState.showSnackbar(error.localizedMessage ?: "Yükleme hatası.")
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yükle")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Method 2: Save to custom location using File Picker
                Text(
                    text = "Yöntem 2: Farklı Kaydet / Dosya Seç...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackupClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yedekle...")
                    }

                    OutlinedButton(
                        onClick = onRestoreClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dosya Seç...")
                    }
                }
            }
        }

        // Danger/Reset Section
        Text(
            text = "Tehlikeli Bölge",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tüm Ürün Kataloğunu Sil",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Bu işlem cihazınızdaki ve buluttaki tüm ürünlerinizi kalıcı olarak silecektir. Lütfen yedek aldığınızdan emin olun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tümünü Sıfırla")
                }
            }
        }

        // About / Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aykut Hızlı Barkod v1.2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Tüm Kataloğu Sil?") },
            text = { Text("Tüm verileriniz silinecektir. Bu işlem geri alınamaz!") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.clearAllInventory()
                        showResetConfirm = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Tüm katalog başarıyla sıfırlandı.")
                        }
                    }
                ) {
                    Text("Kataloğu Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
