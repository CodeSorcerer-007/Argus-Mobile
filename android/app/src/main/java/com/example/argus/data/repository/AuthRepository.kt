package com.example.argus.data.repository

import android.os.Build
import com.example.argus.crypto.keys.ArgusKeyPair
import com.example.argus.crypto.keys.OneTimePreKey
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
    val localStore: ArgusLocalStore,
    private val apiClient: ArgusApiClient,
    private val webSocketClient: ArgusWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    val currentUser: StateFlow<User?> = preferences.currentUserFlow

    fun isLoggedIn(): Boolean {
        return preferences.getAuthToken() != null && preferences.loadCurrentUser() != null
    }

    suspend fun register(username: String, password: String, displayName: String): Result<User> {
        return try {
            val identityKeyPair = getOrCreateIdentityKeyPair()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            val response = apiClient.register(
                username = username.trim(),
                password = password,
                displayName = displayName.trim(),
                identityKeyBase64 = identityKeyPair.publicKeyBase64,
                deviceName = deviceName
            )

            if (response.success && response.user != null && response.token != null) {
                preferences.setAuthToken(response.token)
                if (response.refreshToken != null) {
                    preferences.setRefreshToken(response.refreshToken)
                }
                preferences.saveCurrentUser(response.user)
                if (response.recoveryKey != null) {
                    preferences.setEmergencyRecoveryKey(response.recoveryKey)
                }

                // Publish initial PreKey bundle to server for X3DH session establishment & persist private keys
                publishInitialPreKeys(identityKeyPair)

                // Connect WebSocket
                webSocketClient.connect()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<User> {
        return try {
            val identityKeyPair = getOrCreateIdentityKeyPair()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            val response = apiClient.login(
                username = username.trim(),
                password = password,
                identityKeyBase64 = identityKeyPair.publicKeyBase64,
                deviceName = deviceName
            )

            if (response.success && response.user != null && response.token != null) {
                preferences.setAuthToken(response.token)
                if (response.refreshToken != null) {
                    preferences.setRefreshToken(response.refreshToken)
                }
                preferences.saveCurrentUser(response.user)

                // Refresh PreKeys upon login & persist private keys
                publishInitialPreKeys(identityKeyPair)

                // Connect WebSocket
                webSocketClient.connect()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyRecoveryKey(username: String, recoveryKey: String): Result<Boolean> {
        return try {
            val res = apiClient.verifyRecoveryKey(username, recoveryKey)
            if (res.valid) {
                Result.success(true)
            } else {
                Result.failure(Exception(res.error ?: "Invalid emergency recovery key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(username: String, newPassword: String, recoveryKey: String? = null): Result<User> {
        return try {
            val identityKeyPair = getOrCreateIdentityKeyPair()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            val response = apiClient.resetPassword(
                username = username.trim(),
                newPassword = newPassword,
                recoveryKey = recoveryKey?.trim()?.takeIf { it.isNotBlank() },
                identityKeyBase64 = identityKeyPair.publicKeyBase64,
                deviceName = deviceName
            )

            if (response.success && response.user != null && response.token != null) {
                preferences.setAuthToken(response.token)
                if (response.refreshToken != null) {
                    preferences.setRefreshToken(response.refreshToken)
                }
                preferences.saveCurrentUser(response.user)
                if (response.recoveryKey != null) {
                    preferences.setEmergencyRecoveryKey(response.recoveryKey)
                }

                // Re-publish PreKeys with new session & persist private keys
                publishInitialPreKeys(identityKeyPair)

                // Connect WebSocket
                webSocketClient.connect()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Password reset failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEmergencyRecoveryKey(): String? {
        return preferences.getEmergencyRecoveryKey()
    }

    suspend fun checkUsernameAvailability(username: String): Boolean {
        return apiClient.checkUsername(username)
    }

    private suspend fun publishInitialPreKeys(identityKeyPair: ArgusKeyPair) {
        val signedPreKeyPair = Curve25519Engine.generateX25519KeyPair()
        preferences.setSignedPreKeyPairJson(json.encodeToString(signedPreKeyPair))

        val sig = Curve25519Engine.sign(
            identityKeyPair.privateKeyBase64,
            signedPreKeyPair.publicKeyBase64.toByteArray(Charsets.UTF_8)
        )
        val sigBase64 = com.example.argus.core.common.Base64Compat.encodeToString(sig)

        // Generate pool of 20 One-Time PreKeys and PERSIST private keys locally
        val oneTimeKeyPairs = (1..20).map { id ->
            val pair = Curve25519Engine.generateX25519KeyPair()
            OneTimePreKey(
                keyId = id,
                keyPair = pair,
                isUsed = false
            )
        }
        preferences.saveOneTimePreKeys(oneTimeKeyPairs)

        val oneTimeKeyItems = oneTimeKeyPairs.map {
            OneTimeKeyItem(
                keyId = it.keyId,
                publicKeyBase64 = it.keyPair.publicKeyBase64
            )
        }

        val payload = PublishBundlePayload(
            identityPublicKeyBase64 = identityKeyPair.publicKeyBase64,
            signedPreKeyId = 1,
            signedPreKeyPublicBase64 = signedPreKeyPair.publicKeyBase64,
            signedPreKeySignatureBase64 = sigBase64,
            oneTimePreKeys = oneTimeKeyItems
        )

        val published = apiClient.publishPreKeyBundle(payload)
        if (published) {
            preferences.setLastSignedPreKeyRotation(System.currentTimeMillis())
        }
    }

    suspend fun checkAndEnsurePreKeysPublished() {
        if (!isLoggedIn()) return
        val signedPreKeyJson = preferences.getSignedPreKeyPairJson()
        val oneTimeKeys = preferences.getOneTimePreKeys()
        if (signedPreKeyJson == null || oneTimeKeys.isEmpty()) {
            try {
                val identityKeyPair = getOrCreateIdentityKeyPair()
                publishInitialPreKeys(identityKeyPair)
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Failed to publish initial prekeys on startup", e)
            }
        } else {
            checkAndRotatePreKeysIfNeeded()
        }
    }

    suspend fun checkAndRotatePreKeysIfNeeded() {
        val lastRotation = preferences.getLastSignedPreKeyRotation()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastRotation > sevenDaysMs) {
            try {
                val identityKeyPair = getOrCreateIdentityKeyPair()
                publishInitialPreKeys(identityKeyPair)
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Periodic PreKey rotation error", e)
            }
        }
    }

    fun getAndConsumeOneTimePreKey(keyId: Int): ArgusKeyPair? {
        return preferences.getAndConsumeOneTimePreKey(keyId)
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
        val newPair = Curve25519Engine.generateX25519KeyPair()
        preferences.setIdentityKeyPairJson(json.encodeToString(newPair))
        return newPair
    }

    fun getOrCreateSignedPreKeyPair(): ArgusKeyPair {
        val saved = preferences.getSignedPreKeyPairJson()
        if (saved != null) {
            try {
                return json.decodeFromString<ArgusKeyPair>(saved)
            } catch (e: Exception) {
                // fall through to regenerate
            }
        }
        val newPair = Curve25519Engine.generateX25519KeyPair()
        preferences.setSignedPreKeyPairJson(json.encodeToString(newPair))
        return newPair
    }

    fun hashPhoneNumber(phone: String): String {
        val salt = preferences.getPhoneSalt()
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest("Argus_PhoneSalt:$salt:${phone.trim()}".toByteArray(Charsets.UTF_8))
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
        val safeDisplayName = when {
            user.displayName.isNotBlank() -> user.displayName
            user.username.isNotBlank() -> "@${user.username}"
            !user.phoneNumber.isNullOrBlank() -> user.phoneNumber
            else -> "User ${user.id.takeLast(4)}"
        }

        try {
            val contact = com.example.argus.data.model.Contact(
                id = "contact_${user.id}",
                userId = user.id,
                displayName = safeDisplayName,
                phoneNumber = user.phoneNumber ?: "",
                username = user.username.ifBlank { null },
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

            val myUserId = preferences.loadCurrentUser()?.id ?: "me"
            val convId = ArgusLocalStore.getDirectConversationId(myUserId, user.id)
            val existing = localStore.loadConversations().firstOrNull { it.id == convId }
            if (existing == null) {
                val conv = com.example.argus.data.model.Conversation(
                    id = convId,
                    title = safeDisplayName,
                    participantIds = listOf(user.id),
                    avatarUrl = user.avatarUrl
                )
                localStore.upsertConversation(conv)
            }
            return convId
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "startConversationWithUser error", e)
            val myUserId = preferences.loadCurrentUser()?.id ?: "me"
            return ArgusLocalStore.getDirectConversationId(myUserId, user.id)
        }
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
