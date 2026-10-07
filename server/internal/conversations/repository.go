package conversations

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	apperrors "github.com/securechat/server/pkg/errors"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

type Conversation struct {
	ID        int64
	Type      string
	CreatedAt time.Time
	UpdatedAt time.Time
}

type ConversationWithParticipants struct {
	Conversation
	Participants []*Participant
	LastMessage  *MessagePreview
	UnreadCount  int
}

type Participant struct {
	ConversationID int64
	UserID         int64
	JoinedAt       time.Time
	LeftAt         sql.NullTime
	User           *UserPreview
}

type UserPreview struct {
	ID int64
	// Phone is NULL for Google-signed-in users, so the scan target must be
	// NULL-safe — a plain string made every conversation containing such a
	// user fail with a Scan error.
	PhoneNumber    sql.NullString
	Username       sql.NullString
	DisplayName    string
	ProfileImageID sql.NullInt64
	IsOnline       bool
}

type MessagePreview struct {
	ID        int64
	ConversationID int64
	SenderID  int64
	Type      string
	Text      sql.NullString
	MediaID   sql.NullInt64
	Status    string
	CreatedAt time.Time
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*Conversation, error) {
	var conv Conversation
	err := r.db.QueryRowContext(ctx, `
		SELECT id, type, created_at, updated_at FROM conversations WHERE id = $1
	`, id).Scan(&conv.ID, &conv.Type, &conv.CreatedAt, &conv.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrConversationNotFound
		}
		return nil, fmt.Errorf("failed to get conversation: %w", err)
	}
	return &conv, nil
}

func (r *Repository) GetByParticipant(ctx context.Context, userID int64, limit, offset int) ([]*ConversationWithParticipants, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT c.id, c.type, c.created_at, c.updated_at
		FROM conversations c
		JOIN conversation_participants cp ON c.id = cp.conversation_id
		WHERE cp.user_id = $1 AND cp.left_at IS NULL
		ORDER BY c.updated_at DESC
		LIMIT $2 OFFSET $3
	`, userID, limit, offset)
	if err != nil {
		return nil, fmt.Errorf("failed to get conversations: %w", err)
	}
	defer rows.Close()

	var conversations []*ConversationWithParticipants
	for rows.Next() {
		var conv ConversationWithParticipants
		if err := rows.Scan(&conv.ID, &conv.Type, &conv.CreatedAt, &conv.UpdatedAt); err != nil {
			return nil, err
		}

		// Load participants
		participants, err := r.getParticipants(ctx, conv.ID)
		if err != nil {
			return nil, err
		}
		conv.Participants = participants

		// Load last message
		lastMsg, err := r.getLastMessage(ctx, conv.ID)
		if err != nil {
			return nil, err
		}
		conv.LastMessage = lastMsg

		// Get unread count
		unread, err := r.getUnreadCount(ctx, conv.ID, userID)
		if err != nil {
			return nil, err
		}
		conv.UnreadCount = unread

		conversations = append(conversations, &conv)
	}

	return conversations, nil
}

func (r *Repository) getParticipants(ctx context.Context, conversationID int64) ([]*Participant, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT cp.conversation_id, cp.user_id, cp.joined_at, cp.left_at,
		       u.id, u.phone_number, u.username, u.display_name, u.profile_image_id, u.is_online
		FROM conversation_participants cp
		JOIN users u ON cp.user_id = u.id
		WHERE cp.conversation_id = $1 AND cp.left_at IS NULL
	`, conversationID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var participants []*Participant
	for rows.Next() {
		var p Participant
		var u UserPreview
		if err := rows.Scan(&p.ConversationID, &p.UserID, &p.JoinedAt, &p.LeftAt,
			&u.ID, &u.PhoneNumber, &u.Username, &u.DisplayName, &u.ProfileImageID, &u.IsOnline); err != nil {
			return nil, err
		}
		p.User = &u
		participants = append(participants, &p)
	}
	return participants, nil
}

