package crypto

import (
	"math"
	"testing"
)

// TestGenerateOTPIsUniform is a regression test for modulo bias.
//
// The original implementation mapped a random byte onto a digit with
// `bytes[i] % 10`. Because 256 is not a multiple of 10, that made digits 0-5
// (26 byte values each) about 1.7% more likely than 6-9 (25 each). The bias is
// far too small to matter for a single code, but it is a real deviation from
// uniform and it is trivially detectable in aggregate, so this pins it down.
//
// With 600,000 sampled digits the expected count per digit is 60,000 and the
// standard deviation is ~232. A 2% tolerance is ~5.2 sigma for an unbiased
// generator (false-failure probability around 1e-7) while the biased generator
// lands 6-9 at roughly -1400, i.e. outside the band.
func TestGenerateOTPIsUniform(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping OTP uniformity sampling in short mode")
	}

	const (
		codes    = 100_000
		length   = 6
		total    = codes * length
		tolerance = 0.02 // 2% relative deviation per digit
	)

	counts := make(map[rune]int, len(OTPCharset))
	for i := 0; i < codes; i++ {
		otp, err := GenerateOTP(length)
		if err != nil {
			t.Fatalf("GenerateOTP failed: %v", err)
		}
		if len(otp) != length {
			t.Fatalf("GenerateOTP(6) returned %d chars", len(otp))
		}
		for _, c := range otp {
			counts[c]++
		}
	}

	expected := float64(total) / float64(len(OTPCharset))
	limit := expected * tolerance

	for _, digit := range OTPCharset {
		got := float64(counts[digit])
		deviation := math.Abs(got - expected)
		if deviation > limit {
			t.Errorf("digit %c appeared %.0f times, expected ~%.0f (deviation %.0f exceeds %.0f): the digit distribution is not uniform",
				digit, got, expected, deviation, limit)
		}
	}

	// Guard against a generator that emits only a subset of the charset.
	if len(counts) != len(OTPCharset) {
		t.Errorf("only %d of %d digits were ever produced", len(counts), len(OTPCharset))
	}
}

// TestGenerateOTPRespectsLength covers the defaults and the requested lengths.
func TestGenerateOTPRespectsLength(t *testing.T) {
	for _, length := range []int{1, 4, 6, 8} {
		otp, err := GenerateOTP(length)
		if err != nil {
			t.Fatalf("GenerateOTP(%d) failed: %v", length, err)
		}
		if len(otp) != length {
			t.Errorf("GenerateOTP(%d) returned %d chars", length, len(otp))
		}
	}

	// A non-positive length falls back to the package default rather than
	// returning an empty code that would be trivially guessable.
	otp, err := GenerateOTP(0)
	if err != nil {
		t.Fatalf("GenerateOTP(0) failed: %v", err)
	}
	if len(otp) != OTPLength {
		t.Errorf("GenerateOTP(0) returned %d chars, expected the default of %d", len(otp), OTPLength)
	}
}
