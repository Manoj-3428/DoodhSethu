package com.example.doodhsethu.data.models

import androidx.room.*

@Dao
interface FatTableDao {
    @Query("SELECT * FROM fat_table ORDER BY `from` ASC")
    suspend fun getAllFatRows(): List<FatRangeRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFatRow(row: FatRangeRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFatRows(rows: List<FatRangeRow>)

    @Update
    suspend fun updateFatRow(row: FatRangeRow)

    @Delete
    suspend fun deleteFatRow(row: FatRangeRow)

    @Query("DELETE FROM fat_table")
    suspend fun deleteAllFatRows()

    // Sync-related queries
    @Query("SELECT * FROM fat_table WHERE isSynced = 0")
    suspend fun getUnsyncedFatRows(): List<FatRangeRow>

    @Query("UPDATE fat_table SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markFatRowsAsSynced(ids: List<Int>)
} 