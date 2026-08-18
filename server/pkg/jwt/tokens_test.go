package jwt

import (
	"encoding/base64"
	"testing"
	"time"
)

func TestTokenManager(t *testing.T) {
	// Generate test keys
	accessPriv, _, err := GenerateKeyPair()
	if err != nil {
		t.Fatalf("GenerateKeyPair failed: %v", err)
	}

	refreshPriv, _, err := GenerateKeyPair()
	if err != nil {
		t.Fatalf("GenerateKeyPair failed: %v", err)
	}

	// Encode keys as base64 (as expected by NewTokenManager)
	accessPrivB64 := base64.StdEncoding.EncodeToString([]byte(accessPriv))
	refreshPrivB64 := base64.StdEncoding.EncodeToString([]byte(refreshPriv))

	tm, err := NewTokenManager(
		accessPrivB64, refreshPrivB64,
		15*time.Minute, 30*24*time.Hour,
		"securechat", "securechat-android",
	)
	if err != nil {
		t.Fatalf("NewTokenManager failed: %v", err)
	}

	// Test access token generation
	accessToken, err := tm.GenerateAccessToken(1, 2, 3)
	if err != nil {
		t.Fatalf("GenerateAccessToken failed: %v", err)
	}

	if accessToken == "" {
		t.Fatal("GenerateAccessToken returned empty string")
	}

	// Test refresh token generation
	refreshToken, err := tm.GenerateRefreshToken(1, 2, 3)
	if err != nil {
		t.Fatalf("GenerateRefreshToken failed: %v", err)
	}

	if refreshToken == "" {
		t.Fatal("GenerateRefreshToken returned empty string")
	}

	// Test token pair generation
	at, rt, err := tm.GenerateTokenPair(1, 2, 3)
	if err != nil {
		t.Fatalf("GenerateTokenPair failed: %v", err)
	}

	if at == "" || rt == "" {
		t.Fatal("GenerateTokenPair returned empty tokens")
	}

	// Test validation
	claims, err := tm.ValidateAccessToken(at)
	if err != nil {
		t.Fatalf("ValidateAccessToken failed: %v", err)
	}

	if claims.UserID != 1 || claims.DeviceID != 2 || claims.SessionID != 3 {
		t.Fatalf("Claims mismatch: %+v", claims)
	}

	if claims.Type != "access" {
		t.Fatalf("Wrong token type: %s", claims.Type)
	}

	// Test refresh token validation
	refreshClaims, err := tm.ValidateRefreshToken(rt)
	if err != nil {
		t.Fatalf("ValidateRefreshToken failed: %v", err)
	}

	if refreshClaims.Type != "refresh" {
		t.Fatalf("Wrong refresh token type: %s", refreshClaims.Type)
	}

	// Test cross-validation fails
	_, err = tm.ValidateAccessToken(rt)
	if err == nil {
		t.Fatal("ValidateAccessToken should fail for refresh token")
	}

	_, err = tm.ValidateRefreshToken(at)
	if err == nil {
		t.Fatal("ValidateRefreshToken should fail for access token")
	}
}

func TestExpiredToken(t *testing.T) {
	accessPriv, _, _ := GenerateKeyPair()
	refreshPriv, _, _ := GenerateKeyPair()

	accessPrivB64 := base64.StdEncoding.EncodeToString([]byte(accessPriv))
	refreshPrivB64 := base64.StdEncoding.EncodeToString([]byte(refreshPriv))

	tm, err := NewTokenManager(
		accessPrivB64, refreshPrivB64,
		-1*time.Hour, // expired
		-1*time.Hour,
		"securechat", "securechat-android",
	)
	if err != nil {
		t.Fatalf("NewTokenManager failed: %v", err)
	}

	token, _ := tm.GenerateAccessToken(1, 2, 3)
	_, valErr := tm.ValidateAccessToken(token)
	if valErr == nil {
		t.Fatal("ValidateAccessToken should fail for expired token")
	}
}

func TestWrongAudience(t *testing.T) {
	accessPriv, _, _ := GenerateKeyPair()
	refreshPriv, _, _ := GenerateKeyPair()

	accessPrivB64 := base64.StdEncoding.EncodeToString([]byte(accessPriv))
	refreshPrivB64 := base64.StdEncoding.EncodeToString([]byte(refreshPriv))

	tm, err := NewTokenManager(
		accessPrivB64, refreshPrivB64,
		15*time.Minute, 30*24*time.Hour,
		"securechat", "wrong-audience",
	)
	if err != nil {
		t.Fatalf("NewTokenManager failed: %v", err)
	}

	token, _ := tm.GenerateAccessToken(1, 2, 3)

	// Create new manager with correct audience
	tm2, _ := NewTokenManager(
		accessPrivB64, refreshPrivB64,
		15*time.Minute, 30*24*time.Hour,
		"securechat", "securechat-android",
	)

	_, valErr := tm2.ValidateAccessToken(token)
	if valErr == nil {
		t.Fatal("ValidateAccessToken should fail for wrong audience")
	}
}

func TestPublicKeyExport(t *testing.T) {
	accessPriv, _, _ := GenerateKeyPair()
	refreshPriv, _, _ := GenerateKeyPair()

	accessPrivB64 := base64.StdEncoding.EncodeToString([]byte(accessPriv))
	refreshPrivB64 := base64.StdEncoding.EncodeToString([]byte(refreshPriv))

	tm, err := NewTokenManager(
		accessPrivB64, refreshPrivB64,
		15*time.Minute, 30*24*time.Hour,
		"securechat", "securechat-android",
	)
	if err != nil {
		t.Fatalf("NewTokenManager failed: %v", err)
	}

	accessPubPEM, err := tm.GetAccessPublicKeyPEM()
	if err != nil {
		t.Fatalf("GetAccessPublicKeyPEM failed: %v", err)
	}

	if accessPubPEM == "" {
		t.Fatal("GetAccessPublicKeyPEM returned empty string")
	}

	refreshPubPEM, err := tm.GetRefreshPublicKeyPEM()
	if err != nil {
		t.Fatalf("GetRefreshPublicKeyPEM failed: %v", err)
	}

	if refreshPubPEM == "" {
		t.Fatal("GetRefreshPublicKeyPEM returned empty string")
	}
}