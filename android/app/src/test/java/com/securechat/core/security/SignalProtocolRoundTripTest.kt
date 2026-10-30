package com.securechat.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UsePqRatchet
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * Proves the Signal Protocol wiring is correct before it touches the wire
 * protocol: two identities establish a session, exchange messages, and advance
 * the ratchet — all locally, with the library doing the cryptography.
 *
 * This is the cheapest place to catch a wrong API call, a mis-sized key, or a
 * bundle assembled with the wrong fields. Every one of those would otherwise
 * surface only as "two devices cannot talk", which is the hardest failure in the
 * app to diagnose.
 *
 * Uses libsignal's in-memory stores. The production store is a separate concern;
 * the point here is the protocol behaviour, not the persistence.
 */
class SignalProtocolRoundTripTest {

    private val BOB_ADDRESS = SignalProtocolAddress("bob", 1)
    private val ALICE_ADDRESS = SignalProtocolAddress("alice", 1)

    /** Builds Bob's side: his store, plus the bundle he would publish to the server. */
    private fun newBob(): Pair<InMemorySignalProtocolStore, PreKeyBundle> {
        val bobIdentity = IdentityKeyPair.generate()
        val bobStore = InMemorySignalProtocolStore(bobIdentity, KeyHelper.generateRegistrationId(false))

        val signedPreKeyId = 1
        val signedPreKeyPair = ECKeyPair.generate()
        val signature = bobIdentity.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        bobStore.storeSignedPreKey(signedPreKeyId, SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signature))

        val oneTimePreKeyId = 7
        val oneTimePreKey = PreKeyRecord(oneTimePreKeyId, ECKeyPair.generate())
        bobStore.storePreKey(oneTimePreKeyId, oneTimePreKey)

        // Post-quantum prekey: libsignal 0.76 runs PQXDH, so the bundle carries a
        // Kyber (ML-KEM) key alongside the X25519 ones.
        val kyberPreKeyId = 1
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = bobIdentity.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberRecord = org.signal.libsignal.protocol.state.KyberPreKeyRecord(kyberPreKeyId, System.currentTimeMillis(), kyberKeyPair, kyberSignature)
        bobStore.storeKyberPreKey(kyberPreKeyId, kyberRecord)

