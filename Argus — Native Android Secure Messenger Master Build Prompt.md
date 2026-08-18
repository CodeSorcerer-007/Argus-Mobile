# Build "Argus" — Production-Grade Private Messaging Platform

You are a senior Android architect, cybersecurity engineer, backend engineer, UX designer, and product engineer.

Build a **complete, production-grade native Android messaging application called "Argus"**.

Argus should combine the strongest **core communication capabilities associated with WhatsApp and Telegram** into one modern application, while introducing genuinely useful features for modern communication.

This is NOT a UI prototype, mockup, proof of concept, or collection of placeholder screens.

Build a **real, functional Android application** with real authentication, real messaging, real encryption, real file transfer, real notifications, real voice/video communication, proper persistence, offline support, error handling, and a production-ready architecture.

Do not simply imitate proprietary source code, assets, trademarks, or exact UI implementations of existing applications. Recreate the underlying communication capabilities with an original Argus identity and UX.

---

# 1. PRODUCT IDENTITY

Application name:

**Argus**

Concept:

> "Private communication, without compromise."

Argus should feel like a combination of:

- WhatsApp's simplicity and reliability
- Telegram's flexibility and powerful messaging
- Signal's privacy-first architecture
- Google Messages' polished communication experience
- Google Drive-like file handling
- Modern AI-assisted communication
- Modern Android design

But Argus must have its own visual identity.

The application should feel:

- Premium
- Fast
- Private
- Minimal
- Modern
- Extremely responsive
- Reliable
- Technically sophisticated

Avoid making it look like a WhatsApp clone.

---

# 2. TECHNOLOGY REQUIREMENTS

Build it as a **true native Android application**.

Preferred stack:

- Kotlin
- Jetpack Compose
- Material 3
- Android SDK
- Kotlin Coroutines
- Kotlin Flow
- ViewModel
- Navigation Compose
- Room
- WorkManager
- DataStore
- Hilt
- Retrofit/OkHttp where appropriate
- WebSocket for realtime messaging
- Firebase Cloud Messaging for push notifications where appropriate
- Android Keystore
- BiometricPrompt
- WebRTC for voice/video communication
- libsignal / Signal Protocol implementation for E2EE

Use modern Android architecture.

Recommended architecture:

**Clean Architecture + MVVM**

Structure the project into logical modules such as:

- app
- core
- data
- domain
- presentation
- crypto
- messaging
- calls
- media
- notifications
- storage
- networking

Keep security-sensitive functionality isolated.

Do NOT place everything inside one Activity or one giant Kotlin file.

---

# 3. BACKEND

Argus requires a real backend.

Design and implement a production-ready backend architecture capable of handling:

- Authentication
- User accounts
- Device registration
- Public/private keys
- Contacts
- Conversations
- Messages
- Message delivery
- Message acknowledgements
- Presence
- Typing indicators
- File metadata
- File uploads
- File downloads
- Push notification tokens
- Group management
- Group encryption
- Call signalling
- Abuse prevention
- Rate limiting
- Session/device management

The backend should be designed as a **zero-knowledge / privacy-preserving system wherever practical**.

The server should NOT have access to plaintext private conversations.

Prefer:

- PostgreSQL
- Redis
- Object storage such as S3-compatible storage
- WebSocket gateway
- REST APIs where appropriate
- WebRTC signalling infrastructure
- Background workers

The architecture should be cloud-deployable.

Keep backend configuration separate from the Android client.

Never hardcode production secrets.

Provide `.env.example`.

---

# 4. AUTHENTICATION

Implement secure authentication.

Support:

### Phone number authentication

- Phone number registration
- OTP verification
- Country code selector
- Resend OTP
- Rate limiting
- OTP expiration
- Device registration

Also architect the application so future authentication methods can be added:

- Email
- Passkeys
- Username-based login

Do NOT store passwords in plaintext.

Use secure session/token management.

Implement:

- Access tokens
- Refresh tokens
- Token rotation
- Secure logout
- Session revocation

---

# 5. USER PROFILE

Each user should have:

- Profile photo
- Display name
- Username
- Phone number
- About/bio
- Last seen setting
- Online visibility setting
- Read receipt setting
- Typing indicator setting
- Profile photo privacy
- Blocked users
- Privacy settings

Username should be optional.

Allow users to find each other without necessarily exposing their phone number.

---

# 6. END-TO-END ENCRYPTION

