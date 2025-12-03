package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.repository.FarmerRepository
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.example.doodhsethu.utils.LocalPhotoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.*
import android.net.Uri
import android.util.Log
import com.example.doodhsethu.utils.FarmerExcelParser
import com.example.doodhsethu.utils.FarmerParseResult

class FarmerViewModel(context: Context) : ViewModel() {
    private val repository = FarmerRepository(context)
    private val authViewModel = AuthViewModel()
    private var localPhotoManager: LocalPhotoManager? = null
    private var networkUtils: NetworkUtils? = null
    private val appContext = context
    
    private val _farmers = MutableStateFlow<List<Farmer>>(emptyList())
    val farmers: StateFlow<List<Farmer>> = _farmers.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingUploads = MutableStateFlow(0)
    val pendingUploads: StateFlow<Int> = _pendingUploads.asStateFlow()
    
    // Cache management
    private var isInitialized = false
    private var lastSyncTime = 0L
    private val syncInterval = 5 * 60 * 1000L // 5 minutes
    
    // Recently deleted farmers cache (to prevent re-downloading)
    private val recentlyDeletedFarmers = mutableSetOf<String>()
    private var deletionCacheTime = 0L
    private val deletionCacheDuration = 30 * 1000L // 30 seconds
    
    init {
        // Load from Room on init in background thread
        viewModelScope.launch(Dispatchers.IO) {
            loadFarmersFromLocal()
        }
    }

    fun initializePhotoManager(context: Context) {
        localPhotoManager = LocalPhotoManager(context)
        networkUtils = NetworkUtils(context)
        
        // Start network monitoring
        networkUtils?.startMonitoring()
        
        // Monitor network status
        viewModelScope.launch {
            networkUtils?.isOnline?.collect { isOnline ->
                _isOnline.value = isOnline
                if (isOnline) {
                    // Sync local data to Firestore when online
                    syncLocalWithFirestore()
                }
            }
        }
    }

    suspend fun saveFarmerPhoto(farmerId: String, localUri: Uri): String? {
        return localPhotoManager?.saveFarmerPhoto(farmerId, localUri)
    }

    @Suppress("unused")
    fun getFarmerPhotoUri(farmerId: String): Uri? {
        return localPhotoManager?.getFarmerPhotoUri(farmerId)
    }

    @Suppress("unused")
    fun hasFarmerPhoto(farmerId: String): Boolean {
        return localPhotoManager?.hasFarmerPhoto(farmerId) ?: false
    }

    @Suppress("unused")
    fun deleteFarmerPhoto(farmerId: String): Boolean {
        return localPhotoManager?.deleteFarmerPhoto(farmerId) ?: false
    }

    @Suppress("unused")
    fun refreshDataWhenOnline() {
        if (isOnline.value) {
            viewModelScope.launch {
                syncLocalWithFirestore()
            }
        }
    }
    
    // Force refresh data (bypass cache)
    @Suppress("unused")
    fun forceRefreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NO LOADING INDICATOR - background refresh for smooth UX
                Log.d("FarmerViewModel", "Starting background force refresh...")
                
                // Load local data first (instant)
                val localFarmers = sortFarmersById(repository.getAllFarmers())
                _farmers.value = localFarmers
                Log.d("FarmerViewModel", "Loaded ${localFarmers.size} farmers from local (instant)")
                
                // Then sync with Firestore in background
                repository.syncLocalWithFirestore(_isOnline.value)
                lastSyncTime = System.currentTimeMillis()
                
