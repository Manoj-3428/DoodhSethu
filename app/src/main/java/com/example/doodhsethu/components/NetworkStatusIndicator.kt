package com.example.doodhsethu.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.*

@Composable
fun NetworkStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "network_status")
    
    // Pulse animation for the status dot
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOnline) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Fade animation for status text
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    // Slide animation for the container
    val slideOffset by animateFloatAsState(
        targetValue = if (isOnline) 0f else -10f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "slide"
    )
    
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(500, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(500)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(500, easing = EaseInCubic)
        ) + fadeOut(animationSpec = tween(500))
    ) {
        Card(
            modifier = modifier
                .offset(x = (slideOffset * 1).dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) Color(0xFFE8F5E8) else Color(0xFFFFEBEE)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Animated status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                )
                
                // Status text with fade animation
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.alpha(alpha)
                )
                
                // Network icon
                Icon(
                    painter = painterResource(
                        id = if (isOnline) R.drawable.ic_wifi_off else R.drawable.ic_wifi_off
                    ),
                    contentDescription = if (isOnline) "Online" else "Offline",
                    modifier = Modifier.size(14.dp),
                    tint = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun CompactNetworkStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "compact_network_status")
    
    // Pulse animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOnline) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            animationSpec = tween(300, easing = EaseOutBack)
        ) + fadeIn(animationSpec = tween(300)),
        exit = scaleOut(
            animationSpec = tween(300, easing = EaseInBack)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = modifier
                .size(12.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
        )
    }
} 