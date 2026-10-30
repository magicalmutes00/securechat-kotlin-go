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

	// ErrNoRows simply means there is no live request to rate-limit against;
	// any other error is a real failure and must not be swallowed.
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("failed to check existing OTP: %w", err)
	}

	if err == nil && time.Since(createdAt) < s.resendCooldown {
		return nil, apperrors.ErrOTPResendCooldown
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
		if _, execErr := s.db.ExecContext(ctx, `UPDATE otp_requests SET verified_at = NOW() WHERE id = $1`, otpID); execErr != nil {
			logger.Log.Error("Failed to invalidate undelivered OTP", zap.Int64("otp_id", otpID), zap.Error(execErr))
		}
		logger.Log.Error("Failed to send OTP", zap.String("provider", s.provider.Name()), zap.Error(err))
		return nil, apperrors.ErrInternalServer
	}

	logger.Log.Info("OTP sent", zap.String("phone", crypto.MaskPhoneNumber(phoneNumber)), zap.String("provider", s.provider.Name()))

	return &SendOTPResult{
		ExpiresIn:       int(s.ttl.Seconds()),
		ResendCooldown:  int(s.resendCooldown.Seconds()),
	}, nil
}

// VerifyOTP checks a submitted code against the newest live request for the
// phone number.
//
// Attempt accounting is deliberately a single atomic UPDATE ... RETURNING that
// claims one attempt before any hashing work happens. The previous
// read-then-compare-then-increment sequence was raceable: N parallel requests
// all read the same attempt_count, all passed the cap, and the 5-attempt limit
// on a 10^6 keyspace could be bypassed by concurrency alone.
func (s *Service) VerifyOTP(ctx context.Context, phoneNumber, otp string) (bool, error) {
	var (
		id       int64
		hashedOTP string
		expiresAt time.Time
	)

	// Atomically claim one attempt from the newest unexpired, unverified
	// request. A zero-row result means: no request, expired, or cap reached —
	// diagnosed below so the caller still gets an accurate error.
	err := s.db.QueryRowContext(ctx, `
		UPDATE otp_requests
		SET attempt_count = attempt_count + 1
		WHERE id = (
			SELECT id FROM otp_requests
			WHERE phone_number = $1 AND verified_at IS NULL AND expires_at > NOW()
			ORDER BY created_at DESC
			LIMIT 1
		)
		AND attempt_count < $2
		RETURNING id, otp_hash, expires_at
	`, phoneNumber, s.maxAttempts).Scan(&id, &hashedOTP, &expiresAt)

	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, s.diagnoseVerifyFailure(ctx, phoneNumber)
		}
		return false, fmt.Errorf("failed to claim OTP attempt: %w", err)
	}

	// The attempt is already spent at this point, by design: a correct code
	// costs one attempt just like an incorrect one, so the budget is a hard
	// cap on total verification work.
	if !crypto.VerifyOTP(hashedOTP, otp) {
		return false, apperrors.ErrInvalidOTP
	}

	// Consume the request atomically so two concurrent submissions of the same
	// correct code cannot both succeed.
	res, err := s.db.ExecContext(ctx, `
		UPDATE otp_requests SET verified_at = NOW() WHERE id = $1 AND verified_at IS NULL
	`, id)
	if err != nil {
		return false, fmt.Errorf("failed to mark OTP verified: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return false, fmt.Errorf("failed to confirm OTP verification: %w", err)
	}
	if affected == 0 {
		// Another request verified this code first.
		return false, apperrors.ErrInvalidOTP
	}

	return true, nil
}

// diagnoseVerifyFailure explains why the atomic claim matched no row. It only
// runs on the failure path, so the extra query costs nothing on success.
func (s *Service) diagnoseVerifyFailure(ctx context.Context, phoneNumber string) error {
	var (
		attemptCount int
		expiresAt    time.Time
	)
	err := s.db.QueryRowContext(ctx, `
		SELECT attempt_count, expires_at FROM otp_requests
		WHERE phone_number = $1 AND verified_at IS NULL
		ORDER BY created_at DESC LIMIT 1
	`, phoneNumber).Scan(&attemptCount, &expiresAt)

	if err != nil {
		// No request at all (or the lookup itself failed): report an invalid
		// code rather than leaking which phone numbers have live requests.
		if !errors.Is(err, sql.ErrNoRows) {
			logger.Log.Error("Failed to diagnose OTP failure", zap.Error(err))
		}
		return apperrors.ErrInvalidOTP
	}

	if attemptCount >= s.maxAttempts {
		return apperrors.ErrOTPMaxAttempts
	}
	if !time.Now().Before(expiresAt) {
		return apperrors.ErrOTPExpired
	}
	return apperrors.ErrInvalidOTP
}

// CleanupExpired removes expired OTP requests (can be run as a cron job)
func (s *Service) CleanupExpired(ctx context.Context) (int64, error) {
	result, err := s.db.ExecContext(ctx, `DELETE FROM otp_requests WHERE expires_at < NOW()`)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}
