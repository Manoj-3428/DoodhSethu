package com.example.doodhsethu.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.example.doodhsethu.utils.DateConverter
import java.util.*

@Entity(tableName = "milk_collections")
@TypeConverters(DateConverter::class)
data class MilkCollection(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "farmerId") val farmerId: String = "",
    @ColumnInfo(name = "farmerName") val farmerName: String = "",
    @ColumnInfo(name = "quantity") val quantity: Double = 0.0,
    @ColumnInfo(name = "fatPercentage") val fatPercentage: Double = 0.0,
    @ColumnInfo(name = "basePrice") val basePrice: Double = 0.0,
    @ColumnInfo(name = "totalPrice") val totalPrice: Double = 0.0,
    @ColumnInfo(name = "session") val session: String = "", // "AM" or "PM"
    @ColumnInfo(name = "collectedBy") val collectedBy: String = "",
    @ColumnInfo(name = "collectedAt") val collectedAt: Date = Date(),
    @ColumnInfo(name = "isSynced") val isSynced: Boolean = false
)

// Data class for milk report entries
data class MilkReportEntry(
    val date: String,
    val amQuantity: Double = 0.0,
    val pmQuantity: Double = 0.0,
    val amFat: Double = 0.0,
    val pmFat: Double = 0.0,
    val amPrice: Double = 0.0,
    val pmPrice: Double = 0.0,
    val totalQuantity: Double = 0.0,
    val totalPrice: Double = 0.0
)

// Data class for detailed farmer milk collection data (shown when clicking on a date)
data class FarmerMilkDetail(
    val farmerId: String,
    val farmerName: String,
    val amMilk: Double = 0.0,
    val amFat: Double = 0.0,
    val amPrice: Double = 0.0,
    val pmMilk: Double = 0.0,
    val pmFat: Double = 0.0,
    val pmPrice: Double = 0.0,
    val totalMilk: Double = 0.0,
    val totalPrice: Double = 0.0
)

// Data class for milk report summary
data class MilkReportSummary(
    val period: String,
    val totalDays: Int,
    val totalAmQuantity: Double,
    val totalPmQuantity: Double,
    val totalQuantity: Double,
    val totalAmPrice: Double,
    val totalPmPrice: Double,
    val totalPrice: Double,
    val averageFat: Double
) 