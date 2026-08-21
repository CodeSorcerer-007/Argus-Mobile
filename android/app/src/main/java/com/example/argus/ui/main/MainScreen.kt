package com.example.argus.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argus.R
import com.example.argus.core.permission.ArgusPermissionType
import com.example.argus.core.permission.PermissionManager
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.model.CallRecord
import com.example.argus.data.model.CallStatus
import com.example.argus.data.model.CallType
import com.example.argus.data.model.Contact
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.User
import com.example.argus.data.repository.AuthRepository
import com.example.argus.theme.*
import com.example.argus.ui.components.ArgusAvatar
import com.example.argus.ui.components.ArgusPermissionRationaleDialog
import com.example.argus.ui.components.ArgusPulseBadge
import com.example.argus.ui.status.EphemeralStatusItem
import com.example.argus.ui.status.StatusScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class MainTab(val title: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Default.ChatBubble),
    UPDATES("Updates", Icons.Default.CircleNotifications),
    CALLS("Calls", Icons.Default.Phone)
}

enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    FAVORITES("Favorites"),
    GROUPS("Groups")
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
    onSettingsClick: () -> Unit,
    onAiAssistantClick: () -> Unit,
    onViewStatus: (EphemeralStatusItem) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }
    var selectedFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeRationalePermission by remember { mutableStateOf<ArgusPermissionType?>(null) }
    var pendingCallTarget by remember { mutableStateOf<Pair<Contact, CallType>?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser by authRepository.currentUser.collectAsState()

    // Call permissions launcher
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingCallTarget
        if (pending != null) {
            val audioOk = grants[android.Manifest.permission.RECORD_AUDIO] ?: PermissionManager.hasAudioPermission(context)
            val cameraOk = grants[android.Manifest.permission.CAMERA] ?: PermissionManager.hasCameraPermission(context)
            if (pending.second == CallType.VOICE && audioOk) {
                onStartCallClick(pending.first, pending.second)
            } else if (pending.second == CallType.VIDEO && audioOk && cameraOk) {
                onStartCallClick(pending.first, pending.second)
            } else if (!audioOk) {
                activeRationalePermission = ArgusPermissionType.AUDIO
            } else {
                activeRationalePermission = ArgusPermissionType.CAMERA
            }
            pendingCallTarget = null
        }
    }

    fun initiateCallWithPermissionCheck(contact: Contact, type: CallType) {
        if (type == CallType.VOICE) {
            if (PermissionManager.hasAudioPermission(context)) {
                onStartCallClick(contact, type)
            } else {
                pendingCallTarget = contact to type
                callPermissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
            }
        } else {
            if (PermissionManager.hasAudioPermission(context) && PermissionManager.hasCameraPermission(context)) {
                onStartCallClick(contact, type)
            } else {
                pendingCallTarget = contact to type
                callPermissionLauncher.launch(PermissionManager.getCallPermissions())
            }
        }
    }

    // Active Permission Rationale Dialog
    if (activeRationalePermission != null) {
        ArgusPermissionRationaleDialog(
            permissionType = activeRationalePermission!!,
            onGrantClick = {
                val perm = activeRationalePermission!!
                activeRationalePermission = null
                when (perm) {
                    ArgusPermissionType.AUDIO -> callPermissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                    ArgusPermissionType.CAMERA -> callPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
                    else -> callPermissionLauncher.launch(PermissionManager.getCallPermissions())
                }
            },
            onDismiss = { activeRationalePermission = null },
            onOpenSettingsClick = {
                activeRationalePermission = null
                PermissionManager.openAppSettings(context)
            }
        )
    }

    // Smart Back Button Handler (WhatsApp / Telegram standard)
    BackHandler {
        if (activeRationalePermission != null) {
            activeRationalePermission = null
        } else if (showNewChatDialog) {
            showNewChatDialog = false
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        } else if (selectedTab != MainTab.CHATS) {
            selectedTab = MainTab.CHATS
        }
    }

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
                    Text("New Secure Chat", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 450.dp)
                ) {
                    Text(
                        text = "Find friends on Argus by searching their @username or international phone number (+1...).",
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
                                    delay(350)
                                    try {
                                        val results = if (query.trim().startsWith("+")) {
                                            val found = authRepository.findUserByPhone(query.trim())
                                            if (found != null) listOf(found) else emptyList()
                                        } else {
                                            authRepository.searchUsers(query.trim().removePrefix("@"))
                                        }
                                        searchResults = results.filter { it.id != currentUser?.id }
                                        if (searchResults.isEmpty()) {
                                            searchError = "No user found for \"$query\""
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
                                                val myId = currentUser?.id ?: "me"
                                                val convId = try {
                                                    authRepository.startConversationWithUser(user)
                                                } catch (e: Exception) {
                                                    ArgusLocalStore.getDirectConversationId(myId, user.id)
                                                }
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
                                            text = if (user.username.isNotBlank()) "@${user.username}" else (user.phoneNumber ?: ""),
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
                    } else if (!isSearching && searchInput.isNotBlank()) {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        val rawId = searchInput.trim().removePrefix("@")
                                        val directUser = User(
                                            id = if (rawId.startsWith("user_") || rawId.startsWith("u_")) rawId else "u_$rawId",
                                            username = rawId,
                                            displayName = searchInput.trim()
                                        )
                                        val myId = currentUser?.id ?: "me"
                                        val convId = try {
                                            authRepository.startConversationWithUser(directUser)
                                        } catch (e: Exception) {
                                            ArgusLocalStore.getDirectConversationId(myId, directUser.id)
                                        }
                                        showNewChatDialog = false
                                        onConversationClick(convId)
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.ChatBubble, contentDescription = null, tint = EmeraldPrimary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Message \"$searchInput\" directly", color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(text = "Start E2EE Signal Double Ratchet session", color = EmeraldLight, style = MaterialTheme.typography.labelSmall)
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
                                    text = "Your username is @${currentUser?.username ?: "user"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldLight
                                )
                                Text(
                                    text = "Share it with contacts to start messaging!",
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

    // Filter conversations & contacts (M-5)
    val filteredConversations = remember(conversations, searchQuery, selectedFilter) {
        conversations.filter { conv ->
            val matchesSearch = searchQuery.isEmpty() ||
                    conv.title.contains(searchQuery, ignoreCase = true) ||
                    conv.lastSnippet.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                ChatFilter.ALL -> true
                ChatFilter.UNREAD -> conv.unreadCount > 0
                ChatFilter.FAVORITES -> conv.isPinned
                ChatFilter.GROUPS -> conv.type == com.example.argus.data.model.ConversationType.GROUP
            }

            matchesSearch && matchesFilter
        }
    }

    val matchingContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    (it.username?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.phoneNumber?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            Surface(
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Bar (WhatsApp / Telegram style)
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
                            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                            }
                            IconButton(onClick = onAiAssistantClick) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI Assistant", tint = CyanAccent)
                            }

                            // 3-Dots Overflow Menu
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.background(ObsidianCard)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New Chat / Group", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = EmeraldPrimary) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showNewChatDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("AI Assistant", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onAiAssistantClick()
                                        }
                                    )
                                    HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp)
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSettingsClick()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Search Bar (Animated Expandable)
                    AnimatedVisibility(visible = isSearchExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                placeholder = { Text(text = "Search chats, contacts, messages...", fontSize = 13.sp, color = TextMuted) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
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
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianCard,
                                    unfocusedContainerColor = ObsidianCard,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }

                    // WhatsApp Style Category Filter Chips (All, Unread, Favorites, Groups)
                    if (selectedTab == MainTab.CHATS) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ChatFilter.values()) { filter ->
                                val isSelected = selectedFilter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFilter = filter },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = ObsidianCard,
                                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                        labelColor = TextSecondary,
                                        selectedLabelColor = EmeraldLight
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = ObsidianBorder,
                                        selectedBorderColor = EmeraldPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                MainTab.CHATS -> {
                    FloatingActionButton(
                        onClick = { showNewChatDialog = true },
                        containerColor = EmeraldPrimary,
                        contentColor = TextOnEmerald,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "New Chat", modifier = Modifier.size(24.dp))
                    }
                }
                MainTab.CALLS -> {
                    FloatingActionButton(
                        onClick = {
                            val firstContact = contacts.firstOrNull()
                            if (firstContact != null) {
                                initiateCallWithPermissionCheck(firstContact, CallType.VOICE)
                            } else {
                                showNewChatDialog = true
                            }
                        },
                        containerColor = EmeraldPrimary,
                        contentColor = TextOnEmerald,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddIcCall, contentDescription = "New Call", modifier = Modifier.size(24.dp))
                    }
                }
                MainTab.UPDATES -> {
                    // Status creation is accessible via the StatusScreen and stories header
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    val unreadChatsCount = conversations.sumOf { it.unreadCount }
                    val missedCallsCount = calls.count { it.status == CallStatus.MISSED }

                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == MainTab.CHATS && unreadChatsCount > 0) {
                                        Badge(containerColor = EmeraldPrimary, contentColor = TextOnEmerald) {
                                            Text(unreadChatsCount.toString())
                                        }
                                    } else if (tab == MainTab.CALLS && missedCallsCount > 0) {
                                        Badge(containerColor = MissedCallRed, contentColor = Color.White) {
                                            Text(missedCallsCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(imageVector = tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextOnEmerald,
                            selectedTextColor = EmeraldLight,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = EmeraldPrimary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                MainTab.CHATS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // WhatsApp Status Stories Carousel Header
                        item {
                            StoriesHorizontalCarousel(
                                currentUser = currentUser,
                                onAddStatusClick = { selectedTab = MainTab.UPDATES },
                                onStoryClick = {
                                    selectedTab = MainTab.UPDATES
                                }
                            )
                            HorizontalDivider(color = ObsidianBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                        }

                        // Matching Contacts Section in Search Mode
                        if (searchQuery.isNotEmpty() && matchingContacts.isNotEmpty()) {
                            item {
                                Text(
                                    text = "CONTACTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(matchingContacts, key = { "contact_${it.id}" }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onContactClick(contact) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ArgusAvatar(name = contact.displayName, size = 46.dp, isOnline = contact.isOnline)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = contact.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(
                                            text = if (!contact.username.isNullOrBlank()) "@${contact.username}" else (contact.phoneNumber ?: "E2EE Contact"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldLight
                                        )
                                    }
                                    Icon(imageVector = Icons.Default.ChatBubble, contentDescription = "Chat", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                                }
                                HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
                            }
                            item {
                                Text(
                                    text = "CHATS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Conversations List
                        if (filteredConversations.isEmpty() && matchingContacts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "No results match \"$searchQuery\"" else "No conversations yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap the green button below to start a secure chat.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredConversations, key = { it.id }) { conv ->
                                ChatListItemWhatsAppStyle(
                                    conversation = conv,
                                    onClick = { onConversationClick(conv.id) }
                                )
                                HorizontalDivider(
                                    color = ObsidianBorder.copy(alpha = 0.4f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 76.dp)
                                )
                            }
                        }
                    }
                }

                MainTab.UPDATES -> {
                    StatusScreen(
                        currentUser = currentUser,
                        localStore = authRepository.localStore,
                        onViewStatus = onViewStatus,
                        onCreateStatus = {}
                    )
                }

                MainTab.CALLS -> {
                    CallsTabWhatsAppStyle(
                        calls = calls,
                        contacts = contacts,
                        onStartCall = { contact, type -> initiateCallWithPermissionCheck(contact, type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoriesHorizontalCarousel(
    currentUser: User?,
    onAddStatusClick: () -> Unit,
    onStoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // My Status Item
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onAddStatusClick() }
                .width(64.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ArgusAvatar(name = currentUser?.displayName ?: "Me", size = 52.dp)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary)
                        .border(1.5.dp, ObsidianSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = TextOnEmerald, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "My status", style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun ChatListItemWhatsAppStyle(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val formattedTime = remember(conversation.lastMessageTimestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(conversation.lastMessageTimestamp))
    }

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
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0) EmeraldLight else TextSecondary,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Delivery / Read Receipt Tick (✓✓)
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Read",
                        tint = ReadTickBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = conversation.lastSnippet.ifBlank { "🔒 End-to-end encrypted session" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (conversation.unreadCount > 0) TextPrimary else TextSecondary,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Icon(imageVector = Icons.Default.PushPin, contentDescription = "Pinned", tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (conversation.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextOnEmerald,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsTabWhatsAppStyle(
    calls: List<CallRecord>,
    contacts: List<Contact>,
    onStartCall: (Contact, CallType) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Start Call Action Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .clickable {
                        val firstContact = contacts.firstOrNull()
                        if (firstContact != null) {
                            onStartCall(firstContact, CallType.VOICE)
                        } else {
                            Toast.makeText(context, "Search user to start encrypted call", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = TextOnEmerald)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(text = "Start a new call", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Encrypted peer-to-peer HD voice & video", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        item {
            Text(
                text = "RECENT CALLS",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        if (calls.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PhoneMissed, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No recent calls", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Your encrypted voice and video calls will appear here.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        } else {
            items(calls) { call ->
                val formattedTime = remember(call.timestamp, call.durationSec) {
                    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    val timeStr = sdf.format(Date(call.timestamp))
                    if (call.durationSec > 0) "$timeStr (${call.durationSec / 60}m ${call.durationSec % 60}s)" else timeStr
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .clickable {
                            val contact = contacts.firstOrNull { it.userId == call.peerId } ?: Contact(
                                id = "c_${call.peerId}",
                                userId = call.peerId,
                                displayName = call.peerName,
                                phoneNumber = "+1 555 000 0000"
                            )
                            onStartCall(contact, call.callType)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArgusAvatar(name = call.peerName, size = 46.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = call.peerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (call.status == CallStatus.MISSED) MissedCallRed else TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val callIcon = when (call.status) {
                                    CallStatus.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                                    CallStatus.ENDED -> Icons.AutoMirrored.Filled.CallReceived
                                    CallStatus.CONNECTED -> Icons.AutoMirrored.Filled.CallMade
                                    else -> Icons.AutoMirrored.Filled.CallReceived
                                }
                                val iconTint = if (call.status == CallStatus.MISSED) MissedCallRed else IncomingCallGreen
                                Icon(imageVector = callIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val contact = contacts.firstOrNull { it.userId == call.peerId } ?: Contact(
                                id = "c_${call.peerId}",
                                userId = call.peerId,
                                displayName = call.peerName,
                                phoneNumber = "+1 555 000 0000"
                            )
                            onStartCall(contact, call.callType)
                        }
                    ) {
                        Icon(
                            imageVector = if (call.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = "Call",
                            tint = EmeraldPrimary
                        )
                    }
                }
            }
        }
    }
}
