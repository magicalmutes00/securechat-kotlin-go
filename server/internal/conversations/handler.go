package conversations

import (
	"database/sql"
	"errors"
	"strings"

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
	conversations.Patch("/:id", handler.RenameConversation)
	conversations.Post("/:id/members", handler.AddMembers)
	conversations.Delete("/:id/members/:user_id", handler.RemoveMember)
	conversations.Delete("/:id", handler.DeleteConversation)
}

// A conversation can be started either from a phone number (legacy, e164) or
// from a user id. User id is preferred by clients that found the person through
// search, since search deliberately never exposes phone numbers.
//
// With type "group" it instead creates a named group from participant_ids; the
// caller is added automatically and must not be listed.
type CreateConversationRequest struct {
	ParticipantPhone string  `json:"participant_phone"`
	ParticipantID    int64   `json:"participant_id"`
	Type             string  `json:"type"`
	Name             string  `json:"name"`
	ParticipantIDs   []int64 `json:"participant_ids"`
}

type ConversationResponse struct {
	ID             int64                   `json:"id"`
	Type           string                  `json:"type"`
	Name           *string                 `json:"name,omitempty"`
	Participants   []ParticipantResponse   `json:"participants"`
	LastMessage    *MessagePreviewResponse `json:"last_message,omitempty"`
	UnreadCount    int                     `json:"unread_count"`
	CreatedAt      int64                   `json:"created_at"`
	UpdatedAt      int64                   `json:"updated_at"`
}

type ParticipantResponse struct {
	UserID         int64   `json:"user_id"`
	DisplayName    string  `json:"display_name"`
	Username       *string `json:"username,omitempty"`
	ProfileImageID *int64  `json:"profile_image_id,omitempty"`
	IsOnline       bool    `json:"is_online"`
	// LastSeen is omitted when the column is NULL, so a client can distinguish
	// "never seen" from "seen at the epoch".
	LastSeen *int64 `json:"last_seen,omitempty"`
}

type MessagePreviewResponse struct {
	ID         int64   `json:"id"`
	SenderID   int64   `json:"sender_id"`
	SenderName *string `json:"sender_name,omitempty"`
	Type       string  `json:"type"`
	Text       *string `json:"text,omitempty"`
	MediaID    *int64  `json:"media_id,omitempty"`
	Status     string  `json:"status"`
	CreatedAt  int64   `json:"created_at"`
}

func (h *Handler) GetConversations(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
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
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}

	var req CreateConversationRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	// Group creation is a different flow from 1:1: a name plus a member list
	// rather than exactly one other participant.
	if strings.EqualFold(strings.TrimSpace(req.Type), "group") {
		conversation, err := h.service.CreateGroupConversation(c.Context(), userID, req.Name, req.ParticipantIDs)
		if err != nil {
			var appErr *apperrors.AppError
			if errors.As(err, &appErr) {
				return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
			}
			logger.Log.Error("Failed to create group", zap.Error(err))
			return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
		}
		return c.JSON(fiber.Map{
			"success": true,
			"data":    toConversationResponse(conversation),
		})
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
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
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

// RenameConversationRequest is the body of PATCH /conversations/:id.
type RenameConversationRequest struct {
	Name string `json:"name"`
}

func (h *Handler) RenameConversation(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	conversationID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	var req RenameConversationRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	if err := h.service.RenameConversation(c.Context(), int64(conversationID), userID, req.Name); err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to rename conversation", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	// Return the updated conversation so the caller can refresh its cache in one
	// round trip instead of issuing a follow-up GET.
	conversation, err := h.service.GetConversation(c.Context(), int64(conversationID), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to read renamed conversation", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toConversationResponse(conversation),
	})
}

// AddMembersRequest is the body of POST /conversations/:id/members.
type AddMembersRequest struct {
	ParticipantIDs []int64 `json:"participant_ids"`
}

// AddMembers adds users to a group and returns the updated conversation.
func (h *Handler) AddMembers(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	conversationID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	var req AddMembersRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	conversation, err := h.service.AddMembers(c.Context(), int64(conversationID), userID, req.ParticipantIDs)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to add members", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toConversationResponse(conversation),
	})
}

// RemoveMember takes one member out of a group and returns the updated
// conversation.
func (h *Handler) RemoveMember(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	conversationID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}
	targetUserID, err := c.ParamsInt("user_id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	conversation, err := h.service.RemoveMember(c.Context(), int64(conversationID), userID, int64(targetUserID))
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to remove member", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data":    toConversationResponse(conversation),
	})
}

func (h *Handler) DeleteConversation(c *fiber.Ctx) error {
	userID, ok := middleware.UserID(c)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
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
			LastSeen:       nullTimePtr(p.User.LastSeen),
		}
	}

	var lastMsg *MessagePreviewResponse
	if conv.LastMessage != nil {
		lastMsg = &MessagePreviewResponse{
			ID:        conv.LastMessage.ID,
			SenderID:   conv.LastMessage.SenderID,
			SenderName: nullStringPtr(conv.LastMessage.SenderName),
			Type:       conv.LastMessage.Type,
			Text:       nullStringPtr(conv.LastMessage.Text),
			MediaID:    nullInt64Ptr(conv.LastMessage.MediaID),
			Status:     conv.LastMessage.Status,
			CreatedAt:  conv.LastMessage.CreatedAt.UnixMilli(),
		}
	}

	return ConversationResponse{
		ID:             conv.ID,
		Type:           conv.Type,
		Name:           nullStringPtr(conv.Name),
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

// nullTimePtr renders a nullable timestamp as Unix millis, or nil when the
// column is NULL — matching the client's `last_seen: Long?`.
func nullTimePtr(nt sql.NullTime) *int64 {
	if nt.Valid {
		ts := nt.Time.UnixMilli()
		return &ts
	}
	return nil
}