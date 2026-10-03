package media

import (
	"context"
	"crypto/rand"
	"crypto/sha1"
	"database/sql"
	"encoding/hex"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/cloudinary/cloudinary-go/v2"
	"github.com/cloudinary/cloudinary-go/v2/api/uploader"
	"github.com/securechat/server/config"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Service struct {
	cld         *cloudinary.Cloudinary
	cfg         *config.CloudinaryConfig
	repo        *Repository
}

type SignedUploadParams struct {
	UploadURL      string   `json:"upload_url"`
	PublicID       string   `json:"public_id"`
	Signature      string   `json:"signature"`
	Timestamp      int64    `json:"timestamp"`
	APIKey         string   `json:"api_key"`
	Folder         string   `json:"folder"`
	ResourceType   string   `json:"resource_type"`
	AllowedFormats []string `json:"allowed_formats"`
	MaxFileSize    int64    `json:"max_file_size"`
	Transformation string   `json:"transformation,omitempty"`
}

func NewService(cfg *config.CloudinaryConfig, repo *Repository) (*Service, error) {
	cld, err := cloudinary.NewFromParams(cfg.CloudName, cfg.APIKey, cfg.APISecret)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize Cloudinary: %w", err)
	}

	return &Service{
		cld:  cld,
		cfg:  cfg,
		repo: repo,
	}, nil
}

func (s *Service) GetSignedUploadParams(ctx context.Context, userID int64, resourceType, filename, mimeType string, fileSize int64) (*SignedUploadParams, error) {
	// Validate file size
	maxSize := s.getMaxFileSize(resourceType)
	if fileSize > maxSize {
		return nil, apperrors.ErrMediaTooLarge
	}

	// Validate resource type
	if !s.isValidResourceType(resourceType) {
		return nil, apperrors.ErrMediaInvalidType
	}

	// Generate public ID
	publicID := fmt.Sprintf("%s/users/%d/%s", s.cfg.UploadFolder, userID, generateUniqueID())

	// Generate timestamp
	timestamp := time.Now().Unix()

	// Build upload parameters. Keep values as plain strings: the signature is
	// computed over exactly these key=value pairs, so the client must send
	// them verbatim. Note resource_type is NOT part of a Cloudinary signature.
	folder := fmt.Sprintf("%s/users/%d", s.cfg.UploadFolder, userID)
	params := map[string]string{
		"public_id":       publicID,
		"timestamp":       fmt.Sprintf("%d", timestamp),
		"folder":          folder,
		"overwrite":       "false",
		"unique_filename": "true",
		"use_filename":    "false",
	}

	// Add eager transformation for thumbnails (single plain string per
	// Cloudinary's parameter serialization).
	var transformation string
	if resourceType == "image" {
		transformation = "c_fill,w_200,h_200,q_auto,f_auto"
		params["eager"] = transformation
		params["eager_async"] = "true"
	} else if resourceType == "video" {
		transformation = "so_1,c_fill,w_320,h_180,q_auto,f_auto"
		params["eager"] = transformation
		params["eager_async"] = "true"
	}

	// Generate signature
	signature := s.generateSignature(params)

	// Determine allowed formats
	allowedFormats := s.getAllowedFormats(resourceType)

	// Build upload URL
	uploadURL := fmt.Sprintf("https://api.cloudinary.com/v1_1/%s/%s/upload", s.cfg.CloudName, resourceType)

	return &SignedUploadParams{
		UploadURL:      uploadURL,
		PublicID:       publicID,
		Signature:      signature,
		Timestamp:      timestamp,
		APIKey:         s.cfg.APIKey,
		Folder:         folder,
		ResourceType:   resourceType,
		AllowedFormats: allowedFormats,
		MaxFileSize:    maxSize,
		Transformation: transformation,
	}, nil
}

