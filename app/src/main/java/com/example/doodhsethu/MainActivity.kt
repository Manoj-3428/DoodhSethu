package com.example.doodhsethu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.doodhsethu.ui.theme.DoodhSethuTheme
import com.example.doodhsethu.ui.screens.DashboardScreen
import com.example.doodhsethu.ui.screens.AddFarmerScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doodhsethu.ui.screens.AddMilkCollectionScreen
import com.example.doodhsethu.ui.viewmodels.FarmerViewModel
import com.example.doodhsethu.ui.screens.UserProfileScreen
import com.example.doodhsethu.ui.screens.FatTableScreenNew
import com.example.doodhsethu.ui.screens.MilkReportsScreen
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.example.doodhsethu.data.models.User
import com.example.doodhsethu.ui.screens.BillingCycleScreen
import com.example.doodhsethu.ui.screens.FarmerProfileScreen
import com.example.doodhsethu.ui.screens.UserReportsScreen
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.utils.AutoSyncManager
import com.example.doodhsethu.utils.GlobalNetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var networkUtils: NetworkUtils
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Global Network Manager
        GlobalNetworkManager.initialize(this)
        
        // Initialize NetworkUtils (for backward compatibility)
        networkUtils = NetworkUtils(this)
        networkUtils.startMonitoring()
        
        setContent {
            DoodhSethuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthApp()
                }
            }
        }
        
        // Note: Data restoration is now handled during login process for better UX
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkUtils.stopMonitoring()
        GlobalNetworkManager.cleanup()
    }
}

