package errors

import (
	"net/http"
)

type AppError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details any    `json:"details,omitempty"`
	Status  int    `json:"-"`
}

func (e *AppError) Error() string {
	return e.Message
}

func New(code, message string, status int) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  status,
	}
}

func NewWithDetails(code, message string, status int, details any) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Details: details,
		Status:  status,
	}
}

// Predefined errors
var (
	ErrInvalidRequest     = New("INVALID_REQUEST", "Invalid request payload", http.StatusBadRequest)
	ErrUnauthorized       = New("UNAUTHORIZED", "Authentication required", http.StatusUnauthorized)
	ErrForbidden          = New("FORBIDDEN", "Access denied", http.StatusForbidden)
	ErrNotFound           = New("NOT_FOUND", "Resource not found", http.StatusNotFound)
	ErrConflict           = New("CONFLICT", "Resource conflict", http.StatusConflict)
	ErrRateLimited        = New("RATE_LIMITED", "Too many requests", http.StatusTooManyRequests)
	ErrInternalServer     = New("INTERNAL_SERVER_ERROR", "Internal server error", http.StatusInternalServerError)
	ErrServiceUnavailable = New("SERVICE_UNAVAILABLE", "Service temporarily unavailable", http.StatusServiceUnavailable)

	// Auth errors
	ErrInvalidOTP         = New("INVALID_OTP", "The OTP is invalid or expired", http.StatusBadRequest)
	ErrOTPExpired         = New("OTP_EXPIRED", "The OTP has expired", http.StatusBadRequest)
	ErrOTPMaxAttempts     = New("OTP_MAX_ATTEMPTS", "Maximum OTP attempts exceeded", http.StatusTooManyRequests)
	ErrOTPResendCooldown  = New("OTP_RESEND_COOLDOWN", "Please wait before requesting another OTP", http.StatusTooManyRequests)
	ErrInvalidCredentials = New("INVALID_CREDENTIALS", "Invalid credentials", http.StatusUnauthorized)
	ErrTokenExpired       = New("TOKEN_EXPIRED", "Token has expired", http.StatusUnauthorized)
	ErrTokenInvalid       = New("TOKEN_INVALID", "Token is invalid", http.StatusUnauthorized)
	ErrTokenRevoked       = New("TOKEN_REVOKED", "Token has been revoked", http.StatusUnauthorized)
	ErrRefreshFailed      = New("REFRESH_FAILED", "Failed to refresh token", http.StatusUnauthorized)

	// User errors
	ErrUserNotFound       = New("USER_NOT_FOUND", "User not found", http.StatusNotFound)
	ErrUserExists         = New("USER_EXISTS", "User already exists", http.StatusConflict)
	ErrUsernameTaken      = New("USERNAME_TAKEN", "Username is already taken", http.StatusConflict)

	// Conversation errors
	ErrConversationNotFound = New("CONVERSATION_NOT_FOUND", "Conversation not found", http.StatusNotFound)
	ErrNotParticipant       = New("NOT_PARTICIPANT", "Not a participant in this conversation", http.StatusForbidden)

	// Message errors
	ErrMessageNotFound    = New("MESSAGE_NOT_FOUND", "Message not found", http.StatusNotFound)
	ErrMessageNotOwned    = New("MESSAGE_NOT_OWNED", "Cannot modify message owned by another user", http.StatusForbidden)
	ErrMessageTooLong     = New("MESSAGE_TOO_LONG", "Message exceeds maximum length", http.StatusBadRequest)

	// Media errors
	ErrMediaNotFound      = New("MEDIA_NOT_FOUND", "Media not found", http.StatusNotFound)
	ErrMediaTooLarge      = New("MEDIA_TOO_LARGE", "File exceeds maximum allowed size", http.StatusBadRequest)
	ErrMediaInvalidType   = New("MEDIA_INVALID_TYPE", "Unsupported media type", http.StatusBadRequest)
	ErrUploadFailed       = New("UPLOAD_FAILED", "Failed to upload media", http.StatusInternalServerError)
	ErrCloudinaryError    = New("CLOUDINARY_ERROR", "Cloudinary service error", http.StatusInternalServerError)

	// Device errors
	ErrDeviceNotFound     = New("DEVICE_NOT_FOUND", "Device not found", http.StatusNotFound)

	// WebSocket errors
	ErrWSAuthFailed       = New("WS_AUTH_FAILED", "WebSocket authentication failed", http.StatusUnauthorized)
	ErrWSConnectionLimit  = New("WS_CONNECTION_LIMIT", "Connection limit exceeded", http.StatusTooManyRequests)
)

// ErrorResponse is the standard API error response
type ErrorResponse struct {
	Success bool      `json:"success"`
	Error   *AppError `json:"error"`
}

func NewErrorResponse(err *AppError) ErrorResponse {
	return ErrorResponse{
		Success: false,
		Error:   err,
	}
}