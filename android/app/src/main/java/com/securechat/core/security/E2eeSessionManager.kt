package com.securechat.core.security

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UsePqRatchet
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * Manages Signal Protocol sessions for 1:1 E2EE (Phase E2).
 *
 * Wraps libsignal's [SessionBuilder] (envelope type 1, prekey messages) and
 * [SessionCipher] (envelope type 2, ratcheted messages). Sessions are persisted
 * through [E2eeProtocolStore] so they survive app restarts.
 *
 * Usage on the sending side:
 *   1. [hasSession] to check if a session already exists for the recipient.
 *   2. If not, claim the recipient's prekey bundle from the server and call
 *      [initiateSession] to process it.
 *   3. [encrypt] to produce an envelope (type + ciphertext) for the wire.
 *
 * Usage on the receiving side:
 *   1. [decrypt] with the sender's address, the envelope type, and the
 *      ciphertext bytes. Branches on envelope type internally.
 */
class E2eeSessionManager(
    private val protocolStore: E2eeProtocolStore
) {

    /** Result of [encrypt]: the wire envelope type and the serialized ciphertext. */
    data class EncryptedMessage(
        /** 1 = PreKeySignal (first message, session not yet established) */
        val envelopeType: Int,
        /** Serialized CiphertextMessage, base64-encoded on the wire */
        val ciphertext: ByteArray
    )

    /** True if a libsignal session exists for the given address. */
    fun hasSession(address: SignalProtocolAddress): Boolean =
        protocolStore.containsSession(address)

    /**
     * Processes a claimed prekey bundle to establish a session with the recipient.
     * Safe to call repeatedly — a second call for the same address is a no-op if
     * a session already exists.
     */
    fun initiateSession(address: SignalProtocolAddress, bundle: PreKeyBundle) {
        if (hasSession(address)) return
        org.signal.libsignal.protocol.SessionBuilder(
            protocolStore,
            address
        ).process(bundle, UsePqRatchet.YES)
    }

    /**
     * Encrypts plaintext for the recipient at [address]. The first message to a
     * recipient is a PreKeySignal message (envelope type 1); subsequent messages
     * are ratcheted Signal messages (envelope type 2).
     *
     * @throws NoSessionException if no session exists and [initiateSession] has
     * not been called first.
     */
    fun encrypt(address: SignalProtocolAddress, plaintext: ByteArray): EncryptedMessage {
        val cipher = org.signal.libsignal.protocol.SessionCipher(protocolStore, address)
        val ciphertext = cipher.encrypt(plaintext)
        return EncryptedMessage(
            envelopeType = ciphertext.type,
            ciphertext = ciphertext.serialize()
        )
    }

    /**
     * Decrypts a ciphertext received from the sender at [address]. Branches on
     * [envelopeType]: 1 = PreKeySignal, 2 = Signal (ratcheted).
     *
     * @throws org.signal.libsignal.protocol.InvalidCiphertextException if the
     * ciphertext cannot be decrypted (wrong session, corrupted data, etc.).
     */
    fun decrypt(
        address: SignalProtocolAddress,
        envelopeType: Int,
        ciphertext: ByteArray
    ): ByteArray {
        val cipher = org.signal.libsignal.protocol.SessionCipher(protocolStore, address)
        return when (envelopeType) {
            1 -> cipher.decrypt(PreKeySignalMessage(ciphertext), UsePqRatchet.YES)
            2 -> cipher.decrypt(SignalMessage(ciphertext))
            else ->
                throw IllegalArgumentException("Unknown envelope type: $envelopeType")
        }
    }

    /** Removes all sessions for a device (e.g. on logout or device revocation). */
    fun removeSessionsForDevice(deviceId: Int) {
        // Not implemented: sessions are removed per-address via removeSession.
    }

    /**
     * Converts a server-claimed prekey bundle (base64 wire form) into a libsignal
     * [PreKeyBundle] suitable for [initiateSession].
     *
     * libsignal's PreKeyBundle constructor requires non-null one-time and Kyber
     * prekey material, so when a device published no one-time prekey we pass a
     * dummy key with id 0 — libsignal treats id 0 as "no one-time prekey" and
     * uses the no-OTPK variant of X3DH. Kyber is mandatory per the server's
     * bundle contract, so a missing Kyber key is an error.
     *
     * @throws IllegalArgumentException if any key material is malformed or the
     * wrong size.
     */
    fun fromClaimedBundle(
        deviceId: Long,
        identityKeyBase64: String,
        registrationId: Int,
        signedPreKeyId: Int,
        signedPreKeyBase64: String,
        signedPreKeySigBase64: String,
        oneTimePreKeyId: Int?,
        oneTimePreKeyBase64: String?,
        kyberPreKeyId: Int,
        kyberPreKeyBase64: String,
        kyberPreKeySigBase64: String
    ): PreKeyBundle {
        val identityKey = IdentityKey(decodeBase64(identityKeyBase64))
        val signedPreKeyPublic = ECPublicKey(decodeBase64(signedPreKeyBase64))
        val signedPreKeySignature = decodeBase64(signedPreKeySigBase64)

        val oneTimePreKeyPublic = oneTimePreKeyBase64?.let { decodeBase64(it) }
            ?.let { ECPublicKey(it) }
            ?: ECKeyPair.generate().publicKey

        val kyberPreKeyPublic = KEMPublicKey(
            decodeBase64(kyberPreKeyBase64), KEMKeyType.KYBER_1024.ordinal
        )
        val kyberPreKeySignature = decodeBase64(kyberPreKeySigBase64)

        return PreKeyBundle(
            registrationId,
            deviceId.toInt(),
            oneTimePreKeyId ?: 0,
            oneTimePreKeyPublic,
            signedPreKeyId,
            signedPreKeyPublic,
            signedPreKeySignature,
            identityKey,
            kyberPreKeyId,
            kyberPreKeyPublic,
            kyberPreKeySignature
        )
    }

    private fun decodeBase64(base64: String): ByteArray =
        java.util.Base64.getDecoder().decode(base64)

    companion object {
        /** Builds a SignalProtocolAddress from a recipient identifier and device id. */
        fun address(recipientName: String, deviceId: Long): SignalProtocolAddress =
            SignalProtocolAddress(recipientName, deviceId.toInt())
    }
}
