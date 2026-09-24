package com.coparently.app.data.local.preferences

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [PreferenceBlobCodec]: every type a `SharedPreferences` holds survives, and nothing else is read. */
class PreferenceBlobCodecTest {

    @Test
    fun `every preference type round-trips, separators and non-ASCII included`() {
        val values = mapOf(
            "refresh_token" to "1//0e:x,y\nz",
            "chat_draft_a:b" to "Vyzvednu ji v 17:00 — ďakujem, дякую",
            "token_expiry" to 1_750_000_000_000L,
            "split_ratio_basis_points" to 6_000,
            "scale" to 1.5f,
            "dark_theme" to true,
            "tags" to setOf("a,b", "c:d", ""),
            "empty_set" to emptySet<String>(),
            "empty" to ""
        )

        assertEquals(values, PreferenceBlobCodec.decode(PreferenceBlobCodec.encode(values)))
    }

    @Test
    fun `a null value is left out, as a SharedPreferences would`() {
        assertEquals(emptyMap(), PreferenceBlobCodec.decode(PreferenceBlobCodec.encode(mapOf("k" to null))))
    }

    @Test
    fun `an empty store is a header alone`() {
        assertEquals(PreferenceBlobCodec.HEADER, PreferenceBlobCodec.encode(emptyMap()))
        assertEquals(emptyMap(), PreferenceBlobCodec.decode(PreferenceBlobCodec.HEADER))
    }

    @Test
    fun `a blob of another format or version is refused, not read as empty`() {
        assertFailsWith<IllegalArgumentException> { PreferenceBlobCodec.decode("") }
        assertFailsWith<IllegalArgumentException> { PreferenceBlobCodec.decode("coplanly-prefs-2\n") }
        assertFailsWith<IllegalArgumentException> {
            PreferenceBlobCodec.decode("${PreferenceBlobCodec.HEADER}\nx:a2V5:1")
        }
    }

    @Test
    fun `a value no SharedPreferences can hold is refused`() {
        assertFailsWith<IllegalArgumentException> { PreferenceBlobCodec.encode(mapOf("k" to 1.0)) }
    }
}
