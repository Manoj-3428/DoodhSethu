package com.example.doodhsethu.utils

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PendingPhoto(
    val localUri: Uri,
    val farmerId: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PhotoUploadManager(private val context: Context) {
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _pendingPhotos = MutableStateFlow<Map<String, PendingPhoto>>(emptyMap())
    val pendingPhotos: StateFlow<Map<String, PendingPhoto>> = _pendingPhotos.asStateFlow()
    
    private val _uploadStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val uploadStatus: StateFlow<Map<String, String>> = _uploadStatus.asStateFlow()
    
    private val pendingUploads = ConcurrentHashMap<String, PendingPhoto>()
    
    fun addPendingPhoto(farmerId: String, localUri: Uri) {
        val pendingPhoto = PendingPhoto(localUri, farmerId)
        pendingUploads[farmerId] = pendingPhoto
        _pendingPhotos.value = pendingUploads.toMap()
        _uploadStatus.value = _uploadStatus.value.toMutableMap().apply {
            put(farmerId, "Pending upload")
        }
    }
    
    fun removePendingPhoto(farmerId: String) {
        pendingUploads.remove(farmerId)
        _pendingPhotos.value = pendingUploads.toMap()
        _uploadStatus.value = _uploadStatus.value.toMutableMap().apply {
            remove(farmerId)
        }
    }
    
    suspend fun uploadPendingPhotos() {
        val photosToUpload = pendingUploads.toMap()
        
        photosToUpload.forEach { (farmerId, pendingPhoto) ->
            try {
                _uploadStatus.value = _uploadStatus.value.toMutableMap().apply {
                    put(farmerId, "Uploading...")
                }
                
                val storageRef = storage.reference.child("farmer_photos/$farmerId.jpg")
                val uploadTask = storageRef.putFile(pendingPhoto.localUri)
                
                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await()
                
                // Update Firestore with photo URL
                firestore.collection("farmers").document(farmerId)
                    .update("photoUrl", downloadUrl.toString())
                    .await()
                
                // Remove from pending
                removePendingPhoto(farmerId)
                
                _uploadStatus.value = _uploadStatus.value.toMutableMap().apply {
                    put(farmerId, "Uploaded successfully")
                }
                
            } catch (e: Exception) {
                _uploadStatus.value = _uploadStatus.value.toMutableMap().apply {
                    put(farmerId, "Upload failed: ${e.message}")
                }
            }
        }
    }
    
    fun getLocalPhotoPath(farmerId: String): String? {
        val pendingPhoto = pendingUploads[farmerId]
        return pendingPhoto?.localUri?.toString()
    }
    
    fun hasPendingUploads(): Boolean = pendingUploads.isNotEmpty()
} 