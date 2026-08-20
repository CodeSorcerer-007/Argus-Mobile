package com.example.argus.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.theme.*
import com.example.argus.ui.components.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthTab {
    SIGN_IN,
    SIGN_UP
}

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, ObsidianSurface, ObsidianBlack)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Logo & Hero
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(EmeraldPrimary.copy(alpha = 0.25f), Color.Transparent)))
                        .border(2.dp, EmeraldPrimary.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.argus_logo),
                        contentDescription = "Argus Logo",
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Argus",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Private communication, without compromise.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = EmeraldLight
                )
            }

            // Feature Highlights
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianCard, RoundedCornerShape(20.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureRow(
                    icon = Icons.Default.Lock,
                    title = "Signal Double Ratchet E2EE",
                    desc = "Zero-knowledge encryption for all messages and calls."
                )
                FeatureRow(
                    icon = Icons.Default.VpnKey,
                    title = "Hardware Keystore Storage",
                    desc = "Cryptographic keys never leave your device."
                )
                FeatureRow(
                    icon = Icons.Default.AccountCircle,
                    title = "Zero Carrier Dependency",
                    desc = "Sign in directly with your unique @username and password."
                )
            }

            // Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArgusButton(
                    text = "Get Started",
                    onClick = onGetStartedClick,
                    isPrimary = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "By continuing you agree to the Argus Zero-Knowledge Security Terms.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(EmeraldPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
