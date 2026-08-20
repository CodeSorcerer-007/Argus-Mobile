package com.example.argus.crypto.ratchet

import com.example.argus.core.common.Base64Compat
import com.example.argus.crypto.keys.ArgusKeyPair
import com.example.argus.crypto.keys.PreKeyBundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RatchetWireMessage(
    val dhPublicKeyBase64: String,
    val sequenceNumber: Int,
    val previousChainLength: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

@Serializable
data class SerializedSessionState(
    val rootKeyBase64: String,
    val sendingChainKeyBase64: String?,
    val receivingChainKeyBase64: String?,
    val dhSendingKeyPair: ArgusKeyPair,
    val dhReceivingPublicKeyBase64: String?,
    val sequenceNumberSending: Int,
    val sequenceNumberReceiving: Int,
    val previousChainLength: Int,
    val skippedMessageKeys: Map<String, String>
)

class DoubleRatchetSession(
    private var rootKey: ByteArray,
    private var sendingChainKey: ByteArray?,
    private var receivingChainKey: ByteArray?,
    private var dhSendingKeyPair: ArgusKeyPair,
    private var dhReceivingPublicKeyBase64: String?,
    private var sequenceNumberSending: Int = 0,
    private var sequenceNumberReceiving: Int = 0,
    private var previousChainLength: Int = 0,
    private val skippedMessageKeys: MutableMap<String, ByteArray> = mutableMapOf()
) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Initialize Alice's side (Initiator) via X3DH with Bob's PreKeyBundle
         */
        fun initializeInitiator(
            aliceIdentityKeyPair: ArgusKeyPair,
            bobBundle: PreKeyBundle
        ): Pair<DoubleRatchetSession, ArgusKeyPair> {
            val aliceEphemeralKeyPair = Curve25519Engine.generateX25519KeyPair()

            // DH1 = DH(AliceIdentity, BobSignedPreKey)
            val dh1 = Curve25519Engine.calculateSharedSecret(
                aliceIdentityKeyPair.privateKeyBase64,
                bobBundle.signedPreKeyPublicBase64
            )
            // DH2 = DH(AliceEphemeral, BobIdentity)
            val dh2 = Curve25519Engine.calculateSharedSecret(
                aliceEphemeralKeyPair.privateKeyBase64,
                bobBundle.identityPublicKeyBase64
            )
            // DH3 = DH(AliceEphemeral, BobSignedPreKey)
            val dh3 = Curve25519Engine.calculateSharedSecret(
                aliceEphemeralKeyPair.privateKeyBase64,
                bobBundle.signedPreKeyPublicBase64
            )
            // DH4 = DH(AliceEphemeral, BobOneTimePreKey) if available
            val dh4 = if (bobBundle.oneTimePreKeyPublicBase64 != null) {
                Curve25519Engine.calculateSharedSecret(
                    aliceEphemeralKeyPair.privateKeyBase64,
                    bobBundle.oneTimePreKeyPublicBase64
                )
            } else {
                ByteArray(0)
            }

            val masterSecret = dh1 + dh2 + dh3 + dh4
            val sharedKey = HkdfEngine.deriveSecrets(
                inputKeyMaterial = masterSecret,
                salt = "ArgusX3DH_Salt_v1".toByteArray(Charsets.UTF_8),
                info = "ArgusSessionMasterSecret".toByteArray(Charsets.UTF_8),
                length = 32
            )

            // Alice performs first DH ratchet step using Bob's SignedPreKey as initial remote DH key
            val aliceDhRatchetPair = Curve25519Engine.generateX25519KeyPair()
            val dhShared = Curve25519Engine.calculateSharedSecret(
                aliceDhRatchetPair.privateKeyBase64,
                bobBundle.signedPreKeyPublicBase64
            )
            val (newRootKey, newSendingChainKey) = HkdfEngine.kdfRootKey(sharedKey, dhShared)

            val session = DoubleRatchetSession(
                rootKey = newRootKey,
                sendingChainKey = newSendingChainKey,
                receivingChainKey = null,
                dhSendingKeyPair = aliceDhRatchetPair,
                dhReceivingPublicKeyBase64 = bobBundle.signedPreKeyPublicBase64,
                sequenceNumberSending = 0,
                sequenceNumberReceiving = 0,
                previousChainLength = 0
            )

            return Pair(session, aliceEphemeralKeyPair)
        }

        /**
         * Initialize Bob's side (Receiver) via X3DH from Alice's initial handshake keys
         */
        fun initializeReceiver(
            bobIdentityKeyPair: ArgusKeyPair,
            bobSignedPreKeyPair: ArgusKeyPair,
            bobOneTimePreKeyPair: ArgusKeyPair?,
            aliceIdentityPublicKeyBase64: String,
            aliceEphemeralPublicKeyBase64: String,
            aliceInitialDhRatchetPublicKeyBase64: String
        ): DoubleRatchetSession {
            // DH1 = DH(BobSignedPreKey, AliceIdentity)
            val dh1 = Curve25519Engine.calculateSharedSecret(
                bobSignedPreKeyPair.privateKeyBase64,
                aliceIdentityPublicKeyBase64
            )
            // DH2 = DH(BobIdentity, AliceEphemeral)
            val dh2 = Curve25519Engine.calculateSharedSecret(
                bobIdentityKeyPair.privateKeyBase64,
                aliceEphemeralPublicKeyBase64
            )
            // DH3 = DH(BobSignedPreKey, AliceEphemeral)
            val dh3 = Curve25519Engine.calculateSharedSecret(
                bobSignedPreKeyPair.privateKeyBase64,
                aliceEphemeralPublicKeyBase64
            )
            // DH4 = DH(BobOneTimePreKey, AliceEphemeral)
            val dh4 = if (bobOneTimePreKeyPair != null) {
                Curve25519Engine.calculateSharedSecret(
                    bobOneTimePreKeyPair.privateKeyBase64,
                    aliceEphemeralPublicKeyBase64
                )
            } else {
                ByteArray(0)
            }

            val masterSecret = dh1 + dh2 + dh3 + dh4
            val sharedKey = HkdfEngine.deriveSecrets(
                inputKeyMaterial = masterSecret,
                salt = "ArgusX3DH_Salt_v1".toByteArray(Charsets.UTF_8),
                info = "ArgusSessionMasterSecret".toByteArray(Charsets.UTF_8),
                length = 32
            )

            // Bob receives Alice's initial DH ratchet key and derives his initial receiving chain key
            val dhShared = Curve25519Engine.calculateSharedSecret(
                bobSignedPreKeyPair.privateKeyBase64,
                aliceInitialDhRatchetPublicKeyBase64
            )
            val (rootAfterDh, recvChainKey) = HkdfEngine.kdfRootKey(sharedKey, dhShared)

            val session = DoubleRatchetSession(
                rootKey = rootAfterDh,
                sendingChainKey = null,
                receivingChainKey = recvChainKey,
                dhSendingKeyPair = bobSignedPreKeyPair,
                dhReceivingPublicKeyBase64 = aliceInitialDhRatchetPublicKeyBase64,
                sequenceNumberSending = 0,
                sequenceNumberReceiving = 0,
                previousChainLength = 0
            )

            return session
        }

        fun fromSerialized(jsonString: String): DoubleRatchetSession {
            val state = json.decodeFromString<SerializedSessionState>(jsonString)
            val skippedMap = state.skippedMessageKeys.mapValues {
                Base64Compat.decode(it.value)
            }.toMutableMap()

            return DoubleRatchetSession(
                rootKey = Base64Compat.decode(state.rootKeyBase64),
                sendingChainKey = state.sendingChainKeyBase64?.let { Base64Compat.decode(it) },
                receivingChainKey = state.receivingChainKeyBase64?.let { Base64Compat.decode(it) },
                dhSendingKeyPair = state.dhSendingKeyPair,
                dhReceivingPublicKeyBase64 = state.dhReceivingPublicKeyBase64,
                sequenceNumberSending = state.sequenceNumberSending,
                sequenceNumberReceiving = state.sequenceNumberReceiving,
                previousChainLength = state.previousChainLength,
                skippedMessageKeys = skippedMap
            )
        }
    }

    @Synchronized
    fun serialize(): String {
        val skippedMap = skippedMessageKeys.mapValues {
            Base64Compat.encodeToString(it.value)
        }
        val state = SerializedSessionState(
            rootKeyBase64 = Base64Compat.encodeToString(rootKey),
            sendingChainKeyBase64 = sendingChainKey?.let { Base64Compat.encodeToString(it) },
            receivingChainKeyBase64 = receivingChainKey?.let { Base64Compat.encodeToString(it) },
            dhSendingKeyPair = dhSendingKeyPair,
            dhReceivingPublicKeyBase64 = dhReceivingPublicKeyBase64,
            sequenceNumberSending = sequenceNumberSending,
            sequenceNumberReceiving = sequenceNumberReceiving,
            previousChainLength = previousChainLength,
            skippedMessageKeys = skippedMap
        )
        return json.encodeToString(state)
    }

    /**
     * Encrypt a plaintext message using the current sending chain key
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray? = null): RatchetWireMessage {
        val currentChainKey = sendingChainKey ?: run {
            // Generate new DH sending key pair and perform DH send ratchet
            dhSendingKeyPair = Curve25519Engine.generateX25519KeyPair()
            val dhShared = Curve25519Engine.calculateSharedSecret(
                dhSendingKeyPair.privateKeyBase64,
                dhReceivingPublicKeyBase64 ?: error("No remote DH public key set")
            )
            val (newRoot, newSendChain) = HkdfEngine.kdfRootKey(rootKey, dhShared)
            rootKey = newRoot
            sendingChainKey = newSendChain
            sequenceNumberSending = 0
            newSendChain
        }

        val (nextChainKey, messageKey) = HkdfEngine.kdfChainKey(currentChainKey)
        sendingChainKey = nextChainKey

        val ad = (associatedData ?: ByteArray(0)) +
                dhSendingKeyPair.publicKeyBase64.toByteArray(Charsets.UTF_8) +
                sequenceNumberSending.toString().toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmEngine.encrypt(messageKey, plaintext, ad)

        val wireMessage = RatchetWireMessage(
            dhPublicKeyBase64 = dhSendingKeyPair.publicKeyBase64,
            sequenceNumber = sequenceNumberSending,
            previousChainLength = previousChainLength,
            ivBase64 = encrypted.ivBase64,
            ciphertextBase64 = encrypted.ciphertextBase64
        )

        sequenceNumberSending++
        return wireMessage
    }

    /**
     * Decrypt an incoming ratchet wire message
     */
    @Synchronized
    fun decrypt(wireMessage: RatchetWireMessage, associatedData: ByteArray? = null): ByteArray {
        val skippedKeyId = "${wireMessage.dhPublicKeyBase64}:${wireMessage.sequenceNumber}"
        val skippedKey = skippedMessageKeys.remove(skippedKeyId)

        val messageKey = if (skippedKey != null) {
            skippedKey
        } else {
            // Check if remote party sent a new DH ratchet public key
            if (wireMessage.dhPublicKeyBase64 != dhReceivingPublicKeyBase64) {
                // Skip any pending keys in the previous receiving chain
                skipMessageKeys(wireMessage.previousChainLength)
                // Perform DH Ratchet
                performDhRatchet(wireMessage.dhPublicKeyBase64)
            }

            // Skip any missing message keys in the current receiving chain
            skipMessageKeys(wireMessage.sequenceNumber)

            val currentRecvChain = receivingChainKey ?: error("No receiving chain key available")
            val (nextChainKey, mKey) = HkdfEngine.kdfChainKey(currentRecvChain)
            receivingChainKey = nextChainKey
            sequenceNumberReceiving++
            mKey
        }

        val ad = (associatedData ?: ByteArray(0)) +
                wireMessage.dhPublicKeyBase64.toByteArray(Charsets.UTF_8) +
                wireMessage.sequenceNumber.toString().toByteArray(Charsets.UTF_8)

        return AesGcmEngine.decrypt(messageKey, wireMessage.ivBase64, wireMessage.ciphertextBase64, ad)
    }

    private fun performDhRatchet(remoteNewDhPublicKeyBase64: String) {
        previousChainLength = sequenceNumberSending
        sequenceNumberSending = 0
        sequenceNumberReceiving = 0
        dhReceivingPublicKeyBase64 = remoteNewDhPublicKeyBase64

        // DH Step 1: Receive ratchet
        val dhSharedRecv = Curve25519Engine.calculateSharedSecret(
            dhSendingKeyPair.privateKeyBase64,
            remoteNewDhPublicKeyBase64
        )
        val (rootAfterRecv, newRecvChain) = HkdfEngine.kdfRootKey(rootKey, dhSharedRecv)
        rootKey = rootAfterRecv
        receivingChainKey = newRecvChain

        // DH Step 2: Prepare new DH sending key pair and send chain for subsequent responses
        dhSendingKeyPair = Curve25519Engine.generateX25519KeyPair()
        val dhSharedSend = Curve25519Engine.calculateSharedSecret(
            dhSendingKeyPair.privateKeyBase64,
            remoteNewDhPublicKeyBase64
        )
        val (rootAfterSend, newSendChain) = HkdfEngine.kdfRootKey(rootKey, dhSharedSend)
        rootKey = rootAfterSend
        sendingChainKey = newSendChain
    }

    private fun skipMessageKeys(untilSequence: Int) {
        if (sequenceNumberReceiving + 2000 < untilSequence) {
            error("Too many skipped messages: $untilSequence")
        }
        val currentRecvChain = receivingChainKey ?: return
        var chain = currentRecvChain
        val remoteDh = dhReceivingPublicKeyBase64 ?: return

        while (sequenceNumberReceiving < untilSequence) {
            val (nextChain, skippedMessageKey) = HkdfEngine.kdfChainKey(chain)
            chain = nextChain
            val keyId = "$remoteDh:$sequenceNumberReceiving"

            // Evict oldest skipped keys if exceeding max memory capacity
            if (skippedMessageKeys.size >= 1000) {
                val oldestKey = skippedMessageKeys.keys.firstOrNull()
                if (oldestKey != null) {
                    skippedMessageKeys.remove(oldestKey)
                }
            }

            skippedMessageKeys[keyId] = skippedMessageKey
            sequenceNumberReceiving++
        }
        receivingChainKey = chain
    }
}
