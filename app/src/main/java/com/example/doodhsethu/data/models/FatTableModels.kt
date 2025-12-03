package com.example.doodhsethu.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "fat_table")
data class FatRangeRow(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "from") val from: Float,
    @ColumnInfo(name = "to") val to: Float,
    @ColumnInfo(name = "price") val price: Double,
    @ColumnInfo(name = "addedBy") val addedBy: String = "",
    @ColumnInfo(name = "isSynced") val isSynced: Boolean = false
) 