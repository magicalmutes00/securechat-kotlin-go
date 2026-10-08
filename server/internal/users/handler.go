package users

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
	service := NewService(repo)
	handler := &Handler{service: service}

	users := api.Group("/users", middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db)))
	users.Get("/me", handler.GetProfile)
	users.Patch("/me", handler.UpdateProfile)
	users.Get("/search", handler.SearchUsers)
}

type UpdateProfileRequest struct {
	DisplayName    string  `json:"display_name" validate:"required,min=1,max=100"`
	Username       string  `json:"username,omitempty" validate:"omitempty,min=3,max=50,alphanum"`
	ProfileImageID *int64  `json:"profile_image_id,omitempty"`
}

type UserResponse struct {
	ID             int64   `json:"id"`
	PhoneNumber    *string `json:"phone_number,omitempty"`
	Email          *string `json:"email,omitempty"`
	Username       *string `json:"username,omitempty"`
	DisplayName    string  `json:"display_name"`
	ProfileImageID *int64  `json:"profile_image_id,omitempty"`
	AvatarURL      *string `json:"avatar_url,omitempty"`
	LastSeen       *int64  `json:"last_seen,omitempty"`
	IsOnline       bool    `json:"is_online"`
	CreatedAt      int64   `json:"created_at"`
	UpdatedAt      int64   `json:"updated_at"`
}

func (h *Handler) GetProfile(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	user, err := h.service.GetProfile(c.Context(), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get profile", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toUserResponse(user),
	})
}

func (h *Handler) UpdateProfile(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	var req UpdateProfileRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	user, err := h.service.UpdateProfileWithAvatar(c.Context(), userID, req.DisplayName, req.Username, req.ProfileImageID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to update profile", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toUserResponse(user),
	})
}

func (h *Handler) SearchUsers(c *fiber.Ctx) error {
	query := c.Query("q")
	limit := c.QueryInt("limit", 20)

	users, err := h.service.SearchUsers(c.Context(), query, limit)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to search users", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	response := make([]UserResponse, len(users))
	for i, u := range users {
		response[i] = toUserResponse(u)
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    response,
	})
}

func toUserResponse(user *User) UserResponse {
	var lastSeen *int64
	if user.LastSeen.Valid {
		ts := user.LastSeen.Time.UnixMilli()
		lastSeen = &ts
	}
	return UserResponse{
		ID:             user.ID,
		PhoneNumber:    nullStringPtr(user.PhoneNumber),
		Email:          nullStringPtr(user.Email),
		Username:       nullStringPtr(user.Username),
		DisplayName:    user.DisplayName,
		ProfileImageID: nullInt64Ptr(user.ProfileImageID),
		AvatarURL:      nullStringPtr(user.AvatarURL),
		LastSeen:       lastSeen,
		IsOnline:       user.IsOnline,
		CreatedAt:      user.CreatedAt.UnixMilli(),
		UpdatedAt:      user.UpdatedAt.UnixMilli(),
	}
}

func nullStringPtr(ns sql.NullString) *string {
	if ns.Valid {
		return &ns.String
	}
	return nil
}

func nullInt64Ptr(ni sql.NullInt64) *int64 {
	if ni.Valid {
		return &ni.Int64
	}
	return nil
}