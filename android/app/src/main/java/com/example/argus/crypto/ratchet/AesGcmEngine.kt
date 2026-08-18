package com.example.argus.crypto.ratchet

import com.example.argus.core.common.Base64Compat
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ivBase64: String,
    val ciphertextBase64: String
)

object AesGcmEngine {
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private val secureRandom = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray? = null): EncryptedPayload {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        val ciphertextWithTag = cipher.doFinal(plaintext)

        return EncryptedPayload(
            ivBase64 = Base64Compat.encodeToString(iv),
            ciphertextBase64 = Base64Compat.encodeToString(ciphertextWithTag)
        )
    }

    fun decrypt(key: ByteArray, ivBase64: String, ciphertextBase64: String, associatedData: ByteArray? = null): ByteArray {
        val iv = Base64Compat.decode(ivBase64)
        val ciphertextWithTag = Base64Compat.decode(ciphertextBase64)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertextWithTag)
    }
}
