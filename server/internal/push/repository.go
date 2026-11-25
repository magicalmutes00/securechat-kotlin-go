package push

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
)

// Repository stores and looks up FCM push tokens. Tokens are keyed by
// (user_id, device_id, fcm_token) so a user can have several active devices,
// each with its own registration token.
type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// Upsert inserts a new push token or refreshes an existing one. A token is
// uniquely identified by its value, so re-registration after a token refresh
// updates the owning user/device rather than creating a duplicate row.
func (r *Repository) Upsert(ctx context.Context, userID, deviceID int64, fcmToken, platform string) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO push_tokens (user_id, device_id, fcm_token, platform, is_active, created_at, updated_at)
		VALUES ($1, $2, $3, $4, TRUE, NOW(), NOW())
		ON CONFLICT (fcm_token) DO UPDATE
			SET user_id = EXCLUDED.user_id,
				device_id = EXCLUDED.device_id,
				platform = EXCLUDED.platform,
				is_active = TRUE,
				updated_at = NOW()
	`, userID, deviceID, fcmToken, platform)
	if err != nil {
		return fmt.Errorf("failed to upsert push token: %w", err)
	}
	return nil
}

// Deactivate marks a token inactive (soft revocation). Used on logout or when
// the user disables push notifications.
func (r *Repository) Deactivate(ctx context.Context, fcmToken string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE push_tokens SET is_active = FALSE, updated_at = NOW() WHERE fcm_token = $1
	`, fcmToken)
	if err != nil {
		return fmt.Errorf("failed to deactivate push token: %w", err)
	}
	if n, _ := result.RowsAffected(); n == 0 {
		return apperrors.ErrNotFound
	}
	return nil
}

// DeactivateByUser deactivates all active tokens for a user (e.g. account
// deletion or logout-all).
func (r *Repository) DeactivateByUser(ctx context.Context, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE push_tokens SET is_active = FALSE, updated_at = NOW() WHERE user_id = $1 AND is_active = TRUE
	`, userID)
	if err != nil {
		return fmt.Errorf("failed to deactivate push tokens for user: %w", err)
	}
	return nil
}

// ActiveTokensForUser returns all active FCM tokens for a user, one per device.
func (r *Repository) ActiveTokensForUser(ctx context.Context, userID int64) ([]*PushToken, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, user_id, device_id, fcm_token, platform, created_at, updated_at
		FROM push_tokens
		WHERE user_id = $1 AND is_active = TRUE
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to query active push tokens: %w", err)
	}
	defer rows.Close()

	var tokens []*PushToken
	for rows.Next() {
		var t PushToken
		if err := rows.Scan(&t.ID, &t.UserID, &t.DeviceID, &t.FcmToken, &t.Platform, &t.CreatedAt, &t.UpdatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan push token: %w", err)
		}
		tokens = append(tokens, &t)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate push tokens: %w", err)
	}
	return tokens, nil
}

// ActiveTokensForDevices returns active FCM tokens for the given device IDs.
// Used to notify specific recipient devices of a new message.
func (r *Repository) ActiveTokensForDevices(ctx context.Context, deviceIDs []int64) ([]*PushToken, error) {
	if len(deviceIDs) == 0 {
		return nil, nil
	}

	rows, err := r.db.QueryContext(ctx, `
		SELECT id, user_id, device_id, fcm_token, platform, created_at, updated_at
		FROM push_tokens
		WHERE device_id = ANY($1::bigint[]) AND is_active = TRUE
	`, deviceIDs)
	if err != nil {
		return nil, fmt.Errorf("failed to query active push tokens for devices: %w", err)
	}
	defer rows.Close()

	var tokens []*PushToken
	for rows.Next() {
		var t PushToken
		if err := rows.Scan(&t.ID, &t.UserID, &t.DeviceID, &t.FcmToken, &t.Platform, &t.CreatedAt, &t.UpdatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan push token: %w", err)
		}
		tokens = append(tokens, &t)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate push tokens: %w", err)
	}
	return tokens, nil
}

// PushToken is a device's FCM registration token.
type PushToken struct {
	ID        int64
	UserID    int64
	DeviceID  int64
	FcmToken  string
	Platform  string
	IsActive  bool
	CreatedAt time.Time
	UpdatedAt time.Time
}
