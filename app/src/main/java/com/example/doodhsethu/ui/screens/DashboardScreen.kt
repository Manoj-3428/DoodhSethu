package com.example.doodhsethu.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.models.NavigationItem
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.R
import com.example.doodhsethu.components.OfflineSyncIndicator
import com.example.doodhsethu.components.NetworkStatusIndicator
import com.example.doodhsethu.utils.GlobalNetworkManager
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.doodhsethu.utils.LocalPhotoManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.doodhsethu.utils.FarmerExcelParser


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onNavigateToAddFarmer: () -> Unit,
    onNavigateToFarmerDetail: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToAddMilkCollection: () -> Unit,
    onNavigateToAddMilkCollectionWithFarmerId: (String) -> Unit,
    onEditFarmer: (Farmer) -> Unit = {},
    onDeleteFarmer: (Farmer) -> Unit = {},
    onNavigateToFatTable: () -> Unit,
    onNavigateToBillingCycles: () -> Unit,

    onNavigateToUserReports: () -> Unit = {}
) {
    val context = LocalContext.current
    val farmerViewModel: com.example.doodhsethu.ui.viewmodels.FarmerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.doodhsethu.ui.viewmodels.FarmerViewModelFactory(context)
    )
    
    // Initialize photo manager
    LaunchedEffect(Unit) {
        farmerViewModel.initializePhotoManager(context)
        // Load farmers when screen is first shown
        farmerViewModel.loadFarmers()
        // AutoSyncManager will handle farmer profile updates when needed
    }
    
    // Observe ViewModel states
    val farmers by farmerViewModel.farmers.collectAsState()
    val isLoading by farmerViewModel.isLoading.collectAsState()
    val errorMessage by farmerViewModel.errorMessage.collectAsState()
    val successMessage by farmerViewModel.successMessage.collectAsState()
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    val pendingUploads by farmerViewModel.pendingUploads.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredFarmers = if (searchQuery.isBlank()) farmers else farmers.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.phone.contains(searchQuery, ignoreCase = true) ||
        it.id.contains(searchQuery, ignoreCase = true)
    }
    
    var selectedNavItem: NavigationItem by remember { mutableStateOf(NavigationItem.Home) }
    var isDrawerOpen by remember { mutableStateOf(true) }
    
    // File picker for farmer import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            android.util.Log.d("DashboardScreen", "File selected: $fileUri")
            
            // Check file type first
            val fileTypeCheck = FarmerExcelParser.checkFileType(fileUri)
            if (fileTypeCheck != null) {
                android.widget.Toast.makeText(context, fileTypeCheck, android.widget.Toast.LENGTH_LONG).show()
                return@let
            }
            
            // Get file info
            val contentResolver = context.contentResolver
            val contentType = contentResolver.getType(fileUri) ?: "unknown"
            val fileName = fileUri.toString().substringAfterLast("/")
            
            android.util.Log.d("DashboardScreen", "Content type: $contentType")
            android.util.Log.d("DashboardScreen", "File name: $fileName")
            
            // Validate file - improved validation for XLSX and CSV files
            val isValidFile = { uri: Uri ->
                val type = contentResolver.getType(uri)
                val fileName = uri.toString().lowercase()
                
                // Check MIME types for Excel and CSV files
                val isValidType = type?.contains("csv") == true || 
                                 type?.contains("text") == true ||
                                 type?.contains("application/octet-stream") == true ||
                                 type?.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") == true ||
                                 type?.contains("application/vnd.ms-excel") == true
                
                // Check file extensions
                val isValidExtension = fileName.endsWith(".csv") || 
                                      fileName.endsWith(".xlsx") || 
                                      fileName.endsWith(".xls")
                
                // For Android content URIs, we need to be more flexible
                val isContentUri = uri.toString().startsWith("content://")
                
                android.util.Log.d("DashboardScreen", "File validation - Type: $type, Extension: ${fileName.takeLast(5)}, Content URI: $isContentUri")
                
                isValidType || isValidExtension || isContentUri
            }
            
            if (!isValidFile(fileUri)) {
                android.widget.Toast.makeText(context, "Please select a valid CSV or Excel file (.csv, .xlsx)", android.widget.Toast.LENGTH_LONG).show()
                return@let
            }
            
            android.util.Log.d("DashboardScreen", "File validation passed, starting import...")
            
            // Show loading toast
            android.widget.Toast.makeText(context, "Importing farmers from file...", android.widget.Toast.LENGTH_SHORT).show()
            
            // Start import process
            farmerViewModel.importFromExcel(fileUri)
        }
    }
    
    // Handle error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            farmerViewModel.clearMessages()
        }
    }
    
    // Handle success messages
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            farmerViewModel.clearMessages()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Farmers",
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
                actions = {
                    // Upload farmers from Excel/CSV
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cloud_upload),
                            contentDescription = "Import farmers from Excel/CSV",
                            tint = PrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddFarmer,
                containerColor = PrimaryBlue,
                contentColor = White
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add Farmer"
                )
            }
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
                    selectedItem = selectedNavItem,
                    onItemClick = { item ->
                        selectedNavItem = item
                        // Keep drawer open; only the hamburger toggles it
                        when (item) {
                            NavigationItem.AddFarmer -> onNavigateToAddFarmer()
                            NavigationItem.Profile -> onNavigateToProfile()
                            NavigationItem.Reports -> onNavigateToReports()
        
                            NavigationItem.UserReports -> onNavigateToUserReports()
                
                            NavigationItem.MilkCollection -> onNavigateToAddMilkCollection()
                            NavigationItem.FatTable -> onNavigateToFatTable()
                            NavigationItem.BillingCycles -> onNavigateToBillingCycles()
                
                            else -> {}
                        }
                    }
                )
            }
            
            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBlue.copy(alpha = 0.1f))
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryBlue,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "Loading farmers...",
                                fontFamily = PoppinsFont,
                                fontSize = 16.sp,
                                color = PrimaryBlue
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Offline Sync Indicator
                        OfflineSyncIndicator(
                            isOnline = isOnline,
                            pendingUploads = pendingUploads
                        )
                        
                        // Beautiful Network Status Indicator
                        NetworkStatusIndicator(
                            isOnline = isOnline,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search farmers", fontFamily = PoppinsFont) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_search),
                                    contentDescription = "Search",
                                    tint = PrimaryBlue
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue,
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Farmers (${filteredFarmers.size})",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = PrimaryBlue
                                    )
                                    IconButton(
                                        onClick = { farmerViewModel.loadFarmers() }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_refresh),
                                            contentDescription = "Refresh",
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                            if (filteredFarmers.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = White)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_person),
                                                contentDescription = "No Farmers",
                                                tint = SecondaryBlue,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No Farmers Yet",
                                                fontFamily = PoppinsFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = PrimaryBlue
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Add your first farmer to get started",
                                                fontFamily = PoppinsFont,
                                                fontSize = 16.sp,
                                                color = SecondaryBlue,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredFarmers, key = { it.id }) { farmer ->
                                    FarmerCard(
                                        farmer = farmer,
                                        onClick = { onNavigateToFarmerDetail(farmer.id) },
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SideNavigationDrawer(
    selectedItem: NavigationItem,
    onItemClick: (NavigationItem) -> Unit
) {
    val navItems = listOf(
        NavigationItem.MilkCollection, // Top/Home
        NavigationItem.Home, // Farmers
        NavigationItem.FatTable,
        NavigationItem.Reports,
        NavigationItem.UserReports,
        NavigationItem.BillingCycles,
        NavigationItem.Profile // Bottom
    )
    
    Card(
        modifier = Modifier
            .width(280.dp)
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
            // Header
            Text(
                text = "Menu",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PrimaryBlue,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
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
                painter = painterResource(id = when (item) {
                    NavigationItem.Home -> R.drawable.ic_groups
                    NavigationItem.AddFarmer -> R.drawable.ic_person_add
                    NavigationItem.MilkCollection -> R.drawable.ic_local_drink
                    NavigationItem.FatTable -> R.drawable.ic_assessment
                    NavigationItem.Profile -> R.drawable.ic_person
                    NavigationItem.Reports -> R.drawable.ic_assessment

                    NavigationItem.BillingCycles -> R.drawable.ic_receipt_long
        

                    NavigationItem.UserReports -> R.drawable.ic_assessment
                }),
                contentDescription = item.title,
                tint = if (isSelected) PrimaryBlue else SecondaryBlue,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = item.title,
                fontFamily = PoppinsFont,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp,
                color = if (isSelected) PrimaryBlue else SecondaryBlue
            )
        }
    }
}

@Composable
fun FarmerCard(
    farmer: Farmer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localPhotoManager = remember { LocalPhotoManager(context) }
    
    // Get local photo URI if available
    val localPhotoUri = localPhotoManager.getFarmerPhotoUri(farmer.id)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Farmer Photo
            Card(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(30.dp)
            ) {
                // Priority: Local photo > Remote photo > Default
                val photoUri = when {
                    localPhotoUri != null -> localPhotoUri
                    farmer.photoUrl.isNotEmpty() -> farmer.photoUrl
                    else -> null
                }
                
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Farmer Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PrimaryBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = "Default Photo",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Farmer Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = farmer.name,
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${farmer.id}",
                    fontFamily = PoppinsFont,
                    fontSize = 12.sp,
                    color = SecondaryBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = farmer.phone,
                    fontFamily = PoppinsFont,
                    fontSize = 14.sp,
                    color = SecondaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = farmer.address,
                    fontFamily = PoppinsFont,
                    fontSize = 12.sp,
                    color = SecondaryBlue.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            // Arrow Icon
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward_ios),
                contentDescription = "View Details",
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}