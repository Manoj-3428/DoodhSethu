package com.example.doodhsethu.data.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface DailyMilkCollectionDao {
    @Query("SELECT * FROM daily_milk_collections ORDER BY date DESC")
    fun getAllDailyMilkCollections(): Flow<List<DailyMilkCollection>>

    @Query("SELECT * FROM daily_milk_collections WHERE date = :date")
    suspend fun getDailyMilkCollectionsByDate(date: String): List<DailyMilkCollection>

    @Query("SELECT * FROM daily_milk_collections WHERE farmerId = :farmerId AND date = :date")
    suspend fun getDailyMilkCollectionByFarmerAndDate(farmerId: String, date: String): DailyMilkCollection?

    @Query("SELECT * FROM daily_milk_collections WHERE farmerId = :farmerId ORDER BY date DESC")
    suspend fun getDailyMilkCollectionsByFarmer(farmerId: String): List<DailyMilkCollection>

    @Query("SELECT * FROM daily_milk_collections WHERE farmerId = :farmerId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getDailyMilkCollectionsByFarmerAndDateRange(farmerId: String, startDate: String, endDate: String): List<DailyMilkCollection>

    @Query("SELECT * FROM daily_milk_collections WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getDailyMilkCollectionsByDateRange(startDate: String, endDate: String): List<DailyMilkCollection>

    @Query("SELECT * FROM daily_milk_collections WHERE isSynced = 0")
    suspend fun getUnsyncedDailyMilkCollections(): List<DailyMilkCollection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyMilkCollection(dailyMilkCollection: DailyMilkCollection)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyMilkCollections(dailyMilkCollections: List<DailyMilkCollection>)

    @Update
    suspend fun updateDailyMilkCollection(dailyMilkCollection: DailyMilkCollection)

    @Delete
    suspend fun deleteDailyMilkCollection(dailyMilkCollection: DailyMilkCollection)

    @Query("DELETE FROM daily_milk_collections WHERE id = :id")
    suspend fun deleteDailyMilkCollectionById(id: String)

    @Query("UPDATE daily_milk_collections SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markDailyMilkCollectionsAsSynced(ids: List<String>)

    @Query("DELETE FROM daily_milk_collections")
    suspend fun deleteAllDailyMilkCollections()

    // Remove duplicate entries for the same farmer and date, keeping only the most recent one
    @Query("""
        DELETE FROM daily_milk_collections 
        WHERE id NOT IN (
            SELECT MAX(id) 
            FROM daily_milk_collections 
            GROUP BY farmerId, date
        )
    """)
    suspend fun removeDuplicateEntries()
} 