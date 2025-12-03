package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.*
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.data.repository.FarmerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class BillingCycleFarmerEntry(
    val farmerId: String,
    val farmerName: String,
    val totalAmMilk: Double,
    val totalPmMilk: Double,
    val totalAmPrice: Double,
    val totalPmPrice: Double,
    val totalMilk: Double,
    val totalAmount: Double,
    val transactionCount: Int,
    val avgAmFat: Double = 0.0,
    val avgPmFat: Double = 0.0
)

class BillingCycleDetailsViewModel(context: Context) : ViewModel() {
    
    private val billingCycleRepository = BillingCycleRepository(context)
    private val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
    private val farmerRepository = FarmerRepository(context)
    
    companion object {
        private const val TAG = "BillingCycleDetailsViewModel"
    }
    
    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _billingCycle = MutableStateFlow<BillingCycle?>(null)
    val billingCycle: StateFlow<BillingCycle?> = _billingCycle.asStateFlow()
    
    private val _farmerEntries = MutableStateFlow<List<BillingCycleFarmerEntry>>(emptyList())
    val farmerEntries: StateFlow<List<BillingCycleFarmerEntry>> = _farmerEntries.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()
    
    private val _totalMilk = MutableStateFlow(0.0)
    val totalMilk: StateFlow<Double> = _totalMilk.asStateFlow()
    
    /**
     * Load billing cycle details and farmer transactions
     */
    fun loadBillingCycleDetails(billingCycleId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Loading billing cycle details for ID: $billingCycleId")
                
                // Load billing cycle
                val billingCycle = billingCycleRepository.getBillingCycleById(billingCycleId)
                if (billingCycle == null) {
                    _errorMessage.value = "Billing cycle not found"
                    return@launch
                }
                _billingCycle.value = billingCycle
                
                // Load farmer transactions for this billing cycle period
                loadFarmerTransactions(billingCycle.startDate, billingCycle.endDate)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading billing cycle details: ${e.message}")
                _errorMessage.value = "Error loading billing cycle details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load farmer transactions for the billing cycle period
     */
    private suspend fun loadFarmerTransactions(startDate: Date, endDate: Date) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading farmer transactions from ${startDate} to ${endDate}")
                
                // Get all farmers
                val allFarmers = farmerRepository.getAllFarmers()
                val farmerTransactions = mutableListOf<BillingCycleFarmerEntry>()
                
                var totalCycleAmount = 0.0
                var totalCycleMilk = 0.0
                
                // Process each farmer
                for (farmer in allFarmers) {
                    val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(
                        farmer.id, startDate, endDate
                    )
                    
                    if (collections.isNotEmpty()) {
                        // Calculate average fat content
                        val amCollections = collections.filter { it.amMilk > 0 }
                        val pmCollections = collections.filter { it.pmMilk > 0 }
                        
                        val avgAmFat = if (amCollections.isNotEmpty()) {
                            amCollections.map { it.amFat }.average()
                        } else 0.0
                        
                        val avgPmFat = if (pmCollections.isNotEmpty()) {
                            pmCollections.map { it.pmFat }.average()
                        } else 0.0
                        
                        val farmerEntry = BillingCycleFarmerEntry(
                            farmerId = farmer.id,
                            farmerName = farmer.name,
                            totalAmMilk = collections.sumOf { it.amMilk },
                            totalPmMilk = collections.sumOf { it.pmMilk },
                            totalAmPrice = collections.sumOf { it.amPrice },
                            totalPmPrice = collections.sumOf { it.pmPrice },
                            totalMilk = collections.sumOf { it.totalMilk },
                            totalAmount = collections.sumOf { it.totalAmount },
                            transactionCount = collections.size,
                            avgAmFat = avgAmFat,
                            avgPmFat = avgPmFat
                        )
                        
                        farmerTransactions.add(farmerEntry)
                        totalCycleAmount += farmerEntry.totalAmount
                        totalCycleMilk += farmerEntry.totalMilk
                    }
                }
                
                // Sort farmers by ID (numeric order)
                farmerTransactions.sortBy { 
                    try {
                        it.farmerId.toInt()
                    } catch (e: NumberFormatException) {
                        Int.MAX_VALUE // Put non-numeric IDs at the end
                    }
                }
                
                _farmerEntries.value = farmerTransactions
                _totalAmount.value = totalCycleAmount
                _totalMilk.value = totalCycleMilk
                
                Log.d(TAG, "Loaded ${farmerTransactions.size} farmer transactions")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading farmer transactions: ${e.message}")
                _errorMessage.value = "Error loading farmer transactions: ${e.message}"
            }
        }
    }
    
    /**
     * Get daily milk collection data for a specific farmer and billing cycle period
     */
    suspend fun getFarmerDailyCollections(farmerId: String, startDate: Date, endDate: Date): List<DailyMilkCollection> {
        return withContext(Dispatchers.IO) {
            try {
                dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(farmerId, startDate, endDate)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting farmer daily collections: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
