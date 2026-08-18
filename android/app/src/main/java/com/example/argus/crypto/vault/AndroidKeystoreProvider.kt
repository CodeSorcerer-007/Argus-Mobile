package com.example.argus.crypto.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
    }

    fun getVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(VAULT_KEY_ALIAS, null) as SecretKey
    }
}
