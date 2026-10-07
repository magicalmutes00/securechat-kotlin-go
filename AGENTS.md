# AGENTS.md - SecureChat Development Guidelines

This document provides guidelines for AI agents and developers working on the SecureChat project.

## Project Overview

SecureChat is a private messaging application with:
- Native Android (Kotlin, Jetpack Compose)
- Go Backend (Fiber, WebSocket)
- PostgreSQL Database (Neon)
- Cloudinary Media Storage
- Optional Ollama AI

## Code Generation Rules

### CRITICAL RULES (Must Follow)

1. **Never dump hundreds of files at once** - Build incrementally by phase
2. **Inspect current project first** - Read existing files before making changes
3. **Explain what will be implemented** - Before writing code
4. **List files to create/change** - Be specific
5. **Create complete files** - No placeholders, no "TODO: implement"
6. **Never invent nonexistent APIs** - Use current stable APIs only
7. **Keep dependencies compatible** - Check versions in libs.versions.toml / go.mod
8. **Keep project compiling** - Fix errors before moving forward
9. **Preserve existing architecture** - Don't rewrite unrelated working code
10. **Explain important design decisions** - Document why, not just what

### File Modification Rules

- When modifying a file, provide the complete updated file unless a minimal patch is safer
- Use `edit` tool for small changes, `write` for new files or complete rewrites
- Never use `bash` for file operations - use `read`, `write`, `edit`, `glob`, `grep`

## Architecture Principles

### Android (Clean Architecture + MVVM)

```
presentation/     ← Compose UI, ViewModels
    ↓
domain/           ← Pure Kotlin: Models, Repository interfaces, UseCases
    ↓
data/             ← Repository implementations
    ├── local/    ← Room DAOs, Entities, Database
    ├── remote/   ← Ktor API, WebSocket, DTOs
    └── repository/ ← Coordinates local + remote
    ↓
core/             ← Shared: Network, Security, Database, Media, Utils
```

**Rules:**
- No API calls in Compose screens
- No database operations in Compose screens
- Use Repository interfaces in domain layer
- Use Cases = single responsibility
- Hilt for DI across all layers

### Go Backend (Layered Architecture)

```
cmd/server/main.go          ← Entry point, wiring
internal/
  ├── auth/                 # OTP, JWT, Sessions
  ├── users/                # User management
  ├── devices/              # Device tracking
  ├── conversations/        # Chat management
  ├── messages/             # Message handling
  ├── websocket/            # Real-time events
  ├── media/                # Cloudinary integration
  ├── otp/                  # OTP service
  ├── ai/                   # Ollama client
  ├── middleware/           # Auth, logging, errors
  └── database/             # PostgreSQL, sqlc, migrations
pkg/
  ├── jwt/                  # Token management
  ├── crypto/               # Hashing, OTP generation
  ├── logger/               # Structured logging
  └── errors/               # Error types
```

**Rules:**
- HTTP handlers → Services → Repositories → Database
- sqlc for type-safe queries (prefer over GORM)
- Structured logging with zap
- Consistent error responses
- Never log secrets (OTP, tokens, API keys)

## Technology Standards

### Android Dependencies (gradle/libs.versions.toml)
- Kotlin 1.9.23
- Compose BOM 2024.08.00
- Hilt 2.48.1
- Room 2.6.1
- Ktor 3.0.1
- Coil 2.6.0
- WorkManager 2.9.0

### Go Dependencies (go.mod)
- Fiber v2.52.0
- WebSocket v2.1.0
- pgx/v5 (PostgreSQL driver)
- sqlc (code generation)
- JWT v5, bcrypt
- Cloudinary Go SDK v2
- Zap logging

## Security Requirements

### Android
- ✅ Android Keystore for token storage
- ✅ EncryptedSharedPreferences
- ✅ No API keys in APK
- ✅ Network security config (HTTPS only in prod)
- ✅ Minimal permissions (Internet, Network State, Foreground Service)
- ✅ Photo Picker / SAF for media (no storage permissions)

