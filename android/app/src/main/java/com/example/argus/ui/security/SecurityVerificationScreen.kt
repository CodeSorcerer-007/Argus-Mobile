package com.example.argus.ui.security

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.crypto.keys.SafetyNumberCalculator
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun SecurityVerificationScreen(
    peerName: String,
    peerUserId: String,
    peerIdentityKeyBase64: String,
    myUserId: String,
    myIdentityKeyBase64: String,
    isCurrentlyVerified: Boolean,
    onMarkVerified: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val safetyNumber = remember(peerUserId, myUserId) {
        SafetyNumberCalculator.computeSafetyNumber(myUserId, myIdentityKeyBase64, peerUserId, peerIdentityKeyBase64)
    }

    val qrBitmap: Bitmap? = remember(safetyNumber) {
        try {
            SafetyNumberCalculator.generateQrBitmap("argus-verify:$peerUserId:$safetyNumber", 512)
        } catch (e: Exception) {
            null
        }
    }

    var isVerified by remember { mutableStateOf(isCurrentlyVerified) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Verify Safety Numbers",
                subtitle = peerName,
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "End-to-End Encryption Verification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To verify that your messages and calls with $peerName are end-to-end encrypted and have not been intercepted by a man-in-the-middle, compare the 60-digit number below or scan the QR code.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code Box
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(ObsidianCard, RoundedCornerShape(20.dp))
                    .border(2.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Safety Number QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 60-digit number display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = safetyNumber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            ArgusButton(
                text = if (isVerified) "Verified (Tap to Unverify)" else "Mark as Verified",
                onClick = {
                    isVerified = !isVerified
                    onMarkVerified(isVerified)
                },
                isPrimary = !isVerified,
                icon = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Shield
            )
        }
    }
}
