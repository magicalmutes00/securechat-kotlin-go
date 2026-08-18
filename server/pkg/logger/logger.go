package logger

import (
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

var Log *zap.Logger

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

// Named returns a new logger with the given name added to the logger's context
func Named(name string) *zap.Logger {
	if Log != nil {
		return Log.Named(name)
	}
	// Fallback to noop logger
	return zap.NewNop()
}