### Backend
- ✅ JWT RS256 with rotation
- ✅ Refresh token hashes in DB (not plaintext)
- ✅ OTP bcrypt hash, 5-min expiry, rate limiting
- ✅ Cloudinary secrets only on backend
- ✅ Signed upload URLs for direct Android → Cloudinary
- ✅ PostgreSQL (Neon) not exposed publicly
- ✅ Structured logging without secrets

## Testing Standards

### Android
- Unit tests: ViewModels, UseCases, Repositories
- Instrumented: Room DAOs, WebSocket, Offline sync
- Test files in `src/test` and `src/androidTest`

### Go
- Unit tests: Services, Handlers, Crypto
- Integration: API, WebSocket, Database, Cloudinary
- Test files in `tests/` and `*_test.go`
- Use testcontainers for PostgreSQL

## Git Workflow

### Branch Strategy
- `main` - Production ready
- `develop` - Integration branch
- `feature/*` - Feature branches
- `fix/*` - Bug fixes
- `release/*` - Release preparation

### Commit Messages
```
type(scope): description

[optional body]

[optional footer]
```

Types: feat, fix, docs, style, refactor, test, chore, security

### Pull Requests
- All CI checks must pass
- Code review required
- No direct pushes to main/develop

## Documentation

### Required Documentation
- Architecture decisions in `docs/`
- API specification in `docs/api-spec.md`
- Database schema in `docs/database-schema.md`
- WebSocket protocol in `docs/websocket-protocol.md`
- Deployment guides in `docs/deployment/`

### Code Documentation
- Public APIs: KDoc / Go doc comments
- Complex logic: Inline comments explaining why
- No obvious comments (what the code does)

## Performance Targets

- **App startup**: < 2 seconds cold start
- **Message latency**: < 100ms LAN, < 500ms internet
- **Database queries**: < 10ms p99
- **Media upload**: Progress updates, resumable
- **Memory**: < 150MB typical, < 300MB peak
- **Battery**: Minimal background usage

## Accessibility

- Content descriptions for all interactive elements
- Sufficient color contrast (WCAG AA)
- Scalable text (SP units)
- TalkBack navigation support
- Haptic feedback for actions

## Internationalization

- All strings in `strings.xml`
- RTL layout support
- Date/number formatting via locale
- Language switching in settings

## Error Handling

### Android
- Result<T> sealed class for all operations
- User-friendly error messages (no stack traces)
- Retry mechanisms for network operations
- Offline queue with sync on reconnect

### Backend
- Consistent error response format
- AppError with code, message, status
- Structured logging for debugging
- Graceful degradation (AI optional)

## Monitoring & Observability

### Backend
- Structured JSON logs (zap)
- Request/response logging (middleware)
- Error tracking with context
- Health check endpoint (/health)

### Android
- Crash reporting (Play Console)
- ANR tracking
- Network request monitoring
- Custom events for analytics

## Release Process

1. Update version in `app/build.gradle.kts` and `go.mod`
2. Create release branch
3. Run full test suite
4. Build signed AAB / Go binary
5. Deploy backend with zero-downtime
6. Roll out Android via Play Console (staged)
7. Monitor metrics post-release

## Code Review Checklist

- [ ] Follows architecture (Clean/MVVM, layered backend)
- [ ] No hardcoded secrets or URLs
- [ ] Proper error handling
- [ ] Tests included/updated
- [ ] Documentation updated
- [ ] Performance considered
- [ ] Security reviewed
- [ ] Accessibility checked
- [ ] No breaking API changes (or versioned)

## Useful Commands

### Android
```bash
# Build debug
./gradlew assembleDebug

# Run tests
./gradlew test

# Lint
./gradlew lint

# Generate signed AAB
./gradlew bundleRelease
```

### Go
```bash
# Run server
go run cmd/server/main.go

# Run tests
go test ./...

# Generate sqlc
sqlc generate

# Run migrations
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" up

# Build binary
go build -o bin/server ./cmd/server
```

## Contact

For questions about architecture or implementation, refer to the documentation in `docs/` or the architectural specification in the initial project prompt.