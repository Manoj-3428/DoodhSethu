package com.example.doodhsethu.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.example.doodhsethu.utils.DateConverter
import java.util.*

@Entity(tableName = "users")
@TypeConverters(DateConverter::class)
data class User(
    @PrimaryKey @ColumnInfo(name = "userId") val userId: String = "",
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "password") val password: String = "",
    @ColumnInfo(name = "role") val role: String = "user",
    @ColumnInfo(name = "createdAt") val createdAt: Date = Date(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Date = Date(),
    @ColumnInfo(name = "isSynced") val isSynced: Boolean = false
) 