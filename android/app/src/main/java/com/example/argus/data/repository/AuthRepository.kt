package com.example.argus.data.repository

import android.os.Build
import com.example.argus.crypto.keys.ArgusKeyPair
import com.example.argus.crypto.ratchet.Curve25519Engine
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.local.ArgusPreferences
import com.example.argus.data.model.User
import com.example.argus.data.remote.ArgusApiClient
import com.example.argus.data.remote.ArgusWebSocketClient
import com.example.argus.data.remote.OneTimeKeyItem
import com.example.argus.data.remote.PublishBundlePayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthRepository(
    private val preferences: ArgusPreferences,
    private val localStore: ArgusLocalStore,
    private val apiClient: ArgusApiClient,
    private val webSocketClient: ArgusWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    val currentUser: StateFlow<User?> = preferences.currentUserFlow

    fun isLoggedIn(): Boolean {
        return preferences.getAuthToken() != null && preferences.loadCurrentUser() != null
    }

    suspend fun requestOtp(phoneNumber: String): com.example.argus.data.remote.OtpRequestResponse {
        return apiClient.requestOtp(phoneNumber)
    }

    suspend fun verifyOtp(phoneNumber: String, code: String, displayName: String? = null): Result<User> {
        return try {
            // Ensure local identity key pair exists
            val identityKeyPair = getOrCreateIdentityKeyPair()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            val response = apiClient.verifyOtp(
                phoneNumber = phoneNumber,
                code = code,
                deviceName = deviceName,
                identityKeyBase64 = identityKeyPair.publicKeyBase64,
                displayName = displayName
            )

            if (response.success && response.user != null && response.token != null) {
                preferences.setAuthToken(response.token)
                preferences.saveCurrentUser(response.user)

                // Publish initial PreKey bundle to server for X3DH session establishment
                publishInitialPreKeys(identityKeyPair)

                // Connect WebSocket
                webSocketClient.connect()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun publishInitialPreKeys(identityKeyPair: ArgusKeyPair) {
        val signedPreKeyPair = Curve25519Engine.generateX25519KeyPair()
        preferences.setSignedPreKeyPairJson(json.encodeToString(signedPreKeyPair))

        val sig = Curve25519Engine.sign(
            identityKeyPair.privateKeyBase64,
            signedPreKeyPair.publicKeyBase64.toByteArray(Charsets.UTF_8)
        )
        val sigBase64 = com.example.argus.core.common.Base64Compat.encodeToString(sig)

        // Generate pool of 20 One-Time PreKeys
        val oneTimeKeys = (1..20).map { id ->
            val pair = Curve25519Engine.generateX25519KeyPair()
            OneTimeKeyItem(
                keyId = id,
                publicKeyBase64 = pair.publicKeyBase64
            )
        }

        val payload = PublishBundlePayload(
            identityPublicKeyBase64 = identityKeyPair.publicKeyBase64,
            signedPreKeyId = 1,
            signedPreKeyPublicBase64 = signedPreKeyPair.publicKeyBase64,
            signedPreKeySignatureBase64 = sigBase64,
            oneTimePreKeys = oneTimeKeys
        )

        apiClient.publishPreKeyBundle(payload)
    }

    fun getOrCreateIdentityKeyPair(): ArgusKeyPair {
        val saved = preferences.getIdentityKeyPairJson()
        if (saved != null) {
            try {
                return json.decodeFromString<ArgusKeyPair>(saved)
            } catch (e: Exception) {
                // fall through to regenerate
            }
        }
        val newPair = Curve25519Engine.generateEd25519KeyPair()
        preferences.setIdentityKeyPairJson(json.encodeToString(newPair))
        return newPair
    }

    fun hashPhoneNumber(phone: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest("Argus_Salt_2026:${phone.trim()}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun searchUsers(query: String): List<User> {
        return apiClient.searchUsers(query)
    }

    suspend fun findUserByPhone(phoneNumber: String): User? {
        val hash = hashPhoneNumber(phoneNumber)
        val matches = apiClient.discoverContacts(listOf(hash))
        return matches.firstOrNull()
    }

    suspend fun startConversationWithUser(user: User): String {
        val contact = com.example.argus.data.model.Contact(
            id = "contact_${user.id}",
            userId = user.id,
            displayName = user.displayName,
            phoneNumber = user.phoneNumber,
            username = user.username,
            avatarUrl = user.avatarUrl,
            identityKeyBase64 = user.identityKeyBase64,
            isVerified = false,
            safetyNumber = null,
            isOnline = user.isOnline,
            lastSeen = user.lastSeen
        )
        localStore.upsertContact(contact)

        try {
            apiClient.fetchTargetPreKeyBundle(user.id)
        } catch (e: Exception) {
            // Lazily initializes on first message send
        }

        val convId = "conv_${user.id}"
        val existing = localStore.loadConversations().firstOrNull { it.id == convId }
        if (existing == null) {
            val conv = com.example.argus.data.model.Conversation(
                id = convId,
                title = user.displayName,
                participantIds = listOf(user.id),
                avatarUrl = user.avatarUrl
            )
            localStore.upsertConversation(conv)
        }
        return convId
    }

    suspend fun updateProfile(displayName: String?, username: String?, about: String? = null): Result<User> {
        val updated = apiClient.updateProfile(displayName, username, about)
        return if (updated != null) {
            preferences.saveCurrentUser(updated)
            Result.success(updated)
        } else {
            Result.failure(Exception("Failed to update profile. Username may already be taken."))
        }
    }

    fun logout() {
        webSocketClient.disconnect()
        preferences.clearAll()
        localStore.wipeAllData()
    }
}
