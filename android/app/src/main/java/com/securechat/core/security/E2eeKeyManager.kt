package com.securechat.core.security

import android.content.SharedPreferences
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and stores this device's Signal Protocol key material (E2EE phase E1).
 *
 * The server receives PUBLIC keys only. Every private key is generated here and
 * never leaves the device — that boundary is what makes the server a blind
 * prekey directory rather than a participant in the protocol.
 *
 * Private material is persisted in the same EncryptedSharedPreferences that holds
 * the auth tokens, so a device keeps one stable identity across restarts and the
 * server never sees a second identity key for it (which the server refuses
 * anyway). E2 will move this into a full libsignal ProtocolStore; the generation
 * and publication contract here does not change when it does.
 */
@Singleton
class E2eeKeyManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {

    /** Public material handed to the server. Nothing here is secret. */
    data class PublishBundle(
        val identityKey: ByteArray,
        val registrationId: Int,
        val signedPreKeyId: Int,
        val signedPreKeyPublic: ByteArray,
        // Computed over the 33-byte serialize() form of the signed prekey public
        // key, which is what libsignal's PreKeyBundle verification checks — not
        // the 32-byte raw form the server stores.
        val signedPreKeySignature: ByteArray,
        val oneTimePreKeys: Map<Int, ByteArray>
    )

    /** True once this device has generated and published its identity. */
    fun isRegistered(): Boolean =
        encryptedPrefs.contains(KEY_IDENTITY_PRIVATE)

    /**
     * Generates a fresh identity, or returns the existing one untouched.
     *
     * Re-running after a successful registration must not mint a second identity:
     * a different identity key is rejected by the server with 409, and rightly so
     * — it would invalidate every session built on the first.
     */
    fun ensureGenerated(): PublishBundle {
        if (isRegistered()) return loadExisting()

        val identity = IdentityKeyPair.generate()
        val registrationId = secureRandomRegistrationId()

        val signedPreKeyId = FIRST_SIGNED_PRE_KEY_ID
        val signedPreKeyPair = ECKeyPair.generate()
        // Signed over the 33-byte serialize() form; see [PublishBundle].
        val signature = identity.privateKey.calculateSignature(
            signedPreKeyPair.publicKey.serialize()
        )
        val signedPreKeyRecord = SignedPreKeyRecord(
            signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signature
        )

        val oneTime = (0 until ONE_TIME_PREKEY_COUNT).associate { index ->
            val id = index + 1
            id to PreKeyRecord(id, ECKeyPair.generate())
        }

        persist(identity, registrationId, signedPreKeyRecord, oneTime)

        return PublishBundle(
            identityKey = identity.publicKey.publicKey.publicKeyBytes,
            registrationId = registrationId,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = signedPreKeyPair.publicKey.publicKeyBytes,
            signedPreKeySignature = signature,
            oneTimePreKeys = oneTime.mapValues { it.value.keyPair.publicKey.publicKeyBytes }
        )
    }

    /**
     * Rebuilds the publish bundle from what is stored, for a device that
     * generated its identity but whose publish did not complete.
     *
     * Public keys are read from storage rather than derived from the private
     * halves: deriving them would need libsignal's ECPrivateKey API surface,
     * which differs between the JVM and Android variants of the library. Storing
     * both halves keeps this file independent of that detail.
     */
    private fun loadExisting(): PublishBundle {
        val signedPreKeyId = encryptedPrefs.getInt(KEY_SIGNED_PREKEY_ID, 0)
        val signature = decode(encryptedPrefs.getString(KEY_SIGNED_PREKEY_SIGNATURE, null))

        val oneTime = decodeMap(encryptedPrefs.getString(KEY_ONE_TIME_PUBLIC, null))

        return PublishBundle(
            identityKey = decode(encryptedPrefs.getString(KEY_IDENTITY_PUBLIC, null)),
            registrationId = encryptedPrefs.getInt(KEY_REGISTRATION_ID, 0),
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = decode(encryptedPrefs.getString(KEY_SIGNED_PREKEY_PUBLIC, null)),
            signedPreKeySignature = signature,
            oneTimePreKeys = oneTime
        )
    }

