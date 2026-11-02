package com.coparently.app.data.platform

import com.coparently.app.data.local.preferences.PreferenceBlobCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What [SealedSecurePreferences] does when its storage fails, the rules `EncryptedPreferences`
 * follows on Android (SEC-5): an unreadable store starts again empty, and a store that cannot be
 * written keeps everything in memory rather than anywhere in clear.
 */
class SealedSecurePreferencesTest {

    @Test
    fun `the stored text is the preference blob`() {
        val storage = MemoryTextStore()
        SealedSecurePreferences(storage).putRefreshToken("refresh-1")

        assertEquals(mapOf("refresh_token" to "refresh-1"), PreferenceBlobCodec.decode(storage.text.orEmpty()))
    }

    @Test
    fun `text that cannot be read is replaced by an empty store`() {
        val storage = MemoryTextStore().apply { text = "not a preference blob" }
        val store = SealedSecurePreferences(storage)

        assertNull(store.getRefreshToken())
        store.putRefreshToken("new")
        assertEquals("new", SealedSecurePreferences(storage).getRefreshToken())
    }

    @Test
    fun `storage that refuses to be read is replaced by an empty store`() {
        val storage = object : SealedTextStore by MemoryTextStore() {
            override fun read(): String = throw IllegalStateException("item unreadable")
        }

        assertNull(SealedSecurePreferences(storage).getRefreshToken())
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
