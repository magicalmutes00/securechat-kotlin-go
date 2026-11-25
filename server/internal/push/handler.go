package push

import (
	"database/sql"
	"errors"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/middleware"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Handler struct {
	service *Service
}

func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *auth.TokenManager, service *Service) {
	handler := &Handler{
		service: service,
	}

	push := api.Group("/push", middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db)))
	push.Post("/register", handler.RegisterToken)
	push.Delete("/token", handler.UnregisterToken)
}

// RegisterTokenRequest is the payload for POST /push/register.
type RegisterTokenRequest struct {
	FcmToken string `json:"fcm_token" validate:"required,min=10"`
	Platform string `json:"platform" validate:"required,min=1,max=32"`
}

// RegisterTokenResponse is the response for POST /push/register.
type RegisterTokenResponse struct {
	Registered bool `json:"registered"`
}

// RegisterToken associates the client's FCM token with the authenticated
// user's device. The device id comes from the session (JWT claim), so a client
// can only register tokens for its own device.
func (h *Handler) RegisterToken(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req RegisterTokenRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if req.FcmToken == "" || req.Platform == "" {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.RegisterToken(c.Context(), userID, deviceID, req.FcmToken, req.Platform); err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to register push token",
			zap.Int64("user_id", userID), zap.Int64("device_id", deviceID), zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    RegisterTokenResponse{Registered: true},
	})
}

// UnregisterTokenRequest is the payload for DELETE /push/token.
type UnregisterTokenRequest struct {
	FcmToken string `json:"fcm_token" validate:"required"`
}

// UnregisterToken deactivates an FCM token, typically on logout or when the
// user disables notifications.
func (h *Handler) UnregisterToken(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req UnregisterTokenRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if req.FcmToken == "" {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.UnregisterToken(c.Context(), req.FcmToken); err != nil {
		if errors.Is(err, apperrors.ErrNotFound) {
			return c.Status(200).JSON(fiber.Map{
				"success": true,
				"data":    fiber.Map{"message": "Token not found, already inactive"},
			})
		}
		logger.Log.Error("Failed to unregister push token",
			zap.Int64("user_id", userID), zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    fiber.Map{"message": "Push token unregistered"},
	})
}
