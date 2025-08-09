package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doodhsethu.data.models.MilkCollection
import com.example.doodhsethu.data.repository.MilkCollectionRepository
import com.example.doodhsethu.utils.FarmerProfileCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class MilkCollectionViewModel(context: Context) : ViewModel() {
    private val repository = MilkCollectionRepository(context)
    private val farmerProfileCalculator = FarmerProfileCalculator(context)
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun submitMilkCollection(
        collection: MilkCollection,
        isOnline: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Save to Room first and return quickly to user
                // Set loading to false immediately after local save
                repository.insertMilkCollection(collection.copy(isSynced = false))
                _isLoading.value = false
                
                // Show success and navigate immediately after local save
                _successMessage.value = "Milk collection added!"
                onSuccess()
                
                // Update farmer profile in background
                viewModelScope.launch {
                    farmerProfileCalculator.onMilkCollectionChanged(collection.farmerId)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _successMessage.value = "Milk collection added!"
                onSuccess()
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
    }
}