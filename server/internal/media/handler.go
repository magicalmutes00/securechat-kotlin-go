package media

import (
	"database/sql"
	"errors"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/config"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/middleware"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Handler struct {
	service *Service
}

func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *auth.TokenManager, cfg *config.Config) {
	repo := NewRepository(db)
	service, err := NewService(&cfg.Cloudinary, repo)
	if err != nil {
		logger.Log.Fatal("Failed to initialize media service", zap.Error(err))
	}
	handler := &Handler{service: service}

	authMiddleware := middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db))

	media := api.Group("/media", authMiddleware)
	media.Post("/sign-upload", handler.SignUpload)
	media.Post("/complete", handler.CompleteUpload)
	media.Get("/:id", handler.GetMedia)
	media.Delete("/:id", handler.DeleteMedia)
}

type SignUploadRequest struct {
	ResourceType string `json:"resource_type" validate:"required,oneof=image video audio raw"`
	Filename     string `json:"filename" validate:"required"`
	MimeType     string `json:"mime_type" validate:"required"`
	FileSize     int64  `json:"file_size" validate:"required,min=1"`
}

type MediaResponse struct {
	ID                  int64   `json:"id"`
	CloudinaryPublicID  string  `json:"cloudinary_public_id"`
	ResourceType        string  `json:"resource_type"`
	SecureURL           string  `json:"secure_url"`
	OriginalFilename    string  `json:"original_filename"`
	MimeType            string  `json:"mime_type"`
	FileSize            int64   `json:"file_size"`
	Width               *int    `json:"width,omitempty"`
	Height              *int    `json:"height,omitempty"`
	Duration            *float64 `json:"duration,omitempty"`
	SHA256              string  `json:"sha256"`
	ThumbnailURL        *string `json:"thumbnail_url,omitempty"`
	CreatedAt           int64   `json:"created_at"`
}

func (h *Handler) SignUpload(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	var req SignUploadRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	params, err := h.service.GetSignedUploadParams(c.Context(), userID, req.ResourceType, req.Filename, req.MimeType, req.FileSize)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get signed upload params", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    params,
	})
}

func (h *Handler) CompleteUpload(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	var req CompleteUploadRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	// Create media record
	media, err := h.service.CompleteUpload(c.Context(), userID, &CompleteUploadRequest{
		CloudinaryPublicID: req.CloudinaryPublicID,
		ResourceType:       req.ResourceType,
		SecureURL:          req.SecureURL,
		OriginalFilename:   req.OriginalFilename,
		MimeType:           req.MimeType,
		FileSize:           req.FileSize,
		Width:              req.Width,
		Height:             req.Height,
		Duration:           req.Duration,
		SHA256:             req.SHA256,
	})
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to complete upload", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	// Now create the message with this media
	// This would typically be done by the messages service
	// For now, return the media record
	return c.JSON(fiber.Map{
		"success": true,
		"data":    toMediaResponse(media),
	})
}

func (h *Handler) GetMedia(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	id, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	media, err := h.service.GetMedia(c.Context(), int64(id))
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get media", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	// Verify ownership
	if media.UserID != userID {
		return c.Status(403).JSON(apperrors.NewErrorResponse(apperrors.ErrForbidden))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toMediaResponse(media),
	})
}

func (h *Handler) DeleteMedia(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	id, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	err = h.service.DeleteMedia(c.Context(), int64(id), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to delete media", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": fiber.Map{"message": "Media deleted"},
	})
}

func toMediaResponse(media *MediaRecord) MediaResponse {
	var width *int
	if media.Width.Valid {
		w := int(media.Width.Int64)
		width = &w
	}

	var height *int
	if media.Height.Valid {
		h := int(media.Height.Int64)
		height = &h
	}

	var duration *float64
	if media.Duration.Valid {
		d := media.Duration.Float64
		duration = &d
	}

	var thumbnailURL *string
	if media.ThumbnailURL.Valid {
		thumbnailURL = &media.ThumbnailURL.String
	}

	return MediaResponse{
		ID:                 media.ID,
		CloudinaryPublicID: media.CloudinaryPublicID,
		ResourceType:       media.ResourceType,
		SecureURL:          media.SecureURL,
		OriginalFilename:   media.OriginalFilename,
		MimeType:           media.MimeType,
		FileSize:           media.FileSize,
		Width:              width,
		Height:             height,
		Duration:           duration,
		SHA256:             media.SHA256,
		ThumbnailURL:       thumbnailURL,
		CreatedAt:          media.CreatedAt.UnixMilli(),
	}
}