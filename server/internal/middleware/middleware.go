package middleware

import (
	"context"
	"database/sql"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

// ClaimsValidator is implemented by token managers that can verify an access
// token and return its principal claims. Kept as an interface so middleware
// does not import the auth package (auth imports middleware).
type ClaimsValidator interface {
	ValidateAccessClaims(tokenString string) (userID, deviceID, sessionID int64, err error)
}

// SessionChecker reports whether a session is still active. A nil checker
// skips the session lookup (JWT signature is still verified).
type SessionChecker interface {
	IsSessionActive(ctx context.Context, userID, sessionID int64) (bool, error)
}

// DBSessionChecker validates sessions against the database so that logout and
// device revocation take effect immediately instead of at access-token expiry.
type DBSessionChecker struct {
	db *sql.DB
}

func NewDBSessionChecker(db *sql.DB) *DBSessionChecker {
	return &DBSessionChecker{db: db}
}

func (c *DBSessionChecker) IsSessionActive(ctx context.Context, userID, sessionID int64) (bool, error) {
	var isActive bool
	err := c.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM sessions
			WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL AND expires_at > NOW()
		)
	`, sessionID, userID).Scan(&isActive)
	return isActive, err
}

// JWTAuth returns middleware that verifies the Bearer access token and, when
// a checker is supplied, that the token's session has not been revoked.
func JWTAuth(validator ClaimsValidator, checker SessionChecker) fiber.Handler {
	return func(c *fiber.Ctx) error {
		authHeader := c.Get("Authorization")
		if authHeader == "" {
			return c.Status(401).JSON(errors.NewErrorResponse(errors.ErrUnauthorized))
		}

		const prefix = "Bearer "
		if len(authHeader) < len(prefix) || authHeader[:len(prefix)] != prefix {
			return c.Status(401).JSON(errors.NewErrorResponse(errors.ErrUnauthorized))
		}

		tokenString := authHeader[len(prefix):]

		userID, deviceID, sessionID, err := validator.ValidateAccessClaims(tokenString)
		if err != nil {
			logger.Log.Debug("Invalid access token", zap.Error(err))
			return c.Status(401).JSON(errors.NewErrorResponse(errors.ErrTokenInvalid))
		}

		if checker != nil {
			active, err := checker.IsSessionActive(c.Context(), userID, sessionID)
			if err != nil {
				logger.Log.Error("Session check failed", zap.Error(err))
				return c.Status(500).JSON(errors.NewErrorResponse(errors.ErrInternalServer))
			}
			if !active {
				return c.Status(401).JSON(errors.NewErrorResponse(errors.ErrTokenRevoked))
			}
		}

		c.Locals("user_id", userID)
		c.Locals("device_id", deviceID)
		c.Locals("session_id", sessionID)

		return c.Next()
	}
}

func ErrorHandler(c *fiber.Ctx, err error) error {
	code := fiber.StatusInternalServerError
	message := "Internal server error"

	if e, ok := err.(*fiber.Error); ok {
		code = e.Code
		message = e.Message
	}

	logger.Log.Error("Request error",
		zap.String("path", c.Path()),
		zap.String("method", c.Method()),
		zap.Int("status", code),
		zap.Error(err),
	)

	return c.Status(code).JSON(errors.NewErrorResponse(&errors.AppError{
		Code:    "REQUEST_ERROR",
		Message: message,
		Status:  code,
	}))
}

func Logger() fiber.Handler {
	return func(c *fiber.Ctx) error {
		start := time.Now()

		err := c.Next()

		logger.Log.Info("HTTP request",
			zap.String("method", c.Method()),
			zap.String("path", c.Path()),
			zap.Int("status", c.Response().StatusCode()),
			zap.Duration("latency", time.Since(start)),
			zap.String("ip", c.IP()),
		)

		return err
	}
}
