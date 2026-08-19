package com.example.argus.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.Message
import com.example.argus.data.model.MessageStatus
import com.example.argus.data.repository.SmartContextItem
import com.example.argus.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun E2eeSecurityBanner(onVerifyClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = ObsidianCard.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable { onVerifyClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Messages are end-to-end encrypted. Tap to verify.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DateSeparatorPill(label: String = "TODAY") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = ObsidianCard.copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun DisappearingMessagesDialog(
    currentDuration: Int?,
    onDismiss: () -> Unit,
    onDurationSelected: (Int?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Text("Disappearing Messages", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Messages in this chat will be wiped automatically for both participants after the selected timer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                listOf(
                    "24 Hours (86400s)" to 86400,
                    "7 Days (604800s)" to 604800,
                    "90 Days (7776000s)" to 7776000,
                    "Off" to null
                ).forEach { (label, duration) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDurationSelected(duration)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentDuration == duration,
                            onClick = {
                                onDurationSelected(duration)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = EmeraldPrimary)
            }
        }
    )
}

@Composable
fun MediaViewerDialog(
    photoUrl: String?,
    onDismiss: () -> Unit,
    onSaveToVault: () -> Unit
) {
    if (photoUrl == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBlack,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "🔒 Hardware Decrypted Media", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(text = "AES-256-GCM Secure Image Payload", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveToVault,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save to Vault", color = TextOnEmerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
fun MessageBubbleWhatsAppStyle(
    message: Message,
    isMe: Boolean,
    smartContextItems: List<SmartContextItem>,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    onSmartActionClick: ((SmartContextItem) -> Unit)? = null
) {
    var showReactionMenu by remember { mutableStateOf(false) }
    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showReactionMenu = true },
                    onDoubleTap = { onReaction("❤️") }
                )
            },
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            // Floating Reaction Picker Bar
            if (showReactionMenu) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("👍", "❤️", "🔥", "😂", "😮", "🔒").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clickable {
                                    onReaction(emoji)
                                    showReactionMenu = false
                                }
                                .padding(4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onReply()
                            showReactionMenu = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // WhatsApp / Telegram Bubble Card
            Surface(
                color = if (isMe) BubbleOutgoing else BubbleIncoming,
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isMe) 14.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 14.dp
                ),
                modifier = Modifier.widthIn(min = 90.dp, max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    // Media Photo Bubble
                    if (message.mediaType == "PHOTO") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))))
                                .clickable { onPhotoClick(message.mediaUri ?: "") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Encrypted Media (Tap to view)", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Voice Note Player with Waveforms
                    if (message.mediaType == "AUDIO") {
                        VoiceNoteWaveformPlayer(isMe = isMe)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Message Text
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )

                    // Smart Context Chips
                    if (smartContextItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        smartContextItems.forEach { item ->
                            Surface(
                                color = EmeraldPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        onSmartActionClick?.invoke(item)
                                    }
                            ) {
                                Text(
                                    text = item.actionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Bottom Row: Timestamp + Read Receipts Tick
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (tickIcon, tickColor) = when (message.status) {
                                MessageStatus.READ -> Icons.Default.DoneAll to ReadTickBlue
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll to ReadTickDelivered
                                MessageStatus.SENT -> Icons.Default.Done to ReadTickDelivered
                                MessageStatus.SENDING -> Icons.Default.Schedule to ReadTickDelivered
                                MessageStatus.FAILED -> Icons.Default.Error to Color.Red
                                else -> Icons.Default.Done to ReadTickDelivered
                            }
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = "Status",
                                tint = tickColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceNoteWaveformPlayer(isMe: Boolean, totalDurationSec: Int = 24) {
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf("1.0x") }
    var playProgress by remember { mutableFloatStateOf(0.0f) }

    // Active playback progress animation loop
    LaunchedEffect(isPlaying, playbackSpeed) {
        if (isPlaying) {
            val speedFactor = when (playbackSpeed) {
                "1.5x" -> 1.5f
                "2.0x" -> 2.0f
                else -> 1.0f
            }
            val stepTimeMs = 100L
            val stepIncrement = (stepTimeMs / 1000f) / totalDurationSec * speedFactor

            while (isPlaying && playProgress < 1.0f) {
                kotlinx.coroutines.delay(stepTimeMs)
                playProgress = (playProgress + stepIncrement).coerceAtMost(1.0f)
                if (playProgress >= 1.0f) {
                    isPlaying = false
                    playProgress = 0.0f
                }
            }
        }
    }

    val currentElapsedSec = (playProgress * totalDurationSec).toInt()
    val formattedDuration = String.format(Locale.getDefault(), "%d:%02d / %d:%02d", currentElapsedSec / 60, currentElapsedSec % 60, totalDurationSec / 60, totalDurationSec % 60)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (!isPlaying && playProgress >= 1.0f) playProgress = 0.0f
                    isPlaying = !isPlaying
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isMe) EmeraldPrimary else CyanAccent)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = TextOnEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 20 Interactive Waveform Bars (Clickable Scrubbing)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val heights = listOf(6, 12, 18, 24, 14, 8, 20, 26, 18, 10, 16, 22, 14, 8, 18, 24, 12, 6, 14, 20)
                heights.forEachIndexed { index, h ->
                    val isFilled = (index.toFloat() / heights.size) <= playProgress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h.dp)
                            .clickable {
                                playProgress = (index + 1).toFloat() / heights.size
                            }
                            .background(
                                color = if (isFilled) (if (isMe) EmeraldLight else CyanGlow) else ObsidianBorder,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Speed Toggle Button
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable {
                    playbackSpeed = when (playbackSpeed) {
                        "1.0x" -> "1.5x"
                        "1.5x" -> "2.0x"
                        else -> "1.0x"
                    }
                }
            ) {
                Text(
                    text = playbackSpeed,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Real-time duration readout
        Text(
            text = formattedDuration,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 44.dp, top = 2.dp)
        )
    }
}

@Composable
fun AttachmentGridItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
