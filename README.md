# SecureChat

A production-quality private messaging application with end-to-end encryption (Signal Protocol), FCM push notifications, offline-first architecture, and secure media handling.

```
┌─────────────────┐     HTTPS/WSS     ┌─────────────────┐
│   Android App   │ ◄────────────────► │   Go Backend    │
│                 │                    │                 │
│ • Kotlin        │                    │ • Fiber         │
│ • Jetpack Compose│                   │ • WebSocket     │
│ • Room DB       │                    │ • JWT Auth      │
│ • Keystore      │                    │ • FCM Push      │
└────────┬────────┘                    └────────┬────────┘
         │                                     │
         └─────────────────────────────────────┘
                          ┌──────────────────────┴──────────────────────┐
                          ▼                      ▼                      ▼
                   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
                   │  Neon (PG)  │         │ Cloudinary  │         │   Ollama    │
                   │             │         │             │         │  (Optional) │
                   │ • Users     │         │ • Images    │         │ • AI Assist │
                   │ • Messages  │         │ • Videos    │         │ • Summarize │
                   │ • Prekeys   │         │ • Audio     │         │             │
                   │ • Push tokens│        │ • Docs      │         │             │
                   └─────────────┘         └─────────────┘         └─────────────┘
```

## Features

- **Authentication**: Phone number OTP with JWT access/refresh tokens, plus Google Sign-In (Firebase phone auth)
- **Real-time Messaging**: WebSocket-based with delivery/read receipts, typing indicators, user online/offline presence
- **Offline-First**: Room database with automatic sync, pending-operation queue with retry/backoff
- **Media Handling**: Direct Cloudinary uploads with backend-signed URLs, image/video/document support
- **Secure Storage**: Android Keystore + EncryptedSharedPreferences for tokens; no API keys in the APK
- **Push Notifications**: FCM — server registers device FCM tokens and sends conversation-id-only payloads (never plaintext)
- **End-to-End Encryption (Signal Protocol)**: X3DH + Double Ratchet for 1:1 chats via libsignal-client; server is a blind relay (public prekey directory + opaque envelope storage only)
- **Modern UI**: Jetpack Compose, Material 3, Dark/Light theme
- **Optional AI**: Ollama integration for smart features (planned)

## Quick Start

### Prerequisites

- **Backend**: Go 1.25+, PostgreSQL (Neon), Ollama (optional), `goose` for migrations
- **Android**: Android Studio Ladybug+, JDK 21, Android SDK 34
- **Cloudinary**: Account for media storage
- **Firebase**: Project with FCM enabled (for push notifications)

### Backend Setup

```bash
# 1. Clone and configure
git clone <repo>
cd securechat/server
cp .env.example .env
# Edit .env with your credentials (DATABASE_URL, JWT secrets, Cloudinary, FCM, Twilio)

# 2. Generate JWT keys (RS256)
go run ./cmd/genkeys

# 3. Install migration tool
go install github.com/pressly/goose/v3/cmd/goose@latest

# 4. Run migrations against your Neon database
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" up

# 5. Run
go run ./cmd/server
```

Server listens on `SECURECHAT_SERVER_HOST:SECURECHAT_SERVER_PORT` (default `0.0.0.0:8080`). Health check at `GET /health`.

Deploy to Render: `render.yaml` is present — connect the repo, set the `sync: false` env vars (secrets), and Render builds `go build -o bin/server ./cmd/server` and runs `./bin/server`.

### Android Setup

```bash
cd android

# Configure server URL (use your laptop's LAN IP for LAN development)
echo "server.base.url=http://192.168.1.50:8080/api/v1" > local.properties
echo "server.ws.url=ws://192.168.1.50:8080/ws" >> local.properties

# Build and run
./gradlew installDebug
```

See [docs/deployment/android-config.md](docs/deployment/android-config.md) for details, and [docs/deployment/windows-setup.md](docs/deployment/windows-setup.md) / [docs/deployment/linux-setup.md](docs/deployment/linux-setup.md) for machine-specific setup.

## Project Structure

