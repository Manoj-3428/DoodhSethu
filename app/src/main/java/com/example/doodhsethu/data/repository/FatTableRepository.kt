package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.data.models.FatRangeRow
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FatTableRepository(context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val fatTableDao = db.fatTableDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val authViewModel = AuthViewModel()
    private val appContext = context.applicationContext
    
    // Get current user ID for Firestore operations
    private fun getCurrentUserId(): String? {
        return authViewModel.getStoredUser(appContext)?.userId
    }
    
    // Prevent concurrent sync operations
    private var isSyncing = false
    
    // Real-time sync state
    private var isRealTimeSyncActive = false
    private var realTimeSyncJob: kotlinx.coroutines.Job? = null
    
    // Callback for UI updates
    private var onDataChangedCallback: (() -> Unit)? = null
    
    // Flag to track offline sync progress
    private var isOfflineSyncInProgress = false
    
    // Timestamp for when offline sync completed (for protection period)
    private var lastOfflineSyncTime = 0L
    
    /**
     * Set callback for data changes
     */
    fun setOnDataChangedCallback(callback: () -> Unit) {
        onDataChangedCallback = callback
    }
    
    /**
     * Utility function to round float to exactly 3 decimal places as Double
     */
    private fun roundToDecimal(value: Float): Double {
        return (value * 1000).roundToInt() / 1000.0
    }
    
    /**
     * Start real-time sync with Firestore
     */
    fun startRealTimeSync() {
        if (isRealTimeSyncActive) {
            android.util.Log.d("FatTableRepository", "Real-time sync already active")
            return
        }
        
        isRealTimeSyncActive = true
        realTimeSyncJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                android.util.Log.d("FatTableRepository", "Starting real-time sync with Firestore")
                
                // Set up real-time listener with user-specific path
                val userId = getCurrentUserId()
                if (userId == null) {
                    android.util.Log.e("FatTableRepository", "Cannot start real-time sync: User not authenticated")
                    return@launch
                }
                
                firestore.collection("users").document(userId).collection("fat_table")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("FatTableRepository", "Real-time sync error: ${error.message}")
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    // Process real-time updates
                                    processRealTimeUpdates(snapshot)
                                } catch (e: Exception) {
                                    android.util.Log.e("FatTableRepository", "Error processing real-time updates: ${e.message}")
                                }
                            }
                        }
                    }
                
                android.util.Log.d("FatTableRepository", "Real-time sync listener established")
            } catch (e: Exception) {
                android.util.Log.e("FatTableRepository", "Error starting real-time sync: ${e.message}")
                isRealTimeSyncActive = false
            }
        }
    }
    
    /**
     * Stop real-time sync
     */
    fun stopRealTimeSync() {
        if (!isRealTimeSyncActive) {
            return
        }
        
        isRealTimeSyncActive = false
        realTimeSyncJob?.cancel()
        realTimeSyncJob = null
        android.util.Log.d("FatTableRepository", "Real-time sync stopped")
    }
    
    /**
     * Process real-time updates from Firestore
     */
    private suspend fun processRealTimeUpdates(snapshot: com.google.firebase.firestore.QuerySnapshot) = withContext(Dispatchers.IO) {
        try {
            // Skip processing if offline sync is in progress
            if (isOfflineSyncInProgress) {
                android.util.Log.d("FatTableRepository", "Skipping real-time updates - offline sync in progress")
                return@withContext
            }
            
            // Add a small delay to ensure offline sync operations are fully complete
            kotlinx.coroutines.delay(200)
            
            val remoteRows = snapshot.documents.mapNotNull { doc ->
                val from = doc.getDouble("from")?.toFloat()
                val to = doc.getDouble("to")?.toFloat()
                val price = doc.getDouble("price")
                
                if (from != null && to != null && price != null) {
                    // Round the float values to 3 decimal places to avoid precision issues
                    val roundedFrom = (from * 1000).roundToInt() / 1000f
                    val roundedTo = (to * 1000).roundToInt() / 1000f
                    
                    FatRangeRow(
                        from = roundedFrom,
                        to = roundedTo,
                        price = price,
                        isSynced = true
                    )
                } else null
            }
            
            android.util.Log.d("FatTableRepository", "Processing real-time updates. Remote: ${remoteRows.size} entries")
            
            // Get current local rows
            val localRows = getAllFatRows()
            android.util.Log.d("FatTableRepository", "Local: ${localRows.size} entries")
            
            // Process updates without creating duplicates
            // Note: We process even if remoteRows is empty to handle deletions
            processRemoteUpdatesSafely(remoteRows, localRows)
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error processing real-time updates: ${e.message}")
        }
    }
    
    /**
     * Process remote updates safely without creating duplicates
     */
    private suspend fun processRemoteUpdatesSafely(remoteRows: List<FatRangeRow>, localRows: List<FatRangeRow>) = withContext(Dispatchers.IO) {
        try {
            var dataChanged = false
            
            // Create maps for comparison
            val remoteMap = remoteRows.associateBy { "${it.from}-${it.to}-${it.price}" }
            val localMap = localRows.associateBy { "${it.from}-${it.to}-${it.price}" }
            
            // Find entries that exist locally but not remotely (deletions)
            // But be very careful about recently updated entries
            val deletedEntries = localRows.filter { localRow ->
                val key = "${localRow.from}-${localRow.to}-${localRow.price}"
                if (!remoteMap.containsKey(key) && localRow.isSynced) {
                    // Check if we're in the protection period after offline sync (5 seconds)
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastOfflineSync = currentTime - lastOfflineSyncTime
                    val isInProtectionPeriod = timeSinceLastOfflineSync < 5000 // 5 seconds
                    
                    if (isInProtectionPeriod) {
                        android.util.Log.d("FatTableRepository", "Skipping deletion during protection period: ${localRow.from}-${localRow.to} = ₹${localRow.price}")
                        false // Don't delete during protection period
                    } else {
                        // Check if this might be a recently updated entry
                        // Look for remote entries with same price but different range
                        val mightBeRecentlyUpdated = remoteRows.any { remoteRow ->
                            remoteRow.price == localRow.price && 
                            (remoteRow.from != localRow.from || remoteRow.to != localRow.to)
                        }
                        
                        // Also check if this might be a recently synced entry that was just updated
                        // by looking at the timestamp of when the entry was last synced
                        val mightBeRecentlySynced = localRow.isSynced && 
                            remoteRows.any { remoteRow ->
                                // If there's a remote entry with same price but different range,
                                // and we have a local entry that's synced, it might be an update
                                remoteRow.price == localRow.price && 
                                (remoteRow.from != localRow.from || remoteRow.to != localRow.to)
                            }
                        
                        // Additional check: if we have any remote entries with the same price,
                        // be extra careful about deleting local entries
                        val hasRemoteEntriesWithSamePrice = remoteRows.any { remoteRow ->
                            remoteRow.price == localRow.price
                        }
                        
                        // Additional safety check: if we have any remote entries at all,
                        // and this local entry is synced, be very careful about deleting it
                        val hasAnyRemoteEntries = remoteRows.isNotEmpty()
                        
                        // Only delete if:
                        // 1. It's not a recently updated entry
                        // 2. It's not recently synced
                        // 3. There are no remote entries with the same price (extra safety)
                        // 4. There are no remote entries at all (maximum safety)
                        !mightBeRecentlyUpdated && !mightBeRecentlySynced && !hasRemoteEntriesWithSamePrice && !hasAnyRemoteEntries
                    }
                } else {
                    false
                }
            }
            
            // Delete entries that were removed remotely
            if (deletedEntries.isNotEmpty()) {
                for (deletedEntry in deletedEntries) {
                    deleteFatRow(deletedEntry)
                    android.util.Log.d("FatTableRepository", "Deleted entry from real-time sync: ${deletedEntry.from}-${deletedEntry.to} = ₹${deletedEntry.price}")
                }
                dataChanged = true
            }
            
            // Find new entries that don't exist locally
            val newEntries = remoteRows.filter { remoteRow ->
                val key = "${remoteRow.from}-${remoteRow.to}-${remoteRow.price}"
                !localMap.containsKey(key)
            }
            
            // Insert new entries (but be more careful about recently synced entries)
            if (newEntries.isNotEmpty()) {
                // Filter out entries that might have been just synced from offline operations
                val filteredNewEntries = newEntries.filter { remoteRow ->
                    // Check if this might be a recently synced entry by looking for similar entries
                    val hasSimilarLocalEntry = localRows.any { localRow ->
                        // If there's a local entry with same price but different range, it might be an update
                        localRow.price == remoteRow.price && 
                        (localRow.from != remoteRow.from || localRow.to != remoteRow.to) &&
                        localRow.isSynced
                    }
                    
                    // Also check if this might be a recently updated entry that was just synced
                    val mightBeRecentlySyncedUpdate = localRows.any { localRow ->
                        // Look for local entries with same price but different range that are synced
                        localRow.price == remoteRow.price && 
                        (localRow.from != remoteRow.from || localRow.to != remoteRow.to) &&
                        localRow.isSynced
                    }
                    
                    // Only add if no similar entry exists and it's not a recently synced update
                    !hasSimilarLocalEntry && !mightBeRecentlySyncedUpdate
                }
                
                if (filteredNewEntries.isNotEmpty()) {
                    val syncedNewEntries = filteredNewEntries.map { it.copy(isSynced = true) }
                    insertFatRows(syncedNewEntries)
                    android.util.Log.d("FatTableRepository", "Added ${filteredNewEntries.size} new entries from real-time sync")
                    dataChanged = true
                }
            }
            
            // Handle potential updates (entries that might have been modified)
            val updatesApplied = handleRemoteUpdates(remoteRows, localRows)
            if (updatesApplied) {
                dataChanged = true
            }
            
            // Clean up any duplicates that might have been created
            val duplicatesCleaned = cleanupCrossDeviceDuplicates(remoteRows, localRows)
            if (duplicatesCleaned) {
                dataChanged = true
            }
            
            // Notify UI if data changed
            if (dataChanged) {
                onDataChangedCallback?.invoke()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error processing remote updates safely: ${e.message}")
        }
    }

    /**
     * Get all fat table rows from local storage (sorted by from value)
     */
    suspend fun getAllFatRows(): List<FatRangeRow> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot get fat rows: User not authenticated")
                return@withContext emptyList()
            }
            fatTableDao.getAllFatRows().filter { it.addedBy == userId }
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error getting fat rows: ${e.message}")
            emptyList()
        }
    }

    /**
     * Insert a new fat range row into local storage
     */
    suspend fun insertFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot insert fat row: User not authenticated")
                return@withContext
            }
            val rowWithUser = row.copy(addedBy = userId)
            fatTableDao.insertFatRow(rowWithUser)
            android.util.Log.d("FatTableRepository", "Inserted fat row: ${row.from}-${row.to} = ₹${row.price}")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error inserting fat row: ${e.message}")
            throw e
        }
    }

    /**
     * Insert multiple fat range rows into local storage
     */
    suspend fun insertFatRows(rows: List<FatRangeRow>) = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot insert fat rows: User not authenticated")
                return@withContext
            }
            val rowsWithUser = rows.map { it.copy(addedBy = userId) }
            fatTableDao.insertFatRows(rowsWithUser)
            android.util.Log.d("FatTableRepository", "Inserted ${rows.size} fat rows")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error inserting fat rows: ${e.message}")
            throw e
        }
    }

    /**
     * Update an existing fat range row in local storage
     */
    suspend fun updateFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
        fatTableDao.updateFatRow(row)
            android.util.Log.d("FatTableRepository", "Updated fat row: ${row.from}-${row.to} = ₹${row.price}")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error updating fat row: ${e.message}")
            throw e
        }
    }

    /**
     * Delete a fat range row from local storage
     */
    suspend fun deleteFatRow(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
        fatTableDao.deleteFatRow(row)
            android.util.Log.d("FatTableRepository", "Deleted fat row: ${row.from}-${row.to} = ₹${row.price}")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error deleting fat row: ${e.message}")
            throw e
        }
    }

    /**
     * Delete all fat range rows from local storage
     */
    suspend fun deleteAllFatRows() = withContext(Dispatchers.IO) {
        try {
            fatTableDao.deleteAllFatRows()
            android.util.Log.d("FatTableRepository", "Deleted all fat rows")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error deleting all fat rows: ${e.message}")
            throw e
        }
    }

    /**
     * Get unsynced fat rows from local storage
     */
    suspend fun getUnsyncedFatRows(): List<FatRangeRow> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot get unsynced fat rows: User not authenticated")
                return@withContext emptyList()
            }
            fatTableDao.getUnsyncedFatRows().filter { it.addedBy == userId }
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error getting unsynced fat rows: ${e.message}")
            emptyList()
        }
    }

    /**
     * Mark fat rows as synced in local storage
     */
    suspend fun markFatRowsAsSynced(ids: List<Int>) = withContext(Dispatchers.IO) {
        try {
            fatTableDao.markFatRowsAsSynced(ids)
            android.util.Log.d("FatTableRepository", "Marked ${ids.size} fat rows as synced")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error marking fat rows as synced: ${e.message}")
            throw e
        }
    }

    /**
     * Validate if a fat range overlaps with existing ranges
     * @param newRow The new fat range to validate
     * @param excludeId ID of the row being edited (to exclude from validation)
     * @return true if no overlap, false if overlap exists
     */
    suspend fun validateFatRange(newRow: FatRangeRow, excludeId: Int? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRows = getAllFatRows()
            
            for (existingRow in currentRows) {
                // Skip the row being edited
                if (excludeId != null && existingRow.id == excludeId) {
                    continue
                }
                
                // Check for overlap: new range overlaps with existing range
                val overlaps = !(newRow.to <= existingRow.from || newRow.from >= existingRow.to)
                
                if (overlaps) {
                    android.util.Log.d("FatTableRepository", "Overlap detected: ${newRow.from}-${newRow.to} overlaps with ${existingRow.from}-${existingRow.to}")
                    return@withContext false
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error validating fat range: ${e.message}")
            false
        }
    }

    /**
     * Fetch fat table data from Firestore
     */
    suspend fun fetchFromFirestore(): List<FatRangeRow> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot fetch from Firestore: User not authenticated")
                return@withContext emptyList()
            }
            
            val snapshot = firestore.collection("users").document(userId).collection("fat_table").get().await()
            val rows = snapshot.documents.mapNotNull { doc ->
                val from = doc.getDouble("from")?.toFloat()
                val to = doc.getDouble("to")?.toFloat()
                val price = doc.getDouble("price")
                
                if (from != null && to != null && price != null) {
                    // Round the float values to 3 decimal places to avoid precision issues
                    val roundedFrom = (from * 1000).roundToInt() / 1000f
                    val roundedTo = (to * 1000).roundToInt() / 1000f
                    
                    FatRangeRow(
                        from = roundedFrom,
                        to = roundedTo,
                        price = price,
                        isSynced = true
                    )
                } else null
            }
            android.util.Log.d("FatTableRepository", "Fetched ${rows.size} rows from Firestore")
            rows
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error fetching from Firestore: ${e.message}")
            throw e
        }
    }

    /**
     * Upload a single fat row to Firestore
     */
    suspend fun uploadToFirestore(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
            // Round the float values to 3 decimal places to avoid precision issues in Firestore
            val roundedFrom = (row.from * 1000).roundToInt() / 1000f
            val roundedTo = (row.to * 1000).roundToInt() / 1000f
            
            // Convert to Double with proper rounding to avoid floating point precision issues
            val fromDouble = roundToDecimal(roundedFrom)
            val toDouble = roundToDecimal(roundedTo)
            
            val data = mapOf(
                "from" to fromDouble,
                "to" to toDouble,
                "price" to row.price
            )
            val userId = getCurrentUserId()
            if (userId == null) {
                android.util.Log.e("FatTableRepository", "Cannot upload to Firestore: User not authenticated")
                return@withContext
            }
            
            firestore.collection("users").document(userId).collection("fat_table").add(data).await()
            android.util.Log.d("FatTableRepository", "Uploaded fat row to Firestore: ${fromDouble}-${toDouble} = ₹${row.price}")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error uploading to Firestore: ${e.message}")
            throw e
        }
    }

    /**
     * Update a fat row in Firestore
     */
    suspend fun updateInFirestore(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
            // Round the float values to 3 decimal places to avoid precision issues in Firestore
            val roundedFrom = (row.from * 1000).roundToInt() / 1000f
            val roundedTo = (row.to * 1000).roundToInt() / 1000f
            
            // Convert to Double with proper rounding to avoid floating point precision issues
            val fromDouble = roundToDecimal(roundedFrom)
            val toDouble = roundToDecimal(roundedTo)
            
            // Find the document with matching from, to, and price values
            val snapshot = firestore.collection("users").document(getCurrentUserId() ?: return@withContext).collection("fat_table")
                .whereEqualTo("from", fromDouble)
                .whereEqualTo("to", toDouble)
                .whereEqualTo("price", row.price)
                .get()
                .await()
            
            if (snapshot.documents.isNotEmpty()) {
                val data = mapOf(
                    "from" to fromDouble,
                    "to" to toDouble,
                    "price" to row.price
                )
                snapshot.documents.first().reference.update(data).await()
                android.util.Log.d("FatTableRepository", "Updated fat row in Firestore: ${fromDouble}-${toDouble} = ₹${row.price}")
            } else {
                // If not found, create new document
                uploadToFirestore(row)
            }
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error updating in Firestore: ${e.message}")
            throw e
        }
    }

    /**
     * Delete a fat row from Firestore
     */
    suspend fun deleteFromFirestore(row: FatRangeRow) = withContext(Dispatchers.IO) {
        try {
            // Round the float values to 3 decimal places to avoid precision issues in Firestore
            val roundedFrom = (row.from * 1000).roundToInt() / 1000f
            val roundedTo = (row.to * 1000).roundToInt() / 1000f
            
            // Convert to Double with proper rounding to avoid floating point precision issues
            val fromDouble = roundToDecimal(roundedFrom)
            val toDouble = roundToDecimal(roundedTo)
            
            // Find the document with matching from, to, and price values
            val snapshot = firestore.collection("users").document(getCurrentUserId() ?: return@withContext).collection("fat_table")
                .whereEqualTo("from", fromDouble)
                .whereEqualTo("to", toDouble)
                .whereEqualTo("price", row.price)
                .get()
                .await()
            
            // Delete all matching documents
            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            android.util.Log.d("FatTableRepository", "Deleted fat row from Firestore: ${fromDouble}-${toDouble} = ₹${row.price}")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error deleting from Firestore: ${e.message}")
            throw e
        }
    }

    /**
     * Sync unsynced data from local storage to Firestore (background operation)
     */
    suspend fun syncToFirestore() = withContext(Dispatchers.IO) {
        try {
            val unsyncedRows = getUnsyncedFatRows()
            if (unsyncedRows.isNotEmpty()) {
                android.util.Log.d("FatTableRepository", "Syncing ${unsyncedRows.size} unsynced rows to Firestore")
                
                // Get existing Firestore data to avoid duplicates
                val existingFirestoreRows = fetchFromFirestore()
                val existingKeys = existingFirestoreRows.map { "${it.from}-${it.to}-${it.price}" }.toSet()
                
                // Only upload rows that don't already exist in Firestore
                val newRows = unsyncedRows.filter { row ->
                    val key = "${row.from}-${row.to}-${row.price}"
                    !existingKeys.contains(key)
                }
                
                if (newRows.isNotEmpty()) {
                    // Upload each new row
                    newRows.forEach { row ->
                        uploadToFirestore(row)
                    }
                    
                    // Mark as synced in local storage
                    markFatRowsAsSynced(newRows.map { it.id })
                    
                    android.util.Log.d("FatTableRepository", "Successfully synced ${newRows.size} new rows to Firestore")
                } else {
                    android.util.Log.d("FatTableRepository", "All unsynced rows already exist in Firestore")
                    // Mark all unsynced rows as synced since they already exist
                    markFatRowsAsSynced(unsyncedRows.map { it.id })
                }
                
                // Clean up any duplicates that might have been created
                cleanupFirestoreDuplicates()
            }
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error syncing to Firestore: ${e.message}")
            throw e
        }
    }

    /**
     * Sync data from Firestore to local storage
     */
    suspend fun syncFromFirestore() = withContext(Dispatchers.IO) {
        try {
            val remoteRows = fetchFromFirestore()
            if (remoteRows.isNotEmpty()) {
                android.util.Log.d("FatTableRepository", "Syncing ${remoteRows.size} rows from Firestore")
                
                // Get current local rows
                val localRows = getAllFatRows()
                
                // Create a map of existing rows by their unique key
                val existingRowsMap = localRows.associateBy { "${it.from}-${it.to}-${it.price}" }
                
                // Filter out rows that already exist locally
                val newRows = remoteRows.filter { remoteRow ->
                    val key = "${remoteRow.from}-${remoteRow.to}-${remoteRow.price}"
                    !existingRowsMap.containsKey(key)
                }
                
                if (newRows.isNotEmpty()) {
                    // Mark new rows as synced since they come from Firestore
                    val syncedNewRows = newRows.map { it.copy(isSynced = true) }
                    insertFatRows(syncedNewRows)
                    android.util.Log.d("FatTableRepository", "Successfully synced ${newRows.size} new rows from Firestore")
                } else {
                    android.util.Log.d("FatTableRepository", "No new rows to sync from Firestore")
                }
                
                // Handle potential updates from other devices
                handleRemoteUpdates(remoteRows, localRows)
            }
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error syncing from Firestore: ${e.message}")
            throw e
        }
    }
    
        /**
     * Handle updates from other devices by comparing remote and local data
     */
    private suspend fun handleRemoteUpdates(remoteRows: List<FatRangeRow>, localRows: List<FatRangeRow>): Boolean = withContext(Dispatchers.IO) {
        try {
            var updatesApplied = false
            
            // Create maps for comparison
            val remoteMap = remoteRows.associateBy { "${it.from}-${it.to}-${it.price}" }
            val localMap = localRows.associateBy { "${it.from}-${it.to}-${it.price}" }
            
            // Find entries that exist locally but not remotely (might have been updated)
            val localKeys = localMap.keys
            val remoteKeys = remoteMap.keys
            
            // Check for entries that might have been updated on other devices
            for (localKey in localKeys) {
                if (!remoteKeys.contains(localKey)) {
                    // This local entry doesn't exist in remote anymore
                    // It might have been updated or deleted on another device
                    val localRow = localMap[localKey]
                    if (localRow != null && localRow.isSynced) {
                        // If it was synced before, it might have been updated on another device
                        // We should check if there's a similar entry with different values
                        val similarRemoteRow = remoteRows.find { remoteRow ->
                            // Check if this might be an updated version of our local row
                            // Look for entries with same price but different range, or same range but different price
                            val isSimilar = (remoteRow.price == localRow.price && 
                                           (remoteRow.from != localRow.from || remoteRow.to != localRow.to)) ||
                                          // Or entries that might be the "new" version of an updated entry
                                          (Math.abs(remoteRow.from - localRow.from) < 0.1f && 
                                           Math.abs(remoteRow.to - localRow.to) < 0.1f &&
                                           remoteRow.price != localRow.price)
                            isSimilar
                        }
                        
                        if (similarRemoteRow != null) {
                            // This looks like an update from another device
                            android.util.Log.d("FatTableRepository", "Detected update from another device: ${localRow.from}-${localRow.to} -> ${similarRemoteRow.from}-${similarRemoteRow.to}")
                            
                            // Delete the old local entry first
                            deleteFatRow(localRow)
                            
                            // Insert the new entry from remote
                            val newRemoteRow = similarRemoteRow.copy(isSynced = true)
                            insertFatRow(newRemoteRow)
                            
                            updatesApplied = true
                        }
                    }
                }
            }
            
            // Also check for entries that might be updates but we missed them
            for (remoteRow in remoteRows) {
                val remoteKey = "${remoteRow.from}-${remoteRow.to}-${remoteRow.price}"
                if (!localMap.containsKey(remoteKey)) {
                    // This remote entry doesn't exist locally
                    // Check if it might be an update of an existing entry
                    val potentialUpdate = localRows.find { localRow ->
                        // Look for entries with same price but different range
                        val isSimilar = (remoteRow.price == localRow.price && 
                                       (remoteRow.from != localRow.from || remoteRow.to != localRow.to)) ||
                                      // Or entries that might be the "new" version of an updated entry
                                      (Math.abs(remoteRow.from - localRow.from) < 0.1f && 
                                       Math.abs(remoteRow.to - localRow.to) < 0.1f &&
                                       remoteRow.price != localRow.price)
                        isSimilar && localRow.isSynced
                    }
                    
                    if (potentialUpdate != null) {
                        // This looks like an update
                        android.util.Log.d("FatTableRepository", "Detected update (alternative): ${potentialUpdate.from}-${potentialUpdate.to} -> ${remoteRow.from}-${remoteRow.to}")
                        
                        // Delete the old local entry
                        deleteFatRow(potentialUpdate)
                        
                        // Insert the new entry from remote
                        val newRemoteRow = remoteRow.copy(isSynced = true)
                        insertFatRow(newRemoteRow)
                        
                        updatesApplied = true
                    }
                }
            }
            
            return@withContext updatesApplied
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error handling remote updates: ${e.message}")
            return@withContext false
        }
    }
    
        /**
     * Clean up duplicates that might have been created by cross-device updates
     */
    private suspend fun cleanupCrossDeviceDuplicates(remoteRows: List<FatRangeRow>, localRows: List<FatRangeRow>): Boolean = withContext(Dispatchers.IO) {
        try {
            var duplicatesCleaned = false
            
            // Group local rows by their range (from-to) to find duplicates
            val groupedLocalRows = localRows.groupBy { "${it.from}-${it.to}" }
            
            for ((rangeKey, rows) in groupedLocalRows) {
                if (rows.size > 1) {
                    android.util.Log.d("FatTableRepository", "Found ${rows.size} duplicate entries for range: $rangeKey")
                    
                    // Keep the first entry and delete the rest
                    val rowsToDelete = rows.drop(1)
                    for (rowToDelete in rowsToDelete) {
                        deleteFatRow(rowToDelete)
                        android.util.Log.d("FatTableRepository", "Deleted duplicate entry: ${rowToDelete.from}-${rowToDelete.to} = ₹${rowToDelete.price}")
                        duplicatesCleaned = true
                    }
                }
            }
            
            // Also check if we have entries that should be updated to match remote
            for (remoteRow in remoteRows) {
                val matchingLocalRows = localRows.filter { localRow ->
                    // Check if this remote row might be an updated version of a local row
                    // Look for entries with same price but different range, or same range but different price
                    val isSimilar = (remoteRow.price == localRow.price && 
                                   (remoteRow.from != localRow.from || remoteRow.to != localRow.to)) ||
                                  // Or entries that might be the "new" version of an updated entry
                                  (Math.abs(remoteRow.from - localRow.from) < 0.1f && 
                                   Math.abs(remoteRow.to - localRow.to) < 0.1f &&
                                   remoteRow.price != localRow.price)
                    isSimilar
                }
                
                if (matchingLocalRows.isNotEmpty()) {
                    // We have local entries that might be outdated
                    for (localRow in matchingLocalRows) {
                        if (localRow.isSynced) {
                            // Delete the old local entry and insert the new one from remote
                            deleteFatRow(localRow)
                            val newRemoteRow = remoteRow.copy(isSynced = true)
                            insertFatRow(newRemoteRow)
                            android.util.Log.d("FatTableRepository", "Updated local entry to match remote: ${localRow.from}-${localRow.to} -> ${remoteRow.from}-${remoteRow.to}")
                            duplicatesCleaned = true
                        }
                    }
                }
            }
            
            return@withContext duplicatesCleaned
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error cleaning up cross-device duplicates: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Complete sync operation - sync both directions
     */
    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        // Prevent concurrent sync operations
        if (isSyncing) {
            android.util.Log.d("FatTableRepository", "Sync already in progress, skipping")
            return@withContext
        }
        
        isSyncing = true
        try {
            android.util.Log.d("FatTableRepository", "Starting complete sync with Firestore")
            
            // First, clean up any existing duplicates
            cleanupDuplicateEntries()
            
            // Then, upload any unsynced local data
            syncToFirestore()
            
            // Finally, download latest data from Firestore
            syncFromFirestore()
            
            // Final cleanup to ensure no duplicates remain
            cleanupFirestoreDuplicates()
            
            android.util.Log.d("FatTableRepository", "Complete sync with Firestore finished")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error during complete sync: ${e.message}")
            throw e
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Handle offline-to-online transition sync
     * This method is specifically designed to handle the case where entries were added offline
     */
    suspend fun handleOfflineToOnlineSync() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Handling offline-to-online transition sync")
            
            // Set flag to indicate offline sync is in progress
            isOfflineSyncInProgress = true
            
            // Temporarily stop real-time sync to avoid conflicts during offline sync
            val wasRealTimeActive = isRealTimeSyncActive
            if (wasRealTimeActive) {
                stopRealTimeSync()
                android.util.Log.d("FatTableRepository", "Temporarily stopped real-time sync for offline sync")
            }
            
            // First, sync any offline deletions
            syncOfflineDeletions()
            
            // Then, sync any offline updates
            syncOfflineUpdates()
            
            // Get all unsynced rows
            val unsyncedRows = getUnsyncedFatRows()
            if (unsyncedRows.isEmpty()) {
                android.util.Log.d("FatTableRepository", "No unsynced rows found")
            } else {
                android.util.Log.d("FatTableRepository", "Found ${unsyncedRows.size} unsynced rows from offline operations")
                
                // Get current Firestore data
                val existingFirestoreRows = fetchFromFirestore()
                val existingKeys = existingFirestoreRows.map { "${it.from}-${it.to}-${it.price}" }.toSet()
                
                // Filter out rows that already exist in Firestore
                val trulyNewRows = unsyncedRows.filter { row ->
                    val key = "${row.from}-${row.to}-${row.price}"
                    !existingKeys.contains(key)
                }
                
                if (trulyNewRows.isNotEmpty()) {
                    android.util.Log.d("FatTableRepository", "Uploading ${trulyNewRows.size} truly new rows to Firestore")
                    
                    // Upload only the truly new rows
                    trulyNewRows.forEach { row ->
                        uploadToFirestore(row)
                    }
                    
                    // Mark only the uploaded rows as synced
                    markFatRowsAsSynced(trulyNewRows.map { it.id })
                    
                    android.util.Log.d("FatTableRepository", "Successfully uploaded ${trulyNewRows.size} new rows")
                } else {
                    android.util.Log.d("FatTableRepository", "All unsynced rows already exist in Firestore")
                    // Mark all unsynced rows as synced since they already exist
                    markFatRowsAsSynced(unsyncedRows.map { it.id })
                }
            }
            
            // Clean up any duplicates that might have been created
            cleanupFirestoreDuplicates()
            
            // Add a longer delay to ensure all operations are complete before restarting real-time sync
            kotlinx.coroutines.delay(1000)
            
            // Restart real-time sync if it was active before
            if (wasRealTimeActive) {
                startRealTimeSync()
                android.util.Log.d("FatTableRepository", "Restarted real-time sync after offline sync")
            }
            
            // Clear the offline sync flag and set completion timestamp
            isOfflineSyncInProgress = false
            lastOfflineSyncTime = System.currentTimeMillis()
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error in offline-to-online sync: ${e.message}")
            // Clear the flag even if there's an error
            isOfflineSyncInProgress = false
            lastOfflineSyncTime = System.currentTimeMillis()
            throw e
        }
    }
    
    /**
     * Sync offline deletions to Firestore
     */
    private suspend fun syncOfflineDeletions() = withContext(Dispatchers.IO) {
        try {
            val prefs = appContext.getSharedPreferences("fat_table_deletions", android.content.Context.MODE_PRIVATE)
            val allPrefs = prefs.all
            
            if (allPrefs.isEmpty()) {
                android.util.Log.d("FatTableRepository", "No offline deletions to sync")
                return@withContext
            }
            
            android.util.Log.d("FatTableRepository", "Found ${allPrefs.size} offline deletions to sync")
            
            val deletionsToProcess = mutableListOf<String>()
            
            for ((key, value) in allPrefs) {
                if (key.startsWith("deleted_fat_row_")) {
                    deletionsToProcess.add(key)
                }
            }
            
            for (deletionKey in deletionsToProcess) {
                try {
                    // Extract fat row info from the key
                    val parts = deletionKey.replace("deleted_fat_row_", "").split("_")
                    if (parts.size >= 3) {
                        val from = parts[0].toFloat()
                        val to = parts[1].toFloat()
                        val price = parts[2].toDouble()
                        
                        val deletedRow = FatRangeRow(
                            from = from,
                            to = to,
                            price = price,
                            isSynced = true
                        )
                        
                        // Delete from Firestore
                        deleteFromFirestore(deletedRow)
                        android.util.Log.d("FatTableRepository", "Synced offline deletion: ${from}-${to} = ₹${price}")
                        
                        // Remove from SharedPreferences
                        prefs.edit().remove(deletionKey).apply()
            }
        } catch (e: Exception) {
                    android.util.Log.e("FatTableRepository", "Error syncing deletion $deletionKey: ${e.message}")
                }
            }
            
            android.util.Log.d("FatTableRepository", "Completed syncing ${deletionsToProcess.size} offline deletions")
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error syncing offline deletions: ${e.message}")
        }
    }
    
    /**
     * Sync offline updates to Firestore
     */
    private suspend fun syncOfflineUpdates() = withContext(Dispatchers.IO) {
        try {
            val prefs = appContext.getSharedPreferences("fat_table_updates", android.content.Context.MODE_PRIVATE)
            val allPrefs = prefs.all
            
            if (allPrefs.isEmpty()) {
                android.util.Log.d("FatTableRepository", "No offline updates to sync")
                return@withContext
            }
            
            android.util.Log.d("FatTableRepository", "Found ${allPrefs.size} offline updates to sync")
            
            val updatesToProcess = mutableListOf<String>()
            
            for ((key, value) in allPrefs) {
                if (key.startsWith("updated_fat_row_")) {
                    updatesToProcess.add(key)
                }
            }
            
            for (updateKey in updatesToProcess) {
                try {
                    // Extract update data from the key and value
                    val oldRowKey = updateKey.replace("updated_fat_row_", "")
                    val updateData = prefs.getString(updateKey, "")
                    
                    if (!updateData.isNullOrEmpty()) {
                        val parts = updateData.split("_")
                        if (parts.size >= 4) {
                            val newFrom = parts[0].toFloat()
                            val newTo = parts[1].toFloat()
                            val newPrice = parts[2].toDouble()
                            val rowId = parts[3].toInt()
                            
                            // Create the old row from the key
                            val oldRowParts = oldRowKey.split("_")
                            if (oldRowParts.size >= 3) {
                                val oldFrom = oldRowParts[0].toFloat()
                                val oldTo = oldRowParts[1].toFloat()
                                val oldPrice = oldRowParts[2].toDouble()
                                
                                val oldRow = FatRangeRow(
                                    id = rowId,
                                    from = oldFrom,
                                    to = oldTo,
                                    price = oldPrice,
                                    isSynced = true
                                )
                                
                                val newRow = FatRangeRow(
                                    id = rowId,
                                    from = newFrom,
                                    to = newTo,
                                    price = newPrice,
                                    isSynced = false
                                )
                                
                                // Check if the row still exists in local storage
                                val currentLocalRows = getAllFatRows()
                                val currentRow = currentLocalRows.find { it.id == rowId }
                                
                                if (currentRow != null) {
                                    // Delete old entry from Firestore
                                    deleteFromFirestore(oldRow)
                                    
                                    // Upload new entry to Firestore
                                    uploadToFirestore(newRow)
                                    
                                    // Mark as synced in local storage
                                    markFatRowsAsSynced(listOf(rowId))
                                    
                                    android.util.Log.d("FatTableRepository", "Synced offline update: ${oldFrom}-${oldTo} -> ${newFrom}-${newTo}")
                                } else {
                                    android.util.Log.w("FatTableRepository", "Row with ID $rowId no longer exists, skipping update")
                                }
                                
                                // Remove from SharedPreferences
                                prefs.edit().remove(updateKey).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FatTableRepository", "Error syncing update $updateKey: ${e.message}")
                }
            }
            
            android.util.Log.d("FatTableRepository", "Completed syncing ${updatesToProcess.size} offline updates")
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error syncing offline updates: ${e.message}")
        }
    }

    /**
     * Get price for a given fat percentage
     */
    suspend fun getPriceForFat(fatPercentage: Double): Double = withContext(Dispatchers.IO) {
        try {
            val rows = getAllFatRows()
            val matchingRow = rows.find { fatPercentage >= it.from && fatPercentage <= it.to }
            matchingRow?.price ?: 0.0
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error getting price for fat: ${e.message}")
            0.0
        }
    }

    /**
     * Add fat row with validation and sync
     */
    suspend fun addFatRowWithSync(row: FatRangeRow, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate no overlap with existing ranges using FatTableUtils
            val existingRows = getAllFatRows()
            if (!com.example.doodhsethu.utils.FatTableUtils.validateFatRange(row, existingRows)) {
                android.util.Log.w("FatTableRepository", "Validation failed: Range ${row.from}-${row.to} overlaps with existing ranges")
                return@withContext false
            }
            
            // Save to local storage immediately with isSynced = false
            val newRow = row.copy(isSynced = false)
            insertFatRow(newRow)
            
            // Get the inserted row with the correct ID
            val insertedRow = getAllFatRows().find { 
                it.from == newRow.from && it.to == newRow.to && it.price == newRow.price && !it.isSynced 
            }
            
            // Sync to Firestore in background if online (non-blocking)
            if (isOnline && insertedRow != null) {
                // Launch background coroutine for Firestore sync
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        // Check if this entry already exists in Firestore
                        val existingFirestoreRows = fetchFromFirestore()
                        val existsInFirestore = existingFirestoreRows.any { 
                            it.from == insertedRow.from && it.to == insertedRow.to && it.price == insertedRow.price 
                        }
                        
                        if (!existsInFirestore) {
                            uploadToFirestore(insertedRow)
                            markFatRowsAsSynced(listOf(insertedRow.id))
                            android.util.Log.d("FatTableRepository", "Successfully synced new row to Firestore")
                        } else {
                            // Mark as synced since it already exists in Firestore
                            markFatRowsAsSynced(listOf(insertedRow.id))
                            android.util.Log.d("FatTableRepository", "Row already exists in Firestore, marked as synced")
            }
        } catch (e: Exception) {
                        android.util.Log.e("FatTableRepository", "Background sync failed: ${e.message}")
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error adding fat row: ${e.message}")
            false
        }
    }

    /**
     * Update fat row with validation and sync
     */
    suspend fun updateFatRowWithSync(row: FatRangeRow, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate no overlap with existing ranges (excluding current row)
            val existingRows = getAllFatRows()
            if (!com.example.doodhsethu.utils.FatTableUtils.validateFatRange(row, existingRows, row.id)) {
                android.util.Log.w("FatTableRepository", "Validation failed: Range ${row.from}-${row.to} overlaps with existing ranges")
                return@withContext false
            }
            
            // Get the old row before updating
            val oldRow = existingRows.find { it.id == row.id }
            
            if (oldRow == null) {
                android.util.Log.e("FatTableRepository", "Cannot update: Row with ID ${row.id} not found")
                return@withContext false
            }
            
            // Update local storage immediately with isSynced = false
            updateFatRow(row.copy(isSynced = false))
            
            // Handle offline updates by storing update info for later sync
            if (!isOnline) {
                // Store update info in SharedPreferences for later sync
                val updateKey = "updated_fat_row_${oldRow.from}_${oldRow.to}_${oldRow.price}"
                val updateData = "${row.from}_${row.to}_${row.price}_${row.id}"
                val prefs = appContext.getSharedPreferences("fat_table_updates", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString(updateKey, updateData).apply()
                android.util.Log.d("FatTableRepository", "Marked fat row for update sync: ${oldRow.from}-${oldRow.to} -> ${row.from}-${row.to}")
            } else {
                // Sync to Firestore in background if online (non-blocking)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        // Delete old entry from Firestore
                        deleteFromFirestore(oldRow)
                        
                        // Upload new entry to Firestore
                        uploadToFirestore(row)
                        
                        // Mark as synced in local storage
                        markFatRowsAsSynced(listOf(row.id))
                        android.util.Log.d("FatTableRepository", "Successfully updated row in Firestore")
                    } catch (e: Exception) {
                        android.util.Log.e("FatTableRepository", "Background sync failed: ${e.message}")
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error updating fat row: ${e.message}")
            false
        }
    }

    /**
     * Delete fat row with sync
     */
    suspend fun deleteFatRowWithSync(row: FatRangeRow, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete from local storage immediately
            deleteFatRow(row)
            
            // If offline, mark this deletion for later sync
            if (!isOnline) {
                // Store deletion info in SharedPreferences for later sync
                val deletionKey = "deleted_fat_row_${row.from}_${row.to}_${row.price}"
                val prefs = appContext.getSharedPreferences("fat_table_deletions", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong(deletionKey, System.currentTimeMillis()).apply()
                android.util.Log.d("FatTableRepository", "Marked fat row for deletion sync: ${row.from}-${row.to} = ₹${row.price}")
            } else {
                // Delete from Firestore in background if online (non-blocking)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        deleteFromFirestore(row)
                    } catch (e: Exception) {
                        android.util.Log.e("FatTableRepository", "Background deletion failed: ${e.message}")
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error deleting fat row: ${e.message}")
            false
        }
    }
    
    /**
     * Clean up duplicate fat table entries
     */
    suspend fun cleanupDuplicateEntries() = withContext(Dispatchers.IO) {
        try {
            // Clean up local duplicates
            val allRows = getAllFatRows()
            val uniqueRows = mutableListOf<FatRangeRow>()
            val seenRanges = mutableSetOf<String>()
            
            for (row in allRows) {
                val rangeKey = "${row.from}-${row.to}-${row.price}"
                if (!seenRanges.contains(rangeKey)) {
                    seenRanges.add(rangeKey)
                    uniqueRows.add(row)
                } else {
                    // Delete duplicate
                    deleteFatRow(row)
                    android.util.Log.d("FatTableRepository", "Deleted duplicate fat row: ${row.from}-${row.to} = ₹${row.price}")
                }
            }
            
            android.util.Log.d("FatTableRepository", "Local cleanup completed. Removed ${allRows.size - uniqueRows.size} duplicate entries")
            
            // Clean up Firestore duplicates
            cleanupFirestoreDuplicates()
            
            // Clean up any orphaned SharedPreferences entries
            cleanupOrphanedPreferences()
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error cleaning up duplicates: ${e.message}")
        }
    }
    
    /**
     * Clean up orphaned SharedPreferences entries
     */
    private suspend fun cleanupOrphanedPreferences() = withContext(Dispatchers.IO) {
        try {
            // Clean up orphaned deletion entries
            val deletionPrefs = appContext.getSharedPreferences("fat_table_deletions", android.content.Context.MODE_PRIVATE)
            val deletionKeys = deletionPrefs.all.keys.filter { it.startsWith("deleted_fat_row_") }
            
            for (key in deletionKeys) {
                try {
                    val parts = key.replace("deleted_fat_row_", "").split("_")
                    if (parts.size >= 3) {
                        val from = parts[0].toFloat()
                        val to = parts[1].toFloat()
                        val price = parts[2].toDouble()
                        
                        // Check if this entry still exists in local storage
                        val localRows = getAllFatRows()
                        val existsLocally = localRows.any { 
                            it.from == from && it.to == to && it.price == price 
                        }
                        
                        if (!existsLocally) {
                            // Entry doesn't exist locally, remove the deletion preference
                            deletionPrefs.edit().remove(key).apply()
                            android.util.Log.d("FatTableRepository", "Cleaned up orphaned deletion preference: $key")
                        }
                    }
        } catch (e: Exception) {
                    android.util.Log.e("FatTableRepository", "Error cleaning up deletion preference $key: ${e.message}")
                }
            }
            
            // Clean up orphaned update entries
            val updatePrefs = appContext.getSharedPreferences("fat_table_updates", android.content.Context.MODE_PRIVATE)
            val updateKeys = updatePrefs.all.keys.filter { it.startsWith("updated_fat_row_") }
            
            for (key in updateKeys) {
                try {
                    val updateData = updatePrefs.getString(key, "")
                    if (!updateData.isNullOrEmpty()) {
                        val parts = updateData.split("_")
                        if (parts.size >= 4) {
                            val rowId = parts[3].toInt()
                            
                            // Check if this entry still exists in local storage
                val localRows = getAllFatRows()
                            val existsLocally = localRows.any { it.id == rowId }
                            
                            if (!existsLocally) {
                                // Entry doesn't exist locally, remove the update preference
                                updatePrefs.edit().remove(key).apply()
                                android.util.Log.d("FatTableRepository", "Cleaned up orphaned update preference: $key")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FatTableRepository", "Error cleaning up update preference $key: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error cleaning up orphaned preferences: ${e.message}")
        }
    }
    
    /**
     * Clean up duplicate entries in Firestore
     */
    private suspend fun cleanupFirestoreDuplicates() = withContext(Dispatchers.IO) {
        try {
            val firestoreRows = fetchFromFirestore()
            val uniqueRows = mutableListOf<FatRangeRow>()
            val seenRanges = mutableSetOf<String>()
            val duplicatesToDelete = mutableListOf<FatRangeRow>()
            
            for (row in firestoreRows) {
                val rangeKey = "${row.from}-${row.to}-${row.price}"
                if (!seenRanges.contains(rangeKey)) {
                    seenRanges.add(rangeKey)
                    uniqueRows.add(row)
                } else {
                    // Mark for deletion
                    duplicatesToDelete.add(row)
                }
            }
            
            // Delete duplicates from Firestore
            duplicatesToDelete.forEach { row ->
                deleteFromFirestore(row)
                android.util.Log.d("FatTableRepository", "Deleted duplicate from Firestore: ${row.from}-${row.to} = ₹${row.price}")
            }
            
            android.util.Log.d("FatTableRepository", "Firestore cleanup completed. Removed ${duplicatesToDelete.size} duplicate entries")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error cleaning up Firestore duplicates: ${e.message}")
        }
    }
    
    /**
     * Force cleanup all duplicates (both local and Firestore)
     */
    suspend fun forceCleanupAllDuplicates() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Starting force cleanup of all duplicates")
            
            // Clean up local duplicates
            cleanupDuplicateEntries()
            
            // Force cleanup Firestore duplicates
            cleanupFirestoreDuplicates()
            
            // Fix precision issues in Firestore
            fixFirestorePrecision()
            
            android.util.Log.d("FatTableRepository", "Force cleanup completed")
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error during force cleanup: ${e.message}")
        }
    }
    
    /**
     * Fix precision issues in Firestore by replacing entries with proper rounded values
     */
    private suspend fun fixFirestorePrecision() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Fixing Firestore precision issues")
            
            val firestoreRows = fetchFromFirestore()
            val entriesToFix = mutableListOf<FatRangeRow>()
            
            for (row in firestoreRows) {
                // Check if the values need rounding (if they have more than 3 decimal places)
                val fromNeedsRounding = (row.from * 1000) % 1 != 0f
                val toNeedsRounding = (row.to * 1000) % 1 != 0f
                
                if (fromNeedsRounding || toNeedsRounding) {
                    entriesToFix.add(row)
                }
            }
            
            if (entriesToFix.isNotEmpty()) {
                android.util.Log.d("FatTableRepository", "Found ${entriesToFix.size} entries with precision issues")
                
                for (row in entriesToFix) {
                    try {
                        // Delete the old entry with wrong precision
                        deleteFromFirestore(row)
                        
                        // Create new entry with proper rounded values
                        val roundedRow = FatRangeRow(
                            from = (row.from * 1000).roundToInt() / 1000f,
                            to = (row.to * 1000).roundToInt() / 1000f,
                            price = row.price,
                            isSynced = true
                        )
                        
                        uploadToFirestore(roundedRow)
                        android.util.Log.d("FatTableRepository", "Fixed precision for entry: ${row.from}-${row.to} -> ${roundedRow.from}-${roundedRow.to}")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("FatTableRepository", "Error fixing precision for entry ${row.from}-${row.to}: ${e.message}")
                    }
                }
                
                android.util.Log.d("FatTableRepository", "Completed fixing ${entriesToFix.size} precision issues")
            } else {
                android.util.Log.d("FatTableRepository", "No precision issues found")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error fixing Firestore precision: ${e.message}")
        }
    }
    
    /**
     * Replace all FAT table rows with new data (for Excel import)
     */
    suspend fun replaceAllFatRows(newRows: List<FatRangeRow>) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FatTableRepository", "Replacing all FAT table rows with ${newRows.size} new entries")
            
            // Clear existing data
            fatTableDao.deleteAllFatRows()
            
            // Insert new data with appropriate sync status
            val rowsToInsert = newRows.map { row ->
                // Mark as synced if we're offline (will be synced later when online)
                row.copy(isSynced = false)
            }
            fatTableDao.insertFatRows(rowsToInsert)
            
            // Try to upload to Firestore (non-blocking)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Upload to Firestore
                    for (row in newRows) {
                        uploadToFirestore(row)
                    }
                    
                    // Mark as synced after successful upload
                    val insertedRows = getAllFatRows()
                    markFatRowsAsSynced(insertedRows.map { it.id })
                    
                    android.util.Log.d("FatTableRepository", "Successfully uploaded all rows to Firestore")
                } catch (e: Exception) {
                    android.util.Log.w("FatTableRepository", "Failed to upload to Firestore (offline?): ${e.message}")
                    // Keep rows as unsynced for later sync when online
                }
            }
            
            android.util.Log.d("FatTableRepository", "Successfully replaced all FAT table rows")
            
            // Notify callback
            onDataChangedCallback?.invoke()
            
        } catch (e: Exception) {
            android.util.Log.e("FatTableRepository", "Error replacing FAT table rows: ${e.message}")
            throw e
        }
    }
} 