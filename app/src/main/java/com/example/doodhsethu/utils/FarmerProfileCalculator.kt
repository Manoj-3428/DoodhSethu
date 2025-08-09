package com.example.doodhsethu.utils

import android.content.Context
import android.util.Log
import com.example.doodhsethu.data.models.*
import com.example.doodhsethu.data.repository.FarmerRepository
import com.example.doodhsethu.data.repository.MilkCollectionRepository
import com.example.doodhsethu.data.repository.BillingCycleRepository
import com.example.doodhsethu.data.repository.BillingCycleSummaryRepository
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.*

class FarmerProfileCalculator(private val context: Context) {
    private val farmerRepository = FarmerRepository(context)
    private val milkCollectionRepository = MilkCollectionRepository(context)
    private val billingCycleRepository = BillingCycleRepository(context)
    private val billingCycleSummaryRepository = BillingCycleSummaryRepository(context)
    private val authViewModel = AuthViewModel()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }

    /**
     * Calculate and update farmer profile with current month earnings and billing cycle data
     */
    suspend fun updateFarmerProfile(farmerId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Starting profile update for farmer: $farmerId")
            
            val farmer = farmerRepository.getFarmerById(farmerId)
            if (farmer == null) {
                Log.e("FarmerProfileCalculator", "Farmer not found: $farmerId")
                return@withContext
            }

            Log.d("FarmerProfileCalculator", "Found farmer: ${farmer.name} (ID: $farmerId)")

            // Calculate current month total earnings
            val currentMonthTotal = calculateCurrentMonthEarnings(farmerId)
            Log.d("FarmerProfileCalculator", "Current month total for $farmerId: ₹$currentMonthTotal")

            // Calculate pending amount using new billing cycle summary structure
            val pendingAmount = calculatePendingAmount(farmerId)
            Log.d("FarmerProfileCalculator", "Pending amount for $farmerId: ₹$pendingAmount")

            // Update billing cycles data
            val billingCyclesData = updateBillingCyclesData(farmerId)
            Log.d("FarmerProfileCalculator", "Updated billing cycles data for $farmerId: $billingCyclesData")

            // Update farmer profile
            val updatedFarmer = farmer.copy(
                totalAmount = currentMonthTotal,
                pendingAmount = pendingAmount,
                billingCycles = billingCyclesData,
                updatedAt = Date()
            )

            Log.d("FarmerProfileCalculator", "Updating farmer profile with: totalAmount=₹$currentMonthTotal, pendingAmount=₹$pendingAmount, billingCycles=$billingCyclesData")
            
            farmerRepository.updateFarmer(updatedFarmer)
            Log.d("FarmerProfileCalculator", "Successfully updated farmer profile: $farmerId")
            
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating farmer profile: ${e.message}")
            Log.e("FarmerProfileCalculator", "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Optimized farmer profile update that uses local data instead of excessive Firestore calls
     */
    suspend fun updateFarmerProfileOptimized(farmerId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Starting optimized profile update for farmer: $farmerId")
            
            val farmer = farmerRepository.getFarmerById(farmerId)
            if (farmer == null) {
                Log.e("FarmerProfileCalculator", "Farmer not found: $farmerId")
                return@withContext
            }

            Log.d("FarmerProfileCalculator", "Found farmer: ${farmer.name} (ID: $farmerId)")

            // Calculate current month total earnings from local database (much faster)
            val currentMonthTotal = calculateCurrentMonthEarningsFromLocal(farmerId)
            Log.d("FarmerProfileCalculator", "Current month total for $farmerId: ₹$currentMonthTotal")

            // Calculate pending amount using local data (optimized)
            val pendingAmount = calculatePendingAmountOptimized(farmerId)
            Log.d("FarmerProfileCalculator", "Pending amount for $farmerId: ₹$pendingAmount")

            // Update billing cycles data
            val billingCyclesData = updateBillingCyclesData(farmerId)
            Log.d("FarmerProfileCalculator", "Updated billing cycles data for $farmerId: $billingCyclesData")

            // Update farmer profile
            val updatedFarmer = farmer.copy(
                totalAmount = currentMonthTotal,
                pendingAmount = pendingAmount,
                billingCycles = billingCyclesData,
                updatedAt = Date()
            )

            Log.d("FarmerProfileCalculator", "Updating farmer profile with: totalAmount=₹$currentMonthTotal, pendingAmount=₹$pendingAmount, billingCycles=$billingCyclesData")
            
            farmerRepository.updateFarmer(updatedFarmer)
            Log.d("FarmerProfileCalculator", "Successfully updated farmer profile (optimized): $farmerId")
            
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating farmer profile (optimized): ${e.message}")
        }
    }

    /**
     * Calculate current month total earnings for a farmer from new Firestore structure
     */
    private suspend fun calculateCurrentMonthEarnings(farmerId: String): Double {
        try {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)
            
            // Get start and end of current month
            calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.time
            
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            val monthEnd = calendar.time

            Log.d("FarmerProfileCalculator", "Calculating earnings for $farmerId from $monthStart to $monthEnd")

            // First try to get from new Firestore structure
            val firestoreEarnings = calculateEarningsFromNewFirestoreStructure(farmerId, monthStart, monthEnd)
            if (firestoreEarnings > 0) {
                Log.d("FarmerProfileCalculator", "Found earnings from new Firestore structure: ₹$firestoreEarnings")
                return firestoreEarnings
            }

            // Fallback to new DailyMilkCollection database - create instance locally to avoid circular dependency
            val dailyMilkCollectionRepository = com.example.doodhsethu.data.repository.DailyMilkCollectionRepository(context)
            val collections = dailyMilkCollectionRepository.getAllCollectionsByFarmer(farmerId)

            Log.d("FarmerProfileCalculator", "Found ${collections.size} daily milk collections for farmer $farmerId")
            collections.forEach { collection ->
                Log.d("FarmerProfileCalculator", "  Collection: ${collection.date} - AM(₹${collection.amPrice}) + PM(₹${collection.pmPrice}) = Total(₹${collection.totalAmount})")
            }

            val totalEarnings = collections.sumOf { it.totalAmount }
            Log.d("FarmerProfileCalculator", "Found ${collections.size} collections for $farmerId, total: ₹$totalEarnings")
            
            return totalEarnings
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error calculating current month earnings: ${e.message}")
            Log.e("FarmerProfileCalculator", "Stack trace: ${e.stackTraceToString()}")
            return 0.0
        }
    }
    
    /**
     * Calculate earnings from new Firestore structure
     */
    private suspend fun calculateEarningsFromNewFirestoreStructure(farmerId: String, startDate: Date, endDate: Date): Double {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
            
            var totalEarnings = 0.0
            
            // Generate all dates between start and end
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            
            // Only check current month dates to avoid too many calls
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            
            while (calendar.time <= endDate) {
                // Only check dates in current month
                if (calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear) {
                    val dateString = dateFormat.format(calendar.time)
                    
                    try {
                        val userId = getCurrentUserId()
                        if (userId == null) {
                            Log.e("FarmerProfileCalculator", "Cannot fetch Firestore data: User not authenticated")
                            continue
                        }
                        
                        Log.d("FarmerProfileCalculator", "Fetching data from: users/$userId/milk-collection/$dateString/farmers/$farmerId")
                        
                        val farmerDoc = firestore.collection("users")
                            .document(userId)
                            .collection("milk-collection")
                            .document(dateString)
                            .collection("farmers")
                            .document(farmerId)
                            .get()
                            .await()
                        
                        if (farmerDoc.exists()) {
                            val data = farmerDoc.data
                            if (data != null) {
                                val totalAmount = data["total_amount"] as? Double ?: 0.0
                                totalEarnings += totalAmount
                                
                                Log.d("FarmerProfileCalculator", "Found data for $farmerId on $dateString: total_amount=₹$totalAmount")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("FarmerProfileCalculator", "No data found for $farmerId on $dateString: ${e.message}")
                    }
                }
                
                calendar.add(Calendar.DATE, 1)
            }
            
            Log.d("FarmerProfileCalculator", "Total earnings from new Firestore structure for $farmerId: ₹$totalEarnings")
            return totalEarnings
            
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error calculating earnings from new Firestore structure: ${e.message}")
            return 0.0
        }
    }

    /**
     * Calculate current month earnings from local database (much faster than Firestore calls)
     */
    private suspend fun calculateCurrentMonthEarningsFromLocal(farmerId: String): Double {
        try {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)
            
            // Get start and end of current month
            calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.time
            
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            val monthEnd = calendar.time

            Log.d("FarmerProfileCalculator", "Calculating earnings from local DB for $farmerId from $monthStart to $monthEnd")

            // Use local DailyMilkCollection database (much faster)
            val dailyMilkCollectionRepository = com.example.doodhsethu.data.repository.DailyMilkCollectionRepository(context)
            val collections = dailyMilkCollectionRepository.getAllCollectionsByFarmer(farmerId)

            Log.d("FarmerProfileCalculator", "Found ${collections.size} daily milk collections for farmer $farmerId")
            
            // Filter collections for current month
            val currentMonthCollections = collections.filter { collection ->
                try {
                    val collectionDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).parse(collection.date)
                    collectionDate != null && collectionDate >= monthStart && collectionDate <= monthEnd
                } catch (e: Exception) {
                    false
                }
            }

            Log.d("FarmerProfileCalculator", "Found ${currentMonthCollections.size} collections for current month")
            currentMonthCollections.forEach { collection ->
                Log.d("FarmerProfileCalculator", "  Collection: ${collection.date} - AM(₹${collection.amPrice}) + PM(₹${collection.pmPrice}) = Total(₹${collection.totalAmount})")
            }

            val totalEarnings = currentMonthCollections.sumOf { it.totalAmount }
            Log.d("FarmerProfileCalculator", "Current month total for $farmerId: ₹$totalEarnings")
            
            return totalEarnings
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error calculating current month earnings from local: ${e.message}")
            return 0.0
        }
    }

    /**
     * Calculate pending amount (total earnings minus payments) using local billing cycle data
     */
    private suspend fun calculatePendingAmount(farmerId: String): Double {
        try {
            // Get current month earnings from local data (optimized)
            val currentMonthEarnings = calculateCurrentMonthEarningsFromLocal(farmerId)
            
            // Get total paid amount from local FarmerBillingDetail data (this is what's actually working)
            val farmerBillingDetails = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
            val totalPaidFromBillingCycles = farmerBillingDetails.sumOf { it.originalAmount }
            
            // Pending amount = current month earnings minus what has been paid
            val pendingAmount = currentMonthEarnings - totalPaidFromBillingCycles
            
            Log.d("FarmerProfileCalculator", "Total paid from local billing cycles: ₹$totalPaidFromBillingCycles")
            Log.d("FarmerProfileCalculator", "Current month earnings: ₹$currentMonthEarnings")
            Log.d("FarmerProfileCalculator", "Pending amount: ₹$pendingAmount")
            Log.d("FarmerProfileCalculator", "Number of billing cycles for farmer $farmerId: ${farmerBillingDetails.size}")
            
            return pendingAmount
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error calculating pending amount: ${e.message}")
            Log.e("FarmerProfileCalculator", "Stack trace: ${e.stackTraceToString()}")
            return 0.0
        }
    }

    /**
     * Calculate pending amount using local data (optimized version)
     */
    private suspend fun calculatePendingAmountOptimized(farmerId: String): Double {
        try {
            // Get current month earnings from local data (optimized)
            val currentMonthEarnings = calculateCurrentMonthEarningsFromLocal(farmerId)
            
            // Get total paid amount from local FarmerBillingDetail data
            val farmerBillingDetails = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
            val totalPaidFromBillingCycles = farmerBillingDetails.sumOf { it.originalAmount }
            
            // Pending amount = current month earnings minus what has been paid
            val pendingAmount = currentMonthEarnings - totalPaidFromBillingCycles
            
            Log.d("FarmerProfileCalculator", "Optimized calculation - Total paid: ₹$totalPaidFromBillingCycles")
            Log.d("FarmerProfileCalculator", "Optimized calculation - Current month earnings: ₹$currentMonthEarnings")
            Log.d("FarmerProfileCalculator", "Optimized calculation - Pending amount: ₹$pendingAmount")
            
            return pendingAmount
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error calculating pending amount (optimized): ${e.message}")
            return 0.0
        }
    }

    /**
     * Update billing cycles data as JSON string
     */
    private suspend fun updateBillingCyclesData(farmerId: String): String {
        try {
            Log.d("FarmerProfileCalculator", "Starting updateBillingCyclesData for farmer: $farmerId")
            
            val farmerBillingDetails = billingCycleRepository.getFarmerBillingDetailsByFarmer(farmerId).first()
            Log.d("FarmerProfileCalculator", "Found ${farmerBillingDetails.size} billing details for farmer: $farmerId")
            
            val billingCyclesJson = JSONObject()
            
            for (detail in farmerBillingDetails) {
                Log.d("FarmerProfileCalculator", "Processing billing detail: ${detail.farmerName} - Cycle ID: ${detail.billingCycleId} - Amount: ₹${detail.originalAmount}")
                
                val billingCycle = billingCycleRepository.getBillingCycleById(detail.billingCycleId)
                if (billingCycle != null) {
                    Log.d("FarmerProfileCalculator", "Found billing cycle: ${billingCycle.name} for detail: ${detail.billingCycleId}")
                    
                    // Simplify the structure: just store cycle name as key and amount as value
                    // Convert "1st billing_cycle:jul" to "billing_cycle1_july"
                    val simpleKey = convertBillingCycleNameToSimpleKey(billingCycle.name)
                    billingCyclesJson.put(simpleKey, detail.originalAmount)
                    
                    Log.d("FarmerProfileCalculator", "Added billing cycle data: $simpleKey = ₹${detail.originalAmount}")
                } else {
                    Log.e("FarmerProfileCalculator", "Billing cycle not found for ID: ${detail.billingCycleId}")
                }
            }
            
            val result = billingCyclesJson.toString()
            Log.d("FarmerProfileCalculator", "Updated billing cycles JSON for $farmerId: ${billingCyclesJson.length()} cycles, JSON: $result")
            return result
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating billing cycles data: ${e.message}")
            Log.e("FarmerProfileCalculator", "Stack trace: ${e.stackTraceToString()}")
            return "{}"
        }
    }
    
    /**
     * Convert billing cycle name to simple key format
     * "1st billing_cycle:jul" -> "billing_cycle1_july"
     */
    private fun convertBillingCycleNameToSimpleKey(cycleName: String): String {
        return try {
            // Extract the ordinal and month from "1st billing_cycle:jul"
            val parts = cycleName.split(" ")
            if (parts.size >= 3) {
                val ordinal = parts[0] // "1st", "2nd", etc.
                val month = parts[2] // "jul", "aug", etc.
                
                // Convert ordinal to number
                val number = when {
                    ordinal.startsWith("1") -> "1"
                    ordinal.startsWith("2") -> "2"
                    ordinal.startsWith("3") -> "3"
                    ordinal.startsWith("4") -> "4"
                    ordinal.startsWith("5") -> "5"
                    else -> "1"
                }
                
                // Convert month abbreviation to full name
                val fullMonth = when (month) {
                    "jan" -> "january"
                    "feb" -> "february"
                    "mar" -> "march"
                    "apr" -> "april"
                    "may" -> "may"
                    "jun" -> "june"
                    "jul" -> "july"
                    "aug" -> "august"
                    "sep" -> "september"
                    "oct" -> "october"
                    "nov" -> "november"
                    "dec" -> "december"
                    else -> month
                }
                
                "billing_cycle${number}_${fullMonth}"
            } else {
                // Fallback: use the original name
                cycleName.replace(" ", "_").replace(":", "_")
            }
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error converting billing cycle name: $cycleName")
            cycleName.replace(" ", "_").replace(":", "_")
        }
    }

    /**
     * Update all farmers' profiles
     */
    suspend fun updateAllFarmerProfiles() = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Starting update for all farmer profiles")
            val farmers = farmerRepository.getAllFarmers()
            
            for (farmer in farmers) {
                updateFarmerProfile(farmer.id)
            }
            
            Log.d("FarmerProfileCalculator", "Completed update for ${farmers.size} farmers")
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating all farmer profiles: ${e.message}")
        }
    }

    /**
     * Update farmer profile when billing cycle is created
     */
    suspend fun onBillingCycleCreated(billingCycleId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Billing cycle created: $billingCycleId, updating affected farmers")
            
            // Get all farmers affected by this billing cycle
            val affectedDetails = billingCycleRepository.getFarmerBillingDetailsByBillingCycleId(billingCycleId)
            
            for (detail in affectedDetails) {
                Log.d("FarmerProfileCalculator", "Updating farmer profile after billing cycle creation: ${detail.farmerId}")
                updateFarmerProfile(detail.farmerId)
            }
            
            Log.d("FarmerProfileCalculator", "Updated ${affectedDetails.size} farmers after billing cycle creation")
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating farmers after billing cycle creation: ${e.message}")
        }
    }

    /**
     * Update farmer profile when billing cycle is deleted
     */
    suspend fun onBillingCycleDeleted(billingCycleId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Billing cycle deleted: $billingCycleId, updating affected farmers")
            
            // Get all farmers affected by this billing cycle
            val affectedDetails = billingCycleRepository.getFarmerBillingDetailsByBillingCycleId(billingCycleId)
            
            for (detail in affectedDetails) {
                Log.d("FarmerProfileCalculator", "Updating farmer profile after billing cycle deletion: ${detail.farmerId}")
                updateFarmerProfile(detail.farmerId)
            }
            
            Log.d("FarmerProfileCalculator", "Updated ${affectedDetails.size} farmers after billing cycle deletion")
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating farmers after billing cycle deletion: ${e.message}")
        }
    }

    /**
     * Update farmer profile when milk collection is added/updated
     */
    suspend fun onMilkCollectionChanged(farmerId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Milk collection changed for farmer: $farmerId")
            updateFarmerProfile(farmerId)
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error updating farmer after milk collection change: ${e.message}")
        }
    }
    
    /**
     * Force refresh all farmer profiles (useful when app starts or data is loaded from Firestore)
     */
    suspend fun refreshAllFarmerProfiles() = withContext(Dispatchers.IO) {
        try {
            Log.d("FarmerProfileCalculator", "Force refreshing all farmer profiles")
            updateAllFarmerProfiles()
        } catch (e: Exception) {
            Log.e("FarmerProfileCalculator", "Error refreshing all farmer profiles: ${e.message}")
        }
    }
}