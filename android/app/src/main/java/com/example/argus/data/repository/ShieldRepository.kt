package com.example.argus.data.repository

import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.local.ArgusPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionHealth(
    val hasCamera: Boolean = true,
    val hasAudio: Boolean = true,
    val hasStorage: Boolean = true,
    val hasNotifications: Boolean = true,
    val hasContacts: Boolean = true
)

data class ShieldSecurityStatus(
    val privacyScore: Int, // 0 - 100
    val isE2EEActive: Boolean,
    val isAppLockActive: Boolean,
    val isBiometricActive: Boolean,
    val isChatLockActive: Boolean,
    val verifiedContactsCount: Int,
    val unverifiedContactsCount: Int,
    val isEmergencyPrivacyActive: Boolean,
    val activeDevicesCount: Int,
    val permissions: PermissionHealth,
    val issuesFound: List<String>
)

class ShieldRepository(
    private val preferences: ArgusPreferences,
    private val localStore: ArgusLocalStore
) {
    private var currentPermissionHealth = PermissionHealth()
    private val _statusFlow = MutableStateFlow(
        ShieldSecurityStatus(
            privacyScore = 90,
            isE2EEActive = true,
            isAppLockActive = false,
            isBiometricActive = true,
            isChatLockActive = false,
            verifiedContactsCount = 0,
            unverifiedContactsCount = 0,
            isEmergencyPrivacyActive = false,
            activeDevicesCount = 1,
            permissions = PermissionHealth(),
            issuesFound = emptyList()
        )
    )
    val statusFlow: StateFlow<ShieldSecurityStatus> = _statusFlow.asStateFlow()

    init {
        refresh()
    }

    fun updatePermissionHealth(health: PermissionHealth) {
        currentPermissionHealth = health
        refresh()
    }

    fun refresh() {
        try {
            _statusFlow.value = computeStatus()
        } catch (e: Throwable) {
            android.util.Log.w("ShieldRepository", "Failed to compute status", e)
        }
    }

    private fun computeStatus(): ShieldSecurityStatus {
        val contacts = try { localStore.contactsFlow.value.ifEmpty { localStore.loadContacts() } } catch (e: Throwable) { emptyList() }
        val verifiedCount = contacts.count { it.isVerified }
        val unverifiedCount = contacts.count { !it.isVerified }
        val isAppLock = try { preferences.isAppLockEnabled() } catch (e: Throwable) { false }
        val isBiometric = try { preferences.isBiometricEnabled() } catch (e: Throwable) { true }
        val isEmergency = try { preferences.isEmergencyPrivacyActive() } catch (e: Throwable) { false }

        val issues = mutableListOf<String>()
        var score = 100

        if (!isAppLock) {
            score -= 10
            issues.add("App Lock is not enabled (Enable PIN/Biometrics for device security)")
        }
        if (unverifiedCount > 0) {
            score -= 10
            issues.add("$unverifiedCount contact(s) have unverified 60-digit Safety Numbers")
        }

        val conversations = try { localStore.conversationsFlow.value.ifEmpty { localStore.loadConversations() } } catch (e: Throwable) { emptyList() }

        return ShieldSecurityStatus(
            privacyScore = score.coerceIn(0, 100),
            isE2EEActive = true,
            isAppLockActive = isAppLock,
            isBiometricActive = isBiometric,
            isChatLockActive = conversations.any { it.isLocked },
            verifiedContactsCount = verifiedCount,
            unverifiedContactsCount = unverifiedCount,
            isEmergencyPrivacyActive = isEmergency,
            activeDevicesCount = 1,
            permissions = currentPermissionHealth,
            issuesFound = issues
        )
    }

    fun toggleAppLock(enabled: Boolean) {
        preferences.setAppLockEnabled(enabled)
        refresh()
    }

    fun toggleBiometric(enabled: Boolean) {
        preferences.setBiometricEnabled(enabled)
        refresh()
    }

    fun triggerEmergencyLockdown() {
        preferences.setEmergencyPrivacyActive(true)
        preferences.setAppLockEnabled(true)
        refresh()
    }

    fun panicWipeAllData() {
        localStore.wipeAllData()
        preferences.clearAll()
        refresh()
    }
}
