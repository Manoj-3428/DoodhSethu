package com.example.doodhsethu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.PoppinsFont

@Composable
fun OfflineSyncIndicator(
    isOnline: Boolean,
    pendingUploads: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Network Status Indicator
        if (!isOnline) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi_off),
                        contentDescription = "Offline",
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Working offline. Changes will sync when connection is restored.",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = Color(0xFF856404)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Pending Uploads Indicator
        if (pendingUploads > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1ECF1)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0C5460)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$pendingUploads photo(s) uploading...",
                        fontFamily = PoppinsFont,
                        fontSize = 12.sp,
                        color = Color(0xFF0C5460)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
} 