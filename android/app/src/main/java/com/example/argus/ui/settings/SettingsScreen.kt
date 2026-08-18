package com.example.argus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.argus.data.local.ArgusPreferences
import com.example.argus.data.repository.AuthRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun SettingsScreen(
    preferences: ArgusPreferences,
    authRepository: AuthRepository,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val currentUser by authRepository.currentUser.collectAsState()
    var isAppLock by remember { mutableStateOf(preferences.isAppLockEnabled()) }
    var isBiometric by remember { mutableStateOf(preferences.isBiometricEnabled()) }
    var isReadReceipts by remember { mutableStateOf(preferences.isReadReceiptsEnabled()) }
    var isTypingIndicators by remember { mutableStateOf(preferences.isTypingIndicatorsEnabled()) }
    var isDataSaver by remember { mutableStateOf(preferences.isDataSaverEnabled()) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Settings",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArgusAvatar(name = currentUser?.displayName ?: "Argus User", size = 60.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = currentUser?.displayName ?: "Argus User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = currentUser?.phoneNumber ?: "+1 555 000 0000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Username: @${currentUser?.username ?: "not set"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight
                    )
                }
            }

            // Security & Privacy Group
            SettingsSectionHeader("Privacy & Security")
            SettingsToggleRow(
                icon = Icons.Default.Lock,
                title = "App Lock (PIN / Biometrics)",
                subtitle = "Require authentication when opening Argus",
                checked = isAppLock,
                onCheckedChange = {
                    isAppLock = it
                    preferences.setAppLockEnabled(it)
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Vault Gate",
                subtitle = "Hardware Keystore biometrics",
                checked = isBiometric,
                onCheckedChange = {
                    isBiometric = it
                    preferences.setBiometricEnabled(it)
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.DoneAll,
                title = "Read Receipts",
                subtitle = "Send & receive read acknowledgements",
                checked = isReadReceipts,
                onCheckedChange = {
                    isReadReceipts = it
                    preferences.setReadReceiptsEnabled(it)
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.Keyboard,
                title = "Typing Indicators",
                subtitle = "Let contacts know when you are typing",
                checked = isTypingIndicators,
                onCheckedChange = {
                    isTypingIndicators = it
                    preferences.setTypingIndicatorsEnabled(it)
                }
            )

            // Storage & Network Group
            SettingsSectionHeader("Storage & Network")
            var showServerUrlDialog by remember { mutableStateOf(false) }
            var currentServerUrl by remember { mutableStateOf(preferences.getServerUrl()) }
            var tempServerUrl by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

            if (showServerUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showServerUrlDialog = false },
                    containerColor = ObsidianCard,
                    title = { Text("Backend Server URL", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                "Configure the endpoint for Argus E2EE Gateway (Render Cloud URL, Local IP, or Emulator):",
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
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                preferences.setServerUrl(tempServerUrl)
                                currentServerUrl = preferences.getServerUrl()
                                showServerUrlDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Save", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showServerUrlDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCard)
                    .clickable { showServerUrlDialog = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Backend Server URL", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = currentServerUrl, style = MaterialTheme.typography.labelSmall, color = EmeraldLight)
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
            }

            SettingsToggleRow(
                icon = Icons.Default.DataSaverOn,
                title = "Travel / Low-Data Mode",
                subtitle = "Aggressive media compression on metered connections",
                checked = isDataSaver,
                onCheckedChange = {
                    isDataSaver = it
                    preferences.setDataSaverEnabled(it)
                }
            )

            // About & Logout
            SettingsSectionHeader("Account & Device")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCard)
                    .clickable { onLogoutClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = null, tint = ShieldRed)
                Spacer(modifier = Modifier.width(14.dp))
                Text(text = "Log Out & Revoke Device Keys", color = ShieldRed, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Argus v1.0.0 (Production Build)\nDouble Ratchet Signal Protocol • Android Keystore Backed",
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = TextMuted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = EmeraldPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextOnEmerald,
                checkedTrackColor = EmeraldPrimary,
                uncheckedTrackColor = ObsidianSurface
            )
        )
    }
}
