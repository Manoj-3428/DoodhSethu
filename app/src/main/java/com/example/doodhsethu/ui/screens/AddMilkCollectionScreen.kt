package com.example.doodhsethu.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.doodhsethu.R
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.models.NavigationItem
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.theme.PrimaryBlue
import com.example.doodhsethu.ui.theme.SecondaryBlue
import com.example.doodhsethu.ui.theme.White
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.doodhsethu.ui.viewmodels.FatTableViewModel
import com.example.doodhsethu.utils.GlobalNetworkManager
import com.example.doodhsethu.components.NetworkStatusIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddMilkCollectionScreen(
    allFarmers: List<Farmer>,
    onAddFarmer: () -> Unit,
    onSubmitSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    prefillFarmerId: String? = null,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToAddFarmer: () -> Unit = {},
    onNavigateToFatTable: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToUserReports: () -> Unit = {},
    onNavigateToBillingCycles: () -> Unit = {},

    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    val scope = rememberCoroutineScope()
    val dailyMilkCollectionRepository = remember { DailyMilkCollectionRepository(context) }
    val fatTableViewModel: FatTableViewModel = viewModel(
        factory = com.example.doodhsethu.ui.viewmodels.FatTableViewModelFactory(context)
    )
    val fatTableRows by fatTableViewModel.fatTableRows.collectAsState()
    val isFatTableLoading by fatTableViewModel.isLoading.collectAsState()

    // Initialize fat table data only once
    LaunchedEffect(Unit) {
        if (fatTableRows.isEmpty() && !isFatTableLoading) {
            fatTableViewModel.initializeData(isOnline)
        }
    }

    var customerId by remember { mutableStateOf("") }
    var selectedFarmer: Farmer? by remember { mutableStateOf(null) }
    var fat by remember { mutableStateOf("") }
    var milk by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isDrawerOpen by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedNavigationItem by remember { mutableStateOf<NavigationItem>(NavigationItem.MilkCollection) }
    
    // Focus management for ID field
    val idFieldFocusRequester = remember { FocusRequester() }
    

    // Filter farmers by simple numeric ID as user types
    val filteredFarmers = remember(customerId, allFarmers) {
        if (customerId.isBlank()) emptyList() else allFarmers.filter {
            it.id == customerId.trim() || it.id.startsWith(customerId.trim())
        }
    }
    val farmerExists = allFarmers.any { it.id == customerId.trim() }

    // Navigation handler
    val handleNavigation = { item: NavigationItem ->
        selectedNavigationItem = item
        // Keep drawer open; only the hamburger toggles it
        when (item) {
            NavigationItem.MilkCollection -> { /* Already on this screen as Home */
            }

            NavigationItem.Home -> onNavigateToDashboard()
            NavigationItem.FatTable -> onNavigateToFatTable()
            NavigationItem.Profile -> onNavigateToProfile()
            NavigationItem.Reports -> onNavigateToReports()
            NavigationItem.UserReports -> onNavigateToUserReports()
            NavigationItem.BillingCycles -> onNavigateToBillingCycles()
            NavigationItem.AddFarmer -> TODO()
        }
    }

    // Pre-fill logic
    LaunchedEffect(prefillFarmerId, allFarmers) {
        if (prefillFarmerId != null && customerId.isBlank()) {
            val farmer = allFarmers.find { it.id == prefillFarmerId }
            if (farmer != null) {
                customerId = farmer.id
                selectedFarmer = farmer
            }
        }
    }
    
    // Auto-focus on ID field when screen loads
    LaunchedEffect(Unit) {
        delay(300) // Wait for screen to fully load
        idFieldFocusRequester.requestFocus()
    }

    // Validation
    val fatValue = fat.toDoubleOrNull()
    val milkValue = milk.toDoubleOrNull()
    val isFatValid = fatValue != null && fatValue in 1.0..10.0
    val isMilkValid = milkValue != null && milkValue in 0.1..30.0
    val isFormValid =
        selectedFarmer != null && isFatValid && isMilkValid && farmerExists && !isLoading
    // Get price from fat table using utility function
    val pricePerLiter = if (fatValue != null && fatTableRows.isNotEmpty()) {
        com.example.doodhsethu.utils.FatTableUtils.getPriceForFat(fatValue, fatTableRows)
    } else 0.0
    val totalPrice =
        if (isFatValid && isMilkValid && fatTableRows.isNotEmpty()) (milkValue!! * pricePerLiter) else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home Milk Collection",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = PrimaryBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { isDrawerOpen = !isDrawerOpen }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu),
                            contentDescription = "Menu",
                            tint = PrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Side Navigation Drawer
            AnimatedVisibility(
                visible = isDrawerOpen,
                enter = slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { -it }
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { -it }
                ) + fadeOut(animationSpec = tween(300))
            ) {
                SideNavigationDrawer(
                    selectedItem = selectedNavigationItem,
                    onItemClick = handleNavigation,
                    isOnline = isOnline
                )
            }

            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
            ) {
                // Network Status Indicator moved to navigation menu

                // Live Date and Session Display
                LiveDateSessionDisplay(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 24.dp)
                )

                // Main Card with minimal spacing from date/session display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(top = 80.dp), // Moved much higher - closer to top app bar
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Customer ID (Farmer ID) Field with Add button beside
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customerId,
                                onValueChange = {
                                    customerId = it.filter { ch -> ch.isDigit() }
                                    showDropdown = it.isNotBlank()
                                    selectedFarmer = allFarmers.find { f -> f.id == it.trim() }
                                    errorText = null
                                },
                                label = {
                                    Text(
                                        "Customer ID (Farmer ID)",
                                        fontFamily = PoppinsFont
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(idFieldFocusRequester),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = SecondaryBlue
                                ),
                                isError = customerId.isNotBlank() && !farmerExists,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                onClick = onAddFarmer,
                                shape = CircleShape,
                                color = PrimaryBlue,
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_add),
                                        contentDescription = "Add Farmer",
                                        tint = White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        if (customerId.isNotBlank() && !farmerExists) {
                            Text(
                                text = "No farmer found with this ID.",
                                color = Color.Red,
                                fontFamily = PoppinsFont,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Start)
                                    .padding(top = 2.dp, bottom = 2.dp)
                            )
                        }
                        // Dropdown for farmer search
                        if (showDropdown && filteredFarmers.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = White)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp)
                                ) {
                                    items(filteredFarmers, key = { it.id }) { farmer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItemPlacement()
                                                .clickable {
                                                    selectedFarmer = farmer
                                                    customerId = farmer.id
                                                    showDropdown = false
                                                    errorText = null
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Profile pic
                                            if (farmer.photoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = farmer.photoUrl,
                                                    contentDescription = "Profile Pic",
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(
                                                            PrimaryBlue.copy(alpha = 0.1f),
                                                            CircleShape
                                                        ),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_person),
                                                    contentDescription = "Profile Pic",
                                                    tint = PrimaryBlue,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(
                                                            PrimaryBlue.copy(alpha = 0.1f),
                                                            CircleShape
                                                        )
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = farmer.name,
                                                    fontFamily = PoppinsFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = farmer.phone,
                                                    fontFamily = PoppinsFont,
                                                    fontSize = 13.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Only show selected farmer display when dropdown is closed
                        if (!showDropdown) {
                            selectedFarmer?.let { farmer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_person),
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${farmer.name} (${farmer.id})",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = PrimaryBlue
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = fat,
                            onValueChange = {
                                fat = it.filter { ch -> ch.isDigit() || ch == '.' }
                            },
                            label = { Text("Fat", fontFamily = PoppinsFont) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_fat_machine),
                                    contentDescription = "Fat Testing Machine",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            supportingText = {
                                if (!isFatValid && fat.isNotBlank()) {
                                    Text(
                                        "Enter a value between 1 and 10",
                                        color = Color.Red,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedTextField(
                            value = milk,
                            onValueChange = {
                                milk = it.filter { ch -> ch.isDigit() || ch == '.' }
                            },
                            label = { Text("Milk (liters)", fontFamily = PoppinsFont) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_local_drink),
                                    contentDescription = "Milk Container",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            supportingText = {
                                val warn20 =
                                    milkValue != null && milkValue > 20.0 && milkValue <= 30.0
                                val err30 = !isMilkValid && milk.isNotBlank()
                                if (err30) {
                                    Text(
                                        "Enter a value between 0.1 and 30",
                                        color = Color.Red,
                                        fontSize = 12.sp
                                    )
                                } else if (warn20) {
                                    Text(
                                        "Warning: Milk is more than 20 liters",
                                        color = Color(0xFFFFC107),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        // Calculated Price
                        if (isFormValid) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Total Price: ₹${String.format("%.2f", totalPrice)}",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    customerId = ""
                                    selectedFarmer = null
                                    fat = ""
                                    milk = ""
                                    errorText = null
                                    // Request focus on ID field after clearing
                                    scope.launch {
                                        delay(100) // Small delay to ensure UI updates first
                                        idFieldFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "Clear",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    // Show extra warning if milk > 20L
                                    val warn = milkValue != null && milkValue > 20.0
                                    if (warn) {
                                        // Reuse confirm dialog but with warning copy
                                        showConfirmDialog = true
                                    } else {
                                        showConfirmDialog = true
                                    }
                                },
                                enabled = isFormValid && !isLoading,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                     Text(
                                         "Submit",
                                         fontFamily = PoppinsFont,
                                         fontWeight = FontWeight.Bold,
                                         color = White,
                                         fontSize = 12.sp
                                     )
                                }
                            }
                        }
                        // Confirmation Dialog with Print Option
                        if (showConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showConfirmDialog = false },
                                title = {
                                    Text(
                                        "Confirm Submission",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column {
                                        // 3-line structured display
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Line 1: Date
                                            Text(
                                                "${java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(Date())} ${if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"}",
                                                fontFamily = PoppinsFont,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            // Line 2: ID and Name
                                            Text(
                                                "ID: ${selectedFarmer?.id} - ${selectedFarmer?.name}",
                                                fontFamily = PoppinsFont,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            // Line 3: Details (Fat, Milk, Amount) - showing exact values
                                            Text(
                                                "Fat: ${fat}%  |  Milk: ${milk}L  |  ${String.format(java.util.Locale.getDefault(), "%.2f", totalPrice)}",
                                                fontFamily = PoppinsFont,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Submit button (moved to first position)
                                        OutlinedButton(
                                            onClick = {
                                                showConfirmDialog = false
                                                scope.launch {
                                                    try {
                                                        isLoading = true
                                                        
                                                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                                        val isAM = currentHour < 12
                                                        
                                                        if (isAM) {
                                                            dailyMilkCollectionRepository.createTodayCollection(
                                                                farmerId = selectedFarmer!!.id,
                                                                farmerName = selectedFarmer!!.name,
                                                                amMilk = milkValue!!,
                                                                amFat = fatValue!!,
                                                                amPrice = totalPrice
                                                            )
                                                        } else {
                                                            dailyMilkCollectionRepository.createTodayCollection(
                                                                farmerId = selectedFarmer!!.id,
                                                                farmerName = selectedFarmer!!.name,
                                                                pmMilk = milkValue!!,
                                                                pmFat = fatValue!!,
                                                                pmPrice = totalPrice
                                                            )
                                                        }
                                                        
                                                        Toast.makeText(context, "Milk collection added!", Toast.LENGTH_SHORT).show()
                                                        customerId = ""
                                                        selectedFarmer = null
                                                        fat = ""
                                                        milk = ""
                                                        errorText = null
                                                        // Request focus on ID field for next entry
                                                        delay(100) // Small delay to ensure UI updates first
                                                        idFieldFocusRequester.requestFocus()
                                                        onSubmitSuccess()
                                                        
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "Submit",
                                                fontFamily = PoppinsFont,
                                                fontSize = 14.sp
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Print & Submit button (moved to second position)
                                        Button(
                                            onClick = {
                                                showConfirmDialog = false
                                                // Print receipt first (with testing fallback)
                                                val receiptContent = buildString {
                                                    // Optimized for 2-inch thermal printer (32 characters max)
                                                    appendLine("================================")
                                                    appendLine("      MILK COLLECTION RECEIPT")
                                                    appendLine("================================")
                                                    appendLine()
                                                    appendLine("Farmer ID: ${selectedFarmer?.id}")
                                                    appendLine("Name: ${selectedFarmer?.name}")
                                                    appendLine("Date: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(Date())}")
                                                    appendLine("Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())}")
                                                    appendLine()
                                                    appendLine("Session: ${if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"}")
                                                    appendLine("Fat: ${fat}%")
                                                    appendLine("Milk: ${milk}L")
                                                    appendLine("Total: ${String.format(java.util.Locale.getDefault(), "%.2f", totalPrice)}")
                                                    appendLine()
                                                    appendLine("================================")
                                                }
                                                
                                                // Print to physical printer or show in toast for testing
                                                val printerManager = com.example.doodhsethu.utils.PrinterManager(context)
                                                
                                                // Try to print (permissions should already be requested at app launch)
                                                if (printerManager.isBluetoothAvailable() && printerManager.hasBluetoothPermissions()) {
                                                    try {
                                                        // Try to connect to a paired printer (you can specify device address)
                                                        val pairedDevices = printerManager.getPairedDevices()
                                                        val printerDevice = pairedDevices.find { 
                                                            it.name?.contains("Printer", ignoreCase = true) == true || 
                                                            it.name?.contains("POS", ignoreCase = true) == true ||
                                                            it.name?.contains("Thermal", ignoreCase = true) == true
                                                        }
                                                        
                                                        if (printerDevice != null && printerManager.connectToPrinter(printerDevice.address)) {
                                                            val printSuccess = printerManager.printReceipt(receiptContent)
                                                            printerManager.disconnect()
                                                            
                                                            if (printSuccess) {
                                                                Toast.makeText(context, "🖨️ Receipt printed successfully on ${printerDevice.name}!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "❌ Print failed. Showing receipt in toast:\n\n$receiptContent", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "📱 No printer connected. Showing receipt in toast:\n\n$receiptContent", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: SecurityException) {
                                                        Toast.makeText(context, "🔒 Bluetooth permission error. Showing receipt in toast:\n\n$receiptContent", Toast.LENGTH_LONG).show()
                                                        android.util.Log.e("MilkCollection", "Bluetooth permission error: ${e.message}")
                                                    }
                                                } else {
                                                    Toast.makeText(context, "🔵 Bluetooth not available or permissions not granted. Showing receipt in toast:\n\n$receiptContent", Toast.LENGTH_LONG).show()
                                                }
                                                
                                                // Always log for debugging
                                                android.util.Log.d("MilkCollection", "Receipt to print:\n$receiptContent")
                                                
                                                // Then submit the data
                                                scope.launch {
                                                    try {
                                                        isLoading = true
                                                        
                                                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                                        val isAM = currentHour < 12
                                                        
                                                        if (isAM) {
                                                            dailyMilkCollectionRepository.createTodayCollection(
                                                                farmerId = selectedFarmer!!.id,
                                                                farmerName = selectedFarmer!!.name,
                                                                amMilk = milkValue!!,
                                                                amFat = fatValue!!,
                                                                amPrice = totalPrice
                                                            )
                                                        } else {
                                                            dailyMilkCollectionRepository.createTodayCollection(
                                                                farmerId = selectedFarmer!!.id,
                                                                farmerName = selectedFarmer!!.name,
                                                                pmMilk = milkValue!!,
                                                                pmFat = fatValue!!,
                                                                pmPrice = totalPrice
                                                            )
                                                        }
                                                        
                                                        Toast.makeText(context, "Milk collection added!", Toast.LENGTH_SHORT).show()
                                                        customerId = ""
                                                        selectedFarmer = null
                                                        fat = ""
                                                        milk = ""
                                                        errorText = null
                                                        // Request focus on ID field for next entry
                                                        delay(100) // Small delay to ensure UI updates first
                                                        idFieldFocusRequester.requestFocus()
                                                        onSubmitSuccess()
                                                        
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_print),
                                                    contentDescription = "Print",
                                                    tint = White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Print & Submit",
                                                    fontFamily = PoppinsFont,
                                                    color = White,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = { },
                                dismissButton = { },
                                containerColor = White,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }


        @Composable
        fun NavigationItemComposable(
            item: NavigationItem,
            isSelected: Boolean,
            onClick: () -> Unit
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            id = when (item) {
                                NavigationItem.Home -> R.drawable.ic_groups
                                NavigationItem.AddFarmer -> R.drawable.ic_person_add
                                NavigationItem.MilkCollection -> R.drawable.ic_local_drink
                                NavigationItem.FatTable -> R.drawable.ic_assessment
                                NavigationItem.Profile -> R.drawable.ic_person
                                NavigationItem.Reports -> R.drawable.ic_assessment
                                NavigationItem.BillingCycles -> R.drawable.ic_receipt_long


                                NavigationItem.UserReports -> R.drawable.ic_assessment
                            }
                        ),
                        contentDescription = item.title,
                        tint = if (isSelected) PrimaryBlue else SecondaryBlue,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.title,
                        fontFamily = PoppinsFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (isSelected) PrimaryBlue else SecondaryBlue
                    )
                }
            }
        }

     }
}

@Composable
fun SideNavigationDrawer(
    selectedItem: NavigationItem,
    onItemClick: (NavigationItem) -> Unit,
    isOnline: Boolean
) {
    val navItems = listOf(
        NavigationItem.MilkCollection, // Top: make this the home entry
        NavigationItem.Home, // Renamed label shows as Farmers
        NavigationItem.FatTable,
        NavigationItem.Reports,
        NavigationItem.UserReports,
        NavigationItem.BillingCycles,
        NavigationItem.Profile // Bottom
    )

    Card(
        modifier = Modifier
            .width(270.dp)
            .fillMaxHeight()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with Network Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Menu",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = PrimaryBlue
                )
                
                // Network Status Indicator (moved from main screen)
                NetworkStatusIndicator(
                    isOnline = isOnline,
                    modifier = Modifier.scale(0.8f) // Slightly smaller for menu
                )
            }

            // Navigation Items
            navItems.forEach { item ->
                NavigationItemComposable(
                    item = item,
                    isSelected = selectedItem == item,
                    onClick = { onItemClick(item) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun LiveDateSessionDisplay(
    modifier: Modifier = Modifier
) {
    var currentDate by remember { mutableStateOf(Date()) }

    // Update date (no need for frequent updates, just once per minute)
    LaunchedEffect(Unit) {
        while (true) {
            currentDate = Date()
            kotlinx.coroutines.delay(60000) // Update every minute instead of every second
        }
    }

    val dateFormatter = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val session = if (currentHour < 12) "AM" else "PM"

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Display
            Text(
                text = dateFormatter.format(currentDate),
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = White
            )

            // Session Display
            Text(
                text = session,
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = White.copy(alpha = 0.95f)
            )
        }
    }
}