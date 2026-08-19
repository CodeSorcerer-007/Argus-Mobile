package com.example.argus.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.VaultItem
import com.example.argus.data.model.VaultItemType
import com.example.argus.data.repository.VaultRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArgusVaultScreen(
    vaultRepository: VaultRepository,
    onBackClick: () -> Unit
) {
    val items by vaultRepository.vaultItems.collectAsState()
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var selectedNoteToRead by remember { mutableStateOf<Pair<VaultItem, String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<VaultItemType?>(null) }
    val context = LocalContext.current

    val filteredItems = remember(items, searchQuery, selectedCategory) {
        items.filter { item ->
            val matchesCat = selectedCategory == null || item.type == selectedCategory
            val matchesQuery = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            ArgusTopBar(
                title = "Argus Vault",
                subtitle = "Hardware-backed AES-256 GCM Storage",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { showNewNoteDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item", tint = EmeraldPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Vault Header banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianCard, RoundedCornerShape(16.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(EmeraldPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Local Encrypted Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "Zero-knowledge hardware encrypted notes, seeds, and files.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search encrypted items...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${items.size})", color = if (selectedCategory == null) TextOnEmerald else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                )
                FilterChip(
                    selected = selectedCategory == VaultItemType.NOTE,
                    onClick = { selectedCategory = VaultItemType.NOTE },
                    label = { Text("Notes", color = if (selectedCategory == VaultItemType.NOTE) TextOnEmerald else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                )
                FilterChip(
                    selected = selectedCategory == VaultItemType.PHOTO,
                    onClick = { selectedCategory = VaultItemType.PHOTO },
                    label = { Text("Photos", color = if (selectedCategory == VaultItemType.PHOTO) TextOnEmerald else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                )
                FilterChip(
                    selected = selectedCategory == VaultItemType.FILE,
                    onClick = { selectedCategory = VaultItemType.FILE },
                    label = { Text("Files", color = if (selectedCategory == VaultItemType.FILE) TextOnEmerald else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching items found" else "Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search query" else "Create encrypted secret notes or import files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems) { item ->
                        VaultItemRow(
                            item = item,
                            onClick = {
                                if (item.type == VaultItemType.NOTE) {
                                    val decrypted = vaultRepository.decryptNote(item)
                                    selectedNoteToRead = item to decrypted
                                } else {
                                    Toast.makeText(context, "Encrypted item verified: ${item.title}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                vaultRepository.deleteItem(item.id)
                                Toast.makeText(context, "Item destroyed securely", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewNoteDialog) {
        var noteTitle by remember { mutableStateOf("") }
        var noteContent by remember { mutableStateOf("") }
        var noteCategoryType by remember { mutableStateOf(VaultItemType.NOTE) }

        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            containerColor = ObsidianCard,
            title = { Text("New Encrypted Secret Item", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title (e.g. Master Seed / Note)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Secret Content (Passwords, Keys, Notes)") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                            vaultRepository.saveEncryptedNote(noteTitle.trim(), noteContent.trim())
                            showNewNoteDialog = false
                            Toast.makeText(context, "Note encrypted with hardware key!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Encrypt & Save", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNoteDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (selectedNoteToRead != null) {
        AlertDialog(
            onDismissRequest = { selectedNoteToRead = null },
            containerColor = ObsidianCard,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedNoteToRead!!.first.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Decrypted Note", selectedNoteToRead!!.second)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied decrypted content!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            },
            text = {
                Column {
                    Text("Decrypted Note (Hardware AES-256-GCM):", style = MaterialTheme.typography.labelSmall, color = EmeraldLight)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianSurface, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(selectedNoteToRead!!.second, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNoteToRead = null }) {
                    Text("Close", color = EmeraldPrimary)
                }
            }
        )
    }
}

@Composable
private fun VaultItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (item.type) {
                VaultItemType.NOTE -> Icons.Default.Description
                VaultItemType.PHOTO -> Icons.Default.Image
                VaultItemType.FILE -> Icons.Default.Folder
                VaultItemType.VIDEO -> Icons.Default.Videocam
            },
            contentDescription = null,
            tint = when (item.type) {
                VaultItemType.NOTE -> EmeraldPrimary
                VaultItemType.PHOTO -> CyanAccent
                VaultItemType.FILE -> ShieldAmber
                VaultItemType.VIDEO -> ShieldGreen
            },
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                text = "${item.type.name} • ${dateFormat.format(Date(item.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ShieldRed)
        }
    }
}
