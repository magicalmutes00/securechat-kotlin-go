package logger

import (
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// Log is the process-wide logger.
//
// It starts as a no-op rather than nil so that code using it before Init() is
// safe. database.New and pkg/jwt both log during construction, and a nil here
// meant any caller other than cmd/server — a test, a CLI tool, anything importing
// these packages as a library — panicked with a nil pointer dereference on its
// first log line. Init() replaces this with a real configured logger.
var Log = zap.NewNop()

func Init(level, format string) error {
	var cfg zap.Config

	if format == "json" {
		cfg = zap.NewProductionConfig()
	} else {
		cfg = zap.NewDevelopmentConfig()
		cfg.EncoderConfig.EncodeLevel = zapcore.CapitalColorLevelEncoder
	}

	// Parse log level
	lvl, err := zapcore.ParseLevel(level)
	if err != nil {
		lvl = zapcore.InfoLevel
	}
	cfg.Level = zap.NewAtomicLevelAt(lvl)

	// Don't log caller in production for performance
	if format == "json" {
		cfg.DisableCaller = true
		cfg.DisableStacktrace = true
	}

	logger, err := cfg.Build()
	if err != nil {
		return err
	}

	Log = logger
	zap.ReplaceGlobals(logger)
	return nil
}

func Sync() error {
	if Log != nil {
		return Log.Sync()
	}
	return nil
}

// Named returns a new logger with the given name added to the logger's context.
// Before Init() has run this returns a no-op logger, which is why callers that
// may run in that state should use Named rather than reaching for Log directly.
func Named(name string) *zap.Logger {
	if Log != nil {
		return Log.Named(name)
	}
	return zap.NewNop()
}