package com.example.doodhsethu.data.models

import androidx.room.*
import com.example.doodhsethu.utils.DateConverter
import java.util.*

@Entity(tableName = "daily_milk_collections")
@TypeConverters(DateConverter::class)
data class DailyMilkCollection(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "date") val date: String = "", // Format: "01-08-2025"
    @ColumnInfo(name = "farmerId") val farmerId: String = "",
    @ColumnInfo(name = "farmerName") val farmerName: String = "",
    @ColumnInfo(name = "amMilk") val amMilk: Double = 0.0,
    @ColumnInfo(name = "amFat") val amFat: Double = 0.0,
    @ColumnInfo(name = "amPrice") val amPrice: Double = 0.0,
    @ColumnInfo(name = "pmMilk") val pmMilk: Double = 0.0,
    @ColumnInfo(name = "pmFat") val pmFat: Double = 0.0,
    @ColumnInfo(name = "pmPrice") val pmPrice: Double = 0.0,
    @ColumnInfo(name = "totalMilk") val totalMilk: Double = 0.0,
    @ColumnInfo(name = "totalFat") val totalFat: Double = 0.0,
    @ColumnInfo(name = "totalAmount") val totalAmount: Double = 0.0,
    @ColumnInfo(name = "createdAt") val createdAt: Date = Date(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Date = Date(),
    @ColumnInfo(name = "isSynced") val isSynced: Boolean = false
)

// Firestore data class for the new structure
data class DailyMilkCollectionFirestore(
    val farmerId: String = "",
    val farmerName: String = "",
    val am_milk: Double = 0.0,
    val am_fat: Double = 0.0,
    val am_price: Double = 0.0,
    val pm_milk: Double = 0.0,
    val pm_fat: Double = 0.0,
    val pm_price: Double = 0.0,
    val total_milk: Double = 0.0,
    val total_fat: Double = 0.0,
    val total_amount: Double = 0.0,
    val created_at: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val updated_at: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
)

// Data class for session-specific updates
data class MilkCollectionSession(
    val milk: Double = 0.0,
    val fat: Double = 0.0,
    val price: Double = 0.0
) 