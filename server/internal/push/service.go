package push

import (
	"context"
	"encoding/base64"
	"fmt"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
	"google.golang.org/api/option"
)

// Service sends push notifications via Firebase Cloud Messaging (FCM v1).
// Pushes are never sent with message plaintext: the payload carries only a
// generic alert ("New message") so the design stays E2EE-compatible.
type Service struct {
	repo    *Repository
	client  *messaging.Client
	cfg     *config.FcmConfig
	enabled bool
}

func NewService(repo *Repository, cfg *config.FcmConfig) (*Service, error) {
	s := &Service{
		repo:  repo,
		cfg:   cfg,
		// Push is enabled only when both a project ID and service-account
		// credentials are configured. Without them, Notify* is a no-op so the
		// rest of the system degrades gracefully.
		enabled: cfg.ProjectID != "" && cfg.Credentials != "",
	}
	if !s.enabled {
		return s, nil
	}

	decoded, err := base64.StdEncoding.DecodeString(cfg.Credentials)
	if err != nil {
		return nil, fmt.Errorf("failed to decode FCM service account credentials: %w", err)
	}

	app, err := firebase.NewApp(context.Background(), &firebase.Config{
		ProjectID: cfg.ProjectID,
	}, option.WithCredentialsJSON(decoded))
	if err != nil {
		return nil, fmt.Errorf("failed to initialize Firebase app: %w", err)
	}

	client, err := app.Messaging(context.Background())
	if err != nil {
		return nil, fmt.Errorf("failed to create FCM client: %w", err)
	}
	s.client = client
	return s, nil
}

// Enabled reports whether push delivery is configured. When disabled (e.g. no
// FCM credentials in the environment), the service is a no-op so the rest of
// the system degrades gracefully.
func (s *Service) Enabled() bool {
	return s.enabled && s.client != nil
}

// NotifyDevices sends a push notification to the active FCM tokens of the
// given device IDs. It is a best-effort operation: failures are logged but
// never returned, so a push problem never blocks message delivery.
func (s *Service) NotifyDevices(ctx context.Context, deviceIDs []int64, title, body string, data map[string]string) {
	if !s.Enabled() || len(deviceIDs) == 0 {
		return
	}

	tokens, err := s.repo.ActiveTokensForDevices(ctx, deviceIDs)
	if err != nil {
		logger.Log.Error("Failed to load push tokens", zap.Int64s("device_ids", deviceIDs), zap.Error(err))
		return
	}
	if len(tokens) == 0 {
		return
	}

	for _, token := range tokens {
		msg := &messaging.Message{
			Token:   token.FcmToken,
			Notification: &messaging.Notification{
				Title: title,
				Body:  body,
			},
			Data: data,
			Android: &messaging.AndroidConfig{
				Priority: "high",
				Notification: &messaging.AndroidNotification{
					ChannelID: "securechat_messages",
				},
			},
		}
		if _, err := s.client.Send(ctx, msg); err != nil {
			logger.Log.Error("Failed to send FCM push",
				zap.Int64("device_id", token.DeviceID),
				zap.String("fcm_token_prefix", prefix(token.FcmToken)),
				zap.Error(err))
		}
	}
}

// NotifyUser sends a push notification to all active tokens of a user.
func (s *Service) NotifyUser(ctx context.Context, userID int64, title, body string, data map[string]string) {
	if !s.Enabled() {
		return
	}

	tokens, err := s.repo.ActiveTokensForUser(ctx, userID)
	if err != nil {
		logger.Log.Error("Failed to load push tokens for user", zap.Int64("user_id", userID), zap.Error(err))
		return
	}
	for _, token := range tokens {
		msg := &messaging.Message{
			Token:   token.FcmToken,
			Notification: &messaging.Notification{
				Title: title,
				Body:  body,
			},
			Data: data,
			Android: &messaging.AndroidConfig{
				Priority: "high",
				Notification: &messaging.AndroidNotification{
					ChannelID: "securechat_messages",
				},
			},
		}
		if _, err := s.client.Send(ctx, msg); err != nil {
			logger.Log.Error("Failed to send FCM push",
				zap.Int64("user_id", userID),
				zap.Int64("device_id", token.DeviceID),
				zap.String("fcm_token_prefix", prefix(token.FcmToken)),
				zap.Error(err))
		}
	}
}

// RegisterToken associates an FCM token with a user and device.
func (s *Service) RegisterToken(ctx context.Context, userID, deviceID int64, fcmToken, platform string) error {
	if !s.enabled {
		return fmt.Errorf("push notifications not configured")
	}
	return s.repo.Upsert(ctx, userID, deviceID, fcmToken, platform)
}

// UnregisterToken deactivates an FCM token.
func (s *Service) UnregisterToken(ctx context.Context, fcmToken string) error {
	if !s.enabled {
		return fmt.Errorf("push notifications not configured")
	}
	return s.repo.Deactivate(ctx, fcmToken)
}

// UnregisterUser deactivates all tokens for a user.
func (s *Service) UnregisterUser(ctx context.Context, userID int64) error {
	if !s.enabled {
		return fmt.Errorf("push notifications not configured")
	}
	return s.repo.DeactivateByUser(ctx, userID)
}

// prefix returns the first 12 characters of a token for logging (never the
// full token, which is secret material).
func prefix(token string) string {
	if len(token) > 12 {
		return token[:12] + "..."
	}
	return token
}
