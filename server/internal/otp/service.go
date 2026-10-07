package otp

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/crypto"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Service struct {
	db          *sql.DB
	provider    Provider
	length      int
	ttl         time.Duration
	maxAttempts int
	resendCooldown time.Duration
}

func NewService(db *sql.DB, provider Provider, cfg *config.Config) *Service {
	return &Service{
		db:             db,
		provider:       provider,
		length:         cfg.OTP.Length,
		ttl:            time.Duration(cfg.OTP.TTL) * time.Second,
		maxAttempts:    cfg.OTP.MaxAttempts,
		resendCooldown: time.Duration(cfg.OTP.ResendCooldown) * time.Second,
	}
}

type SendOTPResult struct {
	ExpiresIn    int `json:"expires_in"`
	ResendCooldown int `json:"resend_cooldown"`
}

func (s *Service) SendOTP(ctx context.Context, phoneNumber string) (*SendOTPResult, error) {
	// Check for existing valid OTP (resend cooldown)
	var existingID int64
	var createdAt time.Time
	err := s.db.QueryRowContext(ctx, `
		SELECT id, created_at FROM otp_requests 
		WHERE phone_number = $1 AND verified_at IS NULL AND expires_at > NOW()
		ORDER BY created_at DESC LIMIT 1
	`, phoneNumber).Scan(&existingID, &createdAt)

	if err == nil {
		// Check resend cooldown
		if time.Since(createdAt) < s.resendCooldown {
			return nil, apperrors.ErrOTPResendCooldown
		}
	}

	// Generate OTP
	otp, err := crypto.GenerateOTP(s.length)
	if err != nil {
		return nil, fmt.Errorf("failed to generate OTP: %w", err)
	}

	// Hash OTP
	hashedOTP, err := crypto.HashOTP(otp)
	if err != nil {
		return nil, fmt.Errorf("failed to hash OTP: %w", err)
	}

	// Store OTP request
	expiresAt := time.Now().Add(s.ttl)
	var otpID int64
	err = s.db.QueryRowContext(ctx, `
		INSERT INTO otp_requests (phone_number, otp_hash, expires_at, attempt_count, created_at)
		VALUES ($1, $2, $3, 0, NOW())
		RETURNING id
	`, phoneNumber, hashedOTP, expiresAt).Scan(&otpID)

	if err != nil {
		return nil, fmt.Errorf("failed to store OTP: %w", err)
	}

	// Send via provider
if err := s.provider.Send(ctx, phoneNumber, otp); err != nil {
			// Mark as failed but don't expose OTP
			s.db.ExecContext(ctx, `UPDATE otp_requests SET verified_at = NOW() WHERE id = $1`, otpID)
			logger.Log.Error("Failed to send OTP", zap.String("provider", s.provider.Name()), zap.Error(err))
			return nil, apperrors.ErrInternalServer
		}

	logger.Log.Info("OTP sent", zap.String("phone", crypto.MaskPhoneNumber(phoneNumber)), zap.String("provider", s.provider.Name()))

	return &SendOTPResult{
		ExpiresIn:       int(s.ttl.Seconds()),
		ResendCooldown:  int(s.resendCooldown.Seconds()),
	}, nil
}

func (s *Service) VerifyOTP(ctx context.Context, phoneNumber, otp string) (bool, error) {
	// Find valid OTP request
	var id int64
	var hashedOTP string
	var attemptCount int
	var expiresAt time.Time
	var verifiedAt sql.NullTime

	err := s.db.QueryRowContext(ctx, `
		SELECT id, otp_hash, attempt_count, expires_at, verified_at
		FROM otp_requests
		WHERE phone_number = $1 AND verified_at IS NULL
		ORDER BY created_at DESC LIMIT 1
	`, phoneNumber).Scan(&id, &hashedOTP, &attemptCount, &expiresAt, &verifiedAt)

	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, apperrors.ErrInvalidOTP
		}
		return false, fmt.Errorf("failed to query OTP: %w", err)
	}

	// Check if already verified
	if verifiedAt.Valid {
		return false, apperrors.ErrInvalidOTP
	}

	// Check expiration
	if time.Now().After(expiresAt) {
		return false, apperrors.ErrOTPExpired
	}

	// Check attempt count
	if attemptCount >= s.maxAttempts {
		return false, apperrors.ErrOTPMaxAttempts
	}

	// Verify OTP
	if !crypto.VerifyOTP(hashedOTP, otp) {
		// Increment attempt count
		s.db.ExecContext(ctx, `UPDATE otp_requests SET attempt_count = attempt_count + 1 WHERE id = $1`, id)
		return false, apperrors.ErrInvalidOTP
	}

	// Mark as verified
	_, err = s.db.ExecContext(ctx, `UPDATE otp_requests SET verified_at = NOW() WHERE id = $1`, id)
	if err != nil {
		return false, fmt.Errorf("failed to mark OTP verified: %w", err)
	}

	return true, nil
}

// CleanupExpired removes expired OTP requests (can be run as a cron job)
func (s *Service) CleanupExpired(ctx context.Context) (int64, error) {
	result, err := s.db.ExecContext(ctx, `DELETE FROM otp_requests WHERE expires_at < NOW()`)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}
