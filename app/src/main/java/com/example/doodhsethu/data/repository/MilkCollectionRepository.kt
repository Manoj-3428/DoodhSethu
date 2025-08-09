package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.data.models.MilkCollection
import com.example.doodhsethu.data.models.MilkReportEntry
import com.example.doodhsethu.data.models.MilkReportSummary
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MilkCollectionRepository(private val context: Context) {
    
    private val db = DatabaseManager.getDatabase(context)
    private val milkCollectionDao = db.milkCollectionDao()
    private val networkUtils = NetworkUtils(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val authViewModel = AuthViewModel()
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }
    
    suspend fun getAllMilkCollections(): List<MilkCollection> {
        return milkCollectionDao.getAllMilkCollections()
    }
    
    suspend fun getMilkCollectionsByFarmer(farmerId: String): List<MilkCollection> {
        return milkCollectionDao.getMilkCollectionsByFarmer(farmerId)
    }
    
    suspend fun getMilkCollectionsByDateRange(startDate: Date, endDate: Date): List<MilkCollection> {
        return milkCollectionDao.getMilkCollectionsByDateRange(startDate, endDate)
    }
    
    suspend fun getMilkCollectionsByFarmerAndDateRange(farmerId: String, startDate: Date, endDate: Date): List<MilkCollection> = withContext(Dispatchers.IO) {
        try {
            milkCollectionDao.getMilkCollectionsByFarmerAndDateRange(farmerId, startDate, endDate)
        } catch (e: Exception) {
            android.util.Log.e("MilkCollectionRepository", "Error getting milk collections by farmer and date range: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun insertMilkCollection(milkCollection: MilkCollection) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MilkCollectionRepository", "Inserting milk collection: ${milkCollection.farmerName} - ${milkCollection.collectedAt}")
            milkCollectionDao.insertMilkCollection(milkCollection)
            
            // Also store in daily milk collection structure for billing cycle calculations
            val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
            val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
            val collectionDate = dateFormat.format(milkCollection.collectedAt)
            
            // Get existing daily collection for this farmer and date
            val existingDailyCollection = dailyMilkCollectionRepository.getTodayCollections(collectionDate)
                .find { it.farmerId == milkCollection.farmerId }
            
            if (existingDailyCollection != null) {
                // Update existing daily collection
                val updatedCollection = existingDailyCollection.copy(
                    amMilk = existingDailyCollection.amMilk + milkCollection.quantity,
                    amFat = if (existingDailyCollection.amMilk + milkCollection.quantity > 0) {
                        ((existingDailyCollection.amMilk * existingDailyCollection.amFat + milkCollection.quantity * milkCollection.fatPercentage) / (existingDailyCollection.amMilk + milkCollection.quantity))
                    } else 0.0,
                    amPrice = existingDailyCollection.amPrice + milkCollection.totalPrice,
                    totalMilk = existingDailyCollection.totalMilk + milkCollection.quantity,
                    totalFat = if (existingDailyCollection.totalMilk + milkCollection.quantity > 0) {
                        ((existingDailyCollection.totalMilk * existingDailyCollection.totalFat + milkCollection.quantity * milkCollection.fatPercentage) / (existingDailyCollection.totalMilk + milkCollection.quantity))
                    } else 0.0,
                    totalAmount = existingDailyCollection.totalAmount + milkCollection.totalPrice,
                    updatedAt = Date(),
                    isSynced = false
                )
                dailyMilkCollectionRepository.createTodayCollection(
                    farmerId = updatedCollection.farmerId,
                    farmerName = updatedCollection.farmerName,
                    amMilk = updatedCollection.amMilk,
                    amFat = updatedCollection.amFat,
                    amPrice = updatedCollection.amPrice,
                    pmMilk = updatedCollection.pmMilk,
                    pmFat = updatedCollection.pmFat,
                    pmPrice = updatedCollection.pmPrice
                )
            } else {
                // Create new daily collection
                dailyMilkCollectionRepository.createTodayCollection(
                    farmerId = milkCollection.farmerId,
                    farmerName = milkCollection.farmerName,
                    amMilk = milkCollection.quantity,
                    amFat = milkCollection.fatPercentage,
                    amPrice = milkCollection.totalPrice
                )
            }
            
            android.util.Log.d("MilkCollectionRepository", "Successfully stored in daily milk collection structure")
            
            // Sync to new Firestore structure if online
            if (networkUtils.isCurrentlyOnline()) {
                try {
                    syncToNewFirestoreStructure(milkCollection)
                    android.util.Log.d("MilkCollectionRepository", "Successfully synced to new Firestore structure")
                } catch (e: Exception) {
                    android.util.Log.e("MilkCollectionRepository", "Failed to sync to new Firestore structure: ${e.message}")
                }
            } else {
                android.util.Log.d("MilkCollectionRepository", "Offline - milk collection saved locally only")
            }
        } catch (e: Exception) {
            android.util.Log.e("MilkCollectionRepository", "Error inserting milk collection: ${e.message}")
            throw e
        }
    }
    
    suspend fun updateMilkCollection(milkCollection: MilkCollection) {
        milkCollectionDao.updateMilkCollection(milkCollection)
        
        // Try to sync to Firestore if online using new structure
        if (networkUtils.isCurrentlyOnline()) {
            try {
                syncToNewFirestoreStructure(milkCollection)
            } catch (e: Exception) {
                // Keep local copy, will sync later
            }
        }
    }
    
    suspend fun deleteMilkCollection(milkCollection: MilkCollection) {
        milkCollectionDao.deleteMilkCollection(milkCollection)
        
        // Try to delete from Firestore if online using new structure
        if (networkUtils.isCurrentlyOnline()) {
            try {
                deleteFromNewFirestoreStructure(milkCollection)
            } catch (e: Exception) {
                // Keep local deletion, will sync later
            }
        }
    }
    
    suspend fun getMilkReportEntries(startDate: Date, endDate: Date): List<MilkReportEntry> {
        return milkCollectionDao.getMilkReportEntries(startDate, endDate)
    }
    
    suspend fun getMilkReportSummary(startDate: Date, endDate: Date): MilkReportSummary? {
        return milkCollectionDao.getMilkReportSummary(startDate, endDate)
    }
    
    private suspend fun syncToFirestore(milkCollection: MilkCollection) {
        val data = mapOf(
            "id" to milkCollection.id,
            "farmerId" to milkCollection.farmerId,
            "farmerName" to milkCollection.farmerName,
            "quantity" to milkCollection.quantity,
            "fatPercentage" to milkCollection.fatPercentage,
            "basePrice" to milkCollection.basePrice,
            "totalPrice" to milkCollection.totalPrice,
            "session" to milkCollection.session,
            "collectedBy" to milkCollection.collectedBy,
            "collectedAt" to milkCollection.collectedAt,
            "isSynced" to true
        )
        
        firestore.collection("milk_collections").document(milkCollection.id).set(data).await()
    }
    
    // New methods for date-wise Firestore structure
    private suspend fun syncToNewFirestoreStructure(milkCollection: MilkCollection) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("MilkCollectionRepository", "Cannot sync to Firestore: User not authenticated")
            return
        }
        
        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        val todayDate = dateFormat.format(milkCollection.collectedAt)
        
        android.util.Log.d("MilkCollectionRepository", "Syncing to new structure for date: $todayDate, farmer: ${milkCollection.farmerId}, user: $userId")
        android.util.Log.d("MilkCollectionRepository", "Using Firestore path: users/$userId/milk-collection/$todayDate/farmers/${milkCollection.farmerId}")
        
        // Create the date-based collection path
        val dateCollection = firestore.collection("users")
            .document(userId)
            .collection("milk-collection")
            .document(todayDate)
        
        // Get or create the farmer document within the date collection
        val farmerDoc = dateCollection.collection("farmers").document(milkCollection.farmerId)
        
        // Get existing data for this farmer on this date
        val existingData = try {
            farmerDoc.get().await().data
        } catch (e: Exception) {
            null
        }
        
        // Prepare the data for the new structure
        val firestoreData = mutableMapOf<String, Any>(
            "farmerId" to milkCollection.farmerId,
            "farmerName" to milkCollection.farmerName,
            "created_at" to milkCollection.collectedAt,
            "updated_at" to Date()
        )
        
        // Update session-specific data based on AM/PM
        if (milkCollection.session.equals("AM", ignoreCase = true)) {
            firestoreData["am_milk"] = milkCollection.quantity
            firestoreData["am_fat"] = milkCollection.fatPercentage
            firestoreData["am_price"] = milkCollection.totalPrice
            
            // Keep existing PM data if available
            if (existingData != null) {
                firestoreData["pm_milk"] = existingData["pm_milk"] ?: 0.0
                firestoreData["pm_fat"] = existingData["pm_fat"] ?: 0.0
                firestoreData["pm_price"] = existingData["pm_price"] ?: 0.0
            } else {
                firestoreData["pm_milk"] = 0.0
                firestoreData["pm_fat"] = 0.0
                firestoreData["pm_price"] = 0.0
            }
        } else {
            firestoreData["pm_milk"] = milkCollection.quantity
            firestoreData["pm_fat"] = milkCollection.fatPercentage
            firestoreData["pm_price"] = milkCollection.totalPrice
            
            // Keep existing AM data if available
            if (existingData != null) {
                firestoreData["am_milk"] = existingData["am_milk"] ?: 0.0
                firestoreData["am_fat"] = existingData["am_fat"] ?: 0.0
                firestoreData["am_price"] = existingData["am_price"] ?: 0.0
            } else {
                firestoreData["am_milk"] = 0.0
                firestoreData["am_fat"] = 0.0
                firestoreData["am_price"] = 0.0
            }
        }
        
        // Calculate totals
        val amMilk = firestoreData["am_milk"] as Double
        val pmMilk = firestoreData["pm_milk"] as Double
        val amFat = firestoreData["am_fat"] as Double
        val pmFat = firestoreData["pm_fat"] as Double
        val amPrice = firestoreData["am_price"] as Double
        val pmPrice = firestoreData["pm_price"] as Double
        
        firestoreData["total_milk"] = amMilk + pmMilk
        firestoreData["total_fat"] = if (amMilk + pmMilk > 0) ((amMilk * amFat + pmMilk * pmFat) / (amMilk + pmMilk)) else 0.0
        firestoreData["total_amount"] = amPrice + pmPrice
        
        // Save to Firestore using merge to update only specific fields
        farmerDoc.set(firestoreData, com.google.firebase.firestore.SetOptions.merge()).await()
        
        android.util.Log.d("MilkCollectionRepository", "Successfully synced to new structure: users/$userId/milk-collection/$todayDate/farmers/${milkCollection.farmerId}")
    }
    
    private suspend fun deleteFromNewFirestoreStructure(milkCollection: MilkCollection) {
        val userId = getCurrentUserId()
        if (userId == null) {
            android.util.Log.e("MilkCollectionRepository", "Cannot delete from Firestore: User not authenticated")
            return
        }
        
        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        val todayDate = dateFormat.format(milkCollection.collectedAt)
        
        try {
            android.util.Log.d("MilkCollectionRepository", "Deleting from Firestore path: users/$userId/milk-collection/$todayDate/farmers/${milkCollection.farmerId}")
            
            val farmerDoc = firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .document(todayDate)
                .collection("farmers")
                .document(milkCollection.farmerId)
            
            // Get existing data
            val existingData = farmerDoc.get().await().data
            
            if (existingData != null) {
                // Remove the specific session data
                val updatedData = mutableMapOf<String, Any>()
                
                if (milkCollection.session.equals("AM", ignoreCase = true)) {
                    updatedData["am_milk"] = 0.0
                    updatedData["am_fat"] = 0.0
                    updatedData["am_price"] = 0.0
                } else {
                    updatedData["pm_milk"] = 0.0
                    updatedData["pm_fat"] = 0.0
                    updatedData["pm_price"] = 0.0
                }
                
                // Recalculate totals
                val amMilk = if (milkCollection.session.equals("AM", ignoreCase = true)) 0.0 else (existingData["am_milk"] as? Double ?: 0.0)
                val pmMilk = if (milkCollection.session.equals("PM", ignoreCase = true)) 0.0 else (existingData["pm_milk"] as? Double ?: 0.0)
                val amFat = if (milkCollection.session.equals("AM", ignoreCase = true)) 0.0 else (existingData["am_fat"] as? Double ?: 0.0)
                val pmFat = if (milkCollection.session.equals("PM", ignoreCase = true)) 0.0 else (existingData["pm_fat"] as? Double ?: 0.0)
                val amPrice = if (milkCollection.session.equals("AM", ignoreCase = true)) 0.0 else (existingData["am_price"] as? Double ?: 0.0)
                val pmPrice = if (milkCollection.session.equals("PM", ignoreCase = true)) 0.0 else (existingData["pm_price"] as? Double ?: 0.0)
                
                updatedData["total_milk"] = amMilk + pmMilk
                updatedData["total_fat"] = if (amMilk + pmMilk > 0) ((amMilk * amFat + pmMilk * pmFat) / (amMilk + pmMilk)) else 0.0
                updatedData["total_amount"] = amPrice + pmPrice
                updatedData["updated_at"] = Date()
                
                farmerDoc.set(updatedData, com.google.firebase.firestore.SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("MilkCollectionRepository", "Error deleting from new Firestore structure: ${e.message}")
        }
    }
    
    suspend fun syncLocalWithFirestore() {
        if (!networkUtils.isCurrentlyOnline()) return
        
        try {
            // Get unsynced collections
            val unsyncedCollections = milkCollectionDao.getUnsyncedMilkCollections()
            
            // Sync each collection to Firestore using new structure
            for (collection in unsyncedCollections) {
                try {
                    syncToNewFirestoreStructure(collection)
                } catch (e: Exception) {
                    android.util.Log.e("MilkCollectionRepository", "Error syncing collection ${collection.id}: ${e.message}")
                    // Continue with next collection
                }
            }
            
            // Mark as synced
            if (unsyncedCollections.isNotEmpty()) {
                val syncedIds = unsyncedCollections.map { collection -> collection.id }
                milkCollectionDao.markMilkCollectionsAsSynced(syncedIds)
                android.util.Log.d("MilkCollectionRepository", "Marked ${syncedIds.size} collections as synced")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MilkCollectionRepository", "Error in syncLocalWithFirestore: ${e.message}")
        }
    }
}