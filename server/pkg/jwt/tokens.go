package jwt

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/securechat/server/pkg/logger"
)

type TokenManager struct {
	accessPrivateKey  *rsa.PrivateKey
	accessPublicKey   *rsa.PublicKey
	refreshPrivateKey *rsa.PrivateKey
	refreshPublicKey  *rsa.PublicKey
	accessTTL         time.Duration
	refreshTTL        time.Duration
	issuer            string
	audience          string
}

type Claims struct {
	UserID    int64  `json:"sub"`
	DeviceID  int64  `json:"device_id"`
	SessionID int64  `json:"session_id"`
	Type      string `json:"type"` // "access" or "refresh"
	jwt.RegisteredClaims
}

func NewTokenManager(
	accessSecretB64, refreshSecretB64 string,
	accessTTL, refreshTTL time.Duration,
	issuer, audience string,
) (*TokenManager, error) {
	var accessPriv *rsa.PrivateKey
	var accessPub *rsa.PublicKey
	var refreshPriv *rsa.PrivateKey
	var refreshPub *rsa.PublicKey
	var err error

	// Generate access key if not provided; fail hard if provided but invalid.
	// A silent ephemeral fallback would invalidate every token on restart and
	// hide misconfiguration (e.g. a typo'd env var) in production.
	if accessSecretB64 == "" {
		logger.Log.Warn("JWT access secret not configured - generating EPHEMERAL key pair (tokens will not survive restart)")
		accessPriv, accessPub, err = generateKeyPair()
		if err != nil {
			return nil, fmt.Errorf("failed to generate access key: %w", err)
		}
	} else {
		accessPriv, accessPub, err = parseKeys(accessSecretB64)
		if err != nil {
			return nil, fmt.Errorf("invalid JWT access secret: %w", err)
		}
	}

	// Generate refresh key if not provided; fail hard if provided but invalid.
	if refreshSecretB64 == "" {
		logger.Log.Warn("JWT refresh secret not configured - generating EPHEMERAL key pair (tokens will not survive restart)")
		refreshPriv, refreshPub, err = generateKeyPair()
		if err != nil {
			return nil, fmt.Errorf("failed to generate refresh key: %w", err)
		}
	} else {
		refreshPriv, refreshPub, err = parseKeys(refreshSecretB64)
		if err != nil {
			return nil, fmt.Errorf("invalid JWT refresh secret: %w", err)
		}
	}

	return &TokenManager{
		accessPrivateKey:  accessPriv,
		accessPublicKey:   accessPub,
		refreshPrivateKey: refreshPriv,
		refreshPublicKey:  refreshPub,
		accessTTL:         accessTTL,
		refreshTTL:        refreshTTL,
		issuer:            issuer,
		audience:          audience,
	}, nil
}

func parseKeys(secretB64 string) (*rsa.PrivateKey, *rsa.PublicKey, error) {
	// Decode base64 encoded PEM
	pemBytes, err := base64.StdEncoding.DecodeString(secretB64)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to base64 decode: %w", err)
	}

	// Parse private key
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, nil, errors.New("failed to decode PEM block")
	}

	var privKey *rsa.PrivateKey
	if block.Type == "PRIVATE KEY" {
		key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
		if err != nil {
			return nil, nil, fmt.Errorf("failed to parse PKCS8: %w", err)
		}
		rsaKey, ok := key.(*rsa.PrivateKey)
		if !ok {
			return nil, nil, fmt.Errorf("private key is %T, want RSA", key)
		}
		privKey = rsaKey
	} else if block.Type == "RSA PRIVATE KEY" {
		parsed, err := x509.ParsePKCS1PrivateKey(block.Bytes)
		if err != nil {
			return nil, nil, fmt.Errorf("failed to parse PKCS1: %w", err)
		}
		privKey = parsed
	} else {
		return nil, nil, errors.New("unsupported key type: " + block.Type)
	}

	// Extract public key
	pubKey := &privKey.PublicKey

	return privKey, pubKey, nil
}

// generateKeyPair generates a new RSA key pair
func generateKeyPair() (*rsa.PrivateKey, *rsa.PublicKey, error) {
	privKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, nil, err
	}

	return privKey, &privKey.PublicKey, nil
}

