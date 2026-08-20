package com.example.argus.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.argus.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArgusPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("argus_secure_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

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

    fun getDeviceId(): String? {
        return prefs.getString("device_id", null)
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

    fun getEmergencyRecoveryKey(): String? = prefs.getString("emergency_recovery_key", null)
    fun setEmergencyRecoveryKey(key: String?) {
        if (key == null) {
            prefs.edit().remove("emergency_recovery_key").apply()
        } else {
            prefs.edit().putString("emergency_recovery_key", key).apply()
        }
    }

    fun getServerUrl(): String {
        return DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString("pref_server_url", trimmed).apply()
    }

    fun getWebSocketUrl(): String {
        return "wss://argus-backend-5cg3.onrender.com/ws"
    }

    companion object {
        const val DEFAULT_SERVER_URL = "https://argus-backend-5cg3.onrender.com"
        const val DEFAULT_WS_URL = "wss://argus-backend-5cg3.onrender.com/ws"
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _currentUserFlow.value = null
        _isAppLockedFlow.value = false
    }
}
