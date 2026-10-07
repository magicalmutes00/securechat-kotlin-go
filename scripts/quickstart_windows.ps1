# SecureChat Quick Start - Windows
# Sets up the Go backend against a Neon PostgreSQL database.

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

# Check goose (migrations tool)
$goosePath = Get-Command goose -ErrorAction SilentlyContinue
if (-not $goosePath) {
    Write-Host "Installing goose..." -ForegroundColor Yellow
    go install github.com/pressly/goose/v3/cmd/goose@latest
    $env:PATH += ";$env:USERPROFILE\go\bin"
}

cd server

# 1. Ensure .env exists
if (-not (Test-Path .env)) {
    Write-Host "Creating .env from .env.example..." -ForegroundColor Yellow
    Copy-Item .env.example .env
}

# 2. Get the Neon connection string
$envLine = Select-String -Path .env -Pattern '^SECURECHAT_DATABASE_URL=(.+)$' | Select-Object -First 1
$dbUrl = if ($envLine) { $envLine.Matches.Groups[1].Value } else { "" }
if (-not $dbUrl -or $dbUrl -match 'ep-xxxx') {
    $dbUrl = Read-Host "Enter Neon connection URL (postgresql://user:password@host/db?sslmode=require)"
    if ($envLine) {
        (Get-Content .env) -replace '^SECURECHAT_DATABASE_URL=.*$', "SECURECHAT_DATABASE_URL=$dbUrl" | Set-Content .env
    } else {
        Add-Content .env "SECURECHAT_DATABASE_URL=$dbUrl"
    }
}

# 3. Generate RSA keys and inject them into .env
Write-Host "Generating RSA keys..." -ForegroundColor Yellow
$output = go run ./cmd/genkeys 2>&1
$accessKey = ($output -match 'JWT_ACCESS_SECRET=(.+)') | ForEach-Object { $matches[1] }
$refreshKey = ($output -match 'JWT_REFRESH_SECRET=(.+)') | ForEach-Object { $matches[1] }
if ($accessKey -and $refreshKey) {
    $envContent = Get-Content .env -Raw
    $envContent = $envContent -replace '(?m)^SECURECHAT_JWT_ACCESS_SECRET=.*$', "SECURECHAT_JWT_ACCESS_SECRET=$accessKey"
    $envContent = $envContent -replace '(?m)^SECURECHAT_JWT_REFRESH_SECRET=.*$', "SECURECHAT_JWT_REFRESH_SECRET=$refreshKey"
    Set-Content -Path .env -Value $envContent -NoNewline
}

# 4. Run migrations against Neon
Write-Host "Running migrations..." -ForegroundColor Yellow
$env:PATH += ";$env:USERPROFILE\go\bin"
goose -dir migrations postgres "$dbUrl" up

# 5. Build and run
Write-Host "Building server..." -ForegroundColor Yellow
go build -o bin/server ./cmd/server

Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host "Start server with: .\bin\server.exe" -ForegroundColor Green
Write-Host "Health check: curl http://localhost:8080/health" -ForegroundColor Cyan
Write-Host "Test OTP: curl -X POST http://localhost:8080/api/v1/auth/send-otp -H 'Content-Type: application/json' -d '{\"phone_number\": \"+15551234567\"}'" -ForegroundColor Cyan
