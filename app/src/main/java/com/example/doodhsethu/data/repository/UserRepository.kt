package com.example.doodhsethu.data.repository

import android.content.Context
import com.example.doodhsethu.data.models.DatabaseManager
import com.example.doodhsethu.data.models.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val db = DatabaseManager.getDatabase(context)
    private val userDao = db.userDao()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun authenticateUser(userId: String, password: String): User? = withContext(Dispatchers.IO) {
        userDao.authenticateUser(userId, password)
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)
    }

    suspend fun insertUser(user: User) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }

    suspend fun insertUsers(users: List<User>) = withContext(Dispatchers.IO) {
        userDao.insertUsers(users)
    }

    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        userDao.updateUser(user)
    }

    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        userDao.deleteUser(user)
    }

    suspend fun deleteAllUsers() = withContext(Dispatchers.IO) {
        userDao.deleteAllUsers()
    }

    suspend fun getUnsyncedUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getUnsyncedUsers()
    }

    suspend fun markUsersAsSynced(userIds: List<String>) = withContext(Dispatchers.IO) {
        userDao.markUsersAsSynced(userIds)
    }

    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAllUsers()
    }

    // Firestore sync methods
    suspend fun authenticateUserFromFirestore(userId: String, password: String, isOnline: Boolean): User? = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext null
        try {
            val query = firestore.collection("users")
                .whereEqualTo("userId", userId)
                .whereEqualTo("password", password)
                .get()
                .await()
            
            if (!query.isEmpty) {
                val userDoc = query.documents.first()
                val user = userDoc.toObject(User::class.java)
                user?.let {
                    // Save to Room for offline access
                    insertUser(it.copy(isSynced = true))
                }
                user
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun registerUserToFirestore(user: User, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext false
        try {
            firestore.collection("users").document(user.userId).set(user).await()
            markUsersAsSynced(listOf(user.userId))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncLocalWithFirestore(isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (!isOnline) return@withContext
        // 1. Upload unsynced users
        val unsynced = getUnsyncedUsers()
        if (unsynced.isNotEmpty()) {
            unsynced.forEach { user ->
                try {
                    firestore.collection("users").document(user.userId).set(user.copy(isSynced = true)).await()
                } catch (_: Exception) {}
            }
            markUsersAsSynced(unsynced.map { it.userId })
        }
        // 2. Download from Firestore and update Room
        val snapshot = firestore.collection("users").get().await()
        val remoteUsers = snapshot.documents.mapNotNull { doc ->
            doc.toObject(User::class.java)?.copy(isSynced = true)
        }
        deleteAllUsers()
        insertUsers(remoteUsers)
    }

    // Sync with Firestore (for AutoSyncManager)
    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("UserRepository", "Starting Firestore sync...")
            syncLocalWithFirestore(true)
            android.util.Log.d("UserRepository", "Firestore sync completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error during Firestore sync: ${e.message}")
        }
    }

    // Load from Firestore (for AutoSyncManager)
    suspend fun loadFromFirestore() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("UserRepository", "Loading users from Firestore...")
            val snapshot = firestore.collection("users").get().await()
            val remoteUsers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.copy(isSynced = true)
            }
            if (remoteUsers.isNotEmpty()) {
                deleteAllUsers()
                insertUsers(remoteUsers)
            }
            android.util.Log.d("UserRepository", "Users loaded from Firestore successfully")
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error loading users from Firestore: ${e.message}")
        }
    }
} 