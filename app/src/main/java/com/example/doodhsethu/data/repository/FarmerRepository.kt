package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.data.models.Farmer
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class FarmerRepository(private val context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val farmerDao = db.farmerDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val networkUtils = NetworkUtils(context)
    private val authViewModel = AuthViewModel()
    
    // Inject other repositories for cascade deletion (avoiding circular dependency)
    private val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
    private val billingCycleSummaryRepository = BillingCycleSummaryRepository(context)
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }

    suspend fun getAllFarmers(): List<Farmer> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot get farmers: User not authenticated")
            return@withContext emptyList()
        }
        farmerDao.getAllFarmersList().filter { it.addedBy == userId }
    }

    fun getAllFarmersFlow(): kotlinx.coroutines.flow.Flow<List<Farmer>> {
        val userId = getCurrentUserId()
        return if (userId != null) {
            farmerDao.getAllFarmers().map { farmers -> farmers.filter { it.addedBy == userId } }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    suspend fun insertFarmer(farmer: Farmer) = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot insert farmer: User not authenticated")
            return@withContext
        }
        val farmerWithUser = farmer.copy(addedBy = userId)
        farmerDao.insertFarmer(farmerWithUser)
    }

    suspend fun insertFarmers(farmers: List<Farmer>) = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot insert farmers: User not authenticated")
            return@withContext
        }
        val farmersWithUser = farmers.map { it.copy(addedBy = userId) }
        farmerDao.insertFarmers(farmersWithUser)
    }

    suspend fun updateFarmer(farmer: Farmer) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FarmerRepository", "Updating farmer profile: ${farmer.id}")
            farmerDao.updateFarmer(farmer)
            
            // Sync to Firestore if online
            if (networkUtils.isCurrentlyOnline()) {
                try {
                    val userId = getCurrentUserId()
                    if (userId != null) {
                        android.util.Log.d("FarmerRepository", "Syncing updated farmer to Firestore: users/$userId/farmers/${farmer.id}")
                        firestore.collection("users").document(userId).collection("farmers").document(farmer.id).set(farmer.copy(synced = true)).await()
                    android.util.Log.d("FarmerRepository", "Successfully synced updated farmer to Firestore: ${farmer.id}")
                    } else {
                        android.util.Log.e("FarmerRepository", "Cannot sync farmer: User not authenticated")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FarmerRepository", "Failed to sync updated farmer to Firestore: ${e.message}")
                }
            } else {
                android.util.Log.d("FarmerRepository", "Offline - farmer profile updated locally only")
            }
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error updating farmer: ${e.message}")
            throw e
        }
    }

    suspend fun deleteFarmerById(id: String) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FarmerRepository", "Starting cascade deletion for farmer: $id")
            
            // 1. Delete all milk collections for this farmer
            try {
                val milkCollections = dailyMilkCollectionRepository.getAllCollectionsByFarmer(id)
                android.util.Log.d("FarmerRepository", "Found ${milkCollections.size} milk collections to delete for farmer: $id")
                
                for (collection in milkCollections) {
                    db.dailyMilkCollectionDao().deleteDailyMilkCollection(collection)
                }
                android.util.Log.d("FarmerRepository", "Deleted all milk collections for farmer: $id")
            } catch (e: Exception) {
                android.util.Log.e("FarmerRepository", "Error deleting milk collections for farmer $id: ${e.message}")
            }
            
            // 2. Delete all farmer billing details for this farmer
            try {
                val billingDetails = db.farmerBillingDetailDao().getFarmerBillingDetailsByFarmer(id).first()
                android.util.Log.d("FarmerRepository", "Found ${billingDetails.size} billing details to delete for farmer: $id")
                
                for (billingDetail in billingDetails) {
                    // Delete from local database using DAO directly
                    db.farmerBillingDetailDao().deleteFarmerBillingDetail(billingDetail)
                    
                    // Delete billing cycle summary from Firestore
                    // DISABLED: This was accessing corrupted billing cycle documents inside farmer profiles
                    // billingCycleSummaryRepository.deleteBillingCycleSummary(
                    //     billingCycleId = billingDetail.billingCycleId,
                    //     farmerId = billingDetail.farmerId
                    // )
                }
                android.util.Log.d("FarmerRepository", "Deleted all billing details for farmer: $id")
            } catch (e: Exception) {
                android.util.Log.e("FarmerRepository", "Error deleting billing details for farmer $id: ${e.message}")
            }
            
            // 3. Delete farmer photos (if any)
            try {
                // Note: LocalPhotoManager.deleteFarmerPhoto() would be called here if needed
                android.util.Log.d("FarmerRepository", "Farmer photos cleanup completed for farmer: $id")
            } catch (e: Exception) {
                android.util.Log.e("FarmerRepository", "Error deleting farmer photos for farmer $id: ${e.message}")
            }
            
            // 4. Finally, delete the farmer from local database
        farmerDao.deleteFarmerById(id)
            android.util.Log.d("FarmerRepository", "Successfully deleted farmer: $id")
            
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error during cascade deletion for farmer $id: ${e.message}")
            throw e
        }
    }

    suspend fun deleteAllFarmers() = withContext(Dispatchers.IO) {
        farmerDao.deleteAllFarmers()
    }

    suspend fun getFarmerById(id: String): Farmer? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot get farmer: User not authenticated")
            return@withContext null
        }
        val farmer = farmerDao.getFarmerById(id)
        if (farmer != null && farmer.addedBy == userId) farmer else null
    }

    suspend fun searchFarmers(query: String): List<Farmer> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot search farmers: User not authenticated")
            return@withContext emptyList()
        }
        farmerDao.searchFarmers(query).filter { it.addedBy == userId }
    }

    suspend fun getUnsyncedFarmers(): List<Farmer> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("FarmerRepository", "Cannot get unsynced farmers: User not authenticated")
            return@withContext emptyList()
        }
        farmerDao.getUnsyncedFarmers().filter { it.addedBy == userId }
    }

    suspend fun markFarmersAsSynced(ids: List<String>) = withContext(Dispatchers.IO) {
        farmerDao.markFarmersAsSynced(ids)
    }

    // Firestore sync methods
    suspend fun fetchFarmersFromFirestore(isOnline: Boolean): List<Farmer> = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext emptyList()
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FarmerRepository", "Cannot fetch farmers: User not authenticated")
                return@withContext emptyList()
            }
            
            val snapshot = firestore.collection("users").document(userId).collection("farmers").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Farmer::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error fetching farmers from Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun syncLocalWithFirestore(isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            android.util.Log.d("FarmerRepository", "Starting Firestore sync...")
            // 1. Upload unsynced farmers
            val unsynced = getUnsyncedFarmers()
            if (unsynced.isNotEmpty()) {
                android.util.Log.d("FarmerRepository", "Uploading ${unsynced.size} unsynced farmers")
                val userId = getCurrentUserId()
                if (userId != null) {
                unsynced.forEach { farmer ->
                        firestore.collection("users").document(userId).collection("farmers").document(farmer.id).set(farmer.copy(synced = true)).await()
                    }
                    markFarmersAsSynced(unsynced.map { it.id })
                } else {
                    android.util.Log.e("FarmerRepository", "Cannot sync farmers: User not authenticated")
                }
            }
            // 2. Download from Firestore and update Room
            val remoteFarmers = fetchFarmersFromFirestore(true)
            if (remoteFarmers.isNotEmpty()) {
                android.util.Log.d("FarmerRepository", "Downloading ${remoteFarmers.size} farmers from Firestore")
                deleteAllFarmers()
                insertFarmers(remoteFarmers)
            }
            android.util.Log.d("FarmerRepository", "Firestore sync completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error during Firestore sync: ${e.message}")
        }
    }

    suspend fun uploadFarmerToFirestore(farmer: Farmer, isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            android.util.Log.d("FarmerRepository", "Uploading farmer to Firestore: ${farmer.id}")
            val userId = getCurrentUserId()
            if (userId != null) {
                firestore.collection("users").document(userId).collection("farmers").document(farmer.id).set(farmer.copy(synced = true)).await()
            markFarmersAsSynced(listOf(farmer.id))
            android.util.Log.d("FarmerRepository", "Farmer uploaded successfully: ${farmer.id}")
            } else {
                android.util.Log.e("FarmerRepository", "Cannot upload farmer: User not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error uploading farmer to Firestore: ${e.message}")
        }
    }

    suspend fun deleteFarmerFromFirestore(id: String, isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            android.util.Log.d("FarmerRepository", "Deleting farmer and all related data from Firestore: $id")
            val userId = getCurrentUserId()
            if (userId != null) {
                // 1. Delete all milk collections for this farmer from Firestore
                try {
                    val milkCollectionsSnapshot = firestore.collection("users").document(userId)
                        .collection("milk-collection")
                        .get().await()
                    
                    for (dateDoc in milkCollectionsSnapshot.documents) {
                        val farmerDoc = firestore.collection("users").document(userId)
                            .collection("milk-collection")
                            .document(dateDoc.id)
                            .collection("farmers")
                            .document(id)
                        
                        // Check if farmer document exists and delete it
                        val farmerData = farmerDoc.get().await()
                        if (farmerData.exists()) {
                            farmerDoc.delete().await()
                            android.util.Log.d("FarmerRepository", "Deleted milk collection for farmer $id on date ${dateDoc.id}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FarmerRepository", "Error deleting milk collections from Firestore for farmer $id: ${e.message}")
                }
                
                // 2. Delete all billing cycle data for this farmer from Firestore
                try {
                    val billingCyclesSnapshot = firestore.collection("users").document(userId)
                        .collection("farmers")
                        .document(id)
                        .collection("billing_cycle")
                        .get().await()
                    
                    for (billingCycleDoc in billingCyclesSnapshot.documents) {
                        billingCycleDoc.reference.delete().await()
                        android.util.Log.d("FarmerRepository", "Deleted billing cycle ${billingCycleDoc.id} for farmer $id")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FarmerRepository", "Error deleting billing cycles from Firestore for farmer $id: ${e.message}")
                }
                
                // 3. Finally, delete the farmer document itself
                firestore.collection("users").document(userId).collection("farmers").document(id).delete().await()
                android.util.Log.d("FarmerRepository", "Farmer and all related data deleted successfully from Firestore: $id")
            } else {
                android.util.Log.e("FarmerRepository", "Cannot delete farmer: User not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error deleting farmer from Firestore: ${e.message}")
        }
    }

    // Sync with Firestore (for AutoSyncManager)
    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) return@withContext
        try {
            android.util.Log.d("FarmerRepository", "Starting Firestore sync...")
            // 1. Upload unsynced farmers
            val unsynced = getUnsyncedFarmers()
            if (unsynced.isNotEmpty()) {
                android.util.Log.d("FarmerRepository", "Uploading ${unsynced.size} unsynced farmers")
                val userId = getCurrentUserId()
                if (userId != null) {
                unsynced.forEach { farmer ->
                        firestore.collection("users").document(userId).collection("farmers").document(farmer.id).set(farmer.copy(synced = true)).await()
                    }
                    markFarmersAsSynced(unsynced.map { it.id })
                } else {
                    android.util.Log.e("FarmerRepository", "Cannot sync farmers: User not authenticated")
                }
            }
            android.util.Log.d("FarmerRepository", "Firestore sync completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error during Firestore sync: ${e.message}")
        }
    }

    // Load from Firestore (for AutoSyncManager)
    suspend fun loadFromFirestore() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) return@withContext
        try {
            android.util.Log.d("FarmerRepository", "Loading farmers from Firestore...")
            val remoteFarmers = fetchFarmersFromFirestore(true)
            if (remoteFarmers.isNotEmpty()) {
                android.util.Log.d("FarmerRepository", "Downloading ${remoteFarmers.size} farmers from Firestore")
                deleteAllFarmers()
                insertFarmers(remoteFarmers)
            }
            android.util.Log.d("FarmerRepository", "Farmers loaded from Firestore successfully")
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error loading farmers from Firestore: ${e.message}")
        }
    }
    
    /**
     * Replace all farmers with new data (for bulk import)
     * Handles offline/online scenarios properly
     */
    suspend fun replaceAllFarmers(newFarmers: List<Farmer>) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FarmerRepository", "Replacing all farmers with ${newFarmers.size} new entries")
            
            // Clear existing data
            farmerDao.deleteAllFarmers()
            
            // Insert new data with appropriate sync status and ownership
            val currentUserId = getCurrentUserId() ?: ""
            val farmersToInsert = newFarmers.map { farmer ->
                // Attribute to current user and mark as unsynced (will sync later if online)
                farmer.copy(synced = false, addedBy = currentUserId)
            }
            farmerDao.insertFarmers(farmersToInsert)
            
            // Try to upload to Firestore (non-blocking)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Upload to Firestore
                    val userId = getCurrentUserId()
                    if (userId != null) {
                        for (farmer in farmersToInsert) {
                            firestore.collection("users").document(userId).collection("farmers")
                                .document(farmer.id).set(farmer.copy(synced = true)).await()
                        }
                        
                        // Mark as synced after successful upload
                        val insertedFarmers = getAllFarmers()
                        markFarmersAsSynced(insertedFarmers.map { it.id })
                        
                        android.util.Log.d("FarmerRepository", "Successfully uploaded all farmers to Firestore")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FarmerRepository", "Failed to upload to Firestore (offline?): ${e.message}")
                    // Keep farmers as unsynced for later sync when online
                }
            }
            
            android.util.Log.d("FarmerRepository", "Successfully replaced all farmers")
            
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error replacing farmers: ${e.message}")
            throw e
        }
    }
    
    /**
     * Add farmers with validation and sync
     */
    suspend fun addFarmersWithSync(farmers: List<Farmer>, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate farmers (check for duplicates)
            val existingFarmers = getAllFarmers()
            val existingIds = existingFarmers.map { it.id }.toSet()
            val existingPhones = existingFarmers.map { it.phone }.toSet()
            
            val validFarmers = farmers.filter { farmer ->
                // Only check if ID already exists (allow duplicate phone numbers)
                if (existingIds.contains(farmer.id)) {
                    android.util.Log.w("FarmerRepository", "Farmer with ID ${farmer.id} already exists, skipping")
                    false
                } else {
                    true
                }
            }
            
            if (validFarmers.isEmpty()) {
                android.util.Log.w("FarmerRepository", "No valid farmers to add (all duplicates)")
                return@withContext false
            }
            
            // Ensure imported farmers are attributed to the current user so UI filters include them
            val currentUserId = getCurrentUserId() ?: ""
            // Save to local storage immediately with isSynced = false
            val newFarmers = validFarmers.map { it.copy(synced = false, addedBy = currentUserId) }
            farmerDao.insertFarmers(newFarmers)
            
            // Get the inserted farmers with the correct IDs
            val insertedFarmers = getAllFarmers().filter { farmer ->
                newFarmers.any { it.id == farmer.id && !farmer.synced }
            }
            
            // Sync to Firestore in background if online (non-blocking)
            if (isOnline && insertedFarmers.isNotEmpty()) {
                // Launch background coroutine for Firestore sync
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val userId = getCurrentUserId()
                        if (userId != null) {
                            for (farmer in insertedFarmers) {
                                firestore.collection("users").document(userId).collection("farmers")
                                    .document(farmer.id).set(farmer.copy(synced = true)).await()
                            }
                            markFarmersAsSynced(insertedFarmers.map { it.id })
                            android.util.Log.d("FarmerRepository", "Successfully synced ${insertedFarmers.size} farmers to Firestore")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FarmerRepository", "Background sync failed: ${e.message}")
                    }
                }
            }
            
            android.util.Log.d("FarmerRepository", "Successfully added ${validFarmers.size} farmers")
            true
        } catch (e: Exception) {
            android.util.Log.e("FarmerRepository", "Error adding farmers: ${e.message}")
            false
        }
    }

} 