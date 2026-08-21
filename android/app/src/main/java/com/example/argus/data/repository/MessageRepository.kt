package com.example.argus.data.repository

import android.util.Log
import com.example.argus.crypto.keys.ArgusKeyPair
import com.example.argus.crypto.ratchet.DoubleRatchetSession
import com.example.argus.crypto.ratchet.RatchetWireMessage
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.local.ArgusPreferences
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.Message
import com.example.argus.data.model.MessageStatus
import com.example.argus.data.remote.ArgusApiClient
import com.example.argus.data.remote.ArgusWebSocketClient
import com.example.argus.data.remote.WebSocketInboundEvent
import com.example.argus.data.remote.WireEncryptedPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class MessageRepository(
    private val localStore: ArgusLocalStore,
    private val preferences: ArgusPreferences,
    private val apiClient: ArgusApiClient,
    private val webSocketClient: ArgusWebSocketClient,
    private val authRepository: AuthRepository
) {
    private val TAG = "MessageRepository"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val flushMutex = Mutex()

    val conversations: StateFlow<List<Conversation>> = localStore.conversationsFlow
    val messages: StateFlow<Map<String, List<Message>>> = localStore.messagesFlow

    private val _typingStateFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap<String, Boolean>())
    val typingState: StateFlow<Map<String, Boolean>> = _typingStateFlow.asStateFlow()

    init {
        // Auto-flush queued offline messages when WebSocket connects
        scope.launch {
            webSocketClient.connectionState.collect { isConnected ->
                if (isConnected) {
                    flushQueuedMessages()
                }
            }
        }

        // Listen to real-time WebSocket events
        scope.launch {
            webSocketClient.inboundEvents.collect { event ->
                when (event) {
                    is WebSocketInboundEvent.NewMessage -> handleIncomingWireMessage(event.payload)
                    is WebSocketInboundEvent.MessageStatusUpdate -> {
                        val status = try { MessageStatus.valueOf(event.status) } catch (e: Exception) { MessageStatus.DELIVERED }
                        localStore.updateMessageStatus(event.messageId, status)
                    }
                    is WebSocketInboundEvent.Typing -> {
                        val current = _typingStateFlow.value.toMutableMap()
                        current[event.conversationId] = event.isTyping
                        _typingStateFlow.value = current
                        if (event.isTyping) {
                            // Auto reset after 3.5s of inactivity
                            scope.launch {
                                kotlinx.coroutines.delay(3500)
                                val map = _typingStateFlow.value.toMutableMap()
                                if (map[event.conversationId] == true) {
                                    map[event.conversationId] = false
                                    _typingStateFlow.value = map
                                }
                            }
                        }
                    }
                    is WebSocketInboundEvent.Presence -> {
                        val contacts = localStore.loadContacts().toMutableList()
                        val idx = contacts.indexOfFirst { it.userId == event.userId }
                        if (idx != -1) {
                            val updated = contacts[idx].copy(isOnline = event.isOnline, lastSeen = event.lastSeen)
                            localStore.upsertContact(updated)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private data class EncryptedResult(
        val wireMessage: RatchetWireMessage,
        val senderIdentityKey: String,
        val ephemeralPublicKey: String?,
        val oneTimePreKeyId: Int?
    )

    suspend fun sendMessage(
        conversationId: String,
        recipientId: String,
        text: String,
        mediaUri: String? = null,
        mediaType: String? = null,
        mediaSizeBytes: Long = 0,
        replyToMessageId: String? = null,
        replyToSnippet: String? = null,
        disappearingDurationSec: Int? = null
    ): Message {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = disappearingDurationSec?.let { now + it * 1000L }

        val canonicalConvId = if (conversationId.startsWith("group_")) {
            conversationId
        } else {
            ArgusLocalStore.getDirectConversationId(currentUserId, recipientId)
        }

        var finalStatus = MessageStatus.QUEUED
        var wirePayload: WireEncryptedPayload? = null

        // Encrypt inline synchronously
        try {
            val encResult = encryptForRecipient(recipientId, text)
            wirePayload = WireEncryptedPayload(
                id = messageId,
                conversationId = canonicalConvId,
                senderId = currentUserId,
                recipientId = recipientId,
                dhPublicKeyBase64 = encResult.wireMessage.dhPublicKeyBase64,
                sequenceNumber = encResult.wireMessage.sequenceNumber,
                previousChainLength = encResult.wireMessage.previousChainLength,
                ivBase64 = encResult.wireMessage.ivBase64,
                ciphertextBase64 = encResult.wireMessage.ciphertextBase64,
                senderIdentityPublicKeyBase64 = encResult.senderIdentityKey,
                ephemeralPublicKeyBase64 = encResult.ephemeralPublicKey,
                oneTimePreKeyId = encResult.oneTimePreKeyId,
                mediaUrl = mediaUri,
                mediaType = mediaType,
                mediaSize = mediaSizeBytes,
                replyToMessageId = replyToMessageId,
                timestamp = now,
                expiresAt = expiresAt
            )

            val sent = webSocketClient.sendEncryptedMessage(wirePayload)
            finalStatus = if (sent) MessageStatus.SENT else MessageStatus.QUEUED
        } catch (e: Exception) {
            Log.e(TAG, "Encryption or direct transport failed, queueing message for auto-retry", e)
            finalStatus = MessageStatus.QUEUED
        }

        val wirePayloadJson = wirePayload?.let { json.encodeToString(it) }

        val localMessage = Message(
            id = messageId,
            conversationId = canonicalConvId,
            senderId = currentUserId,
            recipientId = recipientId,
            text = text,
            mediaUri = mediaUri,
            mediaType = mediaType,
            mediaSizeBytes = mediaSizeBytes,
            status = finalStatus,
            timestamp = now,
            replyToMessageId = replyToMessageId,
            replyToSnippet = replyToSnippet,
            expiresAt = expiresAt,
            isEncrypted = true,
            wirePayloadJson = wirePayloadJson
        )

        localStore.saveMessage(localMessage, currentUserId)
        return localMessage
    }

    fun flushQueuedMessages() {
        scope.launch {
            if (!flushMutex.tryLock()) return@launch
            try {
                val queued = localStore.loadQueuedMessages()
                if (queued.isEmpty()) return@launch
                Log.d(TAG, "Flushing ${queued.size} queued offline messages sequentially")
                for (msg in queued) {
                    try {
                        val success = retryMessage(msg)
                        if (!success && !webSocketClient.connectionState.value) {
                            Log.w(TAG, "Stopping flush because transport disconnected during retry of ${msg.id}")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in flushQueuedMessages for msg ${msg.id}", e)
                    }
                }
            } finally {
                flushMutex.unlock()
            }
        }
    }

    suspend fun retryMessage(msg: Message): Boolean {
        return try {
            val wirePayload: WireEncryptedPayload = if (!msg.wirePayloadJson.isNullOrBlank()) {
                // Replay exact wire payload to preserve Double Ratchet state (C-2)
                json.decodeFromString<WireEncryptedPayload>(msg.wirePayloadJson)
            } else {
                val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
                val encResult = encryptForRecipient(msg.recipientId, msg.text)
                val payload = WireEncryptedPayload(
                    id = msg.id,
                    conversationId = msg.conversationId,
                    senderId = currentUserId,
                    recipientId = msg.recipientId,
                    dhPublicKeyBase64 = encResult.wireMessage.dhPublicKeyBase64,
                    sequenceNumber = encResult.wireMessage.sequenceNumber,
                    previousChainLength = encResult.wireMessage.previousChainLength,
                    ivBase64 = encResult.wireMessage.ivBase64,
                    ciphertextBase64 = encResult.wireMessage.ciphertextBase64,
                    senderIdentityPublicKeyBase64 = encResult.senderIdentityKey,
                    ephemeralPublicKeyBase64 = encResult.ephemeralPublicKey,
                    oneTimePreKeyId = encResult.oneTimePreKeyId,
                    mediaUrl = msg.mediaUri,
                    mediaType = msg.mediaType,
                    mediaSize = msg.mediaSizeBytes,
                    replyToMessageId = msg.replyToMessageId,
                    timestamp = msg.timestamp,
                    expiresAt = msg.expiresAt
                )
                // Persist wire payload json
                val updated = msg.copy(wirePayloadJson = json.encodeToString(payload))
                localStore.saveMessage(updated, currentUserId)
                payload
            }

            val sent = webSocketClient.sendEncryptedMessage(wirePayload)
            if (sent) {
                localStore.updateMessageStatus(msg.id, MessageStatus.SENT)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Retry failed for message ${msg.id}", e)
            false
        }
    }

    private suspend fun encryptForRecipient(recipientId: String, plaintext: String): EncryptedResult {
        val identityKey = authRepository.getOrCreateIdentityKeyPair()
        val savedSession = localStore.getRatchetSession(recipientId)
        var ephemeralKey: String? = null
        var otpId: Int? = null

        val session: DoubleRatchetSession = if (savedSession != null) {
            DoubleRatchetSession.fromSerialized(savedSession)
        } else {
            // Establish new X3DH session by fetching target's PreKeyBundle
            val bundle = apiClient.fetchTargetPreKeyBundle(recipientId)
                ?: error("Failed to fetch PreKeyBundle for recipient $recipientId")
            otpId = bundle.oneTimePreKeyId
            val (newSession, ephemPair) = DoubleRatchetSession.initializeInitiator(identityKey, bundle)
            ephemeralKey = ephemPair.publicKeyBase64
            newSession
        }

        val wire = session.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        localStore.saveRatchetSession(recipientId, session.serialize())
        return EncryptedResult(wire, identityKey.publicKeyBase64, ephemeralKey, otpId)
    }

    private suspend fun handleIncomingWireMessage(payload: WireEncryptedPayload) {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val senderId = payload.senderId

        // Direct plaintext payload handling (e.g. from backend bot / unencrypted system alert)
        if (!payload.text.isNullOrBlank() && payload.ciphertextBase64.isBlank()) {
            saveAndAckIncomingMessage(payload, payload.text, currentUserId)
            return
        }

        // Decrypt using recipient DoubleRatchetSession
        var decryptedText: String? = null

        try {
            val savedSession = localStore.getRatchetSession(senderId)
            val session: DoubleRatchetSession? = if (savedSession != null) {
                try {
                    DoubleRatchetSession.fromSerialized(savedSession)
                } catch (e: Exception) {
                    localStore.deleteRatchetSession(senderId)
                    null
                }
            } else null

            val activeSession = session ?: run {
                val myIdentity = authRepository.getOrCreateIdentityKeyPair()
                val mySignedPreKey = authRepository.getOrCreateSignedPreKeyPair()
                val senderIdentity = payload.senderIdentityPublicKeyBase64
                    ?: localStore.loadContacts().firstOrNull { it.userId == senderId }?.identityKeyBase64
                    ?: payload.dhPublicKeyBase64
                val senderEphemeral = payload.ephemeralPublicKeyBase64 ?: payload.dhPublicKeyBase64

                // Retrieve and consume Bob's local OTP private key matching Alice's requested oneTimePreKeyId
                val otpKeyPair = payload.oneTimePreKeyId?.let { authRepository.getAndConsumeOneTimePreKey(it) }

                DoubleRatchetSession.initializeReceiver(
                    bobIdentityKeyPair = myIdentity,
                    bobSignedPreKeyPair = mySignedPreKey,
                    bobOneTimePreKeyPair = otpKeyPair,
                    aliceIdentityPublicKeyBase64 = senderIdentity,
                    aliceEphemeralPublicKeyBase64 = senderEphemeral,
                    aliceInitialDhRatchetPublicKeyBase64 = payload.dhPublicKeyBase64
                )
            }

            if (payload.ciphertextBase64.isNotBlank() && payload.ivBase64.isNotBlank()) {
                try {
                    val wireMsg = RatchetWireMessage(
                        dhPublicKeyBase64 = payload.dhPublicKeyBase64,
                        sequenceNumber = payload.sequenceNumber,
                        previousChainLength = payload.previousChainLength,
                        ivBase64 = payload.ivBase64,
                        ciphertextBase64 = payload.ciphertextBase64
                    )
                    val decryptedBytes = activeSession.decrypt(wireMsg)
                    localStore.saveRatchetSession(senderId, activeSession.serialize())
                    decryptedText = String(decryptedBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    Log.w(TAG, "Decryption with existing ratchet session failed, attempting fresh X3DH receiver init", e)
                    // If session was desynchronized and Alice included initial ephemeral keys, attempt fresh X3DH
                    if (payload.ephemeralPublicKeyBase64 != null && payload.senderIdentityPublicKeyBase64 != null) {
                        try {
                            val myIdentity = authRepository.getOrCreateIdentityKeyPair()
                            val mySignedPreKey = authRepository.getOrCreateSignedPreKeyPair()
                            val otpKeyPair = payload.oneTimePreKeyId?.let { authRepository.getAndConsumeOneTimePreKey(it) }

                            val freshSession = DoubleRatchetSession.initializeReceiver(
                                bobIdentityKeyPair = myIdentity,
                                bobSignedPreKeyPair = mySignedPreKey,
                                bobOneTimePreKeyPair = otpKeyPair,
                                aliceIdentityPublicKeyBase64 = payload.senderIdentityPublicKeyBase64,
                                aliceEphemeralPublicKeyBase64 = payload.ephemeralPublicKeyBase64,
                                aliceInitialDhRatchetPublicKeyBase64 = payload.dhPublicKeyBase64
                            )
                            val wireMsg = RatchetWireMessage(
                                dhPublicKeyBase64 = payload.dhPublicKeyBase64,
                                sequenceNumber = payload.sequenceNumber,
                                previousChainLength = payload.previousChainLength,
                                ivBase64 = payload.ivBase64,
                                ciphertextBase64 = payload.ciphertextBase64
                            )
                            val decryptedBytes = freshSession.decrypt(wireMsg)
                            localStore.saveRatchetSession(senderId, freshSession.serialize())
                            decryptedText = String(decryptedBytes, Charsets.UTF_8)
                        } catch (freshEx: Exception) {
                            Log.e(TAG, "Fresh X3DH receiver init also failed", freshEx)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption pipeline failed for message ${payload.id}", e)
        }

        // Graceful fallback for plaintext / simple payload
        if (decryptedText == null) {
            decryptedText = if (!payload.text.isNullOrBlank()) {
                payload.text
            } else {
                try {
                    val rawDecoded = String(com.example.argus.core.common.Base64Compat.decode(payload.ciphertextBase64), Charsets.UTF_8)
                    if (rawDecoded.all { it.isLetterOrDigit() || it.isWhitespace() || "!@#$%^&*()_+-=[]{}|;':\",./<>?~`".contains(it) }) {
                        rawDecoded
                    } else {
                        "[Encrypted Signal Payload]"
                    }
                } catch (e: Exception) {
                    "[Encrypted Signal Payload]"
                }
            }
        }

        saveAndAckIncomingMessage(payload, decryptedText, currentUserId)
    }

    private fun saveAndAckIncomingMessage(payload: WireEncryptedPayload, text: String, currentUserId: String) {
        val canonicalConvId = if (payload.conversationId.startsWith("group_")) {
            payload.conversationId
        } else {
            ArgusLocalStore.getDirectConversationId(payload.senderId, currentUserId)
        }

        val message = Message(
            id = payload.id,
            conversationId = canonicalConvId,
            senderId = payload.senderId,
            recipientId = currentUserId,
            text = text,
            mediaUri = payload.mediaUrl,
            mediaType = payload.mediaType,
            mediaSizeBytes = payload.mediaSize ?: 0L,
            status = MessageStatus.DELIVERED,
            timestamp = payload.timestamp,
            replyToMessageId = payload.replyToMessageId,
            expiresAt = payload.expiresAt,
            isEncrypted = true,
            wirePayloadJson = json.encodeToString(payload)
        )

        localStore.saveMessage(message, currentUserId)
        webSocketClient.sendDeliveryAck(payload.id, payload.senderId)
    }

    fun markAsRead(conversationId: String, senderId: String) {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val msgs = localStore.loadMessagesForConversation(conversationId)
        msgs.filter { (it.recipientId == currentUserId || it.recipientId == "me") && it.status != MessageStatus.READ }.forEach {
            localStore.updateMessageStatus(it.id, MessageStatus.READ)
            webSocketClient.sendReadAck(it.id, senderId)
        }
        localStore.resetUnreadCount(conversationId)
    }

    fun addReaction(message: Message, emoji: String) {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val updatedReactions = message.reactions.toMutableMap()
        if (updatedReactions[emoji] == currentUserId) {
            updatedReactions.remove(emoji)
        } else {
            updatedReactions[emoji] = currentUserId
        }
        val updated = message.copy(reactions = updatedReactions)
        localStore.saveMessage(updated, currentUserId)
    }

    fun sendTyping(recipientId: String, conversationId: String, isTyping: Boolean) {
        if (preferences.isTypingIndicatorsEnabled()) {
            webSocketClient.sendTyping(recipientId, conversationId, isTyping)
        }
    }

    suspend fun uploadMediaAttachment(file: File, mimeType: String): String? {
        return apiClient.uploadEncryptedMedia(file, mimeType)
    }
}
