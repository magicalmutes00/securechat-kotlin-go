# SecureChat Authentication Flow Test - PowerShell
# Run after server is started: .\scripts\test_auth_flow.ps1

$BaseUrl = "http://localhost:8080/api/v1"
$Phone = "+15551234567"
$DeviceName = "Test Device"
$DeviceId = "test-device-123"

Write-Host "=== SecureChat Auth Flow Test ===" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Gray
Write-Host ""

# 1. Health check
Write-Host "1. Health Check..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$BaseUrl/../health" -Method Get
$health | ConvertTo-Json
Write-Host ""

# 2. Send OTP
Write-Host "2. Send OTP to $Phone..." -ForegroundColor Yellow
$otpResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/send-otp" -Method Post -ContentType "application/json" -Body (@{phone_number = $Phone} | ConvertTo-Json)
$otpResponse | ConvertTo-Json
Write-Host ""

Write-Host "Check server logs for OTP code (mock provider prints to console)" -ForegroundColor Cyan
Write-Host "Press Enter after noting the OTP from server logs..." -ForegroundColor Cyan
Read-Host

# 3. Verify OTP
$otp = Read-Host "Enter OTP from server logs"

$verifyResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/verify-otp" -Method Post -ContentType "application/json" -Body (@{
    phone_number = $Phone
    otp = $otp
    device_name = "Test Device"
    device_identifier = "test-device-123"
} | ConvertTo-Json)

$verifyResponse | ConvertTo-Json

$accessToken = $verifyResponse.data.access_token
$refreshToken = $verifyResponse.data.refresh_token

if (-not $accessToken -or $accessToken -eq "null") {
    Write-Host "Failed to get access token" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Access Token: $($accessToken.Substring(0,50))..." -ForegroundColor Green
Write-Host "Refresh Token: $($refreshToken.Substring(0,50))..." -ForegroundColor Green
Write-Host ""

# 4. Test authenticated endpoint
Write-Host "3. Get current user profile..." -ForegroundColor Yellow
$headers = @{ Authorization = "Bearer $accessToken" }
$profile = Invoke-RestMethod -Uri "$BaseUrl/users/me" -Method Get -Headers $headers
$profile | ConvertTo-Json
Write-Host ""

# 5. Test refresh token
Write-Host "4. Refresh token..." -ForegroundColor Yellow
$refreshResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/refresh" -Method Post -ContentType "application/json" -Body (@{refresh_token = $refreshToken} | ConvertTo-Json)
$refreshResponse | ConvertTo-Json

$newAccessToken = $refreshResponse.data.access_token
$newRefreshToken = $refreshResponse.data.refresh_token
Write-Host ""

# 6. Test conversations
Write-Host "5. List conversations..." -ForegroundColor Yellow
$newHeaders = @{ Authorization = "Bearer $newAccessToken" }
$conversations = Invoke-RestMethod -Uri "$BaseUrl/conversations" -Method Get -Headers $newHeaders
$conversations | ConvertTo-Json
Write-Host ""

# 7. Test logout
Write-Host "6. Logout..." -ForegroundColor Yellow
$logoutResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/logout" -Method Post -Headers $newHeaders
$logoutResponse | ConvertTo-Json

Write-Host ""
Write-Host "=== Auth Flow Test Complete ===" -ForegroundColor Green