fun ArgusAuthScreen(
    currentServerUrl: String,
    onSaveServerUrl: (String) -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String, displayName: String) -> Unit,
    onCheckUsername: suspend (String) -> Boolean,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var activeTab by remember { mutableStateOf(AuthTab.SIGN_IN) }
    var showServerDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

    // Sign In form fields
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // Sign Up form fields
    var registerDisplayName by remember { mutableStateOf("") }
    var registerUsername by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerConfirmPassword by remember { mutableStateOf("") }
    var registerPasswordVisible by remember { mutableStateOf(false) }
    var registerConfirmPasswordVisible by remember { mutableStateOf(false) }

    // Username availability state
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameCheckJob by remember { mutableStateOf<Job?>(null) }

    fun triggerUsernameCheck(name: String) {
        val clean = name.trim().lowercase()
        usernameCheckJob?.cancel()
        if (clean.length < 3) {
            isUsernameAvailable = null
            isCheckingUsername = false
            return
        }
        isCheckingUsername = true
        usernameCheckJob = coroutineScope.launch {
            delay(350) // debounce
            val available = onCheckUsername(clean)
            isUsernameAvailable = available
            isCheckingUsername = false
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Backend Server Gateway", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Configure the endpoint of your Argus backend server (e.g. Render Cloud URL or local IP):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { tempServerUrl = "http://10.0.2.2:8080" },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Emulator", fontSize = 12.sp, color = EmeraldLight)
                        }
                        TextButton(
                            onClick = { tempServerUrl = "http://localhost:8080" },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Localhost", fontSize = 12.sp, color = EmeraldLight)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveServerUrl(tempServerUrl)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.argus_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Argus",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = { showServerDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Settings",
                        tint = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Segment Control (Instagram Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeTab == AuthTab.SIGN_IN) EmeraldPrimary else Color.Transparent)
                        .clickable { activeTab = AuthTab.SIGN_IN }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        color = if (activeTab == AuthTab.SIGN_IN) ObsidianBlack else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeTab == AuthTab.SIGN_UP) EmeraldPrimary else Color.Transparent)
                        .clickable { activeTab = AuthTab.SIGN_UP }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account",
                        color = if (activeTab == AuthTab.SIGN_UP) ObsidianBlack else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Banner
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3B1818), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF8B2525), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD1D1)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Animated Tab Body
            AnimatedContent(
                targetState = activeTab,
                label = "AuthTabAnimation"
            ) { tab ->
                when (tab) {
                    AuthTab.SIGN_IN -> {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Welcome back",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enter your User ID and password to access your encrypted chats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            // Username
                            OutlinedTextField(
                                value = loginUsername,
                                onValueChange = { loginUsername = it.replace(" ", "") },
                                label = { Text("User ID / Username") },
                                placeholder = { Text("e.g. alex_hunter") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AlternateEmail,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = authTextFieldColors()
                            )

                            // Password
                            OutlinedTextField(
                                value = loginPassword,
                                onValueChange = { loginPassword = it },
                                label = { Text("Password") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                        Icon(
                                            imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Password Visibility",
                                            tint = TextMuted
                                        )
                                    }
                                },
                                visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (loginUsername.isNotBlank() && loginPassword.isNotBlank() && !isLoading) {
                                            keyboardController?.hide()
                                            onLogin(loginUsername, loginPassword)
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = authTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            ArgusButton(
                                text = if (isLoading) "Signing In..." else "Log In",
                                onClick = {
                                    keyboardController?.hide()
                                    onLogin(loginUsername, loginPassword)
                                },
                                enabled = loginUsername.trim().length >= 3 && loginPassword.isNotBlank() && !isLoading,
                                icon = Icons.Default.Login
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Don't have an account?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                TextButton(onClick = { activeTab = AuthTab.SIGN_UP }) {
                                    Text(
                                        text = "Create Account",
                                        color = EmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    AuthTab.SIGN_UP -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Create Your Account",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Choose a unique @username. Your end-to-end encryption keys will be generated locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            // Display Name
                            OutlinedTextField(
                                value = registerDisplayName,
                                onValueChange = { registerDisplayName = it },
                                label = { Text("Display Name / Full Name") },
                                placeholder = { Text("e.g. Alex Hunter") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = authTextFieldColors()
                            )

                            // Username with live check
                            Column {
                                OutlinedTextField(
                                    value = registerUsername,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isLetterOrDigit() || it == '.' || it == '_' }.lowercase()
                                        registerUsername = clean
                                        triggerUsernameCheck(clean)
                                    },
                                    label = { Text("User ID / Username") },
                                    placeholder = { Text("e.g. alex_hunter") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AlternateEmail,
                                            contentDescription = null,
                                            tint = EmeraldPrimary
                                        )
                                    },
                                    trailingIcon = {
                                        if (isCheckingUsername) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = EmeraldPrimary
                                            )
                                        } else if (isUsernameAvailable == true && registerUsername.length >= 3) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Available",
                                                tint = EmeraldPrimary
                                            )
                                        } else if (isUsernameAvailable == false && registerUsername.length >= 3) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "Taken",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = authTextFieldColors()
                                )

                                if (registerUsername.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (registerUsername.length < 3) {
                                        Text(
                                            text = "Username must be at least 3 characters",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    } else if (isUsernameAvailable == true) {
                                        Text(
                                            text = "@$registerUsername is available!",
                                            fontSize = 11.sp,
                                            color = EmeraldLight,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else if (isUsernameAvailable == false) {
                                        Text(
                                            text = "@$registerUsername is already taken. Try another.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFF6B6B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Password
                            Column {
                                OutlinedTextField(
                                    value = registerPassword,
                                    onValueChange = { registerPassword = it },
                                    label = { Text("Password (min 6 characters)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = EmeraldPrimary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { registerPasswordVisible = !registerPasswordVisible }) {
                                            Icon(
                                                imageVector = if (registerPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Password Visibility",
                                                tint = TextMuted
                                            )
                                        }
                                    },
                                    visualTransformation = if (registerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = authTextFieldColors()
                                )

                                if (registerPassword.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    PasswordStrengthIndicator(password = registerPassword)
                                }
                            }

                            // Confirm Password
                            OutlinedTextField(
                                value = registerConfirmPassword,
                                onValueChange = { registerConfirmPassword = it },
                                label = { Text("Confirm Password") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LockReset,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { registerConfirmPasswordVisible = !registerConfirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (registerConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Confirm Password Visibility",
                                            tint = TextMuted
                                        )
                                    }
                                },
                                visualTransformation = if (registerConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val canSubmit = registerUsername.trim().length >= 3 &&
                                                registerDisplayName.trim().isNotBlank() &&
                                                registerPassword.length >= 6 &&
                                                registerPassword == registerConfirmPassword &&
                                                isUsernameAvailable != false &&
                                                !isLoading
                                        if (canSubmit) {
                                            keyboardController?.hide()
                                            onRegister(registerUsername, registerPassword, registerDisplayName)
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = authTextFieldColors()
                            )

                            if (registerConfirmPassword.isNotBlank() && registerPassword != registerConfirmPassword) {
                                Text(
                                    text = "Passwords do not match",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF6B6B)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Security note
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = EmeraldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Keys are generated in Android Keystore on this device.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            val isFormValid = registerUsername.trim().length >= 3 &&
                                    registerDisplayName.trim().isNotBlank() &&
                                    registerPassword.length >= 6 &&
                                    registerPassword == registerConfirmPassword &&
                                    isUsernameAvailable != false &&
                                    !isLoading

                            ArgusButton(
                                text = if (isLoading) "Creating Account..." else "Create Account & Sign In",
                                onClick = {
                                    keyboardController?.hide()
                                    onRegister(registerUsername, registerPassword, registerDisplayName)
                                },
                                enabled = isFormValid,
                                icon = Icons.Default.PersonAdd
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Already have an account?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                TextButton(onClick = { activeTab = AuthTab.SIGN_IN }) {
                                    Text(
                                        text = "Sign In",
                                        color = EmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(password: String) {
    val length = password.length
    val hasLetters = password.any { it.isLetter() }
    val hasDigits = password.any { it.isDigit() }
    val hasSymbols = password.any { !it.isLetterOrDigit() }

    val score = (if (length >= 8) 1 else 0) + (if (hasLetters && hasDigits) 1 else 0) + (if (hasSymbols) 1 else 0)
    val (color, label) = when {
        length < 6 -> Color(0xFFFF5252) to "Too short (min 6)"
        score == 0 -> Color(0xFFFF9800) to "Weak"
        score == 1 -> Color(0xFFFFC107) to "Moderate"
        score >= 2 -> EmeraldPrimary to "Strong"
        else -> EmeraldPrimary to "Strong"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (index <= score && length >= 6) color else ObsidianBorder)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldPrimary,
    unfocusedBorderColor = ObsidianBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
    focusedLabelColor = EmeraldLight,
    unfocusedLabelColor = TextSecondary,
    focusedContainerColor = ObsidianSurface,
    unfocusedContainerColor = ObsidianSurface
)
