package com.example.argus.crypto

import com.example.argus.crypto.keys.OneTimePreKey
import com.example.argus.crypto.keys.PreKeyBundle
import com.example.argus.crypto.keys.SafetyNumberCalculator
import com.example.argus.crypto.keys.SignedPreKey
import com.example.argus.crypto.ratchet.AesGcmEngine
import com.example.argus.crypto.ratchet.Curve25519Engine
import com.example.argus.crypto.ratchet.DoubleRatchetSession
import com.example.argus.crypto.ratchet.HkdfEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoEngineTest {

    @Test
    fun testCurve25519DiffieHellman() {
        val alice = Curve25519Engine.generateX25519KeyPair()
        val bob = Curve25519Engine.generateX25519KeyPair()

        val secretAlice = Curve25519Engine.calculateSharedSecret(alice.privateKeyBase64, bob.publicKeyBase64)
        val secretBob = Curve25519Engine.calculateSharedSecret(bob.privateKeyBase64, alice.publicKeyBase64)

        assertEquals(32, secretAlice.size)
        assertEquals(32, secretBob.size)
        assertTrue(secretAlice.contentEquals(secretBob))
    }

    @Test
    fun testEd25519SignAndVerify() {
        val keyPair = Curve25519Engine.generateEd25519KeyPair()
        val data = "Hello Argus Secure Crypto!".toByteArray(Charsets.UTF_8)

        val signature = Curve25519Engine.sign(keyPair.privateKeyBase64, data)
        val isValid = Curve25519Engine.verify(keyPair.publicKeyBase64, data, signature)
        assertTrue(isValid)

        val tamperedData = "Hello Argus Secure Crypto? Tampered".toByteArray(Charsets.UTF_8)
        val isTamperedValid = Curve25519Engine.verify(keyPair.publicKeyBase64, tamperedData, signature)
        assertFalse(isTamperedValid)
    }

    @Test
    fun testAesGcmAuthenticatedEncryption() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val plaintext = "Argus Top Secret Payload 2026".toByteArray(Charsets.UTF_8)
        val ad = "associated-metadata-header".toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmEngine.encrypt(key, plaintext, ad)
        val decrypted = AesGcmEngine.decrypt(key, encrypted.ivBase64, encrypted.ciphertextBase64, ad)

        assertEquals("Argus Top Secret Payload 2026", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testDoubleRatchetFullExchangeAndRatcheting() {
        // 1. Setup Bob's Keys
        val bobIdentity = Curve25519Engine.generateX25519KeyPair()
        val bobSignedPreKeyPair = Curve25519Engine.generateX25519KeyPair()
        val bobOneTimePair = Curve25519Engine.generateX25519KeyPair()

        val bobBundle = PreKeyBundle(
            userId = "bob-id",
            deviceId = "bob-phone",
            identityPublicKeyBase64 = bobIdentity.publicKeyBase64,
            signedPreKeyId = 1,
            signedPreKeyPublicBase64 = bobSignedPreKeyPair.publicKeyBase64,
            signedPreKeySignatureBase64 = "dummy-sig",
            oneTimePreKeyId = 101,
            oneTimePreKeyPublicBase64 = bobOneTimePair.publicKeyBase64
        )

        // 2. Alice generates keys & initializes initiator session
        val aliceIdentity = Curve25519Engine.generateX25519KeyPair()
        val (aliceSession, aliceEphemeral) = DoubleRatchetSession.initializeInitiator(aliceIdentity, bobBundle)

        // 3. Alice encrypts initial message #1
        val msg1Text = "Hi Bob! This is encrypted using Signal Double Ratchet."
        val wireMsg1 = aliceSession.encrypt(msg1Text.toByteArray(Charsets.UTF_8))

        // 4. Bob receives handshake & initializes receiver session
        val bobSession = DoubleRatchetSession.initializeReceiver(
            bobIdentityKeyPair = bobIdentity,
            bobSignedPreKeyPair = bobSignedPreKeyPair,
            bobOneTimePreKeyPair = bobOneTimePair,
            aliceIdentityPublicKeyBase64 = aliceIdentity.publicKeyBase64,
            aliceEphemeralPublicKeyBase64 = aliceEphemeral.publicKeyBase64,
            aliceInitialDhRatchetPublicKeyBase64 = wireMsg1.dhPublicKeyBase64
        )

        // 5. Bob decrypts Alice's first message
        val decrypted1 = bobSession.decrypt(wireMsg1)
        assertEquals(msg1Text, String(decrypted1, Charsets.UTF_8))

        // 6. Alice sends message #2 (same sending chain)
        val msg2Text = "Second message from Alice in same chain."
        val wireMsg2 = aliceSession.encrypt(msg2Text.toByteArray(Charsets.UTF_8))
        val decrypted2 = bobSession.decrypt(wireMsg2)
        assertEquals(msg2Text, String(decrypted2, Charsets.UTF_8))

        // 7. Bob replies -> triggers DH ratchet on both sides
        val msg3Text = "Hey Alice! I received your encrypted messages securely."
        val wireMsg3 = bobSession.encrypt(msg3Text.toByteArray(Charsets.UTF_8))
        val decrypted3 = aliceSession.decrypt(wireMsg3)
        assertEquals(msg3Text, String(decrypted3, Charsets.UTF_8))

        // 8. Alice replies back -> triggers another DH ratchet (post-compromise security)
        val msg4Text = "Awesome! DH Ratchet successfully cycled."
        val wireMsg4 = aliceSession.encrypt(msg4Text.toByteArray(Charsets.UTF_8))
        val decrypted4 = bobSession.decrypt(wireMsg4)
        assertEquals(msg4Text, String(decrypted4, Charsets.UTF_8))
    }

    @Test
    fun testSafetyNumbersSymmetry() {
        val aliceId = "alice_001"
        val aliceKey = Curve25519Engine.generateX25519KeyPair().publicKeyBase64

        val bobId = "bob_002"
        val bobKey = Curve25519Engine.generateX25519KeyPair().publicKeyBase64

        val numberFromAlice = SafetyNumberCalculator.computeSafetyNumber(aliceId, aliceKey, bobId, bobKey)
        val numberFromBob = SafetyNumberCalculator.computeSafetyNumber(bobId, bobKey, aliceId, aliceKey)

        assertNotNull(numberFromAlice)
        assertEquals(numberFromAlice, numberFromBob)
        assertEquals(12, numberFromAlice.split(" ").size) // 12 groups of 5 digits = 60 digits
    }
}
