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

func (s *Service) GetMessages(ctx context.Context, conversationID, userID int64, limit int, cursor *Cursor) (*MessagePage, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	return s.repo.GetByConversation(ctx, conversationID, userID, limit, cursor)
}

func (s *Service) CreateMessage(ctx context.Context, conversationID, senderID int64, msgType, text string, mediaID, replyToID *int64) (*Message, error) {
	// Verify participant
	var isParticipant bool
	err := s.repo.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
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

// CreateEnvelope stores one per-recipient-device envelope for a message.
func (s *Service) CreateEnvelope(ctx context.Context, messageID, recipientDeviceID int64, envelopeType int, ciphertext []byte) error {
	return s.repo.CreateEnvelope(ctx, messageID, recipientDeviceID, envelopeType, ciphertext)
}

// GetEnvelope returns the envelope addressed to recipientDeviceID for messageID.
func (s *Service) GetEnvelope(ctx context.Context, messageID, recipientDeviceID int64) (*Envelope, error) {
	return s.repo.GetEnvelope(ctx, messageID, recipientDeviceID)
}

// GetEnvelopes returns the envelopes addressed to recipientDeviceID for the
// given message ids, keyed by message id. Missing envelopes are absent from
// the map rather than an error.
func (s *Service) GetEnvelopes(ctx context.Context, messageIDs []int64, recipientDeviceID int64) (map[int64]*Envelope, error) {
	return s.repo.GetEnvelopes(ctx, messageIDs, recipientDeviceID)
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