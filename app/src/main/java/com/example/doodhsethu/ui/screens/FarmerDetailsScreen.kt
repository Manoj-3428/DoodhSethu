package com.example.doodhsethu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.data.models.FarmerMilkDetail
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.viewmodels.FarmerDetailsViewModel
import com.example.doodhsethu.ui.viewmodels.FarmerDetailsViewModelFactory
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerDetailsScreen(
    date: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FarmerDetailsViewModel = viewModel(
        factory = FarmerDetailsViewModelFactory(context)
    )
    
    val farmerDetails by viewModel.farmerDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Load data when screen is shown
    LaunchedEffect(date) {
        if (date.isNotEmpty()) {
            viewModel.loadFarmerDetailsForDate(date)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Farmer Details - $date",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = PrimaryBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7FAFC))
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PrimaryBlue
                )
            } else if (farmerDetails.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No farmer data found for $date",
                        fontFamily = PoppinsFont,
                        fontSize = 16.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Farmer details
                    items(farmerDetails) { farmer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Farmer ID and Name
                                Text(
                                    text = "${farmer.farmerId} - ${farmer.farmerName}",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                // AM Session Details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "AM:",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.2f", farmer.amMilk)}L (${String.format(Locale.getDefault(), "%.1f", farmer.amFat)}%)",
                                        fontFamily = PoppinsFont,
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%.2f", farmer.amPrice)}",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = SuccessGreen,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // PM Session Details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "PM:",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.2f", farmer.pmMilk)}L (${String.format(Locale.getDefault(), "%.1f", farmer.pmFat)}%)",
                                        fontFamily = PoppinsFont,
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%.2f", farmer.pmPrice)}",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = SuccessGreen,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                
                                // Total
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total:",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.2f", farmer.totalMilk)}L",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryBlue,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%.2f", farmer.totalPrice)}",
                                        fontFamily = PoppinsFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PrimaryBlue,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
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
