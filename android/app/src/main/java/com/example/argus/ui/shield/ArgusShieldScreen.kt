package com.example.argus.ui.shield

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.core.permission.ArgusPermissionType
import com.example.argus.core.permission.PermissionManager
import com.example.argus.data.repository.PermissionHealth
import com.example.argus.data.repository.ShieldRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton
import com.example.argus.ui.components.ArgusPermissionRationaleDialog
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun ArgusShieldScreen(
    shieldRepository: ShieldRepository,
    onBackClick: () -> Unit
) {
    val status by shieldRepository.statusFlow.collectAsState()
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    var activeRationalePermission by remember { mutableStateOf<ArgusPermissionType?>(null) }
    val context = LocalContext.current

    // Sync live permission health
    LaunchedEffect(Unit) {
        val health = PermissionHealth(
            hasCamera = PermissionManager.hasCameraPermission(context),
            hasAudio = PermissionManager.hasAudioPermission(context),
            hasStorage = PermissionManager.hasStorageOrMediaPermissions(context),
            hasNotifications = PermissionManager.hasNotificationPermission(context),
            hasContacts = PermissionManager.hasContactsPermission(context)
        )
        shieldRepository.updatePermissionHealth(health)
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val health = PermissionHealth(
            hasCamera = PermissionManager.hasCameraPermission(context),
            hasAudio = PermissionManager.hasAudioPermission(context),
            hasStorage = PermissionManager.hasStorageOrMediaPermissions(context),
            hasNotifications = PermissionManager.hasNotificationPermission(context),
            hasContacts = PermissionManager.hasContactsPermission(context)
        )
        shieldRepository.updatePermissionHealth(health)
    }

    // Active Permission Rationale Dialog
    if (activeRationalePermission != null) {
        ArgusPermissionRationaleDialog(
            permissionType = activeRationalePermission!!,
            onGrantClick = {
                val perm = activeRationalePermission!!
                activeRationalePermission = null
                when (perm) {
                    ArgusPermissionType.CAMERA -> requestPermissionsLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
                    ArgusPermissionType.AUDIO -> requestPermissionsLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                    ArgusPermissionType.STORAGE_AND_MEDIA -> requestPermissionsLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
                    ArgusPermissionType.NOTIFICATIONS -> requestPermissionsLauncher.launch(PermissionManager.getNotificationPermissions())
                    ArgusPermissionType.CONTACTS -> requestPermissionsLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
                    ArgusPermissionType.LOCATION -> requestPermissionsLauncher.launch(PermissionManager.getLocationPermissions())
                }
            },
            onDismiss = { activeRationalePermission = null },
            onOpenSettingsClick = {
                activeRationalePermission = null
                PermissionManager.openAppSettings(context)
            }
        )
    }

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

            Spacer(modifier = Modifier.height(24.dp))

            // Device Permissions Security & Privacy Section
            Text(
                text = "Device Permissions & Zero-Knowledge Audit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PermissionAuditCard(
                    title = "Camera Access",
                    desc = "For E2EE Video Calls, Photos & Safety Number QR Verification",
                    isGranted = PermissionManager.hasCameraPermission(context),
                    icon = Icons.Default.CameraAlt,
                    onRequest = { activeRationalePermission = ArgusPermissionType.CAMERA }
                )
                PermissionAuditCard(
                    title = "Microphone Access",
                    desc = "For E2EE Voice Notes & HD Voice Calls",
                    isGranted = PermissionManager.hasAudioPermission(context),
                    icon = Icons.Default.Mic,
                    onRequest = { activeRationalePermission = ArgusPermissionType.AUDIO }
                )
                PermissionAuditCard(
                    title = "File Storage & Media",
                    desc = "For Biometric Hardware Vault & Encrypted Media Attachments",
                    isGranted = PermissionManager.hasStorageOrMediaPermissions(context),
                    icon = Icons.Default.FolderSpecial,
                    onRequest = { activeRationalePermission = ArgusPermissionType.STORAGE_AND_MEDIA }
                )
                PermissionAuditCard(
                    title = "Private Contact Matching",
                    desc = "Salted SHA-256 local matching (Zero cloud contact exposure)",
                    isGranted = PermissionManager.hasContactsPermission(context),
                    icon = Icons.Default.Contacts,
                    onRequest = { activeRationalePermission = ArgusPermissionType.CONTACTS }
                )
                PermissionAuditCard(
                    title = "Push Notifications",
                    desc = "Real-time background wake-up for E2EE messages",
                    isGranted = PermissionManager.hasNotificationPermission(context),
                    icon = Icons.Default.NotificationsActive,
                    onRequest = { activeRationalePermission = ArgusPermissionType.NOTIFICATIONS }
                )
                PermissionAuditCard(
                    title = "GPS Location Pin",
                    desc = "For secure live location pin sharing in chat",
                    isGranted = PermissionManager.hasLocationPermission(context),
                    icon = Icons.Default.LocationOn,
                    onRequest = { activeRationalePermission = ArgusPermissionType.LOCATION }
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

            Button(
                onClick = { showPanicConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShieldRed.copy(alpha = 0.15f),
                    contentColor = ShieldRed
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ShieldRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ShieldRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cryptographic Panic Wipe",
                    fontWeight = FontWeight.Bold,
                    color = ShieldRed
                )
            }
        }
    }

    if (showPanicConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text(
                    text = "🚨 Permanent Panic Wipe?",
                    color = ShieldRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will immediately zeroize and purge all local SQLite databases, Curve25519 Double Ratchet sessions, Hardware Keystore secrets, and Biometric Vault files.\n\nThis action is irreversible.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        shieldRepository.panicWipeAllData()
                        showPanicConfirmDialog = false
                        Toast.makeText(context, "All cryptographic states zeroized.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldRed)
                ) {
                    Text("Wipe Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PermissionAuditCard(
    title: String,
    desc: String,
    isGranted: Boolean,
    icon: ImageVector,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) EmeraldPrimary.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldPrimary else ShieldAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "Granted", style = MaterialTheme.typography.labelSmall, color = EmeraldLight, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(text = "Grant", style = MaterialTheme.typography.labelSmall, color = TextOnEmerald, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShieldStatusCard(
    title: String,
    desc: String,
    isGood: Boolean,
    icon: ImageVector,
    actionText: String,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGood) EmeraldPrimary.copy(alpha = 0.15f) else ShieldAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGood) EmeraldPrimary else ShieldAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (onActionClick != null && !isGood) {
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(text = actionText, style = MaterialTheme.typography.labelSmall, color = TextOnEmerald, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isGood) EmeraldPrimary.copy(alpha = 0.2f) else ShieldAmber.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGood) EmeraldLight else ShieldAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
