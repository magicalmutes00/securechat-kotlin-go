// Package keys implements the E2EE phase E1 prekey directory.
//
// The server stores PUBLIC key material only. Private keys are generated on the
// device and never leave it, so this package is deliberately incapable of
// decrypting anything: it is a directory a sender consults before building a
// session, plus a blind store of per-recipient-device ciphertext for phase E2.
// See docs/e2ee-design.md.
package keys

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// PreKeyBundle is everything a sender needs to run PQXDH against one device.
type PreKeyBundle struct {
	DeviceID        int64
	IdentityKey     []byte
	RegistrationID  int32
	SignedPreKeyID  int32
	SignedPreKey    []byte
	SignedPreKeySig []byte
	// OneTimePreKey is nil when the device has no unclaimed one-time prekey
	// left. X3DH still works without it (that is the "no one-time prekey"
	// variant), so its absence is not an error — but the client should be told,
	// because it means the recipient is running low.
	HasOneTimePreKey bool
	OneTimePreKeyID  int32
	OneTimePreKey    []byte
	// Post-quantum material. libsignal 0.76 runs PQXDH, so a bundle without a
	// Kyber key cannot establish a session at all — its absence is reported as
	// "no bundle", not as a weaker variant.
	HasKyberPreKey bool
	KyberPreKeyID  int32
	KyberPreKey    []byte
	KyberPreKeySig []byte
}

// placeholderList renders "$offset, $offset+1, ..." for an IN clause. Hand-rolled
// rather than relying on array binding so the query stays driver-agnostic.
func placeholderList(offset, n int) string {
	if n <= 0 {
		return ""
	}
	parts := make([]string, n)
	for i := range parts {
		parts[i] = fmt.Sprintf("$%d", offset+i)
	}
	return strings.Join(parts, ", ")
}

// PublishIdentityKey stores a device's long-term public identity key.
//
// It reports created=false when a key was already registered for this device.
// That case is NOT silently resolved by overwriting: a different key means
// either a legitimate rotation or a substituted key, and both invalidate every
// session built on the old one. The service compares and decides.
func (r *Repository) PublishIdentityKey(
	ctx context.Context, deviceID, userID int64, identityKey []byte, registrationID int32,
) (bool, error) {
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO device_identity_keys (device_id, user_id, identity_key, registration_id)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (device_id) DO NOTHING
	`, deviceID, userID, identityKey, registrationID)
	if err != nil {
		return false, fmt.Errorf("failed to publish identity key: %w", err)
	}

	affected, err := res.RowsAffected()
	if err != nil {
		return false, fmt.Errorf("failed to confirm identity key publish: %w", err)
	}
	return affected == 1, nil
}

// ExistingIdentityKey returns the public identity key currently registered for a
// device, or nil when none is.
func (r *Repository) ExistingIdentityKey(ctx context.Context, deviceID int64) ([]byte, error) {
	var existing []byte
	err := r.db.QueryRowContext(ctx, `
		SELECT identity_key FROM device_identity_keys WHERE device_id = $1
	`, deviceID).Scan(&existing)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to read existing identity key: %w", err)
	}
	return existing, nil
}

// UpsertSignedPreKey publishes or rotates a device's signed prekey. Rotation is
// simply a new key_id; re-uploading the same key_id is an idempotent retry.
func (r *Repository) UpsertSignedPreKey(
	ctx context.Context, deviceID int64, keyID int32, publicKey, signature []byte,
) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO device_signed_prekeys (device_id, key_id, public_key, identity_key_signature)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (device_id, key_id) DO UPDATE
		SET public_key = EXCLUDED.public_key,
		    identity_key_signature = EXCLUDED.identity_key_signature,
		    created_at = NOW()
	`, deviceID, keyID, publicKey, signature)
	if err != nil {
		return fmt.Errorf("failed to publish signed prekey: %w", err)
	}
	return nil
}

