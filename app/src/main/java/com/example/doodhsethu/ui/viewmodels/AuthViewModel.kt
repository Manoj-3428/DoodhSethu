package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.User
import com.example.doodhsethu.data.repository.UserRepository
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.utils.NetworkUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val usersRef = firestore.collection("users")
    private val countersRef = firestore.collection("counters")
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    private val _generatedUserId = MutableStateFlow("")
    val generatedUserId: StateFlow<String> = _generatedUserId.asStateFlow()
    
    private var requestInProgress = false
    
    // SharedPreferences keys
    companion object {
        private const val PREFS_NAME = "DoodhSethuPrefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID_COUNTER = "user_id_counter"
    }
    
    fun getCurrentCounter(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val counter = prefs.getLong(KEY_USER_ID_COUNTER, 200L).toInt()
        android.util.Log.d("AuthViewModel", "Current counter value: $counter")
        return counter
    }
    
    suspend fun findNextAvailableUserId(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AuthViewModel", "=== FIND NEXT AVAILABLE USER ID ===")
                val userRepository = UserRepository(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var currentId = prefs.getLong(KEY_USER_ID_COUNTER, 200L).toInt()
                
                android.util.Log.d("AuthViewModel", "Starting search from counter: $currentId")
                
                // Try up to 10 IDs to find an available one
                for (i in 0..9) {
                    val testId = (currentId + i).toString()
                    val existingUser = userRepository.getUserById(testId)
                    android.util.Log.d("AuthViewModel", "Checking ID $testId: ${if (existingUser != null) "EXISTS" else "AVAILABLE"}")
                    
                    if (existingUser == null) {
                        android.util.Log.d("AuthViewModel", "Found available ID: $testId")
                        return@withContext testId
                    }
                }
                
                // If we can't find one in the range, increment the counter and return it
                currentId += 10
                prefs.edit().putLong(KEY_USER_ID_COUNTER, currentId.toLong()).apply()
                android.util.Log.d("AuthViewModel", "No available IDs found, incremented counter to: $currentId")
                return@withContext currentId.toString()
                
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error finding next available ID: ${e.message}")
                return@withContext "200"
            }
        }
    }
    
    fun refreshGeneratedUserId(context: Context) {
        android.util.Log.d("AuthViewModel", "=== MANUAL REFRESH USER ID ===")
        generateUserId(context)
    }
    
    fun generateUserId(context: Context) {
        viewModelScope.launch {
            try {
                android.util.Log.d("AuthViewModel", "=== GENERATE USER ID START ===")
                _isLoading.value = true
                
                // Find the next available user ID
                val availableId = findNextAvailableUserId(context)
                _generatedUserId.value = availableId
                
                android.util.Log.d("AuthViewModel", "Generated User ID: $availableId (next available)")
                android.util.Log.d("AuthViewModel", "=== GENERATE USER ID END ===")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error generating User ID: ${e.message}")
                _generatedUserId.value = "200" // Fallback to default
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun incrementUserId(context: Context) {
        try {
            android.util.Log.d("AuthViewModel", "=== INCREMENT USER ID START ===")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentId = prefs.getLong(KEY_USER_ID_COUNTER, 200L).toInt()
            android.util.Log.d("AuthViewModel", "Current counter before increment: $currentId")
            val newId = currentId + 1
            prefs.edit().putLong(KEY_USER_ID_COUNTER, newId.toLong()).apply()
            android.util.Log.d("AuthViewModel", "User ID counter incremented: $currentId -> $newId")
            android.util.Log.d("AuthViewModel", "=== INCREMENT USER ID END ===")
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Error incrementing User ID counter: ${e.message}")
        }
    }
    
    fun login(userId: String, password: String, context: Context) {
        if (requestInProgress) return
        requestInProgress = true
        viewModelScope.launch {
            try {
                android.util.Log.d("AuthViewModel", "Login attempt for userId=$userId")
                _isLoading.value = true
                _authState.value = AuthState.Loading
                _errorMessage.value = null
                
                val userRepository = UserRepository(context)
                val networkUtils = NetworkUtils(context)
                val isOnline = networkUtils.isCurrentlyOnline()
                android.util.Log.d("AuthViewModel", "Network status: ${if (isOnline) "Online" else "Offline"}")
                
                // Try local authentication first
                var user = userRepository.authenticateUser(userId, password)
                android.util.Log.d("AuthViewModel", "Local authentication result: ${if (user != null) "Success" else "Failed"}")
                
                // If not found locally and online, try Firestore
                if (user == null && isOnline) {
                    android.util.Log.d("AuthViewModel", "Trying Firestore authentication")
                    user = userRepository.authenticateUserFromFirestore(userId, password, true)
                    android.util.Log.d("AuthViewModel", "Firestore authentication result: ${if (user != null) "Success" else "Failed"}")
                }
                
                if (user != null) {
                    saveSession(context, user)
                    _authState.value = AuthState.Success(user)
                    _successMessage.value = "Login successful!"
                    android.util.Log.d("AuthViewModel", "Login success for userId=$userId")
                } else {
                    _authState.value = AuthState.Error("Invalid credentials")
                    _errorMessage.value = "Invalid User ID or password"
                    android.util.Log.d("AuthViewModel", "Login failed: user not found")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
                _errorMessage.value = e.message ?: "Login failed"
                android.util.Log.e("AuthViewModel", "Login exception: ${e.message}")
            } finally {
                _isLoading.value = false
                requestInProgress = false
            }
        }
    }
    
    fun register(userId: String, name: String, password: String, context: Context) {
        if (requestInProgress) return
        requestInProgress = true
        viewModelScope.launch {
            try {
                android.util.Log.d("AuthViewModel", "Register attempt for userId=$userId, name=$name")
                _isLoading.value = true
                _authState.value = AuthState.Loading
                _errorMessage.value = null
                
                val userRepository = UserRepository(context)
                val networkUtils = NetworkUtils(context)
                val isOnline = networkUtils.isCurrentlyOnline()
                android.util.Log.d("AuthViewModel", "Network status: ${if (isOnline) "Online" else "Offline"}")
                
                // Check if user ID already exists locally
                val existingUser = userRepository.getUserById(userId)
                if (existingUser != null) {
                    _authState.value = AuthState.Error("User ID already exists")
                    _errorMessage.value = "User ID already exists. Please try again."
                    android.util.Log.d("AuthViewModel", "Register failed: userId $userId already exists locally")
                    return@launch
                }
                
                // Check if user ID exists in Firestore (if online)
                if (isOnline) {
                    android.util.Log.d("AuthViewModel", "Checking Firestore for existing userId=$userId")
                    val existingUserRemote = usersRef
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    if (!existingUserRemote.isEmpty) {
                        _authState.value = AuthState.Error("User ID already exists")
                        _errorMessage.value = "User ID already exists. Please try again."
                        android.util.Log.d("AuthViewModel", "Register failed: userId $userId already exists in Firestore")
                        return@launch
                    }
                    android.util.Log.d("AuthViewModel", "UserId $userId is available in Firestore")
                }
                
                // Create new user
                val user = User(
                    userId = userId,
                    name = name,
                    password = password,
                    role = "user",
                    createdAt = Date(),
                    updatedAt = Date(),
                    isSynced = false
                )
                
                android.util.Log.d("AuthViewModel", "Saving user to local database")
                // Save to Room first (works offline)
                userRepository.insertUser(user)
                android.util.Log.d("AuthViewModel", "User saved to local database successfully")
                
                // Try to save to Firestore if online
                if (isOnline) {
                    try {
                        android.util.Log.d("AuthViewModel", "Syncing user to Firestore")
                        userRepository.registerUserToFirestore(user, true)
                        android.util.Log.d("AuthViewModel", "User synced to Firestore successfully")
                    } catch (e: Exception) {
                        android.util.Log.w("AuthViewModel", "Firestore sync failed: ${e.message}, but user saved locally")
                        // Firestore save failed, but user is saved locally
                    }
                } else {
                    android.util.Log.d("AuthViewModel", "Offline mode - user saved locally only")
                }
                
                // Increment counter only after successful registration
                android.util.Log.d("AuthViewModel", "Registration successful, incrementing User ID counter")
                // Increment counter to the next value after the registered user ID
                val registeredId = userId.toInt()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_USER_ID_COUNTER, (registeredId + 1).toLong()).apply()
                android.util.Log.d("AuthViewModel", "User ID counter set to: ${registeredId + 1} (after registered ID: $registeredId)")
                
                saveSession(context, user)
                _authState.value = AuthState.Success(user)
                _successMessage.value = "Registration successful! Your User ID is $userId"
                android.util.Log.d("AuthViewModel", "Register success for userId=$userId")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
                _errorMessage.value = e.message ?: "Registration failed"
                android.util.Log.e("AuthViewModel", "Register exception: ${e.message}")
            } finally {
                _isLoading.value = false
                requestInProgress = false
            }
        }
    }
    
    fun logout(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                android.util.Log.d("AuthViewModel", "Logout started")
                
                // Clear SharedPreferences
                clearSession(context)
                android.util.Log.d("AuthViewModel", "SharedPreferences cleared")
                
                // Keep local Room tables - don't clear data on logout
                android.util.Log.d("AuthViewModel", "Keeping local data on logout - not clearing Room database")
                
                // Set auth state to Idle to trigger UI update
                _authState.value = AuthState.Idle
                android.util.Log.d("AuthViewModel", "AuthState set to Idle")
                
                _successMessage.value = "Logged out successfully"
                android.util.Log.d("AuthViewModel", "Logout completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Logout failed: ${e.message}")
                _errorMessage.value = e.message ?: "Logout failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // SharedPreferences methods
    fun saveSession(context: Context, user: User) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_ID, user.userId)
            putString(KEY_USER_NAME, user.name)
            putString("user_password", user.password)
            putString("user_role", user.role)
            putLong("user_created_at", user.createdAt.time)
            putLong("user_updated_at", user.updatedAt.time)
            putBoolean(KEY_IS_LOGGED_IN, true)
        }.apply()
    }
    
    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        android.util.Log.d("AuthViewModel", "=== CLEAR SESSION START ===")
        android.util.Log.d("AuthViewModel", "clearSession - Clearing session data but preserving counter")
        
        // Get the current counter value before clearing
        val currentCounter = prefs.getLong(KEY_USER_ID_COUNTER, 200L)
        android.util.Log.d("AuthViewModel", "clearSession - Current counter before clear: $currentCounter")
        
        // Clear all preferences
        prefs.edit().clear().apply()
        android.util.Log.d("AuthViewModel", "clearSession - All preferences cleared")
        
        // Restore the counter
        prefs.edit().putLong(KEY_USER_ID_COUNTER, currentCounter).apply()
        android.util.Log.d("AuthViewModel", "clearSession - Counter restored: $currentCounter")
        
        // Verify the counter was restored
        val restoredCounter = prefs.getLong(KEY_USER_ID_COUNTER, 200L)
        android.util.Log.d("AuthViewModel", "clearSession - Verification: counter after restore: $restoredCounter")
        android.util.Log.d("AuthViewModel", "clearSession - Session cleared, counter preserved: $currentCounter")
        android.util.Log.d("AuthViewModel", "=== CLEAR SESSION END ===")
    }
    
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        android.util.Log.d("AuthViewModel", "isLoggedIn check: $isLoggedIn")
        return isLoggedIn
    }
    
    fun getStoredUser(context: Context): User? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString(KEY_USER_ID, null)
        val userName = prefs.getString(KEY_USER_NAME, null)
        val userPassword = prefs.getString("user_password", null)
        val userRole = prefs.getString("user_role", "user")
        val userCreatedAt = prefs.getLong("user_created_at", 0L)
        val userUpdatedAt = prefs.getLong("user_updated_at", 0L)
        
        android.util.Log.d("AuthViewModel", "getStoredUser - userId: $userId, userName: $userName")
        
        return if (userId != null && userName != null && userPassword != null) {
            User(
                userId = userId,
                name = userName,
                password = userPassword,
                role = userRole ?: "user",
                createdAt = if (userCreatedAt > 0L) Date(userCreatedAt) else Date(),
                updatedAt = if (userUpdatedAt > 0L) Date(userUpdatedAt) else Date()
            )
        } else {
            android.util.Log.d("AuthViewModel", "getStoredUser - No valid user data found")
            null
        }
    }
    
    fun isSessionValid(context: Context): Boolean {
        val user = getStoredUser(context)
        val isValid = user != null && isLoggedIn(context)
        android.util.Log.d("AuthViewModel", "isSessionValid: $isValid, user: ${user?.name}")
        return isValid
    }
    
    fun restoreSessionFromStorage(context: Context) {
        val user = getStoredUser(context)
        if (user != null && isLoggedIn(context)) {
            _authState.value = AuthState.Success(user)
            android.util.Log.d("AuthViewModel", "Session restored for user: ${user.name}")
        } else {
            _authState.value = AuthState.Idle
            android.util.Log.d("AuthViewModel", "No valid session to restore")
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
    
    fun getCurrentUser(): User? {
        return when (val state = _authState.value) {
            is AuthState.Success -> state.user
            else -> null
        }
    }
} 