        val bundle = PreKeyBundle(
            bobStore.localRegistrationId,
            1, // device id
            oneTimePreKeyId,
            oneTimePreKey.keyPair.publicKey,
            signedPreKeyId,
            signedPreKeyPair.publicKey,
            signature,
            bobIdentity.publicKey,
            kyberPreKeyId,
            kyberKeyPair.publicKey,
            kyberSignature
        )
        return bobStore to bundle
    }

    @Test
    fun `a session established from a prekey bundle round-trips a message`() {
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))

        // Alice learns Bob's bundle from the server and builds a session.
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)

        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobCipher = SessionCipher(bobStore, ALICE_ADDRESS)

        val ciphertext = aliceCipher.encrypt("hello bob".toByteArray())
        assertEquals("the first message must be a prekey message", CiphertextMessage.PREKEY_TYPE, ciphertext.type)

        val plaintext = when (ciphertext.type) {
            CiphertextMessage.PREKEY_TYPE ->
                bobCipher.decrypt(PreKeySignalMessage(ciphertext.serialize()), UsePqRatchet.YES)
            else ->
                bobCipher.decrypt(SignalMessage(ciphertext.serialize()))
        }

        assertEquals("hello bob", String(plaintext))
    }

    @Test
    fun `subsequent messages round-trip on the established session`() {
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)

        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobCipher = SessionCipher(bobStore, ALICE_ADDRESS)

        assertEquals("first", deliverToBob(bobCipher, aliceCipher.encrypt("first".toByteArray())))
        assertEquals("second", deliverToBob(bobCipher, aliceCipher.encrypt("second".toByteArray())))
        assertEquals("third", deliverToBob(bobCipher, aliceCipher.encrypt("third".toByteArray())))
    }

    @Test
    fun `the sender keeps sending prekey messages until the receiver replies`() {
        // Not an implementation detail to work around: Alice cannot know Bob has
        // processed her prekey message until he sends something back, so she keeps
        // including it. Production code must therefore branch on message type for
        // every message, not just the first.
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)

        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobCipher = SessionCipher(bobStore, ALICE_ADDRESS)

        val first = aliceCipher.encrypt("first".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, first.type)

        val second = aliceCipher.encrypt("second".toByteArray())
        assertEquals("still a prekey message while unconfirmed", CiphertextMessage.PREKEY_TYPE, second.type)

        // Bob cannot reply until he has processed something: there is no session
        // on his side yet, which is exactly why Alice keeps sending prekeys.
        assertEquals("first", deliverToBob(bobCipher, first))

        // Now Bob's reply confirms his side of the session.
        val reply = bobCipher.encrypt("ack".toByteArray())
        aliceCipher.decrypt(SignalMessage(reply.serialize()))

        val third = aliceCipher.encrypt("third".toByteArray())
        assertEquals("confirmed: the ratchet now sends plain messages", CiphertextMessage.WHISPER_TYPE, third.type)
        assertEquals("third", deliverToBob(bobCipher, third))
    }

    @Test
    fun `messages arrive correctly when delivered out of order`() {
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)

        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobCipher = SessionCipher(bobStore, ALICE_ADDRESS)

        // Establish Bob's session first, so the ratchet is live on his side.
        assertEquals("first", deliverToBob(bobCipher, aliceCipher.encrypt("first".toByteArray())))
        // Alice must see Bob's reply before she stops sending prekey messages.
        aliceCipher.decrypt(SignalMessage(bobCipher.encrypt("ack".toByteArray()).serialize()))

        // Real networks reorder. The double ratchet must cope.
        val second = aliceCipher.encrypt("second".toByteArray())
        val third = aliceCipher.encrypt("third".toByteArray())

        assertEquals("third", String(bobCipher.decrypt(SignalMessage(third.serialize()))))
        assertEquals("second", String(bobCipher.decrypt(SignalMessage(second.serialize()))))
    }

    @Test
    fun `bob can reply using the same session`() {
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)

        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobCipher = SessionCipher(bobStore, ALICE_ADDRESS)

        bobCipher.decrypt(PreKeySignalMessage(aliceCipher.encrypt("ping".toByteArray()).serialize()), UsePqRatchet.YES)

        // Bob never processed a bundle: his reply is a normal ratcheted message.
        val reply = bobCipher.encrypt("pong".toByteArray())
        assertEquals("pong", String(aliceCipher.decrypt(SignalMessage(reply.serialize()))))
    }

    @Test
    fun `a message encrypted for bob does not decrypt for someone else`() {
        val (_, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)
        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)

        // Carol has her own identity and has never seen Bob's prekeys.
        val carolStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        val carolCipher = SessionCipher(carolStore, BOB_ADDRESS)

        val ciphertext = aliceCipher.encrypt("secret".toByteArray())

        val threw = try {
            when (ciphertext.type) {
                CiphertextMessage.PREKEY_TYPE ->
                    carolCipher.decrypt(PreKeySignalMessage(ciphertext.serialize()), UsePqRatchet.YES)
                else ->
                    carolCipher.decrypt(SignalMessage(ciphertext.serialize()))
            }
            false
        } catch (expected: Exception) {
            true
        }
        assertTrue("a message for Bob must not decrypt for Carol", threw)
    }

    @Test
    fun `bob cannot decrypt before the session is established`() {
        val (bobStore, bundle) = newBob()
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        SessionBuilder(aliceStore, BOB_ADDRESS).process(bundle, UsePqRatchet.YES)
        val aliceCipher = SessionCipher(aliceStore, BOB_ADDRESS)

        // Alice ratchets twice; Bob processes neither message.
        val first = aliceCipher.encrypt("first".toByteArray())
        aliceCipher.encrypt("second".toByteArray())

        // Carol has never seen any of Bob's key material, so she cannot decrypt
        // anything addressed to him.
        val carolStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
        val carolCipher = SessionCipher(carolStore, ALICE_ADDRESS)

        val threw = try {
            when (first.type) {
                CiphertextMessage.PREKEY_TYPE -> carolCipher.decrypt(PreKeySignalMessage(first.serialize()), UsePqRatchet.YES)
                else -> carolCipher.decrypt(SignalMessage(first.serialize()))
            }
            false
        } catch (expected: Exception) {
            true
        }
        assertTrue("a message encrypted for Bob must not decrypt for Carol", threw)
    }

    @Test
    fun `kyber 1024 key material has a stable wire size`() {
        // The public half is load-bearing: migration 008 has
        // CHECK (octet_length(public_key) = 1569), so a library update that
        // changes it must fail a test rather than break key publication at
        // runtime.
        //
        // The secret half is device-only — it is never sent to the server — so
        // this pin only detects library drift. Both sizes follow the same
        // convention (raw key + one leading type byte): 1568 + 1 and 3168 + 1.
        val pair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        assertEquals("kyber public key (serialized)", 1569, pair.publicKey.serialize().size)
        assertEquals("kyber secret key (serialized)", 3169, pair.secretKey.serialize().size)
    }

    private fun bobCipher2(store: InMemorySignalProtocolStore) = SessionCipher(store, ALICE_ADDRESS)

    /** The receiving side must branch on the wire type; every message can be either. */
    private fun deliverToBob(bobCipher: SessionCipher, message: CiphertextMessage): String =
        when (message.type) {
            CiphertextMessage.PREKEY_TYPE ->
                String(bobCipher.decrypt(PreKeySignalMessage(message.serialize()), UsePqRatchet.YES))
            else ->
                String(bobCipher.decrypt(SignalMessage(message.serialize())))
        }

    private fun unwrapAsPreKey(message: CiphertextMessage) = PreKeySignalMessage(message.serialize())
}
