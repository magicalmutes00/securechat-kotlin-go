package messages

import (
	"errors"
	"testing"

	apperrors "github.com/securechat/server/pkg/errors"
)

// TestCursorRoundTrip verifies the opaque wire form survives encode/decode.
func TestCursorRoundTrip(t *testing.T) {
	original := Cursor{CreatedAtMicros: 1_791_403_657_123_456, ID: 987_654_321}

	decoded, err := ParseCursor(original.String())
	if err != nil {
		t.Fatalf("ParseCursor failed on its own output: %v", err)
	}
	if decoded == nil {
		t.Fatal("ParseCursor returned nil for a non-empty cursor")
	}
	if *decoded != original {
		t.Fatalf("round trip changed the cursor: got %+v, want %+v", *decoded, original)
	}
}

// TestParseCursorEmptyMeansFirstPage documents that an absent cursor is not an
// error — it is how the client asks for page one.
func TestParseCursorEmptyMeansFirstPage(t *testing.T) {
	cursor, err := ParseCursor("")
	if err != nil {
		t.Fatalf("ParseCursor(\"\") returned an error: %v", err)
	}
	if cursor != nil {
		t.Fatalf("ParseCursor(\"\") returned %+v, want nil", cursor)
	}
}

// TestParseCursorRejectsMalformed checks the shapes a client could actually
// send. These must be a 400, not a silently-empty page.
func TestParseCursorRejectsMalformed(t *testing.T) {
	cases := map[string]string{
		"missing separator":  "1791403657",
		"missing id":         "1791403657:",
		"missing timestamp":  ":42",
		"non-numeric ts":     "soon:42",
		"non-numeric id":     "1791403657:later",
		"extra separator":    "1:2:3",
		"empty-ish":          " ",
	}

	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			cursor, err := ParseCursor(raw)
			if err == nil {
				t.Fatalf("ParseCursor(%q) succeeded with %+v, want an error", raw, cursor)
			}
			if !errors.Is(err, apperrors.ErrInvalidRequest) {
				t.Fatalf("ParseCursor(%q) returned %v, want ErrInvalidRequest", raw, err)
			}
		})
	}
}

// TestCursorUsesMicrosecondsNotSeconds is the regression test for the dropped
// messages: the cursor used to carry whole Unix seconds, so every message that
// shared the boundary second was skipped on the next page.
func TestCursorUsesMicrosecondsNotSeconds(t *testing.T) {
	// Two messages one millisecond apart, inside the same Unix second.
	first := Cursor{CreatedAtMicros: 1_791_403_657_000_000, ID: 10}
	second := Cursor{CreatedAtMicros: 1_791_403_657_001_000, ID: 11}

	if first.String() == second.String() {
		t.Fatal("cursors one millisecond apart encode identically: sub-second ordering is being lost")
	}

	// Their whole-second truncations are equal, which is exactly what made the
	// old cursor lossy.
	if first.CreatedAtMicros/1_000_000 != second.CreatedAtMicros/1_000_000 {
		t.Fatal("test setup is wrong: the two cursors should share a Unix second")
	}
}

// TestCursorIDBreaksTies confirms the id tiebreaker is part of the encoded key,
// so a burst of messages sharing one timestamp still pages deterministically.
func TestCursorIDBreaksTies(t *testing.T) {
	ts := int64(1_791_403_657_123_456)
	a := Cursor{CreatedAtMicros: ts, ID: 5}
	b := Cursor{CreatedAtMicros: ts, ID: 6}

	if a.String() == b.String() {
		t.Fatal("cursors with identical timestamps but different ids encode identically")
	}
}
