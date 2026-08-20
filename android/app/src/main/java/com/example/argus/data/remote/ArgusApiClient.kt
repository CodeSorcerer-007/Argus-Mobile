package com.example.argus.data.remote

import com.example.argus.crypto.keys.PreKeyBundle
import com.example.argus.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class ArgusApiClient(
    private val getBaseUrl: () -> String,
    private val getAuthToken: () -> String?
) {
    private val baseUrl: String get() = getBaseUrl().trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        identityKeyBase64: String,
        deviceName: String = "Android Device"
    ): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                RegisterPayload(
                    username = username.trim(),
                    password = password,
                    displayName = displayName.trim(),
                    identityKeyBase64 = identityKeyBase64,
                    deviceName = deviceName
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.isNotEmpty()) {
                    json.decodeFromString<AuthResponse>(body)
                } else {
                    AuthResponse(success = false, error = "Server returned empty response (${response.code})")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "register failed: ${e.message}", e)
            AuthResponse(success = false, error = e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun login(
        username: String,
        password: String,
        identityKeyBase64: String? = null,
        deviceName: String = "Android Device"
    ): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                LoginPayload(
                    username = username.trim(),
                    password = password,
                    identityKeyBase64 = identityKeyBase64,
                    deviceName = deviceName
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.isNotEmpty()) {
                    json.decodeFromString<AuthResponse>(body)
                } else {
                    AuthResponse(success = false, error = "Server returned empty response (${response.code})")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "login failed: ${e.message}", e)
            AuthResponse(success = false, error = e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun checkUsername(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = username.trim().lowercase()
            val request = Request.Builder()
                .url("$baseUrl/api/auth/check-username/$clean")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: return@withContext false
                json.decodeFromString<CheckUsernameResponse>(body).available
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun publishPreKeyBundle(payload: PublishBundlePayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext false
            val payloadStr = json.encodeToString(payload)
            val request = Request.Builder()
                .url("$baseUrl/api/keys/publish")
                .header("Authorization", "Bearer $token")
                .post(payloadStr.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "publishPreKeyBundle failed: ${e.message}", e)
            false
        }
    }

    suspend fun publishPreKeyBundle(
        identityKeyBase64: String,
        signedPreKeyId: Int,
        signedPreKeyPublicBase64: String,
        signedPreKeySignatureBase64: String,
        oneTimePreKeys: List<Pair<Int, String>>
    ): Boolean = publishPreKeyBundle(
        PublishBundlePayload(
            identityPublicKeyBase64 = identityKeyBase64,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicBase64 = signedPreKeyPublicBase64,
            signedPreKeySignatureBase64 = signedPreKeySignatureBase64,
            oneTimePreKeys = oneTimePreKeys.map { OneTimeKeyItem(it.first, it.second) }
        )
    )

    suspend fun fetchTargetPreKeyBundle(userId: String): PreKeyBundle? = fetchPreKeyBundle(userId)

    suspend fun fetchPreKeyBundle(userId: String): PreKeyBundle? = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext null
            val request = Request.Builder()
                .url("$baseUrl/api/keys/bundle/$userId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val dto = json.decodeFromString<PreKeyBundleResponse>(body)
                PreKeyBundle(
                    userId = dto.userId.ifEmpty { userId },
                    deviceId = dto.deviceId.ifEmpty { "primary" },
                    identityPublicKeyBase64 = dto.identityPublicKeyBase64,
                    signedPreKeyId = dto.signedPreKeyId,
                    signedPreKeyPublicBase64 = dto.signedPreKeyPublicBase64,
                    signedPreKeySignatureBase64 = dto.signedPreKeySignatureBase64,
                    oneTimePreKeyId = dto.oneTimePreKeyId,
                    oneTimePreKeyPublicBase64 = dto.oneTimePreKeyPublicBase64
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "fetchPreKeyBundle failed: ${e.message}", e)
            null
        }
    }

    suspend fun discoverContacts(phoneHashes: List<String>): List<User> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext emptyList()
            val payload = "{\"phoneHashes\":${json.encodeToString(phoneHashes)}}"
            val request = Request.Builder()
                .url("$baseUrl/api/users/discover-contacts")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<ContactDiscoveryResponse>(body).contacts
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "discoverContacts failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext emptyList()
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/api/users/search?q=$encoded")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<SearchUsersResponse>(body).results
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "searchUsers failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        about: String? = null,
        avatarUrl: String? = null
    ): User? = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext null
            val payload = json.encodeToString(ProfileUpdatePayload(displayName, username, about, avatarUrl))
            val request = Request.Builder()
                .url("$baseUrl/api/users/me")
                .header("Authorization", "Bearer $token")
                .put(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<ProfileUpdateResponse>(body).user
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "updateProfile failed: ${e.message}", e)
            null
        }
    }

    suspend fun registerPushToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext false
            val payload = "{\"token\":${json.encodeToString(fcmToken)}}"
            val request = Request.Builder()
                .url("$baseUrl/api/users/push-token")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "registerPushToken failed: ${e.message}", e)
            false
        }
    }

    suspend fun fetchIceServers(): List<IceServerConfig> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext emptyList()
            val request = Request.Builder()
                .url("$baseUrl/api/calls/ice-servers")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<IceServersResponse>(body).iceServers
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "fetchIceServers failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun uploadEncryptedMedia(file: File, mimeType: String): String? = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/media/upload")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val respJson = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(body)
                respJson["fileUrl"]?.toString()?.replace("\"", "")
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "uploadEncryptedMedia failed: ${e.message}", e)
            null
        }
    }
}

// -----------------------------------------------------------------------------
// DTOs and Serializable Payloads
// -----------------------------------------------------------------------------

@Serializable
data class RegisterPayload(
    val username: String,
    val password: String,
    val displayName: String,
    val identityKeyBase64: String,
    val deviceName: String = "Android Device",
    val platform: String = "android"
)

@Serializable
data class LoginPayload(
    val username: String,
    val password: String,
    val identityKeyBase64: String? = null,
    val deviceName: String = "Android Device",
    val platform: String = "android"
)

@Serializable
data class CheckUsernameResponse(
    val username: String,
    val available: Boolean
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val refreshToken: String? = null,
    val user: User? = null,
    val error: String? = null
)

@Serializable
data class PublishBundlePayload(
    val identityPublicKeyBase64: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublicBase64: String,
    val signedPreKeySignatureBase64: String,
    val oneTimePreKeys: List<OneTimeKeyItem>
)

@Serializable
data class OneTimeKeyItem(
    val keyId: Int,
    val publicKeyBase64: String
)

@Serializable
data class PreKeyBundleResponse(
    val userId: String = "",
    val deviceId: String = "primary",
    val identityPublicKeyBase64: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublicBase64: String,
    val signedPreKeySignatureBase64: String,
    val oneTimePreKeyId: Int? = null,
    val oneTimePreKeyPublicBase64: String? = null
)

@Serializable
data class ContactDiscoveryResponse(
    val contacts: List<User> = emptyList()
)

@Serializable
data class SearchUsersResponse(
    val results: List<User> = emptyList()
)

@Serializable
data class ProfileUpdatePayload(
    val displayName: String? = null,
    val username: String? = null,
    val about: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ProfileUpdateResponse(
    val success: Boolean,
    val user: User? = null,
    val error: String? = null
)

@Serializable
data class IceServerConfig(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class IceServersResponse(
    val iceServers: List<IceServerConfig> = emptyList(),
    val ttlSec: Int = 86400
)
