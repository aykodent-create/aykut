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
    
    var activeTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Inventory List
    
    // SnackBar Host
    val snackbarHostState = remember { SnackbarHostState() }

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
                0 -> DashboardTab(viewModel = viewModel, onScanClick = {
                    val scanner = GmsBarcodeScanning.getClient(context)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            barcode.rawValue?.let {
                                viewModel.onBarcodeScanned(it)
                            }
                        }
                        .addOnFailureListener { e ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Tarama başarısız: ${e.localizedMessage}")
                            }
                        }
                })
                1 -> InventoryListTab(viewModel = viewModel)
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
            onNameChange = { viewModel.currentNameInEdit = it },
            onPriceChange = { viewModel.currentPriceInEdit = it },
            onDescriptionChange = { viewModel.currentDescriptionInEdit = it },
            onDismiss = { viewModel.cancelScanAndEdit() },
            onSave = { viewModel.saveEditingProduct() }
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
        // Hero Image Header representing the Bakkal Theme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_bakkal_hero_1782222675185),
                contentDescription = "Bakkal Hero Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Linear Gradient overlay for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Aykut Hızlı Barkod",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Pratik fiyat seslendirici ve eş zamanlı bakkal sistemi",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.85f)
                    )
                )
            }
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

            // Cloud synchronization Actions Card
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bulut Senkronizasyonu",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kayıtlı ürün sayısı: ${products.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.syncWithCloud() },
                        enabled = !viewModel.syncing,
                        modifier = Modifier.testTag("sync_now_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (viewModel.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Eşitle",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eşitle")
                        }
                    }
                }
            }

            // Quick How To Guidelines Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Nasıl Kullanılır?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val steps = listOf(
                        "1. Kameradan Tarat" to "Yukarıdaki büyük butona tıklayıp kameranızı ürünün barkoduna tutun.",
                        "2. Fiyat Belirle" to "Ürün kayıtlı değilse fiyatını ve adını yazıp kaydedin.",
                        "3. Sesli Geri Bildirim" to "Her taratışta sistem ürünün fiyatını otomatik olarak Türkçe seslendirecektir.",
                        "4. Diğer Telefonlar" to "Aynı bakkal kodunu diğer telefonlara yazarak eş zamanlı veri paylaşın."
                    )

                    steps.forEach { step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.CenterVertically),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = step.first,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                // Barcode Reader Display Field
                OutlinedTextField(
                    value = barcode,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barkod No") },
                    readOnly = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) }
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