```
securechat/
├── android/                 # Android application (Kotlin, Jetpack Compose, Hilt)
│   ├── app/
│   │   ├── src/main/java/com/securechat/
│   │   │   ├── core/          # Network, WebSocket, Security (E2EE, Keystore), Media, Push (FCM)
│   │   │   │   ├── push/      # FCM messaging service + notification helper
│   │   │   │   └── security/  # E2EE SessionManager, KeyManager, ProtocolStore, TokenStorage
│   │   │   ├── data/          # Local (Room), Remote (Ktor API/WS), Repository implementations
│   │   │   │   ├── dto/       # Remote DTOs (incl. PushTokenDto)
│   │   │   │   ├── local/     # Room DAOs, entities, database
│   │   │   │   ├── remote/    # ApiService, WebSocket, DTOs
│   │   │   │   └── repository/# Repository implementations
│   │   │   ├── domain/        # Models, Repository interfaces, UseCases
│   │   │   │   ├── repository/# Repository interfaces
│   │   │   │   └── usecase/   # Single-responsibility use cases (incl. push/)
│   │   │   ├── di/            # Hilt modules (DataModule, AppModule)
│   │   │   └── presentation/# Compose UI, ViewModels, Navigation, Components, Theme
│   │   │       ├── components/# AppleComponents.kt, CommonComponents.kt, DesignComponents.kt
│   │   │       └── theme/     # AppleTheme.kt, Theme.kt
│   │   └── build.gradle.kts
│   └── gradle/libs.versions.toml
│
├── server/                  # Go backend (Fiber, WebSocket, PostgreSQL)
│   ├── cmd/server/          # Entry point (wiring, middleware, routes, graceful shutdown)
│   ├── cmd/genkeys/         # JWT RS256 key generator
│   ├── internal/
│   │   ├── auth/            # OTP, JWT, Sessions, Firebase
│   │   ├── users/           # User management
│   │   ├── devices/         # Device tracking
│   │   ├── conversations/   # Chat management
│   │   ├── messages/        # Message handling
│   │   ├── websocket/       # Real-time events
│   │   ├── media/           # Cloudinary integration
│   │   ├── otp/             # OTP service (Twilio / mock)
│   │   ├── push/            # FCM push notification service
│   │   ├── keys/            # E2EE prekey directory (identity, signed-prekey, kyber-prekey, one-time)
│   │   ├── middleware/      # Auth, logging, errors
│   │   └── database/        # PostgreSQL, sqlc, migrations
│   ├── pkg/                 # Shared packages (jwt, crypto, logger, errors)
│   ├── migrations/          # SQL migrations (001–010, incl. 006 e2ee prekeys, 008 kyber, 009 envelopes, 010 push tokens)
│   ├── config/              # Configuration (Viper)
│   ├── go.mod / go.sum
│   └── Makefile
│
├── docs/                    # Documentation
│   ├── api-spec.md
│   ├── database-schema.md
│   ├── websocket-protocol.md
│   ├── e2ee-design.md
│   └── deployment/
│       ├── android-config.md
│       ├── linux-setup.md
│       └── windows-setup.md
│
├── .github/workflows/       # CI/CD
├── docker-compose.yml
├── render.yaml              # Render deployment blueprint
└── README.md
```

## Development Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Architecture & Project Initialization | ✅ Complete |
| 2 | Android Foundation (Compose, Hilt, Navigation) | ✅ Complete |
| 3 | Go Backend Foundation (Fiber, Config, Logging) | ✅ Complete |
| 4 | PostgreSQL Schema & Repositories | ✅ Complete |
| 5 | Authentication (OTP, JWT, Keystore) | ✅ Complete |
| 6 | WebSocket Infrastructure | ✅ Complete (integration-tested) |
| 7 | Chat Core (Conversations, Messages) | ✅ Complete |
| 8 | Room & Offline Sync | ✅ Attachments and read receipts queue durably and retry with backoff |
| 9 | Cloudinary Media | ✅ End-to-end (sign → upload → complete → send) |
| 10 | Chat Media UI | ✅ Picker, progress/cancel/retry, image bubbles, viewers |
| 11 | Notifications (FCM) | ✅ Implemented — token registration, unregister, local notifications |
| 12 | Ollama AI | 📋 Planned |
| 13 | Security Hardening | ✅ Hardened (atomic OTP, rotation CAS, session checks) |
| 14 | Testing | ✅ Go unit/integration suites + Android unit tests present |
| 15 | Deployment | ✅ Render config present |
| E1 | E2EE prekey directory (server) | ✅ Complete |
| E2 | E2EE X3DH + Double Ratchet (1:1) | ✅ Complete — client-side crypto wired into send/receive paths (libsignal-client) |
| E3 | E2EE plaintext cutover | 📋 Planned |

## Security Model