// AddOneTimePreKeys stores a batch of public one-time prekeys. Re-uploading an
// existing key_id is a no-op, which makes a retried upload safe.
func (r *Repository) AddOneTimePreKeys(
	ctx context.Context, deviceID int64, keys map[int32][]byte,
) (int64, error) {
	if len(keys) == 0 {
		return 0, nil
	}

	var (
		sb      strings.Builder
		args    = make([]interface{}, 0, len(keys)*3)
		written int
	)
	sb.WriteString(`
		INSERT INTO device_one_time_prekeys (device_id, key_id, public_key)
		VALUES
	`)
	for keyID, publicKey := range keys {
		if written > 0 {
			sb.WriteString(",")
		}
		base := written*3 + 1
		fmt.Fprintf(&sb, "($%d, $%d, $%d)", base, base+1, base+2)
		args = append(args, deviceID, keyID, publicKey)
		written++
	}
	sb.WriteString(`
		ON CONFLICT (device_id, key_id) DO NOTHING
	`)

	res, err := r.db.ExecContext(ctx, sb.String(), args...)
	if err != nil {
		return 0, fmt.Errorf("failed to store one-time prekeys: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return 0, fmt.Errorf("failed to confirm one-time prekeys: %w", err)
	}
	return affected, nil
}

// CountUnclaimedOneTimePreKeys reports how many one-time prekeys a device has
// left. Clients use this to decide when to upload more.
func (r *Repository) CountUnclaimedOneTimePreKeys(ctx context.Context, deviceID int64) (int64, error) {
	var count int64
	err := r.db.QueryRowContext(ctx, `
		SELECT COUNT(*) FROM device_one_time_prekeys
		WHERE device_id = $1 AND claimed_at IS NULL
	`, deviceID).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count one-time prekeys: %w", err)
	}
	return count, nil
}