// GenerateAccessToken generates a JWT access token
func (tm *TokenManager) GenerateAccessToken(userID, deviceID, sessionID int64) (string, error) {
	now := time.Now()
	claims := Claims{
		UserID:    userID,
		DeviceID:  deviceID,
		SessionID: sessionID,
		Type:      "access",
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.New().String(),
			Subject:   fmt.Sprintf("%d", userID),
			Issuer:    tm.issuer,
			Audience:  jwt.ClaimStrings{tm.audience},
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(tm.accessTTL)),
			NotBefore: jwt.NewNumericDate(now),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	token.Header["kid"] = "access-1"
	return token.SignedString(tm.accessPrivateKey)
}

// GenerateRefreshToken generates a JWT refresh token
func (tm *TokenManager) GenerateRefreshToken(userID, deviceID, sessionID int64) (string, error) {
	now := time.Now()
	claims := Claims{
		UserID:    userID,
		DeviceID:  deviceID,
		SessionID: sessionID,
		Type:      "refresh",
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.New().String(),
			Subject:   fmt.Sprintf("%d", userID),
			Issuer:    tm.issuer,
			Audience:  jwt.ClaimStrings{tm.audience},
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(tm.refreshTTL)),
			NotBefore: jwt.NewNumericDate(now),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	token.Header["kid"] = "refresh-1"
	return token.SignedString(tm.refreshPrivateKey)
}

// GenerateTokenPair generates both access and refresh tokens
func (tm *TokenManager) GenerateTokenPair(userID, deviceID, sessionID int64) (accessToken, refreshToken string, err error) {
	accessToken, err = tm.GenerateAccessToken(userID, deviceID, sessionID)
	if err != nil {
		return "", "", err
	}
	refreshToken, err = tm.GenerateRefreshToken(userID, deviceID, sessionID)
	return accessToken, refreshToken, err
}

// ValidateAccessToken validates a JWT access token
func (tm *TokenManager) ValidateAccessToken(tokenString string) (*Claims, error) {
	return tm.validateToken(tokenString, tm.accessPublicKey, "access")
}

// ValidateRefreshToken validates a JWT refresh token
func (tm *TokenManager) ValidateRefreshToken(tokenString string) (*Claims, error) {
	return tm.validateToken(tokenString, tm.refreshPublicKey, "refresh")
}

// validateToken validates a JWT token
func (tm *TokenManager) validateToken(tokenString string, publicKey *rsa.PublicKey, expectedType string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (any, error) {
		// Pin to RS256 exactly; accepting the whole RSA family would let a
		// token signed with a different RSA variant slip through.
		if token.Method != jwt.SigningMethodRS256 {
			return nil, errors.New("unexpected signing method")
		}
		return publicKey, nil
	})

	if err != nil {
		return nil, err
	}

	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid token claims")
	}

	if claims.Type != expectedType {
		return nil, errors.New("unexpected token type")
	}

	if claims.Issuer != tm.issuer {
		return nil, errors.New("invalid issuer")
	}

	// Check audience
	audienceValid := false
	for _, aud := range claims.RegisteredClaims.Audience {
		if aud == tm.audience {
			audienceValid = true
			break
		}
	}
	if !audienceValid {
		return nil, errors.New("invalid audience")
	}

	return claims, nil
}

// GetAccessPublicKeyPEM returns the access public key in PEM format
func (tm *TokenManager) GetAccessPublicKeyPEM() (string, error) {
	return publicKeyToPEM(tm.accessPublicKey)
}

// GetRefreshPublicKeyPEM returns the refresh public key in PEM format
func (tm *TokenManager) GetRefreshPublicKeyPEM() (string, error) {
	return publicKeyToPEM(tm.refreshPublicKey)
}

// publicKeyToPEM converts a public key to PEM format
func publicKeyToPEM(pub *rsa.PublicKey) (string, error) {
	pubASN1, err := x509.MarshalPKIXPublicKey(pub)
	if err != nil {
		return "", err
	}
	pubPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: pubASN1,
	})
	return string(pubPEM), nil
}

// GenerateKeyPair generates a new RSA key pair for development
func GenerateKeyPair() (privateKeyPEM, publicKeyPEM string, err error) {
	privKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return "", "", err
	}

	privASN1 := x509.MarshalPKCS1PrivateKey(privKey)
	privPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "RSA PRIVATE KEY",
		Bytes: privASN1,
	})

	pubASN1, err := x509.MarshalPKIXPublicKey(&privKey.PublicKey)
	if err != nil {
		return "", "", err
	}
	pubPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: pubASN1,
	})

	return string(privPEM), string(pubPEM), nil
}