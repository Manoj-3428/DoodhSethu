package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.FarmerMilkDetail
import com.example.doodhsethu.data.repository.DailyMilkCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FarmerDetailsViewModel(private val context: Context) : ViewModel() {
    
    private val repository = DailyMilkCollectionRepository(context)
    
    private val _farmerDetails = MutableStateFlow<List<FarmerMilkDetail>>(emptyList())
    val farmerDetails: StateFlow<List<FarmerMilkDetail>> = _farmerDetails.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadFarmerDetailsForDate(date: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val details = repository.getFarmerMilkDetailsForDate(date)
                _farmerDetails.value = details
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load farmer details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

class FarmerDetailsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FarmerDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FarmerDetailsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
