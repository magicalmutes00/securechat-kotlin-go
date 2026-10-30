# SecureChat

A production-quality private messaging application with end-to-end encryption, offline-first architecture, and secure media handling.

## Architecture Overview

```
┌─────────────────┐     HTTPS/WSS      ┌─────────────────┐
│   Android App   │ ◄────────────────► │   Go Backend    │
│                 │                    │                 │
│ • Kotlin        │                    │ • Fiber         │
│ • Jetpack       │                    │ • WebSocket     │
│   Compose       │                    │ • JWT Auth      │
│ • Room DB       │                    │ • OTP Service   │
│ • Keystore      │                    │ • Cloudinary    │
└─────────────────┘                    └────────┬────────┘
                                                 │
                          ┌──────────────────────┼──────────────────────┐
                          ▼                      ▼                      ▼
                   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
                   │  Neon (PG)  │         │ Cloudinary  │         │   Ollama    │
                   │             │         │             │         │  (Optional) │
                   │ • Users     │         │ • Images    │         │             │
                   │ • Messages  │         │ • Videos    │         │ • AI Assist │
                   │ • Sessions  │         │ • Audio     │         │ • Summarize │
                   │ • Media     │         │ • Docs      │         │             │
                   └─────────────┘         └─────────────┘         └─────────────┘
```

## Features

- **Authentication**: Phone number OTP with JWT access/refresh tokens, plus Google Sign-In
- **Real-time Messaging**: WebSocket-based with delivery/read receipts
- **Offline-First**: Room database with automatic sync
- **Media Handling**: Direct Cloudinary uploads with signed URLs
- **Secure Storage**: Android Keystore / EncryptedSharedPreferences for tokens
- **Modern UI**: Jetpack Compose, Material 3, Dark/Light theme
- **Optional AI**: Ollama integration for smart features (planned)

> **Note:** End-to-end message encryption is not implemented yet — traffic is
> protected in transit with TLS/WSS. The Signal Protocol design (X3DH + Double
> Ratchet, client-side crypto with the server as a blind relay) is specified in
> [docs/e2ee-design.md](docs/e2ee-design.md), and phase E1 (the prekey
> directory) is implemented. See the development phases below.

## Quick Start

### Prerequisites
- **Backend**: Go 1.25+, PostgreSQL (Neon), Ollama (optional)
- **Android**: Android Studio Ladybug+, JDK 21, Android SDK 34
- **Cloudinary**: Account for media storage

### Backend Setup (Secondary Laptop)

```bash
# 1. Clone and configure
git clone <repo>
cd securechat/server
cp .env.example .env
# Edit .env with your credentials
# Generate JWT keys and paste them into .env (SECURECHAT_JWT_*_SECRET)
go run ./cmd/genkeys

# 2. Install tools
go install github.com/pressly/goose/v3/cmd/goose@latest

# 3. Setup database — create a Neon database and put its connection
#    string in .env as SECURECHAT_DATABASE_URL, then:
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" up

# 4. Run
go run ./cmd/server
```

See [Windows Setup](docs/deployment/windows-setup.md) or [Linux Setup](docs/deployment/linux-setup.md) for detailed instructions.

### Android Setup

```bash
cd android
# Configure server URL (use your laptop's LAN IP)
echo "server.base.url=http://192.168.1.50:8080/api/v1" > local.properties
echo "server.ws.url=ws://192.168.1.50:8080/ws" >> local.properties

# Build and run
./gradlew installDebug
```

See [Android Configuration](docs/deployment/android-config.md) for details.

## Project Structure