func (r *Repository) getLastMessage(ctx context.Context, conversationID int64) (*MessagePreview, error) {
	var msg MessagePreview
	err := r.db.QueryRowContext(ctx, `
		SELECT id, conversation_id, sender_id, type, text, media_id, status, created_at
		FROM messages
		WHERE conversation_id = $1 AND deleted_at IS NULL
		ORDER BY created_at DESC LIMIT 1
	`, conversationID).Scan(&msg.ID, &msg.ConversationID, &msg.SenderID, &msg.Type, &msg.Text, &msg.MediaID, &msg.Status, &msg.CreatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &msg, nil
}

func (r *Repository) getUnreadCount(ctx context.Context, conversationID, userID int64) (int, error) {
	var count int
	err := r.db.QueryRowContext(ctx, `
		SELECT COUNT(*) FROM messages
		WHERE conversation_id = $1 AND sender_id != $2 AND read_at IS NULL AND deleted_at IS NULL
	`, conversationID, userID).Scan(&count)
	return count, err
}

func (r *Repository) CreateDirectConversation(ctx context.Context, userID, otherUserID int64) (*ConversationWithParticipants, error) {
	// Check if direct conversation already exists
	var existingID int64
	err := r.db.QueryRowContext(ctx, `
		SELECT c.id FROM conversations c
		JOIN conversation_participants cp1 ON c.id = cp1.conversation_id
		JOIN conversation_participants cp2 ON c.id = cp2.conversation_id
		WHERE c.type = 'direct'
		  AND cp1.user_id = $1 AND cp2.user_id = $2
		  AND cp1.left_at IS NULL AND cp2.left_at IS NULL
	`, userID, otherUserID).Scan(&existingID)

	if err == nil {
		return r.GetWithParticipants(ctx, existingID, userID)
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("failed to check existing conversation: %w", err)
	}

	// Create new conversation
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	var convID int64
	err = tx.QueryRowContext(ctx, `
		INSERT INTO conversations (type, created_at, updated_at) VALUES ('direct', NOW(), NOW())
		RETURNING id
	`).Scan(&convID)
	if err != nil {
		return nil, fmt.Errorf("failed to create conversation: %w", err)
	}

	// Add participants
	_, err = tx.ExecContext(ctx, `
		INSERT INTO conversation_participants (conversation_id, user_id, joined_at) VALUES ($1, $2, NOW())
	`, convID, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to add participant: %w", err)
	}

	_, err = tx.ExecContext(ctx, `
		INSERT INTO conversation_participants (conversation_id, user_id, joined_at) VALUES ($1, $2, NOW())
	`, convID, otherUserID)
	if err != nil {
		return nil, fmt.Errorf("failed to add participant: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	return r.GetWithParticipants(ctx, convID, userID)
}

func (r *Repository) GetWithParticipants(ctx context.Context, conversationID, userID int64) (*ConversationWithParticipants, error) {
	conv, err := r.GetByID(ctx, conversationID)
	if err != nil {
		return nil, err
	}

	participants, err := r.getParticipants(ctx, conversationID)
	if err != nil {
		return nil, err
	}

	lastMsg, err := r.getLastMessage(ctx, conversationID)
	if err != nil {
		return nil, err
	}

	unread, err := r.getUnreadCount(ctx, conversationID, userID)
	if err != nil {
		return nil, err
	}

	return &ConversationWithParticipants{
		Conversation: *conv,
		Participants: participants,
		LastMessage:  lastMsg,
		UnreadCount:  unread,
	}, nil
}

func (r *Repository) DeleteConversation(ctx context.Context, conversationID, userID int64) error {
	// Check if user is participant
	var isParticipant bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, conversationID, userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return apperrors.ErrNotParticipant
	}

	// Mark user as left
	_, err = r.db.ExecContext(ctx, `
		UPDATE conversation_participants SET left_at = NOW() WHERE conversation_id = $1 AND user_id = $2
	`, conversationID, userID)
	return err
}
