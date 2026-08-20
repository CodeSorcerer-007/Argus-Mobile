package com.example.argus.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.core.biometric.BiometricPromptManager
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton

@Composable
fun AppLockScreen(
    biometricManager: BiometricPromptManager?,
    onUnlocked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    var authError by remember { mutableStateOf<String?>(null) }

    fun triggerPrompt() {
        authError = null
        if (biometricManager != null && biometricManager.canAuthenticate()) {
            biometricManager.showBiometricPrompt(
                title = "Argus Locked",
                subtitle = "Touch the fingerprint sensor or enter device PIN",
                description = "Hardware cryptographic keys are sealed until authenticated",
                onSuccess = {
                    onUnlocked()
                },
                onError = { err ->
                    authError = err
                },
                onFailed = {
                    authError = "Biometric authentication not recognized. Try again."
                }
            )
        } else {
            // Fallback for emulator or no hardware biometric
            onUnlocked()
        }
    }

    // Auto-trigger biometric prompt on app load
    LaunchedEffect(Unit) {
        triggerPrompt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, ObsidianSurface, ObsidianBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Shield Logo
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.argus_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Argus Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Hardware cryptographic session sealed.\nTouch fingerprint sensor to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Glowing Animated Fingerprint Sensor
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                EmeraldPrimary.copy(alpha = glowAlpha * 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        2.dp,
                        EmeraldPrimary.copy(alpha = glowAlpha),
                        CircleShape
                    )
                    .clickable { triggerPrompt() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint Sensor",
                    tint = EmeraldLight,
                    modifier = Modifier.size(68.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (authError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF3B1818), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF8B2525), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = authError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD1D1),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            ArgusButton(
                text = "Unlock with Biometrics / PIN",
                onClick = { triggerPrompt() },
                icon = Icons.Default.Fingerprint,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = EmeraldLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Zero-Knowledge Hardware Keystore Vault",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
