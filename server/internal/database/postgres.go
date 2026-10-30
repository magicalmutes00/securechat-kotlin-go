package database

import (
	"context"
	"database/sql"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
	"github.com/securechat/server/config"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

var DB *sql.DB

// Connect opens a pool against dsn, configured for the endpoints this project
// actually runs against, and pings it.
func Connect(ctx context.Context, dsn string) (*sql.DB, error) {
	// The pooled endpoint (Neon, "-pooler") runs PgBouncer in transaction mode,
	// which cannot track server-side prepared statements across connections.
	// pgx's default mode prepares and caches statements per client connection,
	// so the second run of any identical query fails with "prepared statement
	// name is already in use".
	//
	// QueryExecModeExec still uses the extended protocol — parameters keep their
	// types and are never string-interpolated — but it never creates a named
	// prepared statement, which is what makes it safe behind PgBouncer. The
	// statement and description caches are cleared to match. A direct,
	// non-pooled connection pays only a small per-query cost.
	pgxCfg, err := pgx.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to parse database URL: %w", err)
	}
	pgxCfg.DefaultQueryExecMode = pgx.QueryExecModeExec
	pgxCfg.StatementCacheCapacity = 0
	pgxCfg.DescriptionCacheCapacity = 0

	db, err := sql.Open("pgx", stdlib.RegisterConnConfig(pgxCfg))
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if err := db.PingContext(pingCtx); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	return db, nil
}

func New(cfg *config.Config) (*sql.DB, error) {
	db, err := Connect(context.Background(), cfg.GetDSN())
	if err != nil {
		return nil, err
	}

	// Connection pool settings. Keep these well below the Neon pooler's
	// connection limit; the pooled (-pooler) endpoint is transaction-mode.
	db.SetMaxOpenConns(cfg.Database.MaxOpenConns)
	db.SetMaxIdleConns(cfg.Database.MaxIdleConns)
	db.SetConnMaxLifetime(time.Duration(cfg.Database.ConnMaxLifetime) * time.Second)

	DB = db
	// Log a sanitized target. With URL-based config (Neon) the host/database
	// only exist inside the URL, and credentials must never reach the logs.
	logHost, logName := cfg.Database.Host, cfg.Database.Name
	if u, err := url.Parse(cfg.GetDSN()); err == nil && u.Host != "" {
		logHost = u.Host
		logName = strings.TrimPrefix(u.Path, "/")
	}
	logger.Log.Info("Database connected",
		zap.String("host", logHost),
		zap.String("database", logName),
	)

	return db, nil
}

// MigrateDir applies the migrations in dir. Separated from Migrate so tests can
// point at a migrations directory relative to their own package.
func MigrateDir(db *sql.DB, dir string) error {
	if err := goose.SetDialect("postgres"); err != nil {
		return fmt.Errorf("failed to set dialect: %w", err)
	}
	if err := goose.Up(db, dir); err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}
	return nil
}

func Migrate(db *sql.DB) error {
	logger.Log.Info("Running database migrations...")
	if err := MigrateDir(db, "migrations"); err != nil {
		return err
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