This is one of the most important requirements.

Do NOT implement fake encryption.

Do NOT simply AES-encrypt messages with one static key.

Implement a proper modern end-to-end encrypted messaging architecture.

Prefer the **Signal Protocol / libsignal ecosystem** or another well-established, independently reviewed protocol.

Support:

- Identity keys
- Prekeys
- Session establishment
- Forward secrecy
- Post-compromise security
- Ratcheting
- Per-conversation encryption keys
- Secure key storage
- Device identity
- Key verification
- Safety numbers / security codes
- Session reset handling

Private keys must never leave the user's device.

Store sensitive cryptographic material using:

**Android Keystore + encrypted local storage**

The server should only receive encrypted message payloads wherever technically possible.

---

# 7. MULTI-DEVICE SECURITY

Allow users to use Argus on multiple devices.

Support:

- Android phone
- Android tablet
- Future desktop/web clients

Each device should have its own cryptographic identity.

Implement:

- Device list
- Device names
- Device verification
- New-device notifications
- Remote device logout
- Revoke device
- Key synchronization architecture
- Secure session management

If a new device is added, clearly notify the user.

---

# 8. CHAT SYSTEM

Implement complete private messaging.

Support:

- One-to-one chats
- Group chats
- Message timestamps
- Delivery status
- Read status
- Sending status
- Failed messages
- Retry
- Message queue
- Offline sending
- Offline receiving
- Message synchronization

Message types:

- Text
- Emoji
- GIF
- Stickers
- Photos
- Videos
- Audio
- Voice messages
- Documents
- ZIP files
- APK files
- PDF
- Office documents
- Text files
- Any arbitrary file type
- Contacts
- Location
- Links
- Polls

---

# 9. MESSAGE FEATURES

Implement:

- Reply
- Forward
- Edit
- Delete for me
- Delete for everyone
- Copy
- Share
- Star/favorite
- Pin
- Bookmark
- Search
- Select multiple messages
- Multi-message forwarding
- Message reactions
- Emoji reactions
- Mentions
- Hashtags
- Link previews
- Code blocks
- Markdown-like formatting
- Quote messages

Message actions should be context-aware.

---

# 10. VOICE MESSAGES

Create a high-quality voice messaging system.

Support:

- Hold-to-record
- Swipe-to-cancel
- Lock recording
- Pause/resume recording
- Waveform visualization
- Playback speed
- 0.5x
- 1x
- 1.5x
- 2x
- Seek
- Background playback
- Ear-speaker playback
- Bluetooth support
- Automatic audio routing

Voice messages should be encrypted.

---

# 11. MEDIA SHARING

Support high-quality media sharing.

Photos:

- Original quality
- Compressed quality
- Multiple selection
- Gallery picker
- Camera capture
- Image preview
- Crop
- Rotate
- Basic markup

Videos:

- Multiple video selection
- Compression options
- Preview
- Thumbnail generation
- Playback

Allow users to choose:

**Original / High Quality / Data Saver**

---

# 12. FILE SHARING

Argus should be an extremely capable file-sharing platform.

Allow users to send virtually any file.

Examples:

- PDF
- DOCX
- XLSX
- PPTX
- ZIP
- RAR
- APK
- TXT
- CSV
- JSON
- Source code
- Images
- Videos
- Audio
- CAD files
- Archives
- Custom file formats

Show:

- File name
- File type
- File size
- Upload progress
- Download progress
- Pause
- Resume
- Cancel
- Retry
- Transfer speed
- Estimated remaining time

Large file transfers should support resumable uploads/downloads.

Do not load entire large files into RAM.

Use streaming/chunked transfer.

---

# 13. FILE VAULT

Add a powerful optional feature:

## Argus Vault

A private encrypted file storage area.

Users can store:

- Documents
- Photos
- Videos
- Files
- Notes

Vault data should be encrypted locally.

Support:

- Biometric unlock
- PIN
- Auto-lock
- Hidden vault
- Secure deletion
- File categorization

The Vault should not be confused with cloud storage.

Clearly communicate what is stored locally versus remotely.

---

# 14. GROUP CHATS

Build robust group messaging.

Support:

- Group name
- Group photo
- Description
- Admins
- Multiple admins
- Add/remove members
- Promote/demote admins
- Join links
- Revoke join links
- Permissions
- Group mute
- Group notifications
- Mention everyone
- Pinned messages
- Group media
- Group files
- Group polls

