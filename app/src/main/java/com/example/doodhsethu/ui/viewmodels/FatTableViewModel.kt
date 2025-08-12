package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.FatRangeRow
import com.example.doodhsethu.data.repository.FatTableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FatTableViewModel(context: Context) : ViewModel() {
    private val repository = FatTableRepository(context)

    private val _fatTableRows = MutableStateFlow<List<FatRangeRow>>(emptyList())
    val fatTableRows: StateFlow<List<FatRangeRow>> = _fatTableRows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var isInitialized = false

    fun initializeData(isOnline: Boolean) {
        // Prevent multiple initializations
        if (isInitialized && _fatTableRows.value.isNotEmpty()) {
            android.util.Log.d("FatTableViewModel", "Already initialized, skipping...")
            return
        }
        
        // If already loading, don't start another load
        if (_isLoading.value) {
            android.util.Log.d("FatTableViewModel", "Already loading, skipping...")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                android.util.Log.d("FatTableViewModel", "Initializing data, isOnline: $isOnline")
                
                // First, always load from local database
                var rows = repository.getAllFatRows()
                android.util.Log.d("FatTableViewModel", "Loaded ${rows.size} rows from local database")
                
                // If no local data exists, add sample data
                if (rows.isEmpty()) {
                    android.util.Log.d("FatTableViewModel", "No local data found, adding sample data")
                    val sampleData = listOf(
                        FatRangeRow(from = 6.3f, to = 6.5f, price = 49.41),
                        FatRangeRow(from = 6.6f, to = 6.8f, price = 51.84),
                        FatRangeRow(from = 6.9f, to = 7.1f, price = 54.27),
                        FatRangeRow(from = 7.2f, to = 7.4f, price = 56.7),
                        FatRangeRow(from = 7.5f, to = 7.7f, price = 59.13),
                        FatRangeRow(from = 7.8f, to = 8.0f, price = 61.56),
                        FatRangeRow(from = 8.1f, to = 8.3f, price = 63.99),
                        FatRangeRow(from = 8.4f, to = 8.6f, price = 66.42),
                        FatRangeRow(from = 8.7f, to = 8.9f, price = 68.85),
                        FatRangeRow(from = 9.0f, to = 9.2f, price = 71.28),
                        FatRangeRow(from = 9.3f, to = 9.5f, price = 73.71),
                        FatRangeRow(from = 9.6f, to = 9.8f, price = 76.14),
                        FatRangeRow(from = 9.9f, to = 10.1f, price = 78.57),
                        FatRangeRow(from = 10.2f, to = 10.4f, price = 81.0)
                    )
                    repository.insertFatRows(sampleData)
                    rows = repository.getAllFatRows()
                    android.util.Log.d("FatTableViewModel", "After adding sample data: ${rows.size} rows")
                }
                
                // If online, try to sync with Firestore (but don't block if it fails)
                if (isOnline) {
                    try {
                        android.util.Log.d("FatTableViewModel", "Attempting to sync with Firestore")
                        repository.loadFromFirestore(true)
                        // Only reload if sync was successful
                        rows = repository.getAllFatRows()
                        android.util.Log.d("FatTableViewModel", "After Firestore sync: ${rows.size} rows")
                    } catch (e: Exception) {
                        // If sync fails, continue with local data
                        android.util.Log.e("FatTableViewModel", "Firestore sync failed: ${e.message}")
                        _errorMessage.value = "Using local data. Sync failed: ${e.message}"
                    }
                }
                
                android.util.Log.d("FatTableViewModel", "Final rows count: ${rows.size}")
                _fatTableRows.value = rows
                isInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("FatTableViewModel", "Error initializing data: ${e.message}")
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Validate the range doesn't overlap
                if (!validateFatRange(row)) {
                    _errorMessage.value = "This fat range overlaps with an existing range. Please choose a different range."
                    return@launch
                }
                
                // Save locally with isSynced = false
                repository.insertFatRow(row.copy(isSynced = false))
                // Always sync when online, but don't block if offline
                if (isOnline) {
                    try {
                        repository.uploadToFirestore(true)
                    } catch (e: Exception) {
                        // If sync fails, data is still saved locally
                        _errorMessage.value = "Data saved locally. Will sync when online."
                    }
                }
                _fatTableRows.value = repository.getAllFatRows()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Validate the range doesn't overlap (excluding the current row being edited)
                if (!validateFatRange(row, row.id)) {
                    _errorMessage.value = "This fat range overlaps with an existing range. Please choose a different range."
                    return@launch
                }
                
                // Update locally with isSynced = false
                repository.updateFatRow(row.copy(isSynced = false))
                // Always sync when online, but don't block if offline
                if (isOnline) {
                    try {
                        repository.uploadToFirestore(true)
                    } catch (e: Exception) {
                        // If sync fails, data is still saved locally
                        _errorMessage.value = "Data saved locally. Will sync when online."
                    }
                }
                _fatTableRows.value = repository.getAllFatRows()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFatRow(row: FatRangeRow, isOnline: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteFatRow(row)
                // Always sync when online, but don't block if offline
                if (isOnline) {
                    try {
                        repository.uploadToFirestore(true)
                    } catch (e: Exception) {
                        // If sync fails, data is still saved locally
                        _errorMessage.value = "Data saved locally. Will sync when online."
                    }
                }
                _fatTableRows.value = repository.getAllFatRows()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Validate if a fat range overlaps with existing ranges
    fun validateFatRange(newRow: FatRangeRow, excludeId: Int? = null): Boolean {
        val currentRows = _fatTableRows.value
        
        for (existingRow in currentRows) {
            // Skip the row being edited
            if (excludeId != null && existingRow.id == excludeId) {
                continue
            }
            
            // Check for overlap: new range overlaps with existing range
            val overlaps = !(newRow.to <= existingRow.from || newRow.from >= existingRow.to)
            
            if (overlaps) {
                android.util.Log.d("FatTableViewModel", "Range ${newRow.from}-${newRow.to} overlaps with existing range ${existingRow.from}-${existingRow.to}")
                return false
            }
        }
        
        return true
    }
    
    // Sync data when internet becomes available
    fun syncWhenOnline(isOnline: Boolean) {
        if (isOnline) {
            viewModelScope.launch {
                try {
                    repository.uploadToFirestore(true)
                    repository.loadFromFirestore(true)
                    _fatTableRows.value = repository.getAllFatRows()
                } catch (e: Exception) {
                    _errorMessage.value = "Sync failed: ${e.message}"
                }
            }
        }
    }
    
    // Force refresh data
    fun forceRefreshData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                isInitialized = false
                initializeData(true)
            } catch (e: Exception) {
                _errorMessage.value = "Refresh failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
    }
} 