package com.example.doodhsethu.utils

import android.content.Context
import android.util.Log
import com.example.doodhsethu.data.repository.*
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AutoSyncManager(private val context: Context) {
    private val farmerRepository = FarmerRepository(context)
    private val milkCollectionRepository = MilkCollectionRepository(context)
    private val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
    private val billingCycleRepository = BillingCycleRepository(context)

    private val fatTableRepository = FatTableRepository(context)
    private val userRepository = UserRepository(context)
    private val networkUtils = NetworkUtils(context)
    private val authViewModel = AuthViewModel()
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }

    /**
     * Start automatic sync process
     */
    @Suppress("unused")
    fun startAutoSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AutoSyncManager", "Starting auto sync process")
                
                // Clean up duplicate collections first
                try {
                    dailyMilkCollectionRepository.cleanupDuplicateCollections()
                    Log.d("AutoSyncManager", "Cleaned up duplicate collections")
                } catch (e: Exception) {
                    Log.e("AutoSyncManager", "Failed to clean up duplicate collections: ${e.message}")
                }
                
                // Check if we have internet connection
                if (!networkUtils.isCurrentlyOnline()) {
                    Log.d("AutoSyncManager", "No internet connection, skipping auto sync")
                    return@launch
                }
            
            // Sync all repositories
            syncAllRepositories()
            
                Log.d("AutoSyncManager", "Auto sync completed successfully")
        } catch (e: Exception) {
                Log.e("AutoSyncManager", "Error during auto sync: ${e.message}")
            }
        }
    }
    
    /**
     * Sync all repositories with Firestore
     */
    private suspend fun syncAllRepositories() = withContext(Dispatchers.IO) {
        try {
            Log.d("AutoSyncManager", "Syncing all repositories with Firestore")
            
            // Sync farmers
            try {
                farmerRepository.syncWithFirestore()
                Log.d("AutoSyncManager", "Synced farmers with Firestore")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to sync farmers: ${e.message}")
            }
            
            // Sync milk collections
            try {
                milkCollectionRepository.syncLocalWithFirestore()
                Log.d("AutoSyncManager", "Synced milk collections with Firestore")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to sync milk collections: ${e.message}")
            }
            
            // Sync daily milk collections
            try {
                dailyMilkCollectionRepository.syncWithFirestore()
                Log.d("AutoSyncManager", "Synced daily milk collections with Firestore")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to sync daily milk collections: ${e.message}")
            }
            
            // Sync fat table
            try {
                // First, handle any offline entries that need to be synced
                fatTableRepository.handleOfflineToOnlineSync()
                
                // Then do a complete sync
                fatTableRepository.syncWithFirestore()
                Log.d("AutoSyncManager", "Synced fat table with Firestore")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to sync fat table: ${e.message}")
            }
            
            // Clean up duplicate fat table entries
            try {
                fatTableRepository.forceCleanupAllDuplicates()
                Log.d("AutoSyncManager", "Cleaned up duplicate fat table entries")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to cleanup fat table duplicates: ${e.message}")
            }
            
            // Sync users
            try {
                userRepository.syncWithFirestore()
                Log.d("AutoSyncManager", "Synced users with Firestore")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Failed to sync users: ${e.message}")
            }
            
            Log.d("AutoSyncManager", "All repositories synced successfully")
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "Error syncing repositories: ${e.message}")
        }
    }



    /**
     * Update farmer profiles when needed
     */
    @Suppress("unused")
    suspend fun updateFarmerProfilesWhenNeeded() = withContext(Dispatchers.IO) {
        try {
            Log.d("AutoSyncManager", "Updating farmer profiles when needed")
            
            val farmers = farmerRepository.getAllFarmers()
            for (farmer in farmers) {
                try {
                    val farmerProfileCalculator = FarmerProfileCalculator(context)
                    farmerProfileCalculator.updateFarmerProfile(farmer.id)
                } catch (e: Exception) {
                    Log.e("AutoSyncManager", "Error updating profile for farmer ${farmer.id}: ${e.message}")
                }
            }
            
            Log.d("AutoSyncManager", "Farmer profiles updated successfully")
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "Error updating farmer profiles: ${e.message}")
        }
    }
    

    

    
    /**
     * Check if data restoration is needed (fresh installation vs regular login)
     */
    suspend fun isDataRestorationNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if we have any local data
            val farmers = farmerRepository.getAllFarmers()
            val billingCycles = billingCycleRepository.getAllBillingCycles().first()
            val milkCollections = dailyMilkCollectionRepository.getAllDailyMilkCollections()
            
            val hasLocalData = farmers.isNotEmpty() || billingCycles.isNotEmpty() || milkCollections.isNotEmpty()
            
            Log.d("AutoSyncManager", "Data restoration check - Farmers: ${farmers.size}, BillingCycles: ${billingCycles.size}, MilkCollections: ${milkCollections.size}")
            Log.d("AutoSyncManager", "Data restoration needed: ${!hasLocalData}")
            
            return@withContext !hasLocalData
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "Error checking if data restoration is needed: ${e.message}")
            // If we can't determine, assume restoration is needed for safety
            return@withContext true
        }
    }

    /**
     * Comprehensive data pre-loader with progress tracking
     * This ensures no lazy loading delays when navigating between screens
     * Only runs if data restoration is actually needed
     */
    suspend fun preloadAllScreenDataWithProgress(onProgress: (Int, String) -> Unit) {
        try {
            Log.d("AutoSyncManager", "Starting comprehensive data preloading for all screens")
            onProgress(0, "Checking if data restoration is needed...")
            
            // Check if data restoration is actually needed
            if (!isDataRestorationNeeded()) {
                Log.d("AutoSyncManager", "Local data already exists, skipping data restoration")
                onProgress(100, "Data already available")
                return
            }
            
            Log.d("AutoSyncManager", "Data restoration needed, starting comprehensive preloading")
            onProgress(5, "Initializing...")
            
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d("AutoSyncManager", "Cannot preload data - no internet connection")
                onProgress(100, "No internet connection")
                return
            }
            
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e("AutoSyncManager", "Cannot preload data: User not authenticated")
                onProgress(100, "Authentication error")
                return
            }
            
            onProgress(5, "Loading farmers...")
            
            // Load farmers first (other data depends on farmers)
            try {
                farmerRepository.loadFromFirestore()
                Log.d("AutoSyncManager", "✅ Preloaded farmers data")
                onProgress(20, "Farmers loaded successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to preload farmers: ${e.message}")
                onProgress(20, "Farmers loaded with errors")
            }
            
            onProgress(25, "Loading fat table and users...")
            
            // Load fat table and users in parallel
            try {
                kotlinx.coroutines.coroutineScope {
                    val fatTableJob = launch {
                        try {
                            // Only sync if not already syncing
                            fatTableRepository.syncFromFirestore()
                            Log.d("AutoSyncManager", "✅ Preloaded fat table data")
                        } catch (e: Exception) {
                            Log.e("AutoSyncManager", "❌ Failed to preload fat table: ${e.message}")
                        }
                    }
                    
                    val usersJob = launch {
                        try {
                            userRepository.loadFromFirestore()
                            Log.d("AutoSyncManager", "✅ Preloaded users data")
                        } catch (e: Exception) {
                            Log.e("AutoSyncManager", "❌ Failed to preload users: ${e.message}")
                        }
                    }
                    
                    fatTableJob.join()
                    usersJob.join()
                }
                onProgress(40, "Basic data loaded successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "Error in parallel operations: ${e.message}")
                onProgress(40, "Basic data loaded with errors")
            }
            
            onProgress(45, "Fixing Firestore documents...")
            
            // Fix Firestore documents
            try {
                dailyMilkCollectionRepository.fixFirestoreDocumentsForCurrentUser()
                Log.d("AutoSyncManager", "✅ Fixed Firestore documents")
                onProgress(50, "Documents fixed successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to fix documents: ${e.message}")
                onProgress(50, "Documents fixed with errors")
            }
            
            onProgress(55, "Loading milk collections...")
            
            // Load milk collections
            try {
                dailyMilkCollectionRepository.restoreMilkCollectionsForCurrentUser()
                Log.d("AutoSyncManager", "✅ Preloaded milk collections data")
                onProgress(70, "Milk collections loaded successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to preload milk collections: ${e.message}")
                onProgress(70, "Milk collections loaded with errors")
            }

            onProgress(72, "Cleaning up duplicate entries...")
            
            // Clean up duplicate milk collection entries
            try {
                dailyMilkCollectionRepository.cleanupDuplicateEntries()
                Log.d("AutoSyncManager", "✅ Cleaned up duplicate milk collection entries")
                onProgress(73, "Milk collection duplicates cleaned successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to cleanup milk collection duplicates: ${e.message}")
                onProgress(73, "Milk collection cleanup with errors")
            }
            
            // Clean up duplicate fat table entries
            try {
                fatTableRepository.forceCleanupAllDuplicates()
                Log.d("AutoSyncManager", "✅ Cleaned up duplicate fat table entries")
                onProgress(75, "Fat table duplicates cleaned successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to cleanup fat table duplicates: ${e.message}")
                onProgress(75, "Fat table cleanup with errors")
            }
            
            onProgress(77, "Loading billing cycles...")
            
            // Load billing cycles
            try {
                billingCycleRepository.migrateGlobalBillingCyclesToUserSpecific(userId)
                billingCycleRepository.restoreBillingCyclesFromFirestore(userId)
                billingCycleRepository.forceCleanupAllDuplicates() // Force cleanup all duplicates
                billingCycleRepository.cleanupDuplicateData() // Regular cleanup
                Log.d("AutoSyncManager", "✅ Preloaded billing cycles data")
                onProgress(90, "Billing cycles loaded successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to preload billing cycles: ${e.message}")
                onProgress(90, "Billing cycles loaded with errors")
            }
            
            // Clean up corrupted billing cycle documents from farmer profiles
            try {
                billingCycleRepository.cleanupCorruptedBillingCycleDocuments()
                Log.d("AutoSyncManager", "✅ Cleaned up corrupted billing cycle documents")
                onProgress(92, "Corrupted documents cleaned successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to cleanup corrupted documents: ${e.message}")
                onProgress(92, "Corrupted documents cleanup with errors")
            }
            
            onProgress(92, "Updating farmer profiles...")
            
            // Update farmer profiles (optimized to avoid excessive Firestore calls)
            try {
                updateAllFarmerProfilesOptimized()
                Log.d("AutoSyncManager", "✅ Updated all farmer profiles")
                onProgress(95, "Farmer profiles updated successfully")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to update farmer profiles: ${e.message}")
                onProgress(95, "Farmer profiles updated with errors")
            }
            
            onProgress(100, "Data restoration completed!")
            Log.d("AutoSyncManager", "🎉 Comprehensive data preloading completed successfully!")
            
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "❌ Error during comprehensive data preloading: ${e.message}")
            onProgress(100, "Data restoration failed")
        }
    }
    
    /**
     * Optimized farmer profile update that avoids excessive Firestore calls
     */
    private suspend fun updateAllFarmerProfilesOptimized() = withContext(Dispatchers.IO) {
        try {
            Log.d("AutoSyncManager", "Updating all farmer profiles (optimized)")
            
            val farmers = farmerRepository.getAllFarmers()
            Log.d("AutoSyncManager", "Found ${farmers.size} farmers to update")
            
            for ((index, farmer) in farmers.withIndex()) {
                try {
                    val farmerProfileCalculator = FarmerProfileCalculator(context)
                    // Use optimized method that doesn't make individual calls for each day
                    farmerProfileCalculator.updateFarmerProfileOptimized(farmer.id)
                    Log.d("AutoSyncManager", "Updated profile for farmer ${farmer.id} (${index + 1}/${farmers.size})")
                } catch (e: Exception) {
                    Log.e("AutoSyncManager", "Error updating profile for farmer ${farmer.id}: ${e.message}")
                }
            }
            
            Log.d("AutoSyncManager", "All farmer profiles updated successfully (optimized)")
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "Error updating farmer profiles (optimized): ${e.message}")
        }
    }
    
    /**
     * Quick sync for regular logins - only sync unsynced data without full restoration
     */
    suspend fun quickSyncForRegularLogin(onProgress: (Int, String) -> Unit) {
        try {
            Log.d("AutoSyncManager", "Starting quick sync for regular login")
            onProgress(0, "Quick sync...")
            
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d("AutoSyncManager", "No internet connection, skipping quick sync")
                onProgress(100, "No internet connection")
                return
            }
            
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e("AutoSyncManager", "Cannot quick sync: User not authenticated")
                onProgress(100, "Authentication error")
                return
            }
            
            onProgress(25, "Syncing unsynced data...")
            
            // Only sync unsynced data to Firestore
            try {
                farmerRepository.syncWithFirestore()
                milkCollectionRepository.syncLocalWithFirestore()
                dailyMilkCollectionRepository.syncWithFirestore()
                billingCycleRepository.syncAllDataWhenOnline()
                fatTableRepository.syncWithFirestore()
                userRepository.syncWithFirestore()
                
                Log.d("AutoSyncManager", "✅ Quick sync completed successfully")
                onProgress(100, "Quick sync completed")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Quick sync failed: ${e.message}")
                onProgress(100, "Quick sync completed with errors")
            }
            
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "Error during quick sync: ${e.message}")
            onProgress(100, "Quick sync failed")
        }
    }
    
    /**
     * Start real-time sync for all repositories
     */
    fun startRealTimeSync() {
        try {
            Log.d("AutoSyncManager", "Starting real-time sync for all repositories")
            
            // Start real-time sync for milk collections
            try {
                milkCollectionRepository.startRealTimeSync()
                Log.d("AutoSyncManager", "✅ Started real-time sync for milk collections")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to start real-time sync for milk collections: ${e.message}")
            }
            
            // Start real-time sync for billing cycles
            try {
                billingCycleRepository.startRealTimeSync()
                Log.d("AutoSyncManager", "✅ Started real-time sync for billing cycles")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to start real-time sync for billing cycles: ${e.message}")
            }
            
            // Start real-time sync for fat table
            try {
                fatTableRepository.startRealTimeSync()
                Log.d("AutoSyncManager", "✅ Started real-time sync for fat table")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to start real-time sync for fat table: ${e.message}")
            }
            
            Log.d("AutoSyncManager", "🎉 Real-time sync started for all repositories")
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "❌ Error starting real-time sync: ${e.message}")
        }
    }
    
    /**
     * Stop real-time sync for all repositories
     */
    fun stopRealTimeSync() {
        try {
            Log.d("AutoSyncManager", "Stopping real-time sync for all repositories")
            
            // Stop real-time sync for milk collections
            try {
                milkCollectionRepository.stopRealTimeSync()
                Log.d("AutoSyncManager", "✅ Stopped real-time sync for milk collections")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to stop real-time sync for milk collections: ${e.message}")
            }
            
            // Stop real-time sync for billing cycles
            try {
                billingCycleRepository.stopRealTimeSync()
                Log.d("AutoSyncManager", "✅ Stopped real-time sync for billing cycles")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to stop real-time sync for billing cycles: ${e.message}")
            }
            
            // Stop real-time sync for fat table
            try {
                fatTableRepository.stopRealTimeSync()
                Log.d("AutoSyncManager", "✅ Stopped real-time sync for fat table")
            } catch (e: Exception) {
                Log.e("AutoSyncManager", "❌ Failed to stop real-time sync for fat table: ${e.message}")
            }
            
            Log.d("AutoSyncManager", "🎉 Real-time sync stopped for all repositories")
        } catch (e: Exception) {
            Log.e("AutoSyncManager", "❌ Error stopping real-time sync: ${e.message}")
        }
    }
} 