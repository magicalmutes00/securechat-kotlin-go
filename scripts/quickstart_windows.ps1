# SecureChat Quick Start - Windows
# Run as Administrator

Write-Host "=== SecureChat Quick Start ===" -ForegroundColor Cyan

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Yellow

# Check Go
$goVersion = go version 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Go not found. Installing..." -ForegroundColor Yellow
    winget install GoLang.Go
    $env:PATH += ";C:\Program Files\Go\bin"
}

# Check MySQL
$mysqlPath = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysqlPath) {
    Write-Host "MySQL not found. Installing..." -ForegroundColor Yellow
    winget install Oracle.MySQL --accept-source-agreements --accept-package-agreements
}

# 1. Start MySQL
Write-Host "Starting MySQL service..." -ForegroundColor Yellow
Start-Service MySQL80 -ErrorAction SilentlyContinue
Start-Sleep -Seconds 5

# 2. Setup database
Write-Host "Setting up database..." -ForegroundColor Yellow
$rootPassword = Read-Host "Enter MySQL root password (set during installation)"
$secureChatPassword = "SecureChat2024!StrongPass"

mysql -u root -p$rootPassword -e "
CREATE DATABASE IF NOT EXISTS securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'securechat'@'%' IDENTIFIED BY '$secureChatPassword';
GRANT ALL PRIVILEGES ON securechat.* TO 'securechat'@'%';
FLUSH PRIVILEGES;"

# 3. Generate RSA keys
Write-Host "Generating RSA keys..." -ForegroundColor Yellow
cd server
$keys = go run ./cmd/genkeys
# Parse output to get keys
$output = go run ./cmd/genkeys 2>&1
$accessKey = ($output -match 'JWT_ACCESS_SECRET=(.+)') | % { $matches[1] }
$refreshKey = ($output -match 'JWT_REFRESH_SECRET=(.+)') | % { $matches[1] }

# 4. Create .env
Write-Host "Creating .env..." -ForegroundColor Yellow
$envContent = @"
SERVER_HOST=0.0.0.0
SERVER_PORT=8080
ENVIRONMENT=development

MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=securechat
MYSQL_USER=securechat
MYSQL_PASSWORD=SecureChat2024!StrongPass
MYSQL_MAX_OPEN_CONNS=25
MYSQL_MAX_IDLE_CONNS=5
MYSQL_CONN_MAX_LIFETIME=300

JWT_ACCESS_SECRET=$accessKey
JWT_REFRESH_SECRET=$refreshKey
JWT_ACCESS_TTL=900
JWT_REFRESH_TTL=2592000
JWT_ISSUER=securechat
JWT_AUDIENCE=securechat-android

OTP_PROVIDER=mock
OTP_LENGTH=6
OTP_TTL=300
OTP_MAX_ATTEMPTS=5
OTP_RESEND_COOLDOWN=60

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
CLOUDINARY_UPLOAD_FOLDER=securechat

LOG_LEVEL=debug
LOG_FORMAT=console
"@

$envContent | Out-File -FilePath .env -Encoding utf8

# 5. Run migrations
Write-Host "Running migrations..." -ForegroundColor Yellow
go install github.com/pressly/goose/v3/cmd/goose@latest
goose -dir migrations mysql "securechat:SecureChat2024!StrongPass@tcp(localhost:3306)/securechat" up

# 6. Generate sqlc
Write-Host "Generating sqlc..." -ForegroundColor Yellow
go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest
sqlc generate

# 7. Build and run
Write-Host "Building server..." -ForegroundColor Yellow
go build -o bin/server ./cmd/server

Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host "Start server with: .\bin\server.exe" -ForegroundColor Green
Write-Host "Health check: curl http://localhost:8080/health" -ForegroundColor Cyan
Write-Host "Test OTP: curl -X POST http://localhost:8080/api/v1/auth/send-otp -H 'Content-Type: application/json' -d '{\"phone_number\": \"+15551234567\"}'" -ForegroundColor Cyan