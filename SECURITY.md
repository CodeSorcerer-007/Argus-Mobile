# Security Model & Threat Architecture

## 1. Threat Model & Privacy Guarantees

Argus is engineered under a zero-trust model where the backend transport server is assumed to be untrusted.

### Zero-Knowledge Architecture
- **No Plaintext on Server**: All 1-to-1 and group message payloads, voice notes, media files, and WebRTC signaling SDP packets are encrypted on the client device before transmission over the network.
- **Private Key Isolation**: Private keys (Identity Key, Signed PreKey, One-Time PreKeys, and Vault Master Keys) are generated on-device and never transmitted to the server.
- **Hardware-Backed Keystore**: On Android devices, master wrapping keys reside in the Hardware Security Module (StrongBox Keymaster / Trusted Execution Environment).

---

## 2. Cryptographic Protocol Summary

- **Key Exchange**: Curve25519 (X25519) Diffie-Hellman
- **Handshake Protocol**: X3DH (Extended Triple Diffie-Hellman)
- **Continuous Ratchet**: Double Ratchet Algorithm (KDF chain symmetric ratchet + Diffie-Hellman asymmetric ratchet)
- **Authenticated Cipher**: AES-256 in Galois/Counter Mode (GCM) with 128-bit authentication tag and associated data (AD) validation
- **Hashing & Fingerprinting**: HKDF-SHA256 and iterated SHA-512 (5200 rounds) for 60-digit Safety Numbers

---

## 3. Forward Secrecy & Break-in Recovery

- **Forward Secrecy**: If a current message key is compromised, previous messages remain secure because message keys are deleted immediately upon decryption and cannot be derived in reverse from the chain key.
- **Post-Compromise Security (Break-in Recovery)**: If a ratchet state is temporarily compromised, subsequent DH ratchet cycles with fresh ephemeral key pairs restore absolute confidentiality on future turns.

---

## 4. Reporting Security Vulnerabilities

If you discover a security vulnerability in Argus, please report it via private disclosure to `security@argus-messenger.org`. All reports are acknowledged within 24 hours.
