package com.example.doodhsethu.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkUtils(private val context: Context) {
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            android.util.Log.d("NetworkUtils", "Network available")
            _isOnline.value = true
        }
        
        override fun onLost(network: Network) {
            android.util.Log.d("NetworkUtils", "Network lost")
            _isOnline.value = false
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isOnline = hasInternet && isValidated
            
            // Only log if the online status actually changed
            val previousValue = _isOnline.value
            if (previousValue != isOnline) {
                android.util.Log.d("NetworkUtils", "Network status changed: $previousValue -> $isOnline")
            }
            _isOnline.value = isOnline
        }
    }
    
    fun startMonitoring() {
        android.util.Log.d("NetworkUtils", "Starting network monitoring")
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // Check initial state
        checkCurrentNetworkState()
    }
    
    fun stopMonitoring() {
        android.util.Log.d("NetworkUtils", "Stopping network monitoring")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
    
    private fun checkCurrentNetworkState() {
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isOnline = hasInternet && isValidated
        
        android.util.Log.d("NetworkUtils", "Initial network state: hasInternet=$hasInternet, isValidated=$isValidated, isOnline=$isOnline")
        _isOnline.value = isOnline
    }
    
    fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isOnline = hasInternet && isValidated
        
        return isOnline
    }
} 