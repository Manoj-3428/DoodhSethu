package com.example.doodhsethu.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.theme.PrimaryBlue
import com.example.doodhsethu.ui.theme.SecondaryBlue
import com.example.doodhsethu.ui.theme.White
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.doodhsethu.data.models.User
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.utils.LocalPhotoManager
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    currentUser: User?,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val localPhotoManager = remember { LocalPhotoManager(context) }
    
    // Network status monitoring
    val networkUtils = remember { NetworkUtils(context) }
    val isOnline by networkUtils.isOnline.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var localPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    // Start network monitoring
    LaunchedEffect(Unit) {
        networkUtils.startMonitoring()
    }

    // Initialize with current user data and load local profile photo
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            name = currentUser.name
            isLoading = true
            try {
                // Load additional user data from Firestore
                val doc = firestore.collection("users").document(currentUser.userId).get().await()
                phone = doc.getString("phone") ?: ""
                address = doc.getString("address") ?: ""
                
                // Load profile photo from local storage first
                val localPhoto = localPhotoManager.getProfilePhotoUri(currentUser.userId)
                if (localPhoto != null) {
                    localPhotoUri = localPhoto
                    android.util.Log.d("UserProfileScreen", "Loaded profile photo from local storage")
                } else {
                    android.util.Log.d("UserProfileScreen", "No local profile photo found")
                }
            } catch (e: Exception) {
                // User might not have additional data yet, that's okay
                android.util.Log.d("UserProfileScreen", "Error loading user data: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        photoUri = uri
    }

    // Animated background
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Animated multi-stop gradient background
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val gradientShift by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bgshift"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.18f),
                            SecondaryBlue.copy(alpha = 0.10f),
                            Color(0xFFB3E5FC).copy(alpha = 0.12f),
                            White
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, gradientShift),
                        end = androidx.compose.ui.geometry.Offset(1000f, 2000f - gradientShift)
                    )
                )
        )
        // Animated floating circles
        repeat(7) { index ->
            val circleTransition = rememberInfiniteTransition(label = "float$index")
            val offsetY by circleTransition.animateFloat(
                initialValue = 0f,
                targetValue = -60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500 + index * 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float"
            )
            Box(
                modifier = Modifier
                    .size((70 + index * 14).dp)
                    .offset(
                        x = (index * 110).dp,
                        y = (index * 90 + offsetY).dp
                    )
                    .background(
                        color = PrimaryBlue.copy(alpha = 0.05f),
                        shape = CircleShape
                    )
            )
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Profile",
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
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
                    actions = {
                        var showLogoutDialog by remember { mutableStateOf(false) }
                        if (showLogoutDialog) {
                            AlertDialog(
                                onDismissRequest = { showLogoutDialog = false },
                                title = { Text("Confirm Logout", fontFamily = PoppinsFont, fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to logout?", fontFamily = PoppinsFont) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showLogoutDialog = false
                                            onLogout()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) { Text("Logout", fontFamily = PoppinsFont, color = White) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutDialog = false }) {
                                        Text("Cancel", fontFamily = PoppinsFont, color = SecondaryBlue)
                                    }
                                },
                                containerColor = White,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logout),
                                contentDescription = "Logout",
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Unified Profile Card (User ID now inside this card)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // User ID at the top, visually prominent but not in a separate card
                        if (!currentUser?.userId.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "ID: ",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.Red
                                )
                                Text(
                                    text = currentUser?.userId ?: "",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.Red
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.08f))
                                .clickable { showPhotoDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri != null) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (localPhotoUri != null) {
                                AsyncImage(
                                    model = localPhotoUri,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_person),
                                    contentDescription = "Profile Photo",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .background(White, CircleShape)
                                    .clickable { showPhotoDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "Edit Photo",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = name.ifBlank { "Your Name" },
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Personal Info",
                            fontFamily = PoppinsFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = SecondaryBlue,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name", fontFamily = PoppinsFont) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_person),
                                    contentDescription = null,
                                    tint = PrimaryBlue
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Phone Number", fontFamily = PoppinsFont) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_phone),
                                    contentDescription = null,
                                    tint = PrimaryBlue
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address", fontFamily = PoppinsFont) },
                            singleLine = false,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_location_on),
                                    contentDescription = null,
                                    tint = PrimaryBlue
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SecondaryBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        // Save button with animation
                        val buttonScale = animateFloatAsState(
                            targetValue = if (isSaving) 0.98f else 1f,
                            animationSpec = tween(durationMillis = 200)
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank() && currentUser != null) {
                                    isSaving = true
                                    scope.launch {
                                        try {
                                            // Save photo locally if a new photo was selected
                                            var savedPhotoPath: String? = null
                                            val currentPhotoUri = photoUri
                                            if (currentPhotoUri != null) {
                                                android.util.Log.d("UserProfileScreen", "Saving new profile photo locally")
                                                savedPhotoPath = localPhotoManager.saveProfilePhoto(currentUser.userId, currentPhotoUri)
                                                if (savedPhotoPath != null) {
                                                    // Update local photo URI for display
                                                    localPhotoUri = localPhotoManager.getProfilePhotoUri(currentUser.userId)
                                                    android.util.Log.d("UserProfileScreen", "Profile photo saved locally: $savedPhotoPath")
                                                }
                                            }
                                            
                                            val updateMap = mapOf(
                                                "name" to name,
                                                "phone" to phone,
                                                "address" to address,
                                                "profileImageUrl" to (savedPhotoPath ?: localPhotoUri?.toString()),
                                                "updatedAt" to java.util.Date()
                                            )
                                            
                                            if (isOnline) {
                                                // Try to update Firestore with timeout
                                                try {
                                                    withTimeout(10000) { // 10 second timeout
                                                        firestore.collection("users").document(currentUser.userId).update(updateMap).await()
                                                    }
                                                    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    // Firestore update failed, but we can still save locally
                                                    Toast.makeText(context, "Profile saved locally. Will sync when online.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                // Offline - save locally only
                                                Toast.makeText(context, "Profile saved locally. Will sync when online.", Toast.LENGTH_LONG).show()
                                            }
                                            
                                            // Clear the temporary photo URI since it's now saved
                                            photoUri = null
                                            
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .scale(buttonScale.value),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSaving && !isLoading
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Save",
                                    fontFamily = PoppinsFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = White
                                )
                            }
                        }
                    }
                }
            }
        }
        // Photo selection dialog
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
                        text = "Choose how you want to update your profile photo",
                        fontFamily = PoppinsFont,
                        fontSize = 14.sp,
                        color = SecondaryBlue
                    )
                },
                confirmButton = {
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
        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryBlue,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
            }
        }
    }
} 