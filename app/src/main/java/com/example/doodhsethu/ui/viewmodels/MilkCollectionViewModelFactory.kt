package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MilkCollectionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MilkCollectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MilkCollectionViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 