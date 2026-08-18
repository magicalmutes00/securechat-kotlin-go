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
	"github.com/securechat/server/internal/users"
	"github.com/securechat/server/internal/websocket"
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
	app.Use(cors.New(cors.Config{
		AllowOrigins:     "*",
		AllowMethods:     "GET,POST,PUT,PATCH,DELETE,OPTIONS",
		AllowHeaders:     "Origin,Content-Type,Accept,Authorization",
		AllowCredentials: true,
		MaxAge:           86400,
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
	auth.RegisterRoutes(api, db, tokenManager, cfg)

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