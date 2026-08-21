package com.example.argus.data.remote

import android.util.Log
import com.example.argus.crypto.keys.PreKeyBundle
import com.example.argus.crypto.vault.ArgusVaultCipher
import com.example.argus.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ArgusApiClient(
    private val getBaseUrl: () -> String,
    private val getAuthToken: () -> String?,
    private val getRefreshToken: () -> String? = { null },
    private val onTokenRefreshed: (String) -> Unit = {}
) {
    private val baseUrl: String get() = getBaseUrl().trimEnd('/')

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Retry Interceptor for transient network / 5xx errors
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null
        val maxRetries = 3

        for (attempt in 0 until maxRetries) {
            try {
                response = chain.proceed(request)
                if (response.isSuccessful || response.code < 500 || attempt == maxRetries - 1) {
                    return@Interceptor response
                }
                // Server 5xx error, close and retry with backoff
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries - 1) {
                    throw e
                }
            }
            try {
                Thread.sleep((300L * (1 shl attempt)) + (0..100).random())
            } catch (ignored: InterruptedException) {}
        }
        response ?: throw (lastException ?: IOException("Request failed after $maxRetries retries"))
    }

    // Auto-refresh token on 401 Unauthorized
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        var response = chain.proceed(original)

        if (response.code == 401 && !original.url.encodedPath.contains("/api/auth/")) {
            val rToken = getRefreshToken()
            if (!rToken.isNullOrBlank()) {
                response.close()
                synchronized(this) {
                    val refreshedToken = runBlockingRefreshToken(rToken)
                    if (refreshedToken != null) {
                        onTokenRefreshed(refreshedToken)
                        val newRequest = original.newBuilder()
                            .header("Authorization", "Bearer $refreshedToken")
                            .build()
                        response = chain.proceed(newRequest)
                    }
                }
            }
        }
        response
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(retryInterceptor)
        .addInterceptor(authInterceptor)
        .build()

    private fun runBlockingRefreshToken(refreshToken: String): String? {
        return try {
            val payload = "{\"refreshToken\":\"$refreshToken\"}"
            val request = Request.Builder()
                .url("$baseUrl/api/auth/refresh")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val authResp = json.decodeFromString<AuthResponse>(body)
                authResp.token
            }
        } catch (e: Exception) {
            Log.e("ArgusApiClient", "Failed to refresh token", e)
            null
        }
    }

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
                parseAuthResponse(response)
            }
        } catch (e: Exception) {
            Log.e("ArgusApiClient", "register failed: ${e.message}", e)
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
                parseAuthResponse(response)
            }
        } catch (e: Exception) {
            Log.e("ArgusApiClient", "login failed: ${e.message}", e)
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

    suspend fun verifyRecoveryKey(username: String, recoveryKey: String): VerifyRecoveryKeyResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(VerifyRecoveryKeyPayload(username.trim().lowercase(), recoveryKey.trim()))
            val request = Request.Builder()
                .url("$baseUrl/api/auth/verify-recovery-key")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.isNotEmpty()) {
                    try {
                        json.decodeFromString<VerifyRecoveryKeyResponse>(body)
                    } catch (e: Exception) {
                        VerifyRecoveryKeyResponse(valid = false, error = "Server returned status ${response.code}")
                    }
                } else {
                    VerifyRecoveryKeyResponse(valid = false, error = "Server returned empty response (${response.code})")
                }
            }
        } catch (e: Exception) {
            VerifyRecoveryKeyResponse(valid = false, error = e.localizedMessage ?: "Network error")
        }
    }

    suspend fun resetPassword(
        username: String,
        newPassword: String,
        recoveryKey: String? = null,
        identityKeyBase64: String? = null,
        deviceName: String = "Android Device"
    ): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                ResetPasswordPayload(
                    username = username.trim().lowercase(),
                    newPassword = newPassword,
                    recoveryKey = recoveryKey?.trim()?.takeIf { it.isNotBlank() },
                    identityKeyBase64 = identityKeyBase64,
                    deviceName = deviceName
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/api/auth/reset-password")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                parseAuthResponse(response)
            }
        } catch (e: Exception) {
            Log.e("ArgusApiClient", "resetPassword failed: ${e.message}", e)
            AuthResponse(success = false, error = e.localizedMessage ?: "Network connection error")
        }
    }

    private fun parseAuthResponse(response: Response): AuthResponse {
        val body = response.body?.string() ?: ""
        if (body.isEmpty()) {
            return AuthResponse(success = false, error = "Server returned empty response (${response.code})")
        }
        val parsed = try {
            json.decodeFromString<AuthResponse>(body)
        } catch (e: Exception) {
            try {
                val jsonElement = json.parseToJsonElement(body) as? JsonObject
                val errMsg = jsonElement?.get("error")?.jsonPrimitive?.contentOrNull
                    ?: jsonElement?.get("message")?.jsonPrimitive?.contentOrNull
                AuthResponse(success = false, error = errMsg ?: "Server response error (${response.code})")
            } catch (ex: Exception) {
                AuthResponse(success = false, error = "Error ${response.code}")
            }
        }
        return if (!response.isSuccessful && parsed.error.isNullOrBlank()) {
            parsed.copy(success = false, error = parsed.message ?: "Request failed with status ${response.code}")
        } else {
            parsed
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
            Log.e("ArgusApiClient", "publishPreKeyBundle failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "fetchPreKeyBundle failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "discoverContacts failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "searchUsers failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "updateProfile failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "registerPushToken failed: ${e.message}", e)
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
            Log.e("ArgusApiClient", "fetchIceServers failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun uploadEncryptedMedia(sourceFile: File, mimeType: String): String? = withContext(Dispatchers.IO) {
        var tempEncFile: File? = null
        try {
            val token = getAuthToken()
            // Encrypt file stream to a temporary encrypted file
            tempEncFile = File.createTempFile("argus_enc_", ".bin")
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempEncFile).use { output ->
                    ArgusVaultCipher.encryptStream(input, output)
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", sourceFile.name + ".enc", tempEncFile.asRequestBody("application/octet-stream".toMediaType()))
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
            Log.e("ArgusApiClient", "uploadEncryptedMedia failed: ${e.message}", e)
            null
        } finally {
            tempEncFile?.delete()
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
    val username: String = "",
    val available: Boolean = false
)

@Serializable
data class AuthResponse(
    val success: Boolean = false,
    val token: String? = null,
    val refreshToken: String? = null,
    val recoveryKey: String? = null,
    val user: User? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ResetPasswordPayload(
    val username: String,
    val newPassword: String,
    val recoveryKey: String? = null,
    val identityKeyBase64: String? = null,
    val deviceName: String = "Android Device",
    val platform: String = "android"
)

@Serializable
data class VerifyRecoveryKeyPayload(
    val username: String,
    val recoveryKey: String
)

@Serializable
data class VerifyRecoveryKeyResponse(
    val valid: Boolean = false,
    val requiresNewKey: Boolean = false,
    val message: String? = null,
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
    val identityPublicKeyBase64: String = "",
    val signedPreKeyId: Int = 0,
    val signedPreKeyPublicBase64: String = "",
    val signedPreKeySignatureBase64: String = "",
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
    val success: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val message: String? = null
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