```
securechat/
├── android/                 # Android application
│   ├── app/
│   │   ├── src/main/java/com/securechat/
│   │   │   ├── core/        # Network, WebSocket, Security, Database, Media
│   │   │   ├── data/        # Local (Room), Remote (API/WS), Repository
│   │   │   ├── domain/      # Models, Repository interfaces, UseCases
│   │   │   ├── presentation/# Compose UI, ViewModels, Navigation
│   │   │   └── di/          # Hilt modules
│   │   └── build.gradle.kts
│   └── gradle/libs.versions.toml
│
├── server/                  # Go backend
│   ├── cmd/server/          # Entry point
│   ├── internal/
│   │   ├── auth/            # OTP, JWT, Sessions
│   │   ├── users/           # User management
│   │   ├── devices/         # Device tracking
│   │   ├── conversations/   # Chat management
│   │   ├── messages/        # Message handling
│   │   ├── websocket/       # Real-time events
│   │   ├── media/           # Cloudinary integration
│   │   ├── otp/             # OTP service
│   │   ├── ai/              # Ollama client
│   │   ├── middleware/      # Auth, logging, errors
│   │   └── database/        # PostgreSQL, sqlc, migrations
│   ├── pkg/                 # Shared packages (jwt, crypto, logger, errors)
│   ├── migrations/          # SQL migrations
│   ├── config/              # Configuration
│   └── go.mod
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
| 8 | Room & Offline Sync | 🔶 Attachments and read receipts queue durably and retry with backoff; text sends rely on the socket's outbound queue |
| 9 | Cloudinary Media | ✅ End-to-end (sign → upload → complete → send) |
| 10 | Chat Media UI | ✅ Picker, progress/cancel/retry, image bubbles, viewers (video is download-and-open; no in-app player) |
| 11 | Notifications | 📋 Planned |
| 12 | Ollama AI | 📋 Planned |
| 13 | Security Hardening | ✅ Hardened (atomic OTP, rotation CAS, session checks) |
| 14 | Testing | 🔶 Go unit/integration suites + Android unit tests present |
| 15 | Deployment | 🔶 Render config present |
| E1 | E2EE prekey directory (server) | ✅ Complete |
| E2 | E2EE X3DH + Double Ratchet (1:1) | 📋 Planned |
| E3 | E2EE plaintext cutover | 📋 Planned |

> **Media contract.** Attachments have a single path: upload to Cloudinary via a
> backend-signed request, register the asset with `POST /media/complete`, then
> send an ordinary `MESSAGE_SEND` carrying `media_id`. Message payloads embed the
> full `media` object (REST and WebSocket alike) because `GET /media/:id` is
> owner-scoped and a recipient could not otherwise resolve a bare id. See
> [docs/api-spec.md](docs/api-spec.md) and
> [docs/websocket-protocol.md](docs/websocket-protocol.md).


## Technology Stack

### Android
- Kotlin 1.9+, Jetpack Compose, Material 3
- Hilt DI, Room, Kotlin Coroutines/Flow
- Ktor Client, WebSocket, Kotlinx Serialization
- Android Keystore, EncryptedSharedPreferences
- Coil (images), WorkManager (background sync)

### Backend
- Go 1.22, Fiber v2, WebSocket
- PostgreSQL 16 (Neon), goose (migrations), sqlc (type-safe SQL)
- JWT (RS256), bcrypt, Twilio (SMS)
- Cloudinary Go SDK, Zap (structured logging)
- Viper (config), UUID/KSUID

### Infrastructure
- Cloudinary (media storage & transformations)
- Ollama (local AI, optional)
- Neon (PostgreSQL) — primary database, pooled connection via `SECURECHAT_DATABASE_URL`
- Reverse proxy (Caddy/nginx) for TLS termination

## Security Model

- **No secrets in Android app** - Cloudinary API secret only on backend
- **Signed direct uploads** - Android → Cloudinary with backend-generated signatures
- **JWT in Keystore** - Access/refresh tokens encrypted at rest
- **Short-lived tokens** - 15min access, 30day refresh with rotation
- **OTP security** - bcrypt hash, 5-min expiry, per-number cooldown
- **HTTPS/WSS only** - Cleartext only for LAN development
- **Database isolation** - PostgreSQL (Neon) not exposed publicly

## API Endpoints

```
Authentication:
POST   /api/v1/auth/send-otp
POST   /api/v1/auth/verify-otp
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

Users:
GET    /api/v1/users/me
PATCH  /api/v1/users/me
GET    /api/v1/users/search

Conversations:
GET    /api/v1/conversations
POST   /api/v1/conversations
GET    /api/v1/conversations/:id
DELETE /api/v1/conversations/:id

Messages:
GET    /api/v1/conversations/:id/messages
DELETE /api/v1/messages/:id

Media:
POST   /api/v1/media/sign-upload
POST   /api/v1/media/complete
GET    /api/v1/media/:id

Devices:
GET    /api/v1/devices
DELETE /api/v1/devices/:id

E2EE Keys (public key material only):
PUT    /api/v1/keys/identity
PUT    /api/v1/keys/signed-prekey
POST   /api/v1/keys/one-time-prekeys
GET    /api/v1/keys/count
POST   /api/v1/keys/claim

WebSocket: /ws
```

## WebSocket Events

| Client → Server | Server → Client |
|----------------|-----------------|
| AUTHENTICATE | AUTH_ACK |
| MESSAGE_SEND | MESSAGE_ACK / MESSAGE_RECEIVED |
| TYPING_START/STOP | TYPING_START/STOP |
| MESSAGE_READ | MESSAGE_DELIVERED / MESSAGE_READ |
| MESSAGE_DELETE | MESSAGE_DELETE |
| | USER_ONLINE / USER_OFFLINE |
| | CONVERSATION_UPDATED |
| | MEDIA_MESSAGE_CREATED |

## License

Proprietary - All rights reserved.

## Contributing

This is a private project. See [AGENTS.md](AGENTS.md) for development guidelines.
