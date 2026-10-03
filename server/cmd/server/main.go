package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"github.com/gofiber/fiber/v2/middleware/recover"
	fiberwebsocket "github.com/gofiber/websocket/v2"
	"github.com/joho/godotenv"
	"github.com/securechat/server/config"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/conversations"
	"github.com/securechat/server/internal/database"
	"github.com/securechat/server/internal/devices"
	"github.com/securechat/server/internal/media"
	"github.com/securechat/server/internal/messages"
	"github.com/securechat/server/internal/middleware"
	"github.com/securechat/server/internal/otp"
	"github.com/securechat/server/internal/users"
	"github.com/securechat/server/internal/websocket"
	errorsPkg "github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

func main() {
	// Load .env file if present (local dev); Render uses real env vars
	_ = godotenv.Load()

	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Initialize logger
	if err := logger.Init(cfg.Logger.Level, cfg.Logger.Format); err != nil {
		log.Fatalf("Failed to initialize logger: %v", err)
	}
	defer logger.Sync()

	log := logger.Named("main")
	log.Info("Starting SecureChat server",
		zap.String("env", cfg.Server.Env),
		zap.String("addr", cfg.ServerAddr()),
	)

	// Initialize database
	db, err := database.New(cfg)
	if err != nil {
		log.Fatal("Failed to connect to database", zap.Error(err))
	}
	defer db.Close()

	// Run migrations
	if err := database.Migrate(db); err != nil {
		log.Fatal("Failed to run migrations", zap.Error(err))
	}

	// Initialize token manager
	tokenManager, err := auth.NewTokenManager(cfg)
	if err != nil {
		log.Fatal("Failed to create token manager", zap.Error(err))
	}

	// Initialize OTP provider and service (shared with the cleanup loop)
	var otpProvider otp.Provider
	switch cfg.OTP.Provider {
	case "twilio":
		otpProvider = otp.NewTwilioProvider(cfg.OTP.TwilioAccountSID, cfg.OTP.TwilioAuthToken, cfg.OTP.TwilioFromNumber)
	default:
		otpProvider = &otp.MockProvider{}
	}
	otpService := otp.NewService(db, otpProvider, cfg)

	// Periodically purge expired OTP rows
	go func() {
		ticker := time.NewTicker(time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			if n, err := otpService.CleanupExpired(ctx); err != nil {
				log.Error("OTP cleanup failed", zap.Error(err))
			} else if n > 0 {
				log.Info("OTP cleanup", zap.Int64("deleted", n))
			}
			cancel()
		}
	}()

	// Create Fiber app
	app := fiber.New(fiber.Config{
		ErrorHandler: middleware.ErrorHandler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	})

	// Global middleware
	app.Use(recover.New())
	app.Use(middleware.Logger())

	// The Android clients are not browsers, so CORS is irrelevant to them; it
	// exists for any web client. "*" combined with AllowCredentials is an
	// invalid (and unsafe) combination, so credentials are only allowed when
	// explicit origins are configured via SECURECHAT_CORS_ALLOWED_ORIGINS.
	allowedOrigins := os.Getenv("SECURECHAT_CORS_ALLOWED_ORIGINS")
	if allowedOrigins == "" {
		allowedOrigins = "*"
	}
	app.Use(cors.New(cors.Config{
		AllowOrigins:     allowedOrigins,
		AllowMethods:     "GET,POST,PUT,PATCH,DELETE,OPTIONS",
		AllowHeaders:     "Origin,Content-Type,Accept,Authorization",
		AllowCredentials: allowedOrigins != "*",
		MaxAge:           86400,
	}))

	// Per-IP request rate limiting on the API (generous ceiling; the stricter
	// per-number OTP cooldown lives in the OTP service).
	app.Use("/api/v1", limiter.New(limiter.Config{
		Max:        300,
		Expiration: time.Minute,
		KeyGenerator: func(c *fiber.Ctx) string {
			return c.IP()
		},
		LimitReached: func(c *fiber.Ctx) error {
			return c.Status(429).JSON(errorsPkg.NewErrorResponse(errorsPkg.ErrRateLimited))
		},
	}))

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"status":    "ok",
			"service":   "securechat",
			"version":   "1.0.0",
			"timestamp": time.Now().Unix(),
		})
	})

	// API routes
	api := app.Group("/api/v1")

	// Auth routes
	auth.RegisterRoutes(api, db, tokenManager, cfg, otpService)

	// Users routes
	users.RegisterRoutes(api, db, tokenManager, cfg)

	// Conversations routes
	conversations.RegisterRoutes(api, db, tokenManager)

	// Messages routes
	messages.RegisterRoutes(api, db, tokenManager)

	// Media routes
	media.RegisterRoutes(api, db, tokenManager, cfg)

	// Devices routes
	devices.RegisterRoutes(api, db, tokenManager)

	// WebSocket
	app.Get("/ws", fiberwebsocket.New(websocket.WebSocketHandler(db, tokenManager)))

	// Start server
	go func() {
		addr := cfg.ServerAddr()
		log.Info("Server listening", zap.String("addr", addr))
		if err := app.Listen(addr); err != nil && err != http.ErrServerClosed {
			log.Fatal("Server failed", zap.Error(err))
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info("Shutting down server...")
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := app.ShutdownWithContext(ctx); err != nil {
		log.Error("Server forced to shutdown", zap.Error(err))
	}

	log.Info("Server exited")
}