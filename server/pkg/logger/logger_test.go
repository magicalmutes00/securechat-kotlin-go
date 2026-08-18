package logger

import (
	"testing"
)

func TestLoggerInit(t *testing.T) {
	// Test development logger
	err := Init("debug", "console")
	if err != nil {
		t.Fatalf("Init failed: %v", err)
	}

	if Log == nil {
		t.Fatal("Log should not be nil")
	}

	// Test production logger
	err = Init("info", "json")
	if err != nil {
		t.Fatalf("Init failed: %v", err)
	}

	// Test invalid level falls back to info
	err = Init("invalid", "json")
	if err != nil {
		t.Fatalf("Init should not fail for invalid level: %v", err)
	}

	// Clean up
	Sync()
}

func TestNamedLogger(t *testing.T) {
	err := Init("debug", "console")
	if err != nil {
		t.Fatalf("Init failed: %v", err)
	}

	named := Named("test-component")
	if named == nil {
		t.Fatal("Named logger should not be nil")
	}

	// Test it doesn't panic
	named.Info("test message")
	named.Error("test error")
	named.Debug("test debug")
	named.Warn("test warn")
}

func TestLoggerOutput(t *testing.T) {
	// Test that logger initializes and logs without panic
	err := Init("debug", "console")
	if err != nil {
		t.Fatalf("Init failed: %v", err)
	}

	// Test all log levels don't panic
	Log.Info("test info message")
	Log.Error("test error message")
	Log.Debug("test debug message")
	Log.Warn("test warn message")

	// Test Sync doesn't panic
	Sync()
}