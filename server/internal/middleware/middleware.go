package middleware

import (
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/securechat/server/pkg/errors"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

// TokenValidator is an interface for validating access tokens
type TokenValidator interface {
	ValidateAccessToken(tokenString string) (TokenClaims, error)
}

// TokenValidatorFunc is a function type that implements TokenValidator
type TokenValidatorFunc func(tokenString string) (TokenClaims, error)

func (f TokenValidatorFunc) ValidateAccessToken(tokenString string) (TokenClaims, error) {
	return f(tokenString)
}

type TokenClaims struct {
	UserID    int64
	DeviceID  int64
	SessionID int64
}

func AuthMiddleware(tokenValidator TokenValidator) fiber.Handler {
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

		claims, err := tokenValidator.ValidateAccessToken(tokenString)
		if err != nil {
			logger.Log.Debug("Invalid access token", zap.Error(err))
			return c.Status(401).JSON(errors.NewErrorResponse(errors.ErrTokenInvalid))
		}

		c.Locals("user_id", claims.UserID)
		c.Locals("device_id", claims.DeviceID)
		c.Locals("session_id", claims.SessionID)

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