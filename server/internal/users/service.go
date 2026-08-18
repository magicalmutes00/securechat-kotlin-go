package users

import (
	"context"

	apperrors "github.com/securechat/server/pkg/errors"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetProfile(ctx context.Context, userID int64) (*User, error) {
	return s.repo.GetByID(ctx, userID)
}

func (s *Service) UpdateProfile(ctx context.Context, userID int64, displayName, username string) (*User, error) {
	if displayName == "" {
		return nil, apperrors.ErrInvalidRequest
	}
	if len(displayName) > 100 {
		return nil, apperrors.NewWithDetails("DISPLAY_NAME_TOO_LONG", "Display name too long", 400, nil)
	}
	if username != "" && len(username) > 50 {
		return nil, apperrors.NewWithDetails("USERNAME_TOO_LONG", "Username too long", 400, nil)
	}

	return s.repo.UpdateProfile(ctx, userID, displayName, username)
}

func (s *Service) UpdateAvatar(ctx context.Context, userID int64, imageID int64) (*User, error) {
	if imageID <= 0 {
		return nil, apperrors.ErrInvalidRequest
	}
	return s.repo.UpdateAvatar(ctx, userID, imageID)
}

func (s *Service) SearchUsers(ctx context.Context, query string, limit int) ([]*User, error) {
	if query == "" {
		return nil, apperrors.ErrInvalidRequest
	}
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	return s.repo.Search(ctx, query, limit)
}

func (s *Service) GetSettings(ctx context.Context, userID int64) (*UserSettings, error) {
	return s.repo.GetSettings(ctx, userID)
}

func (s *Service) UpdateSettings(ctx context.Context, userID int64, settings *UserSettings) error {
	settings.UserID = userID
	return s.repo.UpdateSettings(ctx, settings)
}