// UpsertKyberPreKey publishes this device's current post-quantum prekey.
//
// Deliberately a single signed key rather than a consumed batch: the PreKeyBundle
// carries exactly one Kyber key and verifies its signature, mirroring the X25519
// signed prekey. Reusing it across sessions is the PQXDH equivalent of the
// "no one-time prekey" fallback — functional, at a small forward-secrecy cost
// until the client starts rotating it. Rotation is a new key_id; re-uploading the
// same key_id is an idempotent retry.
func (r *Repository) UpsertKyberPreKey(
	ctx context.Context, deviceID int64, keyID int32, publicKey, signature []byte,
) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO device_kyber_prekeys (device_id, key_id, public_key, identity_key_signature)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (device_id, key_id) DO UPDATE
		SET public_key = EXCLUDED.public_key,
		    identity_key_signature = EXCLUDED.identity_key_signature,
		    created_at = NOW()
	`, deviceID, keyID, publicKey, signature)
	if err != nil {
		return fmt.Errorf("failed to publish kyber prekey: %w", err)
	}
	return nil
}

// AuthorizedDeviceIDs filters a requested device list down to the devices the
// caller is actually allowed to claim prekeys for: those whose user shares at
// least one conversation with the caller.
//
// Without this gate, POST /keys/claim would let any authenticated user drain
// another user's one-time prekeys, forcing their conversations into the weaker
// no-one-time-prekey variant of X3DH.
func (r *Repository) AuthorizedDeviceIDs(
	ctx context.Context, callerUserID int64, deviceIDs []int64,
) ([]int64, error) {
	if len(deviceIDs) == 0 {
		return nil, nil
	}

	query := fmt.Sprintf(`
		SELECT DISTINCT d.id
		FROM devices d
		WHERE d.id IN (%s)
		  AND EXISTS (
		      SELECT 1
		      FROM conversation_participants mine
		      JOIN conversation_participants theirs
		           ON theirs.conversation_id = mine.conversation_id
		      JOIN devices dd ON dd.user_id = theirs.user_id
		      WHERE mine.user_id = $1
		        AND mine.left_at IS NULL
		        AND theirs.left_at IS NULL
		        AND dd.id = d.id
		  )
		  AND d.user_id <> $1
	`, placeholderList(2, len(deviceIDs)))

	args := make([]interface{}, 0, len(deviceIDs)+1)
	args = append(args, callerUserID)
	for _, id := range deviceIDs {
		args = append(args, id)
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to authorize devices: %w", err)
	}
	defer rows.Close()

	var authorized []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		authorized = append(authorized, id)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read authorized devices: %w", err)
	}
	return authorized, nil
}

// ClaimPreKeyBundles returns an X3DH bundle for each requested device, consuming
// one unclaimed one-time prekey per device where one is available.
//
// All three steps run in one transaction. The first statement takes row locks on
// every unclaimed one-time prekey belonging to the requested devices, so a
// concurrent claim either blocks on those rows or (once committed) sees them
// already claimed — two senders can never be handed the same key. The picked key
// per device is chosen in Go as the lowest key_id, and the rows read under lock
// are reused for the response rather than queried again.
//
// The statements here are deliberately plain (SELECT ... FOR UPDATE, then
// UPDATE ... FROM (VALUES ...), then a plain SELECT) so behaviour does not depend
// on subtler SQL features.
//
// A device with no published identity key is simply absent from the result — the
// caller treats that as "this device cannot be written to yet".
func (r *Repository) ClaimPreKeyBundles(
	ctx context.Context, deviceIDs []int64,
) (map[int64]*PreKeyBundle, error) {
	if len(deviceIDs) == 0 {
		return map[int64]*PreKeyBundle{}, nil
	}

	inList := placeholderList(1, len(deviceIDs))
	listArgs := make([]interface{}, len(deviceIDs))
	for i, id := range deviceIDs {
		listArgs[i] = id
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to begin claim transaction: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	// Step 1: lock every unclaimed one-time prekey for these devices and read
	// them back in one go.
	lockSQL := fmt.Sprintf(`
		SELECT device_id, key_id, public_key
		FROM device_one_time_prekeys
		WHERE device_id IN (%s) AND claimed_at IS NULL
		ORDER BY device_id, key_id
		FOR UPDATE
	`, inList)

	lockRows, err := tx.QueryContext(ctx, lockSQL, listArgs...)
	if err != nil {
		return nil, fmt.Errorf("failed to lock one-time prekeys: %w", err)
	}

	// Pick the lowest key_id per device while reading.
	picked := make(map[int64]*oneTimePreKey, len(deviceIDs))
	for lockRows.Next() {
		var (
			deviceID  int64
			keyID     int32
			publicKey []byte
		)
		if err := lockRows.Scan(&deviceID, &keyID, &publicKey); err != nil {
			lockRows.Close()
			return nil, fmt.Errorf("failed to scan one-time prekey: %w", err)
		}
		if existing, ok := picked[deviceID]; ok && existing.KeyID <= keyID {
			continue
		}
		picked[deviceID] = &oneTimePreKey{KeyID: keyID, PublicKey: publicKey}
	}
	if err := lockRows.Err(); err != nil {
		lockRows.Close()
		return nil, fmt.Errorf("failed to read one-time prekeys: %w", err)
	}
	if err := lockRows.Close(); err != nil {
		return nil, fmt.Errorf("failed to close one-time prekey scan: %w", err)
	}

	// Step 2: mark exactly the picked rows as claimed.
	if len(picked) > 0 {
		var (
			sb   strings.Builder
			args = make([]interface{}, 0, len(picked)*2)
		)
		sb.WriteString(`
			UPDATE device_one_time_prekeys otp
			SET claimed_at = NOW()
			FROM (VALUES
		`)
		written := 0
		for deviceID, key := range picked {
			if written > 0 {
				sb.WriteString(",")
			}
			base := written*2 + 1
			fmt.Fprintf(&sb, "($%d::bigint, $%d::int)", base, base+1)
			args = append(args, deviceID, key.KeyID)
			written++
		}
		sb.WriteString(`
			) AS picked(device_id, key_id)
			WHERE otp.device_id = picked.device_id
			  AND otp.key_id = picked.key_id
		`)

		if _, err := tx.ExecContext(ctx, sb.String(), args...); err != nil {
			return nil, fmt.Errorf("failed to claim one-time prekeys: %w", err)
		}
	}

	// Step 3: read identity keys and the newest signed prekey. Devices with no
	// identity key are absent from the result.
	//
	// The Kyber prekey is required for PQXDH: libsignal 0.76 cannot build a
	// session from a bundle without one. A device that has not published one yet
	// is therefore still reported as "no bundle", which is what tells the caller
	// that this peer cannot be written to yet.
	bundleSQL := fmt.Sprintf(`
		SELECT
			d.id,
			ik.identity_key,
			ik.registration_id,
			sp.key_id,
			sp.public_key,
			sp.identity_key_signature,
			kp.key_id,
			kp.public_key,
			kp.identity_key_signature
		FROM devices d
		JOIN device_identity_keys ik ON ik.device_id = d.id
		LEFT JOIN LATERAL (
			SELECT key_id, public_key, identity_key_signature
			FROM device_signed_prekeys
			WHERE device_id = d.id
			ORDER BY created_at DESC, key_id DESC
			LIMIT 1
		) sp ON TRUE
		LEFT JOIN LATERAL (
			SELECT key_id, public_key, identity_key_signature
			FROM device_kyber_prekeys
			WHERE device_id = d.id
			ORDER BY created_at DESC, key_id DESC
			LIMIT 1
		) kp ON TRUE
		WHERE d.id IN (%s)
	`, inList)

	bundleRows, err := tx.QueryContext(ctx, bundleSQL, listArgs...)
	if err != nil {
		return nil, fmt.Errorf("failed to read prekey bundles: %w", err)
	}
	defer bundleRows.Close()

	bundles := make(map[int64]*PreKeyBundle, len(deviceIDs))
	for bundleRows.Next() {
		var (
			bundle       PreKeyBundle
			signedKeyID  sql.NullInt32
			signedKey    []byte
			signedKeySig []byte
			kyberKeyID   sql.NullInt32
			kyberKey     []byte
			kyberSig     []byte
		)

		if err := bundleRows.Scan(
			&bundle.DeviceID,
			&bundle.IdentityKey,
			&bundle.RegistrationID,
			&signedKeyID, &signedKey, &signedKeySig,
			&kyberKeyID, &kyberKey, &kyberSig,
		); err != nil {
			return nil, fmt.Errorf("failed to scan prekey bundle: %w", err)
		}

		// A signed prekey is mandatory for X3DH; its absence means the device
		// registered its identity key but never completed setup.
		if !signedKeyID.Valid || signedKey == nil {
			continue
		}

		bundle.SignedPreKeyID = signedKeyID.Int32
		bundle.SignedPreKey = signedKey
		bundle.SignedPreKeySig = signedKeySig

		// PQXDH requires a Kyber prekey; without one the bundle is incomplete.
		bundle.HasKyberPreKey = kyberKeyID.Valid && kyberKey != nil
		if bundle.HasKyberPreKey {
			bundle.KyberPreKeyID = kyberKeyID.Int32
			bundle.KyberPreKey = kyberKey
			bundle.KyberPreKeySig = kyberSig
		}

		if key, ok := picked[bundle.DeviceID]; ok {
			bundle.HasOneTimePreKey = true
			bundle.OneTimePreKeyID = key.KeyID
			bundle.OneTimePreKey = key.PublicKey
		}

		bundles[bundle.DeviceID] = &bundle
	}
	if err := bundleRows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read prekey bundles: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit prekey claim: %w", err)
	}

	return bundles, nil
}

// oneTimePreKey is a picked one-time prekey carried between the lock step and the
// response build.
type oneTimePreKey struct {
	KeyID     int32
	PublicKey []byte
}

// DeviceOwnerIDs maps device ids to their owning user id, used by the handler to
// report which requested devices were unknown or unauthorized.
func (r *Repository) DeviceOwnerIDs(ctx context.Context, deviceIDs []int64) (map[int64]int64, error) {
	if len(deviceIDs) == 0 {
		return map[int64]int64{}, nil
	}

	query := fmt.Sprintf(`
		SELECT id, user_id FROM devices WHERE id IN (%s)
	`, placeholderList(1, len(deviceIDs)))

	args := make([]interface{}, len(deviceIDs))
	for i, id := range deviceIDs {
		args[i] = id
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to read device owners: %w", err)
	}
	defer rows.Close()

	owners := make(map[int64]int64, len(deviceIDs))
	for rows.Next() {
		var id, userID int64
		if err := rows.Scan(&id, &userID); err != nil {
			return nil, err
		}
		owners[id] = userID
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to read device owners: %w", err)
	}
	return owners, nil
}
