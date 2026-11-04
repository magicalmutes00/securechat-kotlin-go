package com.securechat.domain.repository

import com.securechat.core.common.Result

/**
 * E2EE key management and per-message encryption (phases E1 and E2).
 *
 * Phase E1 publishes this device's Signal Protocol key material to the server.
 * Phase E2 encrypts outgoing messages and decrypts incoming ones using libsignal
 * sessions, with the server as a blind relay storing opaque ciphertext envelopes.
 */
interface E2eeRepository {
    /**
     * Generates and publishes this device's identity, signed prekey and a batch
     * of one-time prekeys, if it has not already. Safe to call repeatedly.
     */
    suspend fun ensureRegistered(): Result<Unit>

    /**
     * How many one-time prekeys this device has left. When it runs low the device
     * should upload more, or senders fall back to the weaker no-one-time-prekey
     * variant of X3DH.
     */
    suspend fun unclaimedPreKeyCount(): Result<Long>

    /**
     * Claims prekey bundles for the given recipient devices from the server.
     * Returns bundles keyed by device id plus any devices that had no bundle.
     */
    suspend fun claimPreKeyBundles(deviceIds: List<Long>): Result<ClaimPreKeyBundlesResult>

    /**
     * Encrypts plaintext for the recipient at [address]. Returns the wire envelope
     * type (1 = prekey message, 2 = ratcheted message) and the ciphertext bytes.
     * Establishes a session with the recipient first if none exists yet.
     */
    suspend fun encryptFor(recipientDeviceId: Long, recipientName: String, plaintext: ByteArray): Result<EncryptedMessage>

    /**
     * Decrypts a ciphertext received from the sender at [address], branching on
     * [envelopeType] (1 = prekey message, 2 = ratcheted message).
     */
    suspend fun decryptFrom(senderDeviceId: Long, senderName: String, envelopeType: Int, ciphertext: ByteArray): Result<ByteArray>
}

/** Result of [claimPreKeyBundles]. */
data class ClaimPreKeyBundlesResult(
    /** Successfully claimed bundles, one per recipient device. */
    val bundles: Map<Long, ClaimedPreKeyBundle>,
    /** Devices requested but for which no bundle could be produced. */
    val missing: List<Long>
)

/** A claimed prekey bundle for a single recipient device. */
data class ClaimedPreKeyBundle(
    val deviceId: Long,
    val identityKey: String,
    val registrationId: Int,
    val signedPreKeyId: Int,
    val signedPreKey: String,
    val signedPreKeySig: String,
    val hasOneTimePreKey: Boolean,
    val oneTimePreKeyId: Int?,
    val oneTimePreKey: String?,
    val hasKyberPreKey: Boolean,
    val kyberPreKeyId: Int?,
    val kyberPreKey: String?,
    val kyberPreKeySig: String?
)

/** Result of [encryptFor]: the wire envelope type and serialized ciphertext. */
data class EncryptedMessage(
    /** 1 = PreKeySignal (first message), 2 = Signal (ratcheted) */
    val envelopeType: Int,
    val ciphertext: ByteArray
)
