#!/bin/bash
# SecureChat Authentication Flow Test
# Run after server is started: ./scripts/test_auth_flow.sh

BASE_URL="http://localhost:8080/api/v1"
PHONE="+15551234567"
DEVICE_NAME="Test Device"
DEVICE_ID="test-device-123"

echo "=== SecureChat Auth Flow Test ==="
echo "Base URL: $BASE_URL"
echo ""

# 1. Health check
echo "1. Health Check..."
curl -s "$BASE_URL/../health" | jq .
echo ""

# 2. Send OTP
echo "2. Send OTP to $PHONE..."
OTP_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/send-otp" \
  -H "Content-Type: application/json" \
  -d "{\"phone_number\": \"$PHONE\"}")
echo "$OTP_RESPONSE" | jq .
echo ""

# Extract OTP from server logs (mock provider prints to stdout)
# In mock mode, OTP is printed to server console
echo "Check server logs for OTP code..."
echo "Press Enter after noting the OTP from server logs..."
read

# 3. Verify OTP (user enters OTP)
read -p "Enter OTP from server logs: " OTP

VERIFY_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"phone_number\": \"$PHONE\", \"otp\": \"$OTP\", \"device_name\": \"Test Device\", \"device_identifier\": \"test-device-123\"}")

echo "$VERIFY_RESPONSE" | jq .

# Extract tokens
ACCESS_TOKEN=$(echo "$VERIFY_RESPONSE" | jq -r '.data.access_token')
REFRESH_TOKEN=$(echo "$VERIFY_RESPONSE" | jq -r '.data.refresh_token')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "Failed to get access token"
    exit 1
fi

echo ""
echo "Access Token: ${ACCESS_TOKEN:0:50}..."
echo "Refresh Token: ${REFRESH_TOKEN:0:50}..."
echo ""

# 4. Test authenticated endpoint
echo "3. Get current user profile..."
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/users/me" | jq .
echo ""

# 5. Test refresh token
echo "4. Refresh token..."
REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refresh_token\": \"$REFRESH_TOKEN\"}")
echo "$REFRESH_RESPONSE" | jq .

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.data.access_token')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.data.refresh_token')
echo ""

# 6. Test conversations
echo "5. List conversations..."
curl -s -H "Authorization: Bearer $NEW_ACCESS_TOKEN" "$BASE_URL/conversations" | jq .
echo ""

# 7. Test logout
echo "6. Logout..."
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN" | jq .

echo ""
echo "=== Auth Flow Test Complete ==="