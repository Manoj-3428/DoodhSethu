package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.MilkReportEntry
import com.example.doodhsethu.data.models.FarmerMilkDetail

import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

enum class ReportPeriod {
    PREV_MONTH,
    CURR_MONTH,
    CUSTOM
}

class MilkReportViewModel(private val context: Context) : ViewModel() {
    
    private val repository = DailyMilkCollectionRepository(context)
    private val networkUtils = NetworkUtils(context)
    
    private val _reportEntries = MutableStateFlow<List<MilkReportEntry>>(emptyList())
    val reportEntries: StateFlow<List<MilkReportEntry>> = _reportEntries.asStateFlow()
    
    private val _farmerDetails = MutableStateFlow<List<FarmerMilkDetail>>(emptyList())
    val farmerDetails: StateFlow<List<FarmerMilkDetail>> = _farmerDetails.asStateFlow()
    
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()
    

    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _selectedPeriod = MutableStateFlow(ReportPeriod.CURR_MONTH)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()
    
    private val _customStartDate = MutableStateFlow<Date?>(null)
    val customStartDate: StateFlow<Date?> = _customStartDate.asStateFlow()
    
    private val _customEndDate = MutableStateFlow<Date?>(null)
    val customEndDate: StateFlow<Date?> = _customEndDate.asStateFlow()
    
    init {
        networkUtils.startMonitoring()
        viewModelScope.launch {
            networkUtils.isOnline.collect { isOnline ->
                _isOnline.value = isOnline
                if (isOnline) {
                    syncLocalWithFirestore()
                }
            }
        }
    }
    
    fun setPeriod(period: ReportPeriod) {
        _selectedPeriod.value = period
        loadReportData()
    }
    
