package otp

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"strings"
)

// Provider defines the interface for OTP delivery providers (SMS, email, etc.)
type Provider interface {
	// Send sends an OTP to the given phone number
	Send(ctx context.Context, phoneNumber, otp string) error
	// Name returns the provider name for logging
	Name() string
}

// MockProvider is a development provider that logs OTPs instead of sending them
type MockProvider struct{}

func (m *MockProvider) Send(ctx context.Context, phoneNumber, otp string) error {
	// In development, just log the OTP (in production, NEVER log OTPs)
	// Using a structured logger would be better
	println("[DEV MOCK OTP] To:", phoneNumber, "Code:", otp)
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