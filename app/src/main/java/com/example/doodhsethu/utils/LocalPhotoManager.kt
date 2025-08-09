package com.example.doodhsethu.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class LocalPhotoManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalPhotoManager"
        private const val PROFILE_PHOTOS_DIR = "profile_photos"
        private const val FARMER_PHOTOS_DIR = "farmer_photos"
    }
    
    /**
     * Save profile picture to local storage
     */
    suspend fun saveProfilePhoto(userId: String, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving profile photo for user: $userId")
            
            // Create directory if it doesn't exist
            val photosDir = File(context.filesDir, PROFILE_PHOTOS_DIR)
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }
            
            // Create file for this user
            val photoFile = File(photosDir, "${userId}_profile.jpg")
            
            // Copy image from URI to local file
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val outputStream = FileOutputStream(photoFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            val localPath = photoFile.absolutePath
            Log.d(TAG, "Profile photo saved locally: $localPath")
            return@withContext localPath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile photo: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Save farmer photo to local storage
     */
    suspend fun saveFarmerPhoto(farmerId: String, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving farmer photo for farmer: $farmerId")
            
            // Create directory if it doesn't exist
            val photosDir = File(context.filesDir, FARMER_PHOTOS_DIR)
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }
            
            // Create file for this farmer
            val photoFile = File(photosDir, "${farmerId}_photo.jpg")
            
            // Copy image from URI to local file
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val outputStream = FileOutputStream(photoFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            val localPath = photoFile.absolutePath
            Log.d(TAG, "Farmer photo saved locally: $localPath")
            return@withContext localPath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving farmer photo: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Get profile photo URI from local storage
     */
    fun getProfilePhotoUri(userId: String): Uri? {
        try {
            val photosDir = File(context.filesDir, PROFILE_PHOTOS_DIR)
            val photoFile = File(photosDir, "${userId}_profile.jpg")
            
            return if (photoFile.exists()) {
                Log.d(TAG, "Profile photo found locally: ${photoFile.absolutePath}")
                Uri.fromFile(photoFile)
            } else {
                Log.d(TAG, "Profile photo not found for user: $userId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile photo: ${e.message}")
            return null
        }
    }
    
    /**
     * Get farmer photo URI from local storage
     */
    fun getFarmerPhotoUri(farmerId: String): Uri? {
        try {
            val photosDir = File(context.filesDir, FARMER_PHOTOS_DIR)
            val photoFile = File(photosDir, "${farmerId}_photo.jpg")
            
            return if (photoFile.exists()) {
                Log.d(TAG, "Farmer photo found locally: ${photoFile.absolutePath}")
                Uri.fromFile(photoFile)
            } else {
                Log.d(TAG, "Farmer photo not found for farmer: $farmerId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting farmer photo: ${e.message}")
            return null
        }
    }
    
    /**
     * Delete profile photo from local storage
     */
    fun deleteProfilePhoto(userId: String): Boolean {
        try {
            val photosDir = File(context.filesDir, PROFILE_PHOTOS_DIR)
            val photoFile = File(photosDir, "${userId}_profile.jpg")
            
            return if (photoFile.exists()) {
                val deleted = photoFile.delete()
                Log.d(TAG, "Profile photo deleted: $deleted")
                deleted
            } else {
                Log.d(TAG, "Profile photo not found for deletion: $userId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile photo: ${e.message}")
            return false
        }
    }
    
    /**
     * Delete farmer photo from local storage
     */
    fun deleteFarmerPhoto(farmerId: String): Boolean {
        try {
            val photosDir = File(context.filesDir, FARMER_PHOTOS_DIR)
            val photoFile = File(photosDir, "${farmerId}_photo.jpg")
            
            return if (photoFile.exists()) {
                val deleted = photoFile.delete()
                Log.d(TAG, "Farmer photo deleted: $deleted")
                deleted
            } else {
                Log.d(TAG, "Farmer photo not found for deletion: $farmerId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting farmer photo: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if profile photo exists locally
     */
    fun hasProfilePhoto(userId: String): Boolean {
        val photosDir = File(context.filesDir, PROFILE_PHOTOS_DIR)
        val photoFile = File(photosDir, "${userId}_profile.jpg")
        return photoFile.exists()
    }
    
    /**
     * Check if farmer photo exists locally
     */
    fun hasFarmerPhoto(farmerId: String): Boolean {
        val photosDir = File(context.filesDir, FARMER_PHOTOS_DIR)
        val photoFile = File(photosDir, "${farmerId}_photo.jpg")
        return photoFile.exists()
    }
    
    /**
     * Get total size of stored photos
     */
    fun getStoredPhotosSize(): Long {
        try {
            val profileDir = File(context.filesDir, PROFILE_PHOTOS_DIR)
            val farmerDir = File(context.filesDir, FARMER_PHOTOS_DIR)
            
            var totalSize = 0L
            
            if (profileDir.exists()) {
                profileDir.listFiles()?.forEach { file ->
                    totalSize += file.length()
                }
            }
            
            if (farmerDir.exists()) {
                farmerDir.listFiles()?.forEach { file ->
                    totalSize += file.length()
                }
            }
            
            return totalSize
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stored photos size: ${e.message}")
            return 0L
        }
    }
} 