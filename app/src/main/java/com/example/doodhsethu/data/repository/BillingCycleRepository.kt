package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.*
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.utils.FarmerProfileCalculator
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.*
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await

class BillingCycleRepository(private val context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val billingCycleDao = db.billingCycleDao()
    private val farmerBillingDetailDao = db.farmerBillingDetailDao()
    private val farmerRepository = FarmerRepository(context)
    private val dailyMilkCollectionRepository = DailyMilkCollectionRepository(context)
    private val billingCycleSummaryRepository = BillingCycleSummaryRepository(context)
    private val networkUtils = NetworkUtils(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val authViewModel = AuthViewModel()
    




    // Get all billing cycles
    fun getAllBillingCycles(): Flow<List<BillingCycle>> {
        return billingCycleDao.getAllBillingCycles()
    }

    // Get farmer billing details for a specific farmer
    fun getFarmerBillingDetailsByFarmer(farmerId: String): Flow<List<FarmerBillingDetail>> {
        return farmerBillingDetailDao.getFarmerBillingDetailsByFarmer(farmerId)
    }

    // Get farmer payment summary (total amounts and balances)
    @Suppress("unused")
    suspend fun getFarmerPaymentSummary(farmerId: String): FarmerPaymentSummary? {
        val farmer = farmerRepository.getFarmerById(farmerId) ?: return null
        val billingDetails = farmerBillingDetailDao.getFarmerBillingDetailsByFarmer(farmerId).first()
        val totalOriginalAmount = billingDetails.sumOf { it.originalAmount }
        val totalPaidAmount = billingDetails.sumOf { it.paidAmount }
        val totalBalanceAmount = totalOriginalAmount - totalPaidAmount
        return FarmerPaymentSummary(
            farmerId = farmerId,
            farmerName = farmer.name,
            totalOriginalAmount = totalOriginalAmount,
            totalPaidAmount = totalPaidAmount,
            totalBalanceAmount = totalBalanceAmount,
            billingCycles = billingDetails
        )
    }

    // Get a single billing cycle by ID
    suspend fun getBillingCycleById(billingCycleId: String): BillingCycle? {
        return billingCycleDao.getBillingCycleById(billingCycleId)
    }

    // Get farmer billing details by billing cycle ID
    suspend fun getFarmerBillingDetailsByBillingCycleId(billingCycleId: String): List<FarmerBillingDetail> {
        return farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(billingCycleId)
    }

    // Create a new billing cycle
    suspend fun createBillingCycle(startDate: Date, endDate: Date): BillingCycle = withContext(Dispatchers.IO) {
        try {
            // Generate a consistent ID that will be used for both local storage and Firestore
            val billingCycleId = generateBillingCycleId(startDate, endDate)
            
            // Generate billing cycle name
        val existingCycles = billingCycleDao.getAllBillingCycles().first()
            val billingCycleName = generateBillingCycleName(startDate, existingCycles)
            android.util.Log.d("BillingCycleRepository", "Generated billing cycle name: $billingCycleName")
            
            android.util.Log.d("BillingCycleRepository", "Creating billing cycle from $startDate to $endDate")
            
            // Calculate total amount for the date range
            var totalAmount = 0.0
            val allFarmers = farmerRepository.getAllFarmers()
            for (farmer in allFarmers) {
                val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(farmer.id, startDate, endDate)
                totalAmount += collections.sumOf { it.totalAmount }
            }
            android.util.Log.d("BillingCycleRepository", "Total amount for date range $startDate to $endDate: ₹$totalAmount")
            
            // Create billing cycle with consistent ID
        val billingCycle = BillingCycle(
                id = billingCycleId,
                name = billingCycleName,
            startDate = startDate,
            endDate = endDate,
            totalAmount = totalAmount,
                createdAt = Date(),
                isSynced = false
            )
            
            android.util.Log.d("BillingCycleRepository", "Billing cycle name: ${billingCycle.name}")
            
            // Get all farmers
            val farmers = farmerRepository.getAllFarmers()
            android.util.Log.d("BillingCycleRepository", "Found ${farmers.size} farmers")
            
            // Calculate total amount for each farmer in the date range
            var totalAmountForAllFarmers = 0.0
            val farmersWithEarnings = mutableListOf<Farmer>()
            
            for (farmer in farmers) {
                val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(
                    farmer.id, startDate, endDate
                )
                
                val farmerTotal = collections.sumOf { it.totalAmount }
                android.util.Log.d("BillingCycleRepository", "Date range: $startDate to $endDate")
                android.util.Log.d("BillingCycleRepository", "Farmer ${farmer.name}: ${collections.size} collections, total: ₹$farmerTotal")
                
                if (farmerTotal > 0) {
                    totalAmountForAllFarmers += farmerTotal
                    farmersWithEarnings.add(farmer)
                }
            }
            
            android.util.Log.d("BillingCycleRepository", "Total amount for all farmers: ₹$totalAmountForAllFarmers")
            android.util.Log.d("BillingCycleRepository", "Farmers with earnings: ${farmersWithEarnings.size}")
            
            // Insert billing cycle to local database
            billingCycleDao.insertBillingCycle(billingCycle)
            android.util.Log.d("BillingCycleRepository", "Created billing cycle: ${billingCycle.id} - ${billingCycle.name}")
            android.util.Log.d("BillingCycleRepository", "Successfully inserted billing cycle to local database")
            
            // Create farmer billing details for all farmers (even those with zero earnings)
            for (farmer in farmers) {
                val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(
                    farmer.id, startDate, endDate
                )
                val farmerTotal = collections.sumOf { it.totalAmount }
                
                val farmerDetail = FarmerBillingDetail(
                    id = "${farmer.id}_$billingCycleId",
                    farmerId = farmer.id,
                    farmerName = farmer.name,
                    billingCycleId = billingCycleId,
                    originalAmount = farmerTotal,
                    paidAmount = farmerTotal, // Assuming if it's in Firestore, it's paid
                    balanceAmount = 0.0,
                    isPaid = true,
                    paymentDate = Date(),
                    isSynced = true
                )
                
                farmerBillingDetailDao.insertFarmerBillingDetail(farmerDetail)
                android.util.Log.d("BillingCycleRepository", "Inserted farmer detail: ${farmer.name} - ₹$farmerTotal for cycle ${billingCycle.id}")
            }
            
            // Initialize billing cycle summaries in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    android.util.Log.d("BillingCycleRepository", "Initializing billing cycle summaries in background")
                    initializeBillingCycleSummaries(billingCycleId, farmers)
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Failed to initialize billing cycle summaries: ${e.message}")
                }
            }
            
            // Sync to Firestore in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    android.util.Log.d("BillingCycleRepository", "Starting optimized syncAllDataWhenOnline for BillingCycleRepository")
                    syncAllDataWhenOnline()
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Failed to sync billing cycle to Firestore: ${e.message}")
                }
            }
            
            billingCycle
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error creating billing cycle: ${e.message}")
            throw e
        }
    }
    
    // Generate a consistent billing cycle ID based on date range
    private fun generateBillingCycleId(startDate: Date, endDate: Date): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val startStr = dateFormat.format(startDate)
        val endStr = dateFormat.format(endDate)
        return "billing_${startStr}_${endStr}"
    }
    
    // Initialize billing cycle summaries for all farmers
    private suspend fun initializeBillingCycleSummaries(billingCycleId: String, farmers: List<Farmer>) {
        android.util.Log.d("BillingCycleRepository", "Billing cycle ID: $billingCycleId")
        android.util.Log.d("BillingCycleRepository", "Number of farmers: ${farmers.size}")
        
        for (farmer in farmers) {
            // Get the billing cycle to access start and end dates
            val billingCycle = billingCycleDao.getBillingCycleById(billingCycleId)
            if (billingCycle != null) {
                val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(
                    farmer.id, billingCycle.startDate, billingCycle.endDate
                )
                val farmerTotal = collections.sumOf { it.totalAmount }
                
                // Initialize billing cycle summary for this farmer in Firestore with actual amounts
                android.util.Log.d("BillingCycleRepository", "Initializing billing cycle summary for farmer ${farmer.id} with amount ₹$farmerTotal")
                
                try {
                    billingCycleSummaryRepository.initializeBillingCycleSummary(
                        billingCycleId = billingCycleId,
                        farmerId = farmer.id,
                        farmerName = farmer.name,
                        totalAmount = farmerTotal,
                        totalMilk = 0.0, // Will be updated when milk collections are processed
                        totalFat = 0.0   // Will be updated when milk collections are processed
                    )
                    android.util.Log.d("BillingCycleRepository", "Completed billing cycle summary initialization for farmer ${farmer.id}")
                    } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error initializing billing cycle summary for farmer ${farmer.id}: ${e.message}")
                }
            }
        }
        android.util.Log.d("BillingCycleRepository", "Initialized billing cycle summaries for ${farmers.size} farmers")
    }

    // Delete a billing cycle
    suspend fun deleteBillingCycle(billingCycle: BillingCycle) {
        try {
            // Get affected farmer details before deleting the billing cycle
            val affectedFarmerDetails = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(billingCycle.id)

            // Delete from local database first
            billingCycleDao.deleteBillingCycle(billingCycle)
            
            // Delete farmer billing details one by one
            for (farmerDetail in affectedFarmerDetails) {
                farmerBillingDetailDao.deleteFarmerBillingDetail(farmerDetail)
            }
            
            android.util.Log.d("BillingCycleRepository", "Successfully deleted billing cycle ${billingCycle.id} from local database")

            // Delete from Firestore if online
            if (networkUtils.isCurrentlyOnline()) {
                try {
                    val userId = authViewModel.getStoredUser(context)?.userId ?: return
                    android.util.Log.d("BillingCycleRepository", "Deleting billing cycle ${billingCycle.id} from Firestore for user: $userId")
                    
                    // Delete the main billing cycle document from Firestore
                    firestore.collection("users").document(userId)
                        .collection("billing_cycle")
                        .document(billingCycle.id)
                        .delete()
                        .await()
                    
                    android.util.Log.d("BillingCycleRepository", "Successfully deleted billing cycle ${billingCycle.id} from Firestore")
                    
                    // Delete individual farmer billing details from Firestore
                    for (farmerDetail in affectedFarmerDetails) {
                        try {
                            firestore.collection("users").document(userId)
                                .collection("farmers")
                                .document(farmerDetail.farmerId)
                                .collection("billing_cycle")
                                .document(billingCycle.id)
                                .delete()
                                .await()
                            
                            android.util.Log.d("BillingCycleRepository", "Deleted farmer billing detail from Firestore: ${farmerDetail.farmerId} - ${billingCycle.id}")
                        } catch (e: Exception) {
                            android.util.Log.e("BillingCycleRepository", "Error deleting farmer billing detail from Firestore: ${farmerDetail.farmerId} - ${billingCycle.id}: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error deleting billing cycle from Firestore: ${e.message}")
                    android.util.Log.e("BillingCycleRepository", "Stack trace: ${e.stackTraceToString()}")
                }
            } else {
                android.util.Log.d("BillingCycleRepository", "Network not available, skipping Firestore deletion")
            }

            // Update farmer profiles and delete billing cycle summaries in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    android.util.Log.d("BillingCycleRepository", "Updating farmer profiles after billing cycle deletion")
                    val farmerProfileCalculator = FarmerProfileCalculator(context) // Create instance here
                    for (farmerDetail in affectedFarmerDetails) {
                        farmerProfileCalculator.updateFarmerProfile(farmerDetail.farmerId)
                        
                        // Delete billing cycle summary from Firestore
                        billingCycleSummaryRepository.deleteBillingCycleSummary(
                            billingCycleId = billingCycle.id,
                            farmerId = farmerDetail.farmerId
                        )
                    }
                    android.util.Log.d("BillingCycleRepository", "Updated ${affectedFarmerDetails.size} farmers after billing cycle deletion")
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error updating farmer profiles or deleting billing cycle summaries: ${e.message}")
                    android.util.Log.e("BillingCycleRepository", "Stack trace: ${e.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error deleting billing cycle: ${e.message}")
            throw e
        }
    }
    
    // Generate billing cycle name (e.g., "1st billing_cycle:aug")
    private fun generateBillingCycleName(startDate: Date, existingCycles: List<BillingCycle>): String {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        val monthAbbr = SimpleDateFormat("MMM", Locale.getDefault()).format(startDate).lowercase(Locale.getDefault())
        
        // Count unique billing cycles for the same month and year (group by date range to avoid counting duplicates)
        val uniqueCyclesForMonth = existingCycles.filter {
            val existingCalendar = Calendar.getInstance()
            existingCalendar.time = it.startDate
            existingCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
            existingCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
        }.groupBy { "${it.startDate.time}_${it.endDate.time}" }.size
        
        val ordinal = when (uniqueCyclesForMonth + 1) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            else -> "${uniqueCyclesForMonth + 1}th"
        }
        
        val result = "$ordinal billing_cycle:$monthAbbr"
        android.util.Log.d("BillingCycleRepository", "Generated billing cycle name: $result (month: $monthAbbr, unique cycles: ${uniqueCyclesForMonth + 1})")
        return result
    }

    // Clean up duplicate billing cycles and farmer billing details
    suspend fun cleanupDuplicateData() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BillingCycleRepository", "Starting cleanup of duplicate data")
            
            // Get all billing cycles
            val allBillingCycles = billingCycleDao.getAllBillingCycles().first()
            android.util.Log.d("BillingCycleRepository", "Found ${allBillingCycles.size} billing cycles before cleanup")
            
            // Group billing cycles by name to find duplicates
            val cyclesByName = allBillingCycles.groupBy { it.name }
            val duplicates = cyclesByName.filter { it.value.size > 1 }
            
            if (duplicates.isNotEmpty()) {
                android.util.Log.d("BillingCycleRepository", "Found ${duplicates.size} duplicate billing cycle names")
                
                for ((name, cycles) in duplicates) {
                    android.util.Log.d("BillingCycleRepository", "Duplicate name '$name' has ${cycles.size} entries")
                    
                    // Only delete if they have the same date range (true duplicates)
                    val cyclesByDateRange = cycles.groupBy { "${it.startDate.time}_${it.endDate.time}" }
                    
                    for ((dateRange, cyclesWithSameDate) in cyclesByDateRange) {
                        if (cyclesWithSameDate.size > 1) {
                            android.util.Log.d("BillingCycleRepository", "Found ${cyclesWithSameDate.size} cycles with same date range: $dateRange")
                            
                            // Keep the one with the most recent creation date, delete the rest
                            val sortedCycles = cyclesWithSameDate.sortedBy { it.createdAt }
                            val toDelete = sortedCycles.drop(1)
                            
                    for (cycle in toDelete) {
                        // Delete associated farmer billing details first
                        val farmerDetails = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycle.id)
                        for (detail in farmerDetails) {
                            farmerBillingDetailDao.deleteFarmerBillingDetail(detail)
                        }
                        
                        // Delete the billing cycle
                        billingCycleDao.deleteBillingCycle(cycle)
                        android.util.Log.d("BillingCycleRepository", "Deleted duplicate billing cycle: ${cycle.id}")
                            }
                        }
                    }
                }
            }
            
            // Check for billing cycles with similar names (different formatting)
            val remainingCycles = billingCycleDao.getAllBillingCycles().first()
            val cyclesToDelete = mutableListOf<BillingCycle>()
            
            for (i in remainingCycles.indices) {
                for (j in (i + 1) until remainingCycles.size) {
                    val cycle1 = remainingCycles[i]
                    val cycle2 = remainingCycles[j]
                    
                    // Check if cycles have similar names (ignore formatting differences)
                    val name1 = normalizeBillingCycleName(cycle1.name)
                    val name2 = normalizeBillingCycleName(cycle2.name)
                    
                    if (name1 == name2) {
                        android.util.Log.d("BillingCycleRepository", "Found similar names: '${cycle1.name}' and '${cycle2.name}' (normalized: '$name1')")
                        
                        // Check if they have overlapping date ranges
                        val hasOverlap = (cycle1.startDate <= cycle2.endDate && cycle1.endDate >= cycle2.startDate)
                        
                        if (hasOverlap) {
                            android.util.Log.d("BillingCycleRepository", "Cycles have overlapping dates, marking for deletion")
                            // Keep the one with the more recent creation date, delete the other
                            val toDelete = if (cycle1.createdAt.after(cycle2.createdAt)) cycle2 else cycle1
                            if (!cyclesToDelete.contains(toDelete)) {
                                cyclesToDelete.add(toDelete)
                            }
                        }
                    }
                }
            }
            
            // Delete the identified duplicate cycles
            for (cycle in cyclesToDelete) {
                android.util.Log.d("BillingCycleRepository", "Deleting duplicate cycle with similar name: ${cycle.id} - ${cycle.name}")
                
                // Delete associated farmer billing details first
                val farmerDetails = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycle.id)
                for (detail in farmerDetails) {
                    farmerBillingDetailDao.deleteFarmerBillingDetail(detail)
                }
                
                // Delete the billing cycle
                billingCycleDao.deleteBillingCycle(cycle)
            }
            
            // Get all farmer billing details
            val allFarmerDetails = mutableListOf<FarmerBillingDetail>()
            val finalCycles = billingCycleDao.getAllBillingCycles().first()
            for (cycle in finalCycles) {
                allFarmerDetails.addAll(farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycle.id))
            }
            
            android.util.Log.d("BillingCycleRepository", "Found ${allFarmerDetails.size} farmer billing details before cleanup")
            
            // Group farmer details by farmerId and billingCycleId to find duplicates
            val detailsByKey = allFarmerDetails.groupBy { "${it.farmerId}_${it.billingCycleId}" }
            val duplicateDetails = detailsByKey.filter { it.value.size > 1 }
            
            if (duplicateDetails.isNotEmpty()) {
                android.util.Log.d("BillingCycleRepository", "Found ${duplicateDetails.size} duplicate farmer billing details")
                
                for ((key, details) in duplicateDetails) {
                    android.util.Log.d("BillingCycleRepository", "Duplicate key '$key' has ${details.size} entries")
                    
                    // Keep the first one, delete the rest
                    val toDelete = details.drop(1)
                    for (detail in toDelete) {
                        farmerBillingDetailDao.deleteFarmerBillingDetail(detail)
                        android.util.Log.d("BillingCycleRepository", "Deleted duplicate farmer billing detail: ${detail.id}")
                    }
                }
            }
            
            val finalBillingCycles = billingCycleDao.getAllBillingCycles().first()
            val finalFarmerDetails = mutableListOf<FarmerBillingDetail>()
            for (cycle in finalBillingCycles) {
                finalFarmerDetails.addAll(farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycle.id))
            }
            
            android.util.Log.d("BillingCycleRepository", "Cleanup completed. Final count: ${finalBillingCycles.size} billing cycles, ${finalFarmerDetails.size} farmer details")
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error during cleanup: ${e.message}")
        }
    }
    
    // Normalize billing cycle name to handle formatting differences
    private fun normalizeBillingCycleName(name: String): String {
        return name.lowercase()
            .replace("_", " ")
            .replace(":", " ")
            .replace(" ", "")
            .trim()
    }
    
    // Force cleanup all duplicate billing cycles (more aggressive)
    suspend fun forceCleanupAllDuplicates() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BillingCycleRepository", "Starting FORCE cleanup of all duplicate data")
            
            // Get all billing cycles
            val allBillingCycles = billingCycleDao.getAllBillingCycles().first()
            android.util.Log.d("BillingCycleRepository", "Found ${allBillingCycles.size} billing cycles before force cleanup")
            
            if (allBillingCycles.size <= 1) {
                android.util.Log.d("BillingCycleRepository", "Only ${allBillingCycles.size} billing cycle found, no cleanup needed")
                return@withContext
            }
            
            // Group by date range to find duplicates
            val cyclesByDateRange = mutableMapOf<String, MutableList<BillingCycle>>()
            
            for (cycle in allBillingCycles) {
                val dateKey = "${cycle.startDate.time}_${cycle.endDate.time}"
                if (!cyclesByDateRange.containsKey(dateKey)) {
                    cyclesByDateRange[dateKey] = mutableListOf()
                }
                cyclesByDateRange[dateKey]?.add(cycle)
            }
            
            // Find and delete duplicates
            var deletedCount = 0
            for ((dateKey, cycles) in cyclesByDateRange) {
                if (cycles.size > 1) {
                    android.util.Log.d("BillingCycleRepository", "Found ${cycles.size} cycles with same date range: $dateKey")
                    
                    // Sort by creation date and keep the most recent one
                    val sortedCycles = cycles.sortedBy { it.createdAt }
                    val toDelete = sortedCycles.drop(1) // Keep the most recent, delete the rest
                    
                    for (cycle in toDelete) {
                        android.util.Log.d("BillingCycleRepository", "Force deleting duplicate cycle: ${cycle.id} - ${cycle.name}")
                        
                        // Delete associated farmer billing details first
                        val farmerDetails = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycle.id)
                        for (detail in farmerDetails) {
                            farmerBillingDetailDao.deleteFarmerBillingDetail(detail)
                        }
                        
                        // Delete the billing cycle
                        billingCycleDao.deleteBillingCycle(cycle)
                        deletedCount++
                    }
                }
            }
            
            val finalCycles = billingCycleDao.getAllBillingCycles().first()
            android.util.Log.d("BillingCycleRepository", "Force cleanup completed. Deleted $deletedCount cycles. Final count: ${finalCycles.size} billing cycles")
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error during force cleanup: ${e.message}")
        }
    }

    // DEPRECATED: This method is replaced by restoreBillingCyclesFromFirestore(userId: String)
    // Keeping this stub to prevent compilation errors in other parts of the code
    suspend fun loadFromFirestore() = withContext(Dispatchers.IO) {
        android.util.Log.d("BillingCycleRepository", "loadFromFirestore() is deprecated. Use restoreBillingCyclesFromFirestore(userId: String) instead.")
        // This method is intentionally empty to prevent duplicate billing cycle creation
    }

    // Sync all unsynced data to Firestore
    suspend fun syncAllDataWhenOnline() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("BillingCycleRepository", "Network not available, skipping syncAllDataWhenOnline")
            return@withContext
        }

        try {
            android.util.Log.d("BillingCycleRepository", "Starting optimized syncAllDataWhenOnline for BillingCycleRepository")

            // Get all billing cycles that need syncing (only unsynced ones)
            val unsyncedBillingCycles = billingCycleDao.getUnsyncedBillingCycles()
            android.util.Log.d("BillingCycleRepository", "Found ${unsyncedBillingCycles.size} unsynced billing cycles")
            
            if (unsyncedBillingCycles.isEmpty()) {
                android.util.Log.d("BillingCycleRepository", "No unsynced billing cycles found, skipping sync")
                return@withContext
            }
            
            // Process only unsynced billing cycles to reduce Firestore operations
            for (billingCycle in unsyncedBillingCycles) {
                val farmerDetails = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(billingCycle.id)
                android.util.Log.d("BillingCycleRepository", "Syncing billing cycle ${billingCycle.id} with ${farmerDetails.size} farmer details")
                
                // Use coroutines to process farmer details in parallel for better performance
                val syncJobs = farmerDetails.map { farmerDetail ->
                    CoroutineScope(Dispatchers.IO).async {
                        try {
                            billingCycleSummaryRepository.initializeBillingCycleSummary(
                                billingCycleId = billingCycle.id,
                                farmerId = farmerDetail.farmerId,
                                farmerName = farmerDetail.farmerName,
                                totalAmount = farmerDetail.originalAmount,
                                totalMilk = 0.0,
                                totalFat = 0.0
                            )
                            android.util.Log.d("BillingCycleRepository", "Synced farmer ${farmerDetail.farmerId} for cycle ${billingCycle.id}")
        } catch (e: Exception) {
                            android.util.Log.e("BillingCycleRepository", "Failed to sync farmer ${farmerDetail.farmerId}: ${e.message}")
                        }
                    }
                }
                
                // Wait for all farmer details to be synced
                syncJobs.awaitAll()
                
                // Mark billing cycle as synced
                billingCycleDao.markBillingCycleAsSynced(billingCycle.id)
                android.util.Log.d("BillingCycleRepository", "Marked billing cycle ${billingCycle.id} as synced")
            }

            android.util.Log.d("BillingCycleRepository", "Successfully synced ${unsyncedBillingCycles.size} billing cycles to Firestore")
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error syncing data: ${e.message}")
            android.util.Log.e("BillingCycleRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Restore billing cycles and farmer billing details from Firestore to local Room database
     * This function fetches data from the Firestore structure: "billing_cycle -> [cycleId] -> farmers -> [farmerId] -> { billing data }"
     */
    suspend fun restoreBillingCyclesFromFirestore() = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("BillingCycleRepository", "Network not available, skipping restoreBillingCyclesFromFirestore")
            return@withContext
        }
        
        try {
            android.util.Log.d("BillingCycleRepository", "Starting restoration of billing cycles from Firestore")
            
            var totalBillingCyclesRestored = 0
            var totalFarmerDetailsRestored = 0
            
            // Track processed billing cycles to avoid duplicates
            val processedBillingCycleIds = mutableSetOf<String>()
            val processedFarmerDetailIds = mutableSetOf<String>()
            
            // 1. Fetch all documents from billing_cycle collection
            val billingCyclesSnapshot = firestore.collection("billing_cycle").get().await()
            android.util.Log.d("BillingCycleRepository", "Found ${billingCyclesSnapshot.documents.size} billing cycle documents in billing_cycle")
            
            for (billingCycleDoc in billingCyclesSnapshot.documents) {
                val cycleId = billingCycleDoc.id
                val cycleData = billingCycleDoc.data
                
                android.util.Log.d("BillingCycleRepository", "Processing billing cycle: $cycleId")
                
                try {
                    // 2. Fetch nested farmer data for this billing cycle
                    val farmersSnapshot = firestore.collection("billing_cycle")
                        .document(cycleId)
                        .collection("farmers")
                        .get().await()
                    
                    android.util.Log.d("BillingCycleRepository", "Found ${farmersSnapshot.documents.size} farmer documents for billing cycle: $cycleId")
                    
                    // 3. Process each farmer's billing data
                    for (farmerDoc in farmersSnapshot.documents) {
                        val farmerId = farmerDoc.id
                        val farmerData = farmerDoc.data
                        
                        if (farmerData != null) {
                            android.util.Log.d("BillingCycleRepository", "Restoring from billing_cycle/$cycleId/farmers/$farmerId")
                            
                            // Process BillingCycle if not already processed
                            if (!processedBillingCycleIds.contains(cycleId)) {
                                processedBillingCycleIds.add(cycleId)
                                
                                // Check if billing cycle already exists locally
                                val existingCycle = billingCycleDao.getBillingCycleById(cycleId)
                                if (existingCycle == null) {
                                                                    // Create BillingCycle object from parent data
                                val billingCycle = BillingCycle(
                                    id = cycleId,
                                    name = cycleData?.get("name") as? String ?: "Imported billing cycle",
                                    startDate = (cycleData?.get("start_date") as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                                    endDate = (cycleData?.get("end_date") as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                                    totalAmount = (cycleData?.get("total_amount") as? Number)?.toDouble() ?: 0.0,
                                    isPaid = cycleData?.get("is_paid") as? Boolean ?: true,
                                    isActive = cycleData?.get("is_active") as? Boolean ?: true,
                                    createdAt = (cycleData?.get("created_at") as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                                    isSynced = true
                                )
                                    
                                    // Insert into local Room database
                                    billingCycleDao.insertBillingCycle(billingCycle)
                                    totalBillingCyclesRestored++
                                    android.util.Log.d("BillingCycleRepository", "Restored billing cycle: $cycleId - ${billingCycle.name} - Total: ₹${billingCycle.totalAmount}")
                                } else {
                                    android.util.Log.d("BillingCycleRepository", "Skipped existing billing cycle: $cycleId")
                                }
                            }
                            
                            // Process FarmerBillingDetail
                            val farmerDetailId = "${farmerId}_$cycleId"
                            if (!processedFarmerDetailIds.contains(farmerDetailId)) {
                                processedFarmerDetailIds.add(farmerDetailId)
                                
                                // Check if farmer billing detail already exists locally
                                val existingDetail = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(cycleId)
                                    .firstOrNull { it.farmerId == farmerId }
                                
                                if (existingDetail == null) {
                                    // Convert Firestore document to FarmerBillingDetail object
                                    val farmerBillingDetail = FarmerBillingDetail(
                                        id = farmerDetailId,
                                        farmerId = farmerId,
                                        farmerName = farmerData?.get("farmer_name") as? String ?: "",
                                        billingCycleId = cycleId,
                                        originalAmount = (farmerData?.get("original_amount") as? Number)?.toDouble() ?: 0.0,
                                        paidAmount = (farmerData?.get("paid_amount") as? Number)?.toDouble() ?: 0.0,
                                        balanceAmount = (farmerData?.get("balance_amount") as? Number)?.toDouble() ?: 0.0,
                                        isPaid = farmerData?.get("is_paid") as? Boolean ?: true,
                                        paymentDate = (farmerData?.get("payment_date") as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                                        isSynced = true
                                    )
                                    
                                    // Insert into local Room database
                                    farmerBillingDetailDao.insertFarmerBillingDetail(farmerBillingDetail)
                                    totalFarmerDetailsRestored++
                                    android.util.Log.d("BillingCycleRepository", "Restored farmer billing detail: ${farmerBillingDetail.farmerName} - Cycle: $cycleId - Amount: ₹${farmerBillingDetail.originalAmount}")
                                } else {
                                    android.util.Log.d("BillingCycleRepository", "Skipped existing farmer billing detail: $farmerId - Cycle: $cycleId")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error processing billing cycle $cycleId: ${e.message}")
                }
            }
            
            android.util.Log.d("BillingCycleRepository", "Successfully restored $totalBillingCyclesRestored billing cycles and $totalFarmerDetailsRestored farmer details from Firestore")
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error during billing cycles restoration: ${e.message}")
            android.util.Log.e("BillingCycleRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Helper function to parse dates from billing cycle ID format: billing_YYYYMMDD_YYYYMMDD
     */
    private fun parseDateFromBillingCycleId(billingCycleId: String, isStartDate: Boolean): Date {
        val datePattern = Regex("billing_(\\d{8})_(\\d{8})")
        val match = datePattern.find(billingCycleId)
        return if (match != null) {
            val dateStr = if (isStartDate) match.groupValues[1] else match.groupValues[2]
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            dateFormat.parse(dateStr) ?: Date()
        } else {
            Date() // Fallback to current date if ID format is unexpected
        }
    }

    /**
     * Restore billing cycles from Firestore for a specific user
     */
    suspend fun restoreBillingCyclesFromFirestore(userId: String) = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("BillingCycleRepository", "Network not available, skipping restoreBillingCyclesFromFirestore")
            return@withContext
        }
        
        try {
            android.util.Log.d("BillingCycleRepository", "Starting restoration of billing cycles from Firestore for user: $userId")
            
            val allFarmers = farmerRepository.getAllFarmers()
            android.util.Log.d("BillingCycleRepository", "Found ${allFarmers.size} farmers to restore billing cycles for")
            
            var totalBillingCyclesRestored = 0
            var totalFarmerDetailsRestored = 0
            
            // Map to temporarily store all farmer billing details, grouped by their consistentBillingCycleId
            val tempFarmerBillingDetailsByCycle = mutableMapOf<String, MutableList<FarmerBillingDetail>>()
            
            // Pass 1: Collect all farmer billing details from Firestore for all farmers
            for (farmer in allFarmers) {
                try {
                    val userSpecificPath = "users/$userId/farmers/${farmer.id}/billing_cycle"
                    val billingCycleSnapshot = try {
                        firestore.collection(userSpecificPath).get().await()
                    } catch (e: Exception) {
                        android.util.Log.d("BillingCycleRepository", "User-specific path not found for farmer ${farmer.id}, trying legacy path")
                        val legacyPath = "farmers/${farmer.id}/billing_cycle"
                        firestore.collection(legacyPath).get().await()
                    }

                    android.util.Log.d("BillingCycleRepository", "Found ${billingCycleSnapshot.documents.size} billing cycle documents for farmer: ${farmer.id}")
                    
                    for (doc in billingCycleSnapshot.documents) {
                            val data = doc.data
                            if (data != null) {
                            val startDate = (data["start_date"] as? com.google.firebase.Timestamp)?.toDate() ?: parseDateFromBillingCycleId(doc.id, true)
                            val endDate = (data["end_date"] as? com.google.firebase.Timestamp)?.toDate() ?: parseDateFromBillingCycleId(doc.id, false)
                            val consistentBillingCycleId = generateBillingCycleId(startDate, endDate)

                            val farmerDetail = FarmerBillingDetail(
                                id = "${farmer.id}_$consistentBillingCycleId",
                                farmerId = farmer.id,
                                farmerName = farmer.name,
                                billingCycleId = consistentBillingCycleId,
                                originalAmount = (data["total_amount"] as? Number)?.toDouble() ?: 0.0, // This is the individual farmer's amount
                                paidAmount = (data["paid_amount"] as? Number)?.toDouble() ?: 0.0,
                                balanceAmount = (data["balance_amount"] as? Number)?.toDouble() ?: 0.0,
                                isPaid = data["is_paid"] as? Boolean ?: true,
                                paymentDate = (data["payment_date"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                                isSynced = true
                            )

                            if (!tempFarmerBillingDetailsByCycle.containsKey(consistentBillingCycleId)) {
                                tempFarmerBillingDetailsByCycle[consistentBillingCycleId] = mutableListOf()
                            }
                            tempFarmerBillingDetailsByCycle[consistentBillingCycleId]?.add(farmerDetail)
                            android.util.Log.d("BillingCycleRepository", "Collected farmer detail for cycle $consistentBillingCycleId: ${farmerDetail.farmerName} - ₹${farmerDetail.originalAmount}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error collecting billing cycles for farmer ${farmer.id}: ${e.message}")
                }
            }

            // Pass 2: Create/update main BillingCycle objects and insert FarmerBillingDetails
            for ((consistentBillingCycleId, farmerDetailsList) in tempFarmerBillingDetailsByCycle) {
                val totalAmountForCycle = farmerDetailsList.sumOf { it.originalAmount }
                val firstDetail = farmerDetailsList.first() // Use first detail to get common cycle properties

                val startDate = parseDateFromBillingCycleId(consistentBillingCycleId, true)
                val endDate = parseDateFromBillingCycleId(consistentBillingCycleId, false)
                val createdAt = firstDetail.paymentDate ?: Date() // Using paymentDate as a proxy for creation date

                // Temporarily use a simple name. fixBillingCycleNames will correct it later.
                val tempBillingCycleName = "Billing Cycle ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(startDate)} - ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(endDate)}"

                // Create or update the main BillingCycle object
                val existingCycle = billingCycleDao.getBillingCycleById(consistentBillingCycleId)
                                    if (existingCycle == null) {
                                        val billingCycle = BillingCycle(
                        id = consistentBillingCycleId,
                        name = tempBillingCycleName, // Use temporary name
                        startDate = startDate,
                        endDate = endDate,
                        totalAmount = totalAmountForCycle,
                        isPaid = true, // Assuming if restored, it's paid
                        isActive = true,
                        createdAt = createdAt,
                                            isSynced = true
                                        )
                                        billingCycleDao.insertBillingCycle(billingCycle)
                                        totalBillingCyclesRestored++
                    android.util.Log.d("BillingCycleRepository", "Restored main billing cycle: $consistentBillingCycleId - ${billingCycle.name} - Total: ₹${billingCycle.totalAmount}")
                                    } else {
                    // Update existing cycle if total amount or name changed
                    if (existingCycle.totalAmount != totalAmountForCycle || existingCycle.name != tempBillingCycleName) {
                        val updatedCycle = existingCycle.copy(
                            totalAmount = totalAmountForCycle,
                            name = tempBillingCycleName, // Update with temporary name
                            isSynced = true
                        )
                        billingCycleDao.updateBillingCycle(updatedCycle)
                        android.util.Log.d("BillingCycleRepository", "Updated existing main billing cycle: $consistentBillingCycleId - ${updatedCycle.name} - Total: ₹${updatedCycle.totalAmount}")
                    } else {
                        android.util.Log.d("BillingCycleRepository", "Skipped existing main billing cycle (no changes): $consistentBillingCycleId")
                    }
                }

                // Insert/update individual FarmerBillingDetail objects
                for (farmerDetail in farmerDetailsList) {
                    val existingFarmerDetail = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(farmerDetail.billingCycleId)
                        .firstOrNull { it.farmerId == farmerDetail.farmerId }

                    if (existingFarmerDetail == null) {
                        farmerBillingDetailDao.insertFarmerBillingDetail(farmerDetail)
                        totalFarmerDetailsRestored++
                        android.util.Log.d("BillingCycleRepository", "Restored farmer billing detail: ${farmerDetail.farmerName} - Cycle: ${farmerDetail.billingCycleId} - Amount: ₹${farmerDetail.originalAmount}")
                    } else {
                        if (existingFarmerDetail.originalAmount != farmerDetail.originalAmount ||
                            existingFarmerDetail.paidAmount != farmerDetail.paidAmount ||
                            existingFarmerDetail.balanceAmount != farmerDetail.balanceAmount ||
                            existingFarmerDetail.isPaid != farmerDetail.isPaid) {
                            val updatedFarmerDetail = existingFarmerDetail.copy(
                                originalAmount = farmerDetail.originalAmount,
                                paidAmount = farmerDetail.paidAmount,
                                balanceAmount = farmerDetail.balanceAmount,
                                isPaid = farmerDetail.isPaid,
                                isSynced = true
                            )
                            farmerBillingDetailDao.updateFarmerBillingDetail(updatedFarmerDetail)
                            android.util.Log.d("BillingCycleRepository", "Updated existing farmer billing detail: ${farmerDetail.farmerName} - Cycle: ${farmerDetail.billingCycleId}")
                        } else {
                            android.util.Log.d("BillingCycleRepository", "Skipped existing farmer billing detail (no changes): ${farmerDetail.farmerName} - Cycle: ${farmerDetail.billingCycleId}")
                        }
                    }
                }
            }

            android.util.Log.d("BillingCycleRepository", "Successfully restored $totalBillingCyclesRestored main billing cycles and $totalFarmerDetailsRestored farmer details from Firestore")

            // Ensure all farmers have billing cycles for the current month (this will now use the correctly restored main cycles)
            ensureAllFarmersHaveBillingCycles()

            // Force cleanup all duplicates (based on date range)
            forceCleanupAllDuplicates()

            // Fix billing cycle names to ensure proper formatting (this should be the last step for names)
            fixBillingCycleNames()

            // Regular cleanup (might be redundant after forceCleanup and fixNames, but keep for safety)
            cleanupDuplicateData()

        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error restoring billing cycles from Firestore: ${e.message}")
            android.util.Log.e("BillingCycleRepository", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Check for billing cycles in global path and migrate them to user-specific paths
     */
    suspend fun migrateGlobalBillingCyclesToUserSpecific(userId: String) = withContext(Dispatchers.IO) {
        if (!networkUtils.isCurrentlyOnline()) {
            android.util.Log.d("BillingCycleRepository", "Network not available, skipping migration")
            return@withContext
        }
        
        try {
            android.util.Log.d("BillingCycleRepository", "Checking for billing cycles in global path to migrate for user: $userId")
            
            // Check global billing_cycle collection
            val globalBillingCyclesSnapshot = try {
                firestore.collection("billing_cycle").get().await()
            } catch (e: Exception) {
                android.util.Log.d("BillingCycleRepository", "No global billing cycles found or error accessing: ${e.message}")
                return@withContext
            }
            
            if (globalBillingCyclesSnapshot.documents.isEmpty()) {
                android.util.Log.d("BillingCycleRepository", "No global billing cycles found to migrate")
                return@withContext
            }
            
            android.util.Log.d("BillingCycleRepository", "Found ${globalBillingCyclesSnapshot.documents.size} global billing cycles to migrate")
            
            var migratedCount = 0
            
            for (doc in globalBillingCyclesSnapshot.documents) {
                try {
                    val data = doc.data
                    if (data != null) {
                        val billingCycleId = doc.id
                        
                        // Check if this billing cycle has farmer information
                        val farmerId = data["farmer_id"] as? String
                        if (farmerId != null) {
                            // Migrate to user-specific path
                            val userSpecificPath = "users/$userId/farmers/$farmerId/billing_cycle/$billingCycleId"
                            
                            try {
                                firestore.document(userSpecificPath).set(data).await()
                                android.util.Log.d("BillingCycleRepository", "Migrated billing cycle $billingCycleId for farmer $farmerId to user-specific path")
                                migratedCount++
                            } catch (e: Exception) {
                                android.util.Log.e("BillingCycleRepository", "Error migrating billing cycle $billingCycleId: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BillingCycleRepository", "Error processing global billing cycle document: ${e.message}")
                }
            }
            
            android.util.Log.d("BillingCycleRepository", "Successfully migrated $migratedCount billing cycles to user-specific paths")
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error during billing cycle migration: ${e.message}")
        }
    }
    
    /**
     * Ensure all farmers have billing cycles for the current month
     */
    suspend fun ensureAllFarmersHaveBillingCycles() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BillingCycleRepository", "Ensuring all farmers have billing cycles for current month")
            
            val allFarmers = farmerRepository.getAllFarmers()
            val existingBillingCycles = billingCycleDao.getAllBillingCycles().first()
            
            if (existingBillingCycles.isEmpty()) {
                android.util.Log.d("BillingCycleRepository", "No billing cycles exist, skipping farmer billing cycle creation")
                return@withContext
            }
            
            // Get the most recent billing cycle to use as template
            val mostRecentCycle = existingBillingCycles.maxByOrNull { it.createdAt }
            if (mostRecentCycle == null) {
                android.util.Log.d("BillingCycleRepository", "No valid billing cycle found for template")
                return@withContext
            }
            
            android.util.Log.d("BillingCycleRepository", "Using billing cycle '${mostRecentCycle.name}' as template for all farmers")
            
            for (farmer in allFarmers) {
                // Check if this farmer already has a billing detail for this cycle
                val existingDetail = farmerBillingDetailDao.getFarmerBillingDetailsByBillingCycleId(mostRecentCycle.id)
                                        .firstOrNull { it.farmerId == farmer.id }
                                    
                                    if (existingDetail == null) {
                    // Calculate farmer's earnings for this billing cycle period
                    val collections = dailyMilkCollectionRepository.getCollectionsByFarmerAndDateRange(
                        farmer.id, mostRecentCycle.startDate, mostRecentCycle.endDate
                    )
                    val farmerTotal = collections.sumOf { it.totalAmount }
                    
                    // Create farmer billing detail
                    val farmerDetail = FarmerBillingDetail(
                        id = "${farmer.id}_${mostRecentCycle.id}",
                                            farmerId = farmer.id,
                                            farmerName = farmer.name,
                        billingCycleId = mostRecentCycle.id,
                        originalAmount = farmerTotal,
                        paidAmount = farmerTotal, // Assume paid if it's from existing data
                        balanceAmount = 0.0,
                        isPaid = true,
                        paymentDate = Date(),
                                            isSynced = true
                                        )
                                        
                    farmerBillingDetailDao.insertFarmerBillingDetail(farmerDetail)
                    android.util.Log.d("BillingCycleRepository", "Created billing detail for farmer ${farmer.name}: ₹$farmerTotal for cycle ${mostRecentCycle.name}")
                                    } else {
                    android.util.Log.d("BillingCycleRepository", "Farmer ${farmer.name} already has billing detail for cycle ${mostRecentCycle.name}")
                                    }
                                }
            
            android.util.Log.d("BillingCycleRepository", "Completed ensuring all farmers have billing cycles")
            
                        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error ensuring all farmers have billing cycles: ${e.message}")
        }
    }

    /**
     * Fix billing cycle names to ensure proper formatting (e.g., "1st billing_cycle:aug")
     */
    suspend fun fixBillingCycleNames() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BillingCycleRepository", "Starting to fix billing cycle names")
            
            val allBillingCycles = billingCycleDao.getAllBillingCycles().first()
            android.util.Log.d("BillingCycleRepository", "Found ${allBillingCycles.size} billing cycles to check")
            
            var fixedCount = 0
            
            for (cycle in allBillingCycles) {
                // Check if the name follows the proper format
                val properName = generateBillingCycleName(cycle.startDate, allBillingCycles.filter { it.id != cycle.id })
                
                if (cycle.name != properName) {
                    android.util.Log.d("BillingCycleRepository", "Fixing billing cycle name: '${cycle.name}' -> '$properName'")
                    
                    val updatedCycle = cycle.copy(name = properName)
                    billingCycleDao.updateBillingCycle(updatedCycle)
                    fixedCount++
                }
            }
            
            android.util.Log.d("BillingCycleRepository", "Fixed $fixedCount billing cycle names")
            
        } catch (e: Exception) {
            android.util.Log.e("BillingCycleRepository", "Error fixing billing cycle names: ${e.message}")
        }
    }
}