    fun setCustomDateRange(startDate: Date, endDate: Date) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
        _selectedPeriod.value = ReportPeriod.CUSTOM
        loadReportData()
    }
    
    fun setCustomStartDate(startDate: Date) {
        _customStartDate.value = startDate
        _selectedPeriod.value = ReportPeriod.CUSTOM
        // If both dates are set, load data
        if (_customEndDate.value != null) {
            loadReportData()
        }
    }
    
    fun setCustomEndDate(endDate: Date) {
        _customEndDate.value = endDate
        _selectedPeriod.value = ReportPeriod.CUSTOM
        // If both dates are set, load data
        if (_customStartDate.value != null) {
            loadReportData()
        }
    }
    
    fun loadReportData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                // Clean up orphaned collections first
                repository.cleanupOrphanedCollections()
                
                val (startDate, endDate) = getDateRangeForPeriod()
                
                // Debug logging
                val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                android.util.Log.d("MilkReportViewModel", "Date range: ${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}")
                
                val entries = repository.getMilkReportEntries(startDate, endDate)
                
                // Generate all dates in the range and fill with actual data
                val allDatesEntries = generateAllDatesWithData(startDate, endDate, entries)
                
                android.util.Log.d("MilkReportViewModel", "Generated ${allDatesEntries.size} entries")
                if (allDatesEntries.isNotEmpty()) {
                    android.util.Log.d("MilkReportViewModel", "First date: ${allDatesEntries.first().date}")
                    android.util.Log.d("MilkReportViewModel", "Last date: ${allDatesEntries.last().date}")
                }
                
                _reportEntries.value = allDatesEntries
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load report data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun generateAllDatesWithData(startDate: Date, endDate: Date, actualEntries: List<MilkReportEntry>): List<MilkReportEntry> {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate
        endCalendar.set(Calendar.HOUR_OF_DAY, 23)
        endCalendar.set(Calendar.MINUTE, 59)
        endCalendar.set(Calendar.SECOND, 59)
        endCalendar.set(Calendar.MILLISECOND, 999)
        
        val allEntries = mutableListOf<MilkReportEntry>()
        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        
        // Debug logging
        android.util.Log.d("MilkReportViewModel", "Generating dates from ${dateFormat.format(calendar.time)} to ${dateFormat.format(endCalendar.time)}")
        
        // Create a map of actual data by date for quick lookup
        val actualDataMap = actualEntries.associateBy { it.date }
        
        var dayCount = 0
        val endTime = endCalendar.timeInMillis
        
        // Generate entries for all dates in the range
        while (calendar.timeInMillis <= endTime) {
            val dateStr = dateFormat.format(calendar.time)
            val actualData = actualDataMap[dateStr]
            
            android.util.Log.d("MilkReportViewModel", "Adding date: $dateStr (day $dayCount)")
            
            allEntries.add(
                MilkReportEntry(
                    date = dateStr,
                    amQuantity = actualData?.amQuantity ?: 0.0,
                    pmQuantity = actualData?.pmQuantity ?: 0.0,
                    amFat = actualData?.amFat ?: 0.0,
                    pmFat = actualData?.pmFat ?: 0.0,
                    amPrice = actualData?.amPrice ?: 0.0,
                    pmPrice = actualData?.pmPrice ?: 0.0,
                    totalQuantity = (actualData?.amQuantity ?: 0.0) + (actualData?.pmQuantity ?: 0.0),
                    totalPrice = (actualData?.amPrice ?: 0.0) + (actualData?.pmPrice ?: 0.0)
                )
            )
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            dayCount++
        }
        
        android.util.Log.d("MilkReportViewModel", "Generated $dayCount days total")
        // For current month, show newest first (reverse order)
        return if (_selectedPeriod.value == ReportPeriod.CURR_MONTH) {
            allEntries.sortedByDescending { it.date }
        } else {
            allEntries.sortedBy { it.date }
        }
    }
    
    private fun getDateRangeForPeriod(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        
        when (_selectedPeriod.value) {
            ReportPeriod.PREV_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                endDate.add(Calendar.MONTH, -1)
                endDate.set(Calendar.DAY_OF_MONTH, endDate.getActualMaximum(Calendar.DAY_OF_MONTH))
                endDate.set(Calendar.HOUR_OF_DAY, 23)
                endDate.set(Calendar.MINUTE, 59)
                endDate.set(Calendar.SECOND, 59)
                endDate.set(Calendar.MILLISECOND, 999)
            }
            ReportPeriod.CURR_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                // Explicitly set end date to today
                val today = Calendar.getInstance()
                endDate.set(Calendar.YEAR, today.get(Calendar.YEAR))
                endDate.set(Calendar.MONTH, today.get(Calendar.MONTH))
                endDate.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                endDate.set(Calendar.HOUR_OF_DAY, 23)
                endDate.set(Calendar.MINUTE, 59)
                endDate.set(Calendar.SECOND, 59)
                endDate.set(Calendar.MILLISECOND, 999)
            }
            ReportPeriod.CUSTOM -> {
                val start = _customStartDate.value
                val end = _customEndDate.value
                if (start != null && end != null) {
                    val startCal = Calendar.getInstance().apply {
                        time = start
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val endCal = Calendar.getInstance().apply {
                        time = end
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    return Pair(startCal.time, endCal.time)
                } else {
                    // Fallback to current month if custom dates not set
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    
                    endDate.set(Calendar.DAY_OF_MONTH, endDate.getActualMaximum(Calendar.DAY_OF_MONTH))
                    endDate.set(Calendar.HOUR_OF_DAY, 23)
                    endDate.set(Calendar.MINUTE, 59)
                    endDate.set(Calendar.SECOND, 59)
                    endDate.set(Calendar.MILLISECOND, 999)
                }
            }
        }
        
        // Debug logging for date range
        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        android.util.Log.d("MilkReportViewModel", "getDateRangeForPeriod: ${dateFormat.format(calendar.time)} to ${dateFormat.format(endDate.time)}")
        android.util.Log.d("MilkReportViewModel", "Current date: ${dateFormat.format(Date())}")
        android.util.Log.d("MilkReportViewModel", "Selected period: ${_selectedPeriod.value}")
        
        return Pair(calendar.time, endDate.time)
    }
    
    private suspend fun syncLocalWithFirestore() {
        try {
            repository.syncWithFirestore()
        } catch (e: Exception) {
            // Handle sync errors silently
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Load detailed farmer milk collection data for a specific date
     */
    fun loadFarmerDetailsForDate(date: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                _selectedDate.value = date
                
                val details = repository.getFarmerMilkDetailsForDate(date)
                _farmerDetails.value = details
                
                android.util.Log.d("MilkReportViewModel", "Loaded ${details.size} farmer details for date: $date")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load farmer details: ${e.message}"
                android.util.Log.e("MilkReportViewModel", "Error loading farmer details: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear farmer details (when closing the detail view)
     */
    fun clearFarmerDetails() {
        _farmerDetails.value = emptyList()
        _selectedDate.value = null
    }
}

class MilkReportViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MilkReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MilkReportViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 