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
	"github.com/securechat/server/pkg/crypto"
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

	return s.completeAuth(ctx, user, deviceName, deviceIdentifier)
}

type GoogleUserInfo struct {
	Sub         string
	Email       string
	EmailVerified bool
	Name        string
	Picture     string
}

func (s *Service) SignInWithGoogle(ctx context.Context, info GoogleUserInfo, deviceName, deviceIdentifier string) (*RegisterResult, error) {
	if !info.EmailVerified || info.Sub == "" {
		return nil, apperrors.ErrInvalidRequest
	}

	// Find or create user by Google subject
	user, err := s.repo.FindUserByGoogleSub(ctx, info.Sub)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			displayName := info.Name
			if displayName == "" {
				displayName = "Google User"
			}
			user, err = s.repo.CreateGoogleUser(ctx, info.Sub, info.Email, displayName)
			if err != nil {
				return nil, fmt.Errorf("failed to create google user: %w", err)
			}
		} else {
			return nil, fmt.Errorf("failed to find google user: %w", err)
		}
	}

	return s.completeAuth(ctx, user, deviceName, deviceIdentifier)
}

// SignInWithFirebase authenticates a user whose phone number was verified by
// Firebase Auth (the ID token was already validated server-side; only the
// verified claims arrive here). Existing OTP users are matched by phone, so a
// Firebase sign-in simply upgrades their session without losing data.
func (s *Service) SignInWithFirebase(ctx context.Context, phoneNumber, deviceName, deviceIdentifier string) (*RegisterResult, error) {
	if phoneNumber == "" {
		return nil, apperrors.ErrInvalidRequest
	}

	user, err := s.findOrCreateUser(ctx, phoneNumber)
	if err != nil {
		return nil, fmt.Errorf("failed to find/create firebase user: %w", err)
	}

	return s.completeAuth(ctx, user, deviceName, deviceIdentifier)
}

func (s *Service) completeAuth(ctx context.Context, user *User, deviceName, deviceIdentifier string) (*RegisterResult, error) {
	// Create device
	device, err := s.repo.CreateDevice(ctx, user.ID, deviceName, deviceIdentifier, "android")
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}

	// Create session, then generate tokens bound to it. The refresh token
	// contains the session ID, so the row must exist before the token pair is
	// minted; the token's hash is written back to the same row afterwards.
	refreshExpiresAt := time.Now().Add(time.Duration(s.cfg.JWT.RefreshTTL) * time.Second)
	placeholderHash, err := crypto.GenerateSecureToken(32)
	if err != nil {
		return nil, fmt.Errorf("failed to generate session placeholder: %w", err)
	}
	session, err := s.repo.CreateSession(ctx, user.ID, device.ID, placeholderHash, refreshExpiresAt)
	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	accessToken, refreshToken, err := s.tokenManager.GenerateTokenPair(user.ID, device.ID, session.ID)
	if err != nil {
		return nil, fmt.Errorf("failed to generate tokens: %w", err)
	}

	if err := s.repo.SetSessionRefreshHash(ctx, session.ID, refreshToken, refreshExpiresAt); err != nil {
		return nil, fmt.Errorf("failed to store refresh token: %w", err)
	}

	// Update user online status
	if err := s.repo.UpdateUserOnlineStatus(ctx, user.ID, true); err != nil {
		logger.Log.Error("Failed to mark user online", zap.Int64("user_id", user.ID), zap.Error(err))
	}

	return &RegisterResult{
		User:             user,
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		AccessExpiresIn:  s.cfg.JWT.AccessTTL,
		RefreshExpiresIn: s.cfg.JWT.RefreshTTL,
	}, nil
}

// refreshReuseGraceWindow is how long after a rotation a resubmitted *previous*
// refresh token is treated as a client retry rather than token theft. Rotation
// and reuse detection pull in opposite directions: a double-submitted refresh
// is indistinguishable from a replay unless the server remembers that it just
// rotated. Thirty seconds comfortably covers a retry storm while keeping the
// window useless to an attacker replaying a token harvested from an old log.
const refreshReuseGraceWindow = 30 * time.Second

func (s *Service) RefreshToken(ctx context.Context, refreshToken string) (accessToken, newRefreshToken string, err error) {
	claims, err := s.tokenManager.ValidateRefreshToken(refreshToken)
	if err != nil {
		return "", "", apperrors.ErrTokenInvalid
	}

	// Find the session named in the token; the digest comparison happens
	// against the stored hash below.
	session, err := s.repo.FindSessionByRefreshToken(ctx, claims.SessionID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", apperrors.ErrTokenRevoked
		}
		return "", "", fmt.Errorf("failed to find session: %w", err)
	}

	if session.RevokedAt.Valid || time.Now().After(session.ExpiresAt) {
		return "", "", apperrors.ErrTokenRevoked
	}

	// Refresh tokens are high-entropy signed JWTs, stored as SHA-256 digests
	// (see crypto.HashToken), so a re-computed digest cannot be inverted or
	// rainbow-tabled; the constant-time compare keeps the check itself from
	// leaking timing information.
	if !crypto.CheckToken(session.RefreshTokenHash, refreshToken) {
		// A digest mismatch means the presented token is not the session's
		// current one. That is either theft (a rotated-out token replayed) or a
		// benign retry of a rotation that happened seconds ago. Only the second
		// explanation is plausible inside the grace window, and revoking every
		// session on a double-submit is a far worse failure than answering one
		// retry with a retryable conflict.
		if session.RefreshRotatedAt.Valid &&
			time.Since(session.RefreshRotatedAt.Time) < refreshReuseGraceWindow {
			logger.Log.Info("Refresh token resubmitted within rotation grace window",
				zap.Int64("user_id", session.UserID),
				zap.Int64("session_id", session.ID),
			)
			return "", "", apperrors.ErrRefreshConflict
		}

		// Outside the window this is a replayed rotated-out token, a classic
		// sign of theft. Revoke everything for this user.
		logger.Log.Warn("Refresh token reuse detected, revoking all sessions",
			zap.Int64("user_id", session.UserID),
			zap.Int64("session_id", session.ID),
		)
		_ = s.repo.RevokeAllUserSessions(ctx, session.UserID)
		return "", "", apperrors.ErrTokenRevoked
	}

	if session.UserID != claims.UserID || session.DeviceID != claims.DeviceID {
		return "", "", apperrors.ErrTokenInvalid
	}

	// Rotate in place: same session row and ID, new refresh token hash.
	newRefreshExpiresAt := time.Now().Add(time.Duration(s.cfg.JWT.RefreshTTL) * time.Second)
	newAccessToken, newRefreshToken, err := s.tokenManager.GenerateTokenPair(claims.UserID, claims.DeviceID, session.ID)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate new tokens: %w", err)
	}

	rotated, err := s.repo.RotateSessionRefreshHash(ctx, session.ID, refreshToken, newRefreshToken, newRefreshExpiresAt)
	if err != nil {
		return "", "", fmt.Errorf("failed to store rotated refresh token: %w", err)
	}
	if !rotated {
		// Lost a concurrent rotation race: the token we validated was the
		// session's current one when we read it, so this is a double-submit
		// (client retry, or two devices sharing a token), not theft. Report a
		// retryable conflict and deliberately leave every session intact.
		logger.Log.Info("Concurrent refresh rotation detected",
			zap.Int64("user_id", session.UserID),
			zap.Int64("session_id", session.ID),
		)
		return "", "", apperrors.ErrRefreshConflict
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