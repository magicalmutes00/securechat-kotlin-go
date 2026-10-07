package auth

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/crypto"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type User struct {
	ID             int64
	PhoneNumber    sql.NullString
	Email          sql.NullString
	Username       sql.NullString
	DisplayName    string
	ProfileImageID sql.NullInt64
	LastSeen       sql.NullTime
	IsOnline       bool
	CreatedAt      time.Time
	UpdatedAt      time.Time
}

type Device struct {
	ID                int64
	UserID            int64
	DeviceName        string
	DeviceIdentifier  string
	Platform          string
	CreatedAt         time.Time
	LastSeen          sql.NullTime
}

type Session struct {
	ID               int64
	UserID           int64
	DeviceID         int64
	RefreshTokenHash string
	ExpiresAt        time.Time
	RevokedAt        sql.NullTime
	CreatedAt        time.Time
}

func (r *Repository) FindUserByPhone(ctx context.Context, phoneNumber string) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT id, phone_number, email, username, display_name, profile_image_id, last_seen, is_online, created_at, updated_at
		FROM users WHERE phone_number = $1
	`, phoneNumber).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrUserNotFound
		}
		return nil, fmt.Errorf("failed to find user: %w", err)
	}
	return &user, nil
}

func (r *Repository) FindUserByUsername(ctx context.Context, username string) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT id, phone_number, email, username, display_name, profile_image_id, last_seen, is_online, created_at, updated_at
		FROM users WHERE username = $1
	`, username).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrUserNotFound
		}
		return nil, fmt.Errorf("failed to find user: %w", err)
	}
	return &user, nil
}

func (r *Repository) FindUserByGoogleSub(ctx context.Context, googleSub string) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT id, phone_number, email, username, display_name, profile_image_id, last_seen, is_online, created_at, updated_at
		FROM users WHERE google_sub = $1
	`, googleSub).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *Repository) CreateUser(ctx context.Context, phoneNumber, displayName string) (*User, error) {
	// PostgreSQL has no LastInsertId; the new row comes back via RETURNING.
	var id int64
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO users (phone_number, display_name, created_at, updated_at)
		VALUES ($1, $2, NOW(), NOW())
		RETURNING id
	`, phoneNumber, displayName).Scan(&id)
	if err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	return r.GetUserByID(ctx, id)
}

func (r *Repository) CreateGoogleUser(ctx context.Context, googleSub, email, displayName string) (*User, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO users (email, google_sub, display_name, created_at, updated_at)
		VALUES ($1, $2, $3, NOW(), NOW())
		RETURNING id
	`, email, googleSub, displayName).Scan(&id)
	if err != nil {
		return nil, fmt.Errorf("failed to create google user: %w", err)
	}

	return r.GetUserByID(ctx, id)
}

func (r *Repository) CreateDevice(ctx context.Context, userID int64, deviceName, deviceIdentifier, platform string) (*Device, error) {
	// Check if device already exists
	existing, err := r.FindDeviceByUserAndIdentifier(ctx, userID, deviceIdentifier)
	if err == nil {
		// Update existing device
		_, err = r.db.ExecContext(ctx, `
			UPDATE devices SET device_name = $1, last_seen = NOW() WHERE id = $2
		`, deviceName, existing.ID)
		if err != nil {
			return nil, fmt.Errorf("failed to update device: %w", err)
		}
		return r.FindDeviceByID(ctx, existing.ID)
	}

	var id int64
	err = r.db.QueryRowContext(ctx, `
		INSERT INTO devices (user_id, device_name, device_identifier, platform, created_at, last_seen)
		VALUES ($1, $2, $3, $4, NOW(), NOW())
		RETURNING id
	`, userID, deviceName, deviceIdentifier, platform).Scan(&id)
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}

	return r.FindDeviceByID(ctx, id)
}

func (r *Repository) FindDeviceByUserAndIdentifier(ctx context.Context, userID int64, identifier string) (*Device, error) {
	var device Device
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_name, device_identifier, platform, created_at, last_seen
		FROM devices WHERE user_id = $1 AND device_identifier = $2
	`, userID, identifier).Scan(
		&device.ID, &device.UserID, &device.DeviceName, &device.DeviceIdentifier,
		&device.Platform, &device.CreatedAt, &device.LastSeen,
	)
	if err != nil {
		return nil, err
	}
	return &device, nil
}

