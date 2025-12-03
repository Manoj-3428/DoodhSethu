package com.example.doodhsethu.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "billing_cycles")
data class BillingCycle(
    @PrimaryKey val id: String,
    val name: String, // Added name field for billing cycle naming convention
    val startDate: Date,
    val endDate: Date,
    val totalAmount: Double, // Total amount paid to all farmers for this period
    val isPaid: Boolean = true, // Always true since creating billing cycle = payment made
    val isActive: Boolean = true, // True if billing cycle is within current month, false if passive
    val addedBy: String = "", // User ID who created this billing cycle
    val createdAt: Date = Date(),
    val isSynced: Boolean = false
)

@Entity(tableName = "farmer_billing_details")
data class FarmerBillingDetail(
    @PrimaryKey val id: String,
    val billingCycleId: String,
    val farmerId: String,
    val farmerName: String,
    val originalAmount: Double, // Original amount farmer should get for this period
    val paidAmount: Double = 0.0, // Amount actually paid to farmer
    val balanceAmount: Double = 0.0, // Remaining amount (originalAmount - paidAmount)
    val isPaid: Boolean = false,
    val paymentDate: Date? = null,
    val isSynced: Boolean = false
)

data class BillingCycleSummary(
    val billingCycleId: String,
    val startDate: Date,
    val endDate: Date,
    val totalAmount: Double,
    val farmerDetails: List<FarmerBillingDetail>
)

data class FarmerPaymentSummary(
    val farmerId: String,
    val farmerName: String,
    val totalOriginalAmount: Double, // Total amount from all billing cycles
    val totalPaidAmount: Double, // Total amount paid across all cycles
    val totalBalanceAmount: Double, // Remaining balance
    val billingCycles: List<FarmerBillingDetail>
) 