package com.coparently.app.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reading the photograph list off a child document.
 *
 * Every case here is one a real document can be in, and every one of them is on the path that
 * renders a child's medical record — so the rule throughout is "drop it", never "throw".
 */
class ChildInfoPhotosTest {

    @Test
    fun `a document written before this field existed reads as an empty list - not null`() {
        assertEquals(emptyList(), ChildInfoPhotos.decode(null))
    }

    @Test
    fun `the urls survive in the order they were added`() {
        val urls = listOf("https://example.test/a.jpg", "https://example.test/b.jpg")

        assertEquals(urls, ChildInfoPhotos.decode(urls))
    }

    @Test
    fun `a value of the wrong shape is dropped rather than thrown on`() {
        // A hand-edited document, or a newer build writing a shape this one does not know.
        assertEquals(emptyList(), ChildInfoPhotos.decode("https://example.test/a.jpg"))
        assertEquals(emptyList(), ChildInfoPhotos.decode(42))
        assertEquals(emptyList(), ChildInfoPhotos.decode(mapOf("0" to "a.jpg")))
    }

    @Test
    fun `a non-string entry is dropped and its neighbours survive`() {
        assertEquals(
            listOf("https://example.test/a.jpg"),
            ChildInfoPhotos.decode(listOf("https://example.test/a.jpg", 7, null))
        )
    }

    @Test
    fun `a blank url is dropped`() {
        // A failed upload that stored an empty string would otherwise become a thumbnail that can
        // never load and can only be removed by guessing which one it is.
        assertEquals(
            listOf("https://example.test/a.jpg"),
            ChildInfoPhotos.decode(listOf("", "   ", "https://example.test/a.jpg"))
        )
    }

    @Test
    fun `a document without a portrait reads as none`() {
        // Every document an older build wrote, and every one before schema 47.
        assertEquals(null, ChildInfoPhotos.decodePortrait(null))
    }

    @Test
    fun `a portrait reference survives as it is`() {
        val reference = "ph1|child_photos/a__b/c1/p.jpg|image/jpeg|10|" + "a".repeat(64)

        assertEquals(reference, ChildInfoPhotos.decodePortrait(reference))
    }

    @Test
    fun `a portrait of the wrong shape or blank reads as none`() {
        assertEquals(null, ChildInfoPhotos.decodePortrait(""))
        assertEquals(null, ChildInfoPhotos.decodePortrait("   "))
        assertEquals(null, ChildInfoPhotos.decodePortrait(42))
        assertEquals(null, ChildInfoPhotos.decodePortrait(listOf("ph1|child_photos/a__b/c1/p.jpg")))
    }
}
