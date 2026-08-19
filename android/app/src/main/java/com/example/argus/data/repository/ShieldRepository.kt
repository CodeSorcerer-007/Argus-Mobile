package com.example.argus.data.repository

import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.local.ArgusPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val issuesFound: List<String>
)

class ShieldRepository(
    private val preferences: ArgusPreferences,
    private val localStore: ArgusLocalStore
) {
    private val _statusFlow = MutableStateFlow(computeStatus())
    val statusFlow: StateFlow<ShieldSecurityStatus> = _statusFlow.asStateFlow()

    fun refresh() {
        _statusFlow.value = computeStatus()
    }

    private fun computeStatus(): ShieldSecurityStatus {
        val contacts = localStore.loadContacts()
        val verifiedCount = contacts.count { it.isVerified }
        val unverifiedCount = contacts.count { !it.isVerified }
        val isAppLock = preferences.isAppLockEnabled()
        val isBiometric = preferences.isBiometricEnabled()
        val isEmergency = preferences.isEmergencyPrivacyActive()

        val issues = mutableListOf<String>()
        var score = 100

        if (!isAppLock) {
            score -= 20
            issues.add("App Lock is not enabled (Enable PIN/Biometrics for device security)")
        }
        if (unverifiedCount > 0) {
            score -= 15
            issues.add("$unverifiedCount contact(s) have unverified 60-digit Safety Numbers")
        }

        return ShieldSecurityStatus(
            privacyScore = score.coerceIn(0, 100),
            isE2EEActive = true,
            isAppLockActive = isAppLock,
            isBiometricActive = isBiometric,
            isChatLockActive = localStore.loadConversations().any { it.isLocked },
            verifiedContactsCount = verifiedCount,
            unverifiedContactsCount = unverifiedCount,
            isEmergencyPrivacyActive = isEmergency,
            activeDevicesCount = 1,
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
