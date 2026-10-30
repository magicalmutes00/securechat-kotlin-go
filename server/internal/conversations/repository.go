package conversations

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
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
	// Name is set for group conversations; a direct conversation leaves it NULL
	// and derives its title from the other participant.
	Name      sql.NullString
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

// UserPreview is the flattened participant payload.
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
	LastSeen       sql.NullTime
}

type MessagePreview struct {
	ID        int64
	ConversationID int64
	SenderID  int64
	// SenderName attributes the preview in a group's list row ("Alice: hi").
	SenderName sql.NullString
	Type      string
	Text      sql.NullString
	MediaID   sql.NullInt64
	Status    string
	CreatedAt time.Time
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*Conversation, error) {
	var conv Conversation
	err := r.db.QueryRowContext(ctx, `
		SELECT id, type, name, created_at, updated_at FROM conversations WHERE id = $1
	`, id).Scan(&conv.ID, &conv.Type, &conv.Name, &conv.CreatedAt, &conv.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrConversationNotFound
		}
		return nil, fmt.Errorf("failed to get conversation: %w", err)
	}
	return &conv, nil
}

// GetByParticipant returns a page of a user's conversations, each with its
// participants, last message and unread count.
//
// This is deliberately two round trips instead of the 1 + 3N it used to be. The
// previous implementation ran a participants query, a last-message query and an
// unread-count query for every conversation in the page, so a 20-conversation
// inbox cost 61 queries against a pooled Neon endpoint — the single slowest path
// in the API. The last message and unread count now come from LATERAL joins in
// the page query, and every participant row for the page is fetched once and
// grouped in memory.
//
// Ordering is by newest message with a fallback to conversation creation, so the
// inbox is genuinely recency-ordered. conversations.updated_at is never bumped
// when a message arrives, so the old `ORDER BY c.updated_at DESC` listed
// conversations in creation order.
func (r *Repository) GetByParticipant(ctx context.Context, userID int64, limit, offset int) ([]*ConversationWithParticipants, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			c.id, c.type, c.name, c.created_at, c.updated_at,
			lm.id, lm.sender_id, lm.type, lm.text, lm.media_id, lm.status, lm.created_at,
			lm.display_name,
			COALESCE(unread.total, 0)
		FROM conversations c
		JOIN conversation_participants me
			ON c.id = me.conversation_id AND me.user_id = $1 AND me.left_at IS NULL
		LEFT JOIN LATERAL (
			SELECT m.id, m.sender_id, m.type, m.text, m.media_id, m.status, m.created_at,
			       u.display_name
			FROM messages m
			LEFT JOIN users u ON u.id = m.sender_id
			WHERE m.conversation_id = c.id AND m.deleted_at IS NULL
			ORDER BY m.created_at DESC, m.id DESC
			LIMIT 1
		) lm ON TRUE
		LEFT JOIN LATERAL (
			SELECT COUNT(*) AS total
			FROM messages m2
			WHERE m2.conversation_id = c.id
			  AND m2.sender_id <> $1
			  AND m2.read_at IS NULL
			  AND m2.deleted_at IS NULL
		) unread ON TRUE
		ORDER BY COALESCE(lm.created_at, c.created_at) DESC, c.id DESC
		LIMIT $2 OFFSET $3
	`, userID, limit, offset)
	if err != nil {
		return nil, fmt.Errorf("failed to get conversations: %w", err)
	}
	defer rows.Close()

	conversations := make([]*ConversationWithParticipants, 0, limit)
	byID := make(map[int64]*ConversationWithParticipants, limit)

	for rows.Next() {
		var (
			conv        ConversationWithParticipants
			lastID      sql.NullInt64
			lastSender  sql.NullInt64
			lastType    sql.NullString
			lastText    sql.NullString
			lastMediaID sql.NullInt64
			lastStatus  sql.NullString
			lastCreated sql.NullTime
			lastSenderName sql.NullString
		)

		if err := rows.Scan(
			&conv.ID, &conv.Type, &conv.Name, &conv.CreatedAt, &conv.UpdatedAt,
			&lastID, &lastSender, &lastType, &lastText, &lastMediaID, &lastStatus, &lastCreated, &lastSenderName,
			&conv.UnreadCount,
		); err != nil {
			return nil, err
		}

		// The LATERAL join yields NULLs for a conversation with no messages.
		if lastID.Valid {
			conv.LastMessage = &MessagePreview{
				ID:             lastID.Int64,
				ConversationID: conv.ID,
				SenderID:       lastSender.Int64,
				SenderName:     lastSenderName,
				Type:           lastType.String,
				Text:           lastText,
				MediaID:        lastMediaID,
				Status:         lastStatus.String,
				CreatedAt:      lastCreated.Time,
			}
		}

		conversations = append(conversations, &conv)
		byID[conv.ID] = &conv
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read conversations: %w", err)
	}

	if len(conversations) == 0 {
		return conversations, nil
	}

	// Second (and final) round trip: every participant row for the whole page.
	placeholders := make([]string, len(conversations))
	args := make([]interface{}, len(conversations))
	for i, conv := range conversations {
		placeholders[i] = fmt.Sprintf("$%d", i+1)
		args[i] = conv.ID
	}

	participantRows, err := r.db.QueryContext(ctx, fmt.Sprintf(`
		SELECT cp.conversation_id, cp.user_id, cp.joined_at, cp.left_at,
		       u.id, u.phone_number, u.username, u.display_name, u.profile_image_id, u.is_online,
		       u.last_seen
		FROM conversation_participants cp
		JOIN users u ON cp.user_id = u.id
		WHERE cp.conversation_id IN (%s) AND cp.left_at IS NULL
		ORDER BY cp.conversation_id, cp.joined_at
	`, strings.Join(placeholders, ", ")), args...)
	if err != nil {
		return nil, fmt.Errorf("failed to get participants: %w", err)
	}
	defer participantRows.Close()

	for participantRows.Next() {
		var (
			p Participant
			u UserPreview
		)
		if err := participantRows.Scan(&p.ConversationID, &p.UserID, &p.JoinedAt, &p.LeftAt,
			&u.ID, &u.PhoneNumber, &u.Username, &u.DisplayName, &u.ProfileImageID, &u.IsOnline,
			&u.LastSeen); err != nil {
			return nil, err
		}
		p.User = &u

		if conv, ok := byID[p.ConversationID]; ok {
			conv.Participants = append(conv.Participants, &p)
		}
	}
	if err := participantRows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read participants: %w", err)
	}

	return conversations, nil
}

func (r *Repository) getParticipants(ctx context.Context, conversationID int64) ([]*Participant, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT cp.conversation_id, cp.user_id, cp.joined_at, cp.left_at,
		       u.id, u.phone_number, u.username, u.display_name, u.profile_image_id, u.is_online,
		       u.last_seen
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
			&u.ID, &u.PhoneNumber, &u.Username, &u.DisplayName, &u.ProfileImageID, &u.IsOnline,
			&u.LastSeen); err != nil {
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

// userExists reports whether a user id is present. Used to validate group
// members up front so the caller gets a 404 rather than a foreign-key 500.
func (r *Repository) userExists(ctx context.Context, userID int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM users WHERE id = $1)
	`, userID).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("failed to check user existence: %w", err)
	}
	return exists, nil
}

