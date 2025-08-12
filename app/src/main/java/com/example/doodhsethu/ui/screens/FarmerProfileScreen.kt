package com.example.doodhsethu.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.models.FarmerBillingDetail
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.viewmodels.FarmerProfileViewModel
import com.example.doodhsethu.utils.LocalPhotoManager
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerProfileScreen(
    farmer: Farmer,
    onBackClick: () -> Unit,
    onEditFarmer: (Farmer) -> Unit,
    onDeleteFarmer: (Farmer) -> Unit,
    onNavigateToReports: (String) -> Unit,
    onNavigateToAddMilkCollection: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { FarmerProfileViewModel(context) }
    
    // Load farmer profile when screen is created
    LaunchedEffect(farmer.id) {
        viewModel.loadFarmerProfile(farmer.id)
    }
    
    // Collect UI state
    val isLoading by viewModel.isLoading.collectAsState()
    val currentFarmer by viewModel.farmer.collectAsState()
    val billingCycles by viewModel.billingCycles.collectAsState()
    val billingCycleMap by viewModel.billingCycleMap.collectAsState()
    val billingCycleSummaries by viewModel.billingCycleSummaries.collectAsState()
    val currentMonthTotal by viewModel.currentMonthTotal.collectAsState()
    val pendingAmount by viewModel.pendingAmount.collectAsState()
    val paidAmount by viewModel.paidAmount.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Show error message if any
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
                        text = "Farmer Profile",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { currentFarmer?.let { onEditFarmer(it) } }) {
                        Icon(
                            painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_edit),
                            contentDescription = "Edit Farmer"
                        )
                    }
                    IconButton(onClick = { currentFarmer?.let { onDeleteFarmer(it) } }) {
                        Icon(
                            painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_delete),
                            contentDescription = "Delete Farmer"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddMilkCollection(farmer.id) }
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_add),
                    contentDescription = "Add Milk Collection"
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Farmer Info Card
                item {
                    FarmerInfoCard(farmer = currentFarmer ?: farmer)
                }
                
                // Financial Summary Card
                item {
                    FinancialSummaryCard(
                        currentMonthTotal = currentMonthTotal,
                        pendingAmount = pendingAmount,
                        paidAmount = paidAmount
                    )
                }
                
                // Billing Cycle Summaries Card
                item {
                    BillingCycleSummariesCard(
                        billingCycleSummaries = billingCycleSummaries,
                        billingCycles = billingCycles,
                        billingCycleMap = billingCycleMap
                    )
                }
                
                // Action Buttons Card
                item {
                    ActionButtonsCard(
                        onViewReports = { onNavigateToReports(farmer.id) },
                        onRefresh = { viewModel.refreshFarmerProfile() }
                    )
                }
            }
        }
    }
}

@Composable
fun FarmerInfoCard(farmer: Farmer) {
    val context = LocalContext.current
    val localPhotoManager = remember { LocalPhotoManager(context) }
    
    // Get local photo URI if available
    val localPhotoUri = localPhotoManager.getFarmerPhotoUri(farmer.id)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile photo with background
            Card(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(40.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // Load from local storage only
                if (localPhotoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(localPhotoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Farmer Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_person),
                            contentDescription = "Default Photo",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = farmer.name,
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = farmer.id,
                fontFamily = PoppinsFont,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            if (farmer.phone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = farmer.phone,
                    fontFamily = PoppinsFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FinancialSummaryCard(
    currentMonthTotal: Double,
    pendingAmount: Double,
    paidAmount: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_assessment),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Financial Summary",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Main financial metrics in a grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FinancialItem(
                    label = "Total Earnings",
                    amount = currentMonthTotal,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                FinancialItem(
                    label = "Paid Amount",
                    amount = paidAmount,
                    color = Color(0xFF4CAF50), // Green
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pending amount with special styling
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingAmount > 0) Color(0xFFFFEBEE) else Color(0xFFF5F5F5)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_assessment),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (pendingAmount > 0) Color(0xFFD32F2F) else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pending Amount",
                            fontFamily = PoppinsFont,
                            fontSize = 12.sp,
                            color = if (pendingAmount > 0) Color(0xFFD32F2F) else Color.Gray
                        )
                        Text(
                            text = "₹${String.format("%.2f", pendingAmount)}",
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (pendingAmount > 0) Color(0xFFD32F2F) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialItem(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontFamily = PoppinsFont,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "₹${String.format("%.2f", amount)}",
            fontFamily = PoppinsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        )
    }
}

@Composable
fun BillingCycleSummariesCard(
    billingCycleSummaries: List<Map<String, Any>>,
    billingCycles: List<FarmerBillingDetail>,
    billingCycleMap: Map<String, com.example.doodhsethu.data.models.BillingCycle>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_assessment),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Billing Cycles",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (billingCycles.isEmpty()) {
                Text(
                    text = "No billing cycles found",
                    fontFamily = PoppinsFont,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                billingCycles.forEach { billingCycle ->
                    BillingCycleItem(
                        billingCycle = billingCycle,
                        billingCycleObject = billingCycleMap[billingCycle.billingCycleId],
                        summary = billingCycleSummaries.find { 
                            // Try to match by billing cycle ID if available
                            it.containsKey("billing_cycle_id") && 
                            it["billing_cycle_id"] == billingCycle.billingCycleId
                        }
                    )
                    
                    if (billingCycle != billingCycles.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BillingCycleItem(
    billingCycle: FarmerBillingDetail,
    billingCycleObject: com.example.doodhsethu.data.models.BillingCycle?,
    summary: Map<String, Any>?
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = billingCycleObject?.name ?: "Cycle: ${billingCycle.billingCycleId.take(8)}...",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            
            Text(
                text = "₹${String.format("%.2f", billingCycle.originalAmount)}",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (summary != null) {
            val totalMilk = (summary["total_milk"] as? Double) ?: 0.0
            val totalFat = (summary["total_fat"] as? Double) ?: 0.0
            val totalAmount = (summary["total_amount"] as? Double) ?: 0.0
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Milk: ${String.format("%.1f", totalMilk)}L",
                    fontFamily = PoppinsFont,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Fat: ${String.format("%.1f", totalFat)}%",
                    fontFamily = PoppinsFont,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Amount: ₹${String.format("%.2f", totalAmount)}",
                    fontFamily = PoppinsFont,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



@Composable
fun ActionButtonsCard(
    onViewReports: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onViewReports,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_assessment),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View Reports",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = com.example.doodhsethu.R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refresh",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
} 