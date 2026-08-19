package com.example.argus.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.theme.*

/**
 * Realistic Simulated In-App SMS Heads-Up Notification Banner.
 * Appears at the top of the screen when OTP is requested from the server.
 */
@Composable
fun IncomingSmsNotificationBanner(
    code: String,
    phoneNumber: String,
    onAutoFillClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp), spotColor = EmeraldPrimary.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E2422),
                        Color(0xFF121615)
                    )
                )
            )
            .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: SMS App Icon + Sender + Timestamp + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(EmeraldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "SMS",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MESSAGES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldLight,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = " • just now",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "Argus Security Gateway ($phoneNumber)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body text
            Text(
                text = "Your Argus verification code is: ",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = code,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EmeraldPrimary,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(Valid for 5 mins)",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto-fill Action
                Button(
                    onClick = onAutoFillClick,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = ObsidianBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto-Fill Code",
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Copy Action
                OutlinedButton(
                    onClick = onCopyClick,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ObsidianCard),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Interactive 6-Digit OTP Pin Input with individual glowing boxes.
 */
@Composable
fun ArgusSixDigitPinInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Invisible underlying text field handling input, keyboard, and paste
        BasicTextField(
            value = value,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }.take(6)
                onValueChange(filtered)
                if (filtered.length == 6) {
                    onComplete(filtered)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (value.length == 6) {
                        onComplete(value)
                    }
                }
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .height(58.dp)
                .background(Color.Transparent),
            decorationBox = {
                // Render the 6 individual stylized boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val digit = value.getOrNull(i)?.toString() ?: ""
                        val isFocused = (value.length == i) || (value.length == 6 && i == 5)
                        val isFilled = digit.isNotEmpty()

                        PinDigitBox(
                            digit = digit,
                            isFocused = isFocused,
                            isFilled = isFilled
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PinDigitBox(
    digit: String,
    isFocused: Boolean,
    isFilled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    val borderColor = when {
        isFilled -> EmeraldPrimary
        isFocused -> EmeraldPrimary.copy(alpha = 0.8f)
        else -> ObsidianBorder
    }

    val backgroundColor = when {
        isFilled -> ObsidianCard
        isFocused -> ObsidianSurface
        else -> ObsidianCard
    }

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused || isFilled) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (digit.isNotEmpty()) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        } else if (isFocused) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(EmeraldPrimary.copy(alpha = cursorAlpha))
            )
        }
    }
}

/**
 * Country Selector Pills for quick phone code selection
 */
data class CountryCodeItem(val flag: String, val name: String, val code: String)

val PopularCountryCodes = listOf(
    CountryCodeItem("🇺🇸", "US", "+1"),
    CountryCodeItem("🇮🇳", "IN", "+91"),
    CountryCodeItem("🇬🇧", "UK", "+44"),
    CountryCodeItem("🇩🇪", "DE", "+49"),
    CountryCodeItem("🇫🇷", "FR", "+33"),
    CountryCodeItem("🇯🇵", "JP", "+81"),
    CountryCodeItem("🇦🇺", "AU", "+61"),
    CountryCodeItem("🇦🇪", "UAE", "+971"),
    CountryCodeItem("🇨🇦", "CA", "+1")
)

@Composable
fun CountryCodeChips(
    selectedCode: String,
    onSelectCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PopularCountryCodes.take(5).forEach { item ->
            val isSelected = selectedCode == item.code
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianCard)
                    .border(
                        1.dp,
                        if (isSelected) EmeraldPrimary else ObsidianBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectCode(item.code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${item.flag} ${item.code}",
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) EmeraldLight else TextSecondary
                )
            }
        }
    }
}
