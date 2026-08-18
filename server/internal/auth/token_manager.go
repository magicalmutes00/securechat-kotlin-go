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