package com.example.argus.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.CallStatus
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
    onAcceptCall: () -> Unit = {},
    onRejectCall: () -> Unit = {},
    onEndCall: (Int) -> Unit
) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(callState.status) {
        if (callState.status == CallStatus.CONNECTED) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val isConnected = callState.status == CallStatus.CONNECTED
    val isIncoming = callState.status == CallStatus.INCOMING

    // Pulsating animation for calling / live stream
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveOffset"
    )

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
            // Top Header: Status & Encryption Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ObsidianCard, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
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

                Spacer(modifier = Modifier.height(28.dp))

                // Avatar with live sound ripple
                Box(contentAlignment = Alignment.Center) {
                    if (isConnected || !isIncoming) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .scale(if (isConnected) 1.08f else pulseScale)
                                .clip(CircleShape)
                                .background(
                                    if (isConnected) EmeraldPrimary.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.15f)
                                )
                        )
                    }

                    ArgusAvatar(name = callState.peerName, size = 110.dp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val statusText = when (callState.status) {
                    CallStatus.CONNECTED -> "Live HD Audio • $durationFormatted"
                    CallStatus.INCOMING -> "Incoming ${if (callState.callType == CallType.VIDEO) "Video" else "Voice"} Call..."
                    CallStatus.OUTGOING -> "Calling..."
                    CallStatus.ENDED -> "Call Ended"
                    CallStatus.MISSED -> "Call Missed"
                    CallStatus.REJECTED -> "Call Declined"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isConnected) EmeraldLight else if (isIncoming) CyanAccent else TextSecondary,
                    fontWeight = if (isConnected || isIncoming) FontWeight.Bold else FontWeight.Normal
                )

                // Live Equalizer Visualizer during active call
                if (isConnected) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { index ->
                            val height = (10 + (index * 6 * waveOffset)).dp
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(height)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(EmeraldPrimary)
                            )
                        }
                    }
                }
            }

            // Video Preview PIP (for video calls)
            if (callState.callType == CallType.VIDEO && callState.isVideoEnabled) {
                Box(
                    modifier = Modifier
                        .size(width = 170.dp, height = 230.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianSurface)
                        .border(2.dp, CyanAccent, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Encrypted Video Stream", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            // Call Controls Grid / Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isIncoming) {
                    // Incoming Call: Big Accept (Green) and Reject (Red) Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onRejectCall,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(ShieldRed, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline Call",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Decline", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAcceptCall,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(EmeraldPrimary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Accept Call",
                                    tint = TextOnEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Accept", style = MaterialTheme.typography.labelMedium, color = EmeraldLight, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // In-Call / Outgoing Controls
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
                            icon = if (callState.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
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
