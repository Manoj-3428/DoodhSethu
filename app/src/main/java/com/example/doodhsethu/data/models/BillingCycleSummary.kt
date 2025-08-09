package com.example.doodhsethu.data.models

import com.google.firebase.Timestamp

// Firestore structure for billing cycle summary
data class BillingCycleSummaryFirestore(
    val total_milk: Double = 0.0,
    val total_fat: Double = 0.0,
    val total_amount: Double = 0.0,
    val created_at: Timestamp = Timestamp.now(),
    val updated_at: Timestamp = Timestamp.now()
)

// Local Room entity for billing cycle summary
data class BillingCycleSummaryLocal(
    val billingCycleId: String,
    val farmerId: String,
    val totalMilk: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalAmount: Double = 0.0,
    val isSynced: Boolean = false
)

// Data class for farmer profile with billing cycle summaries
data class FarmerProfileWithBillingCycles(
    val farmerId: String,
    val farmerName: String,
    val totalEarnings: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val billingCycleSummaries: List<BillingCycleSummaryLocal> = emptyList()
) 