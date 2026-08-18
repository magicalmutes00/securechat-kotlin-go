package database

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/pressly/goose/v3"
	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

var DB *sql.DB

func New(cfg *config.Config) (*sql.DB, error) {
	dsn := cfg.GetDSN()

	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// Connection pool settings
	db.SetMaxOpenConns(cfg.MySQL.MaxOpenConns)
	db.SetMaxIdleConns(cfg.MySQL.MaxIdleConns)
	db.SetConnMaxLifetime(time.Duration(cfg.MySQL.ConnMaxLifetime) * time.Second)

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := db.PingContext(ctx); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	DB = db
	logger.Log.Info("Database connected",
		zap.String("host", cfg.MySQL.Host),
		zap.String("database", cfg.MySQL.Database),
	)

	return db, nil
}

func Migrate(db *sql.DB) error {
	logger.Log.Info("Running database migrations...")

	if err := goose.SetDialect("mysql"); err != nil {
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