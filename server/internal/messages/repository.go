package messages

import (
	"context"
	"database/sql"
	"fmt"
	"strconv"
	"strings"
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
	// SenderName is the author's display name at read time. Group chats need it
	// on every message; it is NULL only if the sender's row has vanished.
	SenderName  sql.NullString
	Type        string
	Text        sql.NullString
	MediaID     sql.NullInt64
	ReplyToID   sql.NullInt64
	Status      string
	CreatedAt   time.Time
	UpdatedAt   time.Time
	DeliveredAt sql.NullTime
	ReadAt      sql.NullTime
	DeletedAt   sql.NullTime
	// Media is the attachment row referenced by MediaID, loaded by attachMedia.
	// Not a column: it is resolved by a second query so the message queries stay
	// readable and neither of them repeats thirteen scan targets.
	Media *MediaAttachment
}

// MediaAttachment is a media row in the shape the client consumes.
//
// Messages used to carry only media_id, which left a recipient unable to render
// the attachment: GET /media/:id is scoped to the uploader, so the other side of
// a conversation was refused the record it had just been told about. Embedding
// the row in the message (and in the WebSocket frame) makes one payload
// sufficient to draw a bubble.
type MediaAttachment struct {
	ID                 int64    `json:"id"`
	MessageID          int64    `json:"message_id"`
	CloudinaryPublicID string   `json:"cloudinary_public_id"`
	ResourceType       string   `json:"resource_type"`
	SecureURL          string   `json:"secure_url"`
	OriginalFilename   string   `json:"original_filename"`
	MimeType           string   `json:"mime_type"`
	FileSize           int64    `json:"file_size"`
	Width              *int     `json:"width,omitempty"`
	Height             *int     `json:"height,omitempty"`
	Duration           *float64 `json:"duration,omitempty"`
	SHA256             string   `json:"sha256"`
	ThumbnailURL       *string  `json:"thumbnail_url,omitempty"`
	CreatedAt          int64    `json:"created_at"`
}

// attachMedia loads the media rows referenced by msgs and hangs each one off its
// message. Missing rows are tolerated: media can be deleted while the message
// survives (the FK is ON DELETE SET NULL), and one dangling reference must not
// fail a whole page of history.
func (r *Repository) attachMedia(ctx context.Context, msgs []*Message) error {
	ids := make([]int64, 0, len(msgs))
	for _, m := range msgs {
		if m.MediaID.Valid {
			ids = append(ids, m.MediaID.Int64)
		}
	}
	if len(ids) == 0 {
		return nil
	}

	args := make([]interface{}, len(ids))
	for i, id := range ids {
		args[i] = id
	}

	query := fmt.Sprintf(`
		SELECT id, message_id, cloudinary_public_id, resource_type, secure_url,
		       original_filename, mime_type, file_size, width, height, duration,
		       sha256, thumbnail_url, created_at
		FROM media WHERE id IN (%s)`, placeholders(len(ids)))

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("failed to load message media: %w", err)
	}
	defer rows.Close()

	byID := make(map[int64]*MediaAttachment, len(ids))
	for rows.Next() {
		var (
			a            MediaAttachment
			messageID    sql.NullInt64
			width        sql.NullInt64
			height       sql.NullInt64
			duration     sql.NullFloat64
			thumbnailURL sql.NullString
			createdAt    time.Time
		)
		if err := rows.Scan(&a.ID, &messageID, &a.CloudinaryPublicID, &a.ResourceType,
			&a.SecureURL, &a.OriginalFilename, &a.MimeType, &a.FileSize,
			&width, &height, &duration, &a.SHA256, &thumbnailURL, &createdAt); err != nil {
			return fmt.Errorf("failed to scan message media: %w", err)
		}
		if messageID.Valid {
			a.MessageID = messageID.Int64
		}
		if width.Valid {
			w := int(width.Int64)
			a.Width = &w
		}
		if height.Valid {
			h := int(height.Int64)
			a.Height = &h
		}
		if duration.Valid {
			d := duration.Float64
			a.Duration = &d
		}
		if thumbnailURL.Valid {
			t := thumbnailURL.String
			a.ThumbnailURL = &t
		}
		a.CreatedAt = createdAt.UnixMilli()
		byID[a.ID] = &a
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("failed to read message media: %w", err)
	}

	for _, m := range msgs {
		if m.MediaID.Valid {
			m.Media = byID[m.MediaID.Int64]
		}
	}
	return nil
}

// placeholders renders "$1,$2,..." for an IN clause of n parameters.
func placeholders(n int) string {
	parts := make([]string, n)
	for i := range parts {
		parts[i] = fmt.Sprintf("$%d", i+1)
	}
	return strings.Join(parts, ",")
}

// Cursor is an opaque keyset position in a conversation's timeline. It encodes
// the full ordering key — (created_at in microseconds, id) — rather than just a
// timestamp.
//
// The previous cursor carried whole Unix *seconds* and the query compared
// `created_at < to_timestamp(cursor)`, so every message sharing the boundary
// second was silently skipped on the next page. Chat messages routinely arrive
// several per second, so this dropped real messages, not a rare edge case. The
// id tiebreaker also makes the order a total order, so paging can never loop or
// stall on a duplicate timestamp.
type Cursor struct {
	CreatedAtMicros int64
	ID              int64
}

