package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.AppDatabase
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.data.models.FatRangeRow
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FatTableRepository(context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val fatTableDao = db.fatTableDao()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getAllFatRows(): List<FatRangeRow> = withContext(Dispatchers.IO) {
        try {
            val rows = fatTableDao.getAllFatRows()
            android.util.Log.d("FatTableRepository", "getAllFatRows: Retrieved ${rows.size} rows from database")
            rows.forEachIndexed { index, row ->
                android.util.Log.d("FatTableRepository", "Row $index: ${row.from}-${row.to} = ₹${row.price} (synced: ${row.isSynced})")
            }
            rows
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error getting fat rows: ${e.message}")
            emptyList()
        }
    }

    suspend fun insertFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        fatTableDao.insertFatRow(row)
    }

    suspend fun insertFatRows(rows: List<FatRangeRow>) = withContext(Dispatchers.IO) {
        fatTableDao.insertFatRows(rows)
    }

    suspend fun updateFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        fatTableDao.updateFatRow(row)
    }

    suspend fun deleteFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        fatTableDao.deleteFatRow(row)
    }

    suspend fun deleteAllFatRows() = withContext(Dispatchers.IO) {
        fatTableDao.deleteAllFatRows()
    }

    // Firestore sync methods
    suspend fun fetchFatTableFromFirestore(isOnline: Boolean): List<FatRangeRow> = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext emptyList()
        try {
            val snapshot = firestore.collection("fat_table").get().await()
            snapshot.documents.mapNotNull { doc ->
                val from = doc.getDouble("from")?.toFloat()
                val to = doc.getDouble("to")?.toFloat()
                val price = doc.getLong("price")?.toInt()
                if (from != null && to != null && price != null) {
                    FatRangeRow(from = from, to = to, price = price)
                } else null
            }
        } catch (e: Exception) {
            // Return empty list if Firestore fetch fails
            emptyList()
        }
    }

    suspend fun syncLocalWithFirestore(isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            val remoteRows = fetchFatTableFromFirestore(true)
            if (remoteRows.isNotEmpty()) {
                // Only replace local data if we successfully got remote data
                deleteAllFatRows()
                insertFatRows(remoteRows.map { it.copy(isSynced = true) })
            }
        } catch (e: Exception) {
            // If sync fails, keep local data intact
            throw e
        }
    }
    
    // Load data from Firestore without clearing local data first
    suspend fun loadFromFirestore(isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            val remoteRows = fetchFatTableFromFirestore(true)
            if (remoteRows.isNotEmpty()) {
                // Merge remote data with local data
                val localRows = getAllFatRows()
                val allRows = (localRows + remoteRows).distinctBy { "${it.from}-${it.to}" }
                deleteAllFatRows()
                insertFatRows(allRows.map { it.copy(isSynced = true) })
            }
        } catch (e: Exception) {
            // If loading fails, keep existing local data
        }
    }
    
    // Upload local changes to Firestore
    suspend fun uploadToFirestore(isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        try {
            val unsyncedRows = fatTableDao.getUnsyncedFatRows()
            unsyncedRows.forEach { row ->
                val data = mapOf(
                    "from" to row.from,
                    "to" to row.to,
                    "price" to row.price
                )
                firestore.collection("fat_table").add(data).await()
            }
            if (unsyncedRows.isNotEmpty()) {
                fatTableDao.markFatRowsAsSynced(unsyncedRows.map { it.id })
            }
        } catch (e: Exception) {
            // If upload fails, data remains unsynced for later retry
        }
    }

    // Sync with Firestore (for AutoSyncManager)
    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Starting Firestore sync...")
            uploadToFirestore(true)
            android.util.Log.d("FatTableRepository", "Firestore sync completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error during Firestore sync: ${e.message}")
        }
    }

    // Load from Firestore (for AutoSyncManager)
    suspend fun loadFromFirestore() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Loading fat table from Firestore...")
            val remoteRows = fetchFatTableFromFirestore(true)
            if (remoteRows.isNotEmpty()) {
                // Merge remote data with local data
                val localRows = getAllFatRows()
                val allRows = (localRows + remoteRows).distinctBy { "${it.from}-${it.to}" }
                deleteAllFatRows()
                insertFatRows(allRows.map { it.copy(isSynced = true) })
            }
            android.util.Log.d("FatTableRepository", "Fat table loaded from Firestore successfully")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error loading fat table from Firestore: ${e.message}")
        }
    }
} 