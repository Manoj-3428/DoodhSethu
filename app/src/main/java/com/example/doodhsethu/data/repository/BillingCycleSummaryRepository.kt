package com.example.doodhsethu.data.repository

import android.content.Context
import android.util.Log
import com.example.doodhsethu.data.models.DailyMilkCollection
import com.example.doodhsethu.utils.NetworkUtils
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.*

class BillingCycleSummaryRepository(private val context: Context) {
    private val db: FirebaseFirestore = Firebase.firestore
    private val networkUtils = NetworkUtils(context)
    private val authViewModel = AuthViewModel()
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(context)?.userId
    }
    
    companion object {
        private const val TAG = "BillingCycleSummaryRepo"
    }
    
    /**
     * Initialize billing cycle summary for a farmer when a new billing cycle is created
     */
    suspend fun initializeBillingCycleSummary(
        billingCycleId: String,
        farmerId: String,
        farmerName: String,
        totalAmount: Double = 0.0,
        totalMilk: Double = 0.0,
        totalFat: Double = 0.0
    ) {
        try {
            Log.d(TAG, "Initializing billing cycle summary for farmer $farmerId, cycle $billingCycleId")
            Log.d(TAG, "Initial amounts - Milk: $totalMilk, Fat: $totalFat, Amount: $totalAmount")
            
            val summaryData = mapOf(
                "total_milk" to totalMilk,
                "total_fat" to totalFat,
                "total_amount" to totalAmount,
                "farmer_name" to farmerName,
                "created_at" to com.google.firebase.Timestamp.now(),
                "updated_at" to com.google.firebase.Timestamp.now()
            )
            
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "Cannot initialize billing cycle summary: User not authenticated")
                return
            }
            
            // Debug: Log the exact path we're writing to
            val path = "users/$userId/farmers/$farmerId/billing_cycle/$billingCycleId"
            Log.d(TAG, "Writing to Firestore path: $path")
            Log.d(TAG, "Summary data: $summaryData")
            
            // Save to Firestore
            if (networkUtils.isCurrentlyOnline()) {
                db.collection("users")
                    .document(userId)
                    .collection("farmers")
                    .document(farmerId)
                    .collection("billing_cycle")
                    .document(billingCycleId)
                    .set(summaryData)
                    .await()
                
                Log.d(TAG, "Successfully initialized billing cycle summary for farmer $farmerId, cycle $billingCycleId")
                Log.d(TAG, "Saved amounts - Milk: $totalMilk, Fat: $totalFat, Amount: $totalAmount")
                
                // Verify the write by reading it back
                val verificationDoc = db.collection("users")
                    .document(userId)
                    .collection("farmers")
                    .document(farmerId)
                    .collection("billing_cycle")
                    .document(billingCycleId)
                    .get()
                    .await()
                
                if (verificationDoc.exists()) {
                    Log.d(TAG, "Verification successful: Document exists in Firestore")
                    Log.d(TAG, "Verification data: ${verificationDoc.data}")
                } else {
                    Log.e(TAG, "Verification failed: Document does not exist in Firestore")
                }
            } else {
                Log.d(TAG, "Network not available, cannot initialize billing cycle summary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing billing cycle summary: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    /**
     * Update billing cycle summary when milk collection is added
     */
    suspend fun updateBillingCycleSummary(
        billingCycleId: String,
        farmerId: String,
        milkCollection: DailyMilkCollection
    ) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "Cannot update billing cycle summary: User not authenticated")
                return
            }
            
            Log.d(TAG, "Starting updateBillingCycleSummary for farmer $farmerId, cycle $billingCycleId, user $userId")
            Log.d(TAG, "Milk collection data: ${milkCollection.totalMilk}L, ${milkCollection.totalFat}%, ₹${milkCollection.totalAmount}")
            
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d(TAG, "Network not available, skipping Firestore update")
                return
            }
            
            // Get current summary
            val summaryDoc = db.collection("users")
                .document(userId)
                .collection("farmers")
                .document(farmerId)
                .collection("billing_cycle")
                .document(billingCycleId)
            
            Log.d(TAG, "Fetching current summary from Firestore...")
            val currentData = summaryDoc.get().await().data
            val currentMilk = currentData?.get("total_milk") as? Double ?: 0.0
            val currentFat = currentData?.get("total_fat") as? Double ?: 0.0
            val currentAmount = currentData?.get("total_amount") as? Double ?: 0.0
            
            Log.d(TAG, "Current summary - Milk: $currentMilk, Fat: $currentFat, Amount: $currentAmount")
            
            // Update with new milk collection data
            val updatedData = mapOf(
                "total_milk" to (currentMilk + milkCollection.totalMilk),
                "total_fat" to (currentFat + milkCollection.totalFat),
                "total_amount" to (currentAmount + milkCollection.totalAmount),
                "updated_at" to com.google.firebase.Timestamp.now()
            )
            
            Log.d(TAG, "Updated summary - Milk: ${updatedData["total_milk"]}, Fat: ${updatedData["total_fat"]}, Amount: ${updatedData["total_amount"]}")
            
            // Save updated summary
            Log.d(TAG, "Saving updated summary to Firestore...")
            summaryDoc.set(updatedData, com.google.firebase.firestore.SetOptions.merge()).await()
            
            Log.d(TAG, "Successfully updated billing cycle summary for farmer $farmerId, cycle $billingCycleId")
            Log.d(TAG, "New totals - Milk: ${updatedData["total_milk"]}, Fat: ${updatedData["total_fat"]}, Amount: ${updatedData["total_amount"]}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating billing cycle summary: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    /**
     * Get billing cycle summary for a farmer
     */
    suspend fun getBillingCycleSummary(
        billingCycleId: String,
        farmerId: String
    ): Map<String, Any>? {
        return try {
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d(TAG, "Network not available, cannot fetch from Firestore")
                return null
            }
            
            val summaryDoc = db.collection("farmers")
                .document(farmerId)
                .collection("billing_cycle")
                .document(billingCycleId)
                .get()
                .await()
            
            summaryDoc.data
        } catch (e: Exception) {
            Log.e(TAG, "Error getting billing cycle summary: ${e.message}")
            null
        }
    }
    
    /**
     * Get all billing cycle summaries for a farmer
     */
    suspend fun getAllBillingCycleSummaries(farmerId: String): List<Map<String, Any>> {
        return try {
            Log.d(TAG, "Getting all billing cycle summaries for farmer: $farmerId")
            
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d(TAG, "Network not available, cannot fetch from Firestore")
                return emptyList()
            }
            
            // Debug: Log the exact path we're querying
            val path = "farmers/$farmerId/billing_cycle"
            Log.d(TAG, "Querying Firestore path: $path")
            
            val summaries = db.collection("farmers")
                .document(farmerId)
                .collection("billing_cycle")
                .get()
                .await()
            
            Log.d(TAG, "Found ${summaries.documents.size} billing cycle documents for farmer $farmerId")
            
            // Debug: List all document IDs
            summaries.documents.forEach { doc ->
                Log.d(TAG, "Document ID: ${doc.id}")
            }
            
            val result = mutableListOf<Map<String, Any>>()
            
            for (cycleDoc in summaries.documents) {
                Log.d(TAG, "Processing billing cycle document: ${cycleDoc.id}")
                val data = cycleDoc.data
                if (data != null) {
                    Log.d(TAG, "Billing cycle data: $data")
                    result.add(data)
                } else {
                    Log.d(TAG, "Billing cycle document ${cycleDoc.id} has no data")
                }
            }
            
            Log.d(TAG, "Fetched ${result.size} billing cycle summaries for farmer $farmerId")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all billing cycle summaries: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            emptyList()
        }
    }
    
    /**
     * Calculate total amount from billing cycles for a farmer (this represents what has been paid)
     */
    suspend fun calculateTotalPaidFromBillingCycles(farmerId: String): Double {
        return try {
            Log.d(TAG, "Calculating total paid from billing cycles for farmer: $farmerId")
            
            val summaries = getAllBillingCycleSummaries(farmerId)
            Log.d(TAG, "Found ${summaries.size} billing cycle summaries for farmer $farmerId")
            
            val totalPaidFromBillingCycles = summaries.sumOf { summary -> 
                val amount = (summary["total_amount"] as? Double) ?: 0.0
                Log.d(TAG, "Billing cycle summary amount: ₹$amount")
                amount
            }
            
            Log.d(TAG, "Calculated total paid from billing cycles for farmer $farmerId: ₹$totalPaidFromBillingCycles")
            totalPaidFromBillingCycles
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total paid from billing cycles: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            0.0
        }
    }
    
    /**
     * Calculate pending amount for a farmer
     */
    suspend fun calculatePendingAmount(farmerId: String): Double {
        return try {
            val summaries = getAllBillingCycleSummaries(farmerId)
            val totalPaidFromBillingCycles = summaries.sumOf { 
                (it["total_amount"] as? Double) ?: 0.0 
            }
            
            Log.d(TAG, "Calculated pending amount for farmer $farmerId: $totalPaidFromBillingCycles")
            totalPaidFromBillingCycles
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating pending amount: ${e.message}")
            0.0
        }
    }
    
    /**
     * Delete billing cycle summary when billing cycle is deleted
     */
    suspend fun deleteBillingCycleSummary(billingCycleId: String, farmerId: String) {
        try {
            if (!networkUtils.isCurrentlyOnline()) {
                Log.d(TAG, "Network not available, skipping Firestore deletion")
                return
            }
            
            db.collection("farmers")
                .document(farmerId)
                .collection("billing_cycle")
                .document(billingCycleId)
                .delete()
                .await()
            
            Log.d(TAG, "Deleted billing cycle summary for farmer $farmerId, cycle $billingCycleId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting billing cycle summary: ${e.message}")
        }
    }
} 