Group permissions:

- Send messages
- Send media
- Send files
- Add members
- Edit group information
- Create polls
- Start calls

Use appropriate group encryption architecture.

---

# 15. GROUP CALLS

Support:

- Group voice calls
- Group video calls

Implement using WebRTC.

Include:

- Microphone mute
- Camera toggle
- Speaker selection
- Bluetooth
- Front/back camera
- Participant list
- Screen sharing architecture
- Connection quality indicator
- Reconnect handling
- Network adaptation

Optimize for poor mobile networks.

---

# 16. ONE-TO-ONE CALLING

Implement:

### Voice calls

- Incoming call
- Outgoing call
- Missed call
- Call history
- Mute
- Speaker
- Bluetooth
- Call duration

### Video calls

- Camera switching
- Mute
- Speaker
- Bluetooth
- Picture-in-picture
- Network adaptation
- Reconnection

Calls should use end-to-end encryption where supported by the chosen architecture.

---

# 17. OFFLINE-FIRST DESIGN

Argus must work properly when connectivity is poor.

Implement:

- Local message database
- Outgoing message queue
- Retry queue
- Offline composition
- Automatic synchronization
- Conflict handling
- Background synchronization

A user should be able to type and send a message even when temporarily offline.

When connectivity returns, synchronize automatically.

Never lose messages because of temporary network failure.

---

# 18. SEARCH

Create extremely powerful search.

Search:

- Messages
- People
- Groups
- Files
- Photos
- Videos
- Links
- Documents

Filters:

- Date
- Sender
- Chat
- File type
- Media type

Examples:

> "Find PDF files sent by Ashok last month"

> "Find messages containing AWS"

> "Find photos from July"

For encrypted content, design local search carefully because the server should not receive plaintext private messages merely to enable search.

---

# 19. MESSAGE BOOKMARKS

Add a dedicated:

## Saved

section.

Users can save:

- Messages
- Files
- Photos
- Links
- Voice messages
- Documents

Allow folders/tags.

Example:

- College
- Projects
- Work
- Important
- Ideas

---

# 20. DISAPPEARING MESSAGES

Support configurable disappearing messages:

- Off
- 30 seconds
- 1 minute
- 5 minutes
- 1 hour
- 1 day
- 1 week
- Custom

Clearly distinguish:

**Message expiration**

from

**Local deletion**

Do not claim screenshot prevention is guaranteed by Android.

Where Android permits, provide screenshot detection/warnings for sensitive content, but never falsely claim complete screenshot protection.

---

# 21. PRIVACY FEATURES

Include a dedicated Privacy Center.

Controls for:

- Last seen
- Online status
- Profile photo
- About
- Read receipts
- Typing indicator
- Calls
- Groups
- Message forwarding
- Link previews
- Media auto-download
- Screenshot-related protections
- Blocked users

Add:

### Chat Lock

Protect individual chats using:

- Biometric
- PIN

Locked chats should not expose message previews in notifications.

---

# 22. APP LOCK

Allow users to lock Argus itself.

Methods:

- Fingerprint
- Face unlock where available
- Device PIN
- Argus PIN

Options:

- Lock immediately
- After 1 minute
- After 5 minutes
- After screen off

---

# 23. NOTIFICATIONS

Implement polished Android notifications.

Support:

- Direct reply
- Mark as read
- Mute
- Call notifications
- Group notifications
- Notification grouping

For private chats:

Do not expose plaintext message content on the lock screen when privacy mode is enabled.

---

# 24. CONTACTS

Implement:

- Contact synchronization
- Permission management
- Contact discovery
- Username search
- Phone number search
- QR-based user discovery

Never upload the user's entire address book unnecessarily.

If contact discovery is implemented, use privacy-preserving techniques such as normalized hashed identifiers or an appropriately designed privacy-preserving contact discovery mechanism.

---

# 25. QR SECURITY

Every user should have a personal QR identity.

QR can be used for:

- Adding contacts
- Sharing username
- Device verification
- Security verification

For encrypted chats, provide a clear security verification experience.

---

# 26. MODERN "WOW" FEATURES

Do not add gimmicks merely to make the feature list large.

Add features that are genuinely useful.

## A. Universal Message Translator

Allow users to translate incoming messages.

Example:

Tamil → English  
Hindi → English  
English → Japanese

Translation should be opt-in.

For privacy-sensitive chats, prefer on-device translation where possible.

