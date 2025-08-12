package com.example.doodhsethu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.res.painterResource
import com.example.doodhsethu.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.ui.theme.PrimaryBlue
import com.example.doodhsethu.ui.theme.SurfacePrimary
import com.example.doodhsethu.ui.viewmodels.DailyMilkCollectionViewModel
import com.example.doodhsethu.ui.viewmodels.DailyMilkCollectionViewModelFactory
import com.example.doodhsethu.ui.viewmodels.FarmerViewModel
import com.example.doodhsethu.ui.viewmodels.FarmerViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMilkCollectionScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dailyViewModel: DailyMilkCollectionViewModel = viewModel(factory = DailyMilkCollectionViewModelFactory(context))
    val farmerViewModel: FarmerViewModel = viewModel(factory = FarmerViewModelFactory(context))
    
    val todayCollections by dailyViewModel.todayCollections.collectAsState()
    val isLoading by dailyViewModel.isLoading.collectAsState()
    val errorMessage by dailyViewModel.errorMessage.collectAsState()
    val successMessage by dailyViewModel.successMessage.collectAsState()
    val farmers by farmerViewModel.farmers.collectAsState()
    
    var selectedFarmerId by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf("AM") }
    var milkQuantity by remember { mutableStateOf("") }
    var fatPercentage by remember { mutableStateOf("") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        farmerViewModel.loadFarmers()
    }
    
    // Clear messages after 3 seconds
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            dailyViewModel.clearError()
        }
        if (successMessage != null) {
            kotlinx.coroutines.delay(3000)
            dailyViewModel.clearSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Milk Collection", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { dailyViewModel.syncWithFirestore() }) {
                        Icon(painterResource(id = R.drawable.ic_sync), contentDescription = "Sync")
                    }
                    IconButton(onClick = { dailyViewModel.loadFromFirestore() }) {
                        Icon(painterResource(id = R.drawable.ic_download), contentDescription = "Load from Firestore")
                    }
                    IconButton(onClick = { dailyViewModel.manualSync() }) {
                        Icon(painterResource(id = R.drawable.ic_refresh), contentDescription = "Manual Sync")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header with today's date
            Card(
                modifier = Modifier.fillMaxWidth(),
                                 colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Today's Collection",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = dailyViewModel.getTodayDate(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${todayCollections.size} Farmers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Update Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quick Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Farmer Selection
                    ExposedDropdownMenuBox(
                        expanded = false,
                        onExpandedChange = { },
                    ) {
                        OutlinedTextField(
                            value = selectedFarmerId.ifEmpty { "Select Farmer" },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Farmer") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable { showUpdateDialog = true }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Session Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSession == "AM",
                            onClick = { selectedSession = "AM" },
                            label = { Text("AM") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedSession == "PM",
                            onClick = { selectedSession = "PM" },
                            label = { Text("PM") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showUpdateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFarmerId.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Collection")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Today's Collections List
            Text(
                text = "Today's Collections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (todayCollections.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfacePrimary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painterResource(id = R.drawable.ic_local_drink),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No collections for today",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(todayCollections) { collection ->
                        DailyCollectionCard(
                            collection = collection,
                            onUpdateClick = {
                                selectedFarmerId = collection.farmerId
                                showUpdateDialog = true
                            }
                        )
                    }
                }
            }
        }
        
        // Messages
        errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { dailyViewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(message)
            }
        }
        
        successMessage?.let { message ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { dailyViewModel.clearSuccess() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(message)
            }
        }
        
        // Update Dialog
    if (showUpdateDialog) {
        UpdateCollectionDialog(
            farmerId = selectedFarmerId,
            session = selectedSession,
            onSessionChange = { selectedSession = it },
            onDismiss = { 
                // Only allow dismissal if not currently loading
                if (!isLoading) {
                    showUpdateDialog = false 
                }
            },
            onUpdate = { milk, fat, price ->
                // Dialog will be dismissed after update completes in viewModel
                dailyViewModel.updateMilkCollection(selectedFarmerId, selectedSession, milk, fat, price)
                // Dialog will be automatically dismissed when isLoading becomes false
                // This is handled by the LaunchedEffect below
            }
        )
        
        // Automatically dismiss dialog when loading completes
        LaunchedEffect(isLoading) {
            if (!isLoading && showUpdateDialog) {
                showUpdateDialog = false
            }
        }
    }
    }
}

@Composable
fun DailyCollectionCard(
    collection: DailyMilkCollection,
    onUpdateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = collection.farmerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${collection.farmerId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                IconButton(onClick = onUpdateClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Update")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // AM Session
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SessionInfo(
                    title = "AM",
                    milk = collection.amMilk,
                    fat = collection.amFat,
                    price = collection.amPrice,
                    modifier = Modifier.weight(1f)
                )
                
                SessionInfo(
                    title = "PM",
                    milk = collection.pmMilk,
                    fat = collection.pmFat,
                    price = collection.pmPrice,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Totals
            Card(
                modifier = Modifier.fillMaxWidth(),
                                 colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Milk",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "${collection.totalMilk}L",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Fat",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "${collection.totalFat}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Amount",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "₹${collection.totalAmount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                                                         color = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionInfo(
    title: String,
    milk: Double,
    fat: Double,
    price: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            
            Text(
                text = "${milk}L",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${fat}%",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Text(
                text = "₹$price",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryBlue
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCollectionDialog(
    farmerId: String,
    session: String,
    onSessionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onUpdate: (Double, Double, Double) -> Unit
) {
    var milkQuantity by remember { mutableStateOf("") }
    var fatPercentage by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update $session Collection") },
        text = {
            Column {
                Text("Farmer ID: $farmerId")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Session Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = session == "AM",
                        onClick = { onSessionChange("AM") },
                        label = { Text("AM") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = session == "PM",
                        onClick = { onSessionChange("PM") },
                        label = { Text("PM") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = milkQuantity,
                    onValueChange = { milkQuantity = it },
                    label = { Text("Milk Quantity (L)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = fatPercentage,
                    onValueChange = { fatPercentage = it },
                    label = { Text("Fat Percentage (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (isUpdating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val milk = milkQuantity.toDoubleOrNull() ?: 0.0
                    val fat = fatPercentage.toDoubleOrNull() ?: 0.0
                    val priceValue = price.toDoubleOrNull() ?: 0.0
                    isUpdating = true
                    onUpdate(milk, fat, priceValue)
                    // Note: Dialog will be dismissed by the parent component after update completes
                },
                enabled = !isUpdating && milkQuantity.isNotEmpty() && fatPercentage.isNotEmpty() && price.isNotEmpty()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}