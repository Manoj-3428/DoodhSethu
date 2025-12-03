package com.example.doodhsethu.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BillingCycleDetailsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BillingCycleDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BillingCycleDetailsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
