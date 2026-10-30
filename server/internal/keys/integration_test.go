package keys_test

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"testing"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"

	"github.com/securechat/server/internal/database"
	"github.com/securechat/server/internal/keys"
)

// These tests exercise the prekey directory against a real PostgreSQL: publish a
// complete set of key material, claim it, and check the bundle that comes back is
// actually usable to start a session.
//
// Skipped unless SECURECHAT_TEST_DATABASE_URL is set. The Kyber assertion is the
// important one: libsignal 0.76 cannot build a session without post-quantum
// material, so a claim that returns a bundle without it is a silent dead end.
func TestPreKeyDirectoryIntegration(t *testing.T) {
	url := os.Getenv("SECURECHAT_TEST_DATABASE_URL")
	if url == "" {
		t.Skipf("SECURECHAT_TEST_DATABASE_URL not set; skipping integration test")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	db, err := database.Connect(ctx, url)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := database.MigrateDir(db, "../../migrations"); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	repo := keys.NewRepository(db)
	svc := keys.NewService(repo)
	fx := newKeyFixture(t, db)

	// --- publish a complete set -------------------------------------------
	identityKey := make([]byte, keys.IdentityKeySize)
	for i := range identityKey {
		identityKey[i] = byte(i)
	}
	if err := svc.PublishIdentityKey(ctx, fx.deviceID, fx.userID, &keys.IdentityKeyRequest{
		IdentityKey:    identityKey,
		RegistrationID: 4242,
	}); err != nil {
		t.Fatalf("publish identity key: %v", err)
	}

	if err := svc.PublishSignedPreKey(ctx, fx.deviceID, &keys.SignedPreKeyRequest{
		KeyID:                1,
		PublicKey:            make([]byte, keys.SignedPreKeySize),
		IdentityKeySignature: make([]byte, keys.IdentitySignatureSize),
	}); err != nil {
		t.Fatalf("publish signed prekey: %v", err)
	}

	kyberKey := make([]byte, keys.KyberPreKeyPublicSize)
	for i := range kyberKey {
		kyberKey[i] = byte(i % 251)
	}
	if err := svc.PublishKyberPreKey(ctx, fx.deviceID, &keys.PublishKyberPreKeyRequest{
		KeyID:                1,
		PublicKey:            kyberKey,
		IdentityKeySignature: make([]byte, keys.IdentitySignatureSize),
	}); err != nil {
		t.Fatalf("publish kyber prekey: %v", err)
	}

	if _, err := svc.AddOneTimePreKeys(ctx, fx.deviceID, &keys.OneTimePreKeysRequest{
		Keys: map[int32][]byte{1: make([]byte, keys.SignedPreKeySize)},
	}); err != nil {
		t.Fatalf("publish one-time prekeys: %v", err)
	}

	// --- claim it ---------------------------------------------------------
	// A caller with no shared conversation is filtered out rather than erroring:
	// the response carries zero bundles and a not_authorized reason, which is
	// what the API contract documents.
	outsider, err := svc.ClaimPreKeyBundles(ctx, fx.outsiderID, &keys.ClaimRequest{DeviceIDs: []int64{fx.deviceID}})
	if err != nil {
		t.Fatalf("outsider claim: %v", err)
	}
	if len(outsider.Bundles) != 0 {
		t.Fatalf("an outsider received %d prekey bundle(s)", len(outsider.Bundles))
	}
	if !hasMissingReason(outsider.Missing, fx.deviceID, "not_authorized") {
		t.Errorf("outsider missing = %+v, want not_authorized for device %d", outsider.Missing, fx.deviceID)
	}

	authorized, err := repo.AuthorizedDeviceIDs(ctx, fx.sharedUserID, []int64{fx.deviceID})
	if err != nil {
		t.Fatalf("authorize: %v", err)
	}
	if len(authorized) != 1 {
		t.Fatalf("authorized = %v, want [%d]", authorized, fx.deviceID)
	}

	response, err := svc.ClaimPreKeyBundles(ctx, fx.sharedUserID, &keys.ClaimRequest{DeviceIDs: authorized})
	if err != nil {
		t.Fatalf("claim: %v", err)
	}

	if len(response.Bundles) != 1 {
		t.Fatalf("bundles = %d, want 1", len(response.Bundles))
	}
	bundle := response.Bundles[0]

	if bundle.RegistrationID != 4242 {
		t.Errorf("registration id = %d, want 4242", bundle.RegistrationID)
	}
	if string(bundle.IdentityKey) != string(identityKey) {
		t.Error("claimed identity key does not round-trip")
	}
	if !bundle.HasKyberPreKey {
		// This is the assertion that matters most: without post-quantum material
		// libsignal 0.76 cannot start a session, so the bundle is a dead end.
		t.Fatal("claimed bundle has no kyber prekey: PQXDH session establishment would be impossible")
	}
	if string(bundle.KyberPreKey) != string(kyberKey) {
		t.Error("claimed kyber prekey does not round-trip")
	}
	if !bundle.HasOneTimePreKey {
		t.Error("bundle has no one-time prekey despite one being in stock")
	}

	// The one-time prekey is consumed: a second claim returns the bundle without
	// it, and the count drops.
	remaining, err := svc.CountUnclaimedOneTimePreKeys(ctx, fx.deviceID)
	if err != nil {
		t.Fatalf("count after claim: %v", err)
	}
	if remaining != 0 {
		t.Errorf("unclaimed one-time prekeys = %d after a claim, want 0", remaining)
	}

	second, err := svc.ClaimPreKeyBundles(ctx, fx.sharedUserID, &keys.ClaimRequest{DeviceIDs: authorized})
	if err != nil {
		t.Fatalf("second claim: %v", err)
	}
	if b := second.Bundles[0]; b.HasOneTimePreKey {
		t.Error("the same one-time prekey was handed out twice")
	}

	// The Kyber prekey is reused (it is the signed, non-consumed variant), so a
	// second claim still returns a usable bundle.
	if !second.Bundles[0].HasKyberPreKey {
		t.Error("kyber prekey missing from the second claim")
	}
}

// ---------------------------------------------------------------------------
// fixture

// hasMissingReason reports whether the response explains a device with the
// expected reason.
func hasMissingReason(missing []keys.MissingDevice, deviceID int64, want string) bool {
	for _, m := range missing {
		if m.DeviceID == deviceID && m.Reason == want {
			return true
		}
	}
	return false
}

type keyFixture struct {
	db                  *sql.DB
	userID              int64
	sharedUserID        int64
	outsiderID          int64
	deviceID            int64
	sharedConversationID int64
}

func newKeyFixture(t *testing.T, db *sql.DB) *keyFixture {
	t.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	suffix := time.Now().UnixNano() % 1_000_000
	fx := &keyFixture{db: db}

	insertUser := func(name string, n int) int64 {
		var id int64
		if err := db.QueryRowContext(ctx,
			`INSERT INTO users (phone_number, display_name) VALUES ($1, $2) RETURNING id`,
			fmt.Sprintf("+1996%06d%d", suffix, n), name).Scan(&id); err != nil {
			t.Fatalf("create %s: %v", name, err)
		}
		return id
	}
	fx.userID = insertUser("probe-owner", 1)
	fx.sharedUserID = insertUser("probe-shared", 2)
	fx.outsiderID = insertUser("probe-outsider", 3)

	if err := db.QueryRowContext(ctx, `
		INSERT INTO devices (user_id, device_name, device_identifier, platform)
		VALUES ($1, 'probe-device', $2, 'android') RETURNING id`,
		fx.userID, fmt.Sprintf("key-probe-%d", suffix)).Scan(&fx.deviceID); err != nil {
		t.Fatalf("create device: %v", err)
	}

	// The shared user shares a conversation with the owner, so they may claim.
	if err := db.QueryRowContext(ctx, `INSERT INTO conversations (type) VALUES ('direct') RETURNING id`).
		Scan(&fx.sharedConversationID); err != nil {
		t.Fatalf("create conversation: %v", err)
	}
	if _, err := db.ExecContext(ctx,
		`INSERT INTO conversation_participants (conversation_id, user_id) VALUES ($1, $2), ($1, $3)`,
		fx.sharedConversationID, fx.userID, fx.sharedUserID); err != nil {
		t.Fatalf("add participants: %v", err)
	}

	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cleanupCancel()
		_, _ = db.ExecContext(cleanupCtx, `DELETE FROM conversations WHERE id = $1`, fx.sharedConversationID)
		_, _ = db.ExecContext(cleanupCtx, `DELETE FROM users WHERE id IN ($1, $2, $3)`, fx.userID, fx.sharedUserID, fx.outsiderID)
	})

	return fx
}
