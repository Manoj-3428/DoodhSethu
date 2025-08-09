package com.example.doodhsethu.data.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerDao {
    @Query("SELECT * FROM farmers ORDER BY name ASC")
    fun getAllFarmers(): Flow<List<Farmer>>

    @Query("SELECT * FROM farmers ORDER BY name ASC")
    suspend fun getAllFarmersList(): List<Farmer>

    @Query("SELECT * FROM farmers WHERE id = :id")
    suspend fun getFarmerById(id: String): Farmer?

    @Query("SELECT * FROM farmers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    suspend fun searchFarmers(query: String): List<Farmer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmer(farmer: Farmer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmers(farmers: List<Farmer>)

    @Update
    suspend fun updateFarmer(farmer: Farmer)

    @Delete
    suspend fun deleteFarmer(farmer: Farmer)

    @Query("DELETE FROM farmers WHERE id = :id")
    suspend fun deleteFarmerById(id: String)

    @Query("DELETE FROM farmers")
    suspend fun deleteAllFarmers()

    @Query("SELECT * FROM farmers WHERE synced = 0")
    suspend fun getUnsyncedFarmers(): List<Farmer>

    @Query("UPDATE farmers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markFarmersAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM farmers")
    suspend fun getFarmerCount(): Int
} 