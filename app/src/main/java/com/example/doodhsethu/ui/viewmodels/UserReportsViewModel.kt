package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.data.repository.FarmerRepository
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.ui.screens.DailyMilkCollectionData
import com.example.doodhsethu.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UserReportsViewModel(private val context: android.content.Context) : ViewModel() {
    private val repository = DailyMilkCollectionRepository(context.applicationContext)
    private val farmerRepository = FarmerRepository(context.applicationContext)
    private val billingCycleRepository = BillingCycleRepository(context.applicationContext)
    private val networkUtils = NetworkUtils(context.applicationContext)
    
    private val _dailyCollections = MutableStateFlow<List<DailyMilkCollectionData>>(emptyList())
    val dailyCollections: StateFlow<List<DailyMilkCollectionData>> = _dailyCollections.asStateFlow()
    
    private val _farmerName = MutableStateFlow("")
    val farmerName: StateFlow<String> = _farmerName.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    init {
        viewModelScope.launch {
            networkUtils.isOnline.collect { isOnline ->
                _isOnline.value = isOnline
                // Disabled real-time sync to ensure fast local data access
                // Real-time sync causes delays when online
            }
        }
        
        // Set up callback for data changes
        repository.setOnDataChangedCallback {
            refreshData()
        }
        billingCycleRepository.setOnDataChangedCallback {
            refreshData()
        }
    }
    
    /**
     * Load milk collection reports for a specific farmer for the current month
     */
    fun loadUserReports(farmerId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                _farmerName.value = ""
                
                // Get farmer name
                val farmer = farmerRepository.getFarmerById(farmerId)
                if (farmer != null) {
                    _farmerName.value = farmer.name
                } else {
                    _errorMessage.value = "Farmer not found with ID: $farmerId"
                    _isLoading.value = false
                    return@launch
                }
                
                // Always use local storage for fast response
                // Removed Firestore sync to ensure fast local data access
                
                // Get collections for this farmer
                val collections = repository.getAllCollectionsByFarmer(farmerId)
                
                // Get billing cycles to check payment status
                val billingCycles = billingCycleRepository.getAllBillingCycles().first()
                
                // Generate daily collection data for the current month (all days)
                val dailyData = generateDailyCollectionData(collections, billingCycles)
                _dailyCollections.value = dailyData
                _successMessage.value = "Found ${dailyData.size} days with milk collections"
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load reports: ${e.message}"
                android.util.Log.e("UserReportsViewModel", "Error loading reports: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Generate daily collection data from first day of month to today only
     */
    private fun generateDailyCollectionData(
        collections: List<DailyMilkCollection>, 
        billingCycles: List<com.example.doodhsethu.data.models.BillingCycle>
    ): List<DailyMilkCollectionData> {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        // Get start of current month
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStart = calendar.time
        
        // Get today's date (end date)
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 23)
        today.set(Calendar.MINUTE, 59)
        today.set(Calendar.SECOND, 59)
        today.set(Calendar.MILLISECOND, 999)
        val todayDate = today.time
        
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val dailyData = mutableListOf<DailyMilkCollectionData>()
        
        // Create a map of date to collection for faster lookup
        val collectionMap = collections.associateBy { it.date }
        
        // Generate data for each day from first day of month to today
        calendar.time = monthStart
        while (!calendar.time.after(todayDate)) {
            val dateStr = dateFormat.format(calendar.time)
            val collection = collectionMap[dateStr]
            
            // Always add a row for each day, even if no collection
            val paymentStatus = if (collection != null) {
                checkPaymentStatus(calendar.time, billingCycles)
            } else {
                "No Data"
            }
            
            dailyData.add(
                DailyMilkCollectionData(
                    date = dateStr,
                    amMilk = collection?.amMilk ?: 0.0,
                    amFat = collection?.amFat ?: 0.0,
                    amAmount = collection?.amPrice ?: 0.0,
                    pmMilk = collection?.pmMilk ?: 0.0,
                    pmFat = collection?.pmFat ?: 0.0,
                    pmAmount = collection?.pmPrice ?: 0.0,
                    totalAmount = collection?.totalAmount ?: 0.0,
                    paymentStatus = paymentStatus
                )
            )
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Sort by actual date to avoid string-based ordering issues (oldest first)
        return dailyData.sortedBy {
            try {
                dateFormat.parse(it.date)
            } catch (_: Exception) {
                null
            }
        }
    }
    
    /**
     * Check if a given date is within any billing cycle
     */
    private fun checkPaymentStatus(date: Date, billingCycles: List<com.example.doodhsethu.data.models.BillingCycle>): String {
        for (cycle in billingCycles) {
            // Check if the date falls within the billing cycle range (inclusive)
            if (!date.before(cycle.startDate) && !date.after(cycle.endDate)) {
                return "Paid"
            }
        }
        return "Pending"
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
    
    /**
     * Clear all data to reset the screen state
     */
    fun clearData() {
        _dailyCollections.value = emptyList()
        _farmerName.value = ""
        _errorMessage.value = null
        _successMessage.value = null
        _isLoading.value = false
    }
    
    /**
     * Start real-time sync
     */
    private fun startRealTimeSync() {
        viewModelScope.launch {
            try {
                repository.startRealTimeSync()
                billingCycleRepository.startRealTimeSync()
                android.util.Log.d("UserReportsViewModel", "Real-time sync started")
            } catch (e: Exception) {
                android.util.Log.e("UserReportsViewModel", "Error starting real-time sync: ${e.message}")
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
                billingCycleRepository.stopRealTimeSync()
                android.util.Log.d("UserReportsViewModel", "Real-time sync stopped")
            } catch (e: Exception) {
                android.util.Log.e("UserReportsViewModel", "Error stopping real-time sync: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh data when real-time updates are received
     */
    private fun refreshData() {
        viewModelScope.launch {
            try {
                // Get current farmer ID if any
                val currentFarmerId = _farmerName.value.let { name ->
                    if (name.isNotEmpty()) {
                        // Try to find farmer by name
                        val allFarmers = farmerRepository.getAllFarmers()
                        allFarmers.find { it.name == name }?.id
                    } else null
                }
                
                if (currentFarmerId != null) {
                    // Reload data for current farmer
                    loadUserReports(currentFarmerId)
                }
            } catch (e: Exception) {
                android.util.Log.e("UserReportsViewModel", "Error refreshing data: ${e.message}")
            }
        }
    }
}

class UserReportsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserReportsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserReportsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}