func (r *Repository) FindDeviceByID(ctx context.Context, id int64) (*Device, error) {
	var device Device
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_name, device_identifier, platform, created_at, last_seen
		FROM devices WHERE id = $1
	`, id).Scan(
		&device.ID, &device.UserID, &device.DeviceName, &device.DeviceIdentifier,
		&device.Platform, &device.CreatedAt, &device.LastSeen,
	)
	if err != nil {
		return nil, err
	}
	return &device, nil
}

func (r *Repository) CreateSession(ctx context.Context, userID, deviceID int64, refreshToken string, expiresAt time.Time) (*Session, error) {
	refreshTokenHash, err := crypto.HashPassword(refreshToken)
	if err != nil {
		return nil, fmt.Errorf("failed to hash refresh token: %w", err)
	}

	var id int64
	err = r.db.QueryRowContext(ctx, `
		INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at, created_at)
		VALUES ($1, $2, $3, $4, NOW())
		RETURNING id
	`, userID, deviceID, refreshTokenHash, expiresAt).Scan(&id)
	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	return r.FindSessionByID(ctx, id)
}

func (r *Repository) FindSessionByID(ctx context.Context, id int64) (*Session, error) {
	var session Session
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_id, refresh_token_hash, expires_at, revoked_at, created_at
		FROM sessions WHERE id = $1
	`, id).Scan(
		&session.ID, &session.UserID, &session.DeviceID, &session.RefreshTokenHash,
		&session.ExpiresAt, &session.RevokedAt, &session.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &session, nil
}

// UpdateSessionRefreshHash rotates a session's refresh token hash in place.
// The session row (and therefore the session_id claim inside the JWTs) stays
// stable across rotations, so access tokens remain bound to the same session.
func (r *Repository) UpdateSessionRefreshHash(ctx context.Context, sessionID int64, refreshToken string, expiresAt time.Time) error {
	refreshTokenHash, err := crypto.HashPassword(refreshToken)
	if err != nil {
		return fmt.Errorf("failed to hash refresh token: %w", err)
	}

	_, err = r.db.ExecContext(ctx, `
		UPDATE sessions SET refresh_token_hash = $1, expires_at = $2 WHERE id = $3
	`, refreshTokenHash, expiresAt, sessionID)
	if err != nil {
		return fmt.Errorf("failed to update session refresh hash: %w", err)
	}
	return nil
}

// IsSessionActive reports whether a session exists, belongs to the user, and
// has not been revoked or expired. Used by the HTTP auth middleware so that
// logout/device revocation takes effect immediately, not at token expiry.
func (r *Repository) IsSessionActive(ctx context.Context, userID, sessionID int64) (bool, error) {
	var isActive bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM sessions
			WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL AND expires_at > NOW()
		)
	`, sessionID, userID).Scan(&isActive)
	return isActive, err
}

func (r *Repository) RevokeSession(ctx context.Context, sessionID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE id = $1
	`, sessionID)
	return err
}

func (r *Repository) RevokeAllUserSessions(ctx context.Context, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1 AND revoked_at IS NULL
	`, userID)
	return err
}

func (r *Repository) RevokeAllOtherSessions(ctx context.Context, userID, currentSessionID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1 AND id != $2 AND revoked_at IS NULL
	`, userID, currentSessionID)
	return err
}

func (r *Repository) GetUserDevices(ctx context.Context, userID int64) ([]*Device, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, user_id, device_name, device_identifier, platform, created_at, last_seen
		FROM devices WHERE user_id = $1 ORDER BY last_seen DESC NULLS LAST
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var devices []*Device
	for rows.Next() {
		var device Device
		if err := rows.Scan(&device.ID, &device.UserID, &device.DeviceName, &device.DeviceIdentifier, &device.Platform, &device.CreatedAt, &device.LastSeen); err != nil {
			return nil, err
		}
		devices = append(devices, &device)
	}
	return devices, nil
}

// FindSessionByRefreshToken returns the session row for a presented refresh
// token so callers can verify the bcrypt hash against it. It matches by
// session ID — bcrypt salts every hash, so the stored hash can never be
// looked up by re-hashing the token.
func (r *Repository) FindSessionByRefreshToken(ctx context.Context, sessionID int64) (*Session, error) {
	var session Session
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_id, refresh_token_hash, expires_at, revoked_at, created_at
		FROM sessions
		WHERE id = $1
	`, sessionID).Scan(
		&session.ID, &session.UserID, &session.DeviceID, &session.RefreshTokenHash,
		&session.ExpiresAt, &session.RevokedAt, &session.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (r *Repository) UpdateUserOnlineStatus(ctx context.Context, userID int64, isOnline bool) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE users SET is_online = $1, last_seen = NOW() WHERE id = $2
	`, isOnline, userID)
	return err
}

func (r *Repository) GetUserByID(ctx context.Context, userID int64) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT id, phone_number, email, username, display_name, profile_image_id, last_seen, is_online, created_at, updated_at
		FROM users WHERE id = $1
	`, userID).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &user, nil
}