func (s *Service) CompleteUpload(ctx context.Context, userID int64, req *CompleteUploadRequest) (*MediaRecord, error) {
	// The public_id must live inside the caller's signed folder prefix — only
	// the backend issues signatures scoped to that folder, so this proves the
	// caller uploaded an asset the backend authorized.
	userPrefix := fmt.Sprintf("%s/users/%d/", s.cfg.UploadFolder, userID)
	if !strings.HasPrefix(req.CloudinaryPublicID, userPrefix) {
		return nil, apperrors.ErrForbidden
	}

	if !s.isValidResourceType(req.ResourceType) {
		return nil, apperrors.ErrMediaInvalidType
	}
	if req.FileSize > s.getMaxFileSize(req.ResourceType) {
		return nil, apperrors.ErrMediaTooLarge
	}

	// Verify the upload by checking with Cloudinary
	uploadResult, err := s.cld.Upload.Explicit(ctx, uploader.ExplicitParams{
		PublicID:     req.CloudinaryPublicID,
		ResourceType: req.ResourceType,
		Type:         "upload",
	})
	if err != nil {
		logger.Log.Error("Cloudinary asset verification failed", zap.Error(err))
		return nil, apperrors.ErrCloudinaryError
	}

	// The asset exists server-side: derive the URL from Cloudinary's response
	// rather than trusting the client-supplied one.
	secureURL := req.SecureURL
	if uploadResult != nil && uploadResult.SecureURL != "" {
		secureURL = uploadResult.SecureURL
	}

	// Create media record
	media := &MediaRecord{
		UserID:             userID,
		CloudinaryPublicID: req.CloudinaryPublicID,
		ResourceType:       req.ResourceType,
		SecureURL:          secureURL,
		OriginalFilename:   req.OriginalFilename,
		MimeType:           req.MimeType,
		FileSize:           req.FileSize,
		Width:              toNullInt64(req.Width),
		Height:             toNullInt64(req.Height),
		Duration:           toNullFloat64(req.Duration),
		SHA256:             req.SHA256,
	}

	// Save to database
	id, err := s.repo.Create(ctx, media)
	if err != nil {
		return nil, fmt.Errorf("failed to save media record: %w", err)
	}
	media.ID = id

	return media, nil
}

func (s *Service) GetMedia(ctx context.Context, id int64) (*MediaRecord, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *Service) DeleteMedia(ctx context.Context, id int64, userID int64) error {
	media, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}

	if media.UserID != userID {
		return apperrors.ErrForbidden
	}

	// Delete from Cloudinary
	_, err = s.cld.Upload.Destroy(ctx, uploader.DestroyParams{
		PublicID:     media.CloudinaryPublicID,
		ResourceType: media.ResourceType,
	})
	if err != nil {
		logger.Log.Error("Failed to delete from Cloudinary", zap.Error(err))
		// Continue with DB deletion even if Cloudinary fails
	}

	return s.repo.Delete(ctx, id)
}

func (s *Service) getMaxFileSize(resourceType string) int64 {
	switch resourceType {
	case "image":
		return int64(s.cfg.MaxImageMB) * 1024 * 1024
	case "video":
		return int64(s.cfg.MaxVideoMB) * 1024 * 1024
	case "audio":
		return int64(s.cfg.MaxAudioMB) * 1024 * 1024
	case "raw":
		return int64(s.cfg.MaxDocumentMB) * 1024 * 1024
	default:
		return 20 * 1024 * 1024 // 20MB default
	}
}

func (s *Service) isValidResourceType(resourceType string) bool {
	validTypes := []string{"image", "video", "audio", "raw"}
	for _, t := range validTypes {
		if t == resourceType {
			return true
		}
	}
	return false
}

func (s *Service) getAllowedFormats(resourceType string) []string {
	switch resourceType {
	case "image":
		return []string{"jpg", "jpeg", "png", "gif", "webp", "heic"}
	case "video":
		return []string{"mp4", "mov", "webm", "mkv"}
	case "audio":
		return []string{"mp3", "wav", "ogg", "m4a", "aac"}
	case "raw":
		return []string{"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar"}
	default:
		return []string{}
	}
}

// generateSignature computes a Cloudinary upload signature: SHA-1 hex digest
// of the sorted "key=value" pairs joined with "&", with the API secret
// appended. (Cloudinary's spec: sha1 of `to_sign + api_secret`.)
func (s *Service) generateSignature(params map[string]string) string {
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var parts []string
	for _, k := range keys {
		parts = append(parts, k+"="+params[k])
	}

	toSign := strings.Join(parts, "&") + s.cfg.APISecret
	h := sha1.Sum([]byte(toSign))
	return hex.EncodeToString(h[:])
}

func generateUniqueID() string {
	return fmt.Sprintf("%d_%s", time.Now().Unix(), randomString(16))
}

func randomString(n int) string {
	const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		// crypto/rand failure is unrecoverable in practice; panic is avoided
		// by falling back to a lower-entropy ID rather than guessing.
		return fmt.Sprintf("%x", time.Now().UnixNano())
	}
	for i := range b {
		b[i] = charset[b[i]%byte(len(charset))]
	}
	return string(b)
}

func toNullInt64(i *int) sql.NullInt64 {
	if i != nil {
		return sql.NullInt64{Int64: int64(*i), Valid: true}
	}
	return sql.NullInt64{}
}

func toNullFloat64(f *float64) sql.NullFloat64 {
	if f != nil {
		return sql.NullFloat64{Float64: *f, Valid: true}
	}
	return sql.NullFloat64{}
}