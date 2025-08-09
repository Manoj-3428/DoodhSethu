package com.example.doodhsethu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.theme.PrimaryBlue
import com.example.doodhsethu.ui.theme.White
import com.example.doodhsethu.components.NetworkStatusIndicator
import com.example.doodhsethu.utils.GlobalNetworkManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.ui.viewmodels.BillingCycleViewModel
import com.example.doodhsethu.ui.viewmodels.BillingCycleViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingCycleScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BillingCycleViewModel = viewModel(
        factory = BillingCycleViewModelFactory(context)
    )
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    
    val billingCycles by viewModel.billingCycles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val farmerDetailsMap by viewModel.farmerDetailsMap.collectAsState()
    
    // Debug logging
    LaunchedEffect(billingCycles, isLoading) {
        android.util.Log.d("BillingCycleScreen", "UI State - isLoading: $isLoading, billingCycles.size: ${billingCycles.size}")
    }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<Date?>(null) }
    var endDate by remember { mutableStateOf<Date?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<com.example.doodhsethu.data.models.BillingCycle?>(null) }
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    // Calculate the next available start date (next day after last billing cycle end date)
    val nextAvailableStartDate = remember(billingCycles) {
        if (billingCycles.isEmpty()) {
            // If no billing cycles exist, start from today
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        } else {
            // Get the latest billing cycle and set start date to next day
            val latestCycle = billingCycles.maxByOrNull { it.endDate }
            Calendar.getInstance().apply {
                time = latestCycle!!.endDate
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        }
    }
    
    // Get today's date for max date validation
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }
    
    // Handle error and success messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            // Show error toast
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let {
            // Show success toast
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Billing Cycles",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = White
                        )
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.refreshBillingCycles() }
                    ) {
                        Icon(
                            painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            tint = White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    // Auto-set start date to next available date
                    startDate = nextAvailableStartDate
                    endDate = null
                    showCreateDialog = true 
                },
                containerColor = PrimaryBlue
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_add),
                    contentDescription = "Create Billing Cycle",
                    tint = White
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Beautiful Network Status Indicator
            NetworkStatusIndicator(
                isOnline = isOnline,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            // Show loading indicator
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
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Loading billing cycles...",
                            fontFamily = PoppinsFont,
                            fontSize = 16.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
            } else if (billingCycles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "No billing cycles created yet.\nTap the + button to create one.",
                            fontFamily = PoppinsFont,
                            fontSize = 16.sp,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.refreshBillingCycles() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                text = "Refresh",
                                fontFamily = PoppinsFont,
                                color = White
                            )
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(billingCycles) { cycle ->
                        val farmerDetails = farmerDetailsMap[cycle.id] ?: emptyList()
                        BillingCycleCard(
                            cycle = cycle,
                            farmerDetails = farmerDetails,
                            onDeleteClick = { cycleToDelete ->
                                showDeleteConfirmation = cycleToDelete
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Create Billing Cycle Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "Create Billing Cycle",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Date Selection
                    Column {
                        Text(
                            text = "Start Date",
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                text = startDate?.let { dateFormat.format(it) } ?: "Select Start Date",
                                fontFamily = PoppinsFont,
                                fontSize = 14.sp,
                                color = White
                            )
                        }
                    }
                    
                    // End Date Selection
                    Column {
                        Text(
                            text = "End Date",
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                text = endDate?.let { dateFormat.format(it) } ?: "Select End Date",
                                fontFamily = PoppinsFont,
                                fontSize = 14.sp,
                                color = White
                            )
                        }
                    }
                    
                    // Days count
                    if (startDate != null && endDate != null) {
                        val daysCount = ((endDate!!.time - startDate!!.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                        Text(
                            text = "Billing cycle will be created for $daysCount days",
                            fontFamily = PoppinsFont,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (startDate != null && endDate != null) {
                            viewModel.createBillingCycle(startDate!!, endDate!!)
                            showCreateDialog = false
                        }
                    },
                    enabled = startDate != null && endDate != null && !isLoading
                ) {
                    Text(
                        text = "Create",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontFamily = PoppinsFont,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        )
    }
    
    // Start Date Picker
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.time ?: nextAvailableStartDate.time
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Date(millis)
                            // Check if selected date is not in the future
                            if (selectedDate <= today) {
                                startDate = selectedDate
                            } else {
                                // Show error message for invalid date selection
                                android.util.Log.w("BillingCycleScreen", "Invalid start date selected: $selectedDate (today: $today)")
                            }
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // End Date Picker
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.time ?: startDate?.time ?: nextAvailableStartDate.time
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Date(millis)
                            // Check if selected date is not in the future and not before start date
                            if (selectedDate <= today && (startDate == null || selectedDate >= startDate!!)) {
                                endDate = selectedDate
                            } else {
                                // Show error message for invalid date selection
                                android.util.Log.w("BillingCycleScreen", "Invalid end date selected: $selectedDate (today: $today, startDate: $startDate)")
                            }
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmation?.let { cycle ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = {
                Text(
                    text = "Delete Billing Cycle",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the billing cycle '${cycle.name}'? This action cannot be undone.",
                    fontFamily = PoppinsFont,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBillingCycle(cycle)
                        showDeleteConfirmation = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text(
                        text = "Cancel",
                        fontFamily = PoppinsFont,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        )
    }
}

@Composable
fun BillingCycleCard(
    cycle: com.example.doodhsethu.data.models.BillingCycle,
    farmerDetails: List<com.example.doodhsethu.data.models.FarmerBillingDetail> = emptyList(),
    onDeleteClick: (com.example.doodhsethu.data.models.BillingCycle) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val daysCount =
        ((cycle.endDate.time - cycle.startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Billing Cycle Name
            Text(
                text = cycle.name,
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryBlue
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${dateFormat.format(cycle.startDate)} - ${dateFormat.format(cycle.endDate)}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                    Text(
                        text = "$daysCount days",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${cycle.totalAmount.toInt()}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = androidx.compose.ui.graphics.Color.Green
                    )
                    Text(
                        text = "Total Amount",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (cycle.isPaid) "PAID" else "PENDING",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (cycle.isPaid) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                    )
                    Text(
                        text = " • ${farmerDetails.size} farmers",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
                
                IconButton(onClick = { onDeleteClick(cycle) }) {
                    Icon(
                        painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_delete),
                        contentDescription = "Delete Billing Cycle",
                        tint = androidx.compose.ui.graphics.Color.Red
                    )
                }
            }
        }
    }
}