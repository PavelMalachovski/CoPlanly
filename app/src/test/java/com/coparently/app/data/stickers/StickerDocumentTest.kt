package com.coparently.app.data.stickers

import com.coparently.app.domain.stickers.StickerKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wire form of `stickers/{id}`: the keys the rules admit, and a reader that refuses what this
 * build cannot word rather than guessing.
 */
class StickerDocumentTest {

    @Test
    fun `a parent's sticker carries exactly the keys the rules admit`() {
        val doc = StickerDocument.parentSticker("uA", "uB", "uA__uB", StickerKind.THANK_YOU, "ts")
        assertEquals(setOf("to", "from", "participants", "familyId", "kind", "createdAt"), doc.keys)
        assertEquals(listOf("uA", "uB"), doc["participants"])
        assertEquals("thank_you", doc["kind"])
    }

    @Test
    fun `the app's sticker comes from the app and names only its receiver`() {
        val doc = StickerDocument.appSticker("uB", "", StickerKind.ON_TIME, "ts")
        assertEquals("app", doc["from"])
        assertEquals(listOf("uB"), doc["participants"])
    }

    @Test
    fun `a stored sticker reads back, the app's with no sender`() {
        val read = StickerDocument.read(
            "app_uB_on_time_2026-W43",
            mapOf("to" to "uB", "from" to "app", "familyId" to "", "kind" to "on_time", "createdAt" to 5L)
        )
        assertEquals(null, read?.fromUid)
        assertEquals(StickerKind.ON_TIME, read?.kind)
        assertEquals(5L, read?.createdAtMillis)
        assertNull(read?.seenAtMillis)
    }

    @Test
    fun `an unknown kind or no receiver is not drawn`() {
        assertNull(StickerDocument.read("s", mapOf("to" to "uB", "from" to "uA", "kind" to "newer_kind")))
        assertNull(StickerDocument.read("s", mapOf("from" to "uA", "kind" to "thank_you")))
        assertNull(StickerDocument.read("s", mapOf("to" to 7, "from" to "uA", "kind" to "thank_you")))
    }
}
