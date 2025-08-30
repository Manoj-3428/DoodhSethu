package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.FatRangeRow
import com.example.doodhsethu.data.repository.FatTableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.doodhsethu.utils.FatTableUtils
import kotlin.math.roundToInt

class FatTableViewModel(context: Context) : ViewModel() {
    private val repository = FatTableRepository(context)
    
    init {
        // Set up callback for real-time updates
        repository.setOnDataChangedCallback {
            refreshUI()
        }
    }
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _fatTableRows = MutableStateFlow<List<FatRangeRow>>(emptyList())
    val fatTableRows: StateFlow<List<FatRangeRow>> = _fatTableRows.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    private var isInitialized = false

    /**
     * Initialize fat table data
     * Priority: Local storage first, then Firestore if online
     */
    fun initializeData(isOnline: Boolean) {
        if (isInitialized && _fatTableRows.value.isNotEmpty()) {
            return
        }
        
        if (_isLoading.value) {
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Always load from local storage first
                var rows = repository.getAllFatRows()
                
                // If online, handle offline-to-online transition properly
                if (isOnline) {
                    try {
                        // First, handle any offline entries that need to be synced
                        repository.handleOfflineToOnlineSync()
                        
                        // Then do a complete sync
                        repository.syncWithFirestore()
                        
                        // Start real-time sync for immediate updates
                        repository.startRealTimeSync()
                        
                        rows = repository.getAllFatRows()
                    } catch (e: Exception) {
                        // Continue with local data if Firestore sync fails
                        _errorMessage.value = "Using local data. Sync failed: ${e.message}"
                    }
                } else {
                    // Stop real-time sync if offline
                    repository.stopRealTimeSync()
                }
                
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                isInitialized = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load fat table: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Add new fat range entry
     * 1. Validate no overlap with existing ranges
     * 2. Save to local storage immediately
     * 3. Sync to Firestore in background if online
     */
    fun addFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                // Round the float values to 3 decimal places to avoid precision issues
                val roundedRow = row.copy(
                    from = (row.from * 1000).roundToInt() / 1000f,
                    to = (row.to * 1000).roundToInt() / 1000f
                )
                
                // Use repository method that handles validation and sync
                val success = repository.addFatRowWithSync(roundedRow, isOnline)
                
                if (success) {
                    // Update UI immediately without showing loader
                    _fatTableRows.value = FatTableUtils.sortFatRanges(repository.getAllFatRows())
                    _successMessage.value = "Fat range added successfully!"
                } else {
                    _errorMessage.value = "This fat range overlaps with an existing range. Please choose a different range."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add fat range: ${e.message}"
            }
        }
    }

    /**
     * Update existing fat range entry
     * 1. Validate no overlap with existing ranges (excluding current entry)
     * 2. Update local storage immediately
     * 3. Sync to Firestore in background if online
     */
    fun updateFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                // Round the float values to 3 decimal places to avoid precision issues
                val roundedRow = row.copy(
                    from = (row.from * 1000).roundToInt() / 1000f,
                    to = (row.to * 1000).roundToInt() / 1000f
                )
                
                // Use repository method that handles validation and sync
                val success = repository.updateFatRowWithSync(roundedRow, isOnline)
                