---

## B. AI Message Assistant

Add optional AI assistance for:

- Summarizing long conversations
- Rewriting messages
- Making a message more professional
- Making a message shorter
- Translation
- Extracting tasks
- Extracting dates
- Extracting addresses
- Extracting phone numbers
- Summarizing documents

CRITICAL:

Never silently upload private E2EE messages to an AI service.

Clearly indicate when data leaves the device.

Prefer on-device AI where practical.

---

## C. Conversation Intelligence

Optional local AI can identify:

- Tasks
- Dates
- Deadlines
- Meeting information
- Addresses
- Links
- Important documents

Example:

Someone sends:

> "Meeting tomorrow at 10 AM in Lab 3."

Argus can offer:

**Add to Calendar**

without automatically doing anything without permission.

---

## D. Smart Link Safety

When a user receives a suspicious link:

Show:

- Domain
- HTTPS status
- Basic reputation information
- Potential phishing warning
- Redirect warning

Never claim a link is safe with absolute certainty.

---

## E. Message Recall Safety

When deleting a message for everyone:

Clearly show whether deletion succeeded.

Do not pretend deletion succeeded if the recipient device has not confirmed it.

---

## F. Network-Aware Media

Automatically adapt media transfer based on:

- Wi-Fi
- 5G
- 4G
- Metered connection
- Battery state

Example:

On mobile data:

**"Send compressed video to save 48 MB?"**

---

## G. Smart Storage Manager

Provide:

**Argus Storage**

Show:

- Total storage
- Chat storage
- Media
- Documents
- Voice messages
- Largest files
- Duplicate media candidates
- Cached files

Allow selective cleanup.

Never delete user content silently.

---

## H. Nearby Transfer

Implement optional local-device transfer using technologies such as:

- Wi-Fi Direct
- Nearby Connections
- Local network

Useful for transferring very large files between nearby Argus users without routing the entire file through cloud storage.

Clearly show whether the transfer is:

**Internet**

or

**Local**

---

## I. Emergency Privacy Mode

Add a user-triggered privacy mode.

Example:

**Lock Everything**

It should immediately:

- Lock Argus
- Hide notification content
- Lock Vault
- Lock protected chats

Do NOT implement deceptive or malicious functionality.

---

## J. Travel / Low-Connectivity Mode

Add a mode optimized for poor connectivity.

Features:

- Aggressive media compression
- Disable automatic media downloads
- Prioritize text
- Queue large files
- Reduce background synchronization

Useful for:

- Flights
- Rural areas
- Roaming
- Weak networks
- Congested networks

---

# 27. CHAT THEMES

Provide modern themes.

Include:

- System default
- Light
- Dark
- AMOLED black

Allow:

- Chat wallpaper
- Bubble style
- Accent color
- Message density

Keep the design tasteful.

---

# 28. HOME SCREEN

Design a polished main screen.

Recommended layout:

Top:

**Argus**

Search icon  
Camera/scan icon  
Profile/settings

Main content:

- Chats
- Favorites
- Calls
- Contacts

Use bottom navigation if it improves usability.

Do not overcrowd the interface.

---

# 29. CHAT UI

The chat interface should include:

Top bar:

- Back
- Avatar
- Name
- Online/last seen
- Voice call
- Video call
- More

Message area:

- Date separators
- Incoming/outgoing bubbles
- Reply previews
- Reactions
- Read indicators
- Attachments
- Voice messages

Composer:

- Emoji
- Text input
- Attachment
- Camera
- Voice recorder
- Send

Make gestures intuitive.

---

# 30. MEDIA VIEWER

Create a full-screen media viewer.

Support:

- Swipe
- Zoom
- Video playback
- Download/save
- Share
- Forward
- Delete
- Reply
- Favorite

Use efficient image loading.

Do not decode huge images unnecessarily.

---

# 31. DOCUMENT VIEWER

Where Android supports it, provide previews for:

- PDF
- Images
- Text
- Common office formats

Otherwise provide a secure handoff to an installed application.

Never execute unknown files automatically.

---

# 32. SECURITY HARDENING

Treat Argus as a security-sensitive application.

Implement:

- Android Keystore
- Certificate/public-key pinning where appropriate
- TLS
- Secure token storage
- Encrypted local database where appropriate
- Secure random generation
- No plaintext private keys
- No sensitive logging
- Debug logging disabled in production
- Root/tamper awareness where appropriate
- Screenshot policy for sensitive screens where appropriate
- Clipboard timeout for sensitive copied content
- Secure backup design

