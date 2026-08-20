<div align="center">
  <img src="Argus%20Logo.png" width="140" alt="Argus Logo" style="border-radius: 50%; box-shadow: 0 8px 32px rgba(16, 185, 129, 0.35);" />
  
  # 🛡️ Argus Mobile
  ### *Private Communication, Without Compromise.*

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![Signal Protocol](https://img.shields.io/badge/Signal%20Protocol-Double%20Ratchet-10B981?style=for-the-badge&logo=signal&logoColor=white)](https://signal.org/docs/)
  [![Android](https://img.shields.io/badge/Android-API%2026--36-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
  [![Node.js](https://img.shields.io/badge/Node.js-v20+-339933?style=for-the-badge&logo=nodedotjs&logoColor=white)](https://nodejs.org/)
  [![TypeScript](https://img.shields.io/badge/TypeScript-5.3-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
  [![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-10B981?style=for-the-badge&logo=jest&logoColor=white)](https://jestjs.io/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

  <p align="center">
    <strong>Argus</strong> is a production-grade, zero-knowledge, native Android secure messenger combining the <strong>gold-standard cryptographic privacy of Signal</strong>, the <strong>everyday speed & reliability of WhatsApp</strong>, and the <strong>power & customization of Telegram</strong> — wrapped in a bespoke <em>Obsidian Black & Emerald Green</em> Material 3 interface.
  </p>

  <p align="center">
    <a href="#-key-features">Key Features</a> •
    <a href="#-system-architecture">Architecture</a> •
    <a href="#-cryptographic-specification">Cryptography</a> •
    <a href="#-feature-matrix">Comparison</a> •
    <a href="#-quick-start-guide">Quick Start</a> •
    <a href="#-security--threat-model">Threat Model</a>
  </p>
</div>

---

## 🌟 Key Features

<table>
  <tr>
    <td width="50%">
      <h3>🔐 Signal Double Ratchet E2EE</h3>
      <ul>
        <li><strong>X3DH Handshake</strong> with 4-DH Pre-Key bundle exchange.</li>
        <li><strong>Per-message symmetric/asymmetric ratchets</strong> for forward & post-compromise secrecy.</li>
        <li>Out-of-order <strong>skipped message key buffer</strong> (up to 2,000 keys).</li>
        <li><strong>60-digit iterated SHA-512 Safety Numbers</strong> + QR code verification.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>💬 Flagship Chat Experience</h3>
      <ul>
        <li><strong>Instant messaging</strong> with live status ticks (Queued, Sent, Delivered, Read).</li>
        <li><strong>Voice notes</strong> with 20-bar animated waveform visualizer & scrubbing.</li>
        <li><strong>Rich media transfer</strong> (Photos, Videos, Audio, PDF, APK, Documents).</li>
        <li><strong>Disappearing messages</strong> (24h, 7d, 90d timers) & emoji reactions.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🛡️ Argus Shield</h3>
      <ul>
        <li><strong>Real-time Privacy Score meter</strong> (0–100) analyzing device exposure.</li>
        <li><strong>Hardware Keystore check</strong> & active device session audit.</li>
        <li><strong>Cryptographic Panic Wipe</strong>: One-tap hardware wipe of all local databases, ratchet states, and vaults.</li>
        <li><strong>Incognito Keyboard & Screen Security</strong> (FLAG_SECURE protection).</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🗄️ Biometric Argus Vault</h3>
      <ul>
        <li><strong>Hardware-encrypted local vault</strong> for private notes and files.</li>
        <li><strong>Android StrongBox TEE</strong> biometric key wrapping (Fingerprint / Face Unlock).</li>
        <li>Zero-cloud, zero-telemetry hardware enclave isolation.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🤖 On-Device Intelligence</h3>
      <ul>
        <li><strong>100% On-device privacy-preserving AI</strong> assistant.</li>
        <li><strong>Smart Context Chips</strong>: Detects phone numbers, links, dates, and addresses with 1-tap actions.</li>
        <li><strong>Tone rewrites</strong> (Professional, Concise, Friendly) & conversation summarization.</li>
        <li><strong>Multi-language offline translation</strong> (Tamil, Hindi, Spanish, French).</li>
      </ul>
    </td>
    <td width="50%">
      <h3>📞 WebRTC HD Calls</h3>
      <ul>
        <li><strong>End-to-End Encrypted Voice & Video Calling</strong>.</li>
        <li>Peer-to-peer WebRTC mesh with dynamic ICE (STUN/TURN) traversal.</li>
        <li>In-call controls: Front/Back camera flip, Audio route selector, and Picture-in-Picture (PiP).</li>
      </ul>
    </td>
  </tr>
</table>

---

## 📊 Feature Matrix

| Feature | WhatsApp | Telegram | Signal | **Argus Mobile** |
|---|:---:|:---:|:---:|:---:|
| **Default End-to-End Encryption** | ⚠️ Closed-source | ❌ Opt-in only | ✅ Open-source | **✅ Signal Double Ratchet** |
| **Zero-Knowledge Architecture** | ❌ Metadata logged | ❌ Server-stored chats | ✅ Zero-knowledge | **✅ Full Zero-Knowledge** |
| **Biometric Local Vault** | ❌ | ❌ | ❌ | **✅ StrongBox TEE Vault** |
| **Cryptographic Panic Wipe** | ❌ | ❌ | ❌ | **✅ 1-Tap Emergency Wipe** |
| **Privacy Health Meter (Shield)** | ❌ | ❌ | ❌ | **✅ Argus Shield Dashboard** |
| **On-Device Private AI Assistant** | ❌ Cloud AI | ❌ | ❌ | **✅ 100% Offline AI** |
| **Modern Material 3 Obsidian UI** | ❌ Generic | ❌ Custom | ❌ Basic | **✅ Obsidian & Emerald Design** |
| **WebRTC HD Voice & Video Calls** | ✅ | ✅ | ✅ | **✅ P2P Encrypted WebRTC** |
| **Refresh Token Rotation (RTR)** | ⚠️ Proprietary | ⚠️ Proprietary | ✅ | **✅ Sliding-Window RTR** |

---

## 🏛️ System Architecture

```
Argus Ecosystem
│
├── android/                             # Native Android Application (Kotlin, Jetpack Compose, Material 3)
│   ├── app/src/main/java/com/example/argus/
│   │   ├── ArgusApplication.kt         # Dependency container & app lifecycle coordinator
│   │   ├── MainActivity.kt             # Edge-to-edge Compose host & system UI styling
│   │   ├── crypto/                     # Signal Double Ratchet Engine
│   │   │   ├── keys/                   # Curve25519, Ed25519, PreKey bundles & Safety Numbers
│   │   │   ├── ratchet/                # DoubleRatchetSession, HKDF-SHA256, AES-GCM
│   │   │   └── vault/                  # Biometric StrongBox TEE Vault storage engine
│   │   ├── data/                       # Local SQLite Database, DataStore & Network clients
│   │   │   ├── local/                  # Room/SQLite encryption, Contact sync
│   │   │   ├── remote/                 # ArgusApiClient, WebRTC Client, FCM push handler
│   │   │   └── repository/             # Auth, Message, Call, Vault, Shield & AI Repositories
│   │   ├── theme/                      # Obsidian Black & Emerald Green Material 3 Design System
│   │   └── ui/                         # 13 Modular Jetpack Compose Feature Packages
│   │       ├── auth/                   # Username/password authentication, emergency recovery key & biometric login
│   │       ├── main/                   # Bottom navigation host (Chats, Status, Calls, Vault, Shield, AI)
│   │       ├── chat/                   # WhatsApp-style chat bubbles, voice waveform player, reactions, file attachments
│   │       ├── security/               # 60-digit Safety Numbers & QR Code scanner/renderer
│   │       ├── call/                   # WebRTC 1-to-1 & Group Audio/Video calling screens
│   │       ├── vault/                  # Biometric-gated encrypted note & file storage UI
│   │       ├── shield/                 # Privacy Score dashboard, live permission audit & panic wipe
│   │       ├── ai/                     # On-device translator, summarizer & smart tone rewriter
│   │       ├── status/                 # 24-hour ephemeral rich status stories & viewer
│   │       ├── components/             # Reusable UI components & permission rationale dialogs
│   │       └── settings/               # Privacy controls, App lock, data saver & device management
│   └── app/build/outputs/apk/debug/    # Compiled Android Debug APK (Argus-debug.apk)
│
└── server/                              # Zero-Knowledge Backend Gateway (Node.js, TypeScript, WebSockets)
    ├── src/
    │   ├── routes/                     # Modular REST API endpoints (Auth, Keys, Users, Media, Groups, Calls)
    │   ├── ws/                         # Real-time WebSocket router (Delivery/Read receipts, Typing, WebRTC)
    │   ├── db/                         # Schema v2 Zero-Knowledge atomic database with automated migrations
    │   └── server.ts                   # Master server entrypoint & graceful shutdown lifecycle
    ├── dist/                           # Compiled production JavaScript bundle
    └── tests/                          # Automated Jest integration test suite (41/41 Passing)
```

---

## 🔒 Cryptographic Specification

```mermaid
sequenceDiagram
    autonumber
    actor Alice as 📱 Alice (Initiator)
    participant Server as 🌐 Argus Gateway
    actor Bob as 📱 Bob (Recipient)

    Note over Alice,Bob: 1. X3DH Session Initiation (4-DH Handshake)
    Bob->>Server: Publish PreKey Bundle (IK_B, SPK_B, Sig_B, OPK_B[1..n])
    Alice->>Server: Request Bob's PreKey Bundle
    Server-->>Alice: Return (IK_B, SPK_B, Sig_B, OPK_B_1) [Consumes OPK_B_1]
    Note over Alice: Verify Sig_B with IK_B<br/>DH1 = DH(IK_A, SPK_B)<br/>DH2 = DH(EK_A, IK_B)<br/>DH3 = DH(EK_A, SPK_B)<br/>DH4 = DH(EK_A, OPK_B_1)<br/>SK = HKDF(DH1 || DH2 || DH3 || DH4)
    
    Note over Alice,Bob: 2. Double Ratchet Per-Message Cycles
    Alice->>Server: Send Ratchet Encrypted Payload (EK_A, N, PN, Ciphertext, Tag)
    Server->>Bob: Relay Encrypted Message (Zero-Knowledge)
    Note over Bob: Compute SK & Ratchet DH State<br/>AES-256-GCM Decrypt with Bound Associated Data
    Bob-->>Server: ACK_DELIVERED
    Server-->>Alice: MESSAGE_STATUS (DELIVERED)
```

| Component | Cryptographic Primitive | Security Guarantee |
|---|---|---|
| **Asymmetric Key Exchange** | Curve25519 (X25519 ECDH) | 128-bit quantum-resistant curve security |
| **Digital Signatures** | Ed25519 | Forgery-proof identity & pre-key authentication |
| **Session Initiation** | X3DH (Extended Triple Diffie-Hellman) | Mutual identity authentication & forward secrecy |
| **Symmetric Ratchet** | KDF Chain Keys via HKDF-SHA256 | Per-message forward secrecy |
| **Asymmetric Ratchet** | Continuous Diffie-Hellman Key Exchange | Immediate post-compromise healing |
| **Authenticated Cipher** | AES-256-GCM (128-bit auth tag) | Confidentiality + ciphertext integrity |
| **Out-of-Order Handling** | Skipped Message Key Store | Reliable delivery through unreliable networks (up to 2000 keys) |
| **Identity Verification** | 60-digit iterated SHA-512 Safety Numbers | Protection against Man-in-the-Middle (MITM) attacks |
| **Local Keystore** | Android StrongBox TEE Hardware KeyStore | Keys never exposed to Android userland |

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Android Studio** Ladybug (2024.2+) or Command-Line Tools
- **JDK**: Java 21 (`OpenJDK 21.0.8`)
- **Node.js**: v20.x or v22.x+
- **npm**: v10.x+

---

### 2. Backend Gateway Setup

```bash
# 1. Clone repository
git clone https://github.com/CodeSorcerer-007/Argus-Mobile.git
cd Argus-Mobile/server

# 2. Install dependencies
npm install

# 3. Configure environment
cp .env.example .env

# 4. Compile TypeScript & run automated tests
npm test
npm run build

# 5. Start the production gateway server
npm start
```

*The server will start listening on `http://localhost:8080` (REST) and `ws://localhost:8080/ws` (WebSockets).*

---

### 3. Android Application Setup

```bash
# Navigate to Android directory
cd ../android

# Run cryptographic & AI unit test suite
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

*The generated APK binary is output to:*  
`android/app/build/outputs/apk/debug/app-debug.apk` (and mirrored to root [`Argus-debug.apk`](Argus-debug.apk)).

---

## 📡 REST API & WebSocket Protocol Reference

### REST Endpoints

| Method | Endpoint | Auth | Description |
|---|---|:---:|---|
| `GET` | `/health` | No | System health diagnostics, uptime & schema version |
| `GET` | `/api/auth/check-username/:username` | No | Check handle availability before registration |
| `POST` | `/api/auth/register` | No | Register username, password & receive 256-bit emergency recovery key |
| `POST` | `/api/auth/login` | No | Authenticate user with rate-limiting brute-force lockouts |
| `POST` | `/api/auth/verify-recovery-key` | No | Validate emergency recovery key for account recovery |
| `POST` | `/api/auth/reset-password` | No | Sovereign password reset using 256-bit emergency recovery key |
| `POST` | `/api/auth/refresh-token` | No | **Sliding-Window RTR**: Rotate access token & refresh token |
| `POST` | `/api/auth/logout` | No | Revoke refresh token & terminate sessions |
| `POST` | `/api/keys/publish-bundle` | Yes | Publish device X3DH pre-key bundle |
| `GET` | `/api/keys/status` | Yes | Inspect pre-key pool health & replenishment alerts |
| `POST` | `/api/keys/replenish` | Yes | Batch-upload additional one-time prekeys |
| `GET` | `/api/keys/bundle/:userId` | Yes | Consume 1 OTP key and fetch recipient pre-key bundle |
| `POST` | `/api/keys/bundles` | Yes | Batch-fetch recipient pre-key bundles |
| `GET` | `/api/users/me` | Yes | Fetch authenticated user profile |
| `PUT` | `/api/users/me` | Yes | Update profile name, bio & avatar |
| `DELETE` | `/api/users/me` | Yes | Permanently delete user account & purge tokens (GDPR/Play Store compliance) |
| `GET` | `/api/users/search` | Yes | Search users by username handle |
| `POST` | `/api/users/push-token` | Yes | Register Firebase Cloud Messaging (FCM) push token |
| `GET` | `/api/users/:userId` | Yes | Fetch public user profile |
| `POST` | `/api/groups/create` | Yes | Create end-to-end encrypted group chat |
| `GET` | `/api/groups/:groupId` | Yes | Get group information & member list |
| `PUT` | `/api/groups/:groupId` | Yes | Update group name, avatar & topic (Admin only) |
| `POST` | `/api/groups/:groupId/add-members` | Yes | Add members to group (Admin only) |
| `POST` | `/api/groups/:groupId/remove-member` | Yes | Remove member from group (Admin only) |
| `POST` | `/api/groups/:groupId/leave` | Yes | Leave group chat |
| `DELETE` | `/api/groups/:groupId` | Yes | Delete group chat (Admin only) |
| `POST` | `/api/media/upload` | Yes | Upload encrypted media binary blob |
| `GET` | `/api/media/download/:file` | No | Download media with chunked HTTP 206 range streaming |
| `GET` | `/api/calls/ice-servers` | Yes | Fetch STUN/TURN ICE server credentials |

### WebSocket Event Protocol (`ws://localhost:8080/ws`)

```json
// Client -> Server: Authenticate Connection
{ "type": "AUTH", "token": "<JWT_BEARER_TOKEN>", "deviceId": "<DEVICE_UUID>" }

// Client -> Server: Send E2EE Message
{
  "type": "SEND_MESSAGE",
  "payload": {
    "id": "msg-uuid-1234",
    "senderId": "alice",
    "recipientId": "bob",
    "ciphertext": "AES256GCM_BASE64_PAYLOAD",
    "iv": "IV_BASE64",
    "ephemeralKey": "CURVE25519_EPHEMERAL_PUB",
    "sequenceNumber": 42
  }
}

// Server -> Client: Real-Time Delivery Relay
{ "type": "NEW_MESSAGE", "payload": { ... } }

// Delivery & Read Receipts
{ "type": "ACK_DELIVERED", "senderId": "alice", "messageId": "msg-uuid-1234" }
{ "type": "ACK_READ", "senderId": "alice", "messageId": "msg-uuid-1234" }
```

---

## 🛡️ Security & Threat Model

1. **Zero-Knowledge Server Principle**: The server has zero access to message contents, media payloads, contact phone books, voice notes, or private keys. All payload encryption is performed client-side on Android before hitting the network.
2. **Contact Discovery Privacy**: Phone numbers are hashed using a 256-bit salted SHA-256 digest (`Argus_Salt_2026:number`). Plaintext phone books are never uploaded.
3. **Hardware Storage Isolation**: Root keys and vault secrets are wrapped using the Android Keystore backed by **StrongBox Keymaster / TEE**. Keys are unextractable from user space even on compromised devices.
4. **Brute-Force & Replay Defenses**:
   - 5 consecutive invalid password attempts trigger an automatic 15-minute lockout.
   - Refresh Token Rotation (RTR) revokes consumed tokens immediately, neutralizing replay attacks.
5. **Path Traversal & Ingress Sanitization**: Media storage strictly uses UUID-based randomized filenames with path canonicalization checks to prevent directory traversal.

---

## 📄 License

Argus Mobile is open-source software licensed under the [MIT License](LICENSE).

<div align="center">
  <sub>Built with ❤️ for privacy, freedom, and uncompromising digital security.</sub>
</div>
