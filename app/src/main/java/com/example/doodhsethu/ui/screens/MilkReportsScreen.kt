package com.example.doodhsethu.ui.screens

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.R
import com.example.doodhsethu.data.models.MilkReportEntry

import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.viewmodels.MilkReportViewModel
import com.example.doodhsethu.ui.viewmodels.ReportPeriod
import com.example.doodhsethu.ui.viewmodels.MilkReportViewModelFactory
import com.example.doodhsethu.utils.GlobalNetworkManager
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.graphics.Color.Companion.LightGray
import com.example.doodhsethu.data.models.FarmerMilkDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkReportsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFarmerDetails: (String) -> Unit = {}
) {
    var showCustomDateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel: MilkReportViewModel = viewModel(
        factory = MilkReportViewModelFactory(context)
    )
    
    // Permission launcher for storage access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("MilkReportsScreen", "Permission result: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permission granted, proceed with export
            android.util.Log.d("MilkReportsScreen", "All permissions granted, proceeding with export")
            viewModel.exportToExcel()
        } else {
            // Permission denied
            android.util.Log.d("MilkReportsScreen", "Some permissions denied")
            Toast.makeText(context, "Storage permission required to export Excel file", Toast.LENGTH_LONG).show()
        }
    }
    
    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MilkReportsScreen", "Notification permission result: $isGranted")
        if (isGranted) {
            // Notification permission granted, proceed with export
            viewModel.exportToExcel()
        } else {
            // Notification permission denied, still proceed with export but without notification
            viewModel.exportToExcel()
        }
    }
    
    // Collect ViewModel states
    val reportEntries by viewModel.reportEntries.collectAsState()
    val farmerDetails by viewModel.farmerDetails.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsState()
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    


    
    // Load data when screen is first shown
    LaunchedEffect(Unit) {
        viewModel.loadReportData()
    }
    
    // Handle error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    // Handle success messages
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }
    
    // Handle download confirmation dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDownloadDialog() },
            title = {
                Text(
                    text = "Download Report",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Do you want to download the report?",
                    fontFamily = PoppinsFont,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.hideDownloadDialog()
                        // For Android 10+ (API 29+), we don't need storage permissions for Downloads folder
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            android.util.Log.d("MilkReportsScreen", "Android 10+, no storage permissions needed")
                            // Check notification permission for Android 13+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, 
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasNotificationPermission) {
                                    viewModel.exportToExcel()
                                } else {
                                    // Request notification permission
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.exportToExcel()
                            }
                        } else {
                            // Check if permissions are already granted for older Android versions
                            val hasWritePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, 
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            val hasReadPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, 
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            android.util.Log.d("MilkReportsScreen", "Write permission: $hasWritePermission, Read permission: $hasReadPermission")
                            
                            if (hasWritePermission && hasReadPermission) {
                                // Permissions already granted, proceed with export
                                android.util.Log.d("MilkReportsScreen", "Permissions already granted, proceeding with export")
                                viewModel.exportToExcel()
                            } else {
                                // Request permissions
                                android.util.Log.d("MilkReportsScreen", "Requesting permissions")
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Download",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.hideDownloadDialog() }
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = PoppinsFont,
                        color = Color.Gray
                    )
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MILK Reports",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = White
                        )
                    }
                },
                actions = {
                    // Excel Export Button
                    IconButton(
                        onClick = {
                            android.util.Log.d("MilkReportsScreen", "Export button clicked")
                            viewModel.confirmAndExport()
                        },
                        enabled = reportEntries.isNotEmpty() && !isLoading && !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Export to Excel",
                                tint = if (reportEntries.isNotEmpty() && !isLoading) White else White.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundBlue)
        ) {
            // Period Selection Buttons
            PeriodSelectionButtons(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { period ->
                    if (period == ReportPeriod.CUSTOM) {
                        showCustomDateDialog = true
                    } else {
                        viewModel.setPeriod(period)
                    }
                }
            )
            
            // Custom Date Range Display
            if (selectedPeriod == ReportPeriod.CUSTOM) {
                CustomDateRangeDisplay(
                    customStartDate = viewModel.customStartDate.collectAsState().value,
                    customEndDate = viewModel.customEndDate.collectAsState().value
                )
            }
            
            // Network Status Indicator
            NetworkStatusIndicator(isOnline = isOnline)
            
            // Loading Indicator
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading report data...",
                            fontFamily = PoppinsFont,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                }
            } else {
                // Report Content
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 400)
                        ),
                    colors = CardDefaults.cardColors(containerColor = White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        // Table Header
                        item {
                            ReportTableHeader()
                        }
                        
                        // Table Data
                        items(reportEntries.size) { index ->
                            val entry = reportEntries[index]
                            ReportTableRow(
                                entry = entry, 
                                index = index,
                                onClick = {
                                    onNavigateToFarmerDetails(entry.date)
                                }
                            )
                        }
                        

                    }
                }
            }
        }
        
        // Custom Date Range Dialog
        if (showCustomDateDialog) {
            CustomDateRangeDialog(
                onDismiss = { showCustomDateDialog = false },
                onConfirm = { start, end ->
                    viewModel.setCustomDateRange(start, end)
                    showCustomDateDialog = false
                }
            )
        }
        

        

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Date, Date) -> Unit
) {
    var startDate by remember { mutableStateOf<Date?>(null) }
    var endDate by remember { mutableStateOf<Date?>(null) }
    var isSelectingStartDate by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Date Range",
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
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isSelectingStartDate = true
                            showDatePicker = true
                        },
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
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isSelectingStartDate = false
                            showDatePicker = true
                        },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (startDate != null && endDate != null) {
                        onConfirm(startDate!!, endDate!!)
                    }
                },
                enabled = startDate != null && endDate != null
            ) {
                Text(
                    text = "Generate Report",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = PoppinsFont,
                    color = TextSecondary
                )
            }
        }
    )
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Date(millis)
                            if (isSelectingStartDate) {
                                startDate = selectedDate
                            } else {
                                endDate = selectedDate
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun PeriodSelectionButtons(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PeriodButton(
            text = "Prev month",
            isSelected = selectedPeriod == ReportPeriod.PREV_MONTH,
            onClick = { onPeriodSelected(ReportPeriod.PREV_MONTH) }
        )
        
        PeriodButton(
            text = "Curr month",
            isSelected = selectedPeriod == ReportPeriod.CURR_MONTH,
            onClick = { onPeriodSelected(ReportPeriod.CURR_MONTH) }
        )
        
        PeriodButton(
            text = "Custom",
            isSelected = selectedPeriod == ReportPeriod.CUSTOM,
            onClick = { 
                onPeriodSelected(ReportPeriod.CUSTOM)
            }
        )
    }
}

