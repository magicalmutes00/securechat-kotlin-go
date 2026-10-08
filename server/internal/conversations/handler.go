package conversations

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

func RegisterRoutes(api fiber.Router, db *sql.DB, tokenManager *auth.TokenManager) {
	repo := NewRepository(db)
	service := NewService(repo)
	handler := &Handler{service: service}

	conversations := api.Group("/conversations", middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db)))
	conversations.Get("", handler.GetConversations)
	conversations.Post("", handler.CreateConversation)
	conversations.Get("/:id", handler.GetConversation)
	conversations.Delete("/:id", handler.DeleteConversation)
}

// A conversation can be started either from a phone number (legacy, e164) or
// from a user id. User id is preferred by clients that found the person through
// search, since search deliberately never exposes phone numbers.
type CreateConversationRequest struct {
	ParticipantPhone string `json:"participant_phone"`
	ParticipantID    int64  `json:"participant_id"`
}

type ConversationResponse struct {
	ID             int64                  `json:"id"`
	Type           string                 `json:"type"`
	Participants   []ParticipantResponse  `json:"participants"`
	LastMessage    *MessagePreviewResponse `json:"last_message,omitempty"`
	UnreadCount    int                    `json:"unread_count"`
	CreatedAt      int64                  `json:"created_at"`
	UpdatedAt      int64                  `json:"updated_at"`
}

type ParticipantResponse struct {
	UserID         int64   `json:"user_id"`
	DisplayName    string  `json:"display_name"`
	Username       *string `json:"username,omitempty"`
	ProfileImageID *int64  `json:"profile_image_id,omitempty"`
	IsOnline       bool    `json:"is_online"`
}

type MessagePreviewResponse struct {
	ID        int64   `json:"id"`
	SenderID  int64   `json:"sender_id"`
	Type      string  `json:"type"`
	Text      *string `json:"text,omitempty"`
	MediaID   *int64  `json:"media_id,omitempty"`
	Status    string  `json:"status"`
	CreatedAt int64   `json:"created_at"`
}

func (h *Handler) GetConversations(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	limit := c.QueryInt("limit", 20)
	offset := c.QueryInt("offset", 0)

	conversations, err := h.service.GetConversations(c.Context(), userID, limit, offset)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get conversations", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	response := make([]ConversationResponse, len(conversations))
	for i, conv := range conversations {
		response[i] = toConversationResponse(conv)
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    response,
	})
}

func (h *Handler) CreateConversation(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)

	var req CreateConversationRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if req.ParticipantID == 0 && req.ParticipantPhone == "" {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	conversation, err := h.service.CreateDirectConversation(c.Context(), userID, req.ParticipantPhone, req.ParticipantID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to create conversation", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toConversationResponse(conversation),
	})
}

func (h *Handler) GetConversation(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	conversationID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	conversation, err := h.service.GetConversation(c.Context(), int64(conversationID), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get conversation", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toConversationResponse(conversation),
	})
}

func (h *Handler) DeleteConversation(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	conversationID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	err = h.service.LeaveConversation(c.Context(), int64(conversationID), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to leave conversation", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": fiber.Map{"message": "Left conversation"},
	})
}

func toConversationResponse(conv *ConversationWithParticipants) ConversationResponse {
	participants := make([]ParticipantResponse, len(conv.Participants))
	for i, p := range conv.Participants {
		participants[i] = ParticipantResponse{
			UserID:         p.UserID,
			DisplayName:    p.User.DisplayName,
			Username:       nullStringPtr(p.User.Username),
			ProfileImageID: nullInt64Ptr(p.User.ProfileImageID),
			IsOnline:       p.User.IsOnline,
		}
	}

	var lastMsg *MessagePreviewResponse
	if conv.LastMessage != nil {
		lastMsg = &MessagePreviewResponse{
			ID:        conv.LastMessage.ID,
			SenderID:  conv.LastMessage.SenderID,
			Type:      conv.LastMessage.Type,
			Text:      nullStringPtr(conv.LastMessage.Text),
			MediaID:   nullInt64Ptr(conv.LastMessage.MediaID),
			Status:    conv.LastMessage.Status,
			CreatedAt: conv.LastMessage.CreatedAt.UnixMilli(),
		}
	}

	return ConversationResponse{
		ID:             conv.ID,
		Type:           conv.Type,
		Participants:   participants,
		LastMessage:    lastMsg,
		UnreadCount:    conv.UnreadCount,
		CreatedAt:      conv.CreatedAt.UnixMilli(),
		UpdatedAt:      conv.UpdatedAt.UnixMilli(),
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