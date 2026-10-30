package config

import (
	"strings"
	"testing"
)

// baseConfig returns a Config that passes production validation, so each test
// can break exactly one field and assert on that field alone.
func validProductionConfig() *Config {
	return &Config{
		Server: ServerConfig{Env: "production"},
		Database: DatabaseConfig{
			URL: "postgresql://user:pass@example.neon.tech/neondb?sslmode=require",
		},
		JWT: JWTConfig{
			AccessSecret:  "access-key-material",
			RefreshSecret: "refresh-key-material",
		},
		OTP: OTPConfig{
			Provider:         "twilio",
			MaxAttempts:      5,
			TwilioAccountSID: "ACxxxxxxxx",
			TwilioAuthToken:  "token",
			TwilioFromNumber: "+15550000000",
		},
		Cloudinary: CloudinaryConfig{
			CloudName: "cloud",
			APIKey:    "key",
			APISecret: "secret",
		},
	}
}

// TestValidateProductionRejectsMockFallback is the regression test for an unset
// SECURECHAT_OTP_PROVIDER silently selecting the mock provider in production,
// which logged every OTP to stdout and never delivered it.
func TestValidateProductionRejectsMockFallback(t *testing.T) {
	// An unset variable resolves to the "mock" default from Load(); assert that
	// value specifically, because "" is what previously slipped through.
	for _, provider := range []string{"mock", "", "Mock", "twilio ", "sms", "twilio"} {
		cfg := validProductionConfig()
		cfg.OTP.Provider = provider

		err := cfg.validate()

		if provider == "twilio" {
			if err != nil {
				t.Fatalf("production config with the twilio provider was rejected: %v", err)
			}
			continue
		}

		if err == nil {
			t.Fatalf("production config with OTP provider %q was accepted", provider)
		}
		if !strings.Contains(err.Error(), "OTP_PROVIDER") {
			t.Fatalf("provider %q was rejected for the wrong reason: %v", provider, err)
		}
	}
}

// TestValidateRejectsUnknownProviderInAnyEnvironment covers a typo'd provider,
// which used to fall through to the mock branch in main.go rather than failing.
func TestValidateRejectsUnknownProviderInAnyEnvironment(t *testing.T) {
	cfg := validProductionConfig()
	cfg.Server.Env = "development"
	cfg.OTP.Provider = "twillio"

	err := cfg.validate()
	if err == nil {
		t.Fatal("a misspelled OTP provider was accepted in development")
	}
	if !strings.Contains(err.Error(), "OTP_PROVIDER") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// TestValidateDevelopmentAllowsMock confirms local development still works.
func TestValidateDevelopmentAllowsMock(t *testing.T) {
	cfg := &Config{
		Server: ServerConfig{Env: "development"},
		OTP:    OTPConfig{Provider: "mock"},
	}
	if err := cfg.validate(); err != nil {
		t.Fatalf("development config with the mock provider was rejected: %v", err)
	}
}

// TestValidateProductionRequiresTwilioCredentials catches a provider set to
// twilio with no credentials, which would otherwise fail at send time instead
// of at boot.
func TestValidateProductionRequiresTwilioCredentials(t *testing.T) {
	cfg := validProductionConfig()
	cfg.OTP.TwilioAuthToken = ""

	err := cfg.validate()
	if err == nil {
		t.Fatal("production config with no Twilio auth token was accepted")
	}
	if !strings.Contains(err.Error(), "TWILIO_AUTH_TOKEN") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// TestValidateProductionRequiresSecrets keeps the fail-fast behaviour for the
// JWT signing keys and database URL.
func TestValidateProductionRequiresSecrets(t *testing.T) {
	cases := []struct {
		name   string
		mutate func(*Config)
		want   string
	}{
		{"missing access secret", func(c *Config) { c.JWT.AccessSecret = "" }, "JWT_ACCESS_SECRET"},
		{"missing refresh secret", func(c *Config) { c.JWT.RefreshSecret = "" }, "JWT_REFRESH_SECRET"},
		{"missing database url", func(c *Config) {
			c.Database.URL = ""
			c.Database.Password = ""
		}, "DATABASE_URL"},
		{"missing cloudinary", func(c *Config) { c.Cloudinary.APISecret = "" }, "CLOUDINARY"},
		{"zero attempt cap", func(c *Config) { c.OTP.MaxAttempts = 0 }, "MAX_ATTEMPTS"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := validProductionConfig()
			tc.mutate(cfg)

			err := cfg.validate()
			if err == nil {
				t.Fatalf("production config was accepted with %s", tc.name)
			}
			if !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("error for %s did not mention %s: %v", tc.name, tc.want, err)
			}
		})
	}
}

// TestGetDSNPrefersURL documents that a full Neon-style URL wins over the
// discrete fields, which is what production relies on.
func TestGetDSNPrefersURL(t *testing.T) {
	cfg := &Config{
		Database: DatabaseConfig{
			URL:  "postgresql://neon.example/db?sslmode=require",
			Host: "ignored",
		},
	}
	if got := cfg.GetDSN(); got != cfg.Database.URL {
		t.Fatalf("GetDSN() = %q, want the configured URL", got)
	}
}

// TestGetDSNAssemblesFromDiscreteFields covers the local-development fallback.
func TestGetDSNAssemblesFromDiscreteFields(t *testing.T) {
	cfg := &Config{
		Database: DatabaseConfig{
			Host:     "localhost",
			Port:     "5432",
			Name:     "securechat",
			User:     "securechat",
			Password: "pw",
			SSLMode:  "disable",
		},
	}

	got := cfg.GetDSN()
	for _, want := range []string{"postgres", "localhost:5432", "securechat", "sslmode=disable"} {
		if !strings.Contains(got, want) {
			t.Errorf("GetDSN() = %q, expected it to contain %q", got, want)
		}
	}
}