@Composable
fun PeriodButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryBlue else White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (isSelected) White else PrimaryBlue
        )
    }
}

@Composable
fun CustomDateRangeDisplay(
    customStartDate: Date?,
    customEndDate: Date?
) {
    if (customStartDate != null && customEndDate != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 Custom Range: ${formatDateRange(customStartDate)} - ${formatDateRange(customEndDate)}",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = SuccessGreen
                )
            }
        }
    }
}

@Composable
private fun formatDateRange(date: Date): String {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return dateFormat.format(date)
}

@Composable
fun NetworkStatusIndicator(isOnline: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                id = if (isOnline) R.drawable.ic_refresh else R.drawable.ic_settings
            ),
            contentDescription = "Network Status",
            tint = if (isOnline) SuccessGreen else Color.Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isOnline) "Online" else "Offline",
            fontFamily = PoppinsFont,
            fontSize = 12.sp,
            color = if (isOnline) SuccessGreen else Color.Red
        )
    }
}

@Composable
fun ReportTableHeader() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 300)
            ),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Date",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            Text(
                text = "AM",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            Text(
                text = "PM",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            Text(
                text = "Total",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ReportTableRow(entry: MilkReportEntry, index: Int, onClick: () -> Unit) {
    val animatedOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(
            durationMillis = 300 + (index * 50),
            easing = EaseOutCubic
        )
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .offset(y = (animatedOffset * 20).dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = 300)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (index % 2 == 0) White else Color(0xFFF8F9FA)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = formatDate(entry.date),
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = PrimaryBlue
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            // AM Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                                            text = if (entry.amQuantity > 0) "${String.format(Locale.getDefault(), "%.2f", entry.amQuantity)}L\n(₹${String.format(Locale.getDefault(), "%.2f", entry.amPrice)})" else "0L\n(₹0)",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (entry.amQuantity > 0) SuccessGreen else TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            // PM Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                                            text = if (entry.pmQuantity > 0) "${String.format(Locale.getDefault(), "%.2f", entry.pmQuantity)}L\n(₹${String.format(Locale.getDefault(), "%.2f", entry.pmPrice)})" else "0L\n(₹0)",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (entry.pmQuantity > 0) SuccessGreen else TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(LightGray))
            // Total Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                                            text = if (entry.totalQuantity > 0) "${String.format(Locale.getDefault(), "%.2f", entry.totalQuantity)}L\n(₹${String.format(Locale.getDefault(), "%.2f", entry.totalPrice)})" else "0L\n(₹0)",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (entry.totalQuantity > 0) PrimaryBlue else TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun formatDate(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        val date = inputFormat.parse(dateStr)
        outputFormat.format(date!!)
    } catch (_: Exception) {
        dateStr
    }
}





 