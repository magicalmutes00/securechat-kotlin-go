package keys

import (
	"errors"
	"strconv"
	"strings"
	"testing"

	apperrors "github.com/securechat/server/pkg/errors"
)

// newTestService returns a service whose repository has no database. Every case
// in this file exercises validation, which by design runs before any query, so a
// nil handle is enough to prove an invalid request never reaches the database.
func newTestService() *Service {
	return NewService(NewRepository(nil))
}

func TestPlaceholderList(t *testing.T) {
	cases := []struct {
		offset, n int
		want      string
	}{
		{1, 0, ""},
		{1, 1, "$1"},
		{1, 3, "$1, $2, $3"},
		{2, 2, "$2, $3"},
		{10, 2, "$10, $11"},
	}

	for _, tc := range cases {
		got := placeholderList(tc.offset, tc.n)
		if got != tc.want {
			t.Errorf("placeholderList(%d, %d) = %q, want %q", tc.offset, tc.n, got, tc.want)
		}
	}
}

func TestDedupe(t *testing.T) {
	got := dedupe([]int64{7, 3, 7, 3, 9, 3})
	want := []int64{7, 3, 9}

	if len(got) != len(want) {
		t.Fatalf("dedupe returned %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("dedupe returned %v, want %v (order matters for stable SQL)", got, want)
		}
	}
}

func TestDedupeEmpty(t *testing.T) {
	if got := dedupe(nil); len(got) != 0 {
		t.Errorf("dedupe(nil) = %v, want empty", got)
	}
}

func TestPublishIdentityKeyValidatesKeySize(t *testing.T) {
	svc := newTestService()

	cases := map[string][]byte{
		"empty":      {},
		"too short":  make([]byte, 31),
		"too long":   make([]byte, 33),
	}

	for name, key := range cases {
		t.Run(name, func(t *testing.T) {
			err := svc.PublishIdentityKey(t.Context(), 1, 1, &IdentityKeyRequest{
				IdentityKey:    key,
				RegistrationID: 1,
			})
			if !errors.Is(err, apperrors.ErrInvalidRequest) {
				t.Fatalf("expected ErrInvalidRequest for a %d-byte key, got %v", len(key), err)
			}
		})
	}
}

func TestPublishIdentityKeyValidatesRegistrationID(t *testing.T) {
	svc := newTestService()

	err := svc.PublishIdentityKey(t.Context(), 1, 1, &IdentityKeyRequest{
		IdentityKey:    make([]byte, IdentityKeySize),
		RegistrationID: -1,
	})
	if !errors.Is(err, apperrors.ErrInvalidRequest) {
		t.Fatalf("expected ErrInvalidRequest for a negative registration id, got %v", err)
	}
}

func TestPublishSignedPreKeyValidation(t *testing.T) {
	svc := newTestService()

	cases := []struct {
		name string
		req  SignedPreKeyRequest
	}{
		{
			name: "negative key id",
			req:  SignedPreKeyRequest{KeyID: -1, PublicKey: make([]byte, SignedPreKeySize), IdentityKeySignature: make([]byte, IdentitySignatureSize)},
		},
		{
			name: "short public key",
			req:  SignedPreKeyRequest{KeyID: 1, PublicKey: make([]byte, 31), IdentityKeySignature: make([]byte, IdentitySignatureSize)},
		},
		{
			name: "short signature",
			req:  SignedPreKeyRequest{KeyID: 1, PublicKey: make([]byte, SignedPreKeySize), IdentityKeySignature: make([]byte, 63)},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := tc.req
			if err := svc.PublishSignedPreKey(t.Context(), 1, &req); !errors.Is(err, apperrors.ErrInvalidRequest) {
				t.Fatalf("expected ErrInvalidRequest, got %v", err)
			}
		})
	}
}

func TestAddOneTimePreKeysValidation(t *testing.T) {
	svc := newTestService()

	validKey := make([]byte, SignedPreKeySize)

	cases := []struct {
		name string
		req  OneTimePreKeysRequest
	}{
		{"empty batch", OneTimePreKeysRequest{Keys: map[int32][]byte{}}},
		{"negative key id", OneTimePreKeysRequest{Keys: map[int32][]byte{-1: validKey}}},
		{"wrong key size", OneTimePreKeysRequest{Keys: map[int32][]byte{1: make([]byte, 16)}}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := tc.req
			if _, err := svc.AddOneTimePreKeys(t.Context(), 1, &req); !errors.Is(err, apperrors.ErrInvalidRequest) {
				t.Fatalf("expected ErrInvalidRequest, got %v", err)
			}
		})
	}
}

func TestAddOneTimePreKeysRejectsOversizedBatch(t *testing.T) {
	svc := newTestService()

	// One past the cap. The cap exists so a client cannot turn the prekey table
	// into an unbounded dump ground.
	keys := make(map[int32][]byte, MaxPreKeyBatch+1)
	for i := 0; i <= MaxPreKeyBatch; i++ {
		keys[int32(i)] = make([]byte, SignedPreKeySize)
	}

	req := OneTimePreKeysRequest{Keys: keys}
	if _, err := svc.AddOneTimePreKeys(t.Context(), 1, &req); !errors.Is(err, apperrors.ErrInvalidRequest) {
		t.Fatalf("expected ErrInvalidRequest for a %d-key batch, got %v", len(keys), err)
	}
}

func TestClaimValidatesDeviceList(t *testing.T) {
	svc := newTestService()

	t.Run("empty", func(t *testing.T) {
		req := ClaimRequest{DeviceIDs: nil}
		if _, err := svc.ClaimPreKeyBundles(t.Context(), 1, &req); !errors.Is(err, apperrors.ErrInvalidRequest) {
			t.Fatalf("expected ErrInvalidRequest for an empty claim, got %v", err)
		}
	})

	t.Run("over the per-request cap", func(t *testing.T) {
		ids := make([]int64, 0, MaxClaimDevices+1)
		for i := 0; i <= MaxClaimDevices; i++ {
			ids = append(ids, int64(i+1))
		}
		req := ClaimRequest{DeviceIDs: ids}
		if _, err := svc.ClaimPreKeyBundles(t.Context(), 1, &req); !errors.Is(err, apperrors.ErrInvalidRequest) {
			t.Fatalf("expected ErrInvalidRequest for %d devices, got %v", len(ids), err)
		}
	})
}

// TestClaimBatchCapBindsPlaceholderCount is a sanity check that the caps and the
// dynamic placeholder builder stay consistent: a batch at the limit must produce
// a placeholder list whose highest index matches the argument count.
func TestClaimBatchCapBindsPlaceholderCount(t *testing.T) {
	list := placeholderList(1, MaxClaimDevices)

	idx := strings.LastIndex(list, "$")
	if idx < 0 {
		t.Fatalf("no placeholder found in %q", list)
	}
	last := list[idx+1:]

	highest, err := strconv.Atoi(last)
	if err != nil {
		t.Fatalf("failed to parse trailing placeholder %q: %v", last, err)
	}
	if highest != MaxClaimDevices {
		t.Fatalf("highest placeholder is $%d, want $%d", highest, MaxClaimDevices)
	}
}
