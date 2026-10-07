package conversations

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

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

func (s *Service) CreateDirectConversation(ctx context.Context, userID int64, otherUserPhone string) (*ConversationWithParticipants, error) {
	// Find other user by phone
	var otherUserID int64
	err := s.repo.db.QueryRowContext(ctx, `SELECT id FROM users WHERE phone_number = $1`, otherUserPhone).Scan(&otherUserID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, apperrors.ErrUserNotFound
		}
		return nil, fmt.Errorf("failed to find user: %w", err)
	}

	if otherUserID == userID {
		return nil, apperrors.NewWithDetails("CANNOT_CREATE_SELF_CONVERSATION", "Cannot create conversation with yourself", 400, nil)
	}

	return s.repo.CreateDirectConversation(ctx, userID, otherUserID)
}

func (s *Service) LeaveConversation(ctx context.Context, conversationID, userID int64) error {
	return s.repo.DeleteConversation(ctx, conversationID, userID)
}
