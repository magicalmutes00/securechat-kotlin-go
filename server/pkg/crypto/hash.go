package crypto

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"

	"golang.org/x/crypto/bcrypt"
)

const (
	// Bcrypt cost factor (adjust based on performance requirements)
	BcryptCost = 12
	// OTP length
	OTPLength = 6
	// OTP charset (digits only)
	OTPCharset = "0123456789"
)

// HashPassword hashes a password using bcrypt
func HashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), BcryptCost)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// CheckPassword compares a password with a bcrypt hash
func CheckPassword(hash, password string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}

// GenerateOTP generates a cryptographically secure OTP
func GenerateOTP(length int) (string, error) {
	if length <= 0 {
		length = OTPLength
	}

	bytes := make([]byte, length)
	_, err := rand.Read(bytes)
	if err != nil {
		return "", err
	}

	otp := make([]byte, length)
	for i := 0; i < length; i++ {
		otp[i] = OTPCharset[bytes[i]%byte(len(OTPCharset))]
	}

	return string(otp), nil
}

// HashOTP hashes an OTP using bcrypt (store only the hash)
func HashOTP(otp string) (string, error) {
	return HashPassword(otp)
}

// VerifyOTP verifies an OTP against its bcrypt hash
func VerifyOTP(hashedOTP, otp string) bool {
	return CheckPassword(hashedOTP, otp)
}

// SHA256 computes SHA256 hash of data
func SHA256(data []byte) string {
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:])
}

// SHA256String computes SHA256 hash of a string
func SHA256String(s string) string {
	return SHA256([]byte(s))
}

// GenerateSecureToken generates a cryptographically secure random token
func GenerateSecureToken(length int) (string, error) {
	bytes := make([]byte, length)
	_, err := rand.Read(bytes)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(bytes), nil
}

// GenerateDeviceID generates a unique device identifier
func GenerateDeviceID() (string, error) {
	return GenerateSecureToken(32)
}

// ConstantTimeCompare performs constant-time comparison to prevent timing attacks
func ConstantTimeCompare(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	var result byte
	for i := 0; i < len(a); i++ {
		result |= a[i] ^ b[i]
	}
	return result == 0
}

// FormatOTP formats OTP for display (e.g., "123 456")
func FormatOTP(otp string) string {
	if len(otp) != 6 {
		return otp
	}
	return otp[:3] + " " + otp[3:]
}

// MaskPhoneNumber masks a phone number for logging (e.g., +15551234567 -> +1555***4567)
func MaskPhoneNumber(phone string) string {
	if len(phone) < 7 {
		return "***"
	}
	return phone[:4] + "***" + phone[len(phone)-4:]
}

// MaskEmail masks an email for logging
func MaskEmail(email string) string {
	at := -1
	for i := len(email) - 1; i >= 0; i-- {
		if email[i] == '@' {
			at = i
			break
		}
	}
	if at <= 1 {
		return "***"
	}
	return email[:1] + "***" + email[at-1:]
}

// FormatBytes formats byte count as human readable string
func FormatBytes(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(bytes)/float64(div), "KMGTPE"[exp])
}