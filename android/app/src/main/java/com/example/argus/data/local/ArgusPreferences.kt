package com.example.argus.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.argus.crypto.keys.ArgusKeyPair
import com.example.argus.crypto.keys.OneTimePreKey
import com.example.argus.crypto.vault.AndroidKeystoreProvider
import com.example.argus.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ArgusPreferences(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    var isUsingEncryptedPrefs: Boolean = true
        private set

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "argus_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { isUsingEncryptedPrefs = true }
    } catch (e: Throwable) {
        Log.e("ArgusPreferences", "EncryptedSharedPreferences initialization failed, falling back to standard prefs", e)
        isUsingEncryptedPrefs = false
        context.getSharedPreferences("argus_secure_prefs", Context.MODE_PRIVATE)
    }

    private val _currentUserFlow = MutableStateFlow<User?>(loadCurrentUser())
    val currentUserFlow: StateFlow<User?> = _currentUserFlow.asStateFlow()

    private val _isAppLockedFlow = MutableStateFlow<Boolean>(isAppLockEnabled())
    val isAppLockedFlow: StateFlow<Boolean> = _isAppLockedFlow.asStateFlow()

    fun getAuthToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun setAuthToken(token: String?) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    fun setRefreshToken(refreshToken: String?) {
        prefs.edit().putString("refresh_token", refreshToken).apply()
    }

    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "android_${UUID.randomUUID()}"
            setDeviceId(deviceId)
        }
        return deviceId
    }

    fun setDeviceId(deviceId: String) {
        prefs.edit().putString("device_id", deviceId).apply()
    }

    fun loadCurrentUser(): User? {
        val userJson = prefs.getString("current_user_json", null) ?: return null
        return try {
            json.decodeFromString<User>(userJson)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCurrentUser(user: User?) {
        if (user == null) {
            prefs.edit().remove("current_user_json").apply()
        } else {
            prefs.edit().putString("current_user_json", json.encodeToString(user)).apply()
        }
        _currentUserFlow.value = user
    }

    // --- Privacy & Security Settings ---

    fun isAppLockEnabled(): Boolean = prefs.getBoolean("pref_app_lock_enabled", false)
    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_app_lock_enabled", enabled).apply()
        _isAppLockedFlow.value = enabled
    }

    fun getAppLockPin(): String? = prefs.getString("pref_app_lock_pin", null)
    fun setAppLockPin(pin: String?) {
        prefs.edit().putString("pref_app_lock_pin", pin).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("pref_biometric_enabled", true)
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_biometric_enabled", enabled).apply()
    }

    fun isReadReceiptsEnabled(): Boolean = prefs.getBoolean("pref_read_receipts", true)
    fun setReadReceiptsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_read_receipts", enabled).apply()
    }

    fun isTypingIndicatorsEnabled(): Boolean = prefs.getBoolean("pref_typing_indicators", true)
    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_typing_indicators", enabled).apply()
    }

    fun isDataSaverEnabled(): Boolean = prefs.getBoolean("pref_data_saver", false)
    fun setDataSaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_data_saver", enabled).apply()
    }

    fun isEmergencyPrivacyActive(): Boolean = prefs.getBoolean("pref_emergency_privacy", false)
    fun setEmergencyPrivacyActive(active: Boolean) {
        prefs.edit().putBoolean("pref_emergency_privacy", active).apply()
    }

    fun getIdentityKeyPairJson(): String? = prefs.getString("identity_key_pair", null)
    fun setIdentityKeyPairJson(jsonStr: String) {
        prefs.edit().putString("identity_key_pair", jsonStr).apply()
    }

    fun getSignedPreKeyPairJson(): String? = prefs.getString("signed_pre_key_pair", null)
    fun setSignedPreKeyPairJson(jsonStr: String) {
        prefs.edit().putString("signed_pre_key_pair", jsonStr).apply()
    }

    fun getLastSignedPreKeyRotation(): Long = prefs.getLong("last_signed_prekey_rotation", 0L)
    fun setLastSignedPreKeyRotation(timestamp: Long) {
        prefs.edit().putLong("last_signed_prekey_rotation", timestamp).apply()
    }

    // --- One-Time PreKey Management for X3DH ---

    fun saveOneTimePreKeys(keys: List<OneTimePreKey>) {
        try {
            val encoded = json.encodeToString(keys)
            prefs.edit().putString("one_time_pre_keys", encoded).apply()
        } catch (e: Exception) {
            Log.e("ArgusPreferences", "Failed to save one-time prekeys", e)
        }
    }

    fun getOneTimePreKeys(): List<OneTimePreKey> {
        val raw = prefs.getString("one_time_pre_keys", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<OneTimePreKey>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAndConsumeOneTimePreKey(keyId: Int): ArgusKeyPair? {
        val currentKeys = getOneTimePreKeys().toMutableList()
        val targetIdx = currentKeys.indexOfFirst { it.keyId == keyId && !it.isUsed }
        if (targetIdx != -1) {
            val matched = currentKeys[targetIdx]
            currentKeys[targetIdx] = matched.copy(isUsed = true)
            saveOneTimePreKeys(currentKeys)
            return matched.keyPair
        }
        return null
    }

    fun getPhoneSalt(): String {
        var salt = prefs.getString("argus_phone_salt", null)
        if (salt == null) {
            val randomBytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(randomBytes)
            salt = com.example.argus.core.common.Base64Compat.encodeToString(randomBytes)
            prefs.edit().putString("argus_phone_salt", salt).apply()
        }
        return salt
    }

    fun getEmergencyRecoveryKey(): String? = prefs.getString("emergency_recovery_key", null)
    fun setEmergencyRecoveryKey(key: String?) {
        if (key == null) {
            prefs.edit().remove("emergency_recovery_key").apply()
        } else {
            prefs.edit().putString("emergency_recovery_key", key).apply()
        }
    }

    fun getServerUrl(): String {
        return prefs.getString("pref_server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString("pref_server_url", trimmed).apply()
    }

    fun getWebSocketUrl(): String {
        val server = getServerUrl()
        val wsBase = if (server.startsWith("https://", ignoreCase = true)) {
            server.replaceFirst("https://", "wss://", ignoreCase = true)
        } else if (server.startsWith("http://", ignoreCase = true)) {
            server.replaceFirst("http://", "ws://", ignoreCase = true)
        } else {
            "wss://$server"
        }
        return "$wsBase/ws"
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://argus-backend-5cg3.onrender.com"
        const val DEFAULT_WS_URL = "wss://argus-backend-5cg3.onrender.com/ws"
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        AndroidKeystoreProvider.deleteKeys()
        _currentUserFlow.value = null
        _isAppLockedFlow.value = false
    }
}
