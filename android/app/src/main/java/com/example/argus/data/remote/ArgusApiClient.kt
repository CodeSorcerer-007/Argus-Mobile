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

@Serializable
data class OtpRequestPayload(val phoneNumber: String)

@Serializable
data class OtpRequestResponse(
    val success: Boolean,
    val message: String? = null,
    val expiresInSec: Int? = null,
    val code: String? = null,
    val devCode: String? = null,
    val error: String? = null
)

@Serializable
data class OtpVerifyPayload(
    val phoneNumber: String,
    val code: String,
    val deviceName: String,
    val platform: String = "android",
    val identityKeyBase64: String,
    val displayName: String? = null
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
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
data class ContactDiscoveryResponse(
    val contacts: List<User> = emptyList()
)

class ArgusApiClient(
    private val getBaseUrl: () -> String = { "https://argus-backend-5cg3.onrender.com" },
    private val getAuthToken: () -> String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String
        get() = getBaseUrl().trim().removeSuffix("/")

    suspend fun requestOtp(phoneNumber: String): OtpRequestResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(OtpRequestPayload(phoneNumber))
            val request = Request.Builder()
                .url("$baseUrl/api/auth/request-otp")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext OtpRequestResponse(success = false, error = "Empty response")
                json.decodeFromString<OtpRequestResponse>(body)
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "requestOtp failed: ${e.message}", e)
            OtpRequestResponse(success = false, error = e.localizedMessage ?: "Connection error")
        }
    }

    suspend fun verifyOtp(
        phoneNumber: String,
        code: String,
        deviceName: String,
        identityKeyBase64: String,
        displayName: String? = null
    ): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                OtpVerifyPayload(
                    phoneNumber = phoneNumber,
                    code = code,
                    deviceName = deviceName,
                    identityKeyBase64 = identityKeyBase64,
                    displayName = displayName
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/api/auth/verify-otp")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext AuthResponse(success = false, error = "Empty server response")
                json.decodeFromString<AuthResponse>(body)
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "verifyOtp failed: ${e.message}", e)
            AuthResponse(success = false, error = "Connection error: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun publishPreKeyBundle(payload: PublishBundlePayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext false
            val jsonBody = json.encodeToString(payload)
            val request = Request.Builder()
                .url("$baseUrl/api/keys/publish-bundle")
                .header("Authorization", "Bearer $token")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "publishPreKeyBundle failed: ${e.message}", e)
            false
        }
    }

    suspend fun fetchTargetPreKeyBundle(targetUserId: String): PreKeyBundle? = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext null
            val request = Request.Builder()
                .url("$baseUrl/api/keys/bundle/$targetUserId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<PreKeyBundle>(body)
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgusApiClient", "fetchTargetPreKeyBundle failed: ${e.message}", e)
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
