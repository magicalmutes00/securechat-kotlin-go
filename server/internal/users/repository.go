package users

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type User struct {
	ID             int64
	PhoneNumber    sql.NullString // NULL for Google-authenticated users
	Email          sql.NullString
	Username       sql.NullString
	DisplayName    string
	ProfileImageID sql.NullInt64
	AvatarURL      sql.NullString // secure_url from the media row, when set
	LastSeen       sql.NullTime
	IsOnline       bool
	CreatedAt      time.Time
	UpdatedAt      time.Time
}

type UserSettings struct {
	UserID               int64
	Theme                string
	NotificationsEnabled bool
	MediaAutoDownload    bool
	Language             string
	CreatedAt            time.Time
	UpdatedAt            time.Time
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*User, error) {
	var user User
	err := r.db.QueryRowContext(ctx, `
		SELECT u.id, u.phone_number, u.email, u.username, u.display_name, u.profile_image_id,
		       m.secure_url, u.last_seen, u.is_online, u.created_at, u.updated_at
		FROM users u
		LEFT JOIN media m ON m.id = u.profile_image_id
		WHERE u.id = $1
	`, id).Scan(
		&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName,
		&user.ProfileImageID, &user.AvatarURL, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrUserNotFound
		}
		return nil, fmt.Errorf("failed to get user: %w", err)
	}
	return &user, nil
}

func (r *Repository) UpdateProfile(ctx context.Context, id int64, displayName, username string) (*User, error) {
	// Check if username is taken
	if username != "" {
		var existingID int64
		err := r.db.QueryRowContext(ctx, `SELECT id FROM users WHERE username = $1 AND id != $2`, username, id).Scan(&existingID)
		if err == nil {
			return nil, apperrors.ErrUsernameTaken
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return nil, fmt.Errorf("failed to check username: %w", err)
		}
	}

	_, err := r.db.ExecContext(ctx, `
		UPDATE users SET display_name = $1, username = $2, updated_at = NOW() WHERE id = $3
	`, displayName, nullString(username), id)
	if err != nil {
		return nil, fmt.Errorf("failed to update profile: %w", err)
	}

	return r.GetByID(ctx, id)
}

func (r *Repository) UpdateAvatar(ctx context.Context, id int64, imageID int64) (*User, error) {
	_, err := r.db.ExecContext(ctx, `
		UPDATE users SET profile_image_id = $1, updated_at = NOW() WHERE id = $2
	`, imageID, id)
	if err != nil {
		return nil, fmt.Errorf("failed to update avatar: %w", err)
	}

	return r.GetByID(ctx, id)
}

// Search matches username/display_name/phone but never returns phone numbers
// in the result — a user search must not become a phone-number directory.
func (r *Repository) Search(ctx context.Context, query string, limit int) ([]*User, error) {
	// Escape LIKE wildcards so user input cannot match everything.
	escaped := escapeLike(query)
	pattern := "%" + escaped + "%"
	rows, err := r.db.QueryContext(ctx, `
		SELECT u.id, u.phone_number, u.email, u.username, u.display_name, u.profile_image_id,
		       m.secure_url, u.last_seen, u.is_online, u.created_at, u.updated_at
		FROM users u
		LEFT JOIN media m ON m.id = u.profile_image_id
		WHERE u.username ILIKE $1 OR u.display_name ILIKE $2 OR u.phone_number ILIKE $3
		LIMIT $4
	`, pattern, pattern, pattern, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to search users: %w", err)
	}
	defer rows.Close()

	var users []*User
	for rows.Next() {
		var user User
		if err := rows.Scan(&user.ID, &user.PhoneNumber, &user.Email, &user.Username, &user.DisplayName, &user.ProfileImageID, &user.AvatarURL, &user.LastSeen, &user.IsOnline, &user.CreatedAt, &user.UpdatedAt); err != nil {
			return nil, err
		}
		// Never expose other users' phone numbers or emails through search.
		user.PhoneNumber = sql.NullString{}
		user.Email = sql.NullString{}
		users = append(users, &user)
	}
	return users, nil
}

// escapeLike escapes the SQL LIKE wildcards (% and _) and the escape char.
// PostgreSQL's LIKE treats backslash as the default escape character.
func escapeLike(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `%`, `\%`)
	s = strings.ReplaceAll(s, `_`, `\_`)
	return s
}

func (r *Repository) GetSettings(ctx context.Context, userID int64) (*UserSettings, error) {
	var settings UserSettings
	err := r.db.QueryRowContext(ctx, `
		SELECT user_id, theme, notifications_enabled, media_auto_download, language, created_at, updated_at
		FROM user_settings WHERE user_id = $1
	`, userID).Scan(
		&settings.UserID, &settings.Theme, &settings.NotificationsEnabled, &settings.MediaAutoDownload, &settings.Language, &settings.CreatedAt, &settings.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			// Return defaults
			return &UserSettings{
				UserID:               userID,
				Theme:                "system",
				NotificationsEnabled: true,
				MediaAutoDownload:    true,
				Language:             "en",
			}, nil
		}
		return nil, fmt.Errorf("failed to get settings: %w", err)
	}
	return &settings, nil
}

func (r *Repository) UpdateSettings(ctx context.Context, settings *UserSettings) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO user_settings (user_id, theme, notifications_enabled, media_auto_download, language, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, NOW(), NOW())
		ON CONFLICT (user_id) DO UPDATE SET
			theme = EXCLUDED.theme,
			notifications_enabled = EXCLUDED.notifications_enabled,
			media_auto_download = EXCLUDED.media_auto_download,
			language = EXCLUDED.language,
			updated_at = NOW()
	`, settings.UserID, settings.Theme, settings.NotificationsEnabled, settings.MediaAutoDownload, settings.Language)
	return err
}

func nullString(s string) sql.NullString {
	if s != "" {
		return sql.NullString{String: s, Valid: true}
	}
	return sql.NullString{}
}
