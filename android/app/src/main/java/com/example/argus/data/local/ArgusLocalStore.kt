package com.example.argus.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.argus.crypto.vault.ArgusVaultCipher
import com.example.argus.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArgusLocalStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "argus_secure_local.db"
        const val DATABASE_VERSION = 6

        private val json = Json { ignoreUnknownKeys = true }

        fun getDirectConversationId(userIdA: String, userIdB: String): String {
            val cleanA = userIdA.trim().lowercase()
            val cleanB = userIdB.trim().lowercase()
            val sorted = listOf(cleanA, cleanB).sorted()
            return "conv_${sorted[0]}_${sorted[1]}"
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    val conversationsFlow: StateFlow<List<Conversation>> = _conversationsFlow.asStateFlow()

    private val _messagesFlow = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesFlow: StateFlow<Map<String, List<Message>>> = _messagesFlow.asStateFlow()

    private val _contactsFlow = MutableStateFlow<List<Contact>>(emptyList())
    val contactsFlow: StateFlow<List<Contact>> = _contactsFlow.asStateFlow()

    private val _vaultItemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItemsFlow: StateFlow<List<VaultItem>> = _vaultItemsFlow.asStateFlow()

    private val _callsFlow = MutableStateFlow<List<CallRecord>>(emptyList())
    val callsFlow: StateFlow<List<CallRecord>> = _callsFlow.asStateFlow()

    private val _statusesFlow = MutableStateFlow<List<StatusItem>>(emptyList())
    val statusesFlow: StateFlow<List<StatusItem>> = _statusesFlow.asStateFlow()

    init {
        reloadAll()

        // Periodic background purge for disappearing messages and 24h statuses
        scope.launch {
            while (isActive) {
                delay(30_000)
                purgeExpiredDisappearingMessages()
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                participant_ids TEXT NOT NULL,
                last_snippet TEXT,
                last_message_timestamp INTEGER NOT NULL,
                unread_count INTEGER NOT NULL DEFAULT 0,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                is_locked INTEGER NOT NULL DEFAULT 0,
                disappearing_duration INTEGER,
                avatar_url TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                text TEXT NOT NULL,
                media_uri TEXT,
                media_type TEXT,
                media_size INTEGER DEFAULT 0,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                reply_to_id TEXT,
                reply_to_snippet TEXT,
                reactions_json TEXT,
                is_edited INTEGER DEFAULT 0,
                expires_at INTEGER,
                is_encrypted INTEGER DEFAULT 1,
                wire_payload_json TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                phone_number TEXT,
                username TEXT,
                avatar_url TEXT,
                identity_key TEXT,
                is_verified INTEGER DEFAULT 0,
                safety_number TEXT,
                is_online INTEGER DEFAULT 0,
                last_seen INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vault_items (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                content_or_path TEXT NOT NULL,
                file_size INTEGER DEFAULT 0,
                mime_type TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_locked INTEGER DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calls (
                id TEXT PRIMARY KEY,
                peer_id TEXT NOT NULL,
                peer_name TEXT NOT NULL,
                peer_avatar TEXT,
                call_type TEXT NOT NULL,
                status TEXT NOT NULL,
                duration INTEGER DEFAULT 0,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ratchet_sessions (
                peer_user_id TEXT PRIMARY KEY,
                session_data TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS statuses (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                user_name TEXT NOT NULL,
                avatar_url TEXT,
                caption TEXT NOT NULL,
                gradient_json TEXT,
                timestamp INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                media_uri TEXT,
                is_viewed INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // B-Tree Indexes for High Performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv_ts ON messages(conversation_id, timestamp DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_expires_at ON messages(expires_at);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_timestamp ON conversations(last_message_timestamp DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON contacts(user_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_created ON vault_items(created_at DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calls_timestamp ON calls(timestamp DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_statuses_exp ON statuses(expires_at DESC);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN wire_payload_json TEXT")
            } catch (e: Throwable) {
                // Column may already exist
            }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS statuses (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        user_name TEXT NOT NULL,
                        avatar_url TEXT,
                        caption TEXT NOT NULL,
                        gradient_json TEXT,
                        timestamp INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        media_uri TEXT,
                        is_viewed INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_statuses_exp ON statuses(expires_at DESC);")
            } catch (e: Throwable) {
                // Ignore
            }
        }
        onCreate(db)
    }

    fun purgeExpiredDisappearingMessages() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val deletedCount = writableDatabase.delete(
                    "messages",
                    "expires_at IS NOT NULL AND expires_at > 0 AND expires_at < ?",
                    arrayOf(now.toString())
                )
                val deletedStatuses = writableDatabase.delete(
                    "statuses",
                    "expires_at < ?",
                    arrayOf(now.toString())
                )
                if (deletedCount > 0) {
                    loadConversations()
                    val currentMap = _messagesFlow.value.toMutableMap()
                    for (convId in currentMap.keys) {
                        loadMessagesForConversation(convId)
                    }
                }
                if (deletedStatuses > 0) {
                    loadStatuses()
                }
            } catch (e: Exception) {
                android.util.Log.e("ArgusLocalStore", "purgeExpiredDisappearingMessages failed", e)
            }
        }
    }

    fun reloadAll() {
        scope.launch {
            purgeExpiredDisappearingMessages()
            loadConversations()
            loadAllMessages()
            loadContacts()
            loadVaultItems()
            loadCalls()
            loadStatuses()
        }
    }

    fun loadAllMessages(): Map<String, List<Message>> {
        val map = mutableMapOf<String, MutableList<Message>>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM messages ORDER BY timestamp ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val convId = it.getString(it.getColumnIndexOrThrow("conversation_id"))
                    val reactionsJson = it.getString(it.getColumnIndexOrThrow("reactions_json"))
                    val reactionsMap = try {
                        if (reactionsJson != null) json.decodeFromString<Map<String, String>>(reactionsJson) else emptyMap()
                    } catch (e: Exception) {
                        emptyMap()
                    }

                    val statusStr = it.getString(it.getColumnIndexOrThrow("status"))
                    val msgStatus = try {
                        MessageStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        MessageStatus.DELIVERED
                    }

                    val wireJsonIndex = it.getColumnIndex("wire_payload_json")
                    val wireJson = if (wireJsonIndex != -1 && !it.isNull(wireJsonIndex)) it.getString(wireJsonIndex) else null

                    val msg = Message(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        conversationId = convId,
                        senderId = it.getString(it.getColumnIndexOrThrow("sender_id")),
                        recipientId = it.getString(it.getColumnIndexOrThrow("recipient_id")),
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        mediaUri = it.getString(it.getColumnIndexOrThrow("media_uri")),
                        mediaType = it.getString(it.getColumnIndexOrThrow("media_type")),
                        mediaSizeBytes = it.getLong(it.getColumnIndexOrThrow("media_size")),
                        status = msgStatus,
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        replyToMessageId = it.getString(it.getColumnIndexOrThrow("reply_to_id")),
                        replyToSnippet = it.getString(it.getColumnIndexOrThrow("reply_to_snippet")),
                        reactions = reactionsMap,
                        isEdited = it.getInt(it.getColumnIndexOrThrow("is_edited")) == 1,
                        expiresAt = if (it.isNull(it.getColumnIndexOrThrow("expires_at"))) null else it.getLong(it.getColumnIndexOrThrow("expires_at")),
                        isEncrypted = it.getInt(it.getColumnIndexOrThrow("is_encrypted")) == 1,
                        wirePayloadJson = wireJson
                    )
                    map.getOrPut(convId) { mutableListOf() }.add(msg)
                }
            }
            _messagesFlow.value = map
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadAllMessages failed", e)
        }
        return map
    }

    // --- Conversations CRUD ---

    fun loadConversations(): List<Conversation> {
        val list = mutableListOf<Conversation>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM conversations ORDER BY is_pinned DESC, last_message_timestamp DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val participantIdsRaw = it.getString(it.getColumnIndexOrThrow("participant_ids"))
                    val participants = try {
                        json.decodeFromString<List<String>>(participantIdsRaw)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val typeStr = it.getString(it.getColumnIndexOrThrow("type"))
                    val convType = try {
                        ConversationType.valueOf(typeStr)
                    } catch (e: Exception) {
                        ConversationType.DIRECT
                    }

                    list.add(
                        Conversation(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            type = convType,
                            title = it.getString(it.getColumnIndexOrThrow("title")),
                            participantIds = participants,
                            lastSnippet = it.getString(it.getColumnIndexOrThrow("last_snippet")) ?: "",
                            lastMessageTimestamp = it.getLong(it.getColumnIndexOrThrow("last_message_timestamp")),
                            unreadCount = it.getInt(it.getColumnIndexOrThrow("unread_count")),
                            isPinned = it.getInt(it.getColumnIndexOrThrow("is_pinned")) == 1,
                            isArchived = it.getInt(it.getColumnIndexOrThrow("is_archived")) == 1,
                            isLocked = it.getInt(it.getColumnIndexOrThrow("is_locked")) == 1,
                            disappearingDurationSec = if (it.isNull(it.getColumnIndexOrThrow("disappearing_duration"))) null else it.getInt(it.getColumnIndexOrThrow("disappearing_duration")),
                            avatarUrl = it.getString(it.getColumnIndexOrThrow("avatar_url"))
                        )
                    )
                }
            }
            _conversationsFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadConversations failed", e)
        }
        return list
    }

    fun upsertConversation(conv: Conversation) {
        try {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT OR REPLACE INTO conversations (id, type, title, participant_ids, last_snippet, last_message_timestamp, unread_count, is_pinned, is_archived, is_locked, disappearing_duration, avatar_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    conv.id,
                    conv.type.name,
                    conv.title,
                    json.encodeToString(conv.participantIds),
                    conv.lastSnippet,
                    conv.lastMessageTimestamp,
                    conv.unreadCount,
                    if (conv.isPinned) 1 else 0,
                    if (conv.isArchived) 1 else 0,
                    if (conv.isLocked) 1 else 0,
                    conv.disappearingDurationSec,
                    conv.avatarUrl
                )
            )
            loadConversations()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "upsertConversation failed: ${e.message}", e)
        }
    }

    fun resetUnreadCount(conversationId: String) {
        try {
            val db = writableDatabase
            db.execSQL("UPDATE conversations SET unread_count = 0 WHERE id = ?", arrayOf(conversationId))
            loadConversations()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "resetUnreadCount failed", e)
        }
    }

    // --- Messages CRUD ---

    fun loadMessagesForConversation(convId: String): List<Message> {
        val list = mutableListOf<Message>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC",
                arrayOf(convId)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val reactionsJson = it.getString(it.getColumnIndexOrThrow("reactions_json"))
                    val reactionsMap = try {
                        if (reactionsJson != null) json.decodeFromString<Map<String, String>>(reactionsJson) else emptyMap()
                    } catch (e: Exception) {
                        emptyMap()
                    }

                    val statusStr = it.getString(it.getColumnIndexOrThrow("status"))
                    val msgStatus = try {
                        MessageStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        MessageStatus.DELIVERED
                    }

                    val wireJsonIndex = it.getColumnIndex("wire_payload_json")
                    val wireJson = if (wireJsonIndex != -1 && !it.isNull(wireJsonIndex)) it.getString(wireJsonIndex) else null

                    list.add(
                        Message(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            conversationId = it.getString(it.getColumnIndexOrThrow("conversation_id")),
                            senderId = it.getString(it.getColumnIndexOrThrow("sender_id")),
                            recipientId = it.getString(it.getColumnIndexOrThrow("recipient_id")),
                            text = it.getString(it.getColumnIndexOrThrow("text")),
                            mediaUri = it.getString(it.getColumnIndexOrThrow("media_uri")),
                            mediaType = it.getString(it.getColumnIndexOrThrow("media_type")),
                            mediaSizeBytes = it.getLong(it.getColumnIndexOrThrow("media_size")),
                            status = msgStatus,
                            timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                            replyToMessageId = it.getString(it.getColumnIndexOrThrow("reply_to_id")),
                            replyToSnippet = it.getString(it.getColumnIndexOrThrow("reply_to_snippet")),
                            reactions = reactionsMap,
                            isEdited = it.getInt(it.getColumnIndexOrThrow("is_edited")) == 1,
                            expiresAt = if (it.isNull(it.getColumnIndexOrThrow("expires_at"))) null else it.getLong(it.getColumnIndexOrThrow("expires_at")),
                            isEncrypted = it.getInt(it.getColumnIndexOrThrow("is_encrypted")) == 1,
                            wirePayloadJson = wireJson
                        )
                    )
                }
            }
            val currentMap = _messagesFlow.value.toMutableMap()
            currentMap[convId] = list
            _messagesFlow.value = currentMap
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadMessagesForConversation failed", e)
        }
        return list
    }

    fun loadQueuedMessages(): List<Message> {
        val list = mutableListOf<Message>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM messages WHERE status = 'QUEUED' ORDER BY timestamp ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val reactionsJson = it.getString(it.getColumnIndexOrThrow("reactions_json"))
                    val reactionsMap = try {
                        if (reactionsJson != null) json.decodeFromString<Map<String, String>>(reactionsJson) else emptyMap()
                    } catch (e: Exception) {
                        emptyMap()
                    }

                    val wireJsonIndex = it.getColumnIndex("wire_payload_json")
                    val wireJson = if (wireJsonIndex != -1 && !it.isNull(wireJsonIndex)) it.getString(wireJsonIndex) else null

                    list.add(
                        Message(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            conversationId = it.getString(it.getColumnIndexOrThrow("conversation_id")),
                            senderId = it.getString(it.getColumnIndexOrThrow("sender_id")),
                            recipientId = it.getString(it.getColumnIndexOrThrow("recipient_id")),
                            text = it.getString(it.getColumnIndexOrThrow("text")),
                            mediaUri = it.getString(it.getColumnIndexOrThrow("media_uri")),
                            mediaType = it.getString(it.getColumnIndexOrThrow("media_type")),
                            mediaSizeBytes = it.getLong(it.getColumnIndexOrThrow("media_size")),
                            status = MessageStatus.QUEUED,
                            timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                            replyToMessageId = it.getString(it.getColumnIndexOrThrow("reply_to_id")),
                            replyToSnippet = it.getString(it.getColumnIndexOrThrow("reply_to_snippet")),
                            reactions = reactionsMap,
                            isEdited = it.getInt(it.getColumnIndexOrThrow("is_edited")) == 1,
                            expiresAt = if (it.isNull(it.getColumnIndexOrThrow("expires_at"))) null else it.getLong(it.getColumnIndexOrThrow("expires_at")),
                            isEncrypted = it.getInt(it.getColumnIndexOrThrow("is_encrypted")) == 1,
                            wirePayloadJson = wireJson
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadQueuedMessages failed", e)
        }
        return list
    }

    fun saveMessage(msg: Message, currentUserId: String? = null) {
        try {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT OR REPLACE INTO messages (id, conversation_id, sender_id, recipient_id, text, media_uri, media_type, media_size, status, timestamp, reply_to_id, reply_to_snippet, reactions_json, is_edited, expires_at, is_encrypted, wire_payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    msg.id,
                    msg.conversationId,
                    msg.senderId,
                    msg.recipientId,
                    msg.text,
                    msg.mediaUri,
                    msg.mediaType,
                    msg.mediaSizeBytes,
                    msg.status.name,
                    msg.timestamp,
                    msg.replyToMessageId,
                    msg.replyToSnippet,
                    json.encodeToString(msg.reactions),
                    if (msg.isEdited) 1 else 0,
                    msg.expiresAt,
                    if (msg.isEncrypted) 1 else 0,
                    msg.wirePayloadJson
                )
            )

            val existingConv = loadConversations().firstOrNull { it.id == msg.conversationId }
            val convSnippet = if (msg.mediaType != null) "[${msg.mediaType}] ${msg.text}" else msg.text

            val myId = currentUserId ?: (if (msg.senderId == "me") "me" else null)
            val isIncoming = myId != null && !msg.senderId.equals(myId, ignoreCase = true)
            val shouldIncrementUnread = isIncoming && msg.status != MessageStatus.READ

            if (existingConv == null) {
                val peerId = if (myId != null && msg.senderId.equals(myId, ignoreCase = true)) msg.recipientId else msg.senderId
                val contact = loadContacts().firstOrNull { it.userId.equals(peerId, ignoreCase = true) }
                val title = contact?.displayName ?: "Direct Chat"
                val avatarUrl = contact?.avatarUrl
                val initialUnread = if (shouldIncrementUnread) 1 else 0

                db.execSQL(
                    """
                    INSERT INTO conversations (id, type, title, participant_ids, last_snippet, last_message_timestamp, unread_count, is_pinned, is_archived, is_locked, disappearing_duration, avatar_url)
                    VALUES (?, 'DIRECT', ?, ?, ?, ?, ?, 0, 0, 0, NULL, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(msg.conversationId, title, json.encodeToString(listOf(peerId)), convSnippet, msg.timestamp, initialUnread, avatarUrl)
                )
            } else {
                val newUnread = if (shouldIncrementUnread) existingConv.unreadCount + 1 else existingConv.unreadCount
                db.execSQL(
                    "UPDATE conversations SET last_snippet = ?, last_message_timestamp = ?, unread_count = ? WHERE id = ?",
                    arrayOf<Any?>(convSnippet, msg.timestamp, newUnread, msg.conversationId)
                )
            }
            loadMessagesForConversation(msg.conversationId)
            loadConversations()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "saveMessage failed: ${e.message}", e)
        }
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        try {
            val db = writableDatabase
            db.execSQL("UPDATE messages SET status = ? WHERE id = ?", arrayOf(status.name, messageId))
            val currentMap = _messagesFlow.value.toMutableMap()
            for ((convId, list) in currentMap) {
                val idx = list.indexOfFirst { it.id == messageId }
                if (idx != -1) {
                    loadMessagesForConversation(convId)
                    break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "updateMessageStatus failed", e)
        }
    }

    fun deleteMessage(messageId: String, convId: String) {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM messages WHERE id = ?", arrayOf(messageId))
            loadMessagesForConversation(convId)
            loadConversations()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "deleteMessage failed", e)
        }
    }

    // --- Contacts CRUD ---

    fun loadContacts(): List<Contact> {
        val list = mutableListOf<Contact>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM contacts ORDER BY display_name ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        Contact(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            userId = it.getString(it.getColumnIndexOrThrow("user_id")),
                            displayName = it.getString(it.getColumnIndexOrThrow("display_name")),
                            phoneNumber = it.getString(it.getColumnIndexOrThrow("phone_number")),
                            username = it.getString(it.getColumnIndexOrThrow("username")),
                            avatarUrl = it.getString(it.getColumnIndexOrThrow("avatar_url")),
                            identityKeyBase64 = it.getString(it.getColumnIndexOrThrow("identity_key")) ?: "",
                            isVerified = it.getInt(it.getColumnIndexOrThrow("is_verified")) == 1,
                            safetyNumber = it.getString(it.getColumnIndexOrThrow("safety_number")),
                            isOnline = it.getInt(it.getColumnIndexOrThrow("is_online")) == 1,
                            lastSeen = it.getLong(it.getColumnIndexOrThrow("last_seen"))
                        )
                    )
                }
            }
            _contactsFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadContacts failed", e)
        }
        return list
    }

    fun upsertContact(contact: Contact) {
        try {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT OR REPLACE INTO contacts (id, user_id, display_name, phone_number, username, avatar_url, identity_key, is_verified, safety_number, is_online, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    contact.id,
                    contact.userId,
                    contact.displayName,
                    contact.phoneNumber ?: "",
                    contact.username ?: "",
                    contact.avatarUrl,
                    contact.identityKeyBase64,
                    if (contact.isVerified) 1 else 0,
                    contact.safetyNumber,
                    if (contact.isOnline) 1 else 0,
                    contact.lastSeen
                )
            )
            loadContacts()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "upsertContact failed: ${e.message}", e)
        }
    }

    // --- Vault CRUD ---

    fun loadVaultItems(): List<VaultItem> {
        val list = mutableListOf<VaultItem>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM vault_items ORDER BY updated_at DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val typeStr = it.getString(it.getColumnIndexOrThrow("type"))
                    val vaultType = try {
                        VaultItemType.valueOf(typeStr)
                    } catch (e: Exception) {
                        VaultItemType.FILE
                    }

                    list.add(
                        VaultItem(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            title = it.getString(it.getColumnIndexOrThrow("title")),
                            type = vaultType,
                            contentOrPath = it.getString(it.getColumnIndexOrThrow("content_or_path")),
                            fileSizeBytes = it.getLong(it.getColumnIndexOrThrow("file_size")),
                            mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")),
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                            updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                            isLocked = it.getInt(it.getColumnIndexOrThrow("is_locked")) == 1
                        )
                    )
                }
            }
            _vaultItemsFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadVaultItems failed", e)
        }
        return list
    }

    fun saveVaultItem(item: VaultItem) {
        try {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT OR REPLACE INTO vault_items (id, title, type, content_or_path, file_size, mime_type, created_at, updated_at, is_locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    item.id,
                    item.title,
                    item.type.name,
                    item.contentOrPath,
                    item.fileSizeBytes,
                    item.mimeType,
                    item.createdAt,
                    item.updatedAt,
                    if (item.isLocked) 1 else 0
                )
            )
            loadVaultItems()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "saveVaultItem failed: ${e.message}", e)
        }
    }

    fun deleteVaultItem(id: String) {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM vault_items WHERE id = ?", arrayOf(id))
            loadVaultItems()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "deleteVaultItem failed: ${e.message}", e)
        }
    }

    // --- Calls CRUD ---

    fun loadCalls(): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM calls ORDER BY timestamp DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val callTypeStr = it.getString(it.getColumnIndexOrThrow("call_type"))
                    val callType = try {
                        CallType.valueOf(callTypeStr)
                    } catch (e: Exception) {
                        CallType.VOICE
                    }

                    val statusStr = it.getString(it.getColumnIndexOrThrow("status"))
                    val callStatus = try {
                        CallStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        CallStatus.ENDED
                    }

                    list.add(
                        CallRecord(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            peerId = it.getString(it.getColumnIndexOrThrow("peer_id")),
                            peerName = it.getString(it.getColumnIndexOrThrow("peer_name")),
                            peerAvatar = it.getString(it.getColumnIndexOrThrow("peer_avatar")),
                            callType = callType,
                            status = callStatus,
                            durationSec = it.getInt(it.getColumnIndexOrThrow("duration")),
                            timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
            _callsFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadCalls failed", e)
        }
        return list
    }

    fun saveCall(call: CallRecord) {
        try {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT OR REPLACE INTO calls (id, peer_id, peer_name, peer_avatar, call_type, status, duration, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    call.id,
                    call.peerId,
                    call.peerName,
                    call.peerAvatar,
                    call.callType.name,
                    call.status.name,
                    call.durationSec,
                    call.timestamp
                )
            )
            loadCalls()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "saveCall failed: ${e.message}", e)
        }
    }

    // --- Double Ratchet Sessions Persistence (Hardware-Encrypted via ArgusVaultCipher) ---

    fun saveRatchetSession(peerUserId: String, serializedState: String) {
        try {
            val encryptedBlob = ArgusVaultCipher.encryptBytes(serializedState.toByteArray(Charsets.UTF_8))
            val storedPayload = "${encryptedBlob.ivBase64}:${encryptedBlob.ciphertextBase64}"
            val db = writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO ratchet_sessions (peer_user_id, session_data, updated_at) VALUES (?, ?, ?)",
                arrayOf<Any?>(peerUserId, storedPayload, System.currentTimeMillis())
            )
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "saveRatchetSession failed: ${e.message}", e)
        }
    }

    fun getRatchetSession(peerUserId: String): String? {
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT session_data FROM ratchet_sessions WHERE peer_user_id = ?", arrayOf(peerUserId))
            cursor.use {
                if (it.moveToFirst()) {
                    val rawPayload = it.getString(0) ?: return null
                    if (rawPayload.contains(":")) {
                        val parts = rawPayload.split(":", limit = 2)
                        val decrypted = ArgusVaultCipher.decryptBytes(parts[0], parts[1])
                        return String(decrypted, Charsets.UTF_8)
                    } else {
                        // Fallback unencrypted legacy format
                        return rawPayload
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "getRatchetSession failed: ${e.message}", e)
        }
        return null
    }

    fun deleteRatchetSession(peerUserId: String) {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM ratchet_sessions WHERE peer_user_id = ?", arrayOf(peerUserId))
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "deleteRatchetSession failed: ${e.message}", e)
        }
    }

    // --- Statuses Persistence ---

    fun saveStatus(item: StatusItem) {
        try {
            val db = writableDatabase
            val gradientJson = json.encodeToString(item.backgroundGradientHex)
            db.execSQL(
                """
                INSERT OR REPLACE INTO statuses (
                    id, user_id, user_name, avatar_url, caption, gradient_json, timestamp, expires_at, media_uri, is_viewed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    item.id,
                    item.userId,
                    item.userName,
                    item.avatarUrl,
                    item.caption,
                    gradientJson,
                    item.timestamp,
                    item.expiresAt,
                    item.mediaUri,
                    if (item.isViewed) 1 else 0
                )
            )
            loadStatuses()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "saveStatus failed: ${e.message}", e)
        }
    }

    fun loadStatuses(): List<StatusItem> {
        val list = mutableListOf<StatusItem>()
        try {
            val db = readableDatabase
            val now = System.currentTimeMillis()
            val cursor = db.rawQuery(
                "SELECT id, user_id, user_name, avatar_url, caption, gradient_json, timestamp, expires_at, media_uri, is_viewed FROM statuses WHERE expires_at > ? ORDER BY timestamp DESC",
                arrayOf(now.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    val gradientRaw = it.getString(5)
                    val gradientList = if (!gradientRaw.isNullOrBlank()) {
                        try {
                            json.decodeFromString<List<String>>(gradientRaw)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()

                    list.add(
                        StatusItem(
                            id = it.getString(0),
                            userId = it.getString(1),
                            userName = it.getString(2),
                            avatarUrl = it.getString(3),
                            caption = it.getString(4),
                            backgroundGradientHex = gradientList,
                            timestamp = it.getLong(6),
                            expiresAt = it.getLong(7),
                            mediaUri = it.getString(8),
                            isViewed = it.getInt(9) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "loadStatuses failed: ${e.message}", e)
        }
        _statusesFlow.value = list
        return list
    }

    fun markStatusViewed(statusId: String) {
        try {
            val db = writableDatabase
            db.execSQL("UPDATE statuses SET is_viewed = 1 WHERE id = ?", arrayOf(statusId))
            loadStatuses()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "markStatusViewed failed: ${e.message}", e)
        }
    }

    fun deleteStatus(statusId: String) {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM statuses WHERE id = ?", arrayOf(statusId))
            loadStatuses()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "deleteStatus failed: ${e.message}", e)
        }
    }

    fun wipeAllData() {
        try {
            val db = writableDatabase
            db.execSQL("DELETE FROM messages")
            db.execSQL("DELETE FROM conversations")
            db.execSQL("DELETE FROM contacts")
            db.execSQL("DELETE FROM vault_items")
            db.execSQL("DELETE FROM calls")
            db.execSQL("DELETE FROM ratchet_sessions")
            db.execSQL("DELETE FROM statuses")
            reloadAll()
        } catch (e: Exception) {
            android.util.Log.e("ArgusLocalStore", "wipeAllData failed: ${e.message}", e)
        }
    }
}
