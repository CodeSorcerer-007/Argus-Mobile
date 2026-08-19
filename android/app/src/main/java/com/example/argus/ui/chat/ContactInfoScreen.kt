package com.example.argus.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.argus.data.model.Contact
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun ContactInfoScreen(
    contact: Contact,
    onBackClick: () -> Unit,
    onStartMessageClick: () -> Unit,
    onStartAudioCallClick: () -> Unit,
    onStartVideoCallClick: () -> Unit,
    onVerifySafetyNumberClick: () -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isChatLocked by remember { mutableStateOf(false) }
    var disappearingTimer by remember { mutableStateOf("24 Hours") }
    var showDisappearingDialog by remember { mutableStateOf(false) }

    if (showDisappearingDialog) {
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Disappearing Messages", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "For more privacy, new messages in this chat will disappear for both participants after the selected duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    listOf("24 Hours", "7 Days", "90 Days", "Off").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    disappearingTimer = option
                                    showDisappearingDialog = false
                                    Toast.makeText(context, "Timer updated to $option", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = disappearingTimer == option,
                                onClick = {
                                    disappearingTimer = option
                                    showDisappearingDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisappearingDialog = false }) {
                    Text("Done", color = EmeraldPrimary)
                }
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Contact Info",
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
            // Header Profile Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianSurface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArgusAvatar(name = contact.displayName, size = 88.dp, isOnline = contact.isOnline)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                if (contact.username != null) {
                    Text(
                        text = "@${contact.username}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldLight,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Action Bar (Message, Audio, Video, Search)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ContactActionPill(icon = Icons.Default.Chat, label = "Message", onClick = onStartMessageClick)
                    ContactActionPill(icon = Icons.Default.Phone, label = "Audio", onClick = onStartAudioCallClick)
                    ContactActionPill(icon = Icons.Default.Videocam, label = "Video", onClick = onStartVideoCallClick)
                    ContactActionPill(icon = Icons.Default.Search, label = "Search", onClick = {
                        Toast.makeText(context, "Search chat history", Toast.LENGTH_SHORT).show()
                    })
                }
            }

            // About / Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLight,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🔒 Available on Argus • E2EE Verified",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (contact.isOnline) "Active now" else "Last seen recently",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // Media, Links & Docs Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .clickable {
                        Toast.makeText(context, "Shared media gallery (0 items)", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PermMedia, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Media, Links, and Docs", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(text = "Photos, voice notes & files", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }

            // Encryption & Safety Number Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .clickable { onVerifySafetyNumberClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.EnhancedEncryption, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Encryption", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (contact.isVerified) "Verified 60-digit Safety Numbers" else "Tap to verify Safety Numbers",
                            color = if (contact.isVerified) EmeraldLight else TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }

            // Disappearing Messages & Chat Lock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDisappearingDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timelapse, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Disappearing Messages", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(text = disappearingTimer, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Chat Lock", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(text = "Lock & hide this chat on this device", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = isChatLocked,
                        onCheckedChange = {
                            isChatLocked = it
                            Toast.makeText(context, if (it) "Chat Locked" else "Chat Unlocked", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextOnEmerald,
                            checkedTrackColor = EmeraldPrimary,
                            uncheckedTrackColor = ObsidianCard
                        )
                    )
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = null, tint = TextSecondary)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Mute Notifications", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(text = "Silence alerts for this chat", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = isMuted,
                        onCheckedChange = { isMuted = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextOnEmerald,
                            checkedTrackColor = EmeraldPrimary,
                            uncheckedTrackColor = ObsidianCard
                        )
                    )
                }
            }

            // Block & Report Danger Zone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { Toast.makeText(context, "Blocked ${contact.displayName}", Toast.LENGTH_SHORT).show() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF6B6B))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Block ${contact.displayName}", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { Toast.makeText(context, "Reported ${contact.displayName}", Toast.LENGTH_SHORT).show() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ThumbDown, contentDescription = null, tint = Color(0xFFFF6B6B))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Report Contact", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ContactActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ObsidianCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}
