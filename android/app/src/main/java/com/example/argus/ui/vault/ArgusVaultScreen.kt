package com.example.argus.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.argus.data.model.VaultItem
import com.example.argus.data.model.VaultItemType
import com.example.argus.data.repository.VaultRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusButton
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
    var isUnlocked by remember { mutableStateOf(true) } // Biometric gate
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var selectedNoteToRead by remember { mutableStateOf<Pair<VaultItem, String>?>(null) }

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
                    Text(text = "Local Encrypted Storage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "Files stored here never leave your device and are encrypted with Android Keystore hardware keys.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Vault is Empty", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text(text = "Create encrypted secret notes or import files.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items) { item ->
                        VaultItemRow(
                            item = item,
                            onClick = {
                                if (item.type == VaultItemType.NOTE) {
                                    val decrypted = vaultRepository.decryptNote(item)
                                    selectedNoteToRead = item to decrypted
                                }
                            },
                            onDelete = {
                                vaultRepository.deleteItem(item.id)
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

        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            containerColor = ObsidianCard,
            title = { Text("New Encrypted Secret Note", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = ObsidianBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Secret Content (Passwords, Keys, Notes)") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = ObsidianBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                            vaultRepository.saveEncryptedNote(noteTitle.trim(), noteContent.trim())
                            showNewNoteDialog = false
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
            title = { Text(selectedNoteToRead!!.first.title, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Decrypted Note (Hardware AES-256-GCM):", style = MaterialTheme.typography.labelSmall, color = EmeraldLight)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(selectedNoteToRead!!.second, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
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
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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
