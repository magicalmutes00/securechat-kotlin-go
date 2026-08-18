package messages

import (
	"context"
	"database/sql"

	"github.com/securechat/server/pkg/errors"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetMessages(ctx context.Context, conversationID, userID int64, limit int, cursor *int64) (*MessagePage, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	return s.repo.GetByConversation(ctx, conversationID, userID, limit, cursor)
}

func (s *Service) CreateMessage(ctx context.Context, conversationID, senderID int64, msgType, text string, mediaID, replyToID *int64) (*Message, error) {
	// Verify participant
	var isParticipant bool
	err := s.repo.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL)
	`, conversationID, senderID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return nil, errors.ErrNotParticipant
	}

	msg := &Message{
		ConversationID: conversationID,
		SenderID:       senderID,
		Type:           msgType,
		Text:           toNullString(text),
		MediaID:        toNullInt64(mediaID),
		ReplyToID:      toNullInt64(replyToID),
		Status:         "sent",
	}

	return s.repo.Create(ctx, msg)
}

func (s *Service) UpdateDeliveryStatus(ctx context.Context, messageID int64) error {
	return s.repo.UpdateStatus(ctx, messageID, "delivered")
}

func (s *Service) UpdateReadStatus(ctx context.Context, messageID int64) error {
	return s.repo.UpdateStatus(ctx, messageID, "read")
}

func (s *Service) MarkConversationRead(ctx context.Context, conversationID, userID int64) error {
	return s.repo.MarkConversationRead(ctx, conversationID, userID)
}

func (s *Service) DeleteMessage(ctx context.Context, messageID, userID int64) error {
	return s.repo.SoftDelete(ctx, messageID, userID)
}

func toNullString(s string) sql.NullString {
	if s != "" {
		return sql.NullString{String: s, Valid: true}
	}
	return sql.NullString{}
}

func toNullInt64(i *int64) sql.NullInt64 {
	if i != nil {
		return sql.NullInt64{Int64: *i, Valid: true}
	}
	return sql.NullInt64{}
}