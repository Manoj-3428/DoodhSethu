package com.example.doodhsethu.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doodhsethu.R
import com.example.doodhsethu.ui.theme.*
import com.example.doodhsethu.ui.theme.PoppinsFont
import com.example.doodhsethu.ui.viewmodels.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import android.util.Log

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthForm(
    isLogin: Boolean,
    onToggleMode: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onAuthSuccess: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    // Collect ViewModel states
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()
    val generatedUserId by authViewModel.generatedUserId.collectAsState()
    
    // Handle toast messages and navigation
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Log.d(context.packageName, "AuthForm error: $message")
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            authViewModel.clearMessages()
        }
    }
    
    // Only call navigation callback once per success
    var handledSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(successMessage) {
        if (successMessage != null && !handledSuccess) {
            handledSuccess = true
            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
            authViewModel.clearMessages()
            // Navigate to dashboard after successful authentication
            onAuthSuccess()
            // Reset form state after navigation
            username = ""
            userId = ""
            password = ""
        } else if (successMessage == null) {
            handledSuccess = false
        }
    }
    
    // Get generated user ID for signup
    LaunchedEffect(isLogin) {
        if (!isLogin) {
            android.util.Log.d("AuthForm", "Switching to register mode, generating new User ID")
            authViewModel.generateUserId(context)
        }
    }
    val actualUserId = generatedUserId
    val showRegisterForm = isLogin || actualUserId.isNotBlank()
    
    // Always clear messages and reset handledSuccess when AuthForm is first shown
    LaunchedEffect(Unit) {
        authViewModel.clearMessages()
        handledSuccess = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Toggle buttons with moving underline
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Register",
                    fontSize = 18.sp,
                    fontFamily = PoppinsFont,
                    fontWeight = if (!isLogin) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isLogin) PrimaryBlue else PrimaryBlue.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable { if (isLogin) onToggleMode() }
                        .padding(horizontal = 30.dp, vertical = 16.dp)
                )
                
                Spacer(modifier = Modifier.width(40.dp))
                
                Text(
                    text = "Login",
                    fontSize = 18.sp,
                    fontFamily = PoppinsFont,
                    fontWeight = if (isLogin) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLogin) PrimaryBlue else PrimaryBlue.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable { if (!isLogin) onToggleMode() }
                        .padding(horizontal = 30.dp, vertical = 16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        if (!showRegisterForm) {
            // Show loading indicator while generating user ID
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else {
        // Form fields with animations
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
                // User ID field (move above Name for register)
                AnimatedContent(
                    targetState = isLogin,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(500),
                            initialOffsetY = { -it }
                        ) + fadeIn(animationSpec = tween(500)) with slideOutVertically(
                            animationSpec = tween(300),
                            targetOffsetY = { -it }
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) { isLoginState ->
                    if (isLoginState) {
                        // Login: Editable User ID field
                        AuthTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = "User ID",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_person),
                                    contentDescription = "User ID",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            placeholder = "Enter your User ID",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )
                    } else {
                        // Register: Non-editable generated User ID
                        Column {
                            Text(
                                text = "User ID",
                                fontSize = 14.sp,
                                fontFamily = PoppinsFont,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = actualUserId,
                                onValueChange = { }, // Non-editable
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_person),
                                        contentDescription = "User ID",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = White,
                                    unfocusedContainerColor = White.copy(alpha = 0.7f),
                                    cursorColor = PrimaryBlue,
                                    focusedTextColor = PrimaryBlue,
                                    unfocusedTextColor = PrimaryBlue.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                enabled = false,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = PoppinsFont,
                                    color = PrimaryBlue
                                )
                            )
                        }
                    }
                }
                // Username field (only for register, now below User ID)
            AnimatedContent(
                targetState = !isLogin,
                transitionSpec = {
                    slideInVertically(
                        animationSpec = tween(500),
                        initialOffsetY = { -it }
                    ) + fadeIn(animationSpec = tween(500)) with slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { -it }
                    ) + fadeOut(animationSpec = tween(300))
                }
            ) { showUsername ->
                if (showUsername) {
                    AuthTextField(
                        value = username,
                        onValueChange = { username = it },
                            label = "Full Name",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = "User",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                            placeholder = "Enter your full name"
                    )
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
            
            // Password field
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lock),
                        contentDescription = "Password",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                id = if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_eye_slash
                            ),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = "••••••••••",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                        imeAction = if (!isLogin) ImeAction.Done else ImeAction.Done
                )
            )
        }
        
        // Extra spacing for register page
        if (!isLogin) {
            Spacer(modifier = Modifier.height(30.dp))
        }
        
        // Spacer to push button to bottom of card
        Spacer(modifier = Modifier.weight(1.5f))
        
        // Animated action button at bottom
        AnimatedContent(
            targetState = isLogin,
            transitionSpec = {
                slideInVertically(
                    animationSpec = tween(600, delayMillis = 300),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(600, delayMillis = 300)) with slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { loginState ->
            Button(
                onClick = {
                    if (loginState) {
                            Log.d(context.packageName, "Login button pressed with userId=$userId")
                            // Login validation
                            if (userId.isBlank()) {
                                Toast.makeText(context, "Please enter User ID", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.length < 4) {
                                Toast.makeText(context, "Password must be at least 4 digits", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            authViewModel.login(userId, password, context)
                    } else {
                            Log.d(context.packageName, "Register button pressed with userId=$actualUserId")
                            // Register validation
                            if (username.isBlank()) {
                                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.length < 4) {
                                Toast.makeText(context, "Password must be at least 4 digits", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (actualUserId.isBlank()) {
                                Toast.makeText(context, "Please wait for User ID generation", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onRegister(actualUserId, username, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(25.dp)),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                ),
                    enabled = !isLoading // Prevent repeated clicks while loading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (loginState) "Login" else "Register",
                        fontSize = 16.sp,
                        fontFamily = PoppinsFont,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        }
    }
} 