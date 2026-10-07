package media

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type MediaRecord struct {
	ID                  int64
	UserID              int64
	CloudinaryPublicID  string
	ResourceType        string
	SecureURL           string
	OriginalFilename    string
	MimeType            string
	FileSize            int64
	Width               sql.NullInt64
	Height              sql.NullInt64
	Duration            sql.NullFloat64
	SHA256              string
	ThumbnailURL        sql.NullString
	CreatedAt           time.Time
}

type CompleteUploadRequest struct {
	CloudinaryPublicID string  `json:"cloudinary_public_id" validate:"required"`
	ResourceType       string  `json:"resource_type" validate:"required"`
	SecureURL          string  `json:"secure_url" validate:"required,url"`
	OriginalFilename   string  `json:"original_filename" validate:"required"`
	MimeType           string  `json:"mime_type" validate:"required"`
	FileSize           int64   `json:"file_size" validate:"required,min=1"`
	Width              *int    `json:"width,omitempty"`
	Height             *int    `json:"height,omitempty"`
	Duration           *float64 `json:"duration,omitempty"`
	SHA256             string  `json:"sha256" validate:"required,len=64,hexadecimal"`
}

func (r *Repository) Create(ctx context.Context, media *MediaRecord) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO media (user_id, cloudinary_public_id, resource_type, secure_url, original_filename, mime_type, file_size, width, height, duration, sha256, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, NOW())
		RETURNING id
	`, media.UserID, media.CloudinaryPublicID, media.ResourceType, media.SecureURL, media.OriginalFilename, media.MimeType, media.FileSize, media.Width, media.Height, media.Duration, media.SHA256).Scan(&id)
	if err != nil {
		return 0, fmt.Errorf("failed to create media record: %w", err)
	}

	return id, nil
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*MediaRecord, error) {
	var media MediaRecord
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, cloudinary_public_id, resource_type, secure_url, original_filename, mime_type, file_size, width, height, duration, sha256, thumbnail_url, created_at
		FROM media WHERE id = $1
	`, id).Scan(&media.ID, &media.UserID, &media.CloudinaryPublicID, &media.ResourceType, &media.SecureURL, &media.OriginalFilename, &media.MimeType, &media.FileSize, &media.Width, &media.Height, &media.Duration, &media.SHA256, &media.ThumbnailURL, &media.CreatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrMediaNotFound
		}
		return nil, fmt.Errorf("failed to get media: %w", err)
	}
	return &media, nil
}

func (r *Repository) Delete(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM media WHERE id = $1`, id)
	return err
}

// LinkToMessage attaches an uploaded media row to a freshly created message.
// Ownership is checked so a client cannot attach another user's media.
func (r *Repository) LinkToMessage(ctx context.Context, mediaID, messageID, userID int64) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE media SET message_id = $1 WHERE id = $2 AND user_id = $3
	`, messageID, mediaID, userID)
	if err != nil {
		return fmt.Errorf("failed to link media to message: %w", err)
	}
	if rows, _ := result.RowsAffected(); rows == 0 {
		return apperrors.ErrMediaNotFound
	}
	return nil
}

func (r *Repository) GetByMessageID(ctx context.Context, messageID int64) (*MediaRecord, error) {
	var media MediaRecord
	err := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, cloudinary_public_id, resource_type, secure_url, original_filename, mime_type, file_size, width, height, duration, sha256, thumbnail_url, created_at
		FROM media WHERE message_id = $1
	`, messageID).Scan(&media.ID, &media.UserID, &media.CloudinaryPublicID, &media.ResourceType, &media.SecureURL, &media.OriginalFilename, &media.MimeType, &media.FileSize, &media.Width, &media.Height, &media.Duration, &media.SHA256, &media.ThumbnailURL, &media.CreatedAt)
	if err != nil {
		return nil, err
	}
	return &media, nil
}
