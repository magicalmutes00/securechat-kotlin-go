package devices

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/middleware"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type Device struct {
	ID               int64
	UserID           int64
	DeviceName       string
	DeviceIdentifier string
	Platform         string
	CreatedAt        time.Time
	LastSeen         sql.NullTime
}

func (r *Repository) GetByUserID(ctx context.Context, userID int64) ([]*Device, error) {
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
		var d Device
		if err := rows.Scan(&d.ID, &d.UserID, &d.DeviceName, &d.DeviceIdentifier, &d.Platform, &d.CreatedAt, &d.LastSeen); err != nil {
			return nil, err
		}
		devices = append(devices, &d)
	}
	return devices, nil
}

func (r *Repository) Revoke(ctx context.Context, deviceID, userID int64) error {
	// Verify ownership
	var ownerID int64
	err := r.db.QueryRowContext(ctx, `SELECT user_id FROM devices WHERE id = $1`, deviceID).Scan(&ownerID)
	if err != nil {
		return err
	}
	if ownerID != userID {
		return apperrors.ErrForbidden
	}

	_, err = r.db.ExecContext(ctx, `DELETE FROM devices WHERE id = $1`, deviceID)
	return err
}

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetDevices(ctx context.Context, userID int64) ([]*Device, error) {
	return s.repo.GetByUserID(ctx, userID)
}

func (s *Service) RevokeDevice(ctx context.Context, deviceID, userID int64) error {
	return s.repo.Revoke(ctx, deviceID, userID)
}

type Handler struct {
	service *Service
}

func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *auth.TokenManager) {
	repo := NewRepository(db)
	service := NewService(repo)
	handler := &Handler{service: service}

	authMiddleware := middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db))

	devices := api.Group("/devices", authMiddleware)
	devices.Get("", handler.GetDevices)
	devices.Delete("/:id", handler.DeleteDevice)
}

type DeviceResponse struct {
	ID               int64   `json:"id"`
	DeviceName       string  `json:"device_name"`
	DeviceIdentifier string  `json:"device_identifier"`
	Platform         string  `json:"platform"`
	CreatedAt        int64   `json:"created_at"`
	LastSeen         *int64  `json:"last_seen,omitempty"`
}

func (h *Handler) GetDevices(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	devices, err := h.service.GetDevices(c.Context(), userID)
	if err != nil {
		logger.Log.Error("Failed to get devices", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	response := make([]DeviceResponse, len(devices))
	for i, d := range devices {
		var lastSeen *int64
		if d.LastSeen.Valid {
			ts := d.LastSeen.Time.Unix()
			lastSeen = &ts
		}
		response[i] = DeviceResponse{
			ID:               d.ID,
			DeviceName:       d.DeviceName,
			DeviceIdentifier: d.DeviceIdentifier,
			Platform:         d.Platform,
			CreatedAt:        d.CreatedAt.Unix(),
			LastSeen:         lastSeen,
		}
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    response,
	})
}

func (h *Handler) DeleteDevice(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	deviceID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	err = h.service.RevokeDevice(c.Context(), int64(deviceID), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to revoke device", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": fiber.Map{"message": "Device revoked"},
	})
}
