# Windows Development Setup

## Prerequisites

- Windows 10/11 (64-bit)
- PowerShell 7+ (recommended) or Command Prompt
- Administrator privileges for some installations

---

## 1. Install Go

### Option A: winget (Recommended)
```powershell
winget install GoLang.Go
```

### Option B: Chocolatey
```powershell
choco install golang
```

### Option C: Manual
1. Download from https://go.dev/dl/
2. Run the MSI installer
3. Verify: `go version`

---

## 2. Set Up PostgreSQL (Neon)

The backend uses a hosted PostgreSQL database on [Neon](https://neon.tech) — no local database install is required.

1. Create a free account at https://neon.tech
2. Create a project (region closest to you)
3. Copy the pooled connection string (starts with `postgresql://...-pooler...` and includes `?sslmode=require`)
4. Put it in `server/.env` as `SECURECHAT_DATABASE_URL`

### Optional: local PostgreSQL (offline development)
If you prefer a local database instead of Neon:
```powershell
docker run -d --name securechat-postgres -p 5432:5432 -e POSTGRES_PASSWORD=securechat -e POSTGRES_DB=securechat postgres:16
```
Then set `SECURECHAT_DATABASE_URL=postgres://postgres:securechat@localhost:5432/securechat?sslmode=disable` in `server/.env`
(or run `docker compose up postgres` from the repo root).

---

## 3. Install Ollama (Optional - for AI features)

### Option A: winget
```powershell
winget install Ollama.Ollama
```

### Option B: Manual
1. Download from https://ollama.com/download/windows
2. Run installer
3. Start Ollama: `ollama serve` (runs in background)

### Pull models
```powershell
ollama pull llama3.1:8b
ollama pull nomic-embed-text
```

---

## 4. Install Development Tools

```powershell
# Goose (migrations)
go install github.com/pressly/goose/v3/cmd/goose@latest

# sqlc (SQL code generation)
go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest

# Air (hot reload)
go install github.com/cosmtrek/air@latest

# golangci-lint
go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
```

---

## 5. Clone and Configure Project

```powershell
# Clone repository
git clone <repository-url>
cd securechat

# Server configuration
cd server
copy .env.example .env
# Edit .env: set SECURECHAT_DATABASE_URL to your Neon connection string
notepad .env

# Run migrations
$envUrl = (Select-String -Path .env -Pattern '^SECURECHAT_DATABASE_URL=(.+)$').Matches.Groups[1].Value
goose -dir migrations postgres $envUrl up

# Generate SQL code
sqlc generate

# Start server
go run cmd/server/main.go
```

The server also runs pending migrations automatically on startup, so the manual
`goose up` step is only needed when you want to migrate without starting the API.

---

## 6. Android Development Setup

### Install Android Studio
1. Download from https://developer.android.com/studio
2. Run installer
3. During setup, install:
   - Android SDK (API 34)
   - Android SDK Build-Tools 34
   - Android Emulator
   - Google Play Services (for emulator)

### Configure Local Properties
```powershell
cd android
# Create local.properties with server URL
echo "server.base.url=http://192.168.1.50:8080/api/v1" > local.properties
echo "server.ws.url=ws://192.168.1.50:8080/ws" >> local.properties
```

### Build and Run
```powershell
# Debug build
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

---

## 7. Network Configuration (LAN Testing)

### Windows Firewall
Allow inbound connections on port 8080:
```powershell
New-NetFirewallRule -DisplayName "SecureChat Backend" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

### Find your LAN IP
```powershell
ipconfig | findstr "IPv4"
# Use the IPv4 address (e.g., 192.168.1.50) in Android local.properties
```

### Android Network Security Config
The app includes `network_security_config.xml` that allows cleartext HTTP for:
- 10.0.0.0/8
- 192.168.0.0/16
- 172.16.0.0/12
- localhost

---

## 8. Verify Installation

### Test Backend
```powershell
# Health check
curl http://localhost:8080/health

# Send OTP (use mock provider)
curl -X POST http://localhost:8080/api/v1/auth/send-otp `
  -H "Content-Type: application/json" `
  -d '{"phone_number": "+15551234567"}'
```

### Test Database
```powershell
$envUrl = (Select-String -Path .env -Pattern '^SECURECHAT_DATABASE_URL=(.+)$').Matches.Groups[1].Value
goose -dir migrations postgres $envUrl status
```

---

## 9. Common Issues

### Neon Connection Fails
- Verify `SECURECHAT_DATABASE_URL` includes `?sslmode=require`
- Check the connection string was copied in full (Neon truncates in the UI — use the copy button)
- Free-tier compute autosuspends after inactivity: the first request may take a few seconds

### Goose Migration Fails
- Ensure the connection string points at the right database (`/neondb`)
- Check for a leftover schema: if tables from another project exist in `public`, drop them first (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`)

### Android Build Fails
- Run `./gradlew clean` and retry
- Check JDK version (should be 21)
- Verify Android SDK path in local.properties

### WebSocket Connection Fails
- Ensure server IP in local.properties matches laptop LAN IP
- Check Windows Firewall allows port 8080
- Verify phone and laptop on same WiFi

---

## 10. Useful Commands

```powershell
# Migration status
$envUrl = (Select-String -Path .env -Pattern '^SECURECHAT_DATABASE_URL=(.+)$').Matches.Groups[1].Value
goose -dir migrations postgres $envUrl status

# View Go server logs (if running as service)
# Check the terminal where go run is executing

# Reset database (DANGEROUS - deletes all data)
# Run in the Neon SQL Editor or via psql:
#   DROP SCHEMA public CASCADE; CREATE SCHEMA public;
# then restart the server (migrations re-run automatically)
```
