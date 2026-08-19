package com.example.argus.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.argus.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArgusLocalStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "argus_secure_local.db"
        const val DATABASE_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true }
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

    init {
        reloadAll()
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
                is_encrypted INTEGER DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                phone_number TEXT NOT NULL,
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

        // B-Tree Indexes for Microsecond Performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv_ts ON messages(conversation_id, timestamp DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_expires_at ON messages(expires_at);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_timestamp ON conversations(last_message_timestamp DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON contacts(user_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_created ON vault_items(created_at DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calls_timestamp ON calls(timestamp DESC);")

        // Seed initial sample chats for demonstration
        seedInitialDemoData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations
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
                if (deletedCount > 0) {
                    loadConversations()
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
            loadContacts()
            loadVaultItems()
            loadCalls()
        }
    }

    private fun seedInitialDemoData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        // Seed default contacts
        db.execSQL(
            """
            INSERT OR IGNORE INTO contacts (id, user_id, display_name, phone_number, username, identity_key, is_verified, safety_number, is_online, last_seen)
            VALUES 
            ('c1', 'u_elena', 'Elena Rostova', '+15551234567', 'elena_r', 'id_key_elena', 1, '48201 92837 10928 38472 91827 36452 91827 36452 10928 38472 48201 92837', 1, $now),
            ('c2', 'u_marcus', 'Marcus Vance (Security)', '+15559876543', 'mvance', 'id_key_marcus', 1, '19283 83746 56473 82910 29384 75647 19283 83746 56473 82910 29384 75647', 0, ${now - 1800000}),
            ('c3', 'u_sophia', 'Sophia Chen', '+15553334444', 'sophia_c', 'id_key_sophia', 0, '99281 38472 10293 84756 10293 84756 99281 38472 10293 84756 10293 84756', 1, $now)
            """.trimIndent()
        )

        // Seed default conversations
        db.execSQL(
            """
            INSERT OR IGNORE INTO conversations (id, type, title, participant_ids, last_snippet, last_message_timestamp, unread_count, is_pinned, is_archived, is_locked)
            VALUES 
            ('conv_elena', 'DIRECT', 'Elena Rostova', '["u_elena"]', 'The Signal Double Ratchet session is verified.', $now, 0, 1, 0, 0),
            ('conv_marcus', 'DIRECT', 'Marcus Vance (Security)', '["u_marcus"]', 'Encrypted file report sent. Check the Vault.', ${now - 3600000}, 1, 0, 0, 0),
            ('conv_group_dev', 'GROUP', 'Argus Core Architecture', '["u_elena", "u_marcus", "u_sophia"]', 'Zero-knowledge verification is passing all tests.', ${now - 7200000}, 0, 0, 0, 0)
            """.trimIndent()
        )

        // Seed messages
        db.execSQL(
            """
            INSERT OR IGNORE INTO messages (id, conversation_id, sender_id, recipient_id, text, status, timestamp)
            VALUES 
            ('m1', 'conv_elena', 'u_elena', 'me', 'Hey! Glad we switched to Argus for private messaging.', 'READ', ${now - 600000}),
            ('m2', 'conv_elena', 'me', 'u_elena', 'The Signal Double Ratchet session is verified.', 'READ', ${now - 300000}),
            ('m3', 'conv_marcus', 'u_marcus', 'me', 'Encrypted file report sent. Check the Vault.', 'DELIVERED', ${now - 3600000})
            """.trimIndent()
        )

        // Seed vault demo items
        db.execSQL(
            """
            INSERT OR IGNORE INTO vault_items (id, title, type, content_or_path, file_size, mime_type, created_at, updated_at)
            VALUES 
            ('v1', 'Argus Master Recovery Key Phrase', 'NOTE', 'quantum shield obsidian crystal ratchet zero knowledge hardware biometric', 64, 'text/plain', $now, $now),
            ('v2', 'Project Cryptographic Audit 2026.pdf', 'FILE', 'encrypted_vault_audit_blob.enc', 2457600, 'application/pdf', $now, $now)
            """.trimIndent()
        )

        // Seed calls
        db.execSQL(
            """
            INSERT OR IGNORE INTO calls (id, peer_id, peer_name, call_type, status, duration, timestamp)
            VALUES 
            ('call_1', 'u_elena', 'Elena Rostova', 'VIDEO', 'CONNECTED', 342, ${now - 86400000}),
            ('call_2', 'u_marcus', 'Marcus Vance (Security)', 'VOICE', 'MISSED', 0, ${now - 43200000})
            """.trimIndent()
        )
    }

    // --- Conversations CRUD ---

    fun loadConversations(): List<Conversation> {
        val list = mutableListOf<Conversation>()
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

                list.add(
                    Conversation(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        type = ConversationType.valueOf(it.getString(it.getColumnIndexOrThrow("type"))),
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
        return list
    }

    fun upsertConversation(conv: Conversation) {
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
    }

    // --- Messages CRUD ---

    fun loadMessagesForConversation(convId: String): List<Message> {
        val list = mutableListOf<Message>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC", arrayOf(convId))
        cursor.use {
            while (it.moveToNext()) {
                val reactionsJson = it.getString(it.getColumnIndexOrThrow("reactions_json"))
                val reactionsMap = try {
                    if (reactionsJson != null) json.decodeFromString<Map<String, String>>(reactionsJson) else emptyMap()
                } catch (e: Exception) {
                    emptyMap()
                }

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
                        status = MessageStatus.valueOf(it.getString(it.getColumnIndexOrThrow("status"))),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        replyToMessageId = it.getString(it.getColumnIndexOrThrow("reply_to_id")),
                        replyToSnippet = it.getString(it.getColumnIndexOrThrow("reply_to_snippet")),
                        reactions = reactionsMap,
                        isEdited = it.getInt(it.getColumnIndexOrThrow("is_edited")) == 1,
                        expiresAt = if (it.isNull(it.getColumnIndexOrThrow("expires_at"))) null else it.getLong(it.getColumnIndexOrThrow("expires_at")),
                        isEncrypted = it.getInt(it.getColumnIndexOrThrow("is_encrypted")) == 1
                    )
                )
            }
        }
        val currentMap = _messagesFlow.value.toMutableMap()
        currentMap[convId] = list
        _messagesFlow.value = currentMap
        return list
    }

    fun saveMessage(msg: Message) {
        val db = writableDatabase
        db.execSQL(
            """
            INSERT OR REPLACE INTO messages (id, conversation_id, sender_id, recipient_id, text, media_uri, media_type, media_size, status, timestamp, reply_to_id, reply_to_snippet, reactions_json, is_edited, expires_at, is_encrypted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                if (msg.isEncrypted) 1 else 0
            )
        )
        // Update or insert conversation snippet
        val convSnippet = if (msg.mediaType != null) "[${msg.mediaType}] ${msg.text}" else msg.text
        val cursor = db.rawQuery("SELECT id FROM conversations WHERE id = ?", arrayOf(msg.conversationId))
        val exists = cursor.use { it.moveToFirst() }
        if (!exists) {
            val peerId = if (msg.senderId == "me" || msg.senderId.startsWith("u_")) msg.recipientId else msg.senderId
            val contactCursor = db.rawQuery("SELECT display_name, avatar_url FROM contacts WHERE user_id = ?", arrayOf(peerId))
            val (title, avatarUrl) = contactCursor.use {
                if (it.moveToFirst()) {
                    Pair(it.getString(0) ?: "Secure Chat", it.getString(1))
                } else {
                    Pair("User ${peerId.takeLast(4)}", null)
                }
            }
            db.execSQL(
                """
                INSERT INTO conversations (id, type, title, participant_ids, last_snippet, last_message_timestamp, unread_count, is_pinned, is_archived, is_locked, disappearing_duration, avatar_url)
                VALUES (?, 'DIRECT', ?, ?, ?, ?, 0, 0, 0, 0, NULL, ?)
                """.trimIndent(),
                arrayOf<Any?>(msg.conversationId, title, json.encodeToString(listOf(peerId)), convSnippet, msg.timestamp, avatarUrl)
            )
        } else {
            db.execSQL(
                "UPDATE conversations SET last_snippet = ?, last_message_timestamp = ? WHERE id = ?",
                arrayOf<Any?>(convSnippet, msg.timestamp, msg.conversationId)
            )
        }
        loadMessagesForConversation(msg.conversationId)
        loadConversations()
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val db = writableDatabase
        db.execSQL("UPDATE messages SET status = ? WHERE id = ?", arrayOf(status.name, messageId))
        // Reload in-memory
        val currentMap = _messagesFlow.value.toMutableMap()
        for ((convId, list) in currentMap) {
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx != -1) {
                loadMessagesForConversation(convId)
                break
            }
        }
    }

    fun deleteMessage(messageId: String, convId: String) {
        val db = writableDatabase
        db.execSQL("DELETE FROM messages WHERE id = ?", arrayOf(messageId))
        loadMessagesForConversation(convId)
        loadConversations()
    }

    // --- Contacts CRUD ---

    fun loadContacts(): List<Contact> {
        val list = mutableListOf<Contact>()
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
        return list
    }

    fun upsertContact(contact: Contact) {
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
                contact.phoneNumber,
                contact.username,
                contact.avatarUrl,
                contact.identityKeyBase64,
                if (contact.isVerified) 1 else 0,
                contact.safetyNumber,
                if (contact.isOnline) 1 else 0,
                contact.lastSeen
            )
        )
        loadContacts()
    }

    // --- Vault CRUD ---

    fun loadVaultItems(): List<VaultItem> {
        val list = mutableListOf<VaultItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM vault_items ORDER BY updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    VaultItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        type = VaultItemType.valueOf(it.getString(it.getColumnIndexOrThrow("type"))),
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
        return list
    }

    fun saveVaultItem(item: VaultItem) {
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
    }

    fun deleteVaultItem(id: String) {
        val db = writableDatabase
        db.execSQL("DELETE FROM vault_items WHERE id = ?", arrayOf(id))
        loadVaultItems()
    }

    // --- Calls CRUD ---

    fun loadCalls(): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM calls ORDER BY timestamp DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CallRecord(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        peerId = it.getString(it.getColumnIndexOrThrow("peer_id")),
                        peerName = it.getString(it.getColumnIndexOrThrow("peer_name")),
                        peerAvatar = it.getString(it.getColumnIndexOrThrow("peer_avatar")),
                        callType = CallType.valueOf(it.getString(it.getColumnIndexOrThrow("call_type"))),
                        status = CallStatus.valueOf(it.getString(it.getColumnIndexOrThrow("status"))),
                        durationSec = it.getInt(it.getColumnIndexOrThrow("duration")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        _callsFlow.value = list
        return list
    }

    fun saveCall(call: CallRecord) {
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
    }

    // --- Double Ratchet Sessions Persistence ---

    fun saveRatchetSession(peerUserId: String, serializedState: String) {
        val db = writableDatabase
        db.execSQL(
            "INSERT OR REPLACE INTO ratchet_sessions (peer_user_id, session_data, updated_at) VALUES (?, ?, ?)",
            arrayOf<Any?>(peerUserId, serializedState, System.currentTimeMillis())
        )
    }

    fun getRatchetSession(peerUserId: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT session_data FROM ratchet_sessions WHERE peer_user_id = ?", arrayOf(peerUserId))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    fun wipeAllData() {
        val db = writableDatabase
        db.execSQL("DELETE FROM messages")
        db.execSQL("DELETE FROM conversations")
        db.execSQL("DELETE FROM contacts")
        db.execSQL("DELETE FROM vault_items")
        db.execSQL("DELETE FROM calls")
        db.execSQL("DELETE FROM ratchet_sessions")
        reloadAll()
    }
}
