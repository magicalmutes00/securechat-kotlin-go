package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.core.security.E2eeKeyManager
import com.securechat.core.security.E2eeSessionManager
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.ClaimPreKeyBundlesRequest
import com.securechat.data.remote.dto.ClaimPreKeyBundleResponse
import com.securechat.data.remote.dto.MissingDevice
import com.securechat.data.remote.dto.PublishIdentityRequest
import com.securechat.data.remote.dto.PublishKyberPreKeyRequest
import com.securechat.data.remote.dto.PublishOneTimePreKeysRequest
import com.securechat.data.remote.dto.PublishSignedPreKeyRequest
import com.securechat.domain.repository.ClaimPreKeyBundlesResult
import com.securechat.domain.repository.ClaimedPreKeyBundle
import com.securechat.domain.repository.E2eeRepository
import com.securechat.domain.repository.EncryptedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes this device's Signal Protocol public keys (E2EE phase E1) and
 * encrypts/decrypts per-message envelopes (phase E2).
 *
 * Idempotent by design: [ensureRegistered] does nothing once the device has an
 * identity, so calling it on every login is safe and repairs a device whose
 * publish was interrupted. Private keys never leave [E2eeKeyManager].
 *
 * On the send path, [encryptFor] claims the recipient's prekey bundle, establishes
 * a libsignal session, and returns an envelope (type + ciphertext) for the wire.
 * On the receive path, [decryptFrom] decrypts using the sender's persisted session.
 */
@Singleton
class E2eeRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val keyManager: E2eeKeyManager,
    private val sessionManager: E2eeSessionManager
) : E2eeRepository {

    override suspend fun ensureRegistered(): Result<Unit> = withContext(Dispatchers.IO) {
        if (keyManager.isRegistered()) {
            return@withContext Result.success(Unit)
        }

        val bundle = keyManager.ensureGenerated()

        // Identity first: the server refuses a signed prekey for a device with no
        // identity key to verify its signature against.
        apiService.publishIdentityKey(
            PublishIdentityRequest(
                identity_key = bundle.identityKey,
                registration_id = bundle.registrationId
            )
        ).flatMap {
            apiService.publishSignedPreKey(
                PublishSignedPreKeyRequest(
                    key_id = bundle.signedPreKeyId,
                    public_key = bundle.signedPreKeyPublic,
                    identity_key_signature = bundle.signedPreKeySignature
                )
            )
        }.flatMap {
            apiService.publishKyberPreKey(
                PublishKyberPreKeyRequest(
                    key_id = bundle.kyberPreKeyId,
                    public_key = bundle.kyberPreKey,
                    identity_key_signature = bundle.kyberPreKeySignature
                )
            )
        }.flatMap {
            apiService.publishOneTimePreKeys(
                PublishOneTimePreKeysRequest(keys = bundle.oneTimePreKeys)
            )
        }.map { Unit }
    }

    override suspend fun unclaimedPreKeyCount(): Result<Long> =
        apiService.getUnclaimedPreKeyCount()

    override suspend fun claimPreKeyBundles(deviceIds: List<Long>): Result<ClaimPreKeyBundlesResult> =
        withContext(Dispatchers.IO) {
            apiService.claimPreKeyBundles(ClaimPreKeyBundlesRequest(device_ids = deviceIds))
                .map { response ->
                    val bundles = response.bundles.associate { b ->
                        b.device_id to ClaimedPreKeyBundle(
                            deviceId = b.device_id,
                            identityKey = b.identity_key,
                            registrationId = b.registration_id,
                            signedPreKeyId = b.signed_pre_key_id,
                            signedPreKey = b.signed_pre_key,
                            signedPreKeySig = b.signed_pre_key_sig,
                            hasOneTimePreKey = b.has_one_time_pre_key,
                            oneTimePreKeyId = b.one_time_pre_key_id,
                            oneTimePreKey = b.one_time_pre_key,
                            hasKyberPreKey = b.has_kyber_pre_key,
                            kyberPreKeyId = b.kyber_pre_key_id,
                            kyberPreKey = b.kyber_pre_key,
                            kyberPreKeySig = b.kyber_pre_key_sig
                        )
                    }
                    ClaimPreKeyBundlesResult(
                        bundles = bundles,
                        missing = response.missing.map { it.device_id }
                    )
                }
        }

    override suspend fun encryptFor(
        recipientDeviceId: Long,
        recipientName: String,
        plaintext: ByteArray
    ): Result<EncryptedMessage> = withContext(Dispatchers.IO) {
        val address = com.securechat.core.security.E2eeSessionManager.address(
            recipientName = recipientName,
            deviceId = recipientDeviceId
        )

        if (!sessionManager.hasSession(address)) {
            // Claim the recipient's bundle and establish a session. We need
            // exactly one device here; the caller is responsible for fan-out.
            val result = claimPreKeyBundles(listOf(recipientDeviceId))
            val bundle = if (result.isSuccess) {
                result.getOrThrow().bundles[recipientDeviceId]
            } else {
                return@withContext Result.failure(
                    (result as? Result.Failure)?.exception
                        ?: IllegalStateException("Failed to claim prekey bundle for $recipientDeviceId")
                )
            }

            if (bundle == null) {
                return@withContext Result.failure(
                    IllegalStateException("No prekey bundle for device $recipientDeviceId")
                )
            }

            val signalBundle = sessionManager.fromClaimedBundle(
                deviceId = bundle.deviceId,
                identityKeyBase64 = bundle.identityKey,
                registrationId = bundle.registrationId,
                signedPreKeyId = bundle.signedPreKeyId,
                signedPreKeyBase64 = bundle.signedPreKey,
                signedPreKeySigBase64 = bundle.signedPreKeySig,
                oneTimePreKeyId = bundle.oneTimePreKeyId,
                oneTimePreKeyBase64 = bundle.oneTimePreKey,
                kyberPreKeyId = bundle.kyberPreKeyId ?: 0,
                kyberPreKeyBase64 = bundle.kyberPreKey ?: "",
                kyberPreKeySigBase64 = bundle.kyberPreKeySig ?: ""
            )
            sessionManager.initiateSession(address, signalBundle)
        }

        val encrypted = sessionManager.encrypt(address, plaintext)
        Result.success(
            com.securechat.domain.repository.EncryptedMessage(
                envelopeType = encrypted.envelopeType,
                ciphertext = encrypted.ciphertext
            )
        )
    }

    override suspend fun decryptFrom(
        senderDeviceId: Long,
        senderName: String,
        envelopeType: Int,
        ciphertext: ByteArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val address = com.securechat.core.security.E2eeSessionManager.address(
            recipientName = senderName,
            deviceId = senderDeviceId
        )
        try {
            val plaintext = sessionManager.decrypt(address, envelopeType, ciphertext)
            Result.success(plaintext)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
