package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.*
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.utils.FarmerProfileCalculator
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.example.doodhsethu.data.repository.FarmerRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DailyMilkCollectionRepository(private val context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val dailyMilkCollectionDao = db.dailyMilkCollectionDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val networkUtils = NetworkUtils(context)
    private val billingCycleSummaryRepository = BillingCycleSummaryRepository(context)
    private val authViewModel = AuthViewModel()
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }

    // Create or update today's collection for a farmer
    suspend fun createTodayCollection(
        farmerId: String,
        farmerName: String,
        amMilk: Double = 0.0,
        amFat: Double = 0.0,
        amPrice: Double = 0.0,
        pmMilk: Double = 0.0,
        pmFat: Double = 0.0,
        pmPrice: Double = 0.0
    ) = withContext(Dispatchers.IO) {
        try {
            val today = dateFormat.format(Date())
            
            // Calculate totals
            val totalMilk = amMilk + pmMilk
            val totalFat = if (totalMilk > 0) ((amMilk * amFat + pmMilk * pmFat) / totalMilk) else 0.0
            val totalAmount = amPrice + pmPrice

            android.util.Log.d("DailyMilkCollectionRepository", "Creating collection for farmer $farmerId: AM(₹$amPrice) + PM(₹$pmPrice) = Total(₹$totalAmount)")

            // Check if collection already exists for today
            val existingCollection = dailyMilkCollectionDao.getDailyMilkCollectionByFarmerAndDate(farmerId, today)
            
            val dailyCollection = if (existingCollection != null) {
                // Update existing collection
                existingCollection.copy(
                    amMilk = amMilk,
                    amFat = amFat,
                    amPrice = amPrice,
                    pmMilk = pmMilk,
                    pmFat = pmFat,
                    pmPrice = pmPrice,
                    totalMilk = totalMilk,
                    totalFat = totalFat,
                    totalAmount = totalAmount,
                    updatedAt = Date(),
                    isSynced = false
                )
            } else {
                // Create new collection
                DailyMilkCollection(
                    date = today,
                    farmerId = farmerId,
                    farmerName = farmerName,
                    amMilk = amMilk,
                    amFat = amFat,
                    amPrice = amPrice,
                    pmMilk = pmMilk,
                    pmFat = pmFat,
                    pmPrice = pmPrice,
                    totalMilk = totalMilk,
                    totalFat = totalFat,
                    totalAmount = totalAmount,
                    isSynced = false
                )
            }

            // Save to local database first (immediate response for user)
            dailyMilkCollectionDao.insertDailyMilkCollection(dailyCollection)
            android.util.Log.d("DailyMilkCollectionRepository", "Saved daily collection locally: ${dailyCollection.farmerName} - ${dailyCollection.date} - Total Amount: ₹${dailyCollection.totalAmount}")

            // Return immediately - don't block UI
            // Trigger background farmer profile update and billing cycle summary update
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    updateFarmerProfileAfterCollection(farmerId)
                    
                    // Update billing cycle summaries if this collection falls within any active billing cycle
                    updateBillingCycleSummariesForCollection(dailyCollection)
                    
                    // Sync to Firestore if online (background operation)
            if (networkUtils.isCurrentlyOnline()) {
                try {
                    syncToFirestore(dailyCollection)
                    android.util.Log.d("DailyMilkCollectionRepository", "Synced daily collection to Firestore: ${dailyCollection.farmerName}")
                } catch (e: Exception) {
                    android.util.Log.e("DailyMilkCollectionRepository", "Failed to sync to Firestore: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DailyMilkCollectionRepository", "Error in background operations: ${e.message}")
                }
            }

            dailyCollection
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error creating today's collection: ${e.message}")
            throw e
        }
    }

    // Update milk collection for a specific session (AM/PM)
    suspend fun updateMilkCollection(
        farmerId: String,
        session: String, // "AM" or "PM"
        milk: Double,
        fat: Double,
        price: Double
    ) = withContext(Dispatchers.IO) {
        try {
            val today = dateFormat.format(Date())

            // Get existing collection or create new one
            var existingCollection = dailyMilkCollectionDao.getDailyMilkCollectionByFarmerAndDate(farmerId, today)

            if (existingCollection == null) {
                // Get farmer name from repository
                val farmerRepository = FarmerRepository(context)
                val farmer = farmerRepository.getFarmerById(farmerId)
                val farmerName = farmer?.name ?: "Unknown Farmer"

                existingCollection = DailyMilkCollection(
                    date = today,
                    farmerId = farmerId,
                    farmerName = farmerName,
                    isSynced = false
                )
            }

            // Update the specific session
            val updatedCollection = when (session.uppercase()) {
                "AM" -> existingCollection.copy(
                    amMilk = milk,
                    amFat = fat,
                    amPrice = price,
                    updatedAt = Date(),
                    isSynced = false
                )
                "PM" -> existingCollection.copy(
                    pmMilk = milk,
                    pmFat = fat,
                    pmPrice = price,
                    updatedAt = Date(),
                    isSynced = false
                )
                else -> throw IllegalArgumentException("Invalid session: $session")
            }

            // Recalculate totals
            val finalCollection = updatedCollection.copy(
                totalMilk = updatedCollection.amMilk + updatedCollection.pmMilk,
                totalFat = if (updatedCollection.amMilk + updatedCollection.pmMilk > 0) {
                    ((updatedCollection.amMilk * updatedCollection.amFat + updatedCollection.pmMilk * updatedCollection.pmFat) / (updatedCollection.amMilk + updatedCollection.pmMilk))
                } else 0.0,
                totalAmount = updatedCollection.amPrice + updatedCollection.pmPrice
            )

            android.util.Log.d("DailyMilkCollectionRepository", "Updated $session session for farmer $farmerId: Milk(${milk}L), Fat(${fat}%), Price(₹$price) -> Total Amount: ₹${finalCollection.totalAmount}")

            // Save to local database first (immediate response)
            dailyMilkCollectionDao.insertDailyMilkCollection(finalCollection)
            android.util.Log.d("DailyMilkCollectionRepository", "Updated $session session for farmer $farmerId: ${finalCollection.farmerName} - Milk: ${milk}L, Fat: ${fat}%, Price: ₹$price")

            // Update farmer profile with new earnings
            updateFarmerProfileAfterCollection(farmerId)

            // Sync to Firestore if online (background operation)
            if (networkUtils.isCurrentlyOnline()) {
                try {
                    syncToFirestore(finalCollection)
                    android.util.Log.d("DailyMilkCollectionRepository", "Synced updated collection to Firestore: ${finalCollection.farmerName}")
                } catch (e: Exception) {
                    android.util.Log.e("DailyMilkCollectionRepository", "Failed to sync to Firestore: ${e.message}")
                    // Don't throw error - local storage is primary
                }
            }

            finalCollection
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error updating milk collection: ${e.message}")
            throw e
        }
    }

    // Get collections for a specific date
    suspend fun getTodayCollections(date: String = dateFormat.format(Date())): List<DailyMilkCollection> = withContext(Dispatchers.IO) {
        try {
            dailyMilkCollectionDao.getDailyMilkCollectionsByDate(date)
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error getting today's collections: ${e.message}")
            emptyList()
        }
    }

    // Get collections for a specific date (alias for getTodayCollections)
    suspend fun getCollectionsByDate(date: String): List<DailyMilkCollection> = withContext(Dispatchers.IO) {
        try {
            dailyMilkCollectionDao.getDailyMilkCollectionsByDate(date)
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error getting collections for date $date: ${e.message}")
            emptyList()
        }
    }

    // Get collections for a farmer in date range (for billing cycle calculations)
    suspend fun getCollectionsByFarmerAndDateRange(farmerId: String, startDate: Date, endDate: Date): List<DailyMilkCollection> = withContext(Dispatchers.IO) {
        try {
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)
            
            android.util.Log.d("DailyMilkCollectionRepository", "Getting collections for farmer $farmerId from $startDateStr to $endDateStr")
            
            val collections = dailyMilkCollectionDao.getDailyMilkCollectionsByFarmerAndDateRange(farmerId, startDateStr, endDateStr)
            
            android.util.Log.d("DailyMilkCollectionRepository", "Found ${collections.size} collections for farmer $farmerId")
            collections.forEach { collection ->
                android.util.Log.d("DailyMilkCollectionRepository", "  ${collection.date}: AM(₹${collection.amPrice}) + PM(₹${collection.pmPrice}) = ₹${collection.totalAmount}")
            }
            
            collections
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error getting collections by date range: ${e.message}")
            emptyList()
        }
    }

    // Calculate total amount for a farmer in date range
    suspend fun calculateTotalAmountForFarmerInDateRange(farmerId: String, startDate: Date, endDate: Date): Double = withContext(Dispatchers.IO) {
        try {
            val collections = getCollectionsByFarmerAndDateRange(farmerId, startDate, endDate)
            val totalAmount = collections.sumOf { it.totalAmount }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Total amount for farmer $farmerId: ₹$totalAmount")
            totalAmount
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error calculating total amount: ${e.message}")
            0.0
        }
    }

    // Get all daily milk collections (for database checks)
    suspend fun getAllDailyMilkCollections(): List<DailyMilkCollection> = withContext(Dispatchers.IO) {
        try {
            val collections = dailyMilkCollectionDao.getAllDailyMilkCollections().first()
            android.util.Log.d("DailyMilkCollectionRepository", "Retrieved ${collections.size} total daily milk collections")
            collections
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error getting all daily milk collections: ${e.message}")
            emptyList()
        }
    }

    // Get all collections for a farmer (for profile calculations)
    suspend fun getAllCollectionsByFarmer(farmerId: String): List<DailyMilkCollection> = withContext(Dispatchers.IO) {
        try {
            val collections = dailyMilkCollectionDao.getDailyMilkCollectionsByFarmer(farmerId)
            android.util.Log.d("DailyMilkCollectionRepository", "Retrieved ${collections.size} collections for farmer $farmerId")
            collections.forEach { collection ->
                android.util.Log.d("DailyMilkCollectionRepository", "  ${collection.date}: AM(₹${collection.amPrice}) + PM(₹${collection.pmPrice}) = Total(₹${collection.totalAmount})")
            }
            collections
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error getting all collections for farmer: ${e.message}")
            emptyList()
        }
    }

    // Calculate total earnings for a farmer (all time)
    suspend fun calculateTotalEarningsForFarmer(farmerId: String): Double = withContext(Dispatchers.IO) {
        try {
            val collections = getAllCollectionsByFarmer(farmerId)
            val totalEarnings = collections.sumOf { it.totalAmount }

            android.util.Log.d("DailyMilkCollectionRepository", "Total earnings for farmer $farmerId: ₹$totalEarnings")
            totalEarnings
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error calculating total earnings: ${e.message}")
            0.0
        }
    }

    // Update farmer profile after milk collection
    private suspend fun updateFarmerProfileAfterCollection(farmerId: String) {
        try {
            // Create a new instance of FarmerProfileCalculator to avoid circular dependency
            val farmerProfileCalculator = FarmerProfileCalculator(context)
            farmerProfileCalculator.onMilkCollectionChanged(farmerId)
            android.util.Log.d("DailyMilkCollectionRepository", "Updated farmer profile after collection: $farmerId")
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error updating farmer profile after collection: ${e.message}")
        }
    }

    // Sync local collection to Firestore
    private suspend fun syncToFirestore(dailyCollection: DailyMilkCollection) = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("DailyMilkCollectionRepository", "Cannot sync to Firestore: User not authenticated")
                return@withContext
            }
            
            val firestoreData = DailyMilkCollectionFirestore(
                farmerId = dailyCollection.farmerId,
                farmerName = dailyCollection.farmerName,
                am_milk = dailyCollection.amMilk,
                am_fat = dailyCollection.amFat,
                am_price = dailyCollection.amPrice,
                pm_milk = dailyCollection.pmMilk,
                pm_fat = dailyCollection.pmFat,
                pm_price = dailyCollection.pmPrice,
                total_milk = dailyCollection.totalMilk,
                total_fat = dailyCollection.totalFat,
                total_amount = dailyCollection.totalAmount,
                created_at = com.google.firebase.Timestamp(dailyCollection.createdAt),
                updated_at = com.google.firebase.Timestamp(dailyCollection.updatedAt)
            )

            android.util.Log.d("DailyMilkCollectionRepository", "Syncing to Firestore path: users/$userId/milk-collection/${dailyCollection.date}/farmers/${dailyCollection.farmerId}")

            // First, ensure the parent date document exists
            firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .document(dailyCollection.date)
                .set(mapOf("exists" to true), com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Then set the farmer's milk collection data
            firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .document(dailyCollection.date)
                .collection("farmers")
                .document(dailyCollection.farmerId)
                .set(firestoreData)
                .await()

            // Mark as synced
            dailyMilkCollectionDao.markDailyMilkCollectionsAsSynced(listOf(dailyCollection.id))
            
            android.util.Log.d("DailyMilkCollectionRepository", "Successfully synced to Firestore: ${dailyCollection.farmerName} - ${dailyCollection.date} for user: $userId")
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error syncing to Firestore: ${e.message}")
            throw e
        }
    }

    // Sync all unsynced collections
    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) return@withContext
        
        try {
            val unsyncedCollections = dailyMilkCollectionDao.getUnsyncedDailyMilkCollections()
            android.util.Log.d("DailyMilkCollectionRepository", "Syncing ${unsyncedCollections.size} unsynced collections")
            
            for (collection in unsyncedCollections) {
                try {
                    syncToFirestore(collection)
                } catch (e: Exception) {
                    android.util.Log.e("DailyMilkCollectionRepository", "Failed to sync collection ${collection.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error syncing with Firestore: ${e.message}")
        }
    }

    // Load collections from Firestore to local database
    suspend fun loadFromFirestore() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) return@withContext
        
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("DailyMilkCollectionRepository", "Cannot load from Firestore: User not authenticated")
                return@withContext
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Loading collections from Firestore for user: $userId")
            
            val datesSnapshot = firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .get().await()
            
            android.util.Log.d("DailyMilkCollectionRepository", "Found ${datesSnapshot.documents.size} date documents for user: $userId")
            
            for (dateDoc in datesSnapshot.documents) {
                val date = dateDoc.id
                
                // Check if the parent document exists
                if (!dateDoc.exists()) {
                    android.util.Log.d("DailyMilkCollectionRepository", "Parent document for date $date does not exist, skipping")
                    continue
                }
                
                android.util.Log.d("DailyMilkCollectionRepository", "Processing date: $date for user: $userId")
                
                val farmersSnapshot = dateDoc.reference.collection("farmers").get().await()
                
                for (farmerDoc in farmersSnapshot.documents) {
                    val data = farmerDoc.data
                    if (data != null) {
                        val dailyCollection = DailyMilkCollection(
                            date = date,
                            farmerId = data["farmerId"] as? String ?: "",
                            farmerName = data["farmerName"] as? String ?: "",
                            amMilk = (data["am_milk"] as? Number)?.toDouble() ?: 0.0,
                            amFat = (data["am_fat"] as? Number)?.toDouble() ?: 0.0,
                            amPrice = (data["am_price"] as? Number)?.toDouble() ?: 0.0,
                            pmMilk = (data["pm_milk"] as? Number)?.toDouble() ?: 0.0,
                            pmFat = (data["pm_fat"] as? Number)?.toDouble() ?: 0.0,
                            pmPrice = (data["pm_price"] as? Number)?.toDouble() ?: 0.0,
                            totalMilk = (data["total_milk"] as? Number)?.toDouble() ?: 0.0,
                            totalFat = (data["total_fat"] as? Number)?.toDouble() ?: 0.0,
                            totalAmount = (data["total_amount"] as? Number)?.toDouble() ?: 0.0,
                            isSynced = true
                        )
                        
                        dailyMilkCollectionDao.insertDailyMilkCollection(dailyCollection)
                    }
                }
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Successfully loaded collections from Firestore")
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error loading from Firestore: ${e.message}")
        }
    }
    
    // Clean up orphaned milk collections (collections from deleted farmers)
    suspend fun cleanupOrphanedCollections() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("DailyMilkCollectionRepository", "Starting cleanup of orphaned milk collections")
            
            val farmerRepository = FarmerRepository(context)
            val existingFarmers = farmerRepository.getAllFarmers()
            val existingFarmerIds = existingFarmers.map { it.id }.toSet()
            
            val allCollections = dailyMilkCollectionDao.getAllDailyMilkCollections().first()
            val orphanedCollections = allCollections.filter { collection ->
                !existingFarmerIds.contains(collection.farmerId)
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Found ${orphanedCollections.size} orphaned collections out of ${allCollections.size} total")
            
            if (orphanedCollections.isNotEmpty()) {
                for (collection in orphanedCollections) {
                    dailyMilkCollectionDao.deleteDailyMilkCollection(collection)
                    android.util.Log.d("DailyMilkCollectionRepository", "Deleted orphaned collection: ${collection.date} - Farmer: ${collection.farmerId}")
                }
                android.util.Log.d("DailyMilkCollectionRepository", "Successfully cleaned up ${orphanedCollections.size} orphaned collections")
            } else {
                android.util.Log.d("DailyMilkCollectionRepository", "No orphaned collections found")
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error cleaning up orphaned collections: ${e.message}")
        }
    }

    // Report methods for MilkReportViewModel
    suspend fun getMilkReportEntries(startDate: Date, endDate: Date): List<MilkReportEntry> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("DailyMilkCollectionRepository", "Getting milk report entries from ${startDate} to ${endDate}")
                
                val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                val startDateStr = dateFormat.format(startDate)
                val endDateStr = dateFormat.format(endDate)
                
                android.util.Log.d("DailyMilkCollectionRepository", "Date range: $startDateStr to $endDateStr")
                
                val collections = dailyMilkCollectionDao.getDailyMilkCollectionsByDateRange(startDateStr, endDateStr)
                android.util.Log.d("DailyMilkCollectionRepository", "Found ${collections.size} collections in date range")
                
                // Filter out collections from deleted farmers
                val farmerRepository = FarmerRepository(context)
                val existingFarmers = farmerRepository.getAllFarmers()
                val existingFarmerIds = existingFarmers.map { it.id }.toSet()
                
                val validCollections = collections.filter { collection ->
                    existingFarmerIds.contains(collection.farmerId)
                }
                
                android.util.Log.d("DailyMilkCollectionRepository", "After filtering deleted farmers: ${validCollections.size} valid collections out of ${collections.size} total")
                
                // Debug: Log each valid collection
                validCollections.forEach { collection ->
                    android.util.Log.d("DailyMilkCollectionRepository", "Valid Collection: ${collection.date} - Farmer: ${collection.farmerId} - AM: ${collection.amMilk}L(₹${collection.amPrice}) PM: ${collection.pmMilk}L(₹${collection.pmPrice})")
                }
                
                val reportEntries = mutableListOf<MilkReportEntry>()
                val collectionsByDate = validCollections.groupBy { it.date }
                
                android.util.Log.d("DailyMilkCollectionRepository", "Grouped into ${collectionsByDate.size} unique dates")
                
                for ((date, dateCollections) in collectionsByDate) {
                    // Sum up all collections for this date
                    val totalAmQuantity = dateCollections.sumOf { it.amMilk }
                    val totalPmQuantity = dateCollections.sumOf { it.pmMilk }
                    val totalAmFat = if (dateCollections.isNotEmpty()) dateCollections.sumOf { it.amFat } / dateCollections.size else 0.0
                    val totalPmFat = if (dateCollections.isNotEmpty()) dateCollections.sumOf { it.pmFat } / dateCollections.size else 0.0
                    val totalAmPrice = dateCollections.sumOf { it.amPrice }
                    val totalPmPrice = dateCollections.sumOf { it.pmPrice }
                    
                    reportEntries.add(
                        MilkReportEntry(
                            date = date,
                            amQuantity = totalAmQuantity,
                            pmQuantity = totalPmQuantity,
                            amFat = totalAmFat,
                            pmFat = totalPmFat,
                            amPrice = totalAmPrice,
                            pmPrice = totalPmPrice,
                            totalQuantity = totalAmQuantity + totalPmQuantity,
                            totalPrice = totalAmPrice + totalPmPrice
                        )
                    )
                }
                
                android.util.Log.d("DailyMilkCollectionRepository", "Generated ${reportEntries.size} report entries")
                reportEntries
            } catch (e: Exception) {
                android.util.Log.e("DailyMilkCollectionRepository", "Error getting milk report entries: ${e.message}")
                emptyList()
            }
        }
    }
    
    suspend fun getMilkReportSummary(startDate: Date, endDate: Date): MilkReportSummary? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("DailyMilkCollectionRepository", "Getting milk report summary from ${startDate} to ${endDate}")
                
                val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                val startDateStr = dateFormat.format(startDate)
                val endDateStr = dateFormat.format(endDate)
                
                val collections = dailyMilkCollectionDao.getDailyMilkCollectionsByDateRange(startDateStr, endDateStr)
                android.util.Log.d("DailyMilkCollectionRepository", "Found ${collections.size} collections for summary")
                
                if (collections.isEmpty()) {
                    return@withContext null
                }
                
                val totalAmQuantity = collections.sumOf { it.amMilk }
                val totalPmQuantity = collections.sumOf { it.pmMilk }
                val totalAmFat = collections.sumOf { it.amFat }
                val totalPmFat = collections.sumOf { it.pmFat }
                val totalAmPrice = collections.sumOf { it.amPrice }
                val totalPmPrice = collections.sumOf { it.pmPrice }
                val totalQuantity = totalAmQuantity + totalPmQuantity
                val totalPrice = totalAmPrice + totalPmPrice
                val averageFat = if (totalQuantity > 0) (totalAmFat + totalPmFat) / collections.size else 0.0
                
                MilkReportSummary(
                    period = "Custom Period",
                    totalDays = collections.size,
                    totalAmQuantity = totalAmQuantity,
                    totalPmQuantity = totalPmQuantity,
                    totalQuantity = totalQuantity,
                    totalAmPrice = totalAmPrice,
                    totalPmPrice = totalPmPrice,
                    totalPrice = totalPrice,
                    averageFat = averageFat
                )
            } catch (e: Exception) {
                android.util.Log.e("DailyMilkCollectionRepository", "Error getting milk report summary: ${e.message}")
                null
            }
        }
    }

    /**
     * Update billing cycle summaries when a milk collection is added
     */
    private suspend fun updateBillingCycleSummariesForCollection(dailyCollection: DailyMilkCollection) {
        try {
            android.util.Log.d("DailyMilkCollectionRepository", "Updating billing cycle summaries for collection: ${dailyCollection.date} - Farmer: ${dailyCollection.farmerId}")
            
            // Get all active billing cycles for this farmer
            val billingCycleRepository = BillingCycleRepository(context)
            val billingCycles = billingCycleRepository.getFarmerBillingDetailsByFarmer(dailyCollection.farmerId).first()
            
            android.util.Log.d("DailyMilkCollectionRepository", "Found ${billingCycles.size} billing cycles for farmer ${dailyCollection.farmerId}")
            
            // Find which billing cycle this milk collection belongs to
            val collectionDate = dateFormat.parse(dailyCollection.date)
            android.util.Log.d("DailyMilkCollectionRepository", "Collection date: $collectionDate")
            
            var foundMatchingCycle = false
            
            for (billingCycle in billingCycles) {
                val billingCycleDetails = billingCycleRepository.getBillingCycleById(billingCycle.billingCycleId)
                if (billingCycleDetails != null) {
                    val startDate = billingCycleDetails.startDate
                    val endDate = billingCycleDetails.endDate
                    
                    android.util.Log.d("DailyMilkCollectionRepository", "Checking cycle: ${billingCycle.billingCycleId} (${startDate} to ${endDate})")
                    
                    // Check if collection date falls within this billing cycle
                    if (collectionDate != null && collectionDate >= startDate && collectionDate <= endDate) {
                        android.util.Log.d("DailyMilkCollectionRepository", "Collection date ${collectionDate} falls within billing cycle ${billingCycle.billingCycleId}")
                        
                        // Update the billing cycle summary in background to avoid blocking UI
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                billingCycleSummaryRepository.updateBillingCycleSummary(
                                    billingCycleId = billingCycle.billingCycleId,
                                    farmerId = dailyCollection.farmerId,
                                    milkCollection = dailyCollection
                                )
                                android.util.Log.d("DailyMilkCollectionRepository", "Successfully updated billing cycle summary for cycle: ${billingCycle.billingCycleId}")
                            } catch (e: Exception) {
                                android.util.Log.e("DailyMilkCollectionRepository", "Error updating billing cycle summary: ${e.message}")
                            }
                        }
                        
                        foundMatchingCycle = true
                        break
                    } else {
                        android.util.Log.d("DailyMilkCollectionRepository", "Collection date ${collectionDate} does NOT fall within billing cycle ${billingCycle.billingCycleId}")
                    }
                } else {
                    android.util.Log.e("DailyMilkCollectionRepository", "Billing cycle details not found for ID: ${billingCycle.billingCycleId}")
                }
            }
            
            if (!foundMatchingCycle) {
                android.util.Log.d("DailyMilkCollectionRepository", "No matching billing cycle found for collection date: ${dailyCollection.date}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error updating billing cycle summaries: ${e.message}")
            android.util.Log.e("DailyMilkCollectionRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Restore milk collections from Firestore to local Room database
     * This function fetches data from the Firestore structure: "users/{userId}/milk-collection -> [DATE] -> farmers -> [FARMER_ID] -> { milk data }"
     */
    suspend fun restoreMilkCollectionsFromFirestore(userId: String) = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("DailyMilkCollectionRepository", "Network not available, skipping restoreMilkCollectionsFromFirestore")
            return@withContext
        }
        
        try {
            android.util.Log.d("DailyMilkCollectionRepository", "Starting restoration of milk collections from Firestore for user: $userId")
            android.util.Log.d("DailyMilkCollectionRepository", "Using Firestore path: users/$userId/milk-collection/")
            
            var totalCollectionsRestored = 0
            
            // 1. Fetch all documents from "users/{userId}/milk-collection"
            val dateDocs = firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .get().await()
            android.util.Log.d("DailyMilkCollectionRepository", "Found ${dateDocs.documents.size} date documents in users/$userId/milk-collection")
            
            for (dateDoc in dateDocs.documents) {
                val date = dateDoc.id
                android.util.Log.d("DailyMilkCollectionRepository", "Processing date: $date")
                
                try {
                    // 2. For each dateDoc, get the farmers collection
                    val farmersSnapshot = firestore.collection("users")
                        .document(userId)
                        .collection("milk-collection")
                        .document(dateDoc.id)
                        .collection("farmers")
                        .get().await()
                    
                    android.util.Log.d("DailyMilkCollectionRepository", "Found ${farmersSnapshot.documents.size} farmer documents for date: $date")
                    
                    // 3. For each farmerDoc, convert to DailyMilkCollection and insert into Room
                    for (farmerDoc in farmersSnapshot.documents) {
                        val farmerId = farmerDoc.id
                        val data = farmerDoc.data
                        
                        if (data != null) {
                            android.util.Log.d("DailyMilkCollectionRepository", "Restoring from users/$userId/milk-collection/$date/farmers/$farmerId")
                            
                            // Convert Firestore document to DailyMilkCollection object
                            val dailyCollection = DailyMilkCollection(
                                date = date,
                                farmerId = farmerId,
                                farmerName = data["farmerName"] as? String ?: "",
                                amMilk = (data["am_milk"] as? Number)?.toDouble() ?: 0.0,
                                amFat = (data["am_fat"] as? Number)?.toDouble() ?: 0.0,
                                amPrice = (data["am_price"] as? Number)?.toDouble() ?: 0.0,
                                pmMilk = (data["pm_milk"] as? Number)?.toDouble() ?: 0.0,
                                pmFat = (data["pm_fat"] as? Number)?.toDouble() ?: 0.0,
                                pmPrice = (data["pm_price"] as? Number)?.toDouble() ?: 0.0,
                                totalMilk = (data["total_milk"] as? Number)?.toDouble() ?: 0.0,
                                totalFat = (data["total_fat"] as? Number)?.toDouble() ?: 0.0,
                                totalAmount = (data["total_amount"] as? Number)?.toDouble() ?: 0.0,
                                isSynced = true
                            )
                            
                            // Check if this collection already exists locally
                            val existingCollection = dailyMilkCollectionDao.getDailyMilkCollectionByFarmerAndDate(
                                farmerId, 
                                dailyCollection.date
                            )
                            
                            if (existingCollection == null) {
                                // Insert into local Room database
                                dailyMilkCollectionDao.insertDailyMilkCollection(dailyCollection)
                                totalCollectionsRestored++
                                android.util.Log.d("DailyMilkCollectionRepository", "Restored ${dailyCollection.farmerName} - ${dailyCollection.date} - Total: ₹${dailyCollection.totalAmount}")
                            } else {
                                android.util.Log.d("DailyMilkCollectionRepository", "Skipped existing milk collection: ${dailyCollection.farmerName} - ${dailyCollection.date}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DailyMilkCollectionRepository", "Error processing date $date: ${e.message}")
                }
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Successfully restored $totalCollectionsRestored milk collections from Firestore")
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error during milk collections restoration: ${e.message}")
            android.util.Log.e("DailyMilkCollectionRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Utility function to fix existing Firestore documents by ensuring parent date documents exist
     * This should be called once to fix existing data structure for a specific user
     */
    suspend fun fixExistingFirestoreDocuments(userId: String) = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("DailyMilkCollectionRepository", "Network not available, skipping fixExistingFirestoreDocuments")
            return@withContext
        }
        
        try {
            android.util.Log.d("DailyMilkCollectionRepository", "Starting to fix existing Firestore documents for user: $userId")
            android.util.Log.d("DailyMilkCollectionRepository", "Using Firestore path: users/$userId/milk-collection/")
            
            // Get all dates that have subcollections but no parent document
            val datesSnapshot = firestore.collection("users")
                .document(userId)
                .collection("milk-collection")
                .get().await()
            var fixedCount = 0
            
            for (dateDoc in datesSnapshot.documents) {
                val date = dateDoc.id
                
                // Check if the parent document exists
                if (!dateDoc.exists()) {
                    android.util.Log.d("DailyMilkCollectionRepository", "Creating parent document for date: $date for user: $userId")
                    
                    // Create the parent document with a dummy field
                    firestore.collection("users")
                        .document(userId)
                        .collection("milk-collection")
                        .document(date)
                        .set(mapOf("exists" to true, "created_at" to com.google.firebase.Timestamp.now()))
                        .await()
                    
                    fixedCount++
                    android.util.Log.d("DailyMilkCollectionRepository", "Created parent document for date: $date for user: $userId")
                }
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Successfully fixed $fixedCount existing Firestore documents")
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error fixing existing Firestore documents: ${e.message}")
            android.util.Log.e("DailyMilkCollectionRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    /**
     * Convenience method to restore milk collections for the current logged-in user
     * This should be called after app reinstall or login to restore user's data
     */
    suspend fun restoreMilkCollectionsForCurrentUser() = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("DailyMilkCollectionRepository", "Cannot restore data: User not authenticated")
                return@withContext
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Starting restoration for current user: $userId")
            restoreMilkCollectionsFromFirestore(userId)
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error restoring data for current user: ${e.message}")
        }
    }
    
    /**
     * Convenience method to fix Firestore documents for the current logged-in user
     */
    suspend fun fixFirestoreDocumentsForCurrentUser() = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("DailyMilkCollectionRepository", "Cannot fix documents: User not authenticated")
                return@withContext
            }
            
            android.util.Log.d("DailyMilkCollectionRepository", "Fixing Firestore documents for current user: $userId")
            fixExistingFirestoreDocuments(userId)
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error fixing documents for current user: ${e.message}")
        }
    }

    /**
     * Clean up duplicate milk collection entries for the same farmer and date
     * This removes all but the most recent entry for each farmer-date combination
     */
    suspend fun cleanupDuplicateEntries() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("DailyMilkCollectionRepository", "Starting cleanup of duplicate milk collection entries")
            
            // Get count before cleanup
            val beforeCount = dailyMilkCollectionDao.getAllDailyMilkCollections().first().size
            
            // Remove duplicates
            dailyMilkCollectionDao.removeDuplicateEntries()
            
            // Get count after cleanup
            val afterCount = dailyMilkCollectionDao.getAllDailyMilkCollections().first().size
            val removedCount = beforeCount - afterCount
            
            android.util.Log.d("DailyMilkCollectionRepository", "Cleanup completed: removed $removedCount duplicate entries (before: $beforeCount, after: $afterCount)")
            
        } catch (e: Exception) {
            android.util.Log.e("DailyMilkCollectionRepository", "Error cleaning up duplicate entries: ${e.message}")
        }
    }
}
         