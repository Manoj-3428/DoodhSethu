package com.example.doodhsethu.utils

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

object GlobalNetworkManager {
    private var networkUtils: NetworkUtils? = null
    private var isInitialized = false
    
    fun initialize(context: Context) {
        if (!isInitialized) {
            android.util.Log.d("GlobalNetworkManager", "Initializing global network manager")
            networkUtils = NetworkUtils(context)
            networkUtils?.startMonitoring()
            isInitialized = true
            android.util.Log.d("GlobalNetworkManager", "Global network manager initialized")
        }
    }
    
    fun getNetworkStatus(): StateFlow<Boolean> {
        if (!isInitialized || networkUtils == null) {
            android.util.Log.e("GlobalNetworkManager", "Network manager not initialized!")
            throw IllegalStateException("GlobalNetworkManager must be initialized before use")
        }
        return networkUtils!!.isOnline
    }
    
    fun isCurrentlyOnline(): Boolean {
        if (!isInitialized || networkUtils == null) {
            android.util.Log.e("GlobalNetworkManager", "Network manager not initialized!")
            return false
        }
        return networkUtils!!.isCurrentlyOnline()
    }
    
    fun cleanup() {
        if (isInitialized && networkUtils != null) {
            android.util.Log.d("GlobalNetworkManager", "Cleaning up global network manager")
            networkUtils?.stopMonitoring()
            networkUtils = null
            isInitialized = false
        }
    }
} 