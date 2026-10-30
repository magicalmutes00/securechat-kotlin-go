package conversations

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	apperrors "github.com/securechat/server/pkg/errors"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetConversations(ctx context.Context, userID int64, limit, offset int) ([]*ConversationWithParticipants, error) {
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}
	return s.repo.GetByParticipant(ctx, userID, limit, offset)
}

func (s *Service) GetConversation(ctx context.Context, conversationID, userID int64) (*ConversationWithParticipants, error) {
	// Verify participant
	var isParticipant bool
	err := s.repo.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, conversationID, userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		return nil, apperrors.ErrNotParticipant
	}

	return s.repo.GetWithParticipants(ctx, conversationID, userID)
}

// CreateDirectConversation starts (or reuses) a 1:1 conversation. The other
// participant is identified either by otherUserID (preferred) or, when that is
// zero, resolved from otherUserPhone.
func (s *Service) CreateDirectConversation(ctx context.Context, userID int64, otherUserPhone string, otherUserID int64) (*ConversationWithParticipants, error) {
	// Find other user by phone when no id was supplied.
	if otherUserID == 0 {
		err := s.repo.db.QueryRowContext(ctx, `SELECT id FROM users WHERE phone_number = $1`, otherUserPhone).Scan(&otherUserID)
		if err != nil {
			if errors.Is(err, sql.ErrNoRows) {
				return nil, apperrors.ErrUserNotFound
			}
			return nil, fmt.Errorf("failed to find user: %w", err)
		}
	}

	if otherUserID == userID {
		return nil, apperrors.NewWithDetails("CANNOT_CREATE_SELF_CONVERSATION", "Cannot create conversation with yourself", 400, nil)
	}

	return s.repo.CreateDirectConversation(ctx, userID, otherUserID)
}

// Group size limits. A cap exists mostly to bound the fan-out cost of the
// WebSocket broadcast and the participants query; 256 keeps a group useful while
// keeping those paths cheap.
const (
	MaxGroupNameLength = 255
	MaxGroupMembers    = 256
)

// CreateGroupConversation starts a new group. The caller becomes a participant
// automatically; memberIDs are the *other* members and must not include the
// caller.
func (s *Service) CreateGroupConversation(
	ctx context.Context, userID int64, name string, memberIDs []int64,
) (*ConversationWithParticipants, error) {
	name = strings.TrimSpace(name)
	if name == "" {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_NAME", "A group name is required", 400, nil)
	}
	if len(name) > MaxGroupNameLength {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_NAME", fmt.Sprintf("Group name must be at most %d characters", MaxGroupNameLength), 400, nil)
	}

	if len(memberIDs) == 0 {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_MEMBERS", "A group needs at least one other member", 400, nil)
	}
	if len(memberIDs) > MaxGroupMembers {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_MEMBERS", fmt.Sprintf("A group may have at most %d members", MaxGroupMembers), 400, nil)
	}

	// Deduplicate, drop the caller, and reject self-conversation. A member listed
	// twice would fail the participants primary key and abort the whole insert,
	// which is a confusing error for the user.
	seen := make(map[int64]struct{}, len(memberIDs))
	unique := make([]int64, 0, len(memberIDs))
	for _, id := range memberIDs {
		if id == userID {
			return nil, apperrors.NewWithDetails(
				"INVALID_GROUP_MEMBERS", "The creator is added to the group automatically", 400, nil)
		}
		if _, dup := seen[id]; dup {
			continue
		}
		seen[id] = struct{}{}
		unique = append(unique, id)
	}

	// Every member must be a real user, otherwise the participants insert fails
	// on the foreign key and the caller gets an opaque 500.
	for _, id := range unique {
		exists, err := s.repo.userExists(ctx, id)
		if err != nil {
			return nil, err
		}
		if !exists {
			return nil, apperrors.ErrUserNotFound
		}
	}

	return s.repo.CreateGroupConversation(ctx, userID, name, unique)
}

func (s *Service) LeaveConversation(ctx context.Context, conversationID, userID int64) error {
	return s.repo.DeleteConversation(ctx, conversationID, userID)
}

// AddMembers adds users to a group and returns the resulting conversation.
//
// The caller must be an active participant of that group. Members who are
// already in are skipped rather than rejected, so a retried request succeeds
// quietly; users that do not exist are rejected up front instead of failing the
// insert on the foreign key.
func (s *Service) AddMembers(ctx context.Context, conversationID, callerUserID int64, memberIDs []int64) (*ConversationWithParticipants, error) {
	isMember, err := s.repo.groupMembership(ctx, conversationID, callerUserID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, apperrors.ErrNotParticipant
	}

	// Deduplicate and drop the caller (already a member; listing them is a
	// client bug, not something worth a 500 over).
	unique := make([]int64, 0, len(memberIDs))
	seen := make(map[int64]struct{}, len(memberIDs)+1)
	for _, id := range memberIDs {
		if id <= 0 || id == callerUserID {
			continue
		}
		if _, dup := seen[id]; dup {
			continue
		}
		seen[id] = struct{}{}
		unique = append(unique, id)
	}
	if len(unique) == 0 {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_MEMBERS", "No new members to add", 400, nil)
	}
	if len(unique) > MaxGroupMembers {
		return nil, apperrors.NewWithDetails(
			"INVALID_GROUP_MEMBERS", fmt.Sprintf("A group may have at most %d members", MaxGroupMembers), 400, nil)
	}

	for _, id := range unique {
		exists, err := s.repo.userExists(ctx, id)
		if err != nil {
			return nil, err
		}
		if !exists {
			return nil, apperrors.ErrUserNotFound
		}
	}

	if err := s.repo.AddMembers(ctx, conversationID, unique); err != nil {
		return nil, err
	}

	return s.repo.GetWithParticipants(ctx, conversationID, callerUserID)
}

// RemoveMember takes a member out of a group and returns the resulting
// conversation. Removing yourself is refused: use leave instead.
func (s *Service) RemoveMember(ctx context.Context, conversationID, callerUserID, targetUserID int64) (*ConversationWithParticipants, error) {
	if targetUserID == callerUserID {
		return nil, apperrors.NewWithDetails(
			"CANNOT_REMOVE_SELF", "Use leave to remove yourself from a group", 400, nil)
	}

	isMember, err := s.repo.groupMembership(ctx, conversationID, callerUserID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, apperrors.ErrNotParticipant
	}

	if err := s.repo.RemoveMember(ctx, conversationID, targetUserID); err != nil {
		return nil, err
	}

	return s.repo.GetWithParticipants(ctx, conversationID, callerUserID)
}

// RenameConversation retitles a group.
//
// Any active participant may rename. WhatsApp restricts this to admins; the
// schema has no roles, so until it does the wider rule is applied knowingly —
// a rename is low-risk (it changes a label, not membership or history) and the
// alternative is blocking the feature entirely.
func (s *Service) RenameConversation(ctx context.Context, conversationID, userID int64, name string) error {
	name = strings.TrimSpace(name)
	if name == "" {
		return apperrors.NewWithDetails(
			"INVALID_GROUP_NAME", "A group name is required", 400, nil)
	}
	if len(name) > MaxGroupNameLength {
		return apperrors.NewWithDetails(
			"INVALID_GROUP_NAME", fmt.Sprintf("Group name must be at most %d characters", MaxGroupNameLength), 400, nil)
	}
	return s.repo.RenameConversation(ctx, conversationID, userID, name)
}
