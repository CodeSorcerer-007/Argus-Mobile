package com.example.argus.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.theme.*
import kotlinx.coroutines.delay

@Composable
fun ArgusSplashScreen(
    onSplashFinished: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    // Logo entrance animation
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarScale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    val statusTexts = listOf(
        "INITIALIZING HARDWARE KEYSTORE...",
        "VERIFYING DOUBLE RATCHET SESSIONS...",
        "ARGUS SECURE PROTOCOL ACTIVE"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(400))
        logoScale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        glowAlpha.animateTo(0.9f, animationSpec = tween(500))
        textAlpha.animateTo(1f, animationSpec = tween(400))

        delay(400)
        step = 1
        delay(400)
        step = 2
        delay(500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF072A1E),
                        ObsidianBlack,
                        Color(0xFF020705)
                    ),
                    radius = 1200f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Radar Wave Ring Behind Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(190.dp)
            ) {
                // Expanding Radar Wave
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(radarScale)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            EmeraldPrimary.copy(alpha = radarAlpha),
                            CircleShape
                        )
                )

                // Secondary Pulsing Glow
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    EmeraldPrimary.copy(alpha = glowAlpha.value * 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Central Argus Logo Shield
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    EmeraldPrimary,
                                    EmeraldLight,
                                    Color(0xFF00E5FF),
                                    EmeraldPrimary
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.argus_logo),
                        contentDescription = "Argus Logo",
                        modifier = Modifier.size(86.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Typographic Title & Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "ARGUS",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "ZERO-KNOWLEDGE ENCRYPTED MESSENGER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = EmeraldLight
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Security Status Ticker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusTexts.getOrElse(step) { statusTexts.last() },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
