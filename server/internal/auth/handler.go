package auth

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/config"
	"github.com/securechat/server/internal/middleware"
	"github.com/securechat/server/internal/otp"
	apperrors "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
	"google.golang.org/api/idtoken"
)

type Handler struct {
	service      *Service
	tokenManager *TokenManager
	cfg          *config.Config
}

func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *TokenManager, cfg *config.Config, otpService *otp.Service) {
	repo := NewRepository(db)
	service := NewService(repo, otpService, tokenManager, cfg)
	handler := &Handler{
		service:      service,
		tokenManager: tokenManager,
		cfg:          cfg,
	}

	auth := api.Group("/auth")
	auth.Post("/send-otp", handler.SendOTP)
	auth.Post("/verify-otp", handler.VerifyOTP)
	auth.Post("/google", handler.GoogleAuth)
	auth.Post("/refresh", handler.RefreshToken)
	// Logout must sit behind the auth middleware: it needs the session ID
	// claim from the access token to know which session to revoke.
	auth.Post("/logout", handler.Logout, middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db)))
}

type SendOTPRequest struct {
	PhoneNumber string `json:"phone_number" validate:"required,e164"`
}

type SendOTPResponse struct {
	ExpiresIn      int `json:"expires_in"`
	ResendCooldown int `json:"resend_cooldown"`
}

// e164Pattern enforces the struct tags' `validate:"required,e164"` contract —
// no validator library is wired, so handlers check input explicitly to keep
// malformed payloads out of the DB/provider path.
var e164Pattern = regexp.MustCompile(`^\+[1-9]\d{1,14}$`)

func (h *Handler) SendOTP(c *fiber.Ctx) error {
	var req SendOTPRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if !e164Pattern.MatchString(req.PhoneNumber) {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	result, err := h.service.SendOTP(c.Context(), req.PhoneNumber)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to send OTP", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    result,
	})
}

type VerifyOTPRequest struct {
	PhoneNumber     string `json:"phone_number" validate:"required,e164"`
	OTP             string `json:"otp" validate:"required,len=6,numeric"`
	DeviceName      string `json:"device_name" validate:"required,min=1,max=100"`
	DeviceIdentifier string `json:"device_identifier" validate:"required,min=1,max=255"`
}

type VerifyOTPResponse struct {
	AccessToken       string `json:"access_token"`
	RefreshToken      string `json:"refresh_token"`
	AccessExpiresIn   int    `json:"access_expires_in"`
	RefreshExpiresIn  int    `json:"refresh_expires_in"`
	User              UserInfo `json:"user"`
}

type UserInfo struct {
	ID              int64   `json:"id"`
	PhoneNumber     *string `json:"phone_number"`
	Email           *string `json:"email"`
	DisplayName     string  `json:"display_name"`
	Username        *string `json:"username,omitempty"`
	ProfileImageID  *int64  `json:"profile_image_id,omitempty"`
}

func (h *Handler) VerifyOTP(c *fiber.Ctx) error {
	var req VerifyOTPRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if !e164Pattern.MatchString(req.PhoneNumber) ||
		len(req.OTP) != 6 ||
		req.DeviceName == "" || len(req.DeviceName) > 100 ||
		req.DeviceIdentifier == "" || len(req.DeviceIdentifier) > 255 {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	result, err := h.service.VerifyOTP(c.Context(), req.PhoneNumber, req.OTP, req.DeviceName, req.DeviceIdentifier)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to verify OTP", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": VerifyOTPResponse{
			AccessToken:      result.AccessToken,
			RefreshToken:     result.RefreshToken,
			AccessExpiresIn:  result.AccessExpiresIn,
			RefreshExpiresIn: result.RefreshExpiresIn,
			User: UserInfo{
				ID:             result.User.ID,
				PhoneNumber:    nullString(result.User.PhoneNumber),
				Email:          nullString(result.User.Email),
				DisplayName:    result.User.DisplayName,
				Username:       nullString(result.User.Username),
				ProfileImageID: nullInt64(result.User.ProfileImageID),
			},
		},
	})
}

