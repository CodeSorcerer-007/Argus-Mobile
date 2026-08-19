package com.example.argus.ui.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.Message
import com.example.argus.data.model.MessageStatus
import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.data.repository.SmartContextItem
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

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
    onContactInfoClick: () -> Unit = onVerifySecurityClick,
    aiRepository: AiAssistantRepository
) {
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var selectedDisappearingDuration by remember { mutableStateOf<Int?>(conversation.disappearingDurationSec) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDurationSec = 0
            while (isRecordingVoice) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    // Disappearing Messages Modal
    if (showDisappearingDialog) {
        DisappearingMessagesDialog(
            currentDuration = selectedDisappearingDuration,
            onDismiss = { showDisappearingDialog = false },
            onDurationSelected = { duration ->
                selectedDisappearingDuration = duration
                showDisappearingDialog = false
                Toast.makeText(context, "Disappearing messages timer updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Full Screen Decrypted Photo Viewer Dialog
    if (fullScreenPhotoUrl != null) {
        MediaViewerDialog(
            photoUrl = fullScreenPhotoUrl,
            onDismiss = { fullScreenPhotoUrl = null },
            onSaveToVault = {
                Toast.makeText(context, "Encrypted image saved to Argus Vault!", Toast.LENGTH_SHORT).show()
                fullScreenPhotoUrl = null
            }
        )
    }

    Scaffold(
        containerColor = ChatWallpaperBg,
        topBar = {
            Surface(
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onContactInfoClick() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArgusAvatar(name = conversation.title, size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = if (selectedDisappearingDuration != null) "🔒 Disappearing (${selectedDisappearingDuration}s)" else "Online • E2EE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedDisappearingDuration != null) ShieldAmber else EmeraldLight
                            )
                        }
                    }

                    IconButton(onClick = onVideoCallClick) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call", tint = TextPrimary)
                    }
                    IconButton(onClick = onVoiceCallClick) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Audio Call", tint = TextPrimary)
                    }

                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(ObsidianCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Contact", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldPrimary) },
                                onClick = {
                                    showOptionsMenu = false
                                    onContactInfoClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Verify Safety Numbers", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = EmeraldPrimary) },
                                onClick = {
                                    showOptionsMenu = false
                                    onVerifySecurityClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Disappearing Messages", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Timelapse, contentDescription = null, tint = ShieldAmber) },
                                onClick = {
                                    showOptionsMenu = false
                                    showDisappearingDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat Wallpaper", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = CyanAccent) },
                                onClick = {
                                    showOptionsMenu = false
                                    Toast.makeText(context, "Obsidian Cyber theme active", Toast.LENGTH_SHORT).show()
                                }
                            )
                            HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)
                            DropdownMenuItem(
                                text = { Text("Clear Chat", color = Color(0xFFFF6B6B)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B)) },
                                onClick = {
                                    showOptionsMenu = false
                                    Toast.makeText(context, "Chat history zeroized locally", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .navigationBarsPadding()
            ) {
                // Quoted Reply Dock
                if (replyingToMessage != null) {
                    val reply = replyingToMessage!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard)
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
                            Text(
                                text = if (reply.senderId == "me") "You" else conversation.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight
                            )
                            Text(
                                text = reply.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // WhatsApp / Telegram Rich Attachment Grid
                AnimatedVisibility(visible = showAttachmentMenu) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCard)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttachmentGridItem(icon = Icons.Default.Description, label = "Document", color = Color(0xFF7F66FF)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("📄 Encrypted Project Specs (PDF)", "DOCUMENT", "https://argus.sec/doc.pdf", expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.CameraAlt, label = "Camera", color = Color(0xFFFF2E93)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("📷 Hardware Camera Snapshot", "PHOTO", "https://argus.sec/cam_snapshot.jpg", expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.Image, label = "Gallery", color = Color(0xFFC433FF)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("🖼️ Encrypted Secret Photo", "PHOTO", "https://argus.sec/photo.jpg", expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.Headphones, label = "Audio", color = Color(0xFFFF9800)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("🎵 Secure Audio Track (0:45)", "AUDIO", "https://argus.sec/audio.aac", expiresAt)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttachmentGridItem(icon = Icons.Default.LocationOn, label = "Location", color = Color(0xFF00C853)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("📍 Encrypted GPS Pin: 37.7749° N, 122.4194° W", "LOCATION", null, expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.Person, label = "Contact", color = Color(0xFF00B0FF)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("👤 Contact Card: Argus Core Admin (+1 555-0199)", null, null, expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.Poll, label = "Poll", color = Color(0xFFFFD600)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("📊 Poll: Enable Post-Quantum Kyber1024?\n1. Yes (Recommended)\n2. Postpone", null, null, expiresAt)
                            }
                            AttachmentGridItem(icon = Icons.Default.FolderSpecial, label = "Vault Secret", color = Color(0xFF00E599)) {
                                showAttachmentMenu = false
                                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                onSendMessage("🔒 Hardware-Locked Vault Note (AES-256-GCM)", null, null, expiresAt)
                            }
                        }
                    }
                }

                // Dynamic Input Composer Bar
                if (isRecordingVoice) {
                    // Active Voice Recording Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording ${recordingDurationSec / 60}:${(recordingDurationSec % 60).toString().padStart(2, '0')}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { isRecordingVoice = false }) {
                                Text("Cancel", color = Color(0xFFFF6B6B))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                    onSendMessage("🎤 Voice Note (${recordingDurationSec}s)", "AUDIO", "https://argus.sec/voice_${System.currentTimeMillis()}.aac", expiresAt)
                                    isRecordingVoice = false
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice", tint = TextOnEmerald)
                            }
                        }
                    }
                } else {
                    // Standard Composer Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji / Sticker Button
                        IconButton(onClick = {
                            inputText += "🔒"
                        }) {
                            Icon(imageVector = Icons.Default.SentimentSatisfiedAlt, contentDescription = "Emoji", tint = TextSecondary)
                        }

                        // Message Text Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(ObsidianCard)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = { Text("Message", color = TextMuted, fontSize = 15.sp) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4
                                )

                                IconButton(
                                    onClick = { showAttachmentMenu = !showAttachmentMenu },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach", tint = TextSecondary)
                                }

                                if (inputText.isEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                            onSendMessage("📷 Encrypted Camera Photo", "PHOTO", "https://argus.sec/cam.jpg", expiresAt)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera", tint = TextSecondary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Dynamic Send or Voice Record Button
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                    onSendMessage(inputText.trim(), null, null, expiresAt)
                                    inputText = ""
                                    replyingToMessage = null
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TextOnEmerald, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { isRecordingVoice = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "Record Voice", tint = TextOnEmerald, modifier = Modifier.size(22.dp))
                            }
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // E2EE Security Banner
            item {
                E2eeSecurityBanner(onVerifyClick = onVerifySecurityClick)
            }

            // Date Pill Separator
            item {
                DateSeparatorPill("TODAY")
            }

            // Message Bubbles
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == "me"
                val smartContextItems = remember(msg.text) { aiRepository.analyzeSmartContext(msg.text) }

                MessageBubbleWhatsAppStyle(
                    message = msg,
                    isMe = isMe,
                    smartContextItems = smartContextItems,
                    onReply = { replyingToMessage = msg },
                    onReaction = { emoji -> onReactionClick(msg, emoji) },
                    onPhotoClick = { url -> fullScreenPhotoUrl = url },
                    onSmartActionClick = { smartItem ->
                        when (smartItem.type) {
                            com.example.argus.data.repository.SmartContextType.PHONE_CALL -> {
                                Toast.makeText(context, "Copied number: ${smartItem.value}", Toast.LENGTH_SHORT).show()
                            }
                            com.example.argus.data.repository.SmartContextType.URL_LINK -> {
                                Toast.makeText(context, "Opening link: ${smartItem.value}", Toast.LENGTH_SHORT).show()
                            }
                            com.example.argus.data.repository.SmartContextType.DATE_CALENDAR -> {
                                Toast.makeText(context, "Calendar event created for: ${smartItem.actionLabel}", Toast.LENGTH_SHORT).show()
                            }
                            com.example.argus.data.repository.SmartContextType.ADDRESS_MAPS -> {
                                Toast.makeText(context, "Locating: ${smartItem.value}", Toast.LENGTH_SHORT).show()
                            }
                            com.example.argus.data.repository.SmartContextType.TASK_TODO -> {
                                Toast.makeText(context, "Task saved: ${smartItem.actionLabel}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}
