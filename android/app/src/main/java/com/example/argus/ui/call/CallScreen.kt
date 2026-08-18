package com.example.argus.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.CallType
import com.example.argus.data.repository.ActiveCallState
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    callState: ActiveCallState,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onCameraSwitch: () -> Unit,
    onEndCall: (Int) -> Unit
) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(callState.status) {
        if (callState.status.name == "CONNECTED") {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianSurfaceVariant, ObsidianBlack, ObsidianBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ObsidianCard, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-End Encrypted WebRTC",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                ArgusAvatar(name = callState.peerName, size = 110.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (callState.status.name == "CONNECTED") durationFormatted else "Ringing...",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (callState.status.name == "CONNECTED") EmeraldLight else TextSecondary
                )
            }

            // Video Preview PIP (for video calls)
            if (callState.callType == CallType.VIDEO && callState.isVideoEnabled) {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianSurface)
                        .border(2.dp, CyanAccent, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Local Stream", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            // Call Controls Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (callState.isMuted) "Unmute" else "Mute",
                        isActive = callState.isMuted,
                        onClick = onMuteToggle
                    )

                    CallControlButton(
                        icon = if (callState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = "Speaker",
                        isActive = callState.isSpeakerOn,
                        onClick = onSpeakerToggle
                    )

                    if (callState.callType == CallType.VIDEO) {
                        CallControlButton(
                            icon = if (callState.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = "Video",
                            isActive = callState.isVideoEnabled,
                            onClick = onVideoToggle
                        )

                        CallControlButton(
                            icon = Icons.Default.Cameraswitch,
                            label = "Flip",
                            isActive = false,
                            onClick = onCameraSwitch
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // End Call Button
                IconButton(
                    onClick = { onEndCall(elapsedSeconds) },
                    modifier = Modifier
                        .size(72.dp)
                        .background(ShieldRed, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(if (isActive) EmeraldPrimary else ObsidianCard, CircleShape)
                .border(1.dp, if (isActive) EmeraldPrimary else ObsidianBorder, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) TextOnEmerald else TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