type GoogleAuthRequest struct {
	IDToken          string `json:"id_token" validate:"required"`
	DeviceName       string `json:"device_name" validate:"required,min=1,max=100"`
	DeviceIdentifier string `json:"device_identifier" validate:"required,min=1,max=255"`
}

func (h *Handler) GoogleAuth(c *fiber.Ctx) error {
	var req GoogleAuthRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if req.IDToken == "" ||
		req.DeviceName == "" || len(req.DeviceName) > 100 ||
		req.DeviceIdentifier == "" || len(req.DeviceIdentifier) > 255 {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	info, err := h.verifyGoogleToken(c.Context(), req.IDToken)
	if err != nil {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	result, err := h.service.SignInWithGoogle(c.Context(), info, req.DeviceName, req.DeviceIdentifier)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to sign in with Google", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": VerifyOTPResponse{
			AccessToken:      result.AccessToken,
			RefreshToken:     result.RefreshToken,
			AccessExpiresIn:  result.AccessExpiresIn,
			RefreshExpiresIn: result.RefreshExpiresIn,
			User: UserInfo{
				ID:             result.User.ID,
				PhoneNumber:    nullString(result.User.PhoneNumber),
				Email:          nullString(result.User.Email),
				DisplayName:    result.User.DisplayName,
				Username:       nullString(result.User.Username),
				ProfileImageID: nullInt64(result.User.ProfileImageID),
			},
		},
	})
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" validate:"required"`
}

type RefreshResponse struct {
	AccessToken       string `json:"access_token"`
	RefreshToken      string `json:"refresh_token"`
	AccessExpiresIn   int    `json:"access_expires_in"`
	RefreshExpiresIn  int    `json:"refresh_expires_in"`
}

func (h *Handler) RefreshToken(c *fiber.Ctx) error {
	var req RefreshRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	if req.RefreshToken == "" {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	accessToken, refreshToken, err := h.service.RefreshToken(c.Context(), req.RefreshToken)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to refresh token", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": RefreshResponse{
			AccessToken:      accessToken,
			RefreshToken:     refreshToken,
			AccessExpiresIn:  h.cfg.JWT.AccessTTL,
			RefreshExpiresIn: h.cfg.JWT.RefreshTTL,
		},
	})
}

func (h *Handler) Logout(c *fiber.Ctx) error {
	// Get user ID and session ID from context (set by auth middleware)
	userID := c.Locals("user_id")
	sessionID := c.Locals("session_id")

	if userID == nil || sessionID == nil {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	err := h.service.Logout(c.Context(), userID.(int64), sessionID.(int64))
	if err != nil {
		logger.Log.Error("Failed to logout", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": fiber.Map{"message": "Logged out successfully"},
	})
}

func nullString(ns sql.NullString) *string {
	if ns.Valid {
		return &ns.String
	}
	return nil
}

func nullInt64(ni sql.NullInt64) *int64 {
	if ni.Valid {
		return &ni.Int64
	}
	return nil
}

func (h *Handler) verifyGoogleToken(ctx context.Context, idToken string) (GoogleUserInfo, error) {
	if h.cfg.Google.ClientID == "" {
		return GoogleUserInfo{}, fmt.Errorf("google client id not configured")
	}

	validator, err := idtoken.NewValidator(ctx)
	if err != nil {
		return GoogleUserInfo{}, fmt.Errorf("failed to create google token validator: %w", err)
	}

	payload, err := validator.Validate(ctx, idToken, h.cfg.Google.ClientID)
	if err != nil {
		return GoogleUserInfo{}, err
	}

	info := GoogleUserInfo{
		Sub:   payload.Subject,
		Email: payload.Claims["email"].(string),
		EmailVerified: payload.Claims["email_verified"].(bool),
		Name:  payload.Claims["name"].(string),
	}
	if pic, ok := payload.Claims["picture"].(string); ok {
		info.Picture = pic
	}
	return info, nil
}