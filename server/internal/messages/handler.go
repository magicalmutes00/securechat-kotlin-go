package messages

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

	authMiddleware := middleware.JWTAuth(tokenManager, middleware.NewDBSessionChecker(db))

	messages := api.Group("/conversations/:conversation_id/messages", authMiddleware)
	messages.Get("", handler.GetMessages)
	
	message := api.Group("/messages", authMiddleware)
	message.Delete("/:id", handler.DeleteMessage)
}

type MessageResponse struct {
	ID             int64   `json:"id"`
	ConversationID int64   `json:"conversation_id"`
	SenderID       int64   `json:"sender_id"`
	SenderName     *string `json:"sender_name,omitempty"`
	Type           string  `json:"type"`
	Text           *string `json:"text,omitempty"`
	MediaID        *int64  `json:"media_id,omitempty"`
	// Media is the attachment itself, not just its id. Clients render a bubble
	// from this payload alone; they cannot fetch someone else's media row.
	Media          *MediaAttachment `json:"media,omitempty"`
	ReplyToID      *int64           `json:"reply_to_id,omitempty"`
	Status         string           `json:"status"`
	CreatedAt      int64            `json:"created_at"`
	UpdatedAt      int64            `json:"updated_at"`
	DeliveredAt    *int64           `json:"delivered_at,omitempty"`
	ReadAt         *int64           `json:"read_at,omitempty"`
}

type MessagePageResponse struct {
	Messages   []MessageResponse `json:"messages"`
	NextCursor *string           `json:"next_cursor,omitempty"`
	HasMore    bool              `json:"has_more"`
}

func (h *Handler) GetMessages(c *fiber.Ctx) error {
	userID, ok := c.Locals("user_id").(int64)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	conversationID, err := c.ParamsInt("conversation_id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	limit := c.QueryInt("limit", 30)

	// The cursor is opaque to clients: they echo back whatever next_cursor they
	// received. ParseCursor validates its shape so a malformed value is a 400
	// rather than a confusing empty page.
	cursor, err := ParseCursor(c.Query("cursor"))
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	page, err := h.service.GetMessages(c.Context(), int64(conversationID), userID, limit, cursor)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to get messages", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	response := make([]MessageResponse, len(page.Messages))
	for i, msg := range page.Messages {
		response[i] = toMessageResponse(msg)
	}

	var nextCursor *string
	if page.NextCursor != nil {
		encoded := page.NextCursor.String()
		nextCursor = &encoded
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": MessagePageResponse{
			Messages:   response,
			NextCursor: nextCursor,
			HasMore:    page.HasMore,
		},
	})
}

func (h *Handler) DeleteMessage(c *fiber.Ctx) error {
	userID, ok := c.Locals("user_id").(int64)
	if !ok {
		return c.Status(401).JSON(apperrors.NewErrorResponse(apperrors.ErrUnauthorized))
	}
	messageID, err := c.ParamsInt("id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	err = h.service.DeleteMessage(c.Context(), int64(messageID), userID)
	if err != nil {
		var appErr *apperrors.AppError
		if errors.As(err, &appErr) {
			return c.Status(appErr.Status).JSON(apperrors.NewErrorResponse(appErr))
		}
		logger.Log.Error("Failed to delete message", zap.Error(err))
		return c.Status(500).JSON(apperrors.NewErrorResponse(apperrors.ErrInternalServer))
	}

	return c.JSON(fiber.Map{
		"success": true,
		"data": fiber.Map{"message": "Message deleted"},
	})
}

func toMessageResponse(msg *Message) MessageResponse {
	var text *string
	if msg.Text.Valid {
		text = &msg.Text.String
	}

	var mediaID *int64
	if msg.MediaID.Valid {
		mediaID = &msg.MediaID.Int64
	}

	var replyToID *int64
	if msg.ReplyToID.Valid {
		replyToID = &msg.ReplyToID.Int64
	}

	var deliveredAt *int64
	if msg.DeliveredAt.Valid {
		ts := msg.DeliveredAt.Time.UnixMilli()
		deliveredAt = &ts
	}

	var readAt *int64
	if msg.ReadAt.Valid {
		ts := msg.ReadAt.Time.UnixMilli()
		readAt = &ts
	}

	return MessageResponse{
		ID:            msg.ID,
		ConversationID: msg.ConversationID,
		SenderID:      msg.SenderID,
		SenderName:    nullStringPtr(msg.SenderName),
		Type:          msg.Type,
		Text:          text,
		MediaID:       mediaID,
		Media:         msg.Media,
		ReplyToID:     replyToID,
		Status:        msg.Status,
		CreatedAt:     msg.CreatedAt.UnixMilli(),
		UpdatedAt:     msg.UpdatedAt.UnixMilli(),
		DeliveredAt:   deliveredAt,
		ReadAt:        readAt,
	}
}

// nullStringPtr converts a nullable column into the JSON shape: a NULL becomes a
// missing field rather than an empty string, which the client treats as "unknown
// author" instead of "author with an empty name".
func nullStringPtr(ns sql.NullString) *string {
	if ns.Valid {
		return &ns.String
	}
	return nil
}