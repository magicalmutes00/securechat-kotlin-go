package crypto

import (
	"testing"
)

func TestHashPassword(t *testing.T) {
	password := "testpassword123"
	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword failed: %v", err)
	}

	if hash == "" {
		t.Fatal("HashPassword returned empty string")
	}

	// Verify the hash works
	if !CheckPassword(hash, password) {
		t.Fatal("CheckPassword failed for correct password")
	}

	// Verify wrong password fails
	if CheckPassword(hash, "wrongpassword") {
		t.Fatal("CheckPassword succeeded for wrong password")
	}
}

func TestHashToken(t *testing.T) {
	// A realistic RS256 refresh JWT — several hundred bytes. bcrypt would
	// reject this ("password length exceeds 72 bytes"), which is exactly the
	// bug this digest replaces.
	token := "eyJhbGciOiJSUzI1NiIsImtpZCI6InJzYS0xIiwidHlwIjoiSldUIn0." +
		"eyJzdWIiOjEsImRldmljZV9pZCI6Miwic2Vzc2lvbl9pZCI6MywiZXhwIjoxNzkxNDAzNjU3fQ." +
		"MEUCIQDx8Yc3nYi8vK7p2mQ9wJ5tH0sV6lN4gB1cE7aZ3oFdAiEA9zK2mP4qR6sT8uV0wX1yZ2aB3cD4eF5gH6iJ7kL8oM9"

	if len(token) <= 72 {
		t.Fatalf("test token is only %d bytes; needs to exceed bcrypt's 72-byte cap", len(token))
	}

	hash := HashToken(token)
	if len(hash) != 64 {
		t.Fatalf("HashToken returned %d chars, expected 64 hex chars", len(hash))
	}
	if hash != HashToken(token) {
		t.Fatal("HashToken not deterministic")
	}
	if hash == HashToken(token+"x") {
		t.Fatal("HashToken collides for different tokens")
	}
	if !CheckToken(hash, token) {
		t.Fatal("CheckToken failed for correct token")
	}
	if CheckToken(hash, token+"x") {
		t.Fatal("CheckToken succeeded for wrong token")
	}
}

func TestGenerateOTP(t *testing.T) {
	otp, err := GenerateOTP(6)
	if err != nil {
		t.Fatalf("GenerateOTP failed: %v", err)
	}

	if len(otp) != 6 {
		t.Fatalf("OTP length is %d, expected 6", len(otp))
	}

	// Check all characters are digits
	for _, c := range otp {
		if c < '0' || c > '9' {
			t.Fatalf("OTP contains non-digit: %c", c)
		}
	}

	// Test uniqueness
	otp2, _ := GenerateOTP(6)
	if otp == otp2 {
		t.Log("OTP collision (unlikely but possible)")
	}
}

func TestHashOTP(t *testing.T) {
	otp := "123456"
	hash, err := HashOTP(otp)
	if err != nil {
		t.Fatalf("HashOTP failed: %v", err)
	}

	if !VerifyOTP(hash, otp) {
		t.Fatal("VerifyOTP failed for correct OTP")
	}

	if VerifyOTP(hash, "654321") {
		t.Fatal("VerifyOTP succeeded for wrong OTP")
	}
}

func TestSHA256(t *testing.T) {
	data := []byte("test data")
	hash := SHA256(data)

	if len(hash) != 64 {
		t.Fatalf("SHA256 length is %d, expected 64", len(hash))
	}

	// Test determinism
	hash2 := SHA256(data)
	if hash != hash2 {
		t.Fatal("SHA256 not deterministic")
	}

	// Test string version
	hash3 := SHA256String("test data")
	if hash != hash3 {
		t.Fatal("SHA256String differs from SHA256")
	}
}

func TestGenerateSecureToken(t *testing.T) {
	token, err := GenerateSecureToken(32)
	if err != nil {
		t.Fatalf("GenerateSecureToken failed: %v", err)
	}

	if len(token) != 64 { // 32 bytes = 64 hex chars
		t.Fatalf("Token length is %d, expected 64", len(token))
	}

	// Test uniqueness
	token2, _ := GenerateSecureToken(32)
	if token == token2 {
		t.Fatal("Tokens not unique")
	}
}

func TestGenerateDeviceID(t *testing.T) {
	id, err := GenerateDeviceID()
	if err != nil {
		t.Fatalf("GenerateDeviceID failed: %v", err)
	}

	if len(id) != 64 {
		t.Fatalf("DeviceID length is %d, expected 64", len(id))
	}
}

func TestConstantTimeCompare(t *testing.T) {
	if !ConstantTimeCompare("hello", "hello") {
		t.Fatal("ConstantTimeCompare failed for equal strings")
	}

	if ConstantTimeCompare("hello", "world") {
		t.Fatal("ConstantTimeCompare succeeded for different strings")
	}

	if ConstantTimeCompare("hello", "hello!") {
		t.Fatal("ConstantTimeCompare succeeded for different length strings")
	}
}

func TestMaskPhoneNumber(t *testing.T) {
	masked := MaskPhoneNumber("+15551234567")
	if masked != "+155***4567" {
		t.Fatalf("MaskPhoneNumber returned %s, expected +155***4567", masked)
	}

	// Short number
	masked2 := MaskPhoneNumber("123")
	if masked2 != "***" {
		t.Fatalf("MaskPhoneNumber returned %s for short number", masked2)
	}
}

func TestFormatBytes(t *testing.T) {
	tests := []struct {
		input    int64
		expected string
	}{
		{500, "500 B"},
		{1024, "1.0 KB"},
		{1536, "1.5 KB"},
		{1048576, "1.0 MB"},
		{1073741824, "1.0 GB"},
	}

	for _, tt := range tests {
		result := FormatBytes(tt.input)
		if result != tt.expected {
			t.Fatalf("FormatBytes(%d) = %s, expected %s", tt.input, result, tt.expected)
		}
	}
}