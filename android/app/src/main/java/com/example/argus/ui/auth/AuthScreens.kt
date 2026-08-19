package com.example.argus.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.theme.*
import com.example.argus.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                    icon = Icons.Default.FolderSpecial,
                    title = "Biometric Argus Vault",
                    desc = "Hardware-encrypted local storage for sensitive files & notes."
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
fun PhoneAuthScreen(
    currentServerUrl: String,
    onSaveServerUrl: (String) -> Unit,
    onRequestOtp: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedCountryCode by remember { mutableStateOf("+91") }
    var rawNumber by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

    val fullPhoneNumber = remember(selectedCountryCode, rawNumber) {
        val clean = rawNumber.filter { it.isDigit() }
        "$selectedCountryCode$clean"
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Backend Server URL", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Set the HTTP(S) endpoint of your Argus backend server (e.g. Render Cloud URL or local IP):",
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sign In / Register",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(
                    onClick = { showServerDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Settings",
                        tint = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Argus will deliver a 6-digit SMS verification code to establish your zero-knowledge device keys.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quick Country Code",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            CountryCodeChips(
                selectedCode = selectedCountryCode,
                onSelectCode = { selectedCountryCode = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = rawNumber,
                onValueChange = { input ->
                    val trimmed = input.trim()
                    if (trimmed.startsWith("+91")) {
                        selectedCountryCode = "+91"
                        rawNumber = trimmed.removePrefix("+91").filter { it.isDigit() }
                    } else if (trimmed.startsWith("+1")) {
                        selectedCountryCode = "+1"
                        rawNumber = trimmed.removePrefix("+1").filter { it.isDigit() }
                    } else if (trimmed.startsWith("+44")) {
                        selectedCountryCode = "+44"
                        rawNumber = trimmed.removePrefix("+44").filter { it.isDigit() }
                    } else {
                        rawNumber = trimmed.filter { it.isDigit() }
                    }
                },
                label = { Text("Phone Number") },
                prefix = {
                    Text(
                        text = "$selectedCountryCode ",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (fullPhoneNumber.length >= 8 && !isLoading) {
                            keyboardController?.hide()
                            onRequestOtp(fullPhoneNumber)
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = EmeraldPrimary)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Phone numbers are hashed via SHA-256 before discovery.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        ArgusButton(
            text = if (isLoading) "Sending SMS Code..." else "Send SMS Verification Code",
            onClick = {
                keyboardController?.hide()
                onRequestOtp(fullPhoneNumber)
            },
            enabled = fullPhoneNumber.length >= 8 && !isLoading,
            icon = Icons.Default.Sms
        )
    }
}

@Composable
fun OtpVerifyScreen(
    phoneNumber: String,
    initialCode: String = "",
    onVerifyOtp: (String) -> Unit,
    onResendClick: () -> Unit,
    onBackClick: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var code by remember { mutableStateOf(initialCode) }
    var showBanner by remember { mutableStateOf(initialCode.isNotBlank()) }
    var countdown by remember { mutableStateOf(45) }

    LaunchedEffect(initialCode) {
        if (initialCode.isNotBlank()) {
            code = initialCode
            showBanner = true
        }
    }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMS Verification",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Simulated / Dev SMS Heads-up Banner if dev code exists
                if (showBanner && initialCode.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IncomingSmsNotificationBanner(
                        code = initialCode,
                        phoneNumber = phoneNumber,
                        onAutoFillClick = {
                            code = initialCode
                            keyboardController?.hide()
                            onVerifyOtp(initialCode)
                        },
                        onCopyClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Argus OTP", initialCode))
                            Toast.makeText(context, "Code copied: $initialCode", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showBanner = false }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real SMS Delivery Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Check your phone's Messages app",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "We've dispatched a 6-digit verification code via SMS text message to $phoneNumber.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENTER 6-DIGIT CODE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldLight,
                        letterSpacing = 1.sp
                    )

                    if (initialCode.isNotBlank()) {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                code = initialCode
                                keyboardController?.hide()
                                onVerifyOtp(initialCode)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auto-fill: $initialCode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive 6 individual OTP Pin Boxes
                ArgusSixDigitPinInput(
                    value = code,
                    onValueChange = { code = it },
                    onComplete = { completedCode ->
                        if (!isLoading) {
                            keyboardController?.hide()
                            onVerifyOtp(completedCode)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Resend & Status Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (countdown > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resend SMS in ${countdown}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                countdown = 45
                                onResendClick()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Resend SMS Code", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            ArgusButton(
                text = if (isLoading) "Verifying Identity..." else "Verify & Continue",
                onClick = {
                    keyboardController?.hide()
                    onVerifyOtp(code)
                },
                enabled = code.length == 6 && !isLoading,
                icon = Icons.Default.Check
            )
        }
    }
}
