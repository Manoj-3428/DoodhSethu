package com.example.doodhsethu.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.viewmodels.BillingCycleDetailsViewModel
import com.example.doodhsethu.ui.viewmodels.BillingCycleDetailsViewModelFactory
import com.example.doodhsethu.ui.viewmodels.BillingCycleFarmerEntry
import com.example.doodhsethu.utils.PrinterManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BillingCycleDetailsScreen(
    billingCycleId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BillingCycleDetailsViewModel = viewModel(
        factory = BillingCycleDetailsViewModelFactory(context)
    )
    
    // Collect ViewModel states
    val billingCycle by viewModel.billingCycle.collectAsState()
    val farmerEntries by viewModel.farmerEntries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val totalMilk by viewModel.totalMilk.collectAsState()
    
    // Load data when screen is first shown
    LaunchedEffect(billingCycleId) {
        viewModel.loadBillingCycleDetails(billingCycleId)
    }
    
    // Handle error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = billingCycle?.name ?: "Billing Cycle Details",
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
            // Billing Cycle Info Card
            billingCycle?.let { cycle ->
                BillingCycleInfoCard(
                    cycle = cycle,
                    totalAmount = totalAmount,
                    totalMilk = totalMilk,
                    farmerCount = farmerEntries.size
                )
            }
            
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
                            text = "Loading farmer transactions...",
                            fontFamily = PoppinsFont,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                }
            } else if (farmerEntries.isNotEmpty()) {
                // Farmer Cards (like the image you showed)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(farmerEntries.size) { index ->
                        val entry = farmerEntries[index]
                        FarmerBillingCard(
                            entry = entry,
                            index = index,
                            billingCycle = billingCycle,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                // No data message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No farmer transactions found for this billing cycle.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontFamily = PoppinsFont,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BillingCycleInfoCard(
    cycle: com.example.doodhsethu.data.models.BillingCycle,
    totalAmount: Double,
    totalMilk: Double,
    farmerCount: Int
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val daysCount = ((cycle.endDate.time - cycle.startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Billing Cycle Name
            Text(
                text = cycle.name,
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PrimaryBlue
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Date Range
            Text(
                text = "${dateFormat.format(cycle.startDate)} - ${dateFormat.format(cycle.endDate)}",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.Gray
            )
            
            Text(
                text = "$daysCount days",
                fontFamily = PoppinsFont,
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Total Amount
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%.2f", totalAmount)}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SuccessGreen
                    )
                    Text(
                        text = "Total Amount",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                // Total Milk
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", totalMilk)}L",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryBlue
                    )
                    Text(
                        text = "Total Milk",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                // Farmer Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$farmerCount",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF9C27B0)
                    )
                    Text(
                        text = "Farmers",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun FarmerBillingCard(
    entry: BillingCycleFarmerEntry,
    index: Int,
    billingCycle: com.example.doodhsethu.data.models.BillingCycle?,
    viewModel: BillingCycleDetailsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrintDialog by remember { mutableStateOf(false) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(
            durationMillis = 300 + (index * 100),
            easing = EaseOutCubic
        )
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (animatedOffset * 20).dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = 300)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Farmer ID and Name Header
            Text(
                text = "${entry.farmerId} - ${entry.farmerName}",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = PrimaryBlue
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // AM Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AM:",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", entry.totalAmMilk)}L (${String.format(Locale.getDefault(), "%.1f", entry.avgAmFat)}%)",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%.2f", entry.totalAmPrice)}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = SuccessGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // PM Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PM:",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", entry.totalPmMilk)}L (${String.format(Locale.getDefault(), "%.1f", entry.avgPmFat)}%)",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%.2f", entry.totalPmPrice)}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = SuccessGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Total Section (Bold and Blue like in your image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total:",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PrimaryBlue
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", entry.totalMilk)}L",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PrimaryBlue
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%.2f", entry.totalAmount)}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PrimaryBlue
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Print Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        showPrintDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_print),
                            contentDescription = "Print",
                            tint = White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Print",
                            fontFamily = PoppinsFont,
                            color = White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
    
    // Print Confirmation Dialog
    if (showPrintDialog) {
        AlertDialog(
            onDismissRequest = { showPrintDialog = false },
            title = {
                Text(
                    text = "Print Receipt",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue
                )
            },
            text = {
                Text(
                    text = "Do you want to print the billing receipt for ${entry.farmerId} - ${entry.farmerName}?",
                    fontFamily = PoppinsFont,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintDialog = false
                        scope.launch {
                            try {
                                if (billingCycle != null) {
                                    val dailyCollections = viewModel.getFarmerDailyCollections(
                                        entry.farmerId, 
                                        billingCycle.startDate, 
                                        billingCycle.endDate
                                    )
                                    
                                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    
                                    val receiptContent = buildString {
                                        // Header - match daily milk receipt lines (32 chars)
                                        appendLine("================================")
                                        appendLine("      FARMER BILLING RECEIPT")
                                        appendLine("================================")
                                        appendLine()
                                        appendLine("Farmer ID: ${entry.farmerId}")
                                        appendLine("Name: ${entry.farmerName}")
                                        appendLine("Period: ${dateFormat.format(billingCycle.startDate)} - ${dateFormat.format(billingCycle.endDate)}")
                                        appendLine("Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}")
                                        appendLine("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                                        appendLine()
                                        
                                        // Table header - simple 3 column format as requested
                                        appendLine("Date        amamt        pmamt")
                                        
                                        // Daily entries - simple format without currency symbols
                                        dailyCollections.sortedBy { it.date }.forEach { collection ->
                                            // Convert date from "dd-MM-yyyy" to "dd/MM" format
                                            val dateParts = collection.date.split("-")
                                            val day = dateParts[0]
                                            val month = dateParts[1]
                                            val dateStr = "$day/$month"
                                            
                                            val amAmount = String.format(Locale.getDefault(), "%.2f", collection.amPrice)
                                            val pmAmount = String.format(Locale.getDefault(), "%.2f", collection.pmPrice)
                                            
                                            appendLine("$dateStr      $amAmount        $pmAmount")
                                        }
                                        appendLine("Total: ${String.format(Locale.getDefault(), "%.2f", entry.totalAmount)}")
                                        appendLine("================================")
                                    }
                                    
                                    // Log the complete receipt structure for debugging
                                    android.util.Log.d("BillingReceipt", "=== RECEIPT STRUCTURE ===")
                                    android.util.Log.d("BillingReceipt", "Farmer: ${entry.farmerId} - ${entry.farmerName}")
                                    android.util.Log.d("BillingReceipt", "Period: ${dateFormat.format(billingCycle.startDate)} - ${dateFormat.format(billingCycle.endDate)}")
                                    android.util.Log.d("BillingReceipt", "Total Collections: ${dailyCollections.size}")
                                    android.util.Log.d("BillingReceipt", "Total Amount: ₹${String.format(Locale.getDefault(), "%.2f", entry.totalAmount)}")
                                    android.util.Log.d("BillingReceipt", "=== RECEIPT CONTENT ===")
                                    android.util.Log.d("BillingReceipt", receiptContent)
                                    android.util.Log.d("BillingReceipt", "=== END RECEIPT ===")
                                    
                                    // Print to physical printer or show in toast for testing
                                    val printerManager = PrinterManager(context)
                                    
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
                                            android.util.Log.e("BillingReceipt", "Bluetooth permission error: ${e.message}")
                                        }
                                    } else {
                                        Toast.makeText(context, "🔵 Bluetooth not available or permissions not granted. Showing receipt in toast:\n\n$receiptContent", Toast.LENGTH_LONG).show()
                                    }
                                    
                                    // Always log for debugging
                                    android.util.Log.d("BillingReceipt", "Receipt to print:\n$receiptContent")
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error generating receipt: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Print",
                        fontFamily = PoppinsFont,
                        color = White,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPrintDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = PoppinsFont,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
