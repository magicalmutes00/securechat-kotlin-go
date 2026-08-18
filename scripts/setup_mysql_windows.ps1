# SecureChat MySQL Setup Script for Windows
# Run as Administrator

Write-Host "=== SecureChat MySQL Setup ===" -ForegroundColor Cyan

# 1. Install MySQL using winget
Write-Host "Installing MySQL 8.0..." -ForegroundColor Yellow
winget install Oracle.MySQL --accept-source-agreements --accept-package-agreements

# 2. Start MySQL service
Write-Host "Starting MySQL service..." -ForegroundColor Yellow
Start-Service MySQL80

# 3. Wait for service to be ready
Write-Host "Waiting for MySQL to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 4. Create database and user
Write-Host "Creating database and user..." -ForegroundColor Yellow
$rootPassword = Read-Host "Enter MySQL root password (set during installation)"
$secureChatPassword = "SecureChat2024!StrongPass"

$mysqlCmd = "mysql -u root -p$rootPassword -e"
& $mysqlCmd "CREATE DATABASE IF NOT EXISTS securechat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
& $mysqlCmd "CREATE USER IF NOT EXISTS 'securechat'@'%' IDENTIFIED BY '$secureChatPassword';"
& $mysqlCmd "GRANT ALL PRIVILEGES ON securechat.* TO 'securechat'@'%';"
& $mysqlCmd "FLUSH PRIVILEGES;"

# 5. Configure .env
Write-Host "Configuring .env..." -ForegroundColor Yellow
$envPath = "C:\Users\Prath\Desktop\Projects\securechat\server\.env"
if (-not (Test-Path $envPath)) {
    Copy-Item "C:\Users\Prath\Desktop\Projects\securechat\server\.env.example" $envPath
}

$envContent = @"
SERVER_HOST=0.0.0.0
SERVER_PORT=8080
ENVIRONMENT=development

MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=securechat
MYSQL_USER=securechat
MYSQL_PASSWORD=$secureChatPassword
MYSQL_MAX_OPEN_CONNS=25
MYSQL_MAX_IDLE_CONNS=5
MYSQL_CONN_MAX_LIFETIME=300

JWT_ACCESS_SECRET=BASE64_ENCODED_RSA_PRIVATE_KEY
JWT_REFRESH_SECRET=BASE64_ENCODED_RSA_PRIVATE_KEY
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

$envContent | Out-File -FilePath $envPath -Encoding utf8

Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Generate RSA keys for JWT (see below)"
Write-Host "2. Run: cd server && goose -dir migrations mysql `"securechat:$secureChatPassword@tcp(localhost:3306)/securechat`" up"
Write-Host "3. Run: sqlc generate"
Write-Host "4. Start server: .\bin\server.exe"
Write-Host ""
Write-Host "Generate RSA keys for JWT:" -ForegroundColor Yellow
Write-Host 'go run -exec "go run" ./cmd/genkeys/main.go'