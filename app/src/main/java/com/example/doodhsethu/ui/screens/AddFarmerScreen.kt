package com.example.doodhsethu.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.doodhsethu.R
import com.example.doodhsethu.components.AuthTextField
import com.example.doodhsethu.components.OfflineSyncIndicator
import com.example.doodhsethu.components.NetworkStatusIndicator
import com.example.doodhsethu.utils.GlobalNetworkManager
import com.example.doodhsethu.ui.theme.*
import android.widget.Toast
import com.example.doodhsethu.data.models.Farmer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.ui.viewmodels.FarmerViewModelFactory
import com.example.doodhsethu.ui.viewmodels.FarmerViewModel
import androidx.core.content.FileProvider
import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFarmerScreen(
    onNavigateBack: () -> Unit,
    editFarmer: Farmer? = null,
    onFarmerAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val farmerViewModel: FarmerViewModel = viewModel(
        factory = FarmerViewModelFactory(context)
    )
    
    // Initialize photo manager
    LaunchedEffect(Unit) {
        farmerViewModel.initializePhotoManager(context)
    }
    
    // Observe ViewModel states
    val isViewModelLoading by farmerViewModel.isLoading.collectAsState()
    val errorMessage by farmerViewModel.errorMessage.collectAsState()
    val successMessage by farmerViewModel.successMessage.collectAsState()
    val isOnline by GlobalNetworkManager.getNetworkStatus().collectAsState()
    val pendingUploads by farmerViewModel.pendingUploads.collectAsState()
    
    // Form state with key to force reset for new farmers
    val formKey = editFarmer?.id ?: "new_farmer_${System.currentTimeMillis()}"
    var name by remember(formKey) { mutableStateOf(editFarmer?.name ?: "") }
    var phone by remember(formKey) { mutableStateOf(editFarmer?.phone ?: "") }
    var address by remember(formKey) { mutableStateOf(editFarmer?.address ?: "") }
    var photoUri by remember(formKey) { mutableStateOf<android.net.Uri?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    
    // Reset form fields when editFarmer changes or when screen is first loaded
    LaunchedEffect(editFarmer) {
        android.util.Log.d("AddFarmerScreen", "editFarmer changed: ${editFarmer?.name ?: "null"}")
        if (editFarmer != null) {
            // Editing existing farmer - populate fields
            name = editFarmer.name
            phone = editFarmer.phone
            address = editFarmer.address
            photoUri = null
            android.util.Log.d(
                "AddFarmerScreen",
                "Populated form with: ${editFarmer.name}, ${editFarmer.phone}, ${editFarmer.address}"
            )
        } else {
            // Adding new farmer - clear all fields
            name = ""
            phone = ""
            address = ""
            photoUri = null
            android.util.Log.d("AddFarmerScreen", "Cleared form for new farmer")
        }
    }
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        photoUri = uri
    }
    
    // Camera photo capture launcher
    val cameraImageUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri = cameraImageUri.value
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val imageFile = java.io.File.createTempFile("farmer_photo_", ".jpg", context.cacheDir)
            val imageUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                imageFile
            )
            cameraImageUri.value = imageUri
            cameraLauncher.launch(imageUri)
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to take a photo.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Handle ViewModel states
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            farmerViewModel.clearMessages()
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            farmerViewModel.clearMessages()
            
            // Reset form after successful addition (only for new farmers, not editing)
            if (editFarmer == null) {
                name = ""
                phone = ""
                address = ""
                photoUri = null
            }
            
            onFarmerAdded()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editFarmer != null) "Edit Farmer" else "Add Farmer",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = PrimaryBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(LightBlue.copy(alpha = 0.1f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Network Status Indicator
            OfflineSyncIndicator(
                isOnline = isOnline,
                pendingUploads = pendingUploads
            )

            // Beautiful Network Status Indicator
            NetworkStatusIndicator(
                isOnline = isOnline,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Farmer ID Display (for edit mode)
            if (editFarmer != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = "Farmer ID: ${editFarmer.id}",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Header
            Text(
                text = "Farmer Details",
                fontFamily = PoppinsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = PrimaryBlue,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Photo Section
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { showPhotoDialog = true },
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Farmer Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!editFarmer?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = editFarmer?.photoUrl,
                            contentDescription = "Farmer Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera_alt),
                                contentDescription = "Add Photo",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add Photo",
                                fontFamily = PoppinsFont,
                                fontSize = 10.sp,
                                color = PrimaryBlue
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Form Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(600, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(600)),
                    modifier = Modifier.animateContentSize()
            ) {
                AuthTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full Name",
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = "Name",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = "Enter farmer's full name"
                )
                }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(600, delayMillis = 100, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(600, delayMillis = 100)),
                    modifier = Modifier.animateContentSize()
                ) {
                AuthTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone Number",
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_phone),
                            contentDescription = "Phone",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = "Enter phone number",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )
                )
                }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(600, delayMillis = 200, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(600, delayMillis = 200)),
                    modifier = Modifier.animateContentSize()
                ) {
                AuthTextField(
                    value = address,
                    onValueChange = { address = it },
                        label = "Address (Optional)",
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_location_on),
                            contentDescription = "Address",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = "Enter complete address"
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Add Button
            Button(
                onClick = {
                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                        if (editFarmer != null) {
                            val updated = editFarmer.copy(
                                name = name,
                                phone = phone,
                                address = address,
                                photoUrl = photoUri?.toString() ?: editFarmer.photoUrl
                            )
                            farmerViewModel.updateFarmer(updated)
                            
                            // Handle photo save locally for editing
                            if (photoUri != null) {
                                // Save photo locally
                                scope.launch {
                                        val savedPath = farmerViewModel.saveFarmerPhoto(
                                            editFarmer.id,
                                            photoUri!!
                                        )
                                    if (savedPath != null) {
                                            Log.d(
                                                "AddFarmerScreen",
                                                "Farmer photo saved locally: $savedPath"
                                            )
                                        }
                                }
                            }
                            
                            onFarmerAdded()
                        } else {
                            val farmer = Farmer(
                                name = name,
                                phone = phone,
                                address = address,
                                photoUrl = photoUri?.toString() ?: "",
                                    addedBy = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                        ?: ""
                            )
                            farmerViewModel.addFarmer(farmer) { newFarmerId ->
                                // Handle photo save locally for new farmer
                                if (photoUri != null) {
                                    scope.launch {
                                            val savedPath = farmerViewModel.saveFarmerPhoto(
                                                newFarmerId,
                                                photoUri!!
                                            )
                                        if (savedPath != null) {
                                                Log.d(
                                                    "AddFarmerScreen",
                                                    "New farmer photo saved locally: $savedPath"
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotEmpty() && phone.isNotEmpty() && !isViewModelLoading
            ) {
                if (isViewModelLoading) {
                    CircularProgressIndicator(
                        color = White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (editFarmer != null) "Update Farmer" else "Add Farmer",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = White
                    )
                }
            }
            
            // Reset Button (only for new farmers)
            if (editFarmer == null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        name = ""
                        phone = ""
                        address = ""
                        photoUri = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = SecondaryBlue
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Reset",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear Form",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
        
        // Photo Selection Dialog
        if (showPhotoDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoDialog = false },
                title = {
                    Text(
                        text = "Select Photo",
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryBlue
                    )
                },
                text = {
                    Text(
                        text = "Choose how you want to add the farmer's photo",
                        fontFamily = PoppinsFont,
                        fontSize = 14.sp,
                        color = SecondaryBlue
                    )
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch("image/*")
                                showPhotoDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_photo_library),
                                contentDescription = "Gallery",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gallery",
                                fontFamily = PoppinsFont,
                                fontSize = 14.sp
                            )
                        }
                        
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                showPhotoDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera),
                                contentDescription = "Camera",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera",
                                fontFamily = PoppinsFont,
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPhotoDialog = false }
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = PoppinsFont,
                            fontSize = 14.sp,
                            color = SecondaryBlue
                        )
                    }
                },
                containerColor = White,
                shape = RoundedCornerShape(16.dp)
            )
            }
        }
    }
} 