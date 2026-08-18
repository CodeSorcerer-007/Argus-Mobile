package com.example.argus.ui.main

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.data.model.CallRecord
import com.example.argus.data.model.CallType
import com.example.argus.data.model.Contact
import com.example.argus.data.model.Conversation
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusPulseBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val title: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Default.ChatBubble),
    FAVORITES("Saved", Icons.Default.Bookmark),
    CALLS("Calls", Icons.Default.Call),
    CONTACTS("Contacts", Icons.Default.People),
    VAULT("Vault", Icons.Default.Lock),
    SHIELD("Shield", Icons.Default.Shield)
}

@Composable
fun MainScreen(
    conversations: List<Conversation>,
    contacts: List<Contact>,
    calls: List<CallRecord>,
    onConversationClick: (String) -> Unit,
    onContactClick: (Contact) -> Unit,
    onStartCallClick: (Contact, CallType) -> Unit,
    onVaultClick: () -> Unit,
    onShieldClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Argus",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ArgusPulseBadge(isOnline = true, isE2EE = true)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onAiAssistantClick) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Assistant",
                                    tint = CyanAccent
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    // Search Bar
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search messages, contacts, files...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextOnEmerald,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == MainTab.CHATS || selectedTab == MainTab.CONTACTS) {
                FloatingActionButton(
                    onClick = onNewChatClick,
                    containerColor = EmeraldPrimary,
                    contentColor = TextOnEmerald,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.AddComment, contentDescription = "New Chat")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ObsidianBlack)
        ) {
            when (selectedTab) {
                MainTab.CHATS -> {
                    ChatsTabContent(
                        conversations = conversations.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.lastSnippet.contains(searchQuery, ignoreCase = true)
                        },
                        onConversationClick = onConversationClick
                    )
                }
                MainTab.FAVORITES -> {
                    FavoritesTabContent()
                }
                MainTab.CALLS -> {
                    CallsTabContent(calls = calls)
                }
                MainTab.CONTACTS -> {
                    ContactsTabContent(
                        contacts = contacts.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                    it.phoneNumber.contains(searchQuery)
                        },
                        onContactClick = onContactClick,
                        onStartCall = onStartCallClick
                    )
                }
                MainTab.VAULT -> {
                    onVaultClick()
                }
                MainTab.SHIELD -> {
                    onShieldClick()
                }
            }
        }
    }
}

@Composable
private fun ChatsTabContent(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "No active conversations", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Text(text = "Tap the + button to start a private chat.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(conversations) { conv ->
                ConversationItemRow(conversation = conv, onClick = { onConversationClick(conv.id) })
            }
        }
    }
}

@Composable
private fun ConversationItemRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = dateFormat.format(Date(conversation.lastMessageTimestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArgusAvatar(name = conversation.title, size = 52.dp)

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) EmeraldPrimary else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(EmeraldPrimary, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            color = TextOnEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTabContent(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onStartCall: (Contact, CallType) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(contacts) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContactClick(contact) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArgusAvatar(name = contact.displayName, size = 48.dp, isOnline = contact.isOnline)

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (contact.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Safety Number",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (contact.isOnline) "online" else contact.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (contact.isOnline) EmeraldPrimary else TextSecondary
                    )
                }

                IconButton(onClick = { onStartCall(contact, CallType.VOICE) }) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = "Voice Call", tint = EmeraldPrimary)
                }
                IconButton(onClick = { onStartCall(contact, CallType.VIDEO) }) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call", tint = CyanAccent)
                }
            }
        }
    }
}

@Composable
private fun CallsTabContent(calls: List<CallRecord>) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(calls) { call ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArgusAvatar(name = call.peerName, size = 48.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = call.peerName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (call.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            tint = if (call.status.name == "MISSED") ShieldRed else EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${call.status.name} • ${dateFormat.format(Date(call.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTabContent() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Saved Messages & Documents", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Star any message, encrypted photo, or document to access it instantly here with custom folder tags.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = TextSecondary
            )
        }
    }
}
