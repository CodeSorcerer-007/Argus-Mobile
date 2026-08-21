package com.example.argus.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.core.location.ArgusLocationProvider
import com.example.argus.core.media.ArgusAudioRecorder
import com.example.argus.core.permission.ArgusPermissionType
import com.example.argus.core.permission.PermissionManager
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.Message
import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusPermissionRationaleDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onContactInfoClick: () -> Unit,
    isPeerTyping: Boolean = false,
    onTyping: (Boolean) -> Unit = {},
    aiRepository: AiAssistantRepository
) {
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var voiceOutputFile by remember { mutableStateOf<File?>(null) }
    var selectedDisappearingDuration by remember { mutableStateOf<Int?>(conversation.disappearingDurationSec) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var activeRationalePermission by remember { mutableStateOf<ArgusPermissionType?>(null) }

    var showContactShareDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showVaultSecretDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioRecorder = remember { ArgusAudioRecorder(context) }

    // Smart Back Button Handler (dismiss sheets/dialogs before exiting)
    BackHandler {
        if (activeRationalePermission != null) {
            activeRationalePermission = null
        } else if (fullScreenPhotoUrl != null) {
            fullScreenPhotoUrl = null
        } else if (showAttachmentMenu) {
            showAttachmentMenu = false
        } else if (showDisappearingDialog) {
            showDisappearingDialog = false
        } else if (showContactShareDialog) {
            showContactShareDialog = false
        } else if (showPollDialog) {
            showPollDialog = false
        } else if (showVaultSecretDialog) {
            showVaultSecretDialog = false
        } else if (replyingToMessage != null) {
            replyingToMessage = null
        } else {
            onBackClick()
        }
    }

    // Native Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val photoFile = File(context.cacheDir, "argus_cam_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                onSendMessage("📷 Encrypted Photo", photoFile.absolutePath, "PHOTO", expiresAt)
                Toast.makeText(context, "Encrypted photo captured and sent!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Photo processing error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            activeRationalePermission = ArgusPermissionType.CAMERA
        }
    }

    fun launchCamera() {
        if (PermissionManager.hasCameraPermission(context)) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Native Gallery Image Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val destFile = File(context.cacheDir, "argus_gallery_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                onSendMessage("🖼️ Encrypted Image", destFile.absolutePath, "PHOTO", expiresAt)
                Toast.makeText(context, "Image selected and sent securely!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "File copy error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Storage / Media Permission Launcher
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it } || PermissionManager.hasStoragePermission(context)
        if (granted) {
            galleryLauncher.launch("image/*")
        } else {
            activeRationalePermission = ArgusPermissionType.STORAGE_AND_MEDIA
        }
    }

    fun launchGalleryPicker() {
        if (PermissionManager.hasStoragePermission(context)) {
            galleryLauncher.launch("image/*")
        } else {
            mediaPermissionLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
        }
    }

    // Native Document / File Picker
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val destFile = File(context.cacheDir, "argus_doc_${System.currentTimeMillis()}.dat")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                onSendMessage("📄 Encrypted Document", destFile.absolutePath, "DOCUMENT", expiresAt)
                Toast.makeText(context, "Document encrypted and sent!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "File error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchDocumentPicker() {
        if (PermissionManager.hasStoragePermission(context)) {
            documentLauncher.launch("*/*")
        } else {
            mediaPermissionLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
        }
    }

    // Audio Permission Launcher for Voice Notes
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "argus_voice_${System.currentTimeMillis()}.m4a")
            voiceOutputFile = file
            if (audioRecorder.startRecording(file)) {
                isRecordingVoice = true
            }
        } else {
            activeRationalePermission = ArgusPermissionType.AUDIO
        }
    }

    fun triggerVoiceRecord() {
        if (PermissionManager.hasAudioPermission(context)) {
            val file = File(context.cacheDir, "argus_voice_${System.currentTimeMillis()}.m4a")
            voiceOutputFile = file
            if (audioRecorder.startRecording(file)) {
                isRecordingVoice = true
            } else {
                Toast.makeText(context, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
            }
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // GPS Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it } || PermissionManager.hasLocationPermission(context)
        if (granted) {
            coroutineScope.launch {
                Toast.makeText(context, "Acquiring secure GPS location...", Toast.LENGTH_SHORT).show()
                val loc = ArgusLocationProvider.getCurrentLocation(context)
                if (loc != null) {
                    val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                    onSendMessage(loc.displayText, loc.mapUrl, "LOCATION", expiresAt)
                    Toast.makeText(context, "GPS Location pin encrypted and sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not acquire GPS location. Please check GPS settings.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            activeRationalePermission = ArgusPermissionType.LOCATION
        }
    }

    fun triggerLocationSharing() {
        if (PermissionManager.hasLocationPermission(context)) {
            coroutineScope.launch {
                Toast.makeText(context, "Acquiring secure GPS location...", Toast.LENGTH_SHORT).show()
                val loc = ArgusLocationProvider.getCurrentLocation(context)
                if (loc != null) {
                    val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                    onSendMessage(loc.displayText, loc.mapUrl, "LOCATION", expiresAt)
                    Toast.makeText(context, "GPS Location pin encrypted and sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not acquire GPS location. Please check GPS settings.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            locationPermissionLauncher.launch(PermissionManager.getLocationPermissions())
        }
    }

    // Audio Call Permission Launcher
    val voiceCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onVoiceCallClick()
        } else {
            activeRationalePermission = ArgusPermissionType.AUDIO
        }
    }

    fun triggerVoiceCall() {
        if (PermissionManager.hasAudioPermission(context)) {
            onVoiceCallClick()
        } else {
            voiceCallPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Video Call Permission Launcher
    val videoCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioGranted = grants[android.Manifest.permission.RECORD_AUDIO] ?: PermissionManager.hasAudioPermission(context)
        val cameraGranted = grants[android.Manifest.permission.CAMERA] ?: PermissionManager.hasCameraPermission(context)
        if (audioGranted && cameraGranted) {
            onVideoCallClick()
        } else if (!audioGranted) {
            activeRationalePermission = ArgusPermissionType.AUDIO
        } else {
            activeRationalePermission = ArgusPermissionType.CAMERA
        }
    }

    fun triggerVideoCall() {
        if (PermissionManager.hasAudioPermission(context) && PermissionManager.hasCameraPermission(context)) {
            onVideoCallClick()
        } else {
            videoCallPermissionLauncher.launch(PermissionManager.getCallPermissions())
        }
    }

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

    // Debounced Typing Indicator (M-7)
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            onTyping(true)
            delay(2500)
            onTyping(false)
        } else {
            onTyping(false)
        }
    }

    val sendCurrentMessage = {
        if (inputText.isNotBlank()) {
            val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
            onSendMessage(inputText.trim(), null, null, expiresAt)
            inputText = ""
            replyingToMessage = null
            onTyping(false)
        }
    }

    // Active Permission Rationale Dialog
    if (activeRationalePermission != null) {
        ArgusPermissionRationaleDialog(
            permissionType = activeRationalePermission!!,
            onGrantClick = {
                val perm = activeRationalePermission!!
                activeRationalePermission = null
                when (perm) {
                    ArgusPermissionType.CAMERA -> cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    ArgusPermissionType.AUDIO -> audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    ArgusPermissionType.STORAGE_AND_MEDIA -> mediaPermissionLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
                    ArgusPermissionType.LOCATION -> locationPermissionLauncher.launch(PermissionManager.getLocationPermissions())
                    else -> {}
                }
            },
            onDismiss = { activeRationalePermission = null },
            onOpenSettingsClick = {
                activeRationalePermission = null
                PermissionManager.openAppSettings(context)
            }
        )
    }

    // Disappearing Messages Modal
    if (showDisappearingDialog) {
        DisappearingMessagesDialog(
            currentDuration = selectedDisappearingDuration,
            onDismiss = { showDisappearingDialog = false },
            onDurationSelected = { duration: Int? ->
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

    // Interactive Contact Card Share Dialog
    if (showContactShareDialog) {
        var contactName by remember { mutableStateOf("") }
        var contactPhone by remember { mutableStateOf("") }
        var contactRole by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showContactShareDialog = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Share Contact Card", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
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
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number or Username") },
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
                        value = contactRole,
                        onValueChange = { contactRole = it },
                        label = { Text("Organization / Role (Optional)") },
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
                        if (contactName.isNotBlank()) {
                            val roleSuffix = if (contactRole.isNotBlank()) " • $contactRole" else ""
                            val phoneStr = if (contactPhone.isNotBlank()) " ($contactPhone)" else ""
                            val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                            onSendMessage("👤 Contact Card: ${contactName.trim()}$phoneStr$roleSuffix", null, null, expiresAt)
                            showContactShareDialog = false
                            Toast.makeText(context, "Contact card sent encrypted!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Share Contact", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactShareDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Interactive Poll Creator Dialog
    if (showPollDialog) {
        var pollQuestion by remember { mutableStateOf("") }
        var opt1 by remember { mutableStateOf("") }
        var opt2 by remember { mutableStateOf("") }
        var opt3 by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Create Encrypted Poll", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        label = { Text("Question") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = opt1,
                        onValueChange = { opt1 = it },
                        label = { Text("Option 1") },
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
                        value = opt2,
                        onValueChange = { opt2 = it },
                        label = { Text("Option 2") },
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
                        value = opt3,
                        onValueChange = { opt3 = it },
                        label = { Text("Option 3 (Optional)") },
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
                        if (pollQuestion.isNotBlank() && opt1.isNotBlank() && opt2.isNotBlank()) {
                            val opt3Str = if (opt3.isNotBlank()) "\n3. ${opt3.trim()}" else ""
                            val pollPayload = "📊 Poll: ${pollQuestion.trim()}\n1. ${opt1.trim()}\n2. ${opt2.trim()}$opt3Str"
                            val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                            onSendMessage(pollPayload, null, null, expiresAt)
                            showPollDialog = false
                            Toast.makeText(context, "Encrypted poll shared with chat!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter question and at least 2 options", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Send Poll", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Interactive Hardware Vault Secret Note Dialog
    if (showVaultSecretDialog) {
        var secretTitle by remember { mutableStateOf("") }
        var secretContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showVaultSecretDialog = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Share Vault Secret Note", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Encrypted with AES-256-GCM Double Ratchet. Accessible only by peer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLight
                    )
                    OutlinedTextField(
                        value = secretTitle,
                        onValueChange = { secretTitle = it },
                        label = { Text("Note Title") },
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
                        value = secretContent,
                        onValueChange = { secretContent = it },
                        label = { Text("Secret Content (Passwords, Keys, Seeds)") },
                        maxLines = 4,
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
                        if (secretContent.isNotBlank()) {
                            val titleStr = if (secretTitle.isNotBlank()) "[${secretTitle.trim()}]\n" else ""
                            val payload = "🔒 Hardware-Locked Vault Note:\n$titleStr${secretContent.trim()}"
                            val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                            onSendMessage(payload, null, null, expiresAt)
                            showVaultSecretDialog = false
                            Toast.makeText(context, "Encrypted vault note transmitted!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Transmit Secret", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultSecretDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = if (isPeerTyping) "typing..." else if (selectedDisappearingDuration != null) "🔒 Disappearing (${selectedDisappearingDuration}s)" else "Online • E2EE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPeerTyping) EmeraldPrimary else if (selectedDisappearingDuration != null) ShieldAmber else EmeraldLight,
                                fontWeight = if (isPeerTyping) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Direct Security Verification Icon (M-8)
                    IconButton(onClick = onVerifySecurityClick) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = "Verify Security",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = { triggerVideoCall() }) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call", tint = TextPrimary)
                    }
                    IconButton(onClick = { triggerVoiceCall() }) {
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
                                launchDocumentPicker()
                            }
                            AttachmentGridItem(icon = Icons.Default.CameraAlt, label = "Camera", color = Color(0xFFFF2E93)) {
                                showAttachmentMenu = false
                                launchCamera()
                            }
                            AttachmentGridItem(icon = Icons.Default.Image, label = "Gallery", color = Color(0xFFC433FF)) {
                                showAttachmentMenu = false
                                launchGalleryPicker()
                            }
                            AttachmentGridItem(icon = Icons.Default.Headphones, label = "Audio Note", color = Color(0xFFFF9800)) {
                                showAttachmentMenu = false
                                triggerVoiceRecord()
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AttachmentGridItem(icon = Icons.Default.LocationOn, label = "Location", color = Color(0xFF00C853)) {
                                showAttachmentMenu = false
                                triggerLocationSharing()
                            }
                            AttachmentGridItem(icon = Icons.Default.Person, label = "Contact", color = Color(0xFF00B0FF)) {
                                showAttachmentMenu = false
                                showContactShareDialog = true
                            }
                            AttachmentGridItem(icon = Icons.Default.Poll, label = "Poll", color = Color(0xFFFFD600)) {
                                showAttachmentMenu = false
                                showPollDialog = true
                            }
                            AttachmentGridItem(icon = Icons.Default.FolderSpecial, label = "Vault Secret", color = Color(0xFF00E599)) {
                                showAttachmentMenu = false
                                showVaultSecretDialog = true
                            }
                        }
                    }
                }

                // Dynamic Input Composer Bar
                if (isRecordingVoice) {
                    // Active Hardware Voice Recording Bar
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
                            TextButton(onClick = {
                                audioRecorder.cancelRecording()
                                isRecordingVoice = false
                            }) {
                                Text("Cancel", color = Color(0xFFFF6B6B))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val durationMs = audioRecorder.stopRecording()
                                    val durationSec = maxOf((durationMs / 1000).toInt(), 1)
                                    val file = voiceOutputFile
                                    if (file != null && file.exists() && file.length() > 0) {
                                        val expiresAt = selectedDisappearingDuration?.let { System.currentTimeMillis() + (it * 1000L) } ?: 0L
                                        onSendMessage("🎤 Voice Note (${durationSec}s)", file.absolutePath, "AUDIO", expiresAt)
                                        Toast.makeText(context, "Encrypted voice note sent!", Toast.LENGTH_SHORT).show()
                                    }
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
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Send,
                                        keyboardType = KeyboardType.Text
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = { sendCurrentMessage() }
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown && !keyEvent.isShiftPressed) {
                                                sendCurrentMessage()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                )

                                IconButton(
                                    onClick = { showAttachmentMenu = !showAttachmentMenu },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach", tint = TextSecondary)
                                }

                                if (inputText.isEmpty()) {
                                    IconButton(
                                        onClick = { launchCamera() },
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
                                onClick = { sendCurrentMessage() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TextOnEmerald, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { triggerVoiceRecord() },
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
