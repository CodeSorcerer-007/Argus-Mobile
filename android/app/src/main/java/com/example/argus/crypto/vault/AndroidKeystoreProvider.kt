package com.example.argus.crypto.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object AndroidKeystoreProvider {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "ArgusMasterKeystoreKey_v1"
    private const val VAULT_KEY_ALIAS = "ArgusVaultMasterKey_v1"

    init {
        ensureKeyExists(MASTER_KEY_ALIAS, requireBiometric = false)
        ensureKeyExists(VAULT_KEY_ALIAS, requireBiometric = false)
    }

    private fun ensureKeyExists(alias: String, requireBiometric: Boolean) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (requireBiometric) {
                builder.setUserAuthenticationRequired(true)
            }

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }

    fun getMasterKey(): SecretKey {
        ensureKeyExists(MASTER_KEY_ALIAS, requireBiometric = false)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
    }

    fun getVaultKey(): SecretKey {
        ensureKeyExists(VAULT_KEY_ALIAS, requireBiometric = false)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(VAULT_KEY_ALIAS, null) as SecretKey
    }

    fun deleteKeys() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
            if (keyStore.containsAlias(VAULT_KEY_ALIAS)) {
                keyStore.deleteEntry(VAULT_KEY_ALIAS)
            }
        } catch (e: Exception) {
            android.util.Log.e("AndroidKeystoreProvider", "Failed to delete Keystore keys on wipe", e)
        }
    }
}
