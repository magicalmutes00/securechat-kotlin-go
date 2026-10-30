package keys

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

// RegisterRoutes mounts the prekey directory under /keys. Everything here
// requires an authenticated session, and the device a key belongs to is taken
// from the access token rather than from the request body, so a client can only
// ever publish keys for the device it is actually logged in on.
func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *auth.TokenManager) {
	repo := NewRepository(db)
	service := NewService(repo)
	handler := &Handler{service: service}

	keys := api.Group("/keys", middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db)))

	keys.Put("/identity", handler.PublishIdentityKey)
	keys.Put("/signed-prekey", handler.PublishSignedPreKey)
	keys.Put("/kyber-prekey", handler.PublishKyberPreKey)
	keys.Post("/one-time-prekeys", handler.AddOneTimePreKeys)
	keys.Get("/count", handler.CountOneTimePreKeys)
	keys.Post("/claim", handler.ClaimPreKeyBundles)
}

func (h *Handler) PublishIdentityKey(c *fiber.Ctx) error {
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req IdentityKeyRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.PublishIdentityKey(c.Context(), deviceID, userID, &req); err != nil {
		return respondError(c, err, "failed to publish identity key")
	}

	return c.JSON(fiber.Map{"success": true, "data": fiber.Map{"created": true}})
}

func (h *Handler) PublishSignedPreKey(c *fiber.Ctx) error {
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req SignedPreKeyRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.PublishSignedPreKey(c.Context(), deviceID, &req); err != nil {
		return respondError(c, err, "failed to publish signed prekey")
	}

	return c.JSON(fiber.Map{"success": true, "data": fiber.Map{"created": true}})
}

func (h *Handler) PublishKyberPreKey(c *fiber.Ctx) error {
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req PublishKyberPreKeyRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.PublishKyberPreKey(c.Context(), deviceID, &req); err != nil {
		return respondError(c, err, "failed to publish kyber prekey")
	}

	return c.JSON(fiber.Map{"success": true, "data": fiber.Map{"created": true}})
}

func (h *Handler) AddOneTimePreKeys(c *fiber.Ctx) error {
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req OneTimePreKeysRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	stored, err := h.service.AddOneTimePreKeys(c.Context(), deviceID, &req)
	if err != nil {
		return respondError(c, err, "failed to store one-time prekeys")
	}

	return c.JSON(fiber.Map{"success": true, "data": fiber.Map{"stored": stored}})
}

func (h *Handler) CountOneTimePreKeys(c *fiber.Ctx) error {
	deviceID, ok := middleware.DeviceID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	count, err := h.service.CountUnclaimedOneTimePreKeys(c.Context(), deviceID)
	if err != nil {
		return respondError(c, err, "failed to count one-time prekeys")
	}

	return c.JSON(fiber.Map{"success": true, "data": fiber.Map{"unclaimed": count}})
}

func (h *Handler) ClaimPreKeyBundles(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req ClaimRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	response, err := h.service.ClaimPreKeyBundles(c.Context(), userID, &req)
	if err != nil {
		return respondError(c, err, "failed to claim prekey bundles")
	}

	return c.JSON(fiber.Map{"success": true, "data": response})
}

// respondError maps a validation error to 400, an app error to its own status,
// and everything else to a logged 500.
func respondError(c *fiber.Ctx, err error, context string) error {
	var appErr *apperrors.AppError
	if errors.As(err, &appErr) {
		return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
	}
	logger.Log.Error(context, zap.Error(err))
	return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
}
