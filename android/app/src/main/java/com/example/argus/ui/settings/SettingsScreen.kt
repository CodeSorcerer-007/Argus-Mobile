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
import kotlinx.coroutines.launch

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

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editDisplayName by remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "") }
    var editUsername by remember(currentUser) { mutableStateOf(currentUser?.username ?: "") }
    var profileSaveError by remember { mutableStateOf<String?>(null) }
    var isProfileSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(preferences.getServerUrl()) }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProfileSaving) showEditProfileDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Edit Profile", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Set your Display Name and @username so friends can easily find and message you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase() },
                        label = { Text("Username (@)") },
                        singleLine = true,
                        prefix = { Text("@", color = EmeraldLight) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (profileSaveError != null) {
                        Text(
                            text = profileSaveError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProfileSaving = true
                        profileSaveError = null
                        coroutineScope.launch {
                            val cleanUser = if (editUsername.trim().isEmpty()) null else editUsername.trim()
                            val cleanName = if (editDisplayName.trim().isEmpty()) "Argus User" else editDisplayName.trim()
                            val res = authRepository.updateProfile(cleanName, cleanUser)
                            isProfileSaving = false
                            if (res.isSuccess) {
                                showEditProfileDialog = false
                            } else {
                                profileSaveError = res.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    enabled = !isProfileSaving
                ) {
                    Text(if (isProfileSaving) "Saving..." else "Save Changes", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }, enabled = !isProfileSaving) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Backend Server Configuration", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure the active Argus E2EE Gateway endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server Base URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        preferences.setServerUrl(serverUrlInput)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Save", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

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
            // Profile Card (Clickable to Edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .clickable {
                        editDisplayName = currentUser?.displayName ?: ""
                        editUsername = currentUser?.username ?: ""
                        profileSaveError = null
                        showEditProfileDialog = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArgusAvatar(name = currentUser?.displayName ?: "Argus User", size = 56.dp)
                    Spacer(modifier = Modifier.width(14.dp))
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
                            text = if (currentUser?.username != null) "@${currentUser?.username}" else "Tap to set @username",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentUser?.username != null) EmeraldLight else CyanAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
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
                title = "Biometric Vault Access",
                subtitle = "Use Fingerprint/Face Unlock for hardware-encrypted vault",
                checked = isBiometric,
                onCheckedChange = {
                    isBiometric = it
                    preferences.setBiometricEnabled(it)
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.DoneAll,
                title = "Read Receipts",
                subtitle = "Let contacts see when you've read their messages",
                checked = isReadReceipts,
                onCheckedChange = {
                    isReadReceipts = it
                    preferences.setReadReceiptsEnabled(it)
                }
            )
            SettingsToggleRow(
                icon = Icons.Default.Keyboard,
                title = "Typing Indicators",
                subtitle = "Display typing status when composing messages",
                checked = isTypingIndicators,
                onCheckedChange = {
                    isTypingIndicators = it
                    preferences.setTypingIndicatorsEnabled(it)
                }
            )

            // Storage & Network
            SettingsSectionHeader("Storage & Network")
            SettingsToggleRow(
                icon = Icons.Default.DataUsage,
                title = "Data Saver",
                subtitle = "Download media attachments only on Wi-Fi",
                checked = isDataSaver,
                onCheckedChange = {
                    isDataSaver = it
                    preferences.setDataSaverEnabled(it)
                }
            )
            SettingsClickableRow(
                icon = Icons.Default.Dns,
                title = "Backend Server URL",
                subtitle = preferences.getServerUrl(),
                onClick = {
                    serverUrlInput = preferences.getServerUrl()
                    showServerDialog = true
                }
            )

            // Account & Sign Out
            SettingsSectionHeader("Account")
            Button(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B1818),
                    contentColor = Color(0xFFFF6B6B)
                )
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = "Log Out")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Log Out & Wipe Local Keys", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = EmeraldLight,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
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
            .background(ObsidianSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextOnEmerald,
                checkedTrackColor = EmeraldPrimary,
                uncheckedTrackColor = ObsidianCard
            )
        )
    }
}

@Composable
private fun SettingsClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}
