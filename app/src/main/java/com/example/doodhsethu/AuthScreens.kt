package com.example.doodhsethu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doodhsethu.components.AuthForm
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.theme.AntonFont

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    isLogin: Boolean = true,
    onToggleMode: () -> Unit = {},
    onLogin: (String, String) -> Unit = { _, _ -> },
    onRegister: (String, String, String) -> Unit = { _, _, _ -> },
    onAuthSuccess: () -> Unit = {}
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(LightBlue, PrimaryBlue)
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // Background decorative elements from Figma
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Large background circle (top left)
            Image(
                painter = painterResource(id = R.drawable.bg_circle_large),
                contentDescription = "Background decoration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(400.dp)
                    .offset(x = (-150).dp, y = (-100).dp)
                    .alpha(0.4f)
            )
            
            // Small background circle (top right)
            Image(
                painter = painterResource(id = R.drawable.bg_circle_small),
                contentDescription = "Background decoration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(300.dp)
                    .offset(x = 1600.dp, y = (-50).dp)
                    .alpha(0.3f)
            )
        }
        
        // App name at top
        Text(
            text = "DoodhSethu",
            fontSize = 32.sp,
            fontFamily = AntonFont,
            fontWeight = FontWeight.Normal,
            color = TextBlue,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        
        // Centered Auth form card - positioned below app name
        Card(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.75f)
                .align(Alignment.Center)
                .offset(y = 50.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = BackgroundBlue
            )
        ) {
            AuthForm(
                isLogin = isLogin,
                onToggleMode = onToggleMode,
                onLogin = onLogin,
                onRegister = onRegister,
                onAuthSuccess = onAuthSuccess
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun AuthScreenPreview() {
    DoodhSethuTheme {
        AuthScreen(
            isLogin = true,
            onToggleMode = {},
            onLogin = { userId, password -> 
                // Preview only - no action needed
            },
            onRegister = { userId, name, password -> 
                // Preview only - no action needed
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun AuthScreenRegisterPreview() {
    DoodhSethuTheme {
        AuthScreen(
            isLogin = false,
            onToggleMode = {},
            onLogin = { userId, password -> 
                // Preview only - no action needed
            },
            onRegister = { userId, name, password -> 
                // Preview only - no action needed
            }
        )
    }
} 