@Composable
fun AuthApp() {
    var isLogin by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("milk_collection") }
    var isLoading by remember { mutableStateOf(false) }
    var isSessionChecked by remember { mutableStateOf(false) }  // Track if session check is complete
    var isDataRestoring by remember { mutableStateOf(false) }
    var dataRestorationProgress by remember { mutableStateOf(0) }
    var dataRestorationStatus by remember { mutableStateOf("Initializing...") }
    var prefillFarmerId by remember { mutableStateOf<String?>(null) }
    var editFarmer by remember { mutableStateOf<com.example.doodhsethu.data.models.Farmer?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var farmerToDelete by remember { mutableStateOf<com.example.doodhsethu.data.models.Farmer?>(null) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var selectedFarmer by remember { mutableStateOf<com.example.doodhsethu.data.models.Farmer?>(null) }
    val context = LocalContext.current
    val farmerViewModel: FarmerViewModel = viewModel(
        factory = com.example.doodhsethu.ui.viewmodels.FarmerViewModelFactory(context)
    )
    val authViewModel = viewModel<AuthViewModel>()
    val farmers by farmerViewModel.farmers.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val farmerSuccessMessage by farmerViewModel.successMessage.collectAsState()
    val farmerErrorMessage by farmerViewModel.errorMessage.collectAsState()
    
    // Check authentication state on app start
    LaunchedEffect(Unit) {
        // Move session check to background thread
        withContext(Dispatchers.IO) {
            // Check if user is already logged in via SharedPreferences
            if (authViewModel.isSessionValid(context)) {
                val storedUser = authViewModel.getStoredUser(context)
                if (storedUser != null) {
                    currentUser = storedUser
                    isAuthenticated = true
                    // Stay on milk_collection screen (already set as initial)
                    // Restore the auth state from storage
                    authViewModel.restoreSessionFromStorage(context)
                    android.util.Log.d("MainActivity", "Session restored for user: ${storedUser.name}")
                    
                    // Quick sync for restored session (data already exists locally) - no loader
                    try {
                        val autoSyncManager = AutoSyncManager(context)
                        autoSyncManager.quickSyncForRegularLogin { progress, status ->
                            // Don't update UI for background sync
                        }
                        android.util.Log.d("MainActivity", "Quick sync completed for restored session")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error in quick sync: ${e.message}")
                    }
                } else {
                    // No stored user, navigate to auth screen
                    currentScreen = "auth"
                    isAuthenticated = false
                    android.util.Log.d("MainActivity", "No stored user found, navigating to login screen")
                }
            } else {
                // No valid session, navigate to auth screen
                currentScreen = "auth"
                isAuthenticated = false
                android.util.Log.d("MainActivity", "No valid session found, navigating to login screen")
            }
            
            // Mark session check as complete
            isSessionChecked = true
            android.util.Log.d("MainActivity", "Session check completed, isAuthenticated: $isAuthenticated, currentScreen: $currentScreen")
        }
    }

    // Listen for farmer deletion success and navigate to dashboard
    LaunchedEffect(farmerSuccessMessage) {
        if (farmerSuccessMessage == "Farmer deleted successfully!") {
            currentScreen = "dashboard"
            selectedFarmer = null
            farmerViewModel.clearMessages() // Clear the success message
            android.util.Log.d("MainActivity", "Farmer deleted successfully, navigated to dashboard")
        }
    }

    // Listen for authentication state changes
    LaunchedEffect(authState) {
        android.util.Log.d("MainActivity", "AuthState changed to: ${authState::class.simpleName}")
        
        when (authState) {
            is com.example.doodhsethu.ui.viewmodels.AuthState.Success -> {
                val user = (authState as com.example.doodhsethu.ui.viewmodels.AuthState.Success).user
                currentUser = user
                isAuthenticated = true
                
                // Move heavy operations to background thread
                withContext(Dispatchers.IO) {
                    try {
                        val autoSyncManager = AutoSyncManager(context)
                        
                        // Check if this is a fresh installation or regular login
                        if (autoSyncManager.isDataRestorationNeeded()) {
                            android.util.Log.d("MainActivity", "Fresh installation detected, starting comprehensive data restoration for user: ${user.name}")
                            // Show loader only for fresh installations
                            isDataRestoring = true
                            dataRestorationProgress = 0
                            dataRestorationStatus = "Initializing..."
                            autoSyncManager.preloadAllScreenDataWithProgress { progress, status ->
                                dataRestorationProgress = progress
                                dataRestorationStatus = status
                            }
                            android.util.Log.d("MainActivity", "Comprehensive data restoration completed successfully")
                            isDataRestoring = false
                        } else {
                            android.util.Log.d("MainActivity", "Regular login detected, starting quick sync for user: ${user.name}")
                            // No loader for regular logins - sync in background
                            autoSyncManager.quickSyncForRegularLogin { progress, status ->
                                // Don't update UI for background sync
                            }
                            android.util.Log.d("MainActivity", "Quick sync completed successfully")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error during data sync: ${e.message}")
                        isDataRestoring = false
                    }
                }
                
                currentScreen = "milk_collection"
                
                // Save session to SharedPreferences
                authViewModel.saveSession(context, user)
                android.util.Log.d("MainActivity", "User authenticated: ${user.name}, screen: $currentScreen")
            }
            is com.example.doodhsethu.ui.viewmodels.AuthState.Idle -> {
                android.util.Log.d("MainActivity", "AuthState.Idle detected, isAuthenticated: $isAuthenticated")
                // Only clear session if we don't have a valid stored session
                if (!authViewModel.isSessionValid(context)) {
                    isAuthenticated = false
                    currentUser = null
                    currentScreen = "auth"
                    isLogin = true
                    isDataRestoring = false
                    android.util.Log.d("MainActivity", "User logged out, screen: $currentScreen")
                } else {
                    android.util.Log.d("MainActivity", "AuthState.Idle but valid session exists, keeping authenticated state")
                }
            }
            is com.example.doodhsethu.ui.viewmodels.AuthState.Error -> {
                // Don't change authentication state on error
                val errorState = authState as com.example.doodhsethu.ui.viewmodels.AuthState.Error
                android.util.Log.d("MainActivity", "Authentication error: ${errorState.message}")
                isDataRestoring = false
            }
            else -> {
                android.util.Log.d("MainActivity", "AuthState loading or other state")
            }
        }
    }
    
    if (isDataRestoring) {
        // Show data restoration loading screen
        DataRestorationLoadingScreen(
            progress = dataRestorationProgress,
            status = dataRestorationStatus
        )
    } else if (!isAuthenticated && isSessionChecked) {
        // Show login screen when not authenticated and session check is complete
        AuthScreen(
            isLogin = isLogin,
            onToggleMode = { isLogin = !isLogin },
            onLogin = { userId, password ->
                authViewModel.login(userId, password, context)
            },
            onRegister = { userId, name, password ->
                authViewModel.register(userId, name, password, context)
            },
            onAuthSuccess = {
                // This will be handled by the LaunchedEffect(authState)
            }
        )
    } else {
        // Show authenticated screens
        when (currentScreen) {
            "dashboard" -> {
                DashboardScreen(
                    onNavigateToAddFarmer = { 
                        editFarmer = null  // Clear editFarmer state for new farmer
                        currentScreen = "add_farmer" 
                    },
                    onNavigateToFarmerDetail = { farmerId ->
                        // Find the farmer by ID and navigate to profile
                        val farmer = farmers.find { it.id == farmerId }
                        if (farmer != null) {
                            selectedFarmer = farmer
                            currentScreen = "farmer_profile"
                        }
                    },
                    onNavigateToProfile = {
                        // Always fetch the latest user from SharedPreferences
                        currentUser = authViewModel.getStoredUser(context)
                        currentScreen = "profile"
                    },
                    onNavigateToReports = { currentScreen = "milk_reports" },

                    onNavigateToSettings = { /* TODO: Implement settings */ },
                    onLogout = {
                        authViewModel.logout(context)
                    },
                    onNavigateToAddMilkCollection = { currentScreen = "milk_collection" },
                    onNavigateToAddMilkCollectionWithFarmerId = { farmerId ->
                        prefillFarmerId = farmerId
                        currentScreen = "milk_collection"
                    },
                    onEditFarmer = { farmer ->
                        editFarmer = farmer
                        currentScreen = "add_farmer"
                    },
                    onDeleteFarmer = { farmerToDeleteParam ->
                        farmerToDelete = farmerToDeleteParam
                        showDeleteDialog = true
                    },
                    onNavigateToFatTable = { currentScreen = "fat_table" },
                    onNavigateToBillingCycles = { currentScreen = "billing_cycles" },
                    onNavigateToUserReports = { currentScreen = "user_reports" }
                )
            }
            
            "add_farmer" -> {
                AddFarmerScreen(
                    onNavigateBack = { currentScreen = "dashboard" },
                    editFarmer = editFarmer,
                    onFarmerAdded = {
                        editFarmer = null
                        currentScreen = "dashboard"
                    }
                )
            }
            
            "milk_collection" -> {
                AddMilkCollectionScreen(
                    allFarmers = farmers,
                    onAddFarmer = { currentScreen = "add_farmer" },
                    onSubmitSuccess = {
                        prefillFarmerId = null
                        currentScreen = "dashboard"
                        // AutoSyncManager will handle farmer profile updates
                    },
                    onNavigateBack = { currentScreen = "dashboard" },
                    prefillFarmerId = prefillFarmerId,
                    onNavigateToDashboard = { currentScreen = "dashboard" },
                    onNavigateToAddFarmer = { currentScreen = "add_farmer" },
                    onNavigateToFatTable = { currentScreen = "fat_table" },
                    onNavigateToProfile = { currentScreen = "profile" },
                    onNavigateToReports = { currentScreen = "milk_reports" },
                    onNavigateToUserReports = { currentScreen = "user_reports" },
                                    onNavigateToBillingCycles = { currentScreen = "billing_cycles" },
                onLogout = { authViewModel.logout(context) }
                )
            }
            
            "profile" -> {
                // Always pass the latest currentUser
                UserProfileScreen(
                    onNavigateBack = { currentScreen = "dashboard" },
                    currentUser = currentUser,
                    onLogout = {
                        authViewModel.logout(context)
                    }
                )
            }
            
            "fat_table" -> {
                FatTableScreenNew(onNavigateBack = { currentScreen = "dashboard" })
            }
            
            "milk_reports" -> {
                MilkReportsScreen(onNavigateBack = { currentScreen = "dashboard" })
            }


            
            "billing_cycles" -> {
                BillingCycleScreen(onNavigateBack = { 
                    currentScreen = "dashboard"
                    // AutoSyncManager will handle farmer profile updates
                })
            }
            

            
            "user_reports" -> {
                UserReportsScreen(
                    onNavigateBack = { currentScreen = "dashboard" },
                    preSelectedFarmer = selectedFarmer,
                    preFilledFarmerId = selectedFarmer?.id
                )
            }
            
            "farmer_profile" -> {
                selectedFarmer?.let { farmer ->
                    FarmerProfileScreen(
                        farmer = farmer,
                        onBackClick = { currentScreen = "dashboard" },
                        onEditFarmer = { farmerToEdit ->
                            editFarmer = farmerToEdit
                            currentScreen = "add_farmer"
                        },
                        onDeleteFarmer = { farmerToDeleteParam ->
                            farmerToDelete = farmerToDeleteParam
                            showDeleteDialog = true
                        },
                        onNavigateToReports = { farmerId ->
                            selectedFarmer = farmers.find { it.id == farmerId }
                            currentScreen = "user_reports"
                        },
                        onNavigateToAddMilkCollection = { farmerId ->
                            prefillFarmerId = farmerId
                            currentScreen = "milk_collection"
                        }
                    )
                }
            }
        }
        
        // Handle delete farmer dialog
    if (showDeleteDialog && farmerToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
                title = { androidx.compose.material3.Text("Delete Farmer") },
                text = { androidx.compose.material3.Text("Are you sure you want to delete ${farmerToDelete!!.name}?") },
            confirmButton = {
                    androidx.compose.material3.TextButton(
                    onClick = {
                            farmerToDelete?.let { farmer ->
                                farmerViewModel.deleteFarmer(farmer.id)
                            }
                            farmerToDelete = null
                        showDeleteDialog = false
                        }
                ) {
                        androidx.compose.material3.Text("Delete")
                }
            },
            dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            farmerToDelete = null
                            showDeleteDialog = false
                        }
                    ) {
                        androidx.compose.material3.Text("Cancel")
                }
                }
            )
        }
    }
}

@Composable
fun DataRestorationLoadingScreen(progress: Int = 0, status: String = "Initializing...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(60.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
            
            androidx.compose.material3.Text(
                text = "Restoring Data...",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            
            androidx.compose.material3.Text(
                text = "Please wait while we restore your data from the cloud",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
            
            // Progress percentage
            androidx.compose.material3.Text(
                text = "$progress%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            
            // Status text
            androidx.compose.material3.Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}