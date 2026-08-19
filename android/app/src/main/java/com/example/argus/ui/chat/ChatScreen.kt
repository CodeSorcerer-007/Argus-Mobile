package com.example.argus.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.Message
import com.example.argus.data.model.MessageStatus
import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.data.repository.SmartContextItem
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusTopBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: Conversation,
    messages: List<Message>,
    onBackClick: () -> Unit,
    onSendMessage: (String, String?, String?, Long) -> Unit,
    onVoiceCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onVerifySecurityClick: () -> Unit,
    onReactionClick: (Message, String) -> Unit,
    aiRepository: AiAssistantRepository
) {
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var selectedDisappearingDuration by remember { mutableStateOf<Int?>(conversation.disappearingDurationSec) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = conversation.title,
                subtitle = if (selectedDisappearingDuration != null) "🔒 E2EE • Disappearing in ${selectedDisappearingDuration}s" else "🔒 E2EE • Online",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onVoiceCallClick) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Voice Call", tint = EmeraldPrimary)
                    }
                    IconButton(onClick = onVideoCallClick) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call", tint = CyanAccent)
                    }
                    IconButton(onClick = onVerifySecurityClick) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = "Safety Code", tint = EmeraldPrimary)
                    }
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(ObsidianCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Disappearing Messages", color = TextPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                showDisappearingDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = CyanAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text("Verify Safety Numbers", color = TextPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                onVerifySecurityClick()
                            },
                            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = EmeraldPrimary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Lock Chat (Biometric)", color = TextPrimary) },
                            onClick = { showOptionsMenu = false },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ShieldAmber) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Reply quote banner
                AnimatedVisibility(visible = replyingToMessage != null) {
                    if (replyingToMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianSurfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(EmeraldPrimary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Replying to message", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                                Text(
                                    text = replyingToMessage!!.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { replyingToMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Reply", tint = TextMuted)
                            }
                        }
                    }
                }

                // Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach", tint = TextSecondary)
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Encrypted message...", color = TextMuted) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText.trim(), null, null, 0L)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(EmeraldPrimary, CircleShape)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TextOnEmerald)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isRecordingVoice = !isRecordingVoice
                                if (!isRecordingVoice) {
                                    onSendMessage("🎙️ Voice Message (0:14)", "voice_sample.m4a", "audio/m4a", 64000L)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (isRecordingVoice) ShieldRed else ObsidianCard, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = if (isRecordingVoice) TextPrimary else EmeraldPrimary
                            )
                        }
                    }
                }

                // Attachment Drawer
                AnimatedVisibility(visible = showAttachmentMenu) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AttachmentOption(Icons.Default.Image, "Gallery", EmeraldPrimary) {
                            showAttachmentMenu = false
                            onSendMessage("📷 Photo", "photo.jpg", "image/jpeg", 2500000L)
                        }
                        AttachmentOption(Icons.Default.CameraAlt, "Camera", CyanAccent) {
                            showAttachmentMenu = false
                            onSendMessage("📷 Camera Capture", "capture.jpg", "image/jpeg", 1800000L)
                        }
                        AttachmentOption(Icons.Default.Description, "Document", ShieldAmber) {
                            showAttachmentMenu = false
                            onSendMessage("📄 Security_Whitepaper.pdf", "doc.pdf", "application/pdf", 5242880L)
                        }
                        AttachmentOption(Icons.Default.LocationOn, "Location", ShieldGreen) {
                            showAttachmentMenu = false
                            onSendMessage("📍 Location: 37.7749° N, 122.4194° W", null, null, 0L)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SecurityNoticeHeader(conversationTitle = conversation.title)
            }

            items(messages) { message ->
                MessageBubble(
                    message = message,
                    onReply = { replyingToMessage = message },
                    onReaction = { emoji -> onReactionClick(message, emoji) },
                    aiRepository = aiRepository
                )
            }
        }
    }

    if (showDisappearingDialog) {
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            containerColor = ObsidianCard,
            title = { Text("Disappearing Messages", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New messages in this chat will self-destruct after the timer expires.", color = TextSecondary)
                    listOf(null to "Off", 30 to "30 seconds", 300 to "5 minutes", 86400 to "24 hours", 604800 to "7 days").forEach { (sec, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedDisappearingDuration = sec
                                    showDisappearingDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDisappearingDuration == sec,
                                onClick = {
                                    selectedDisappearingDuration = sec
                                    showDisappearingDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, color = TextPrimary)
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
}

@Composable
private fun SecurityNoticeHeader(conversationTitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(ObsidianCard, RoundedCornerShape(16.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Messages and calls in this chat are end-to-end encrypted with the Signal Double Ratchet protocol. No one outside of this chat, not even Argus, can read or listen to them.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
    aiRepository: AiAssistantRepository
) {
    val isOutgoing = message.senderId == "me"
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showReactionsBar by remember { mutableStateOf(false) }

    val smartContexts = remember(message.text) {
        aiRepository.analyzeSmartContext(message.text)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOutgoing) 16.dp else 4.dp,
                        bottomEnd = if (isOutgoing) 4.dp else 16.dp
                    )
                )
                .background(if (isOutgoing) BubbleOutgoing else BubbleIncoming)
                .border(1.dp, if (isOutgoing) EmeraldDark.copy(alpha = 0.4f) else ObsidianBorder, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showReactionsBar = true },
                        onDoubleTap = { onReaction("❤️") }
                    )
                }
                .padding(12.dp)
        ) {
            Column {
                if (message.replyToSnippet != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBlack.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Text(text = message.replyToSnippet, style = MaterialTheme.typography.labelSmall, color = EmeraldLight, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (message.mediaType != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBlack.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = message.text, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(text = "${message.mediaSizeBytes / 1024} KB • Verified E2EE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.READ -> Icons.Default.DoneAll
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.SENT -> Icons.Default.Done
                                MessageStatus.SENDING -> Icons.Default.Schedule
                                else -> Icons.Default.ErrorOutline
                            },
                            contentDescription = message.status.name,
                            tint = if (message.status == MessageStatus.READ) CyanAccent else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Smart Context Chips
        if (smartContexts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                smartContexts.forEach { item ->
                    SuggestionChip(
                        onClick = { /* Action triggered */ },
                        label = { Text(item.actionLabel, fontSize = 11.sp, color = CyanAccent) },
                        icon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = ObsidianCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    )
                }
            }
        }

        // Reaction Bar
        if (showReactionsBar) {
            Row(
                modifier = Modifier
                    .background(ObsidianCard, RoundedCornerShape(20.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("👍", "❤️", "🔥", "😂", "😮", "🔒").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable {
                                onReaction(emoji)
                                showReactionsBar = false
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
    }
}
