package com.coparently.app.domain.files

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MON-23's client-side check, pinned to the values `storage.rules` and `firestore.rules` were
 * written against — a drift here is a file the app uploads and the server refuses, or the reverse.
 */
class SharedFilePolicyTest {

    @Test
    fun `the cap and the types are the ones the rules enforce`() {
        assertEquals(20L * 1024 * 1024, SharedFilePolicy.MAX_BYTES)
        assertEquals(
            setOf("application/pdf", "image/jpeg", "image/png", "image/heic", "image/heif", "image/webp"),
            SharedFilePolicy.CONTENT_TYPES
        )
    }

    @Test
    fun `a file at the cap is refused, one byte under is not`() {
        val atCap = SharedFilePolicy.check("application/pdf", SharedFilePolicy.MAX_BYTES)
        assertEquals(SharedFilePolicy.Rejection.SIZE, atCap)
        assertNull(SharedFilePolicy.check("application/pdf", SharedFilePolicy.MAX_BYTES - 1))
        assertEquals(SharedFilePolicy.Rejection.SIZE, SharedFilePolicy.check("image/png", 0))
    }

    @Test
    fun `an unknown type is refused before its size is looked at`() {
        assertEquals(SharedFilePolicy.Rejection.TYPE, SharedFilePolicy.check("application/zip", 10))
        assertEquals(SharedFilePolicy.Rejection.TYPE, SharedFilePolicy.check(null, 10))
    }

    @Test
    fun `the extension stands in for a provider that reports octet-stream`() {
        assertEquals("application/pdf", SharedFilePolicy.resolveContentType("application/octet-stream", "Order.PDF"))
        assertEquals("image/jpeg", SharedFilePolicy.resolveContentType("IMAGE/JPEG", "x"))
        assertNull(SharedFilePolicy.resolveContentType(null, "notes.txt"))
    }

    @Test
    fun `a file name cannot change the folder or break the chat reference`() {
        assertEquals("a_b_c.pdf", SharedFilePolicy.safeFileName("a/b|c.pdf", "application/pdf"))
        assertEquals("x_y.jpg", SharedFilePolicy.safeFileName("x\\y.jpg", "image/jpeg"))
        assertEquals("file.pdf", SharedFilePolicy.safeFileName("  ", "application/pdf"))
        assertEquals("file.jpg", SharedFilePolicy.safeFileName(null, "image/jpeg"))
        assertEquals("hidden.png", SharedFilePolicy.safeFileName("..hidden.png", "image/png"))
    }

    @Test
    fun `a long name is shortened from its stem and keeps its extension`() {
        val name = SharedFilePolicy.safeFileName("a".repeat(300) + ".pdf", "application/pdf")
        assertEquals(SharedFilePolicy.MAX_NAME_LENGTH, name.length)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `hex is lowercase, two digits a byte, whatever the locale`() {
        assertEquals("00ff10ab", byteArrayOf(0, -1, 16, -85).toHex())
    }
}
