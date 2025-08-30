package com.example.doodhsethu.ui.viewmodels

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
import android.content.Context
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider

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
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    
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
                    // Start real-time sync when online
                    startRealTimeSync()
                    syncLocalWithFirestore()
                } else {
                    // Stop real-time sync when offline
                    stopRealTimeSync()
                }
            }
        }
        
        // Set up callback for data changes
        repository.setOnDataChangedCallback {
            refreshData()
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
                    totalPrice = {
                        // Use rounded values for AM and PM prices to avoid 1 Paisa difference
                        val roundedAmPrice = String.format(Locale.getDefault(), "%.2f", actualData?.amPrice ?: 0.0).toDouble()
                        val roundedPmPrice = String.format(Locale.getDefault(), "%.2f", actualData?.pmPrice ?: 0.0).toDouble()
                        roundedAmPrice + roundedPmPrice
                    }()
                )
            )
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            dayCount++
        }
        
        android.util.Log.d("MilkReportViewModel", "Generated $dayCount days total")
        // For current month, show newest first (reverse order) using parsed dates
        return if (_selectedPeriod.value == ReportPeriod.CURR_MONTH) {
            allEntries.sortedByDescending {
                try { dateFormat.parse(it.date)?.time } catch (_: Exception) { null }
            }
        } else {
            allEntries.sortedBy {
                try { dateFormat.parse(it.date)?.time } catch (_: Exception) { null }
            }
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
    
    /**
     * Start real-time sync
     */
    private fun startRealTimeSync() {
        viewModelScope.launch {
            try {
                repository.startRealTimeSync()
                android.util.Log.d("MilkReportViewModel", "Real-time sync started")
            } catch (e: Exception) {
                android.util.Log.e("MilkReportViewModel", "Error starting real-time sync: ${e.message}")
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
                android.util.Log.d("MilkReportViewModel", "Real-time sync stopped")
            } catch (e: Exception) {
                android.util.Log.e("MilkReportViewModel", "Error stopping real-time sync: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh data when real-time updates are received
     */
    private fun refreshData() {
        viewModelScope.launch {
            try {
                // Reload current report data
                loadReportData()
            } catch (e: Exception) {
                android.util.Log.e("MilkReportViewModel", "Error refreshing data: ${e.message}")
            }
        }
    }
    
    /**
     * Export milk report data to Excel CSV format (non-suspend wrapper)
     */
    fun exportToExcel() {
        viewModelScope.launch {
            exportToExcelSuspend()
        }
    }
    
    /**
     * Show download confirmation dialog
     */
    fun confirmAndExport() {
        showDownloadConfirmation()
    }
    
    /**
     * Export milk report data to Excel CSV format
     */
    private suspend fun exportToExcelSuspend(): String? {
        // Check for storage permission (only needed for Android < 10)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                _errorMessage.value = "Storage permission required to export Excel file"
                return null
            }
        }
        
        _isExporting.value = true
        return try {
            val reportEntries = _reportEntries.value
            if (reportEntries.isEmpty()) {
                _errorMessage.value = "No data available to export"
                return null
            }
            
            // Get all unique farmer IDs from the data
            val allFarmerIds = mutableSetOf<String>()
            val farmerDataMap = mutableMapOf<String, MutableMap<String, Pair<Double, Double>>>() // date -> farmerId -> (milk, amount)
            
            // Collect all farmer data
            for (entry in reportEntries) {
                if (entry.amQuantity > 0 || entry.pmQuantity > 0) {
                    // Get farmer details for this date
                    val farmerDetails = repository.getFarmerMilkDetailsForDateSync(entry.date)
                    for (farmer in farmerDetails) {
                        allFarmerIds.add(farmer.farmerId)
                        if (!farmerDataMap.containsKey(entry.date)) {
                            farmerDataMap[entry.date] = mutableMapOf()
                        }
                        farmerDataMap[entry.date]!![farmer.farmerId] = Pair(farmer.totalMilk, farmer.totalPrice)
                    }
                }
            }
            
            val sortedFarmerIds = allFarmerIds.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            val sortedDates = reportEntries.map { it.date }.sorted()
            
            // Create CSV content
            val csvBuilder = StringBuilder()
            
            // Header row: Date, Farmer1, Farmer2, ...
            csvBuilder.append("Date")
            for (farmerId in sortedFarmerIds) {
                csvBuilder.append(",\"Farmer $farmerId\"")
            }
            csvBuilder.append("\n")
            
                         // Data rows
             for (date in sortedDates) {
                 csvBuilder.append("\"$date\"")
                 for (farmerId in sortedFarmerIds) {
                     val farmerData = farmerDataMap[date]?.get(farmerId)
                     if (farmerData != null) {
                         val (milk, amount) = farmerData
                         csvBuilder.append(",\"${String.format("%.2f", milk)}L (₹${String.format("%.2f", amount)})\"")
                     } else {
                         // Show zero values when no data is present
                         csvBuilder.append(",\"0.00L (₹0.00)\"")
                     }
                 }
                 csvBuilder.append("\n")
             }
            
                         // Save to file with UTF-8 encoding and BOM for Excel compatibility
             val fileName = "milk_report_${_selectedPeriod.value.name.lowercase()}_${System.currentTimeMillis()}.csv"
             val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
             val file = File(downloadsDir, fileName)
             
                           // Write with UTF-8 encoding and BOM for Excel compatibility
              file.outputStream().use { outputStream ->
                  // Write BOM (Byte Order Mark) for UTF-8
                  outputStream.write(0xEF)
                  outputStream.write(0xBB)
                  outputStream.write(0xBF)
                  
                  // Write the CSV content
                  OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                      writer.write(csvBuilder.toString())
                  }
              }
            
                         android.util.Log.d("MilkReportViewModel", "Excel export saved to: ${file.absolutePath}")
             _successMessage.value = "File downloaded successfully"
             
             // Show notification with click to open
             showNotification(file, fileName)
             
             file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("MilkReportViewModel", "Error exporting to Excel: ${e.message}")
            _errorMessage.value = "Failed to export Excel: ${e.message}"
            null
        } finally {
            _isExporting.value = false
        }
    }
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()
    
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    fun showDownloadConfirmation() {
        _showDownloadDialog.value = true
    }
    
    fun hideDownloadDialog() {
        _showDownloadDialog.value = false
    }
    
    /**
     * Show notification with click to open file
     */
    private fun showNotification(file: File, fileName: String) {
        android.util.Log.d("MilkReportViewModel", "Attempting to show notification for file: $fileName")
        
        // Check notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("MilkReportViewModel", "Notification permission not granted")
                return
            }
        }
        
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create notification channel for Android 8.0+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "file_download",
                    "File Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for downloaded files"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create intent to open file
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
                         // Build notification
             val notification = NotificationCompat.Builder(context, "file_download")
                 .setContentTitle("Report Downloaded")
                 .setContentText("Click to see your reports")
                 .setSmallIcon(android.R.drawable.stat_sys_download_done)
                 .setContentIntent(pendingIntent)
                 .setAutoCancel(true)
                 .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                 .build()
            
                         // Show notification
             notificationManager.notify(System.currentTimeMillis().toInt(), notification)
             android.util.Log.d("MilkReportViewModel", "Notification sent successfully")
             
         } catch (e: Exception) {
             android.util.Log.e("MilkReportViewModel", "Error showing notification: ${e.message}")
             e.printStackTrace()
         }
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