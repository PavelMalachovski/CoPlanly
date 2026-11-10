package com.coparently.app.data.platform

import com.coparently.app.data.local.preferences.PreferenceBlobCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What [SealedSecurePreferences] does when its storage fails. Two rules are `EncryptedPreferences`'
 * on Android (SEC-5): text that cannot be decoded is replaced by an empty store, and storage that
 * cannot be written keeps everything in memory rather than anywhere in clear. The third is the
 * Keychain's own: storage that could not be read at all — locked before the first unlock — is
 * neither deleted nor written, so what it holds is there on the next launch.
 */
class SealedSecurePreferencesTest {

    @Test
    fun `the stored text is the preference blob`() {
        val storage = MemoryTextStore()
        SealedSecurePreferences(storage).putRefreshToken("refresh-1")

        assertEquals(mapOf("refresh_token" to "refresh-1"), PreferenceBlobCodec.decode(storage.text.orEmpty()))
    }

    @Test
    fun `text that cannot be decoded is deleted and the store starts empty`() {
        val storage = MemoryTextStore().apply { text = "not a preference blob" }
        val store = SealedSecurePreferences(storage)

        assertNull(store.getRefreshToken())
        assertEquals(emptyMap(), PreferenceBlobCodec.decode(storage.text.orEmpty()), "replaced, not kept")
        store.putRefreshToken("new")
        assertEquals("new", SealedSecurePreferences(storage).getRefreshToken())
    }

    @Test
    fun `storage that cannot be read is kept and never written`() {
        val keychain = MemoryTextStore()
        SealedSecurePreferences(keychain).putRefreshToken("refresh-1")
        val stored = keychain.text
        val writesBefore = keychain.writes
        var deletes = 0
        // The Keychain before the phone's first unlock: the item is there and cannot be asked for.
        val locked = object : SealedTextStore by keychain {
            override fun read(): StoredText = StoredText.Unreadable("OSStatus -25308")
            override fun delete() {
                deletes++
            }
        }

        val store = SealedSecurePreferences(locked)
        assertNull(store.getRefreshToken(), "nothing could be read")
        store.putRefreshToken("typed-while-locked")
        assertEquals("typed-while-locked", store.getRefreshToken(), "edits live in memory")
        store.clear()

        assertEquals(0, deletes, "a store that could not be read is never deleted")
        assertEquals(writesBefore, keychain.writes, "nor written: no probe and no edit reaches it")
        assertEquals(stored, keychain.text)
        assertEquals("refresh-1", SealedSecurePreferences(keychain).getRefreshToken(), "the next launch reads it")
    }

    @Test
    fun `storage that throws on read is treated as unreadable and kept`() {
        val keychain = MemoryTextStore()
        SealedSecurePreferences(keychain).putRefreshToken("refresh-1")
        val throwing = object : SealedTextStore by keychain {
            override fun read(): StoredText = throw IllegalStateException("Keychain unavailable")
        }

        assertNull(SealedSecurePreferences(throwing).getRefreshToken())
        assertEquals("refresh-1", SealedSecurePreferences(keychain).getRefreshToken())
    }

    @Test
    fun `nothing stored is an empty store that persists`() {
        val storage = MemoryTextStore()
        SealedSecurePreferences(storage).putUserEmail("alice@example.test")

        assertEquals("alice@example.test", SealedSecurePreferences(storage).getUserEmail())
    }

    @Test
    fun `storage that refuses every write keeps the store in memory`() {
        var attempts = 0
        val storage = object : SealedTextStore by MemoryTextStore() {
            override fun write(text: String) {
                attempts++
                throw IllegalStateException("no Keychain")
            }
        }
        val store = SealedSecurePreferences(storage)
        store.putRefreshToken("refresh-1")

        assertEquals("refresh-1", store.getRefreshToken())
        assertEquals(1, attempts, "after the opening probe fails, nothing tries to write again")
    }

    @Test
    fun `every edit is written before it returns`() {
        val storage = MemoryTextStore()
        val store = SealedSecurePreferences(storage)
        val before = storage.writes

        store.putString("a", "1")
        store.remove("a")
        store.removeWithPrefix("nothing_")

        assertEquals(before + 2, storage.writes, "a removal of nothing writes nothing")
        assertTrue(PreferenceBlobCodec.decode(storage.text.orEmpty()).isEmpty())
    }
}
