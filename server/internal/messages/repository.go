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
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL)
	`, conversationID, userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return nil, errors.ErrNotParticipant
	}

	query := `
		SELECT id, conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at, delivered_at, read_at, deleted_at
		FROM messages
		WHERE conversation_id = ? AND deleted_at IS NULL
	`
	args := []interface{}{conversationID}

	if cursor != nil {
		query += " AND created_at < ?"
		args = append(args, *cursor)
	}

	query += " ORDER BY created_at DESC LIMIT ?"
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
	result, err := r.db.ExecContext(ctx, `
		INSERT INTO messages (conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
	`, msg.ConversationID, msg.SenderID, msg.Type, msg.Text, msg.MediaID, msg.ReplyToID, msg.Status)
	if err != nil {
		return nil, fmt.Errorf("failed to create message: %w", err)
	}

	id, _ := result.LastInsertId()
	return r.GetByID(ctx, id)
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*Message, error) {
	var msg Message
	err := r.db.QueryRowContext(ctx, `
		SELECT id, conversation_id, sender_id, type, text, media_id, reply_to_id, status, created_at, updated_at, delivered_at, read_at, deleted_at
		FROM messages WHERE id = ?
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
		query = `UPDATE messages SET status = ?, delivered_at = NOW(), updated_at = NOW() WHERE id = ?`
	case "read":
		query = `UPDATE messages SET status = ?, read_at = NOW(), updated_at = NOW() WHERE id = ?`
	default:
		query = `UPDATE messages SET status = ?, updated_at = NOW() WHERE id = ?`
	}
	_, err := r.db.ExecContext(ctx, query, status, messageID)
	return err
}

func (r *Repository) SoftDelete(ctx context.Context, messageID, userID int64) error {
	// Verify ownership
	var senderID int64
	err := r.db.QueryRowContext(ctx, `SELECT sender_id FROM messages WHERE id = ?`, messageID).Scan(&senderID)
	if err != nil {
		return err
	}
	if senderID != userID {
		return errors.ErrMessageNotOwned
	}

	_, err = r.db.ExecContext(ctx, `UPDATE messages SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?`, messageID)
	return err
}

func (r *Repository) MarkConversationRead(ctx context.Context, conversationID, userID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE messages SET status = 'read', read_at = NOW(), updated_at = NOW()
		WHERE conversation_id = ? AND sender_id != ? AND read_at IS NULL AND deleted_at IS NULL
	`, conversationID, userID)
	return err
}

func (r *Repository) UpdateReadStatus(ctx context.Context, messageID int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE messages SET status = 'read', read_at = NOW(), updated_at = NOW()
		WHERE id = ? AND read_at IS NULL AND deleted_at IS NULL
	`, messageID)
	return err
}