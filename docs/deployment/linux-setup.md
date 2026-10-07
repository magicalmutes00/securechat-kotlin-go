# Linux Development Setup (Ubuntu 22.04+/Debian 12+)

## Prerequisites

- Ubuntu 22.04 LTS, 24.04 LTS, or Debian 12+
- sudo privileges
- Internet connection

---

## 1. System Update

```bash
sudo apt update && sudo apt upgrade -y
```

---

## 2. Install Go

```bash
# Install Go 1.25+
sudo apt install -y golang-go

# Verify
go version
```

### Alternative: Install specific Go version
```bash
# Download and install specific version
wget https://go.dev/dl/go1.25.0.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.25.0.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.profile
source ~/.profile
go version
```

---

## 3. Set Up PostgreSQL (Neon)

The backend uses a hosted PostgreSQL database on [Neon](https://neon.tech) — no local database install is required.

1. Create a free account at https://neon.tech
2. Create a project (region closest to you)
3. Copy the pooled connection string (starts with `postgresql://...-pooler...` and includes `?sslmode=require`)
4. Put it in `server/.env` as `SECURECHAT_DATABASE_URL`

### Optional: local PostgreSQL (offline development)
```bash
docker run -d --name securechat-postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=securechat -e POSTGRES_DB=securechat postgres:16
```
Then set `SECURECHAT_DATABASE_URL=postgres://postgres:securechat@localhost:5432/securechat?sslmode=disable` in `server/.env`
(or run `docker compose up postgres` from the repo root).

### Firewall (UFW)
```bash
sudo ufw allow 8080/tcp comment "SecureChat Backend"
sudo ufw enable
```

---

## 4. Install Ollama (Optional - for AI features)

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Start Ollama service
sudo systemctl enable ollama
sudo systemctl start ollama

# Or run manually: ollama serve
```

### Pull Models
```bash
ollama pull llama3.1:8b
ollama pull nomic-embed-text
# ollama pull llava:7b  # For vision (optional)
```

### Verify
```bash
curl http://localhost:11434/api/generate -d '{"model":"llama3.1:8b","prompt":"Hello"}'
```

---

## 5. Install Development Tools

```bash
# Goose (migrations)
go install github.com/pressly/goose/v3/cmd/goose@latest

# sqlc (SQL code generation)
go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest

# Air (hot reload)
go install github.com/cosmtrek/air@latest

# golangci-lint
go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest

# Add to PATH if needed
echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc
source ~/.bashrc
```

---

## 6. Clone and Configure Project

```bash
# Clone repository
git clone <repository-url>
cd securechat

# Server configuration
cd server
cp .env.example .env
# Edit .env: set SECURECHAT_DATABASE_URL to your Neon connection string
nano .env
# or use your preferred editor

# Run migrations
export $(grep '^SECURECHAT_DATABASE_URL=' .env)
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" up

# Generate SQL code
sqlc generate

# Start server
go run cmd/server/main.go
```

The server also runs pending migrations automatically on startup, so the manual
`goose up` step is only needed when you want to migrate without starting the API.

---

## 7. Run as Systemd Service (Production-like)

```bash
# Create service file
sudo tee /etc/systemd/system/securechat.service > /dev/null <<'EOF'
[Unit]
Description=SecureChat Backend Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/securechat/server
EnvironmentFile=/home/ubuntu/securechat/server/.env
ExecStart=/usr/local/go/bin/go run cmd/server/main.go
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable securechat
sudo systemctl start securechat

# Check status
sudo systemctl status securechat

# View logs
sudo journalctl -u securechat -f
```

---

## 8. Android Development Setup

### Install Android Studio
```bash
# Option 1: Snap (easiest)
sudo snap install android-studio --classic

# Option 2: Manual
# Download from https://developer.android.com/studio
# Extract and run bin/studio.sh
```

### Configure SDK
1. Open Android Studio
2. Tools → SDK Manager
3. Install:
   - Android SDK Platform 34
   - Android SDK Build-Tools 34
   - Android Emulator
   - Google Play Intel x86 Atom System Image (for emulator)

### Configure Local Properties
```bash
cd android
# Replace with your laptop's LAN IP
echo "server.base.url=http://192.168.1.50:8080/api/v1" > local.properties
echo "server.ws.url=ws://192.168.1.50:8080/ws" >> local.properties
```

### Build and Run
```bash
# Make gradlew executable
chmod +x gradlew

# Debug build
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

---

## 9. Network Configuration (LAN Testing)

### Find Your LAN IP
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
# Or: hostname -I
# Use the IP (e.g., 192.168.1.50) in Android local.properties
```

### Test Connectivity from Android
```bash
# On Android device (via adb shell or terminal app)
curl http://192.168.1.50:8080/health
```

---

## 10. Verify Installation

### Test Backend
```bash
# Health check
curl http://localhost:8080/health

# Send OTP (mock provider)
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phone_number": "+15551234567"}'
```

### Test Database
```bash
cd server
export $(grep '^SECURECHAT_DATABASE_URL=' .env)
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" status
```

---

## 11. Common Issues

### Neon Connection Fails
- Verify `SECURECHAT_DATABASE_URL` includes `?sslmode=require`
- Check the connection string was copied in full (Neon truncates in the UI — use the copy button)
- Free-tier compute autosuspends after inactivity: the first request may take a few seconds

### Goose Migration Fails
```bash
# Check migration status / DSN
export $(grep '^SECURECHAT_DATABASE_URL=' .env)
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" status

# If tables from another project exist in the public schema, drop them first
# (Neon SQL Editor or psql):
#   DROP SCHEMA public CASCADE; CREATE SCHEMA public;
```

### Android Build Fails
```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Check Java version
java -version
# Should be 21 (Temurin/OpenJDK)

# Check Android SDK
echo $ANDROID_HOME
```

### WebSocket Connection Fails
- Ensure server IP in `local.properties` matches laptop LAN IP
- Check UFW allows port 8080: `sudo ufw status`
- Verify phone and laptop on same network
- Check server logs: `journalctl -u securechat -f`

### Permission Denied (goose/sqlc/air)
```bash
# Ensure GOPATH/bin is in PATH
export PATH=$PATH:$(go env GOPATH)/bin
echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc
```

---

## 12. Useful Commands

```bash
# SecureChat service management
sudo systemctl start securechat
sudo systemctl stop securechat
sudo systemctl restart securechat
sudo systemctl status securechat

# View SecureChat logs
sudo journalctl -u securechat -f
sudo journalctl -u securechat --since "1 hour ago"

# Migration status
cd server
export $(grep '^SECURECHAT_DATABASE_URL=' .env)
goose -dir migrations postgres "$SECURECHAT_DATABASE_URL" status

# Database backup (Neon also has built-in point-in-time restore)
pg_dump "$SECURECHAT_DATABASE_URL" > backup_$(date +%Y%m%d_%H%M%S).sql

# Database restore
psql "$SECURECHAT_DATABASE_URL" < backup_20240115_103000.sql

# Reset database (DANGEROUS - deletes all data)
# Run in the Neon SQL Editor:
#   DROP SCHEMA public CASCADE; CREATE SCHEMA public;
# then restart the server (migrations re-run automatically)

# Check open ports
sudo ss -tlnp | grep -E '5432|8080|11434'

# Monitor system resources
htop
# or
btop
```
