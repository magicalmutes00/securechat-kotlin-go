package database

import (
	"context"
	"database/sql"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/pressly/goose/v3"
	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"

	_ "github.com/jackc/pgx/v5/stdlib"
)

var DB *sql.DB

func New(cfg *config.Config) (*sql.DB, error) {
	dsn := cfg.GetDSN()

	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// Connection pool settings. Keep these well below the Neon pooler's
	// connection limit; the pooled (-pooler) endpoint is transaction-mode.
	db.SetMaxOpenConns(cfg.Database.MaxOpenConns)
	db.SetMaxIdleConns(cfg.Database.MaxIdleConns)
	db.SetConnMaxLifetime(time.Duration(cfg.Database.ConnMaxLifetime) * time.Second)

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := db.PingContext(ctx); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	DB = db
	// Log a sanitized target. With URL-based config (Neon) the host/database
	// only exist inside the URL, and credentials must never reach the logs.
	logHost, logName := cfg.Database.Host, cfg.Database.Name
	if u, err := url.Parse(dsn); err == nil && u.Host != "" {
		logHost = u.Host
		logName = strings.TrimPrefix(u.Path, "/")
	}
	logger.Log.Info("Database connected",
		zap.String("host", logHost),
		zap.String("database", logName),
	)

	return db, nil
}

func Migrate(db *sql.DB) error {
	logger.Log.Info("Running database migrations...")

	if err := goose.SetDialect("postgres"); err != nil {
		return fmt.Errorf("failed to set dialect: %w", err)
	}

	// Migration files are in migrations/ directory
	if err := goose.Up(db, "migrations"); err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}

	logger.Log.Info("Database migrations completed")
	return nil
}

func Close() error {
	if DB != nil {
		return DB.Close()
	}
	return nil
}
