package com.example.argus.data.repository

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    val conversations: StateFlow<List<Conversation>> = localStore.conversationsFlow
    val messages: StateFlow<Map<String, List<Message>>> = localStore.messagesFlow

    private val _typingStateFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap<String, Boolean>())
    val typingState: StateFlow<Map<String, Boolean>> = _typingStateFlow.asStateFlow()

    init {
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
        val ephemeralPublicKey: String?
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

        val localMessage = Message(
            id = messageId,
            conversationId = canonicalConvId,
            senderId = currentUserId,
            recipientId = recipientId,
            text = text,
            mediaUri = mediaUri,
            mediaType = mediaType,
            mediaSizeBytes = mediaSizeBytes,
            status = MessageStatus.SENDING,
            timestamp = now,
            replyToMessageId = replyToMessageId,
            replyToSnippet = replyToSnippet,
            expiresAt = expiresAt,
            isEncrypted = true
        )

        localStore.saveMessage(localMessage)

        // Perform Signal Double Ratchet Encryption
        scope.launch {
            try {
                val encResult = encryptForRecipient(recipientId, text)

                val wirePayload = WireEncryptedPayload(
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
                    mediaUrl = mediaUri,
                    mediaType = mediaType,
                    mediaSize = mediaSizeBytes,
                    replyToMessageId = replyToMessageId,
                    timestamp = now,
                    expiresAt = expiresAt
                )

                val sent = webSocketClient.sendEncryptedMessage(wirePayload)
                val status = if (sent) MessageStatus.SENT else MessageStatus.QUEUED
                localStore.updateMessageStatus(messageId, status)
            } catch (e: Exception) {
                // If encryption fails or offline, leave in QUEUED state for auto-retry
                localStore.updateMessageStatus(messageId, MessageStatus.QUEUED)
            }
        }

        return localMessage
    }

    private suspend fun encryptForRecipient(recipientId: String, plaintext: String): EncryptedResult {
        val identityKey = authRepository.getOrCreateIdentityKeyPair()
        val savedSession = localStore.getRatchetSession(recipientId)
        var ephemeralKey: String? = null
        val session: DoubleRatchetSession = if (savedSession != null) {
            DoubleRatchetSession.fromSerialized(savedSession)
        } else {
            // Establish new X3DH session by fetching target's PreKeyBundle
            val bundle = apiClient.fetchTargetPreKeyBundle(recipientId)
                ?: error("Failed to fetch PreKeyBundle for recipient $recipientId")
            val (newSession, ephemPair) = DoubleRatchetSession.initializeInitiator(identityKey, bundle)
            ephemeralKey = ephemPair.publicKeyBase64
            newSession
        }

        val wire = session.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        localStore.saveRatchetSession(recipientId, session.serialize())
        return EncryptedResult(wire, identityKey.publicKeyBase64, ephemeralKey)
    }

    private suspend fun handleIncomingWireMessage(payload: WireEncryptedPayload) {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val senderId = payload.senderId

        // Decrypt using recipient DoubleRatchetSession
        val decryptedText = try {
            val savedSession = localStore.getRatchetSession(senderId)
            val session: DoubleRatchetSession = if (savedSession != null) {
                DoubleRatchetSession.fromSerialized(savedSession)
            } else {
                val myIdentity = authRepository.getOrCreateIdentityKeyPair()
                val mySignedPreKey = json.decodeFromString<ArgusKeyPair>(
                    preferences.getSignedPreKeyPairJson() ?: error("Missing signed prekey")
                )
                val senderIdentity = payload.senderIdentityPublicKeyBase64
                    ?: localStore.loadContacts().firstOrNull { it.userId == senderId }?.identityKeyBase64
                    ?: payload.dhPublicKeyBase64
                val senderEphemeral = payload.ephemeralPublicKeyBase64 ?: payload.dhPublicKeyBase64

                DoubleRatchetSession.initializeReceiver(
                    bobIdentityKeyPair = myIdentity,
                    bobSignedPreKeyPair = mySignedPreKey,
                    bobOneTimePreKeyPair = null,
                    aliceIdentityPublicKeyBase64 = senderIdentity,
                    aliceEphemeralPublicKeyBase64 = senderEphemeral,
                    aliceInitialDhRatchetPublicKeyBase64 = payload.dhPublicKeyBase64
                )
            }

            val wireMsg = RatchetWireMessage(
                dhPublicKeyBase64 = payload.dhPublicKeyBase64,
                sequenceNumber = payload.sequenceNumber,
                previousChainLength = payload.previousChainLength,
                ivBase64 = payload.ivBase64,
                ciphertextBase64 = payload.ciphertextBase64
            )
            val decryptedBytes = session.decrypt(wireMsg)
            localStore.saveRatchetSession(senderId, session.serialize())
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Decryption failed, fallback message", e)
            "[Encrypted Signal Payload]"
        }

        val canonicalConvId = if (payload.conversationId.startsWith("group_")) {
            payload.conversationId
        } else {
            ArgusLocalStore.getDirectConversationId(senderId, currentUserId)
        }

        val message = Message(
            id = payload.id,
            conversationId = canonicalConvId,
            senderId = payload.senderId,
            recipientId = currentUserId,
            text = decryptedText,
            mediaUri = payload.mediaUrl,
            mediaType = payload.mediaType,
            mediaSizeBytes = payload.mediaSize ?: 0L,
            status = MessageStatus.DELIVERED,
            timestamp = payload.timestamp,
            replyToMessageId = payload.replyToMessageId,
            expiresAt = payload.expiresAt,
            isEncrypted = true
        )

        localStore.saveMessage(message)
        webSocketClient.sendDeliveryAck(payload.id, payload.senderId)
    }

    fun markAsRead(conversationId: String, senderId: String) {
        val currentUserId = preferences.loadCurrentUser()?.id ?: "me"
        val msgs = localStore.loadMessagesForConversation(conversationId)
        msgs.filter { (it.recipientId == currentUserId || it.recipientId == "me") && it.status != MessageStatus.READ }.forEach {
            localStore.updateMessageStatus(it.id, MessageStatus.READ)
            webSocketClient.sendReadAck(it.id, senderId)
        }
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
        localStore.saveMessage(updated)
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
