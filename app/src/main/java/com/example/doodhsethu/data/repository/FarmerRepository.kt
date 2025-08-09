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
        farmerDao.getAllFarmersList()
    }

    fun getAllFarmersFlow(): kotlinx.coroutines.flow.Flow<List<Farmer>> {
        return farmerDao.getAllFarmers()
    }

    suspend fun insertFarmer(farmer: Farmer) = withContext(Dispatchers.IO) {
        farmerDao.insertFarmer(farmer)
    }

    suspend fun insertFarmers(farmers: List<Farmer>) = withContext(Dispatchers.IO) {
        farmerDao.insertFarmers(farmers)
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
                    billingCycleSummaryRepository.deleteBillingCycleSummary(
                        billingCycleId = billingDetail.billingCycleId,
                        farmerId = billingDetail.farmerId
                    )
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
        farmerDao.getFarmerById(id)
    }

    suspend fun searchFarmers(query: String): List<Farmer> = withContext(Dispatchers.IO) {
        farmerDao.searchFarmers(query)
    }

    suspend fun getUnsyncedFarmers(): List<Farmer> = withContext(Dispatchers.IO) {
        farmerDao.getUnsyncedFarmers()
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
    

} 