Do not invent custom cryptographic algorithms.

Use mature, audited cryptographic primitives.

---

# 33. BACKUPS

Create a secure backup system.

The architecture should support:

### Local encrypted backup

and optionally:

### User-controlled cloud backup

Backups must NOT silently undermine E2EE.

The user should understand:

- What is backed up
- Where it is backed up
- Whether the backup is encrypted
- Who can decrypt it

Prefer client-side encryption.

A backup password/recovery key should be required to restore encrypted backups.

---

# 34. ACCOUNT RECOVERY

Design secure account recovery.

Do not create a recovery mechanism that allows the server to trivially decrypt E2EE conversations.

Use concepts such as:

- Recovery key
- Recovery phrase
- Trusted device
- User-controlled encrypted backup

Make recovery UX extremely clear.

---

# 35. PERFORMANCE

Argus should feel extremely fast.

Target:

- Fast startup
- Smooth 60/90/120 Hz UI
- Lazy loading
- Efficient image caching
- Efficient database queries
- Minimal unnecessary recomposition
- Background work using WorkManager
- Streaming large files
- Memory-safe media handling
- Battery-efficient networking

Do not perform expensive work on the main thread.

---

# 36. ACCESSIBILITY

Support:

- Screen readers
- Content descriptions
- Large text
- Font scaling
- High contrast
- Touch target requirements
- TalkBack
- Reduced motion where appropriate

---

# 37. INTERNATIONALIZATION

Do not hardcode UI strings.

Use Android string resources.

Initially support:

- English
- Tamil
- Hindi

Architect it so additional languages can easily be added.

Support RTL layouts.

---

# 38. DATABASE

Design a proper local database.

Use Room.

Entities should be separated logically.

Potential entities:

- User
- Device
- Contact
- Conversation
- ConversationMember
- Message
- Attachment
- Reaction
- Call
- CallParticipant
- Group
- GroupMember
- Bookmark
- Draft
- Notification
- Transfer
- EncryptionSession
- CachedMedia

Use migrations.

Never destroy user data during schema upgrades.

---

# 39. REALTIME MESSAGE PIPELINE

Implement a reliable message lifecycle.

Example:

User writes message

↓

Local database

↓

Outgoing queue

↓

Encryption

↓

Network transport

↓

Server

↓

Recipient delivery

↓

Recipient acknowledgement

↓

Read acknowledgement

↓

UI update

The application must behave correctly when any stage fails.

Implement idempotency to prevent duplicate messages.

---

# 40. MESSAGE STATES

Support:

**Sending**

**Sent**

**Delivered**

**Read**

**Failed**

**Retrying**

Make state transitions reliable.

---

# 41. ERROR HANDLING

Never show raw exceptions to users.

Create useful error messages.

Examples:

Bad:

> IOException: socket timeout

Good:

> Couldn't send the message. Check your connection and try again.

Provide retry actions.

Handle:

- No internet
- Server unavailable
- Authentication expired
- Encryption failure
- Storage full
- Permission denied
- File unavailable
- Unsupported file
- Call failure
- Background restriction

---

# 42. PRIVACY TRANSPARENCY

Create:

## Privacy Center

Explain in simple language:

- What Argus collects
- What Argus does not collect
- What the server can see
- What the server cannot see
- What E2EE protects
- What metadata may exist
- What happens during calls
- What happens during backups
- AI privacy implications

Do not make misleading claims such as:

"Nobody can ever access anything."

Be technically honest.

---

# 43. SETTINGS

Create a comprehensive Settings system.

Sections:

### Account
- Profile
- Username
- Phone
- Devices
- Security

### Privacy
- Last seen
- Read receipts
- Online
- Calls
- Groups
- Blocked users
- Chat lock

### Chats
- Appearance
- Wallpapers
- Enter key behavior
- Media download
- Backup

### Notifications
- Messages
- Groups
- Calls
- Sounds
- Vibration

### Storage
- Storage manager
- Auto-download
- Cache

### Security
- App lock
- Vault
- Security verification
- Recovery key

### AI
- AI features
- On-device AI
- Cloud AI
- Privacy controls

### About
- Version
- Licenses
- Privacy policy
- Terms
- Security information

---

# 44. ADMIN / MODERATION ARCHITECTURE