- **No secrets in Android app** — Cloudinary API secret and FCM credentials live only on the backend
- **Signed direct uploads** — Android → Cloudinary with backend-generated signatures
- **JWT in Keystore** — Access/refresh tokens encrypted at rest (15 min access, 30 day refresh with rotation)
- **OTP security** — bcrypt hash, 5-min expiry, per-number cooldown, atomic attempt cap
- **HTTPS/WSS only** — Cleartext only for LAN development
- **Database isolation** — PostgreSQL (Neon) not exposed publicly
- **E2EE** — X3DH + Double Ratchet on-device via libsignal-client; the server never sees private keys or plaintext, only opaque ciphertext envelopes and public prekey material
- **Push privacy** — FCM payloads carry only a conversation id; no message text, no sender identity

## API Endpoints

```
Authentication:
POST   /api/v1/auth/send-otp
POST   /api/v1/auth/verify-otp
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/firebase          (Firebase phone sign-in)

Users:
GET    /api/v1/users/me
PATCH  /api/v1/users/me
GET    /api/v1/users/search

Conversations:
GET    /api/v1/conversations
POST   /api/v1/conversations
GET    /api/v1/conversations/:id
PATCH  /api/v1/conversations/:id/rename
POST   /api/v1/conversations/:id/members
DELETE /api/v1/conversations/:id/members/:userId
DELETE /api/v1/conversations/:id

Messages:
GET    /api/v1/conversations/:id/messages
DELETE /api/v1/messages/:id

Media:
POST   /api/v1/media/sign-upload
POST   /api/v1/media/complete
GET    /api/v1/media/:id
DELETE /api/v1/media/:id

Devices:
GET    /api/v1/devices
DELETE /api/v1/devices/:id

E2EE Keys (public key material only):
PUT    /api/v1/keys/identity
PUT    /api/v1/keys/signed-prekey
PUT    /api/v1/keys/kyber-prekey
POST   /api/v1/keys/one-time-prekeys
GET    /api/v1/keys/count
POST   /api/v1/keys/claim

Push Notifications (FCM, authenticated):
POST   /push/register       (register FCM token for this device)
DELETE /push/token          (deactivate an FCM token)

WebSocket: /ws
Health:  GET /health
```

## WebSocket Events

| Client → Server | Server → Client |
|-----------------|-----------------|
| AUTHENTICATE | AUTH_ACK |
| MESSAGE_SEND | MESSAGE_ACK / MESSAGE_RECEIVED |
| TYPING_START/STOP | TYPING_START/STOP |
| MESSAGE_READ | MESSAGE_DELIVERED / MESSAGE_READ |
| MESSAGE_DELETE | MESSAGE_DELETE |
| | USER_ONLINE / USER_OFFLINE |
| | CONVERSATION_UPDATED |
| | MEDIA_MESSAGE_CREATED |

## E2EE Design

All Signal Protocol operations run on-device in Kotlin via the official `org.signal:libsignal-client` artifact. The Go server's role is limited to:

1. **Prekey directory** — store and serve public identity keys, signed prekeys, Kyber prekeys, and one-time prekeys so a sender can build a session with an offline recipient.
2. **Blind envelope relay** — store and forward opaque ciphertext blobs; the server never decrypts.

Read the full design in [docs/e2ee-design.md](docs/e2ee-design.md), including the decision to keep crypto client-only (no server-side libsignal port, no CGo/Rust dependency in the Go image).

## Technology Stack

### Android
- Kotlin 1.9, Jetpack Compose, Material 3
- Hilt DI, Room, Kotlin Coroutines/Flow
- Ktor Client, WebSocket, Kotlinx Serialization
- libsignal-client (Signal Protocol / E2EE)
- Android Keystore, EncryptedSharedPreferences
- Coil (images), WorkManager (background sync)
- Firebase Messaging (FCM push notifications)

### Backend
- Go 1.25, Fiber v2, WebSocket
- PostgreSQL 16 (Neon), goose (migrations), sqlc (type-safe SQL)
- JWT (RS256), bcrypt, Twilio (SMS OTP)
- Cloudinary Go SDK, Zap (structured logging)
- Viper (config), Firebase Admin SDK (FCM)

### Infrastructure
- Cloudinary (media storage & transformations)
- Ollama (local AI, optional)
- Neon (PostgreSQL) — primary database
- Firebase Cloud Messaging (push notifications)
- Render (backend hosting)
- Reverse proxy (Caddy/nginx) for TLS termination

## License

Proprietary - All rights reserved.

## Contributing

This is a private project. See [AGENTS.md](AGENTS.md) for development guidelines.