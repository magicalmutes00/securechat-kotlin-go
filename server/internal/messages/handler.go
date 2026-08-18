package messages

import (
	"database/sql"
	"errors"
	"fmt"

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

	authMiddleware := middleware.AuthMiddleware(middleware.TokenValidatorFunc(func(tokenString string) (middleware.TokenClaims, error) {
		claims, err := tokenManager.ValidateAccessToken(tokenString)
		if err != nil {
			return middleware.TokenClaims{}, err
		}
		return middleware.TokenClaims{
			UserID:    claims.UserID,
			DeviceID:  claims.DeviceID,
			SessionID: claims.SessionID,
		}, nil
	}))

	messages := api.Group("/conversations/:conversation_id/messages", authMiddleware)
	messages.Get("", handler.GetMessages)
	
	message := api.Group("/messages", authMiddleware)
	message.Delete("/:id", handler.DeleteMessage)
}

type MessageResponse struct {
	ID          int64   `json:"id"`
	ConversationID int64 `json:"conversation_id"`
	SenderID    int64   `json:"sender_id"`
	Type        string  `json:"type"`
	Text        *string `json:"text,omitempty"`
	MediaID     *int64  `json:"media_id,omitempty"`
	ReplyToID   *int64  `json:"reply_to_id,omitempty"`
	Status      string  `json:"status"`
	CreatedAt   int64   `json:"created_at"`
	UpdatedAt   int64   `json:"updated_at"`
	DeliveredAt *int64  `json:"delivered_at,omitempty"`
	ReadAt      *int64  `json:"read_at,omitempty"`
}

type MessagePageResponse struct {
	Messages   []MessageResponse `json:"messages"`
	NextCursor *int64            `json:"next_cursor,omitempty"`
	HasMore    bool              `json:"has_more"`
}

func (h *Handler) GetMessages(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
	conversationID, err := c.ParamsInt("conversation_id")
	if err != nil {
		return c.Status(400).JSON(apperrors.NewErrorResponse(apperrors.ErrInvalidRequest))
	}

	limit := c.QueryInt("limit", 30)
	cursorStr := c.Query("cursor")
	var cursor *int64
	if cursorStr != "" {
		var ts int64
		if _, err := fmt.Sscanf(cursorStr, "%d", &ts); err == nil {
			cursor = &ts
		}
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

	return c.JSON(fiber.Map{
		"success": true,
		"data": MessagePageResponse{
			Messages:   response,
			NextCursor: page.NextCursor,
			HasMore:    page.HasMore,
		},
	})
}

func (h *Handler) DeleteMessage(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(int64)
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
		ts := msg.DeliveredAt.Time.Unix()
		deliveredAt = &ts
	}

	var readAt *int64
	if msg.ReadAt.Valid {
		ts := msg.ReadAt.Time.Unix()
		readAt = &ts
	}

	return MessageResponse{
		ID:            msg.ID,
		ConversationID: msg.ConversationID,
		SenderID:      msg.SenderID,
		Type:          msg.Type,
		Text:          text,
		MediaID:       mediaID,
		ReplyToID:     replyToID,
		Status:        msg.Status,
		CreatedAt:     msg.CreatedAt.Unix(),
		UpdatedAt:     msg.UpdatedAt.Unix(),
		DeliveredAt:   deliveredAt,
		ReadAt:        readAt,
	}
}