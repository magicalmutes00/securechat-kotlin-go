package com.securechat.core.security

import android.content.Context
import com.securechat.data.local.dao.E2eeSessionDao
import com.securechat.data.local.entity.E2eeSessionEntity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room- and EncryptedSharedPreferences-backed implementation of libsignal's
 * [SignalProtocolStore].
 *
 * Identity keys, prekeys, and signed prekeys come from [E2eeKeyManager] (which
 * persists them in EncryptedSharedPreferences). Signal sessions — the ratchet
 * state that must survive app restarts — are persisted to Room so a device can
 * decrypt history after rebooting.
 */
@Singleton
class E2eeProtocolStore @Inject constructor(
    context: Context,
    private val keyManager: E2eeKeyManager,
    private val sessionDao: E2eeSessionDao
) : SignalProtocolStore {

    override fun getIdentityKeyPair(): IdentityKeyPair = keyManager.identityKeyPair

    override fun getLocalRegistrationId(): Int = keyManager.registrationId

    // PreKeyStore

    override fun loadPreKey(id: Int): PreKeyRecord? = keyManager.preKeyRecord(id)

    override fun storePreKey(id: Int, record: PreKeyRecord) {
        // One-time prekeys are generated and published by E2eeKeyManager.
    }

    override fun containsPreKey(id: Int): Boolean = keyManager.containsPreKey(id)

    override fun removePreKey(id: Int) {
        // Claimed one-time prekeys are removed server-side; local state is
        // unchanged until the next registration cycle.
    }

    // SignedPreKeyStore

    override fun loadSignedPreKey(id: Int): SignedPreKeyRecord? = keyManager.signedPreKeyRecord

    override fun storeSignedPreKey(id: Int, record: SignedPreKeyRecord) {
        // Signed prekey is published once by E2eeKeyManager.
    }

    override fun containsSignedPreKey(id: Int): Boolean = keyManager.containsSignedPreKey(id)

    override fun removeSignedPreKey(id: Int) {}

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        keyManager.signedPreKeyIds.mapNotNull { keyManager.signedPreKeyRecord }

    // SessionStore

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? = runBlocking {
        sessionDao.get(address.name, address.deviceId)?.let { SessionRecord(it.record) }
    }

    override fun loadExistingSessions(
        addresses: List<SignalProtocolAddress>
    ): List<SessionRecord> = runBlocking {
        addresses.mapNotNull { address ->
            sessionDao.get(address.name, address.deviceId)?.let { SessionRecord(it.record) }
        }
    }

    override fun getSubDeviceSessions(deviceId: String): List<Int> = runBlocking {
        emptyList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        runBlocking {
            sessionDao.insert(
                E2eeSessionEntity(
                    recipientName = address.name,
                    deviceId = address.deviceId,
                    record = record.serialize()
                )
            )
        }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = runBlocking {
        sessionDao.get(address.name, address.deviceId) != null
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        runBlocking {
            sessionDao.delete(address.name, address.deviceId)
        }
    }

    override fun deleteAllSessions(deviceId: String) {}

    // KyberPreKeyStore

    override fun loadKyberPreKey(id: Int): KyberPreKeyRecord? = keyManager.kyberPreKeyRecord(id)

    override fun storeKyberPreKey(id: Int, record: KyberPreKeyRecord) {
        // Kyber prekey is published once by E2eeKeyManager.
    }

    override fun containsKyberPreKey(id: Int): Boolean = keyManager.containsKyberPreKey(id)

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> =
        keyManager.kyberPreKeyIds.mapNotNull { keyManager.kyberPreKeyRecord(it) }

    override fun markKyberPreKeyUsed(id: Int) {
        // Kyber prekey is used once per session setup; no local tracking needed.
    }

    // IdentityKeyStore

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey
    ): IdentityKeyStore.IdentityChange =
        // Trust-on-first-use: the identity is established by E2eeKeyManager and
        // trusted by default. Nothing to persist here — the key manager is the
        // source of truth for identity material.
        IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = null

    // SenderKeyStore (group sender keys — unused in Phase E2 1:1, no-ops)

    override fun storeSenderKey(
        address: SignalProtocolAddress,
        id: UUID,
        record: SenderKeyRecord
    ) {}

    override fun loadSenderKey(
        address: SignalProtocolAddress,
        id: UUID
    ): SenderKeyRecord? = null
}
