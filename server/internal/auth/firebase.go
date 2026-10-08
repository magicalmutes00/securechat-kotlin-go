package auth

import (
	"context"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Firebase ID tokens are signed by Google's "securetoken" service account and
// carry iss/aud both equal to the Firebase project ID. The public keys live on
// Google's generic service-account JWKS endpoint, so verification needs no
// service-account credentials or Admin SDK on the server.
const (
	firebaseJWKSURL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
	jwksCacheTTL    = 6 * time.Hour
)

// FirebaseUserInfo is the subset of a verified Firebase ID token's claims the
// backend uses to authenticate a phone sign-in.
type FirebaseUserInfo struct {
	UID         string
	PhoneNumber string
}

// FirebaseTokenVerifier validates Firebase ID tokens against Google's public
// key set, caching the JWKS for a few hours to avoid a round trip on every
// sign-in.
type FirebaseTokenVerifier struct {
	projectID string
	client    *http.Client

	mu        sync.RWMutex
	keys      map[string]*rsa.PublicKey // key ID -> public key
	fetchedAt time.Time
}

// NewFirebaseTokenVerifier builds a verifier for the given Firebase project.
// projectID must match the "aud"/"iss" claims (and the android/firebase config).
func NewFirebaseTokenVerifier(projectID string) *FirebaseTokenVerifier {
	return &FirebaseTokenVerifier{
		projectID: projectID,
		client:    &http.Client{Timeout: 10 * time.Second},
	}
}

// jwtKeyset mirrors the JWKS document returned by Google's service account
// endpoint. Only RSA keys are currently served for the securetoken signer.
type jwtKeyset struct {
	Keys []jwtKey `json:"keys"`
}

type jwtKey struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Alg string `json:"alg"`
	Use string `json:"use"`
	N   string `json:"n"`
	E   string `json:"e"`
}

// fetchKeys downloads and decodes the JWKS for the securetoken signer.
func (v *FirebaseTokenVerifier) fetchKeys(ctx context.Context) (map[string]*rsa.PublicKey, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, firebaseJWKSURL, nil)
	if err != nil {
		return nil, fmt.Errorf("build jwks request: %w", err)
	}
	resp, err := v.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch firebase jwks: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetch firebase jwks: unexpected status %d", resp.StatusCode)
	}

	var set jwtKeyset
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return nil, fmt.Errorf("decode firebase jwks: %w", err)
	}

	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, k := range set.Keys {
		if k.Kid == "" || k.N == "" || k.E == "" {
			continue
		}
		nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
		if err != nil {
			continue
		}
		eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
		if err != nil {
			continue
		}
		e := decodeExponent(eBytes)
		if e == 0 {
			continue
		}
		keys[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: e}
	}
	if len(keys) == 0 {
		return nil, fmt.Errorf("firebase jwks contained no usable RSA keys")
	}
	return keys, nil
}

func decodeExponent(b []byte) int {
	if len(b) == 0 {
		return 0
	}
	v := 0
	for _, x := range b {
		v = v<<8 | int(x)
	}
	return v
}

// publicKey looks up the cached RSA key for a key ID, refreshing the JWKS when
// the cache is empty or stale (the kid is unknown, which happens when Google
// rotates keys).
func (v *FirebaseTokenVerifier) publicKey(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	v.mu.RLock()
	key, ok := v.keys[kid]
	cached := time.Since(v.fetchedAt) < jwksCacheTTL
	v.mu.RUnlock()

	if ok && cached {
		return key, nil
	}

	keys, err := v.fetchKeys(ctx)
	if err != nil {
		return nil, err
	}

	v.mu.Lock()
	v.keys = keys
	v.fetchedAt = time.Now()
	v.mu.Unlock()

	v.mu.RLock()
	key, ok = v.keys[kid]
	v.mu.RUnlock()
	if !ok {
		return nil, fmt.Errorf("no firebase key for kid %q", kid)
	}
	return key, nil
}

// Verify parses and validates a Firebase ID token, returning the claims the
// backend trusts for a phone sign-in. It enforces RS256, the expected
// iss/aud, and a valid expiry, and requires the token to carry a phone number
// (i.e. it was minted by the Firebase phone provider).
func (v *FirebaseTokenVerifier) Verify(ctx context.Context, idToken string) (*FirebaseUserInfo, error) {
	if idToken == "" {
		return nil, fmt.Errorf("empty firebase id token")
	}

	expectedIss := "https://securetoken.google.com/" + v.projectID

	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{"RS256"}),
		jwt.WithAudience(v.projectID),
		jwt.WithIssuer(expectedIss),
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(30*time.Second),
	)

	claims := jwt.MapClaims{}
	token, err := parser.ParseWithClaims(idToken, claims, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		if kid == "" {
			return nil, fmt.Errorf("token missing kid header")
		}
		return v.publicKey(ctx, kid)
	})
	if err != nil || !token.Valid {
		return nil, fmt.Errorf("invalid firebase id token: %w", err)
	}

	uid, _ := claims["sub"].(string)
	phone, _ := claims["phone_number"].(string)
	if strings.TrimSpace(uid) == "" {
		return nil, fmt.Errorf("firebase token missing subject")
	}
	if strings.TrimSpace(phone) == "" {
		return nil, fmt.Errorf("firebase token has no phone_number claim")
	}

	return &FirebaseUserInfo{UID: uid, PhoneNumber: phone}, nil
}