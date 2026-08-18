package com.example.argus.crypto.ratchet

import com.example.argus.core.common.Base64Compat
import com.example.argus.crypto.keys.ArgusKeyPair
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object Curve25519Engine {
    private val secureRandom = SecureRandom()

    fun generateX25519KeyPair(): ArgusKeyPair {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        val privateKeyParams = keyPair.private as X25519PrivateKeyParameters
        val publicKeyParams = keyPair.public as X25519PublicKeyParameters

        return ArgusKeyPair(
            publicKeyBase64 = Base64Compat.encodeToString(publicKeyParams.encoded),
            privateKeyBase64 = Base64Compat.encodeToString(privateKeyParams.encoded)
        )
    }

    fun calculateSharedSecret(privateKeyBase64: String, publicKeyBase64: String): ByteArray {
        val privBytes = Base64Compat.decode(privateKeyBase64)
        val pubBytes = Base64Compat.decode(publicKeyBase64)

        val privateKeyParams = X25519PrivateKeyParameters(privBytes, 0)
        val publicKeyParams = X25519PublicKeyParameters(pubBytes, 0)

        val sharedSecret = ByteArray(32)
        privateKeyParams.generateSecret(publicKeyParams, sharedSecret, 0)
        return sharedSecret
    }

    fun generateEd25519KeyPair(): ArgusKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        val privateKeyParams = keyPair.private as Ed25519PrivateKeyParameters
        val publicKeyParams = keyPair.public as Ed25519PublicKeyParameters

        return ArgusKeyPair(
            publicKeyBase64 = Base64Compat.encodeToString(publicKeyParams.encoded),
            privateKeyBase64 = Base64Compat.encodeToString(privateKeyParams.encoded)
        )
    }

    fun sign(privateKeyBase64: String, data: ByteArray): ByteArray {
        val privBytes = Base64Compat.decode(privateKeyBase64)
        val privateKeyParams = Ed25519PrivateKeyParameters(privBytes, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKeyParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun verify(publicKeyBase64: String, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val pubBytes = Base64Compat.decode(publicKeyBase64)
            val publicKeyParams = Ed25519PublicKeyParameters(pubBytes, 0)
            val signer = Ed25519Signer()
            signer.init(false, publicKeyParams)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}
