package auth

import (
	"time"

	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/jwt"
)

type TokenManager struct {
	*jwt.TokenManager
}

func NewTokenManager(cfg *config.Config) (*TokenManager, error) {
	accessTTL := time.Duration(cfg.JWT.AccessTTL) * time.Second
	refreshTTL := time.Duration(cfg.JWT.RefreshTTL) * time.Second

	tm, err := jwt.NewTokenManager(
		cfg.JWT.AccessSecret,
		cfg.JWT.RefreshSecret,
		accessTTL,
		refreshTTL,
		cfg.JWT.Issuer,
		cfg.JWT.Audience,
	)
	if err != nil {
		return nil, err
	}

	return &TokenManager{TokenManager: tm}, nil
}

// ValidateAccessClaims implements middleware.ClaimsValidator so route modules
// can share one auth middleware without importing this package's claim types.
func (tm *TokenManager) ValidateAccessClaims(tokenString string) (userID, deviceID, sessionID int64, err error) {
	claims, err := tm.ValidateAccessToken(tokenString)
	if err != nil {
		return 0, 0, 0, err
	}
	return claims.UserID, claims.DeviceID, claims.SessionID, nil
}