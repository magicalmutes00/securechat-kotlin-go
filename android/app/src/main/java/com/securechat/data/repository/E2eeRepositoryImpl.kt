package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.core.security.E2eeKeyManager
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.PublishIdentityRequest
import com.securechat.data.remote.dto.PublishOneTimePreKeysRequest
import com.securechat.data.remote.dto.PublishSignedPreKeyRequest
import com.securechat.domain.repository.E2eeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes this device's Signal Protocol public keys (E2EE phase E1).
 *
 * Idempotent by design: [ensureRegistered] does nothing once the device has an
 * identity, so calling it on every login is safe and repairs a device whose
 * publish was interrupted. Private keys never leave [E2eeKeyManager].
 */
@Singleton
class E2eeRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val keyManager: E2eeKeyManager
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
            apiService.publishOneTimePreKeys(
                PublishOneTimePreKeysRequest(keys = bundle.oneTimePreKeys)
            )
        }.map { Unit }
    }

    override suspend fun unclaimedPreKeyCount(): Result<Long> =
        apiService.getUnclaimedPreKeyCount()
}
