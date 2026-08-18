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
		FROM users WHERE phone_number = ?
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
		FROM users WHERE username = ?
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
		FROM users WHERE google_sub = ?
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
	result, err := r.db.ExecContext(ctx, `
		INSERT INTO users (phone_number, display_name, created_at, updated_at)
		VALUES (?, ?, NOW(), NOW())
	`, phoneNumber, displayName)
	if err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	_, _ = result.LastInsertId()
	return r.FindUserByPhone(ctx, phoneNumber)
}

func (r *Repository) CreateGoogleUser(ctx context.Context, googleSub, email, displayName string) (*User, error) {
	result, err := r.db.ExecContext(ctx, `
		INSERT INTO users (email, google_sub, display_name, created_at, updated_at)
		VALUES (?, ?, ?, NOW(), NOW())
	`, email, googleSub, displayName)
	if err != nil {
		return nil, fmt.Errorf("failed to create google user: %w", err)
	}

	id, _ := result.LastInsertId()
	return r.GetUserByID(ctx, id)
}

func (r *Repository) CreateDevice(ctx context.Context, userID int64, deviceName, deviceIdentifier, platform string) (*Device, error) {
	// Check if device already exists
	existing, err := r.FindDeviceByUserAndIdentifier(ctx, userID, deviceIdentifier)
	if err == nil {
		// Update existing device
		_, err = r.db.ExecContext(ctx, `
			UPDATE devices SET device_name = ?, last_seen = NOW() WHERE id = ?
		`, deviceName, existing.ID)
		if err != nil {
			return nil, fmt.Errorf("failed to update device: %w", err)
		}
		return r.FindDeviceByID(ctx, existing.ID)
	}

	result, err := r.db.ExecContext(ctx, `
		INSERT INTO devices (user_id, device_name, device_identifier, platform, created_at, last_seen)
		VALUES (?, ?, ?, ?, NOW(), NOW())
	`, userID, deviceName, deviceIdentifier, platform)
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}

	id, _ := result.LastInsertId()
	return r.FindDeviceByID(ctx, id)
}

func (r *Repository) FindDeviceByUserAndIdentifier(ctx context.Context, userID int64, identifier string) (*Device, error) {
	var device Device
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_name, device_identifier, platform, created_at, last_seen
		FROM devices WHERE user_id = ? AND device_identifier = ?
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
		FROM devices WHERE id = ?
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

	result, err := r.db.ExecContext(ctx, `
		INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at, created_at)
		VALUES (?, ?, ?, ?, NOW())
	`, userID, deviceID, refreshTokenHash, expiresAt)
	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	id, _ := result.LastInsertId()
	return r.FindSessionByID(ctx, id)
}

func (r *Repository) FindSessionByID(ctx context.Context, id int64) (*Session, error) {
	var session Session
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_id, refresh_token_hash, expires_at, revoked_at, created_at
		FROM sessions WHERE id = ?
	`, id).Scan(
		&session.ID, &session.UserID, &session.DeviceID, &session.RefreshTokenHash,
		&session.ExpiresAt, &session.RevokedAt, &session.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (r *Repository) FindActiveSessionByRefreshToken(ctx context.Context, refreshTokenHash string) (*Session, error) {
	var session Session
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_id, refresh_token_hash, expires_at, revoked_at, created_at
		FROM sessions 
		WHERE refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > NOW()
	`, refreshTokenHash).Scan(
		&session.ID, &session.UserID, &session.DeviceID, &session.RefreshTokenHash,
		&session.ExpiresAt, &session.RevokedAt, &session.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (r *Repository) RevokeSession(ctx context.Context, sessionID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE id = ?
	`, sessionID)
	return err
}

func (r *Repository) RevokeAllUserSessions(ctx context.Context, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL
	`, userID)
	return err
}

func (r *Repository) RevokeAllOtherSessions(ctx context.Context, userID, currentSessionID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = NOW() WHERE user_id = ? AND id != ? AND revoked_at IS NULL
	`, userID, currentSessionID)
	return err
}

func (r *Repository) GetUserDevices(ctx context.Context, userID int64) ([]*Device, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, user_id, device_name, device_identifier, platform, created_at, last_seen
		FROM devices WHERE user_id = ? ORDER BY last_seen DESC
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

func (r *Repository) RevokeDevice(ctx context.Context, deviceID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE devices SET last_seen = NOW() WHERE id = ?
	`, deviceID)
	return err
}

func (r *Repository) FindSessionByRefreshToken(ctx context.Context, refreshToken string) (*Session, error) {
	refreshTokenHash, err := crypto.HashPassword(refreshToken)
	if err != nil {
		return nil, fmt.Errorf("failed to hash refresh token: %w", err)
	}

	var session Session
	err = r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_id, refresh_token_hash, expires_at, revoked_at, created_at
		FROM sessions 
		WHERE refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > NOW()
	`, refreshTokenHash).Scan(
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
		UPDATE users SET is_online = ?, last_seen = NOW() WHERE id = ?
	`, isOnline, userID)
	return err
}

func (r *Repository) GetUserByID(ctx context.Context, userID int64) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT id, phone_number, email, username, display_name, profile_image_id, last_seen, is_online, created_at, updated_at
		FROM users WHERE id = ?
	`, userID).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &user, nil
}