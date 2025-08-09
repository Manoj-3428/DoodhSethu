package com.example.doodhsethu.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import androidx.room.Ignore
import com.example.doodhsethu.utils.DateConverter
import java.util.*

@Entity(tableName = "farmers")
@TypeConverters(DateConverter::class)
data class Farmer(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = "",
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "phone") val phone: String = "",
    @ColumnInfo(name = "address") val address: String = "",
    @ColumnInfo(name = "photoUrl") val photoUrl: String = "",
    @ColumnInfo(name = "addedBy") val addedBy: String = "",
    @ColumnInfo(name = "createdAt") val createdAt: Date = Date(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Date = Date(),
    @ColumnInfo(name = "synced") val synced: Boolean = false,
    @ColumnInfo(name = "totalAmount") val totalAmount: Double = 0.0, // Current month total earnings
    @ColumnInfo(name = "pendingAmount") val pendingAmount: Double = 0.0, // Pending amount to be paid
    @ColumnInfo(name = "billingCycles") val billingCycles: String = "" // JSON string of billing cycle earnings
)

data class Pricing(
    val id: String = "",
    val basePrice: Double = 0.0, // per liter
    val fatMultiplier: Double = 1.0, // multiplier for fat percentage
    val updatedBy: String = "", // Admin ID
    val updatedAt: Date = Date()
)

data class ReportData(
    val totalMilk: Double = 0.0,
    val totalValue: Double = 0.0,
    val farmerCount: Int = 0,
    val collectionCount: Int = 0,
    val period: String = "" // "today", "week", "month"
)

sealed class NavigationItem(
    val title: String,
    val icon: Int,
    val route: String
) {
    object Home : NavigationItem("Home", com.example.doodhsethu.R.drawable.ic_home, "home")
    object AddFarmer : NavigationItem("Add Farmer", com.example.doodhsethu.R.drawable.ic_add, "add_farmer")
    object MilkCollection : NavigationItem("Milk Collection", com.example.doodhsethu.R.drawable.ic_local_drink, "milk_collection")
    object Profile : NavigationItem("Profile", com.example.doodhsethu.R.drawable.ic_person, "profile")
    object Reports : NavigationItem("Reports", com.example.doodhsethu.R.drawable.ic_assessment, "reports")

    object FatTable : NavigationItem("Fat Table", com.example.doodhsethu.R.drawable.ic_assessment, "fat_table")
    object BillingCycles : NavigationItem("Billing Cycles", com.example.doodhsethu.R.drawable.ic_receipt_long, "billing_cycles")

    object UserReports : NavigationItem("User Reports", com.example.doodhsethu.R.drawable.ic_assessment, "user_reports")

}