package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.BillingCycle
import com.example.doodhsethu.data.models.FarmerBillingDetail
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.utils.FarmerProfileCalculator
import com.example.doodhsethu.utils.TestDataCreator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel

class BillingCycleViewModel(private val context: Context) : ViewModel() {
    private val repository = BillingCycleRepository(context)
    private val farmerProfileCalculator = FarmerProfileCalculator(context)
    
    // Cache management
    private var isInitialized = false
    private var lastLoadTime = 0L
    private val CACHE_DURATION = 2 * 60 * 1000L // 2 minutes
    
    private val _billingCycles = MutableStateFlow<List<BillingCycle>>(emptyList())
    val billingCycles: StateFlow<List<BillingCycle>> = _billingCycles.asStateFlow()
    
    private val _farmerDetailsMap = MutableStateFlow<Map<String, List<FarmerBillingDetail>>>(emptyMap())
    val farmerDetailsMap: StateFlow<Map<String, List<FarmerBillingDetail>>> = _farmerDetailsMap.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadBillingCycles()
        
        // Set up callback for data changes
        repository.setOnDataChangedCallback {
            refreshData()
        }
        
        // Start real-time sync
        startRealTimeSync()
    }

    // Load billing cycles with automatic sync
    private fun loadBillingCycles() {
        viewModelScope.launch {
            try {
                // Check cache first
                if (isInitialized && 
                    (System.currentTimeMillis() - lastLoadTime) < CACHE_DURATION &&
                    _billingCycles.value.isNotEmpty()) {
                    android.util.Log.d("BillingCycleViewModel", "Using cached data, last load: ${System.currentTimeMillis() - lastLoadTime}ms ago")
                    return@launch
                }
                
                // Check if it's the first day of the month
                val calendar = Calendar.getInstance()
                val isFirstDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH) == 1
                
                if (isFirstDayOfMonth) {
                    // Reset everything to 0 on 1st day of month
                    _billingCycles.value = emptyList()
                    _farmerDetailsMap.value = emptyMap()
                    isInitialized = true
                    lastLoadTime = System.currentTimeMillis()
                } else {
                    // Show loading indicator for initial data load
                    _isLoading.value = true
                    
                    // Add timeout to prevent loading state from getting stuck
                    val timeoutJob = viewModelScope.launch {
                        delay(5000) // 5 second timeout
                        if (_isLoading.value) {
                            android.util.Log.w("BillingCycleViewModel", "Loading timeout reached, forcing loading state to false")
                            _isLoading.value = false
                        }
                    }
                    
                    // Clean up any duplicate data first (quick operation)
                    try {
                        repository.cleanupDuplicateData()
                        android.util.Log.d("BillingCycleViewModel", "Cleaned up duplicate data")
                        
                        // If we still have multiple billing cycles, use force cleanup
                        val cyclesAfterCleanup = repository.getAllBillingCycles().first()
                        if (cyclesAfterCleanup.size > 1) {
                            android.util.Log.d("BillingCycleViewModel", "Multiple billing cycles detected after cleanup, using force cleanup")
                            repository.forceCleanupAllDuplicates()
                            android.util.Log.d("BillingCycleViewModel", "Force cleanup completed")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BillingCycleViewModel", "Failed to cleanup duplicate data: ${e.message}")
                    }
                    
                    // Load data from local storage immediately (fast)
                    android.util.Log.d("BillingCycleViewModel", "Loading billing cycles from local storage")
                    
                    // Use first() to get the current value immediately instead of collecting
                    val cycles = repository.getAllBillingCycles().first()
                    _billingCycles.value = cycles
                    android.util.Log.d("BillingCycleViewModel", "Loaded ${cycles.size} billing cycles from local storage")
                    
                    // Load farmer details for each billing cycle
                    loadFarmerDetailsForCycles(cycles)
                    
                    // Update cache immediately after local data is loaded
                    isInitialized = true
                    lastLoadTime = System.currentTimeMillis()
                    
                    // Cancel timeout and hide loading indicator after local data is loaded
                    timeoutJob.cancel()
                    _isLoading.value = false
                    
                    // Ensure we have data, if not, try to load from Firestore
                    if (cycles.isEmpty()) {
                        android.util.Log.d("BillingCycleViewModel", "No local billing cycles found, attempting to load from Firestore")
                        viewModelScope.launch {
                            try {
                                repository.loadFromFirestore()
                                val firestoreCycles = repository.getAllBillingCycles().first()
                                _billingCycles.value = firestoreCycles
                                loadFarmerDetailsForCycles(firestoreCycles)
                                android.util.Log.d("BillingCycleViewModel", "Loaded ${firestoreCycles.size} billing cycles from Firestore")
                            } catch (e: Exception) {
                                android.util.Log.e("BillingCycleViewModel", "Failed to load from Firestore: ${e.message}")
                            }
                        }
                    }
                    
                    // Start collecting Flow for real-time updates
                    viewModelScope.launch {
                        repository.getAllBillingCycles().collect { updatedCycles ->
                            if (updatedCycles != _billingCycles.value) {
                                _billingCycles.value = updatedCycles
                                android.util.Log.d("BillingCycleViewModel", "Updated billing cycles: ${updatedCycles.size}")
                                loadFarmerDetailsForCycles(updatedCycles)
                            }
                        }
                    }
                    
                    // Sync data when online in background (non-blocking)
                    viewModelScope.launch {
                        try {
                            android.util.Log.d("BillingCycleViewModel", "Starting background Firestore sync")
                            repository.syncAllDataWhenOnline()
                            android.util.Log.d("BillingCycleViewModel", "Background Firestore sync completed")
                        } catch (e: Exception) {
                            android.util.Log.e("BillingCycleViewModel", "Background Firestore sync failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load billing cycles: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun loadFarmerDetailsForCycles(cycles: List<BillingCycle>) {
        val detailsMap = mutableMapOf<String, List<FarmerBillingDetail>>()
        
        android.util.Log.d("BillingCycleViewModel", "Loading farmer details for ${cycles.size} billing cycles")
        
        for (cycle in cycles) {
            try {
                // Get farmer details for this billing cycle from local database
                val farmerDetails = repository.getFarmerBillingDetailsByBillingCycleId(cycle.id)
                
                detailsMap[cycle.id] = farmerDetails
                android.util.Log.d("BillingCycleViewModel", "Cycle ${cycle.id}: ${farmerDetails.size} farmers, total: ₹${cycle.totalAmount}")
                
                // Log each farmer detail for debugging
                farmerDetails.forEach { detail ->
                    android.util.Log.d("BillingCycleViewModel", "  - ${detail.farmerName}: ₹${detail.originalAmount}")
                }
            } catch (e: Exception) {
                android.util.Log.e("BillingCycleViewModel", "Error loading farmer details for cycle ${cycle.id}: ${e.message}")
                detailsMap[cycle.id] = emptyList()
            }
        }
        
        android.util.Log.d("BillingCycleViewModel", "Total farmer details loaded: ${detailsMap.values.sumOf { it.size }}")
        _farmerDetailsMap.value = detailsMap
    }

    // Create a new billing cycle
    fun createBillingCycle(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = ""
                
                val billingCycle = repository.createBillingCycle(startDate, endDate)
                _successMessage.value = "Billing cycle created successfully!"
                
                // Force reload billing cycles immediately
                android.util.Log.d("BillingCycleViewModel", "Forcing reload of billing cycles")
                val cycles = repository.getAllBillingCycles().first()
                _billingCycles.value = cycles
                
                // Load farmer details for the updated cycles
                loadFarmerDetailsForCycles(cycles)
                
                android.util.Log.d("BillingCycleViewModel", "Reloaded ${cycles.size} billing cycles")
                
                // Load farmer details again to ensure UI is updated
                loadFarmerDetailsForCycles(cycles)
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create billing cycle: ${e.message}"
                android.util.Log.e("BillingCycleViewModel", "Error creating billing cycle: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Sync data to Firestore when online
    fun syncToFirestore() {
        viewModelScope.launch {
            try {
                repository.syncAllDataWhenOnline()
                _successMessage.value = "Successfully synced to Firestore"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync to Firestore: ${e.message}"
            }
        }
    }

    fun deleteBillingCycle(billingCycle: BillingCycle) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                android.util.Log.d("BillingCycleViewModel", "Starting deletion of billing cycle: ${billingCycle.id} - ${billingCycle.name}")
                
                // Add timeout to prevent hanging
                val timeoutJob = viewModelScope.launch {
                    delay(10000) // 10 second timeout
                    if (_isLoading.value) {
                        android.util.Log.w("BillingCycleViewModel", "Deletion timeout reached, forcing loading state to false")
                        _isLoading.value = false
                        _errorMessage.value = "Deletion timed out. Please try again."
                    }
                }
                
                repository.deleteBillingCycle(billingCycle)
                android.util.Log.d("BillingCycleViewModel", "Successfully deleted billing cycle from repository")
                
                // Cancel timeout since operation completed
                timeoutJob.cancel()
                
                // Update affected farmer profiles after billing cycle deletion
                android.util.Log.d("BillingCycleViewModel", "Updating farmer profiles after billing cycle deletion")
                try {
                    farmerProfileCalculator.onBillingCycleDeleted(billingCycle.id)
                    android.util.Log.d("BillingCycleViewModel", "Successfully updated farmer profiles after billing cycle deletion")
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleViewModel", "Failed to update farmer profiles after billing cycle deletion: ${e.message}")
                }
                
                _successMessage.value = "Billing cycle deleted successfully!"
                android.util.Log.d("BillingCycleViewModel", "Billing cycle deleted successfully, attempting to sync to Firestore")
                
                // Automatically sync to Firestore after deletion
                try {
                    repository.syncAllDataWhenOnline()
                    _successMessage.value = "Billing cycle deleted and synced to Firestore successfully!"
                    android.util.Log.d("BillingCycleViewModel", "Successfully synced billing cycle deletion to Firestore")
                } catch (e: Exception) {
                    _successMessage.value = "Billing cycle deleted! Will sync to Firestore when online."
                    android.util.Log.e("BillingCycleViewModel", "Failed to sync after deletion: ${e.message}")
                }
                loadBillingCycles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete billing cycle: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }
    
    // Clean up duplicate data manually
    fun cleanupDuplicateData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.cleanupDuplicateData()
                _successMessage.value = "Duplicate data cleaned up successfully!"
                
                // Reload billing cycles after cleanup
                loadBillingCycles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cleanup duplicate data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Force cleanup all duplicates (more aggressive)
    fun forceCleanupAllDuplicates() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.forceCleanupAllDuplicates()
                _successMessage.value = "All duplicates force cleaned up successfully!"
                
                // Reload billing cycles after cleanup
                loadBillingCycles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to force cleanup duplicates: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Manual refresh method
    fun refreshBillingCycles() {
        viewModelScope.launch {
            try {
                // Clear cache to force reload
                isInitialized = false
                lastLoadTime = 0L
                loadBillingCycles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh billing cycles: ${e.message}"
            }
        }
    }
    
    // Clear cache manually
    fun clearCache() {
        isInitialized = false
        lastLoadTime = 0L
        android.util.Log.d("BillingCycleViewModel", "Cache cleared manually")
    }
    
    /**
     * Start real-time sync
     */
    private fun startRealTimeSync() {
        viewModelScope.launch {
            try {
                repository.startRealTimeSync()
                android.util.Log.d("BillingCycleViewModel", "Real-time sync started")
            } catch (e: Exception) {
                android.util.Log.e("BillingCycleViewModel", "Error starting real-time sync: ${e.message}")
            }
        }
    }
    
    /**
     * Stop real-time sync
     */
    private fun stopRealTimeSync() {
        viewModelScope.launch {
            try {
                repository.stopRealTimeSync()
                android.util.Log.d("BillingCycleViewModel", "Real-time sync stopped")
            } catch (e: Exception) {
                android.util.Log.e("BillingCycleViewModel", "Error stopping real-time sync: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh data when real-time updates are received
     */
    private fun refreshData() {
        viewModelScope.launch {
            try {
                android.util.Log.d("BillingCycleViewModel", "Refreshing data from real-time sync")
                
                // Get the latest data directly from the repository
                val updatedCycles = repository.getAllBillingCycles().first()
                _billingCycles.value = updatedCycles
                
                // Load farmer details for the updated cycles
                loadFarmerDetailsForCycles(updatedCycles)
                
                android.util.Log.d("BillingCycleViewModel", "Data refreshed from real-time sync: ${updatedCycles.size} cycles")
            } catch (e: Exception) {
                android.util.Log.e("BillingCycleViewModel", "Error refreshing data: ${e.message}")
            }
        }
    }
    
    /**
     * Create sample test data for development/testing
     */
    fun createSampleData() {
        viewModelScope.launch {
            try {
                android.util.Log.d("BillingCycleViewModel", "Creating sample test data...")
                TestDataCreator.createSampleData(context)
                // Refresh data after creating sample data
                delay(1000) // Wait a bit for data to be created
                loadBillingCycles()
                android.util.Log.d("BillingCycleViewModel", "Sample data created and refreshed")
            } catch (e: Exception) {
                android.util.Log.e("BillingCycleViewModel", "Error creating sample data: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopRealTimeSync()
        android.util.Log.d("BillingCycleViewModel", "ViewModel cleared, real-time sync stopped")
    }
}

class BillingCycleViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BillingCycleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BillingCycleViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 