                // Update with synced data
                val syncedFarmers = sortFarmersById(repository.getAllFarmers())
                _farmers.value = syncedFarmers
                Log.d("FarmerViewModel", "Background force refresh completed, loaded ${syncedFarmers.size} farmers")
            } catch (e: Exception) {
                Log.w("FarmerViewModel", "Background force refresh failed: ${e.message}")
                // Don't show error to user - just log it
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }
    
    // Clear cache and force reload
    @Suppress("unused")
    fun clearCacheAndReload() {
        isInitialized = false
        lastSyncTime = 0L
        loadFarmers()
    }
    
    fun loadFarmers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If already loaded and recently synced, just return cached data
                if (isInitialized && _farmers.value.isNotEmpty() && 
                    (System.currentTimeMillis() - lastSyncTime) < syncInterval) {
                    Log.d("FarmerViewModel", "Using cached data, last sync: ${System.currentTimeMillis() - lastSyncTime}ms ago")
                    return@launch
                }
                
                // NO LOADING INDICATOR - instant load for smooth UX
                _errorMessage.value = null
                
                // Load local data first (instant)
                var farmers = sortFarmersById(repository.getAllFarmers())
                
                // Filter out recently deleted farmers
                cleanupDeletionCache()
                if (recentlyDeletedFarmers.isNotEmpty()) {
                    val originalSize = farmers.size
                    farmers = farmers.filter { !recentlyDeletedFarmers.contains(it.id) }
                    if (farmers.size != originalSize) {
                        Log.d("FarmerViewModel", "Filtered ${originalSize - farmers.size} recently deleted farmers")
                    }
                }
                
                _farmers.value = farmers
                isInitialized = true
                Log.d("FarmerViewModel", "Loaded ${farmers.size} farmers from local database")
                
                // If online and not recently synced, sync with Firestore in background
                if (_isOnline.value && (System.currentTimeMillis() - lastSyncTime) >= syncInterval) {
                    Log.d("FarmerViewModel", "Starting background Firestore sync...")
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.syncLocalWithFirestore(true)
                            lastSyncTime = System.currentTimeMillis()
                            
                            // Update UI with synced data, filtering deleted farmers
                            var syncedFarmers = sortFarmersById(repository.getAllFarmers())
                            
                            // Filter out recently deleted farmers
                            if (recentlyDeletedFarmers.isNotEmpty()) {
                                val originalSize = syncedFarmers.size
                                syncedFarmers = syncedFarmers.filter { !recentlyDeletedFarmers.contains(it.id) }
                                if (syncedFarmers.size != originalSize) {
                                    Log.d("FarmerViewModel", "Filtered ${originalSize - syncedFarmers.size} recently deleted farmers from sync")
                                }
                            }
                            
                            _farmers.value = syncedFarmers
                            Log.d("FarmerViewModel", "Background sync completed, updated to ${syncedFarmers.size} farmers")
                        } catch (e: Exception) {
                            Log.w("FarmerViewModel", "Background Firestore sync failed: ${e.message}")
                            // Don't show error to user - just log it
                        }
                    }
                } else {
                    Log.d("FarmerViewModel", "Using local data only")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load farmers"
                Log.e("FarmerViewModel", "Error loading farmers: ${e.message}")
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }

    private fun loadFarmersFromLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NO LOADING INDICATOR - instant load for smooth UX
                _errorMessage.value = null
                val farmers = sortFarmersById(repository.getAllFarmers())
                _farmers.value = farmers
                Log.d("FarmerViewModel", "Loaded ${farmers.size} farmers from local database")
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load farmers"
                Log.e("FarmerViewModel", "Error loading farmers: ${e.message}")
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }
    
    private fun syncLocalWithFirestore() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NO LOADING INDICATOR - background sync for smooth UX
                Log.d("FarmerViewModel", "Starting background Firestore sync...")
                
                // Clean up expired deletion cache
                cleanupDeletionCache()
                
                repository.syncLocalWithFirestore(_isOnline.value)
                var farmers = sortFarmersById(repository.getAllFarmers())
                
                // Filter out recently deleted farmers from UI
                if (recentlyDeletedFarmers.isNotEmpty()) {
                    val originalSize = farmers.size
                    farmers = farmers.filter { !recentlyDeletedFarmers.contains(it.id) }
                    if (farmers.size != originalSize) {
                        Log.d("FarmerViewModel", "Filtered ${originalSize - farmers.size} recently deleted farmers from UI")
                    }
                }
                
                _farmers.value = farmers
                Log.d("FarmerViewModel", "Background Firestore sync completed, loaded ${farmers.size} farmers")
            } catch (e: Exception) {
                // If Firestore fails, just use local data silently
                Log.w("FarmerViewModel", "Background Firestore sync failed, using local data: ${e.message}")
                var farmers = sortFarmersById(repository.getAllFarmers())
                
                // Filter out recently deleted farmers even from local data
                if (recentlyDeletedFarmers.isNotEmpty()) {
                    farmers = farmers.filter { !recentlyDeletedFarmers.contains(it.id) }
                }
                
                _farmers.value = farmers
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }
    
    private fun cleanupDeletionCache() {
        if (System.currentTimeMillis() - deletionCacheTime > deletionCacheDuration) {
            if (recentlyDeletedFarmers.isNotEmpty()) {
                Log.d("FarmerViewModel", "Clearing deletion cache of ${recentlyDeletedFarmers.size} farmers")
                recentlyDeletedFarmers.clear()
            }
        }
    }

    fun addFarmer(farmer: Farmer, onSuccess: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                // Generate ID locally based on existing farmers
                val farmers = repository.getAllFarmers()
                val existingIds = farmers.mapNotNull { it.id.toIntOrNull() }
                val nextId = if (existingIds.isEmpty()) 101 else (existingIds.maxOrNull()!! + 1)
                val farmerWithId = farmer.copy(
                    id = nextId.toString(),
                    addedBy = authViewModel.getStoredUser(appContext)?.userId ?: "",
                    createdAt = Date(),
                    updatedAt = Date(),
                    synced = false
                )
                repository.insertFarmer(farmerWithId)
                _farmers.value = sortFarmersById(repository.getAllFarmers())
                _successMessage.value = "Farmer added successfully!"
                onSuccess(farmerWithId.id)
                Log.d("FarmerViewModel", "Farmer added locally: ${farmerWithId.id}")
                
                // Try to save to Firestore if online (non-blocking)
                try {
                    repository.uploadFarmerToFirestore(farmerWithId, _isOnline.value)
                    Log.d("FarmerViewModel", "Farmer uploaded to Firestore: ${farmerWithId.id}")
                } catch (e: Exception) {
                    Log.e("FarmerViewModel", "Failed to upload farmer to Firestore: ${e.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to add farmer"
                Log.e("FarmerViewModel", "Error adding farmer: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateFarmer(farmer: Farmer) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                val updatedFarmer = farmer.copy(updatedAt = Date(), synced = false)
                repository.updateFarmer(updatedFarmer)
                _farmers.value = sortFarmersById(repository.getAllFarmers())
                _successMessage.value = "Farmer updated successfully!"
                try {
                    repository.uploadFarmerToFirestore(updatedFarmer, _isOnline.value)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update farmer"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteFarmer(farmerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NO LOADING INDICATOR - instant deletion for smooth UX
                _errorMessage.value = null
                
                // 1. Delete from local storage immediately (instant UI update)
                repository.deleteFarmerById(farmerId)
                
                // 2. Instantly update UI with new farmers list
                val updatedFarmers = sortFarmersById(repository.getAllFarmers())
                _farmers.value = updatedFarmers
                Log.d("FarmerViewModel", "Farmer $farmerId deleted locally, updated farmers list size: ${updatedFarmers.size}")
                
                // 3. Send success message for navigation
                _successMessage.value = "Farmer deleted successfully!"
                
                // 4. Add to recently deleted cache to prevent re-downloading
                recentlyDeletedFarmers.add(farmerId)
                deletionCacheTime = System.currentTimeMillis()
                Log.d("FarmerViewModel", "Added farmer $farmerId to deletion cache")
                
                // 5. Prevent background sync for a short time to avoid downloading deleted farmer
                lastSyncTime = System.currentTimeMillis() + 10000L // Skip sync for 10 seconds
                
                // 5. Delete from Firestore in background (no blocking, no loader)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.deleteFarmerFromFirestore(farmerId, _isOnline.value)
                        Log.d("FarmerViewModel", "Farmer $farmerId deleted from Firestore in background")
                        
                        // Remove from deletion cache after successful Firestore deletion
                        recentlyDeletedFarmers.remove(farmerId)
                        Log.d("FarmerViewModel", "Removed farmer $farmerId from deletion cache after Firestore deletion")
                        
                        // Reset sync time after successful Firestore deletion
                        lastSyncTime = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.w("FarmerViewModel", "Background Firestore deletion failed (will retry later): ${e.message}")
                        // Reset sync time even on failure to allow retries
                        lastSyncTime = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete farmer"
                Log.e("FarmerViewModel", "Error deleting farmer locally: ${e.message}")
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }
    
    @Suppress("unused")
    fun searchFarmers(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NO LOADING INDICATOR - instant search for smooth UX
                _errorMessage.value = null
                val farmers = sortFarmersById(repository.getAllFarmers())
                if (query.isEmpty()) {
                    _farmers.value = farmers
                    return@launch
                }
                val filteredFarmers = farmers.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true)
                }.let { sortFarmersById(it) }
                _farmers.value = filteredFarmers
                Log.d("FarmerViewModel", "Search completed: found ${filteredFarmers.size} farmers for query '$query'")
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to search farmers"
                Log.e("FarmerViewModel", "Error searching farmers: ${e.message}")
            }
            // NO FINALLY BLOCK - no loading state to clear
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
    
    /**
     * Import farmers from Excel/CSV file
     */
    fun importFromExcel(fileUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val contentResolver = appContext.contentResolver
                
                // Parse Excel or CSV file using the new method
                val existingFarmers = repository.getAllFarmers()
                val parseResult = FarmerExcelParser.parseFarmerFile(fileUri, contentResolver, existingFarmers)
                
                when (parseResult) {
                    is FarmerParseResult.Success -> {
                        val farmers = parseResult.farmers
                        val totalRows = parseResult.totalRowsProcessed
                        val skippedRows = parseResult.skippedRows
                        
                        // Validate imported data
                        if (farmers.isEmpty()) {
                            _errorMessage.value = "No valid farmer data found in file"
                            return@launch
                        }
                        
                        // Add farmers with validation (checks for duplicates)
                        val success = repository.addFarmersWithSync(farmers, _isOnline.value)
                        
                        if (success) {
                            // Refresh UI immediately
                            _farmers.value = sortFarmersById(repository.getAllFarmers())
                            
                            // Success message - all rows were valid and imported
                            val successMessage = if (totalRows > 0) {
                                "✅ Successfully imported all ${farmers.size} farmers from ${totalRows} rows"
                            } else {
                                "✅ Successfully imported all ${farmers.size} farmers from file"
                            }
                            _successMessage.value = successMessage
                            
                            Log.d("FarmerViewModel", "Imported all ${farmers.size} farmers from file")
                        } else {
                            _errorMessage.value = "Some farmers could not be imported due to duplicates"
                        }
                    }
                    
                    is FarmerParseResult.Error -> {
                        _errorMessage.value = parseResult.message
                        Log.e("FarmerViewModel", "File import error: ${parseResult.message}")
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Error importing file: ${e.message}"
                Log.e("FarmerViewModel", "File import exception: ${e.message}")
            } finally {
                // Always clear loading state immediately after import
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Replace all farmers with imported data (bulk replace)
     */
    @Suppress("unused")
    fun replaceAllFarmersFromExcel(fileUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val contentResolver = appContext.contentResolver
                
                // Parse Excel or CSV file using the new method
                val existingFarmers = repository.getAllFarmers()
                val parseResult = FarmerExcelParser.parseFarmerFile(fileUri, contentResolver, existingFarmers)
                
                when (parseResult) {
                    is FarmerParseResult.Success -> {
                        val farmers = parseResult.farmers
                        
                        // Validate imported data
                        if (farmers.isEmpty()) {
                            _errorMessage.value = "No valid farmer data found in file"
                            return@launch
                        }
                        
                        // Replace all farmers with imported data (this is now non-blocking)
                        repository.replaceAllFarmers(farmers)
                        
                        // Refresh UI immediately
                        _farmers.value = sortFarmersById(repository.getAllFarmers())
                        
                        _successMessage.value = "Successfully replaced all farmers with ${farmers.size} entries from file"
                        
                        Log.d("FarmerViewModel", "Replaced all farmers with ${farmers.size} entries from file")
                    }
                    
                    is FarmerParseResult.Error -> {
                        _errorMessage.value = parseResult.message
                        Log.e("FarmerViewModel", "File import error: ${parseResult.message}")
                    }

                    is FarmerParseResult.Success -> TODO()
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Error importing file: ${e.message}"
                Log.e("FarmerViewModel", "File import exception: ${e.message}")
            } finally {
                // Always clear loading state immediately after import
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Sync unsynced farmers when coming back online
     */
    @Suppress("unused")
    fun syncUnsyncedFarmers(isOnline: Boolean) {
        if (!isOnline) {
            _errorMessage.value = "Cannot sync: No internet connection"
            return
        }
        
        viewModelScope.launch {
            try {
                val unsyncedFarmers = repository.getUnsyncedFarmers()
                if (unsyncedFarmers.isNotEmpty()) {
                    _successMessage.value = "Syncing ${unsyncedFarmers.size} unsynced farmers..."
                    
                    // Sync with Firestore
                    repository.syncWithFirestore()
                    
                    // Refresh UI
                    _farmers.value = sortFarmersById(repository.getAllFarmers())
                    
                    _successMessage.value = "Successfully synced ${unsyncedFarmers.size} farmers!"
                } else {
                    _successMessage.value = "All farmers are already synced"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync unsynced farmers: ${e.message}"
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        networkUtils?.stopMonitoring()
    }

    private fun sortFarmersById(list: List<Farmer>): List<Farmer> {
        return list.sortedWith(compareBy(
            { it.id.toIntOrNull() ?: Int.MAX_VALUE },
            { it.id }
        ))
    }
} 