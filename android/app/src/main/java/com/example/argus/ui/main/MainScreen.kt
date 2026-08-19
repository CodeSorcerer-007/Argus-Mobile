package com.example.argus.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.data.model.CallRecord
import com.example.argus.data.model.CallStatus
import com.example.argus.data.model.CallType
import com.example.argus.data.model.Contact
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.User
import com.example.argus.data.repository.AuthRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusPulseBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class MainTab(val title: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Default.ChatBubble),
    CONTACTS("Contacts", Icons.Default.People),
    FAVORITES("Pinned", Icons.Default.Star),
    CALLS("Calls", Icons.Default.Phone),
    VAULT("Vault", Icons.Default.Shield),
    SHIELD("Monitor", Icons.Default.Security)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    conversations: List<Conversation>,
    contacts: List<Contact>,
    calls: List<CallRecord>,
    authRepository: AuthRepository,
    onConversationClick: (String) -> Unit,
    onContactClick: (Contact) -> Unit,
    onStartCallClick: (Contact, CallType) -> Unit,
    onVaultClick: () -> Unit,
    onShieldClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val currentUser by authRepository.currentUser.collectAsState()

    // New Chat & User Search Dialog
    if (showNewChatDialog) {
        var searchInput by remember { mutableStateOf("") }
        var isSearching by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchJob by remember { mutableStateOf<Job?>(null) }

        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            containerColor = ObsidianCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Start New Chat", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 450.dp)
                ) {
                    Text(
                        text = "Find friends on Argus by typing their @username (e.g. @alice) or international phone number (+1...).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { query ->
                            searchInput = query
                            searchError = null
                            searchJob?.cancel()
                            if (query.trim().length >= 2) {
                                isSearching = true
                                searchJob = coroutineScope.launch {
                                    delay(400) // debounce
                                    try {
                                        val results = if (query.trim().startsWith("+")) {
                                            val found = authRepository.findUserByPhone(query.trim())
                                            if (found != null) listOf(found) else emptyList()
                                        } else {
                                            authRepository.searchUsers(query.trim().removePrefix("@"))
                                        }
                                        searchResults = results.filter { it.id != currentUser?.id }
                                        if (searchResults.isEmpty()) {
                                            searchError = "No registered user found for \"$query\""
                                        }
                                    } catch (e: Exception) {
                                        searchError = "Search error: ${e.localizedMessage}"
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            } else {
                                searchResults = emptyList()
                                isSearching = false
                            }
                        },
                        placeholder = { Text("Search @username or +1555...", color = TextMuted) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = EmeraldPrimary)
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                            } else if (searchInput.isNotEmpty()) {
                                IconButton(onClick = { searchInput = ""; searchResults = emptyList() }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (searchError != null) {
                        Text(
                            text = searchError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Matching Users (${searchResults.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianSurface)
                                        .clickable {
                                            coroutineScope.launch {
                                                val convId = authRepository.startConversationWithUser(user)
                                                showNewChatDialog = false
                                                onConversationClick(convId)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ArgusAvatar(name = user.displayName, size = 42.dp, isOnline = user.isOnline)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = user.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(
                                            text = if (user.username != null) "@${user.username}" else user.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldLight
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = "Message",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else if (!isSearching && searchInput.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.PersonSearch, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Your username is @${currentUser?.username ?: "not_set"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldLight
                                )
                                Text(
                                    text = "Share it with your friend so they can text you!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

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
                            Image(
                                painter = painterResource(id = R.drawable.argus_logo),
                                contentDescription = "Argus Logo",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        placeholder = { Text(text = "Search chats, contacts, or messages...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary.copy(alpha = 0.5f),
                            unfocusedBorderColor = ObsidianBorder,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
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
                    onClick = { showNewChatDialog = true },
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
                        onConversationClick = onConversationClick,
                        onNewChatClick = { showNewChatDialog = true }
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
                        onStartCall = onStartCallClick,
                        onAddContactClick = { showNewChatDialog = true }
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
    onConversationClick: (String) -> Unit,
    onNewChatClick: () -> Unit
) {
    if (conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "No active conversations", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Tap the button below to message a friend.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNewChatClick,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Chat", color = TextOnEmerald)
                }
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
    val timeStr = if (conversation.lastMessageTimestamp > 0) dateFormat.format(Date(conversation.lastMessageTimestamp)) else ""

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
                    color = TextPrimary
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
                    text = conversation.lastSnippet.ifEmpty { "Start a secure encrypted conversation" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (conversation.unreadCount > 0) TextPrimary else TextSecondary,
                    maxLines = 1,
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
    onStartCall: (Contact, CallType) -> Unit,
    onAddContactClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddContactClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = EmeraldPrimary)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("Add New Friend", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Search by @username or phone number", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved contacts yet.", color = TextMuted)
            }
        } else {
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
                                text = if (contact.username != null) "@${contact.username}" else contact.phoneNumber,
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
    }
}

@Composable
private fun FavoritesTabContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.StarBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "No pinned chats", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Text(text = "Long-press any chat to pin it here.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

@Composable
private fun CallsTabContent(calls: List<CallRecord>) {
    if (calls.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.AutoMirrored.Filled.PhoneMissed, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "No recent calls", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Text(text = "Make end-to-end encrypted voice and video calls.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
    } else {
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
                                tint = if (call.status == CallStatus.MISSED) Color(0xFFFF6B6B) else EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = call.status.name, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
