# Argus — Production-Grade Native Android Secure Messenger

> *"Private communication, without compromise."*

Argus is a production-grade, privacy-first native Android messaging platform that blends the simplicity and reliability of WhatsApp, the power and flexibility of Telegram, and the gold-standard cryptographic privacy of Signal, wrapped in a bespoke Obsidian & Emerald modern Material 3 aesthetic.

---

## 🏛️ System Architecture

```
Argus Ecosystem
│
├── android/                             # Native Android Application (Kotlin, Jetpack Compose, Material 3)
│   ├── app/src/main/java/com/example/argus/
│   │   ├── ArgusApplication.kt         # Application container & dependency injection
│   │   ├── MainActivity.kt             # Edge-to-edge Compose host
│   │   ├── core/                       # Base64 compatibility, common utilities
│   │   ├── crypto/                     # Signal Double Ratchet, Curve25519, X3DH, Safety Numbers
│   │   ├── data/                       # Local SQLite store, DataStore preferences, WebSocket & REST clients
│   │   ├── theme/                      # Obsidian Black & Emerald Green Material 3 Design System
│   │   └── ui/                         # Jetpack Compose UI Screens & Navigation
│   │       ├── auth/                   # Welcome, Phone Number input, 6-digit OTP verification
│   │       ├── main/                   # MainScreen (Chats, Favorites, Calls, Contacts tabs)
│   │       ├── chat/                   # Flagship ChatScreen (bubbles, rich composer, voice, reactions)
│   │       ├── security/               # 60-digit Safety Numbers & QR Code verification
│   │       ├── call/                   # WebRTC 1-to-1 & Group Audio/Video Calling UI
│   │       ├── vault/                  # Biometric hardware-backed encrypted secret notes & files
│   │       ├── shield/                 # Privacy Score dashboard, security audit & panic wipe
│   │       ├── ai/                     # On-device translator, summarizer & smart context engine
│   │       └── settings/               # Privacy controls, App lock, data saver, account management
│   └── app/build/outputs/apk/debug/    # Compiled Android Debug APK (app-debug.apk)
│
└── server/                              # Zero-Knowledge Real-Time Backend (Node.js, TypeScript, WebSockets)
    ├── src/
    │   ├── api/                        # REST endpoints (OTP Auth, PreKey bundles, Contacts, Media)
    │   ├── ws/                         # Real-time WebSocket router (Delivery/Read receipts, Typing, WebRTC)
    │   ├── db/                         # Zero-knowledge persistence layer
    │   └── server.ts                   # Master server entrypoint
    ├── dist/                           # Compiled production JavaScript bundle
    └── tests/                          # Automated Jest integration test suite (100% Passing)
```

---

## 🔒 Cryptographic Specification

| Feature | Implementation Detail |
|---|---|
| **Asymmetric Key Exchange** | Curve25519 (X25519 Diffie-Hellman) |
| **Digital Signatures** | Ed25519 |
| **Session Handshake** | X3DH (Extended Triple Diffie-Hellman) with Pre-Key Bundles |
| **Message Ratchet** | Signal Double Ratchet (KDF Symmetric Ratchet + Asymmetric DH Ratchet) |
| **Authenticated Cipher** | AES-256-GCM (128-bit authentication tag + Associated Data verification) |
| **Key Derivation** | HKDF-SHA256 (Extract & Expand) |
| **Key Storage** | Android Keystore (StrongBox TEE / Hardware-backed master key wrapping) |
| **Safety Numbers** | 60-digit numeric fingerprint derived from iterated SHA-512 (5200 rounds) + QR Code |
| **Local Vault** | AES-256-GCM stream & block encryption gated by BiometricPrompt |

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Android SDK**: API 26 - 36 (Java 21 / OpenJDK 21)
- **Node.js**: v20+ / v24+
- **npm**: v10+

---

### 2. Running the Backend Server
```bash
cd server
npm install
npm run build
npm start
```
*The server will start on `http://localhost:8080` and `ws://localhost:8080/ws`.*

To run the backend integration test suite:
```bash
npm test
```

---

### 3. Building & Testing the Android App
```bash
cd android

# Run all Cryptographic and AI unit tests:
./gradlew testDebugUnitTest

# Assemble the debug APK:
./gradlew assembleDebug
```
*The generated APK is located at `android/app/build/outputs/apk/debug/app-debug.apk`.*

---

## 📱 Features & Highlights

1. **WhatsApp Simplicity + Telegram Power**: Instant message delivery with receipts (Queued, Sent, Delivered, Read), voice messages with waveform visualizer, multimedia attachments (photos, videos, audio, PDF, APK, ZIP), and emoji reactions.
2. **Signal-Grade End-to-End Encryption**: Zero-knowledge architecture where the server never receives plaintext messages or private keys. Every conversation ratchets keys forward and post-compromise.
3. **Argus Shield**: Central privacy score meter, active device audit, emergency lockdown, and cryptographic panic wipe.
4. **Argus Vault**: Hardware-backed biometric encrypted local storage for private secret notes and confidential files.
5. **On-Device Intelligence**: Privacy-preserving AI assistant for message summarization, tone rewriting (Professional, Concise, Friendly), universal translation (Tamil, Hindi, Spanish, French), and Smart Context action chips (Calendar, Maps, Phone, URL).
6. **WebRTC Calling**: High-performance encrypted voice and video calling with PIP, front/back camera toggles, and audio route controls.
