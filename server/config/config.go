package config

import (
	"fmt"
	"net/url"
	"os"

	"github.com/spf13/viper"
)

type Config struct {
	Server   ServerConfig
	Database DatabaseConfig
	JWT      JWTConfig
	OTP      OTPConfig
	Google   GoogleConfig
	Cloudinary CloudinaryConfig
	Ollama   OllamaConfig
	Logger   LoggerConfig
}

type ServerConfig struct {
	Host string
	Port string
	Env  string
}

// DatabaseConfig holds a PostgreSQL connection. URL (e.g. a Neon pooled
// connection string) takes precedence; the discrete fields are a fallback for
// local development where a full URL is inconvenient to assemble.
type DatabaseConfig struct {
	URL             string
	Host            string
	Port            string
	Name            string
	User            string
	Password        string
	SSLMode         string
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime int
}

type JWTConfig struct {
	AccessSecret  string
	RefreshSecret string
	AccessTTL     int
	RefreshTTL    int
	Issuer        string
	Audience      string
}

type OTPConfig struct {
	Provider       string
	Length         int
	TTL            int
	MaxAttempts    int
	ResendCooldown int
	TwilioAccountSID string
	TwilioAuthToken  string
	TwilioFromNumber string
}

type GoogleConfig struct {
	ClientID string
}

type CloudinaryConfig struct {
	CloudName      string
	APIKey         string
	APISecret      string
	UploadFolder   string
	MaxImageMB     int
	MaxVideoMB     int
	MaxAudioMB     int
	MaxDocumentMB  int
}

type OllamaConfig struct {
	URL           string
	APIKey        string
	DefaultModel  string
	Timeout       int
}

type LoggerConfig struct {
	Level  string
	Format string
}