Even though private messages are encrypted, design backend abuse-prevention infrastructure for:

- Account spam
- OTP abuse
- Automated account creation
- API abuse
- File abuse
- Denial-of-service attempts

Do not build server-side plaintext scanning of private E2EE conversations.

For abuse reporting, design a user-initiated mechanism where the user explicitly chooses what information to report.

---

# 45. ANTI-SPAM

Implement privacy-conscious anti-abuse systems.

Examples:

- Rate limiting
- Device reputation
- OTP throttling
- Message sending limits for suspicious accounts
- Contact request limits
- Temporary restrictions

Avoid invasive surveillance.

---

# 46. UI/UX QUALITY

The UI should be production quality.

Follow Material 3 principles but create an original Argus design system.

Use:

- Consistent spacing
- Typography hierarchy
- Motion
- Haptics
- Loading states
- Empty states
- Error states
- Skeletons where useful
- Proper dark mode

Every screen must have:

- Loading state
- Empty state
- Error state
- Success state where applicable

Do not leave blank screens.

---

# 47. ONBOARDING

Create a polished onboarding flow.

Suggested sequence:

1. Argus logo
2. Privacy/value proposition
3. Phone verification
4. Profile creation
5. Username selection
6. Contacts permission
7. Notification permission
8. Security setup
9. Optional biometric lock
10. Optional backup/recovery setup

Permissions must be requested contextually, not all at once.

---

# 48. SECURITY VERIFICATION UX

For a private conversation:

Show:

**Encryption**

Allow users to:

- Compare security codes
- Scan QR
- Verify contact
- Mark as verified
- See device changes

If a contact's identity changes, make the warning prominent.

---

# 49. TESTING

Do not consider the project complete without tests.

Implement:

### Unit tests

For:

- Encryption wrappers
- Message state transitions
- Repository logic
- Database operations
- Authentication
- File transfer logic
- Retry logic

### UI tests

For:

- Login
- Chat
- Sending messages
- Attachments
- Settings
- Calls
- Vault

### Integration tests

For:

- Authentication
- Message delivery
- Offline synchronization
- File upload/download
- Multi-device behavior

### Security tests

Test:

- Key storage
- Session management
- Authentication bypass
- Unauthorized API access
- Token replay
- File access control
- Permission handling

---

# 50. BUILD QUALITY

The project must compile cleanly.

Before declaring completion:

Run:

- Gradle build
- Unit tests
- Instrumentation tests where available
- Lint
- Static analysis

Fix all compilation errors.

Fix all warnings that indicate real problems.

Do not leave:

- TODO implementations
- Fake APIs
- Placeholder functions
- Dummy encryption
- Hardcoded user data
- Mock network calls in production code
- "Coming soon" screens for core functionality

---

# 51. ENVIRONMENT CONFIGURATION

Provide:

`.env.example`

and clearly document variables for:

- Backend URL
- WebSocket URL
- Firebase configuration
- Object storage
- Database
- Redis
- WebRTC signalling
- Push notifications
- Optional AI services

Never commit secrets.

---

# 52. PROJECT DOCUMENTATION

Create a professional `README.md`.

Include:

- Product overview
- Architecture
- Tech stack
- Project structure
- Local setup
- Android setup
- Backend setup
- Database setup
- Environment variables
- Running development environment
- Building release APK
- Building AAB
- Testing
- Security model
- Encryption architecture
- Backup architecture
- Deployment
- Troubleshooting

Also create:

`SECURITY.md`

Document:

- Threat model
- Encryption architecture
- Key management
- Authentication
- Device security
- Backup security
- Reporting vulnerabilities

---

# 53. DESIGN THE APP FOR REAL-WORLD SCALE

Do not architect Argus as a toy project.

The architecture should be capable of eventually supporting:

- Thousands of users
- Millions of messages
- Large file transfers
- Many simultaneous connections
- Multiple devices per account

Use pagination everywhere appropriate.

Do not load entire conversations into memory.

Do not load entire contact lists unnecessarily.

Use efficient indexes.

---

# 54. BATTERY AND DATA EFFICIENCY

This is a mobile application.

Optimize:

- WebSocket lifecycle
- Background tasks
- Push notifications
- Media uploads
- Database operations
- Location usage
- Calls
- File transfers

Do not maintain unnecessary background services.

Use Android's modern background execution APIs.

---

# 55. PRIVACY BY DEFAULT

