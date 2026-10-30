package com.securechat.core.security

import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the E2EE key manager's invariants against a fake in-memory preference
 * store.
 *
 * These matter because the key material IS the device identity: generating twice,
 * losing the private half, or publishing a public key of the wrong length would
 * either be rejected by the server (409) or silently produce sessions that can
 * never be established.
 */
class E2eeKeyManagerTest {

    private lateinit var store: FakePrefs

    @Before
    fun setUp() {
        store = FakePrefs()
    }

    private fun manager() = E2eeKeyManager(store)

    @Test
    fun `a fresh device is not registered`() {
        assertFalse(manager().isRegistered())
    }

    @Test
    fun `generating publishes correctly sized key material`() {
        val bundle = manager().ensureGenerated()

        assertEquals(32, bundle.identityKey.size)
        assertEquals(32, bundle.signedPreKeyPublic.size)
        assertEquals(64, bundle.signedPreKeySignature.size)
        assertEquals(100, bundle.oneTimePreKeys.size)

        bundle.oneTimePreKeys.forEach { (id, key) ->
            assertEquals(32, key.size)
            assertTrue("one-time prekey ids start at 1 and are contiguous", id in 1..100)
        }
    }

    @Test
    fun `registration id is within the Signal range`() {
        val bundle = manager().ensureGenerated()
        assertTrue("registration id must be positive", bundle.registrationId > 0)
        assertTrue(
            "registration id must be below 16380 (Signal's non-extended range)",
            bundle.registrationId < 16380
        )
    }

    @Test
    fun `a second generation returns the same identity rather than minting a new one`() {
        val manager = manager()
        val first = manager.ensureGenerated()
        assertTrue(manager.isRegistered())

        val second = manager.ensureGenerated()

        assertArrayEquals(first.identityKey, second.identityKey)
        assertArrayEquals(first.signedPreKeyPublic, second.signedPreKeyPublic)
        assertEquals(first.registrationId, second.registrationId)
        assertEquals(first.signedPreKeyId, second.signedPreKeyId)
        assertArrayEquals(first.signedPreKeySignature, second.signedPreKeySignature)
    }

    @Test
    fun `two devices generate different identities`() {
        val a = E2eeKeyManager(FakePrefs()).ensureGenerated()
        val b = E2eeKeyManager(FakePrefs()).ensureGenerated()

        assertFalse(a.identityKey.contentEquals(b.identityKey))
        assertFalse(a.signedPreKeyPublic.contentEquals(b.signedPreKeyPublic))
    }

    @Test
    fun `a reload after generation yields the same public material`() {
        // Simulates an app restart: a new manager instance over the same store,
        // which exercises the stored-key path rather than the generate path.
        val first = manager().ensureGenerated()
        val reloaded = manager().ensureGenerated()

        assertArrayEquals(first.identityKey, reloaded.identityKey)
        assertArrayEquals(first.signedPreKeyPublic, reloaded.signedPreKeyPublic)
        assertEquals(first.oneTimePreKeys.keys, reloaded.oneTimePreKeys.keys)
    }

    @Test
    fun `one-time prekeys are distinct from each other and from the identity`() {
        val bundle = manager().ensureGenerated()

        val allKeys = bundle.oneTimePreKeys.values + listOf(bundle.identityKey, bundle.signedPreKeyPublic)
        val distinct = allKeys.map { it.joinToString(",") }.toSet()
        assertEquals(
            "every published public key must be unique",
            allKeys.size,
            distinct.size
        )
        assertNotEquals(bundle.identityKey.joinToString(","), bundle.signedPreKeyPublic.joinToString(","))
    }
}

/** In-memory [SharedPreferences] standing in for EncryptedSharedPreferences. */
private class FakePrefs : SharedPreferences {
    val map = mutableMapOf<String, Any>()

    private val editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) { if (value == null) map.remove(key) else map[key] = value }
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) map[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) map[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) map[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) map[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) map.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit() = true
        override fun apply() {}
    }

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> = defValues ?: mutableSetOf()
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = key != null && map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = editor
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
