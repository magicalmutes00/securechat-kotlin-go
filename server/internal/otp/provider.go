package otp

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"strings"

	"github.com/securechat/server/pkg/crypto"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

// Provider defines the interface for OTP delivery providers (SMS, email, etc.)
type Provider interface {
	// Send sends an OTP to the given phone number
	Send(ctx context.Context, phoneNumber, otp string) error
	// Name returns the provider name for logging
	Name() string
}

// MockProvider is a development provider that logs OTPs instead of sending them.
//
// It is unreachable in production: config.validate() requires
// SECURECHAT_OTP_PROVIDER=twilio whenever SECURECHAT_ENVIRONMENT=production.
type MockProvider struct{}

func (m *MockProvider) Send(ctx context.Context, phoneNumber, otp string) error {
	// Previously this was a bare println that emitted the raw phone number and
	// the code. It now goes through the structured logger at Warn, with the
	// phone number masked, so a misconfigured non-production environment is
	// loud in logs without leaking a full number.
	logger.Log.Warn("[DEV] Mock OTP provider: no SMS sent",
		zap.String("phone", crypto.MaskPhoneNumber(phoneNumber)),
		zap.String("otp", otp),
		zap.String("hint", "set SECURECHAT_OTP_PROVIDER=twilio to deliver real SMS"),
	)
	return nil
}

func (m *MockProvider) Name() string {
	return "mock"
}

// TwilioProvider sends OTPs via Twilio SMS
type TwilioProvider struct {
	accountSID string
	authToken  string
	fromNumber string
	httpClient *http.Client
}

func NewTwilioProvider(accountSID, authToken, fromNumber string) *TwilioProvider {
	return &TwilioProvider{
		accountSID: accountSID,
		authToken:  authToken,
		fromNumber: fromNumber,
		httpClient: &http.Client{},
	}
}

func (t *TwilioProvider) Send(ctx context.Context, phoneNumber, otp string) error {
	apiURL := fmt.Sprintf("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", t.accountSID)

	form := url.Values{}
	form.Set("To", phoneNumber)
	form.Set("From", t.fromNumber)
	form.Set("Body", fmt.Sprintf("Your SecureChat verification code is %s", otp))

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, apiURL, strings.NewReader(form.Encode()))
	if err != nil {
		return fmt.Errorf("failed to create Twilio request: %w", err)
	}

	req.SetBasicAuth(t.accountSID, t.authToken)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := t.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send SMS via Twilio: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return nil
	}

	return fmt.Errorf("Twilio returned status %d", resp.StatusCode)
}

func (t *TwilioProvider) Name() string {
	return "twilio"
}