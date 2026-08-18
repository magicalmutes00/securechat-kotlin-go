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

## 2. Install MySQL 8.0+

### Option A: MySQL Installer (Recommended)
1. Download MySQL Installer from https://dev.mysql.com/downloads/installer/
2. Run installer, select "Server only" or "Full"
3. Configure:
   - **Authentication Method**: Use Strong Password Encryption (recommended)
   - **Root Password**: Set a strong password
   - **Windows Service**: Enable "Configure MySQL Server as a Windows Service"
   - **Port**: 3306 (default)
4. Complete installation

### Option B: Chocolatey
```powershell
choco install mysql
```

### Option C: winget
```powershell
winget install Oracle.MySQL
```

### Post-installation
```powershell
# Connect to MySQL
mysql -u root -p

# Create database and user
CREATE DATABASE securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'securechat'@'%' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON securechat.* TO 'securechat'@'%';
FLUSH PRIVILEGES;
EXIT;
```

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
# Edit .env with your values (use notepad or VS Code)
notepad .env

# Run migrations
goose -dir migrations mysql "securechat:your_password@tcp(localhost:3306)/securechat" up

# Generate SQL code
sqlc generate

# Start server
go run cmd/server/main.go
```

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
mysql -u securechat -p securechat -e "SHOW TABLES;"
```

---

## 9. Common Issues

### MySQL Connection Refused
- Ensure MySQL service is running: `services.msc` → MySQL80 → Start
- Check port 3306 is not blocked by firewall
- Verify user has `%` host permission

### Goose Migration Fails
- Ensure database exists and user has privileges
- Check DSN format in command

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
# Stop MySQL service
Stop-Service MySQL80

# Start MySQL service
Start-Service MySQL80

# View MySQL logs
Get-EventLog -LogName Application -Source MySQL -Newest 50

# View Go server logs (if running as service)
# Check the terminal where go run is executing

# Reset database (DANGEROUS - deletes all data)
mysql -u root -p -e "DROP DATABASE securechat; CREATE DATABASE securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```