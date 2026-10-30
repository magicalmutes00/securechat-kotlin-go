package com.securechat.domain.repository

import com.securechat.core.common.Result

/**
 * Publishing this device's Signal Protocol key material (E2EE phase E1).
 *
 * The server stores public keys only; private keys are generated and kept on the
 * device. See docs/e2ee-design.md for the architecture and the phasing.
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
}
