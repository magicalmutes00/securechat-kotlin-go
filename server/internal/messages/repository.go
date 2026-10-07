package messages

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/securechat/server/pkg/errors"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type Message struct {
	ID             int64
	ConversationID int64
	SenderID       int64
	Type           string
	Text           sql.NullString
	MediaID        sql.NullInt64
	ReplyToID      sql.NullInt64
	Status         string
	CreatedAt      time.Time
	UpdatedAt      time.Time
	DeliveredAt    sql.NullTime
	ReadAt         sql.NullTime
	DeletedAt      sql.NullTime
}

type MessagePage struct {
	Messages   []*Message
	NextCursor *int64
	HasMore    bool
}

func (r *Repository) GetByConversation(ctx context.Context, conversationID, userID int64, limit int, cursor *int64) (*MessagePage, error) {
	// Verify participant
	var isParticipant bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, conversationID, userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return nil, errors.ErrNotParticipant
	}

	// The cursor travels over the wire as a Unix timestamp, so it is turned
	// back into a timestamptz before comparing against created_at.
	query := `
		SELECT id, conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at, delivered_at, read_at, deleted_at
		FROM messages
		WHERE conversation_id = $1 AND deleted_at IS NULL
	`
	args := []interface{}{conversationID}

	if cursor != nil {
		query += " AND created_at < to_timestamp($2)"
		args = append(args, *cursor)
	}

	if len(args) == 1 {
		query += " ORDER BY created_at DESC LIMIT $2"
	} else {
		query += " ORDER BY created_at DESC LIMIT $3"
	}
	args = append(args, limit+1) // Fetch one extra to check hasMore

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to get messages: %w", err)
	}
	defer rows.Close()

	var messages []*Message
	for rows.Next() {
		var msg Message
		if err := rows.Scan(&msg.ID, &msg.ConversationID, &msg.SenderID, &msg.Type, &msg.Text, &msg.MediaID, &msg.ReplyToID, &msg.Status, &msg.CreatedAt, &msg.UpdatedAt, &msg.DeliveredAt, &msg.ReadAt, &msg.DeletedAt); err != nil {
			return nil, err
		}
		messages = append(messages, &msg)
	}

	hasMore := len(messages) > limit
	if hasMore {
		messages = messages[:limit]
	}

	var nextCursor *int64
	if len(messages) > 0 {
		last := messages[len(messages)-1]
		ts := last.CreatedAt.Unix()
		nextCursor = &ts
	}

	return &MessagePage{
		Messages:   messages,
		NextCursor: nextCursor,
		HasMore:    hasMore,
	}, nil
}

func (r *Repository) Create(ctx context.Context, msg *Message) (*Message, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO messages (conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
		RETURNING id
	`, msg.ConversationID, msg.SenderID, msg.Type, msg.Text, msg.MediaID, msg.ReplyToID, msg.Status).Scan(&id)
	if err != nil {
		return nil, fmt.Errorf("failed to create message: %w", err)
	}

	return r.GetByID(ctx, id)
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*Message, error) {
	var msg Message
	err := r.db.QueryRowContext(ctx, `
		SELECT id, conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at, delivered_at, read_at, deleted_at
		FROM messages WHERE id = $1
	`, id).Scan(&msg.ID, &msg.ConversationID, &msg.SenderID, &msg.Type, &msg.Text, &msg.MediaID, &msg.ReplyToID, &msg.Status, &msg.CreatedAt, &msg.UpdatedAt, &msg.DeliveredAt, &msg.ReadAt, &msg.DeletedAt)
	if err != nil {
		return nil, err
	}
	return &msg, nil
}

func (r *Repository) UpdateStatus(ctx context.Context, messageID int64, status string) error {
	var query string
	switch status {
	case "delivered":
		query = `UPDATE messages SET status = $1, delivered_at = NOW(), updated_at = NOW() WHERE id = $2`
	case "read":
		query = `UPDATE messages SET status = $1, read_at = NOW(), updated_at = NOW() WHERE id = $2`
	default:
		query = `UPDATE messages SET status = $1, updated_at = NOW() WHERE id = $2`
	}
	_, err := r.db.ExecContext(ctx, query, status, messageID)
	return err
}

func (r *Repository) SoftDelete(ctx context.Context, messageID, userID int64) error {
	// Verify ownership
	var senderID int64
	err := r.db.QueryRowContext(ctx, `SELECT sender_id FROM messages WHERE id = $1`, messageID).Scan(&senderID)
	if err != nil {
		return err
	}
	if senderID != userID {
		return errors.ErrMessageNotOwned
	}

	_, err = r.db.ExecContext(ctx, `UPDATE messages SET deleted_at = NOW(), updated_at = NOW() WHERE id = $1`, messageID)
	return err
}

func (r *Repository) MarkConversationRead(ctx context.Context, conversationID, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE messages SET status = 'read', read_at = NOW(), updated_at = NOW()
		WHERE conversation_id = $1 AND sender_id != $2 AND read_at IS NULL AND deleted_at IS NULL
	`, conversationID, userID)
	return err
}

func (r *Repository) UpdateReadStatus(ctx context.Context, messageID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE messages SET status = 'read', read_at = NOW(), updated_at = NOW()
		WHERE id = $1 AND read_at IS NULL AND deleted_at IS NULL
	`, messageID)
	return err
}

// BelongsToConversation verifies that a message is part of the given
// conversation, so receipts and deletes cannot be applied cross-conversation.
func (r *Repository) BelongsToConversation(ctx context.Context, messageID, conversationID int64) (bool, error) {
	var belongs bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM messages WHERE id = $1 AND conversation_id = $2)
	`, messageID, conversationID).Scan(&belongs)
	return belongs, err
}
