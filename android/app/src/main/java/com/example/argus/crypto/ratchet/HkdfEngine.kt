package com.example.argus.crypto.ratchet

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HkdfEngine {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun deriveSecrets(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(inputKeyMaterial, salt, info))
        val output = ByteArray(length)
        hkdf.generateBytes(output, 0, length)
        return output
    }

    /**
     * HKDF Ratchet step: takes a root key and DH shared secret, derives a new root key (32 bytes) and chain key (32 bytes)
     */
    fun kdfRootKey(rootKey: ByteArray, dhSharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
        val derived = deriveSecrets(
            inputKeyMaterial = dhSharedSecret,
            salt = rootKey,
            info = "ArgusRatchetRootKDF_v1".toByteArray(Charsets.UTF_8),
            length = 64
        )
        val newRootKey = derived.copyOfRange(0, 32)
        val newChainKey = derived.copyOfRange(32, 64)
        return Pair(newRootKey, newChainKey)
    }

    /**
     * Symmetric Ratchet step: advances chain key and derives message key
     */
    fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        // Message key = HMAC-SHA256(chainKey, 0x01)
        val mac1 = Mac.getInstance(HMAC_ALGORITHM)
        mac1.init(SecretKeySpec(chainKey, HMAC_ALGORITHM))
        val messageKey = mac1.doFinal(byteArrayOf(0x01))

        // Next chain key = HMAC-SHA256(chainKey, 0x02)
        val mac2 = Mac.getInstance(HMAC_ALGORITHM)
        mac2.init(SecretKeySpec(chainKey, HMAC_ALGORITHM))
        val nextChainKey = mac2.doFinal(byteArrayOf(0x02))

        return Pair(nextChainKey, messageKey)
    }
}
