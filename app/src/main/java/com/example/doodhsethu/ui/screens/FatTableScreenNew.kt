package com.example.doodhsethu.ui.screens

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
import kotlin.math.roundToInt
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.doodhsethu.utils.ExcelParser
import android.util.Log

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
    
    // Track previous network state
    var previousNetworkState by remember { mutableStateOf(isOnline) }
    
    // Initialize data and handle network changes
    LaunchedEffect(isOnline) {
        try {
            // Always initialize data when network state changes
            viewModel.initializeData(isOnline)
            
            // If network changed from offline to online, handle the transition
            if (previousNetworkState == false && isOnline == true) {
                android.util.Log.d("FatTableScreen", "Network changed from offline to online")
                viewModel.handleOfflineToOnlineTransition()
            }
            
            // If network changed from online to offline, stop real-time sync
            if (previousNetworkState == true && isOnline == false) {
                android.util.Log.d("FatTableScreen", "Network changed from online to offline")
                // Real-time sync will be stopped in initializeData
            }
            
            previousNetworkState = isOnline
        } catch (e: Exception) {
            android.util.Log.e("FatTableScreen", "Initialization failed: ${e.message}")
            // Force clear loading state if initialization fails
            viewModel.clearLoadingState()
        }
    }
    
    // Collect states
    val rows by viewModel.fatTableRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    // Local state
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedRow by remember { mutableStateOf<FatRangeRow?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Helper function to validate Excel and CSV files
    fun isValidExcelOrCSVFile(contentType: String?, uri: Uri): Boolean {
        val validMimeTypes = listOf(
            // CSV MIME types
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain", // Some systems report CSV as text/plain
            "application/octet-stream", // Some systems report CSV as octet-stream
            // Excel MIME types
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
        )
        
        // Check file extension first (most reliable)
        val fileName = uri.lastPathSegment ?: return false
        val validExtensions = listOf(".csv", ".xlsx")
        val hasValidExtension = validExtensions.any { fileName.lowercase().endsWith(it) }
        
        if (hasValidExtension) {
            return true
        }
        
        // Check MIME type as secondary validation
        return contentType in validMimeTypes
    }
    
    // Excel/CSV file picker
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    Log.d("FatTableScreen", "File selected: $selectedUri")
                    
                    // Check file type and provide helpful messages
                    val fileTypeMessage = ExcelParser.checkFileType(selectedUri)
                    if (fileTypeMessage != null) {
                        Log.d("FatTableScreen", "File type check failed: $fileTypeMessage")
                        snackbarHostState.showSnackbar(fileTypeMessage)
                        return@launch
                    }
                    
                    // Get file info for logging
                    val contentType = context.contentResolver.getType(selectedUri)
                    val fileName = selectedUri.lastPathSegment
                    Log.d("FatTableScreen", "Content type: $contentType")
                    Log.d("FatTableScreen", "File name: $fileName")
                    
                    // Try to validate file by attempting to read it
                    try {
                        val inputStream = context.contentResolver.openInputStream(selectedUri)
                        if (inputStream == null) {
                            Log.d("FatTableScreen", "Could not open file stream")
                            snackbarHostState.showSnackbar("Could not read the selected file")
                            return@launch
                        }
                        inputStream.close()
                        
                        Log.d("FatTableScreen", "File validation passed, starting import...")
                        snackbarHostState.showSnackbar("Starting file import...")
                        viewModel.importFromExcel(selectedUri)
                        
                    } catch (e: Exception) {
                        Log.d("FatTableScreen", "File validation failed: ${e.message}")
                        snackbarHostState.showSnackbar("Could not read the selected file. Please ensure it's a valid Excel (.xlsx) or CSV file.")
                    }
                } catch (e: Exception) {
                    Log.e("FatTableScreen", "Error selecting file: ${e.message}", e)
                    snackbarHostState.showSnackbar("Error selecting file: ${e.message}")
                }
            }
        } ?: run {
            Log.d("FatTableScreen", "No file selected")
        }
    }
    
    // Handle messages
    LaunchedEffect(errorMessage, successMessage) {
        errorMessage?.let { message ->
            try {
                scope.launch { snackbarHostState.showSnackbar(message) }
                viewModel.clearMessages()
            } catch (e: Exception) {
                // Handle error display
            }
        }
        
        successMessage?.let { message ->
            try {
                scope.launch { snackbarHostState.showSnackbar(message) }
                viewModel.clearMessages()
            } catch (e: Exception) {
                // Handle success display
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fat Table",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = { onNavigateBack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    // Excel/CSV Import button
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cloud_upload),
                            contentDescription = "Import from Excel/CSV",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Fat Range",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Debug info
            Text(
                text = "Fat Table - ${rows.size} entries loaded${if (isLoading) " (Loading...)" else ""}",
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
                // Round the float values to 3 decimal places for validation
                val roundedFrom = (fromValue * 1000).roundToInt() / 1000f
                val roundedTo = (toValue * 1000).roundToInt() / 1000f
                
                // Check for overlap with existing ranges using FatTableUtils
                val testRow = FatRangeRow(
                    id = initialRow?.id ?: -1,
                    from = roundedFrom,
                    to = roundedTo,
                    price = 0.0,
                    isSynced = false
                )
                
                val isValid = com.example.doodhsethu.utils.FatTableUtils.validateFatRange(
                    testRow, 
                    currentRows, 
                    if (initialRow != null) initialRow.id else null
                )
                
                if (!isValid) {
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
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
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
                    val priceValue = price.toDoubleOrNull()
                    
                    if (fromValue != null && toValue != null && priceValue != null && fromValue < toValue && validationError == null) {
                        // Round the float values to 3 decimal places to avoid precision issues
                        val roundedFrom = (fromValue * 1000).roundToInt() / 1000f
                        val roundedTo = (toValue * 1000).roundToInt() / 1000f
                        
                        // Preserve the original ID when editing
                        val updatedRow = if (initialRow != null) {
                            // Editing existing row - preserve ID
                            android.util.Log.d("FatTableScreen", "Editing row with ID: ${initialRow.id}")
                            FatRangeRow(
                                id = initialRow.id,
                                from = roundedFrom,
                                to = roundedTo,
                                price = priceValue,
                                isSynced = false
                            )
                        } else {
                            // Adding new row
                            android.util.Log.d("FatTableScreen", "Adding new row")
                            FatRangeRow(
                                from = roundedFrom,
                                to = roundedTo,
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