package com.example.argus.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun AiAssistantScreen(
    aiRepository: AiAssistantRepository,
    onBackClick: () -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("Tamil") }
    var selectedTone by remember { mutableStateOf("professional") }
    var outputResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Argus Intelligence",
                subtitle = "On-Device Privacy-Preserving AI",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy transparency notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianCard, RoundedCornerShape(14.dp))
                    .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "All AI analysis occurs on-device. Private encrypted messages are never sent to external AI servers without explicit user consent.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                label = { Text("Draft or paste text...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                )
            )

            // Tone Rewriter Row
            Text(text = "Rewrite Tone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("professional", "concise", "friendly").forEach { tone ->
                    FilterChip(
                        selected = selectedTone == tone,
                        onClick = { selectedTone = tone },
                        label = { Text(tone.replaceFirstChar { it.uppercase() }, color = if (selectedTone == tone) TextOnEmerald else TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (inputQuery.isNotBlank()) {
                            outputResult = aiRepository.rewriteMessage(inputQuery, selectedTone)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rewrite")
                }
            }

            // Universal Translator
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Universal Message Translator", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Tamil", "Hindi", "Spanish", "French").forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { selectedLanguage = lang },
                        label = { Text(lang, color = if (selectedLanguage == lang) TextOnEmerald else TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                    )
                }
            }

            Button(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        outputResult = aiRepository.translateMessage(inputQuery, selectedLanguage)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = TextOnEmerald),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Translate to $selectedLanguage")
            }

            // Result Display Box
            val result = outputResult
            if (result != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCard, RoundedCornerShape(14.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(text = "Result:", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = result, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
    }
}
