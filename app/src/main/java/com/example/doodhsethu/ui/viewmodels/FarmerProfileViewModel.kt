package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.models.FarmerBillingDetail
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.data.repository.BillingCycleSummaryRepository
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.data.repository.FarmerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class FarmerProfileViewModel(context: Context) : ViewModel() {
    
    private val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
    private val billingCycleRepository = BillingCycleRepository(context)
    private val billingCycleSummaryRepository = BillingCycleSummaryRepository(context)
    private val farmerRepository = FarmerRepository(context)
    
    companion object {
        private const val TAG = "FarmerProfileViewModel"
    }
    
    // Cache management
    private var lastLoadedFarmerId: String? = null
    private var lastLoadTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes (increased for better performance)
    
    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _farmer = MutableStateFlow<Farmer?>(null)
    val farmer: StateFlow<Farmer?> = _farmer.asStateFlow()
    
    private val _milkCollections = MutableStateFlow<List<DailyMilkCollection>>(emptyList())
    val milkCollections: StateFlow<List<DailyMilkCollection>> = _milkCollections.asStateFlow()
    
    private val _billingCycles = MutableStateFlow<List<FarmerBillingDetail>>(emptyList())
    val billingCycles: StateFlow<List<FarmerBillingDetail>> = _billingCycles.asStateFlow()
    
    private val _billingCycleMap = MutableStateFlow<Map<String, com.example.doodhsethu.data.models.BillingCycle>>(emptyMap())
    val billingCycleMap: StateFlow<Map<String, com.example.doodhsethu.data.models.BillingCycle>> = _billingCycleMap.asStateFlow()
    
    private val _billingCycleSummaries = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val billingCycleSummaries: StateFlow<List<Map<String, Any>>> = _billingCycleSummaries.asStateFlow()
    
    private val _currentMonthTotal = MutableStateFlow(0.0)
    val currentMonthTotal: StateFlow<Double> = _currentMonthTotal.asStateFlow()
    
    private val _pendingAmount = MutableStateFlow(0.0)
    val pendingAmount: StateFlow<Double> = _pendingAmount.asStateFlow()
    
    private val _paidAmount = MutableStateFlow(0.0)
    val paidAmount: StateFlow<Double> = _paidAmount.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Load farmer profile data with immediate local updates and background Firestore sync
     */
    fun loadFarmerProfile(farmerId: String) {
        viewModelScope.launch {
            try {
                // Check if we can use cached data
                if (lastLoadedFarmerId == farmerId && 
                    (System.currentTimeMillis() - lastLoadTime) < CACHE_DURATION &&
                    _farmer.value != null && _currentMonthTotal.value > 0) {
                    Log.d(TAG, "Using cached data for farmer $farmerId, last load: ${System.currentTimeMillis() - lastLoadTime}ms ago")
                    return@launch
                }
                
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Loading farmer profile for ID: $farmerId")
                
                // Load data from local storage immediately (no Firestore dependency)
                withContext(Dispatchers.IO) {
                    // Load farmer details from local
                    val farmerData = farmerRepository.getFarmerById(farmerId)
                    _farmer.value = farmerData
                    
                    // Load milk collections from local
                    val milkCollectionsData = dailyMilkCollectionRepository.getAllCollectionsByFarmer(farmerId)
                    _milkCollections.value = milkCollectionsData
                    
                    // Clean up any duplicate entries for this farmer
                    dailyMilkCollectionRepository.cleanupDuplicateEntries()
                    
                    // Reload milk collections after cleanup to get the cleaned data
                    val cleanedMilkCollectionsData = dailyMilkCollectionRepository.getAllCollectionsByFarmer(farmerId)
                    _milkCollections.value = cleanedMilkCollectionsData
                    
                    // Calculate current month total from local data (filter by current month)
                    val calendar = Calendar.getInstance()
                    val currentMonth = calendar.get(Calendar.MONTH)
                    val currentYear = calendar.get(Calendar.YEAR)
                    
                    // Get start and end of current month
                    calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val monthStart = calendar.time
                    
                    calendar.add(Calendar.MONTH, 1)
                    calendar.add(Calendar.MILLISECOND, -1)
                    val monthEnd = calendar.time
                    
                    val currentMonthCollections = cleanedMilkCollectionsData.filter { collection ->
                        try {
                            val collectionDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(collection.date)
                            collectionDate != null && collectionDate >= monthStart && collectionDate <= monthEnd
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    val currentMonthEarnings = currentMonthCollections.sumOf { it.totalAmount }
                    _currentMonthTotal.value = currentMonthEarnings
                    
                    Log.d(TAG, "Current month collections: ${currentMonthCollections.size}")
                    currentMonthCollections.forEach { collection ->
                        Log.d(TAG, "  Collection: ${collection.date} - Total: ₹${collection.totalAmount}")
                    }
                    Log.d(TAG, "Current month total earnings: ₹$currentMonthEarnings")
                    
                    Log.d(TAG, "Loaded ${cleanedMilkCollectionsData.size} total milk collections from local (after cleanup), current month total: ₹$currentMonthEarnings")
                    
                    // Load billing cycle details from local
                    val billingCyclesData = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
                    _billingCycles.value = billingCyclesData
                    
                    // Load actual billing cycle objects from local
                    val billingCycleMap = mutableMapOf<String, com.example.doodhsethu.data.models.BillingCycle>()
                    for (billingDetail in billingCyclesData) {
                        val billingCycle = billingCycleRepository.getBillingCycleById(billingDetail.billingCycleId)
                        if (billingCycle != null) {
                            billingCycleMap[billingDetail.billingCycleId] = billingCycle
                        }
                    }
                    _billingCycleMap.value = billingCycleMap
                    
                    // Calculate paid amount from local billing cycle data
                    val totalPaidFromBillingCycles = billingCyclesData.sumOf { it.originalAmount }
                    _paidAmount.value = totalPaidFromBillingCycles
                    
                    // Calculate pending amount
                    val pendingAmount = currentMonthEarnings - totalPaidFromBillingCycles
                    _pendingAmount.value = pendingAmount
                    
                    Log.d(TAG, "Loaded ${billingCyclesData.size} billing cycle details from local")
                    Log.d(TAG, "Paid amount: ₹$totalPaidFromBillingCycles, Pending: ₹$pendingAmount")
                }
                
                // Update cache immediately after local data is loaded
                lastLoadedFarmerId = farmerId
                lastLoadTime = System.currentTimeMillis()
                
                // Load billing cycle summaries from Firestore in background (non-blocking)
                viewModelScope.launch {
                    try {
                        val billingSummaries = billingCycleSummaryRepository.getAllBillingCycleSummaries(farmerId)
                        _billingCycleSummaries.value = billingSummaries
                        Log.d(TAG, "Loaded ${billingSummaries.size} billing cycle summaries from Firestore (background)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Background Firestore sync failed: ${e.message}")
                        // Don't show error to user since local data is already loaded
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading farmer profile: ${e.message}")
                _errorMessage.value = "Error loading farmer profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh farmer profile data (force reload)
     */
    fun refreshFarmerProfile() {
        _farmer.value?.let { farmer ->
            // Clear cache to force reload
            lastLoadedFarmerId = null
            lastLoadTime = 0L
            loadFarmerProfile(farmer.id)
        }
    }
    
    /**
     * Clear cache for a specific farmer
     */
    fun clearCache(farmerId: String? = null) {
        if (farmerId == null || lastLoadedFarmerId == farmerId) {
            lastLoadedFarmerId = null
            lastLoadTime = 0L
            Log.d(TAG, "Cache cleared for farmer: $farmerId")
        }
    }
    
    /**
     * Check if data is cached for a farmer
     */
    fun isDataCached(farmerId: String): Boolean {
        return lastLoadedFarmerId == farmerId && 
               (System.currentTimeMillis() - lastLoadTime) < CACHE_DURATION &&
               _farmer.value != null && _currentMonthTotal.value > 0
    }
    
    /**
     * Add milk collection and update billing cycle summaries immediately
     */
    fun addMilkCollection(
        farmerId: String,
        farmerName: String,
        amMilk: Double,
        amFat: Double,
        pmMilk: Double,
        pmFat: Double
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Adding milk collection for farmer: $farmerName")
                
                // Create milk collection locally first (immediate)
                val milkCollection = dailyMilkCollectionRepository.createTodayCollection(
                    farmerId = farmerId,
                    farmerName = farmerName,
                    amMilk = amMilk,
                    amFat = amFat,
                    pmMilk = pmMilk,
                    pmFat = pmFat
                )
                
                // Update local state immediately
                val currentCollections = _milkCollections.value.toMutableList()
                currentCollections.add(milkCollection)
                _milkCollections.value = currentCollections
                
                // Update current month total immediately
                val newTotal = _currentMonthTotal.value + milkCollection.totalAmount
                _currentMonthTotal.value = newTotal
                
                // Get current paid amount from local billing cycle data
                val currentBillingCycles = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
                val currentPaidAmount = currentBillingCycles.sumOf { it.originalAmount }
                _paidAmount.value = currentPaidAmount
                
                // Update pending amount immediately
                val newPendingAmount = newTotal - currentPaidAmount
                _pendingAmount.value = newPendingAmount
                
                Log.d(TAG, "Added milk collection: ₹${milkCollection.totalAmount}")
                Log.d(TAG, "New total: ₹$newTotal, New pending: ₹$newPendingAmount")
                
                // Update billing cycle summaries in background
                updateBillingCycleSummariesForMilkCollection(farmerId, milkCollection)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding milk collection: ${e.message}")
                _errorMessage.value = "Error adding milk collection: ${e.message}"
            }
        }
    }
    
    /**
     * Update billing cycle summaries when milk collection is added
     */
    private suspend fun updateBillingCycleSummariesForMilkCollection(
        farmerId: String,
        milkCollection: DailyMilkCollection
    ) {
        try {
            // Get all active billing cycles for this farmer
            val billingCycles = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
            
            // Find which billing cycle this milk collection belongs to
            val collectionDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(milkCollection.date)
            
            for (billingCycle in billingCycles) {
                val billingCycleDetails = billingCycleRepository.getBillingCycleById(billingCycle.billingCycleId)
                if (billingCycleDetails != null) {
                    val startDate = billingCycleDetails.startDate
                    val endDate = billingCycleDetails.endDate
                    
                    // Check if collection date falls within this billing cycle
                    if (collectionDate != null && collectionDate >= startDate && collectionDate <= endDate) {
                        // Update the billing cycle summary
                        billingCycleSummaryRepository.updateBillingCycleSummary(
                            billingCycleId = billingCycle.billingCycleId,
                            farmerId = farmerId,
                            milkCollection = milkCollection
                        )
                        
                        Log.d(TAG, "Updated billing cycle summary for cycle: ${billingCycle.billingCycleId}")
                        break
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating billing cycle summaries: ${e.message}")
        }
    }
    
    /**
     * Create billing cycle and initialize summaries for all farmers
     */
    fun createBillingCycle(
        startDate: Date,
        endDate: Date
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Creating billing cycle from ${startDate} to ${endDate}")
                
                // Create billing cycle locally first
                val billingCycle = billingCycleRepository.createBillingCycle(
                    startDate = startDate,
                    endDate = endDate
                )
                
                // Initialize billing cycle summaries for all farmers
                val allFarmers = farmerRepository.getAllFarmers()
                for (farmer in allFarmers) {
                    billingCycleSummaryRepository.initializeBillingCycleSummary(
                        billingCycleId = billingCycle.id,
                        farmerId = farmer.id,
                        farmerName = farmer.name
                    )
                }
                
                Log.d(TAG, "Created billing cycle and initialized summaries for ${allFarmers.size} farmers")
                
                // Refresh current farmer profile if we're viewing one
                _farmer.value?.let { farmer ->
                    loadFarmerProfile(farmer.id)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating billing cycle: ${e.message}")
                _errorMessage.value = "Error creating billing cycle: ${e.message}"
            }
        }
    }
    
    /**
     * Delete billing cycle and remove summaries
     */
    fun deleteBillingCycle(billingCycleId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting billing cycle: $billingCycleId")
                
                // Get billing cycle first
                val billingCycle = billingCycleRepository.getBillingCycleById(billingCycleId)
                if (billingCycle == null) {
                    Log.e(TAG, "Billing cycle not found: $billingCycleId")
                    return@launch
                }
                
                // Get all farmers to delete their summaries
                val allFarmers = farmerRepository.getAllFarmers()
                
                // Delete billing cycle locally first
                billingCycleRepository.deleteBillingCycle(billingCycle)
                
                // Delete billing cycle summaries for all farmers
                for (farmer in allFarmers) {
                    billingCycleSummaryRepository.deleteBillingCycleSummary(
                        billingCycleId = billingCycleId,
                        farmerId = farmer.id
                    )
                }
                
                Log.d(TAG, "Deleted billing cycle and summaries for ${allFarmers.size} farmers")
                
                // Refresh current farmer profile if we're viewing one
                _farmer.value?.let { farmer ->
                    loadFarmerProfile(farmer.id)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting billing cycle: ${e.message}")
                _errorMessage.value = "Error deleting billing cycle: ${e.message}"
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Manually sync billing cycle summaries to Firestore (for debugging)
     */
    fun syncBillingCycleSummariesToFirestore() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Manually syncing billing cycle summaries to Firestore")
                
                // Get all billing cycles from local database
                val billingCycles = billingCycleRepository.getAllBillingCycles().first()
                Log.d(TAG, "Found ${billingCycles.size} billing cycles in local database")
                
                for (billingCycle in billingCycles) {
                    // Get farmer details for this billing cycle
                    val farmerDetails = billingCycleRepository.getFarmerBillingDetailsByBillingCycleId(billingCycle.id)
                    Log.d(TAG, "Billing cycle ${billingCycle.id}: ${farmerDetails.size} farmers")
                    
                    for (farmerDetail in farmerDetails) {
                        Log.d(TAG, "Initializing summary for farmer ${farmerDetail.farmerId} with amount ₹${farmerDetail.originalAmount}")
                        billingCycleSummaryRepository.initializeBillingCycleSummary(
                            billingCycleId = billingCycle.id,
                            farmerId = farmerDetail.farmerId,
                            farmerName = farmerDetail.farmerName,
                            totalAmount = farmerDetail.originalAmount,
                            totalMilk = 0.0,
                            totalFat = 0.0
                        )
                    }
                }
                
                Log.d(TAG, "Completed manual sync of billing cycle summaries")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing billing cycle summaries: ${e.message}")
                _errorMessage.value = "Error syncing billing cycle summaries: ${e.message}"
            }
        }
    }
} 