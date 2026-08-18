package com.example.argus.crypto.vault

import com.example.argus.core.common.Base64Compat
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

object ArgusVaultCipher {
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private val secureRandom = SecureRandom()

    fun encryptBytes(data: ByteArray, aad: ByteArray? = null): EncryptedVaultBlob {
        val secretKey = AndroidKeystoreProvider.getVaultKey()
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (aad != null) {
            cipher.updateAAD(aad)
        }

        val ciphertext = cipher.doFinal(data)
        return EncryptedVaultBlob(
            ivBase64 = Base64Compat.encodeToString(iv),
            ciphertextBase64 = Base64Compat.encodeToString(ciphertext)
        )
    }

    fun decryptBytes(ivBase64: String, ciphertextBase64: String, aad: ByteArray? = null): ByteArray {
        val secretKey = AndroidKeystoreProvider.getVaultKey()
        val iv = Base64Compat.decode(ivBase64)
        val ciphertext = Base64Compat.decode(ciphertextBase64)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (aad != null) {
            cipher.updateAAD(aad)
        }

        return cipher.doFinal(ciphertext)
    }

    fun encryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val secretKey = AndroidKeystoreProvider.getVaultKey()
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        // Write IV as header (12 bytes)
        outputStream.write(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherOutputStream(outputStream, cipher).use { cos ->
            inputStream.copyTo(cos)
        }
    }

    fun decryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val secretKey = AndroidKeystoreProvider.getVaultKey()
        val iv = ByteArray(IV_LENGTH_BYTES)
        val bytesRead = inputStream.read(iv)
        if (bytesRead != IV_LENGTH_BYTES) {
            error("Invalid encrypted stream: missing IV header")
        }

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherInputStream(inputStream, cipher).use { cis ->
            cis.copyTo(outputStream)
        }
    }
}

data class EncryptedVaultBlob(
    val ivBase64: String,
    val ciphertextBase64: String
)
