package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FarmerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FarmerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FarmerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 