// CreateGroupConversation creates a new group conversation with the creator plus
// the supplied members, and returns it fully populated.
//
// Runs in one transaction so a group can never exist with a missing or partial
// participant set — a conversation that some member can see but another cannot is
// exactly the kind of inconsistency that produces silent message loss.
//
// The creator is added as a participant automatically; memberIDs must not already
// contain them (the service validates that).
func (r *Repository) CreateGroupConversation(
	ctx context.Context, creatorUserID int64, name string, memberIDs []int64,
) (*ConversationWithParticipants, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var convID int64
	err = tx.QueryRowContext(ctx, `
		INSERT INTO conversations (type, name, created_at, updated_at)
		VALUES ('group', $1, NOW(), NOW())
		RETURNING id
	`, name).Scan(&convID)
	if err != nil {
		return nil, fmt.Errorf("failed to create conversation: %w", err)
	}

	// Creator first, then members. Building a multi-row VALUES list keeps this to
	// one round trip and one point of failure.
	all := append([]int64{creatorUserID}, memberIDs...)

	var (
		sb   strings.Builder
		args = make([]interface{}, 0, len(all)*2)
	)
	sb.WriteString(`
		INSERT INTO conversation_participants (conversation_id, user_id, joined_at)
		VALUES
	`)
	for i, uid := range all {
		if i > 0 {
			sb.WriteString(",")
		}
		base := i*2 + 1
		fmt.Fprintf(&sb, "($%d, $%d, NOW())", base, base+1)
		args = append(args, convID, uid)
	}

	if _, err := tx.ExecContext(ctx, sb.String(), args...); err != nil {
		return nil, fmt.Errorf("failed to add participants: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	return r.GetWithParticipants(ctx, convID, creatorUserID)
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

// groupMembership checks that conversationID is a group and that userID is an
// active participant of it. Add/remove member share this gate: without it a
// member of a 1:1 chat could pull strangers into a private conversation.
func (r *Repository) groupMembership(ctx context.Context, conversationID, userID int64) (bool, error) {
	var ok bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1
			FROM conversations c
			JOIN conversation_participants cp ON cp.conversation_id = c.id
			WHERE c.id = $1
			  AND c.type = 'group'
			  AND cp.user_id = $2
			  AND cp.left_at IS NULL
		)
	`, conversationID, userID).Scan(&ok)
	if err != nil {
		return false, fmt.Errorf("failed to check group membership: %w", err)
	}
	return ok, nil
}

// AddMembers inserts the given users as participants, skipping anyone who is
// already an active member so a retried request is a no-op rather than an error.
// Rows that rejoin after leaving get their left_at cleared, which is how a
// former member is re-added.
//
// The caller must already be an active participant of the group; that is checked
// by the service before this runs.
func (r *Repository) AddMembers(ctx context.Context, conversationID int64, memberIDs []int64) error {
	if len(memberIDs) == 0 {
		return nil
	}

	var (
		sb   strings.Builder
		args = make([]interface{}, 0, len(memberIDs)*2)
	)
	sb.WriteString(`
		INSERT INTO conversation_participants (conversation_id, user_id, joined_at, left_at)
		VALUES
	`)
	for i, uid := range memberIDs {
		if i > 0 {
			sb.WriteString(",")
		}
		base := i*2 + 1
		// left_at is NULL on insert; ON CONFLICT re-arms a returning member.
		fmt.Fprintf(&sb, "($%d, $%d, NOW(), NULL)", base, base+1)
		args = append(args, conversationID, uid)
	}
	sb.WriteString(`
		ON CONFLICT (conversation_id, user_id)
		DO UPDATE SET left_at = NULL
	`)

	if _, err := r.db.ExecContext(ctx, sb.String(), args...); err != nil {
		return fmt.Errorf("failed to add members: %w", err)
	}
	return nil
}

// RemoveMember marks a member as having left. Removing yourself is refused: that
// is "leave", a different operation with different consequences, and treating it
// as a removal would let a caller delete their own membership by accident.
func (r *Repository) RemoveMember(ctx context.Context, conversationID, targetUserID int64) error {
	res, err := r.db.ExecContext(ctx, `
		UPDATE conversation_participants
		SET left_at = NOW()
		WHERE conversation_id = $1
		  AND user_id = $2
		  AND left_at IS NULL
	`, conversationID, targetUserID)
	if err != nil {
		return fmt.Errorf("failed to remove member: %w", err)
	}

	affected, err := res.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to confirm member removal: %w", err)
	}
	if affected == 0 {
		return apperrors.ErrUserNotFound
	}
	return nil
}

// RenameConversation sets a conversation's title. Only groups have a name, so a
// rename against a direct conversation is refused rather than silently ignored.
//
// Any active participant may rename. The schema has no member roles yet, so
// there is no admin gate to apply; that is a deliberate, documented limitation
// rather than an oversight (see the note on UpdateConversation in the service).
func (r *Repository) RenameConversation(ctx context.Context, conversationID, userID int64, name string) error {
	res, err := r.db.ExecContext(ctx, `
		UPDATE conversations c
		SET name = $1, updated_at = NOW()
		WHERE c.id = $2
		  AND c.type = 'group'
		  AND EXISTS (
		      SELECT 1 FROM conversation_participants cp
		      WHERE cp.conversation_id = c.id
		        AND cp.user_id = $3
		        AND cp.left_at IS NULL
		  )
	`, name, conversationID, userID)
	if err != nil {
		return fmt.Errorf("failed to rename conversation: %w", err)
	}

	affected, err := res.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to confirm rename: %w", err)
	}
	if affected == 0 {
		// Either not a participant or not a group; distinguishing them would leak
		// which conversation ids exist, so both answer "not a participant".
		return apperrors.ErrNotParticipant
	}
	return nil
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
