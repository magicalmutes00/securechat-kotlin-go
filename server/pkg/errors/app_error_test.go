package errors

import (
	"net/http"
	"testing"
)

func TestAppError(t *testing.T) {
	err := New("TEST_CODE", "Test message", http.StatusBadRequest)
	if err.Code != "TEST_CODE" {
		t.Fatalf("Code mismatch: %s", err.Code)
	}

	if err.Message != "Test message" {
		t.Fatalf("Message mismatch: %s", err.Message)
	}

	if err.Status != http.StatusBadRequest {
		t.Fatalf("Status mismatch: %d", err.Status)
	}

	// Test with details
	err2 := NewWithDetails("TEST_CODE2", "Test message 2", http.StatusInternalServerError, map[string]string{"field": "value"})
	if err2.Details == nil {
		t.Fatal("Details should not be nil")
	}

	if err2.Details.(map[string]string)["field"] != "value" {
		t.Fatal("Details value mismatch")
	}
}

func TestPredefinedErrors(t *testing.T) {
	tests := []struct {
		err        *AppError
		code       string
		statusCode int
	}{
		{ErrInvalidRequest, "INVALID_REQUEST", http.StatusBadRequest},
		{ErrUnauthorized, "UNAUTHORIZED", http.StatusUnauthorized},
		{ErrForbidden, "FORBIDDEN", http.StatusForbidden},
		{ErrNotFound, "NOT_FOUND", http.StatusNotFound},
		{ErrConflict, "CONFLICT", http.StatusConflict},
		{ErrRateLimited, "RATE_LIMITED", http.StatusTooManyRequests},
		{ErrInternalServer, "INTERNAL_SERVER_ERROR", http.StatusInternalServerError},
		{ErrServiceUnavailable, "SERVICE_UNAVAILABLE", http.StatusServiceUnavailable},
		{ErrInvalidOTP, "INVALID_OTP", http.StatusBadRequest},
		{ErrOTPExpired, "OTP_EXPIRED", http.StatusBadRequest},
		{ErrOTPMaxAttempts, "OTP_MAX_ATTEMPTS", http.StatusTooManyRequests},
		{ErrOTPResendCooldown, "OTP_RESEND_COOLDOWN", http.StatusTooManyRequests},
		{ErrInvalidCredentials, "INVALID_CREDENTIALS", http.StatusUnauthorized},
		{ErrTokenExpired, "TOKEN_EXPIRED", http.StatusUnauthorized},
		{ErrTokenInvalid, "TOKEN_INVALID", http.StatusUnauthorized},
		{ErrTokenRevoked, "TOKEN_REVOKED", http.StatusUnauthorized},
		{ErrRefreshFailed, "REFRESH_FAILED", http.StatusUnauthorized},
		{ErrUserNotFound, "USER_NOT_FOUND", http.StatusNotFound},
		{ErrUserExists, "USER_EXISTS", http.StatusConflict},
		{ErrUsernameTaken, "USERNAME_TAKEN", http.StatusConflict},
		{ErrConversationNotFound, "CONVERSATION_NOT_FOUND", http.StatusNotFound},
		{ErrNotParticipant, "NOT_PARTICIPANT", http.StatusForbidden},
		{ErrMessageNotFound, "MESSAGE_NOT_FOUND", http.StatusNotFound},
		{ErrMessageNotOwned, "MESSAGE_NOT_OWNED", http.StatusForbidden},
		{ErrMessageTooLong, "MESSAGE_TOO_LONG", http.StatusBadRequest},
		{ErrMediaNotFound, "MEDIA_NOT_FOUND", http.StatusNotFound},
		{ErrMediaTooLarge, "MEDIA_TOO_LARGE", http.StatusBadRequest},
		{ErrMediaInvalidType, "MEDIA_INVALID_TYPE", http.StatusBadRequest},
		{ErrUploadFailed, "UPLOAD_FAILED", http.StatusInternalServerError},
		{ErrCloudinaryError, "CLOUDINARY_ERROR", http.StatusInternalServerError},
		{ErrDeviceNotFound, "DEVICE_NOT_FOUND", http.StatusNotFound},
		{ErrWSAuthFailed, "WS_AUTH_FAILED", http.StatusUnauthorized},
		{ErrWSConnectionLimit, "WS_CONNECTION_LIMIT", http.StatusTooManyRequests},
	}

	for _, tt := range tests {
		if tt.err.Code != tt.code {
			t.Errorf("Error %s: code mismatch: got %s, want %s", tt.code, tt.err.Code, tt.code)
		}
		if tt.err.Status != tt.statusCode {
			t.Errorf("Error %s: status mismatch: got %d, want %d", tt.code, tt.err.Status, tt.statusCode)
		}
	}
}

func TestErrorResponse(t *testing.T) {
	err := New("TEST_CODE", "Test message", http.StatusBadRequest)
	response := NewErrorResponse(err)

	if response.Success {
		t.Fatal("Success should be false")
	}

	if response.Error == nil {
		t.Fatal("Error should not be nil")
	}

	if response.Error.Code != "TEST_CODE" {
		t.Fatalf("Error code mismatch: %s", response.Error.Code)
	}
}