package keys

import (
	"bytes"
	"context"

	apperrors "github.com/securechat/server/pkg/errors"
)

// Limits on client-supplied key material. A Curve25519 public key is 32 bytes
// and an Ed25519 signature is 64; anything else is corrupt or hostile, and the
// caps on batch size stop a client from turning the prekey table into a dump
// ground.
const (
	IdentityKeySize     = 32
	SignedPreKeySize    = 32
	IdentitySignatureSize = 64
	MaxPreKeyBatch      = 100
	MaxClaimDevices     = 100
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

type IdentityKeyRequest struct {
	IdentityKey    []byte `json:"identity_key"`
	RegistrationID int32  `json:"registration_id"`
}

type SignedPreKeyRequest struct {
	KeyID                int32  `json:"key_id"`
	PublicKey            []byte `json:"public_key"`
	IdentityKeySignature []byte `json:"identity_key_signature"`
}

type PublishKyberPreKeyRequest struct {
	KeyID                int32  `json:"key_id"`
	PublicKey            []byte `json:"public_key"`
	IdentityKeySignature []byte `json:"identity_key_signature"`
}

type OneTimePreKeysRequest struct {
	// Keys maps key_id -> base64-encoded public key.
	Keys map[int32][]byte `json:"keys"`
}

type ClaimRequest struct {
	DeviceIDs []int64 `json:"device_ids"`
}

type ClaimResponse struct {
	Bundles   []PreKeyBundleResponse `json:"bundles"`
	// Missing lists requested devices for which no bundle could be produced —
	// unknown, unauthorized, or not yet registered for E2EE.
	Missing   []MissingDevice        `json:"missing"`
}

type MissingDevice struct {
	DeviceID int64  `json:"device_id"`
	Reason   string `json:"reason"`
}

type PreKeyBundleResponse struct {
	DeviceID         int64  `json:"device_id"`
	IdentityKey      []byte `json:"identity_key"`
	RegistrationID   int32  `json:"registration_id"`
	SignedPreKeyID   int32  `json:"signed_pre_key_id"`
	SignedPreKey     []byte `json:"signed_pre_key"`
	SignedPreKeySig  []byte `json:"signed_pre_key_signature"`
	HasOneTimePreKey bool   `json:"has_one_time_pre_key"`
	OneTimePreKeyID  int32  `json:"one_time_pre_key_id"`
	OneTimePreKey    []byte `json:"one_time_pre_key"`
	// Post-quantum: required by PQXDH, so a bundle without it is not returned.
	HasKyberPreKey bool   `json:"has_kyber_pre_key"`
	KyberPreKeyID  int32  `json:"kyber_pre_key_id"`
	KyberPreKey    []byte `json:"kyber_pre_key"`
	KyberPreKeySig []byte `json:"kyber_pre_key_signature"`
}

// PublishIdentityKey registers the calling device's long-term public identity key.
//
// Re-publishing the identical key is a no-op (a retried request). A *different*
// key is rejected: silently replacing an identity key would invalidate every
// session built on it and is exactly what a key-substitution attack looks like.
func (s *Service) PublishIdentityKey(
	ctx context.Context, deviceID, userID int64, req *IdentityKeyRequest,
) error {
	if len(req.IdentityKey) != IdentityKeySize {
		return apperrors.ErrInvalidRequest
	}
	if req.RegistrationID < 0 {
		return apperrors.ErrInvalidRequest
	}

	created, err := s.repo.PublishIdentityKey(ctx, deviceID, userID, req.IdentityKey, req.RegistrationID)
	if err != nil {
		return err
	}
	if created {
		return nil
	}

	existing, err := s.repo.ExistingIdentityKey(ctx, deviceID)
	if err != nil {
		return err
	}
	if bytes.Equal(existing, req.IdentityKey) {
		// Same key re-uploaded: idempotent success.
		return nil
	}
	return apperrors.ErrIdentityKeyConflict
}

func (s *Service) PublishSignedPreKey(ctx context.Context, deviceID int64, req *SignedPreKeyRequest) error {
	if req.KeyID < 0 {
		return apperrors.ErrInvalidRequest
	}
	if len(req.PublicKey) != SignedPreKeySize {
		return apperrors.ErrInvalidRequest
	}
	if len(req.IdentityKeySignature) != IdentitySignatureSize {
		return apperrors.ErrInvalidRequest
	}
	return s.repo.UpsertSignedPreKey(ctx, deviceID, req.KeyID, req.PublicKey, req.IdentityKeySignature)
}

// KyberPreKeyPublicSize is the serialized ML-KEM-1024 public key: 1568 raw bytes
// plus a one-byte type prefix. Measured against libsignal 0.76.1 and pinned by the
// client's SignalProtocolRoundTripTest, so a library update that changes it fails
// a test rather than silently breaking registration.
const KyberPreKeyPublicSize = 1569

func (s *Service) PublishKyberPreKey(ctx context.Context, deviceID int64, req *PublishKyberPreKeyRequest) error {
	if req.KeyID < 0 {
		return apperrors.ErrInvalidRequest
	}
	if len(req.PublicKey) != KyberPreKeyPublicSize {
		return apperrors.ErrInvalidRequest
	}
	if len(req.IdentityKeySignature) != IdentitySignatureSize {
		return apperrors.ErrInvalidRequest
	}
	return s.repo.UpsertKyberPreKey(ctx, deviceID, req.KeyID, req.PublicKey, req.IdentityKeySignature)
}

func (s *Service) AddOneTimePreKeys(ctx context.Context, deviceID int64, req *OneTimePreKeysRequest) (int64, error) {
	if len(req.Keys) == 0 {
		return 0, apperrors.ErrInvalidRequest
	}
	if len(req.Keys) > MaxPreKeyBatch {
		return 0, apperrors.ErrInvalidRequest
	}
	for keyID, publicKey := range req.Keys {
		if keyID < 0 {
			return 0, apperrors.ErrInvalidRequest
		}
		if len(publicKey) != SignedPreKeySize {
			return 0, apperrors.ErrInvalidRequest
		}
	}
	return s.repo.AddOneTimePreKeys(ctx, deviceID, req.Keys)
}

func (s *Service) CountUnclaimedOneTimePreKeys(ctx context.Context, deviceID int64) (int64, error) {
	return s.repo.CountUnclaimedOneTimePreKeys(ctx, deviceID)
}

// ClaimPreKeyBundles hands out X3DH bundles for the requested devices.
//
// Devices the caller has no conversation with are filtered out before anything
// is claimed, so the endpoint cannot be used to drain a stranger's one-time
// prekeys. Devices that are unknown, unauthorized, or simply have no keys yet
// are reported in Missing rather than failing the whole request.
func (s *Service) ClaimPreKeyBundles(
	ctx context.Context, callerUserID int64, req *ClaimRequest,
) (*ClaimResponse, error) {
	requested := dedupe(req.DeviceIDs)
	if len(requested) == 0 {
		return nil, apperrors.ErrInvalidRequest
	}
	if len(requested) > MaxClaimDevices {
		return nil, apperrors.ErrInvalidRequest
	}

	authorized, err := s.repo.AuthorizedDeviceIDs(ctx, callerUserID, requested)
	if err != nil {
		return nil, err
	}

	owners, err := s.repo.DeviceOwnerIDs(ctx, requested)
	if err != nil {
		return nil, err
	}

	// Classify what was requested so the client can tell "not your contact" from
	// "they have not finished E2EE setup yet".
	authorizedSet := make(map[int64]struct{}, len(authorized))
	for _, id := range authorized {
		authorizedSet[id] = struct{}{}
	}

	missing := make([]MissingDevice, 0, len(requested))
	claimable := make([]int64, 0, len(authorized))
	for _, id := range requested {
		if _, ok := owners[id]; !ok {
			missing = append(missing, MissingDevice{DeviceID: id, Reason: "unknown_device"})
			continue
		}
		if !contains(authorizedSet, id) {
			missing = append(missing, MissingDevice{DeviceID: id, Reason: "not_authorized"})
			continue
		}
		claimable = append(claimable, id)
	}

	bundles := make([]PreKeyBundleResponse, 0, len(claimable))
	if len(claimable) > 0 {
		claimed, err := s.repo.ClaimPreKeyBundles(ctx, claimable)
		if err != nil {
			return nil, err
		}
		for _, id := range claimable {
			b, ok := claimed[id]
			if !ok {
				// Registered for E2EE but no identity key published yet.
				missing = append(missing, MissingDevice{DeviceID: id, Reason: "no_keys_published"})
				continue
			}
			bundles = append(bundles, PreKeyBundleResponse{
				DeviceID:         b.DeviceID,
				IdentityKey:      b.IdentityKey,
				RegistrationID:   b.RegistrationID,
				SignedPreKeyID:   b.SignedPreKeyID,
				SignedPreKey:     b.SignedPreKey,
				SignedPreKeySig:  b.SignedPreKeySig,
				HasOneTimePreKey: b.HasOneTimePreKey,
				OneTimePreKeyID:  b.OneTimePreKeyID,
				OneTimePreKey:    b.OneTimePreKey,
				HasKyberPreKey:   b.HasKyberPreKey,
				KyberPreKeyID:    b.KyberPreKeyID,
				KyberPreKey:      b.KyberPreKey,
				KyberPreKeySig:   b.KyberPreKeySig,
			})
		}
	}

	return &ClaimResponse{Bundles: bundles, Missing: missing}, nil
}

func dedupe(ids []int64) []int64 {
	seen := make(map[int64]struct{}, len(ids))
	out := make([]int64, 0, len(ids))
	for _, id := range ids {
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}

func contains(set map[int64]struct{}, id int64) bool {
	_, ok := set[id]
	return ok
}