func Load() (*Config, error) {
	viper.AutomaticEnv()

	// Defaults
	viper.SetDefault("securechat_server_host", "0.0.0.0")
	viper.SetDefault("securechat_server_port", "8080")
	viper.SetDefault("securechat_environment", "development")
	// Full connection URL (Neon, Render, etc.) — preferred over discrete fields.
	viper.SetDefault("securechat_database_url", "")
	viper.SetDefault("securechat_postgres_host", "localhost")
	viper.SetDefault("securechat_postgres_port", "5432")
	viper.SetDefault("securechat_postgres_database", "securechat")
	viper.SetDefault("securechat_postgres_user", "securechat")
	// No default password: production must supply one (validated below).
	viper.SetDefault("securechat_postgres_password", "")
	viper.SetDefault("securechat_postgres_sslmode", "require")
	viper.SetDefault("securechat_database_max_open_conns", 25)
	viper.SetDefault("securechat_database_max_idle_conns", 5)
	viper.SetDefault("securechat_database_conn_max_lifetime", 300)
	viper.SetDefault("securechat_jwt_access_ttl", 900)
	viper.SetDefault("securechat_jwt_refresh_ttl", 2592000)
	viper.SetDefault("securechat_jwt_issuer", "securechat")
	viper.SetDefault("securechat_jwt_audience", "securechat-android")
	viper.SetDefault("securechat_otp_length", 6)
	viper.SetDefault("securechat_otp_ttl", 300)
	viper.SetDefault("securechat_otp_max_attempts", 5)
	viper.SetDefault("securechat_otp_resend_cooldown", 60)
	viper.SetDefault("securechat_cloudinary_upload_folder", "securechat")
	viper.SetDefault("securechat_cloudinary_max_image_mb", 20)
	viper.SetDefault("securechat_cloudinary_max_video_mb", 500)
	viper.SetDefault("securechat_cloudinary_max_audio_mb", 50)
	viper.SetDefault("securechat_cloudinary_max_document_mb", 100)
	viper.SetDefault("securechat_ollama_url", "http://localhost:11434")
	viper.SetDefault("securechat_ollama_default_model", "llama3.1:8b")
	viper.SetDefault("securechat_log_level", "info")
	viper.SetDefault("securechat_log_format", "json")

	if err := viper.ReadInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, err
		}
		// .env file not found, continue with env vars and defaults
	}

	var cfg Config

	cfg.Server.Host = viper.GetString("securechat_server_host")
	cfg.Server.Port = viper.GetString("securechat_server_port")
	cfg.Server.Env = viper.GetString("securechat_environment")

	// Render injects PORT automatically; fall back to it when unset
	if cfg.Server.Port == "" {
		if p := os.Getenv("PORT"); p != "" {
			cfg.Server.Port = p
		}
	}
	if cfg.Server.Port == "" {
		cfg.Server.Port = "8080"
	}

	cfg.Database.URL = viper.GetString("securechat_database_url")
	// Standard DATABASE_URL convention (Render/Neon) as a fallback.
	if cfg.Database.URL == "" {
		cfg.Database.URL = os.Getenv("DATABASE_URL")
	}
	cfg.Database.Host = viper.GetString("securechat_postgres_host")
	cfg.Database.Port = viper.GetString("securechat_postgres_port")
	cfg.Database.Name = viper.GetString("securechat_postgres_database")
	cfg.Database.User = viper.GetString("securechat_postgres_user")
	cfg.Database.Password = viper.GetString("securechat_postgres_password")
	cfg.Database.SSLMode = viper.GetString("securechat_postgres_sslmode")
	cfg.Database.MaxOpenConns = viper.GetInt("securechat_database_max_open_conns")
	cfg.Database.MaxIdleConns = viper.GetInt("securechat_database_max_idle_conns")
	cfg.Database.ConnMaxLifetime = viper.GetInt("securechat_database_conn_max_lifetime")

	cfg.JWT.AccessSecret = viper.GetString("securechat_jwt_access_secret")
	cfg.JWT.RefreshSecret = viper.GetString("securechat_jwt_refresh_secret")
	cfg.JWT.AccessTTL = viper.GetInt("securechat_jwt_access_ttl")
	cfg.JWT.RefreshTTL = viper.GetInt("securechat_jwt_refresh_ttl")
	cfg.JWT.Issuer = viper.GetString("securechat_jwt_issuer")
	cfg.JWT.Audience = viper.GetString("securechat_jwt_audience")

	cfg.OTP.Provider = viper.GetString("securechat_otp_provider")
	cfg.OTP.Length = viper.GetInt("securechat_otp_length")
	cfg.OTP.TTL = viper.GetInt("securechat_otp_ttl")
	cfg.OTP.MaxAttempts = viper.GetInt("securechat_otp_max_attempts")
	cfg.OTP.ResendCooldown = viper.GetInt("securechat_otp_resend_cooldown")
	cfg.OTP.TwilioAccountSID = viper.GetString("securechat_otp_twilio_account_sid")
	cfg.OTP.TwilioAuthToken = viper.GetString("securechat_otp_twilio_auth_token")
	cfg.OTP.TwilioFromNumber = viper.GetString("securechat_otp_twilio_from_number")

	cfg.Google.ClientID = viper.GetString("securechat_google_client_id")

	cfg.Cloudinary.CloudName = viper.GetString("securechat_cloudinary_cloud_name")
	cfg.Cloudinary.APIKey = viper.GetString("securechat_cloudinary_api_key")
	cfg.Cloudinary.APISecret = viper.GetString("securechat_cloudinary_api_secret")
	cfg.Cloudinary.UploadFolder = viper.GetString("securechat_cloudinary_upload_folder")
	cfg.Cloudinary.MaxImageMB = viper.GetInt("securechat_cloudinary_max_image_mb")
	cfg.Cloudinary.MaxVideoMB = viper.GetInt("securechat_cloudinary_max_video_mb")
	cfg.Cloudinary.MaxAudioMB = viper.GetInt("securechat_cloudinary_max_audio_mb")
	cfg.Cloudinary.MaxDocumentMB = viper.GetInt("securechat_cloudinary_max_document_mb")

	cfg.Ollama.URL = viper.GetString("securechat_ollama_url")
	cfg.Ollama.APIKey = viper.GetString("securechat_ollama_api_key")
	cfg.Ollama.DefaultModel = viper.GetString("securechat_ollama_default_model")
	cfg.Ollama.Timeout = viper.GetInt("securechat_ollama_timeout")

	cfg.Logger.Level = viper.GetString("securechat_log_level")
	cfg.Logger.Format = viper.GetString("securechat_log_format")

	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return &cfg, nil
}

// validate fails fast on configurations that would silently misbehave in
// production (ephemeral JWT keys, missing DB password, mock OTP).
func (c *Config) validate() error {
	if c.Server.Env == "production" {
		if c.JWT.AccessSecret == "" {
			return fmt.Errorf("SECURECHAT_JWT_ACCESS_SECRET is required in production")
		}
		if c.JWT.RefreshSecret == "" {
			return fmt.Errorf("SECURECHAT_JWT_REFRESH_SECRET is required in production")
		}
		if c.Database.URL == "" && c.Database.Password == "" {
			return fmt.Errorf("SECURECHAT_DATABASE_URL is required in production")
		}
		if c.OTP.Provider == "mock" {
			return fmt.Errorf("SECURECHAT_OTP_PROVIDER=mock is not allowed in production")
		}
	}
	return nil
}

// GetDSN returns a PostgreSQL connection URL. The configured URL (Neon-style
// pooled connection string) wins; otherwise one is assembled from the discrete
// fields so local development needs no URL juggling.
func (c *Config) GetDSN() string {
	if c.Database.URL != "" {
		return c.Database.URL
	}
	u := &url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(c.Database.User, c.Database.Password),
		Host:   c.Database.Host + ":" + c.Database.Port,
		Path:   "/" + c.Database.Name,
	}
	q := url.Values{}
	q.Set("sslmode", c.Database.SSLMode)
	u.RawQuery = q.Encode()
	return u.String()
}

func (c *Config) ServerAddr() string {
	return c.Server.Host + ":" + c.Server.Port
}