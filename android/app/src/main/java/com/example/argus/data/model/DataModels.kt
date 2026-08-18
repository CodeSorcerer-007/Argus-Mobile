package com.example.argus.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
enum class ConversationType {
    DIRECT,
    GROUP
}

@Serializable
enum class CallType {
    VOICE,
    VIDEO
}

@Serializable
enum class CallStatus {
    MISSED,
    INCOMING,
    OUTGOING,
    CONNECTED,
    ENDED,
    REJECTED
}

@Serializable
enum class VaultItemType {
    NOTE,
    PHOTO,
    FILE,
    VIDEO
}

@Serializable
data class User(
    val id: String,
    val phoneNumber: String,
    val username: String? = null,
    val displayName: String,
    val avatarUrl: String? = null,
    val about: String? = null,
    val identityKeyBase64: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class Contact(
    val id: String,
    val userId: String,
    val displayName: String,
    val phoneNumber: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val identityKeyBase64: String = "",
    val isVerified: Boolean = false,
    val safetyNumber: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType = ConversationType.DIRECT,
    val title: String,
    val participantIds: List<String>,
    val lastSnippet: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false, // Biometric Chat Lock
    val disappearingDurationSec: Int? = null,
    val avatarUrl: String? = null
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val mediaUri: String? = null,
    val mediaType: String? = null,
    val mediaSizeBytes: Long = 0,
    val status: MessageStatus = MessageStatus.SENT,
    val timestamp: Long = System.currentTimeMillis(),
    val replyToMessageId: String? = null,
    val replyToSnippet: String? = null,
    val reactions: Map<String, String> = emptyMap(), // emoji -> userId
    val isEdited: Boolean = false,
    val expiresAt: Long? = null,
    val isEncrypted: Boolean = true
)

@Serializable
data class VaultItem(
    val id: String,
    val title: String,
    val type: VaultItemType,
    val contentOrPath: String, // Plaintext note content or path to encrypted file blob
    val fileSizeBytes: Long = 0,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = true
)

@Serializable
data class CallRecord(
    val id: String,
    val peerId: String,
    val peerName: String,
    val peerAvatar: String? = null,
    val callType: CallType,
    val status: CallStatus,
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
