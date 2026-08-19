package com.example.argus.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusTopBar

@Composable
fun AiAssistantScreen(
    aiRepository: AiAssistantRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var inputQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("Tamil") }
    var selectedTone by remember { mutableStateOf("professional") }
    var outputResult by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Translation & Tone, 1: Summarize, 2: Smart Context

    val availableLanguages = listOf("Tamil", "Hindi", "Spanish", "French", "German", "Japanese", "Arabic")
    val availableTones = listOf("professional", "concise", "friendly", "urgent")

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Argus Intelligence",
                subtitle = "100% On-Device Privacy AI Engine",
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
                    text = "All AI analysis occurs on-device. Private encrypted messages are never sent to external AI servers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Feature Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianCard,
                contentColor = EmeraldPrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Translate & Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Summarizer", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Smart Context", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // Text Input Box
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

            when (selectedTab) {
                0 -> {
                    // Translation & Tone Tab
                    Text(text = "Rewrite Tone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTones.forEach { tone ->
                            FilterChip(
                                selected = selectedTone == tone,
                                onClick = { selectedTone = tone },
                                label = { Text(tone.replaceFirstChar { it.uppercase() }, color = if (selectedTone == tone) TextOnEmerald else TextPrimary) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                outputResult = aiRepository.rewriteMessage(inputQuery, selectedTone)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = ObsidianBlack),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply ${selectedTone.replaceFirstChar { it.uppercase() }} Tone")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Universal Message Translator", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    
                    // Languages Chip Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableLanguages.take(4).forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(lang, color = if (selectedLanguage == lang) TextOnEmerald else TextPrimary) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableLanguages.drop(4).forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(lang, color = if (selectedLanguage == lang) TextOnEmerald else TextPrimary) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                                )
                            }
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
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Translate to $selectedLanguage")
                    }
                }

                1 -> {
                    // Summarizer Tab
                    Text(text = "Thread Summarizer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "Extract key decisions, action items, and context from conversation history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            val sampleMessages = listOf(
                                "Alice: Team, the audit is completed.",
                                "Bob: All 12 crypto unit tests passed.",
                                "Charlie: Double Ratchet session persistence is verified.",
                                inputQuery
                            )
                            outputResult = aiRepository.summarizeConversation(sampleMessages)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = TextOnEmerald),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Summarize Thread")
                    }
                }

                2 -> {
                    // Smart Context Tab
                    Text(text = "Smart Context Entity Extractor", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    val contexts = remember(inputQuery) { aiRepository.analyzeSmartContext(inputQuery) }

                    if (contexts.isEmpty()) {
                        Text("Type dates, phone numbers, URLs, or action items above to see smart context extraction.", color = TextMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            contexts.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianCard, RoundedCornerShape(10.dp))
                                        .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            Toast.makeText(context, "Action: ${item.actionLabel}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.actionLabel, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(item.value, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Output Result Card
            val result = outputResult
            if (result != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCard, RoundedCornerShape(14.dp))
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "AI Result", style = MaterialTheme.typography.labelSmall, color = EmeraldLight, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Argus AI Result", result)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Result copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = result, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
    }
}
