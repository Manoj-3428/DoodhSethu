package com.example.doodhsethu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.viewmodels.FatTableViewModel
import com.example.doodhsethu.ui.viewmodels.FatTableViewModelFactory
import com.example.doodhsethu.data.models.FatRangeRow
import com.example.doodhsethu.utils.NetworkUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FatTableScreenNew(
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: FatTableViewModel = viewModel(factory = FatTableViewModelFactory(context))
    val scope = rememberCoroutineScope()
    
    // Network status
    val networkUtils = remember { NetworkUtils(context) }
    val isOnline by networkUtils.isOnline.collectAsState()
    
    // Start network monitoring
    LaunchedEffect(Unit) {
        try {
            networkUtils.startMonitoring()
        } catch (e: Exception) {
            // Handle network monitoring error
        }
    }
    
    // Initialize data and handle network changes
    LaunchedEffect(isOnline) {
        try {
            if (!isOnline) {
                // If offline, just initialize with local data
                viewModel.initializeData(false)
            } else {
                // If online, initialize and sync
                android.util.Log.d("FatTableScreen", "Network available, initializing and syncing...")
                viewModel.initializeData(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("FatTableScreen", "Initialization failed: ${e.message}")
        }
    }
    
    // Collect states
    val rows by viewModel.fatTableRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Local state
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRow by remember { mutableStateOf<FatRangeRow?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            try {
                scope.launch { snackbarHostState.showSnackbar(message) }
                viewModel.clearMessages()
            } catch (e: Exception) {
                // Handle error display
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
                
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Debug info
            Text(
                text = "Fat Table - ${rows.size} entries loaded",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Network status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isOnline) Color.Green else Color.Red,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "Online - Auto-sync enabled" else "Offline - Changes saved locally",
                    color = if (isOnline) Color.Green else Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Simple table display
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "From",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "TO",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Price",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Actions",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Simple data display
                    Column {
                        rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color(0xFFF7FAFC) else Color.White)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.from.toString(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = row.to.toString(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = "₹${row.price}",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            android.util.Log.d("FatTableScreen", "Edit button clicked for row: ${row.from}-${row.to} = ₹${row.price}")
                                            selectedRow = row
                                            editingIndex = index
                                            showEditDialog = true
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_edit),
                                            contentDescription = "Edit",
                                            tint = Color.Blue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedRow = row
                                            showDeleteDialog = true
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_delete),
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Empty state
                    if (rows.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No fat table data found",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                                if (isLoading) {
                                    Text(
                                        text = "Loading...",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
        }
    }
    
    // Add Dialog
    if (showAddDialog) {
        FatRowDialogNew(
            title = "Add Fat Range",
            initialRow = null,
            currentRows = rows,
            onConfirm = { newRow ->
                viewModel.addFatRow(newRow, isOnline)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Edit Dialog
    if (showEditDialog && selectedRow != null) {
        FatRowDialogNew(
            title = "Edit Fat Range",
            initialRow = selectedRow,
            currentRows = rows,
            onConfirm = { updatedRow ->
                viewModel.updateFatRow(updatedRow, isOnline)
                showEditDialog = false
                selectedRow = null
            },
            onDismiss = { 
                showEditDialog = false
                selectedRow = null
            }
        )
    }
    
    // Delete Dialog
    if (showDeleteDialog && selectedRow != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                selectedRow = null
            },
            title = { Text("Delete Fat Range") },
            text = { Text("Are you sure you want to delete this fat range?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFatRow(selectedRow!!, isOnline)
                        showDeleteDialog = false
                        selectedRow = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        selectedRow = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FatRowDialogNew(
    title: String,
    initialRow: FatRangeRow?,
    currentRows: List<FatRangeRow>,
    onConfirm: (FatRangeRow) -> Unit,
    onDismiss: () -> Unit
) {
    var from by remember { mutableStateOf(initialRow?.from?.toString() ?: "") }
    var to by remember { mutableStateOf(initialRow?.to?.toString() ?: "") }
    var price by remember { mutableStateOf(initialRow?.price?.toString() ?: "") }
    
    // Validation state
    var validationError by remember { mutableStateOf<String?>(null) }
    
    // Real-time validation
    LaunchedEffect(from, to, currentRows) {
        val fromValue = from.toFloatOrNull()
        val toValue = to.toFloatOrNull()
        
        if (fromValue != null && toValue != null) {
            if (fromValue >= toValue) {
                validationError = "From value must be less than To value"
            } else {
                // Check for overlap with existing ranges
                val testRow = FatRangeRow(
                    id = initialRow?.id ?: -1,
                    from = fromValue,
                    to = toValue,
                    price = 0,
                    isSynced = false
                )
                
                var hasOverlap = false
                for (existingRow in currentRows) {
                    if (initialRow?.id != existingRow.id) { // Skip current row when editing
                        val overlaps = !(testRow.to <= existingRow.from || testRow.from >= existingRow.to)
                        if (overlaps) {
                            hasOverlap = true
                            break
                        }
                    }
                }
                
                if (hasOverlap) {
                    validationError = "This range overlaps with an existing range"
                } else {
                    validationError = null
                }
            }
        } else {
            validationError = null
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From (%)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To (%)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show validation error if any
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fromValue = from.toFloatOrNull()
                    val toValue = to.toFloatOrNull()
                    val priceValue = price.toIntOrNull()
                    
                    if (fromValue != null && toValue != null && priceValue != null && fromValue < toValue && validationError == null) {
                        // Preserve the original ID when editing
                        val updatedRow = if (initialRow != null) {
                            // Editing existing row - preserve ID
                            android.util.Log.d("FatTableScreen", "Editing row with ID: ${initialRow.id}")
                            FatRangeRow(
                                id = initialRow.id,
                                from = fromValue,
                                to = toValue,
                                price = priceValue,
                                isSynced = false
                            )
                        } else {
                            // Adding new row
                            android.util.Log.d("FatTableScreen", "Adding new row")
                            FatRangeRow(
                                from = fromValue,
                                to = toValue,
                                price = priceValue,
                                isSynced = false
                            )
                        }
                        android.util.Log.d("FatTableScreen", "Confirming row: ${updatedRow.from}-${updatedRow.to} = ₹${updatedRow.price}")
                        onConfirm(updatedRow)
                    }
                },
                enabled = validationError == null && from.isNotEmpty() && to.isNotEmpty() && price.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 