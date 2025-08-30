package com.example.doodhsethu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.PrimaryBlue
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.theme.White
import com.example.doodhsethu.ui.viewmodels.UserReportsViewModel
import com.example.doodhsethu.ui.viewmodels.UserReportsViewModelFactory
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.repository.FarmerRepository
import java.text.SimpleDateFormat
import java.util.*
import com.example.doodhsethu.utils.GlobalNetworkManager
import com.example.doodhsethu.components.NetworkStatusIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserReportsScreen(
    onNavigateBack: () -> Unit,
    preSelectedFarmer: Farmer? = null,
    preFilledFarmerId: String? = null
) {
    val context = LocalContext.current
    val viewModel: UserReportsViewModel = viewModel(factory = UserReportsViewModelFactory(context))
    
    val dailyCollections by viewModel.dailyCollections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    val farmerName by viewModel.farmerName.collectAsState()
    
    var farmerId by remember { mutableStateOf(preFilledFarmerId ?: "") }
    var selectedFarmer: Farmer? by remember { mutableStateOf(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    
    // Get all farmers for filtering
    val allFarmers = remember { mutableStateListOf<Farmer>() }
    LaunchedEffect(Unit) {
        val farmerRepository = FarmerRepository(context)
        allFarmers.clear()
        allFarmers.addAll(farmerRepository.getAllFarmers())
    }
    
    // Auto-load pre-selected farmer's reports
    LaunchedEffect(preSelectedFarmer) {
        if (preSelectedFarmer != null) {
            // Clear any previous data first
            viewModel.clearData()
            farmerId = preSelectedFarmer.id
            selectedFarmer = preSelectedFarmer
            viewModel.loadUserReports(preSelectedFarmer.id)
        }
    }
    
    // Auto-load reports when pre-filled farmer ID is provided
    LaunchedEffect(preFilledFarmerId) {
        if (preFilledFarmerId != null) {
            // Clear any previous data first
            viewModel.clearData()
            farmerId = preFilledFarmerId
            // Find farmer in allFarmers if available, otherwise load reports directly
            val farmer = allFarmers.find { it.id == preFilledFarmerId }
            if (farmer != null) {
                selectedFarmer = farmer
            }
            viewModel.loadUserReports(preFilledFarmerId)
        }
    }
    
    // Update selectedFarmer when allFarmers is loaded and we have a preFilledFarmerId
    LaunchedEffect(allFarmers, preFilledFarmerId) {
        if (allFarmers.isNotEmpty() && preFilledFarmerId != null) {
            val farmer = allFarmers.find { it.id == preFilledFarmerId }
            if (farmer != null && selectedFarmer == null) {
                selectedFarmer = farmer
            }
        }
    }
    
    // Filter farmers by simple numeric ID as user types
    val filteredFarmers = remember(farmerId, allFarmers) {
        if (farmerId.isBlank()) emptyList() else allFarmers.filter {
            it.id == farmerId.trim() || it.id.startsWith(farmerId.trim())
        }
    }
    val farmerExists = allFarmers.any { it.id == farmerId.trim() }
    
    // Handle error message
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMessages() },
            title = { Text("Error", fontFamily = PoppinsFont, fontWeight = FontWeight.Bold) },
            text = { Text(message, fontFamily = PoppinsFont) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMessages() }) {
                    Text("OK", fontFamily = PoppinsFont)
                }
            }
        )
    }
    
    // Handle success message
    successMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "User Reports", 
                        fontFamily = PoppinsFont, 
                        fontWeight = FontWeight.Bold,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Beautiful Network Status Indicator
            NetworkStatusIndicator(
                isOnline = isOnline,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 16.dp)
            )
            
            // Farmer ID input with dropdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = farmerId,
                    onValueChange = { 
                        farmerId = it
                        selectedFarmer = null
                        showDropdown = it.isNotBlank()
                        errorText = null
                    },
                    label = { Text("Enter Farmer ID", fontFamily = PoppinsFont) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (farmerId.isNotBlank() && farmerExists) {
                                viewModel.loadUserReports(farmerId)
                                focusManager.clearFocus()
                                showDropdown = false
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (farmerId.isNotBlank() && farmerExists) {
                                    viewModel.loadUserReports(farmerId)
                                    focusManager.clearFocus()
                                    showDropdown = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    singleLine = true
                )
                
                // Error text for invalid farmer ID
                if (farmerId.isNotBlank() && !farmerExists) {
                    Text(
                        text = "No farmer found with this ID.",
                        color = Color.Red,
                        fontFamily = PoppinsFont,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
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
                                            farmerId = farmer.id
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
                                                .background(PrimaryBlue.copy(alpha = 0.1f), CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_person),
                                            contentDescription = "Profile Pic",
                                            tint = PrimaryBlue,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(PrimaryBlue.copy(alpha = 0.1f), CircleShape)
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
                
                // Selected farmer display
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
            
            // Search button
            Button(
                onClick = {
                    if (farmerId.isNotBlank() && farmerExists) {
                        viewModel.loadUserReports(farmerId)
                        focusManager.clearFocus()
                        showDropdown = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = farmerId.isNotBlank() && farmerExists && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Search", fontFamily = PoppinsFont, fontWeight = FontWeight.Bold, color = White)
                }
            }
            
            // Farmer name if found
            if (farmerName.isNotBlank()) {
                Text(
                    text = "Farmer: $farmerName",
                    fontFamily = PoppinsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Reports table
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (dailyCollections.isNotEmpty()) {
                DailyMilkCollectionTable(dailyCollections = dailyCollections)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No reports available. Enter a farmer ID to view reports.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontFamily = PoppinsFont
                    )
                }
            }
        }
    }
}

@Composable
fun DailyMilkCollectionTable(dailyCollections: List<DailyMilkCollectionData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryBlue.copy(alpha = 0.1f))
                .padding(8.dp)
                .border(1.dp, PrimaryBlue, RoundedCornerShape(4.dp))
        ) {
            TableCell(text = "Date", weight = 0.15f, isHeader = true)
            TableCell(text = "AM Milk (L)", weight = 0.12f, isHeader = true)
            TableCell(text = "AM Fat (%)", weight = 0.12f, isHeader = true)
            TableCell(text = "AM Amount (₹)", weight = 0.12f, isHeader = true)
            TableCell(text = "PM Milk (L)", weight = 0.12f, isHeader = true)
            TableCell(text = "PM Fat (%)", weight = 0.12f, isHeader = true)
            TableCell(text = "PM Amount (₹)", weight = 0.12f, isHeader = true)
            TableCell(text = "Total (₹)", weight = 0.12f, isHeader = true)
            // Pending/Status column removed per requirement
        }
        
        // Table content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            itemsIndexed(dailyCollections) { index, collection ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) Color.White else Color.LightGray.copy(alpha = 0.3f)
                        )
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    TableCell(text = collection.date, weight = 0.15f)
                    TableCell(text = String.format("%.1f", collection.amMilk), weight = 0.12f)
                    TableCell(text = String.format("%.1f", collection.amFat), weight = 0.12f)
                    TableCell(text = String.format("%.2f", collection.amAmount), weight = 0.12f)
                    TableCell(text = String.format("%.1f", collection.pmMilk), weight = 0.12f)
                    TableCell(text = String.format("%.1f", collection.pmFat), weight = 0.12f)
                    TableCell(text = String.format("%.2f", collection.pmAmount), weight = 0.12f)
                    TableCell(
                        text = String.format("%.2f", collection.totalAmount),
                        weight = 0.12f,
                        isBold = true
                    )
                    // Pending/Status column removed per requirement
                }
            }
        }
        
        // Summary row
        if (dailyCollections.isNotEmpty()) {
            val totalMilk = dailyCollections.sumOf { it.amMilk + it.pmMilk }
            val totalAmount = dailyCollections.sumOf { it.totalAmount }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue.copy(alpha = 0.2f))
                    .padding(8.dp)
                    .border(1.dp, PrimaryBlue, RoundedCornerShape(4.dp))
            ) {
                TableCell(
                    text = "TOTAL",
                    weight = 0.5f,
                    isHeader = true
                )
                TableCell(
                    text = String.format("%.1f L", totalMilk),
                    weight = 0.25f,
                    isHeader = true
                )
                TableCell(
                    text = String.format("₹%.2f", totalAmount),
                    weight = 0.25f,
                    isHeader = true
                )
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    textColor: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(4.dp),
        fontWeight = when {
            isHeader -> FontWeight.Bold
            isBold -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        fontFamily = PoppinsFont,
        color = if (textColor != Color.Unspecified) textColor else Color.Unspecified
    )
}

// Data class for daily milk collection
data class DailyMilkCollectionData(
    val date: String,
    val amMilk: Double,
    val amFat: Double,
    val amAmount: Double,
    val pmMilk: Double,
    val pmFat: Double,
    val pmAmount: Double,
    val totalAmount: Double,
    val paymentStatus: String // kept for logic; not displayed in UI
)