Argus should default to reasonable privacy.

Examples:

- Minimal notification preview
- Secure chat lock
- Encrypted local storage
- E2EE conversations
- Minimal analytics
- No unnecessary tracking
- No unnecessary location collection
- No advertising SDKs by default

If analytics are included, make them privacy-conscious and clearly documented.

---

# 56. ARGUS SIGNATURE FEATURES

Give Argus a few features that make it memorable.

### 1. Argus Shield

A central privacy dashboard showing:

- Encryption status
- Verified devices
- Active sessions
- App lock status
- Backup status
- Security alerts

---

### 2. Argus Drop

Fast local file transfer between nearby devices.

Show:

**LOCAL TRANSFER**

when files are transferred directly between nearby devices.

---

### 3. Argus Vault

Encrypted private local storage protected by biometrics.

---

### 4. Argus Pulse

A compact communication health indicator.

Show:

- Connection
- Sync
- Encryption
- Server connectivity

Keep it subtle.

---

### 5. Smart Context

When a message contains useful information, Argus can offer contextual actions.

Examples:

Address:

**Open Maps**

Date:

**Add to Calendar**

Phone number:

**Call**

Tracking number:

**Track package**

URL:

**Open / Inspect**

Do not automatically perform actions.

---

# 57. DO NOT OVERENGINEER THE UI

The user should understand Argus within seconds.

Prioritize:

**Chats → Contacts → Calls → Settings**

Everything else should be discoverable without clutter.

---

# 58. IMPORTANT SECURITY RULES

Never:

- Invent cryptographic algorithms
- Store private keys on the server
- Hardcode encryption keys
- Store plaintext passwords
- Log plaintext messages
- Log private keys
- Upload private messages to AI without explicit consent
- Claim impossible security guarantees
- Disable TLS
- Trust arbitrary certificates
- Execute received files automatically
- Use insecure random number generators
- Store authentication tokens in plaintext SharedPreferences
- Put secrets inside the APK

---

# 59. DEVELOPMENT PROCESS

Do NOT attempt to blindly generate the entire project in one giant file.

Work in stages.

### Phase 1

Architecture + project setup.

### Phase 2

Authentication.

### Phase 3

Database + local messaging.

### Phase 4

Backend + realtime messaging.

### Phase 5

E2EE.

### Phase 6

Media/file transfer.

### Phase 7

Voice/video calling.

### Phase 8

Groups.

### Phase 9

Security/privacy.

### Phase 10

AI features.

### Phase 11

Performance optimization.

### Phase 12

Testing.

### Phase 13

Release preparation.

At the end of each phase:

- Compile
- Test
- Fix issues
- Verify functionality
- Continue only after the previous phase is stable.

---

# 60. OUTPUT EXPECTATION

I want an actual working project, not an explanation of how to build it.

Generate the complete source code required for the project.

When a component cannot reasonably be implemented without an external service, clearly identify the dependency and implement the application-side integration properly rather than replacing it with fake functionality.

Do not use fake data for core functionality.

Do not create fake "encryption enabled" labels.

Do not create fake message delivery.

Do not create fake calls.

Do not create fake file transfers.

Everything represented as functional in the UI must actually work.

---

# 61. FINAL QUALITY BAR

Before declaring Argus complete, evaluate it as if it were being prepared for public release on Google Play.

Check:

- Architecture
- Security
- Cryptography
- Authentication
- Privacy
- UX
- UI
- Performance
- Battery usage
- Networking
- Offline behavior
- Database
- File handling
- Media handling
- Voice calls
- Video calls
- Groups
- Notifications
- Accessibility
- Localization
- Error handling
- Crash resilience
- Scalability
- Testing
- Release configuration
- Play Store requirements

Find and fix problems yourself.

Do not wait for me to discover obvious issues.

If you identify a design decision that could compromise security, privacy, scalability, or reliability, change the implementation and explain the reason.

---

# 62. MOST IMPORTANT REQUIREMENT

Build **Argus as a serious privacy-focused communication platform**, not as a WhatsApp/Telegram visual clone.

The goal is:

**WhatsApp-level communication simplicity + Telegram-level flexibility + Signal-level privacy + modern Android engineering + genuinely useful next-generation features.**

The final application should feel like something that could realistically be launched as a new messaging platform.

Start by establishing the complete architecture and repository structure, then implement the application incrementally.

Do not skip difficult engineering work merely because it is complicated.