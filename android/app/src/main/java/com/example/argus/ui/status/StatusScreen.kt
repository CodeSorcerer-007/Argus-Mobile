package com.example.argus.ui.status

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.core.permission.ArgusPermissionType
import com.example.argus.core.permission.PermissionManager
import com.example.argus.data.model.User
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusPermissionRationaleDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EphemeralStatusItem(
    val id: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val caption: String,
    val backgroundGradient: List<Color>,
    val timestamp: Long,
    val isViewed: Boolean = false,
    val mediaUri: String? = null
)

@Composable
fun StatusScreen(
    currentUser: User?,
    onViewStatus: (EphemeralStatusItem) -> Unit,
    onCreateStatus: () -> Unit
) {
    val context = LocalContext.current
    var myStatuses by remember { mutableStateOf(listOf<EphemeralStatusItem>()) }
    var recentStatuses by remember { mutableStateOf(listOf<EphemeralStatusItem>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newStatusText by remember { mutableStateOf("") }
    var activeRationalePermission by remember { mutableStateOf<ArgusPermissionType?>(null) }

    val colorPalettes = listOf(
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
        listOf(Color(0xFF141E30), Color(0xFF243B55))
    )
    var selectedColorIndex by remember { mutableIntStateOf(0) }

    // Camera Capture for Status
    val statusCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val item = EphemeralStatusItem(
                id = "my_photo_${System.currentTimeMillis()}",
                userId = currentUser?.id ?: "me",
                userName = currentUser?.displayName ?: "My Status",
                caption = "📷 Photo Status",
                backgroundGradient = listOf(Color(0xFF1B2838), Color(0xFF2A475E)),
                timestamp = System.currentTimeMillis(),
                isViewed = true
            )
            myStatuses = listOf(item) + myStatuses
            Toast.makeText(context, "Encrypted photo status shared! (24h timer active)", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            statusCameraLauncher.launch(null)
        } else {
            activeRationalePermission = ArgusPermissionType.CAMERA
        }
    }

    fun launchStatusCamera() {
        if (PermissionManager.hasCameraPermission(context)) {
            statusCameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Gallery Picker for Status
    val statusGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val item = EphemeralStatusItem(
                id = "my_gallery_${System.currentTimeMillis()}",
                userId = currentUser?.id ?: "me",
                userName = currentUser?.displayName ?: "My Status",
                caption = "🖼️ Media Status",
                backgroundGradient = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B)),
                timestamp = System.currentTimeMillis(),
                isViewed = true,
                mediaUri = uri.toString()
            )
            myStatuses = listOf(item) + myStatuses
            Toast.makeText(context, "Media status shared! (24h timer active)", Toast.LENGTH_SHORT).show()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted || PermissionManager.hasStorageOrMediaPermissions(context)) {
            statusGalleryLauncher.launch("image/*")
        } else {
            activeRationalePermission = ArgusPermissionType.STORAGE_AND_MEDIA
        }
    }

    fun launchStatusGallery() {
        if (PermissionManager.hasStorageOrMediaPermissions(context)) {
            statusGalleryLauncher.launch("image/*")
        } else {
            mediaPermissionLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
        }
    }

    // Active Permission Rationale Dialog
    if (activeRationalePermission != null) {
        ArgusPermissionRationaleDialog(
            permissionType = activeRationalePermission!!,
            onGrantClick = {
                val p = activeRationalePermission!!
                activeRationalePermission = null
                when (p) {
                    ArgusPermissionType.CAMERA -> cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    ArgusPermissionType.STORAGE_AND_MEDIA -> mediaPermissionLauncher.launch(PermissionManager.getStorageAndMediaPermissions())
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

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("New Status Update", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(colorPalettes[selectedColorIndex]))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (newStatusText.isEmpty()) "Type your status message..." else newStatusText,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedTextField(
                        value = newStatusText,
                        onValueChange = { newStatusText = it },
                        placeholder = { Text("What's on your mind? (Disappears in 24h)") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        colorPalettes.forEachIndexed { index, palette ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(palette))
                                    .border(
                                        width = if (selectedColorIndex == index) 3.dp else 1.dp,
                                        color = if (selectedColorIndex == index) EmeraldPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorIndex = index }
                            )
                        }
                    }

                    HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                showCreateDialog = false
                                launchStatusCamera()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera")
                        }

                        OutlinedButton(
                            onClick = {
                                showCreateDialog = false
                                launchStatusGallery()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newStatusText.isNotBlank()) {
                            val item = EphemeralStatusItem(
                                id = "my_${System.currentTimeMillis()}",
                                userId = currentUser?.id ?: "me",
                                userName = currentUser?.displayName ?: "My Status",
                                caption = newStatusText.trim(),
                                backgroundGradient = colorPalettes[selectedColorIndex],
                                timestamp = System.currentTimeMillis(),
                                isViewed = true
                            )
                            myStatuses = listOf(item) + myStatuses
                            showCreateDialog = false
                            newStatusText = ""
                            Toast.makeText(context, "Status updated! (Valid for 24h)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Post Status", color = TextOnEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // My Status Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .clickable {
                        if (myStatuses.isNotEmpty()) {
                            onViewStatus(myStatuses.first())
                        } else {
                            showCreateDialog = true
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ArgusAvatar(name = currentUser?.displayName ?: "My Status", size = 52.dp)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary)
                            .border(2.dp, ObsidianSurface, CircleShape)
                            .clickable { showCreateDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = TextOnEmerald, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (myStatuses.isNotEmpty()) "Tap to view updates (24h left)" else "Tap to add status update",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { launchStatusCamera() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera Status", tint = CyanAccent, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "New Status", tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Recent Updates Section
        if (recentStatuses.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT UPDATES",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            items(recentStatuses) { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .clickable { onViewStatus(status) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.5.dp,
                                brush = Brush.linearGradient(listOf(EmeraldPrimary, CyanAccent)),
                                shape = CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        ArgusAvatar(name = status.userName, size = 46.dp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = status.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = "1h ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CircleNotifications,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No recent status updates",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Status updates from your encrypted contacts disappear after 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenStatusViewer(
    status: EphemeralStatusItem,
    onClose: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(isPaused) {
        if (!isPaused) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ((1f - progress.value) * 6000).toInt(),
                    easing = LinearEasing
                )
            )
            onClose()
        } else {
            progress.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(status.backgroundGradient))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        if (offset.x < size.width / 3) {
                            onClose()
                        } else {
                            onClose()
                        }
                    }
                )
            }
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Segmented Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            // Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArgusAvatar(name = status.userName, size = 40.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = status.userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Just now",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Status Content Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.caption,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Bottom Reply Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Reply to ${status.userName}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Icon(imageVector = Icons.Default.Send, contentDescription = "Reply", tint = Color.White)
            }
        }
    }
}