    private fun persist(
        identity: IdentityKeyPair,
        registrationId: Int,
        signedPreKey: SignedPreKeyRecord,
        oneTime: Map<Int, PreKeyRecord>
    ) {
        val oneTimePublic = oneTime.mapValues { it.value.keyPair.publicKey.publicKeyBytes }
        val oneTimePrivate = oneTime.mapValues { it.value.keyPair.privateKey.serialize() }

        encryptedPrefs.edit()
            .putString(KEY_IDENTITY_PRIVATE, encode(identity.privateKey.serialize()))
            .putString(KEY_IDENTITY_PUBLIC, encode(identity.publicKey.publicKey.publicKeyBytes))
            .putInt(KEY_REGISTRATION_ID, registrationId)
            .putInt(KEY_SIGNED_PREKEY_ID, signedPreKey.id)
            .putString(KEY_SIGNED_PREKEY_PUBLIC, encode(signedPreKey.keyPair.publicKey.publicKeyBytes))
            .putString(KEY_SIGNED_PREKEY_PRIVATE, encode(signedPreKey.keyPair.privateKey.serialize()))
            .putString(KEY_SIGNED_PREKEY_SIGNATURE, encode(signedPreKey.signature))
            .putString(KEY_ONE_TIME_PUBLIC, encodeMap(oneTimePublic))
            .putString(KEY_ONE_TIME_PRIVATE, encodeMap(oneTimePrivate))
            .apply()
    }

    /**
     * Signal's registration id convention: a positive int below 16380 (the
     * non-extended range). Generated locally with a CSPRNG rather than pulled
     * from libsignal, whose helper's signature has churned across versions.
     */
    private fun secureRandomRegistrationId(): Int {
        val bytes = ByteArray(4)
        java.security.SecureRandom().nextBytes(bytes)
        return (Math.abs(java.nio.ByteBuffer.wrap(bytes).int) % MAX_REGISTRATION_ID) + 1
    }

    private fun encode(bytes: ByteArray): String =
        // java.util.Base64 rather than android.util.Base64: identical output, and
        // it works in local JVM unit tests (where android.util.Base64 throws).
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String?): ByteArray {
        require(!value.isNullOrBlank()) { "missing stored key material" }
        return Base64.getDecoder().decode(value)
    }

    private fun encodeMap(map: Map<Int, ByteArray>): String =
        map.entries.joinToString(ENTRY_SEPARATOR) { (id, bytes) ->
            "$id${KEY_VALUE_SEPARATOR}${encode(bytes)}"
        }

    private fun decodeMap(stored: String?): Map<Int, ByteArray> {
        if (stored.isNullOrBlank()) return emptyMap()
        return stored.split(ENTRY_SEPARATOR).associate { entry ->
            val parts = entry.split(KEY_VALUE_SEPARATOR, limit = 2)
            require(parts.size == 2) { "corrupt stored key material" }
            parts[0].toInt() to decode(parts[1])
        }
    }

    companion object {
        private const val KEY_IDENTITY_PRIVATE = "e2ee_identity_private"
        private const val KEY_IDENTITY_PUBLIC = "e2ee_identity_public"
        private const val KEY_REGISTRATION_ID = "e2ee_registration_id"
        private const val KEY_SIGNED_PREKEY_ID = "e2ee_signed_prekey_id"
        private const val KEY_SIGNED_PREKEY_PUBLIC = "e2ee_signed_prekey_public"
        private const val KEY_SIGNED_PREKEY_PRIVATE = "e2ee_signed_prekey_private"
        private const val KEY_SIGNED_PREKEY_SIGNATURE = "e2ee_signed_prekey_signature"
        private const val KEY_ONE_TIME_PUBLIC = "e2ee_one_time_public"
        private const val KEY_ONE_TIME_PRIVATE = "e2ee_one_time_private"

        private const val ENTRY_SEPARATOR = ";"
        private const val KEY_VALUE_SEPARATOR = ":"

        private const val MAX_REGISTRATION_ID = 16380
        private const val FIRST_SIGNED_PRE_KEY_ID = 1
        private const val ONE_TIME_PREKEY_COUNT = 100
    }
}