// String renders the cursor in the opaque "<micros>:<id>" wire form.
func (c Cursor) String() string {
	return fmt.Sprintf("%d:%d", c.CreatedAtMicros, c.ID)
}

// ParseCursor decodes a cursor produced by Cursor.String. An empty string means
// "first page" and returns a nil cursor with no error.
func ParseCursor(raw string) (*Cursor, error) {
	if raw == "" {
		return nil, nil
	}

	parts := strings.SplitN(raw, ":", 2)
	if len(parts) != 2 {
		return nil, errors.ErrInvalidRequest
	}

	micros, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		return nil, errors.ErrInvalidRequest
	}
	id, err := strconv.ParseInt(parts[1], 10, 64)
	if err != nil {
		return nil, errors.ErrInvalidRequest
	}

	return &Cursor{CreatedAtMicros: micros, ID: id}, nil
}

type MessagePage struct {
	Messages   []*Message
	NextCursor *Cursor
	HasMore    bool
}

func (r *Repository) GetByConversation(ctx context.Context, conversationID, userID int64, limit int, cursor *Cursor) (*MessagePage, error) {
	// Verify participant
	var isParticipant bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, conversationID, userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return nil, errors.ErrNotParticipant
	}

	// Row-wise comparison against the (created_at, id) ordering key. The
	// timestamp is rebuilt in PostgreSQL from an integer number of
	// microseconds: dividing by 1e6 in float8 stays microsecond-accurate at
	// current epoch values, and avoids sending a float that could round down
	// and re-include the boundary row (an infinite-scroll loop).
	// sender_name comes from a LEFT JOIN: group chats need an author on every
	// message, and a user account can in principle be missing (or the sender's
	// row gone), so a plain JOIN would silently drop those messages from history.
	query := `
		SELECT m.id, m.conversation_id, m.sender_id, m.type, m.text, m.media_id, m.reply_to_id, m.status,
		       m.created_at, m.updated_at, m.delivered_at, m.read_at, m.deleted_at,
		       u.display_name
		FROM messages m
		LEFT JOIN users u ON u.id = m.sender_id
		WHERE m.conversation_id = $1 AND m.deleted_at IS NULL
	`
	args := []interface{}{conversationID}

	if cursor != nil {
		query += " AND (m.created_at, m.id) < (to_timestamp($2::bigint::double precision / 1000000.0), $3)"
		args = append(args, cursor.CreatedAtMicros, cursor.ID)
	}

	// id is the tiebreaker that makes this a total order.
	query += " ORDER BY m.created_at DESC, m.id DESC"
	query += fmt.Sprintf(" LIMIT $%d", len(args)+1)
	args = append(args, limit+1) // Fetch one extra to check hasMore

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to get messages: %w", err)
	}
	defer rows.Close()

	var messages []*Message
	for rows.Next() {
		var msg Message
		if err := rows.Scan(&msg.ID, &msg.ConversationID, &msg.SenderID, &msg.Type, &msg.Text, &msg.MediaID, &msg.ReplyToID, &msg.Status, &msg.CreatedAt, &msg.UpdatedAt, &msg.DeliveredAt, &msg.ReadAt, &msg.DeletedAt, &msg.SenderName); err != nil {
			return nil, err
		}
		messages = append(messages, &msg)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read messages: %w", err)
	}

	hasMore := len(messages) > limit
	if hasMore {
		messages = messages[:limit]
	}

	if err := r.attachMedia(ctx, messages); err != nil {
		return nil, err
	}

	// Only hand back a cursor when there is actually another page; emitting one
	// on the last page invited a pointless extra round trip that returned
	// nothing.
	var nextCursor *Cursor
	if hasMore && len(messages) > 0 {
		last := messages[len(messages)-1]
		nextCursor = &Cursor{
			CreatedAtMicros: last.CreatedAt.UnixMicro(),
			ID:              last.ID,
		}
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
		SELECT m.id, m.conversation_id, m.sender_id, m.type, m.text, m.media_id, m.reply_to_id, m.status,
		       m.created_at, m.updated_at, m.delivered_at, m.read_at, m.deleted_at,
		       u.display_name
		FROM messages m
		LEFT JOIN users u ON u.id = m.sender_id
		WHERE m.id = $1
	`, id).Scan(&msg.ID, &msg.ConversationID, &msg.SenderID, &msg.Type, &msg.Text, &msg.MediaID, &msg.ReplyToID, &msg.Status, &msg.CreatedAt, &msg.UpdatedAt, &msg.DeliveredAt, &msg.ReadAt, &msg.DeletedAt, &msg.SenderName)
	if err != nil {
		return nil, err
	}
	if err := r.attachMedia(ctx, []*Message{&msg}); err != nil {
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