                if (success) {
                    // Update UI immediately without showing loader
                    _fatTableRows.value = FatTableUtils.sortFatRanges(repository.getAllFatRows())
                    _successMessage.value = "Fat range updated successfully!"
                } else {
                    _errorMessage.value = "This fat range overlaps with an existing range. Please choose a different range."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update fat range: ${e.message}"
            }
        }
    }

    /**
     * Delete fat range entry
     * 1. Delete from local storage immediately
     * 2. Delete from Firestore in background if online
     */
    fun deleteFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                // Use repository method that handles sync
                val success = repository.deleteFatRowWithSync(row, isOnline)
                
                if (success) {
                    // Update UI immediately without showing loader
                    _fatTableRows.value = FatTableUtils.sortFatRanges(repository.getAllFatRows())
                    _successMessage.value = "Fat range deleted successfully!"
                } else {
                    _errorMessage.value = "Failed to delete fat range"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete fat range: ${e.message}"
            }
        }
    }

    /**
     * Refresh data from Firestore (manual sync)
     */
    fun refreshData(isOnline: Boolean) {
        if (!isOnline) {
            _errorMessage.value = "Cannot refresh: No internet connection"
            return
        }
        
        viewModelScope.launch {
            try {
                // First, handle any offline entries that need to be synced
                repository.handleOfflineToOnlineSync()
                
                // Then do a complete sync
                repository.syncWithFirestore()
                
                _fatTableRows.value = FatTableUtils.sortFatRanges(repository.getAllFatRows())
                _successMessage.value = "Data refreshed successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh data: ${e.message}"
            }
        }
    }
    
    /**
     * Clean up duplicate entries
     */
    fun cleanupDuplicates() {
        viewModelScope.launch {
            try {
                repository.cleanupDuplicateEntries()
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "Duplicate entries cleaned up successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cleanup duplicates: ${e.message}"
            }
        }
    }
    
    /**
     * Force cleanup all duplicates (both local and Firestore)
     */
    fun forceCleanupAllDuplicates() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.forceCleanupAllDuplicates()
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "All duplicates cleaned up successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cleanup all duplicates: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Emergency cleanup - removes all duplicates and resets sync state
     */
    fun emergencyCleanup() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Force cleanup all duplicates
                repository.forceCleanupAllDuplicates()
                
                // Mark all local entries as synced to prevent re-upload
                val allRows = repository.getAllFatRows()
                if (allRows.isNotEmpty()) {
                    repository.markFatRowsAsSynced(allRows.map { it.id })
                }
                
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "Emergency cleanup completed successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to perform emergency cleanup: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Fix precision issues in Firestore
     */
    fun fixPrecisionIssues() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Force cleanup which includes precision fix
                repository.forceCleanupAllDuplicates()
                
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "Precision issues fixed successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fix precision issues: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Force fix all precision issues in Firestore immediately
     */
    fun forceFixPrecision() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Force cleanup which includes precision fix
                repository.forceCleanupAllDuplicates()
                
                // Also sync with Firestore to ensure all entries are properly formatted
                repository.syncWithFirestore()
                
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "All precision issues fixed and synced successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fix precision issues: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get price for a given fat percentage
     * @param fatPercentage The fat percentage to get price for
     * @return The price per liter for the given fat percentage, or 0.0 if no matching range
     */
    fun getPriceForFat(fatPercentage: Double): Double {
        val rows = _fatTableRows.value
        
        // Find matching fat range
        val matchingRow = rows.find { fatPercentage >= it.from && fatPercentage <= it.to }
        
        return matchingRow?.price ?: 0.0
    }

    /**
     * Clear error and success messages
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Force refresh data (for testing or manual sync)
     */
    fun forceRefresh(isOnline: Boolean) {
        isInitialized = false
        initializeData(isOnline)
    }
    
    /**
     * Refresh UI with latest data from local storage
     * This is called when real-time updates occur
     */
    fun refreshUI() {
        viewModelScope.launch {
            try {
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
            } catch (e: Exception) {
                android.util.Log.e("FatTableViewModel", "Error refreshing UI: ${e.message}")
            }
        }
    }
    
    /**
     * Handle offline-to-online transition
     * This method is called when the app detects a network change from offline to online
     */
    fun handleOfflineToOnlineTransition() {
        viewModelScope.launch {
            try {
                // Handle any offline entries that need to be synced (background operation)
                repository.handleOfflineToOnlineSync()
                
                // Start real-time sync for immediate updates
                repository.startRealTimeSync()
                
                // Then refresh the UI without showing loader
                val rows = repository.getAllFatRows()
                _fatTableRows.value = FatTableUtils.sortFatRanges(rows)
                _successMessage.value = "Offline changes synced successfully!"
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync offline changes: ${e.message}"
            }
        }
    }
} 