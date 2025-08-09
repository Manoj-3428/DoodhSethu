package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import com.example.doodhsethu.utils.AutoSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DailyMilkCollectionViewModel(private val context: Context) : ViewModel() {
    private val repository = DailyMilkCollectionRepository(context.applicationContext)
    private val autoSyncManager = AutoSyncManager(context.applicationContext)
    
    private val _todayCollections = MutableStateFlow<List<DailyMilkCollection>>(emptyList())
    val todayCollections: StateFlow<List<DailyMilkCollection>> = _todayCollections.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadTodayCollections()
    }

    /**
     * Load today's milk collections
     */
    fun loadTodayCollections() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Ensure today's collection exists for all farmers
                val farmerRepository = com.example.doodhsethu.data.repository.FarmerRepository(context.applicationContext)
                val farmers = farmerRepository.getAllFarmers()
                for (farmer in farmers) {
                    repository.createTodayCollection(
                        farmerId = farmer.id,
                        farmerName = farmer.name
                    )
                }
                
                // Load today's collections
                val collections = repository.getTodayCollections()
                _todayCollections.value = collections
                
                android.util.Log.d("DailyMilkCollectionViewModel", "Loaded ${collections.size} collections for today")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load today's collections: ${e.message}"
                android.util.Log.e("DailyMilkCollectionViewModel", "Error loading collections: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update milk collection for a farmer
     */
    fun updateMilkCollection(
        farmerId: String,
        session: String, // "AM" or "PM"
        milk: Double,
        fat: Double,
        price: Double
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Update local storage first (immediate response for user)
                repository.updateMilkCollection(farmerId, session, milk, fat, price)
                
                // Set loading to false immediately after local save
                _isLoading.value = false
                
                // Show success message immediately
                _successMessage.value = "Milk collection updated successfully!"
                android.util.Log.d("DailyMilkCollectionViewModel", "Updated milk collection for farmer $farmerId, session $session")
                
                // Reload collections in background
                viewModelScope.launch {
                    loadTodayCollections()
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = "Failed to update milk collection: ${e.message}"
                android.util.Log.e("DailyMilkCollectionViewModel", "Error updating collection: ${e.message}")
            }
        }
    }



    /**
     * Sync with Firestore
     */
    fun syncWithFirestore() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.syncWithFirestore()
                _successMessage.value = "Successfully synced with Firestore"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync with Firestore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load from Firestore
     */
    fun loadFromFirestore() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.loadFromFirestore()
                loadTodayCollections() // Reload after loading from Firestore
                _successMessage.value = "Successfully loaded from Firestore"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load from Firestore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get today's date in the required format
     */
    fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Manual sync all data
     */
    fun manualSync() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                autoSyncManager.startAutoSync()
                _successMessage.value = "Manual sync completed successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to perform manual sync: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }
}

class DailyMilkCollectionViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyMilkCollectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DailyMilkCollectionViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}