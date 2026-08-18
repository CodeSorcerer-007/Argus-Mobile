package com.example.argus.crypto.keys

import kotlinx.serialization.Serializable

@Serializable
data class ArgusKeyPair(
    val publicKeyBase64: String,
    val privateKeyBase64: String
)

@Serializable
data class SignedPreKey(
    val keyId: Int,
    val keyPair: ArgusKeyPair,
    val signatureBase64: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class OneTimePreKey(
    val keyId: Int,
    val keyPair: ArgusKeyPair,
    val isUsed: Boolean = false
)

@Serializable
data class PreKeyBundle(
    val userId: String,
    val deviceId: String,
    val identityPublicKeyBase64: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublicBase64: String,
    val signedPreKeySignatureBase64: String,
    val oneTimePreKeyId: Int? = null,
    val oneTimePreKeyPublicBase64: String? = null
)
