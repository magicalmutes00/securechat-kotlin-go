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
# Install Go 1.22+
sudo apt install -y golang-go

# Verify
go version
# Should output: go version go1.22.x linux/amd64
```

### Alternative: Install specific Go version
```bash
# Download and install specific version
wget https://go.dev/dl/go1.22.5.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.22.5.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.profile
source ~/.profile
go version
```

---

## 3. Install MySQL 8.0+

```bash
# Install MySQL server
sudo apt install -y mysql-server

# Secure installation
sudo mysql_secure_installation
# Follow prompts:
# - Validate password plugin: No (or Yes with MEDIUM)
# - Root password: Set strong password
# - Remove anonymous users: Yes
# - Disallow root login remotely: Yes
# - Remove test database: Yes
# - Reload privilege tables: Yes
```

### Create Database and User
```bash
sudo mysql -u root -p
```

```sql
CREATE DATABASE securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'securechat'@'%' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON securechat.* TO 'securechat'@'%';
FLUSH PRIVILEGES;
EXIT;
```

### Configure MySQL for Remote Access (if needed)
```bash
# Edit MySQL config
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf

# Find bind-address and change to:
bind-address = 0.0.0.0

# Restart MySQL
sudo systemctl restart mysql
```

### Firewall (UFW)
```bash
sudo ufw allow 3306/tcp comment "MySQL"
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
# Edit .env with your values
nano .env
# or use your preferred editor

# Run migrations
goose -dir migrations mysql "securechat:your_password@tcp(localhost:3306)/securechat" up

# Generate SQL code
sqlc generate

# Start server
go run cmd/server/main.go
```

---

## 7. Run as Systemd Service (Production-like)

```bash
# Create service file
sudo tee /etc/systemd/system/securechat.service > /dev/null <<'EOF'
[Unit]
Description=SecureChat Backend Server
After=network.target mysql.service
Requires=mysql.service

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
mysql -u securechat -p securechat -e "SHOW TABLES;"
```

---

## 11. Common Issues

### MySQL Connection Refused
```bash
# Check MySQL status
sudo systemctl status mysql

# Start if stopped
sudo systemctl start mysql

# Check port
sudo netstat -tlnp | grep 3306

# Check error log
sudo tail -f /var/log/mysql/error.log
```

### Goose Migration Fails
```bash
# Verify database exists
mysql -u securechat -p -e "USE securechat; SHOW TABLES;"

# Check DSN format
goose -dir migrations mysql "securechat:password@tcp(localhost:3306)/securechat" status
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
# MySQL service management
sudo systemctl start mysql
sudo systemctl stop mysql
sudo systemctl restart mysql
sudo systemctl status mysql

# SecureChat service management
sudo systemctl start securechat
sudo systemctl stop securechat
sudo systemctl restart securechat
sudo systemctl status securechat

# View SecureChat logs
sudo journalctl -u securechat -f
sudo journalctl -u securechat --since "1 hour ago"

# MySQL logs
sudo tail -f /var/log/mysql/error.log

# Database backup
mysqldump -u securechat -p securechat > backup_$(date +%Y%m%d_%H%M%S).sql

# Database restore
mysql -u securechat -p securechat < backup_20240115_103000.sql

# Reset database (DANGEROUS)
mysql -u root -p -e "DROP DATABASE securechat; CREATE DATABASE securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Check open ports
sudo ss -tlnp | grep -E '3306|8080|11434'

# Monitor system resources
htop
# or
btop
```