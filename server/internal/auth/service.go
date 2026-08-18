package auth

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/config"
	"github.com/securechat/server/internal/otp"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Service struct {
	repo        *Repository
	otpService  *otp.Service
	tokenManager *TokenManager
	cfg         *config.Config
}

func NewService(repo *Repository, otpService *otp.Service, tokenManager *TokenManager, cfg *config.Config) *Service {
	return &Service{
		repo:         repo,
		otpService:   otpService,
		tokenManager: tokenManager,
		cfg:          cfg,
	}
}

type RegisterResult struct {
	User         *User
	AccessToken  string
	RefreshToken string
	AccessExpiresIn  int
	RefreshExpiresIn int
}

func (s *Service) SendOTP(ctx context.Context, phoneNumber string) (*otp.SendOTPResult, error) {
	return s.otpService.SendOTP(ctx, phoneNumber)
}

func (s *Service) VerifyOTP(ctx context.Context, phoneNumber, otp, deviceName, deviceIdentifier string) (*RegisterResult, error) {
	valid, err := s.otpService.VerifyOTP(ctx, phoneNumber, otp)
	if err != nil {
		return nil, err
	}
	if !valid {
		return nil, apperrors.ErrInvalidOTP
	}

	// Find or create user
	user, err := s.findOrCreateUser(ctx, phoneNumber)
	if err != nil {
		return nil, fmt.Errorf("failed to find/create user: %w", err)
	}

	// Create device
	device, err := s.repo.CreateDevice(ctx, user.ID, deviceName, deviceIdentifier, "android")
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}

	// Create session
	refreshExpiresAt := time.Now().Add(time.Duration(s.cfg.JWT.RefreshTTL) * time.Second)
	session, err := s.repo.CreateSession(ctx, user.ID, device.ID, "placeholder", refreshExpiresAt)
	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	// Generate tokens
	accessToken, refreshToken, err := s.tokenManager.GenerateTokenPair(user.ID, device.ID, session.ID)
	if err != nil {
		return nil, fmt.Errorf("failed to generate tokens: %w", err)
	}

	// Update session with actual refresh token hash
	_, err = s.repo.CreateSession(ctx, user.ID, device.ID, refreshToken, refreshExpiresAt)
	if err != nil {
		logger.Log.Error("Failed to update session with refresh token", zap.Error(err))
	}

	// Update user online status
	s.repo.UpdateUserOnlineStatus(ctx, user.ID, true)

	return &RegisterResult{
		User:             user,
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		AccessExpiresIn:  s.cfg.JWT.AccessTTL,
		RefreshExpiresIn: s.cfg.JWT.RefreshTTL,
	}, nil
}

func (s *Service) RefreshToken(ctx context.Context, refreshToken string) (accessToken, newRefreshToken string, err error) {
	claims, err := s.tokenManager.ValidateRefreshToken(refreshToken)
	if err != nil {
		return "", "", apperrors.ErrTokenInvalid
	}

	// Find session and verify it's still active
	session, err := s.repo.FindSessionByRefreshToken(ctx, refreshToken)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", apperrors.ErrTokenRevoked
		}
		return "", "", fmt.Errorf("failed to find session: %w", err)
	}

	if session.RevokedAt.Valid || time.Now().After(session.ExpiresAt) {
		return "", "", apperrors.ErrTokenRevoked
	}

	// Revoke old session (rotation)
	if err := s.repo.RevokeSession(ctx, session.ID); err != nil {
		logger.Log.Error("Failed to revoke old session", zap.Error(err))
	}

	// Create new session
	newRefreshExpiresAt := time.Now().Add(time.Duration(s.cfg.JWT.RefreshTTL) * time.Second)
	newSession, err := s.repo.CreateSession(ctx, claims.UserID, claims.DeviceID, refreshToken, newRefreshExpiresAt)
	if err != nil {
		return "", "", fmt.Errorf("failed to create new session: %w", err)
	}

	// Generate new tokens
	newAccessToken, newRefreshToken, err := s.tokenManager.GenerateTokenPair(claims.UserID, claims.DeviceID, newSession.ID)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate new tokens: %w", err)
	}

	return newAccessToken, newRefreshToken, nil
}

func (s *Service) Logout(ctx context.Context, userID, sessionID int64) error {
	return s.repo.RevokeSession(ctx, sessionID)
}

func (s *Service) LogoutAll(ctx context.Context, userID int64) error {
	return s.repo.RevokeAllUserSessions(ctx, userID)
}

func (s *Service) GetCurrentUser(ctx context.Context, userID int64) (*User, error) {
	return s.repo.GetUserByID(ctx, userID)
}

func (s *Service) findOrCreateUser(ctx context.Context, phoneNumber string) (*User, error) {
	user, err := s.repo.FindUserByPhone(ctx, phoneNumber)
	if err != nil {
		if errors.Is(err, apperrors.ErrUserNotFound) {
			// Create new user
			displayName := "User " + phoneNumber[len(phoneNumber)-4:]
			return s.repo.CreateUser(ctx, phoneNumber, displayName)
		}
		return nil, err
	}
	return user, nil
}