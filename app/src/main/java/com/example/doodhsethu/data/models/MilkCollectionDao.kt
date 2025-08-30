package com.example.doodhsethu.data.models

import androidx.room.*
import java.util.*

@Dao
interface MilkCollectionDao {
    
    @Query("SELECT * FROM milk_collections ORDER BY collectedAt DESC")
    suspend fun getAllMilkCollections(): List<MilkCollection>
    
    @Query("SELECT * FROM milk_collections WHERE farmerId = :farmerId ORDER BY collectedAt DESC")
    suspend fun getMilkCollectionsByFarmer(farmerId: String): List<MilkCollection>
    
    @Query("SELECT * FROM milk_collections WHERE farmerId = :farmerId AND collectedAt >= :startDate AND collectedAt <= :endDate ORDER BY collectedAt DESC")
    suspend fun getMilkCollectionsByFarmerAndDateRange(farmerId: String, startDate: Date, endDate: Date): List<MilkCollection>
    
    @Query("SELECT * FROM milk_collections WHERE collectedAt BETWEEN :startDate AND :endDate ORDER BY collectedAt DESC")
    suspend fun getMilkCollectionsByDateRange(startDate: Date, endDate: Date): List<MilkCollection>
    
    @Query("SELECT * FROM milk_collections WHERE isSynced = 0")
    suspend fun getUnsyncedMilkCollections(): List<MilkCollection>
    
    @Query("SELECT * FROM milk_collections WHERE id = :id")
    suspend fun getMilkCollectionById(id: String): MilkCollection?
    
    @Query("UPDATE milk_collections SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markMilkCollectionsAsSynced(ids: List<String>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkCollection(milkCollection: MilkCollection)
    
    @Update
    suspend fun updateMilkCollection(milkCollection: MilkCollection)
    
    @Delete
    suspend fun deleteMilkCollection(milkCollection: MilkCollection)
    
    @Query("DELETE FROM milk_collections WHERE id = :id")
    suspend fun deleteMilkCollectionById(id: String)
    
    // Report queries
    @Query("""
        SELECT 
            strftime('%Y-%m-%d', collectedAt/1000, 'unixepoch') as date,
            SUM(CASE WHEN session = 'AM' THEN quantity ELSE 0 END) as amQuantity,
            SUM(CASE WHEN session = 'PM' THEN quantity ELSE 0 END) as pmQuantity,
            AVG(CASE WHEN session = 'AM' THEN fatPercentage ELSE 0 END) as amFat,
            AVG(CASE WHEN session = 'PM' THEN fatPercentage ELSE 0 END) as pmFat,
            SUM(CASE WHEN session = 'AM' THEN totalPrice ELSE 0 END) as amPrice,
            SUM(CASE WHEN session = 'PM' THEN totalPrice ELSE 0 END) as pmPrice,
            SUM(quantity) as totalQuantity,
            SUM(totalPrice) as totalPrice
        FROM milk_collections 
        WHERE collectedAt BETWEEN :startDate AND :endDate
        GROUP BY strftime('%Y-%m-%d', collectedAt/1000, 'unixepoch')
        ORDER BY date DESC
    """)
    suspend fun getMilkReportEntries(startDate: Date, endDate: Date): List<MilkReportEntry>
    
    @Query("""
        SELECT 
            'custom' as period,
            COUNT(DISTINCT strftime('%Y-%m-%d', collectedAt/1000, 'unixepoch')) as totalDays,
            SUM(CASE WHEN session = 'AM' THEN quantity ELSE 0 END) as totalAmQuantity,
            SUM(CASE WHEN session = 'PM' THEN quantity ELSE 0 END) as totalPmQuantity,
            SUM(quantity) as totalQuantity,
            SUM(CASE WHEN session = 'AM' THEN totalPrice ELSE 0 END) as totalAmPrice,
            SUM(CASE WHEN session = 'PM' THEN totalPrice ELSE 0 END) as totalPmPrice,
            SUM(totalPrice) as totalPrice,
            AVG(fatPercentage) as averageFat
        FROM milk_collections 
        WHERE collectedAt BETWEEN :startDate AND :endDate
    """)
    suspend fun getMilkReportSummary(startDate: Date, endDate: Date): MilkReportSummary?
} 