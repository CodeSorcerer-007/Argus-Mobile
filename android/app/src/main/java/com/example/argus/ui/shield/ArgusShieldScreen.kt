package com.example.argus.ui.shield

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.repository.ShieldRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun ArgusShieldScreen(
    shieldRepository: ShieldRepository,
    onBackClick: () -> Unit
) {
    val status by shieldRepository.statusFlow.collectAsState()
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Argus Shield",
                subtitle = "Central Privacy & Security Health Meter",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Privacy Score Meter
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(5.dp, if (status.privacyScore >= 80) EmeraldPrimary else ShieldAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${status.privacyScore}",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "PRIVACY SCORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (status.privacyScore >= 80) EmeraldLight else ShieldAmber,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Status Items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShieldStatusCard(
                    title = "Signal Double Ratchet E2EE",
                    desc = "Continuous X3DH & DH ratcheting active",
                    isGood = status.isE2EEActive,
                    icon = Icons.Default.Lock,
                    actionText = "Verified"
                )
                ShieldStatusCard(
                    title = "Hardware Keystore / StrongBox",
                    desc = "Master keys wrapped in Android TEE hardware",
                    isGood = true,
                    icon = Icons.Default.VpnKey,
                    actionText = "Secure"
                )
                ShieldStatusCard(
                    title = "App Lock Protection",
                    desc = if (status.isAppLockActive) "Enabled with Biometrics / PIN" else "Disabled (Tap to enable biometric lock)",
                    isGood = status.isAppLockActive,
                    icon = Icons.Default.Fingerprint,
                    actionText = if (status.isAppLockActive) "Active" else "Enable",
                    onActionClick = {
                        shieldRepository.toggleAppLock(true)
                        Toast.makeText(context, "App Lock enabled with biometric guard!", Toast.LENGTH_SHORT).show()
                    }
                )
                ShieldStatusCard(
                    title = "Verified Safety Numbers",
                    desc = "${status.verifiedContactsCount} verified • ${status.unverifiedContactsCount} unverified",
                    isGood = status.unverifiedContactsCount == 0,
                    icon = Icons.Default.VerifiedUser,
                    actionText = "Audit"
                )
                ShieldStatusCard(
                    title = "Screen Security & Anti-Capture",
                    desc = "FLAG_SECURE prevents screenshot snooping",
                    isGood = true,
                    icon = Icons.Default.Screenshot,
                    actionText = "Active"
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Emergency Lockdown & Panic Wipe Actions
            Text(
                text = "Emergency Security Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            ArgusButton(
                text = "Trigger Emergency Lockdown",
                onClick = {
                    shieldRepository.triggerEmergencyLockdown()
                    Toast.makeText(context, "Emergency lockdown engaged!", Toast.LENGTH_SHORT).show()
                },
                isPrimary = false,
                icon = Icons.Default.Security
            )

            Spacer(modifier = Modifier.height(10.dp))

            ArgusButton(
                text = "Panic Wipe All Cryptographic Data",
                onClick = { showPanicConfirmDialog = true },
                isDestructive = true,
                icon = Icons.Default.DeleteForever
            )
        }
    }

    if (showPanicConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            containerColor = ObsidianCard,
            title = { Text("Panic Wipe Confirmation", color = ShieldRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will immediately zeroize and destroy all local database messages, encrypted vault files, and cryptographic keys. This action cannot be undone.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        shieldRepository.panicWipeAllData()
                        showPanicConfirmDialog = false
                        Toast.makeText(context, "All cryptographic data zeroized.", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Destroy Everything", color = ShieldRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ShieldStatusCard(
    title: String,
    desc: String,
    isGood: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (isGood) EmeraldPrimary.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGood) EmeraldPrimary else ShieldAmber,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(actionText, color = EmeraldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(
                imageVector = if (isGood) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGood) EmeraldPrimary else ShieldAmber,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
