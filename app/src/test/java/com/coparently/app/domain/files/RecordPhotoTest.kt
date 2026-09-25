package com.coparently.app.domain.files

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L-4: a record's photograph is stored under a path that names its family (or, before there is
 * one, its uploader), referenced by a `ph1|` string rather than a download URL, and drawn only for
 * the family's two parents. These are the pure rules; `storage.rules` enforces the same paths and
 * `firestore-tests/rules/storage-record-photos.test.js` runs them on the emulator.
 */
class RecordPhotoTest {

    private val familyPath = RecordPhotoPaths.build(RecordPhotoKind.PET, FAMILY, ALICE, "pet-1", "a.jpg")
    private val soloPath = RecordPhotoPaths.build(RecordPhotoKind.PET, null, ALICE, "pet-1", "a.jpg")
    private val familyPhoto = RecordPhoto(familyPath, "image/jpeg", 1024, SHA)
    private val soloPhoto = RecordPhoto(soloPath, "image/jpeg", 1024, SHA)

    @Test
    fun `a path names the family, or the uploader before there is one`() {
        assertEquals("pet_photos/alice__bob/pet-1/a.jpg", familyPath)
        assertEquals("pet_photos/solo_alice/pet-1/a.jpg", soloPath)
        assertEquals("medical_photos", RecordPhotoKind.MEDICAL.prefix)
        assertEquals("receipts", RecordPhotoKind.RECEIPT.prefix)
        assertEquals("event_images", RecordPhotoKind.EVENT.prefix)
    }

    @Test
    fun `a reference survives the round trip unchanged`() {
        val encoded = RecordPhotoCodec.encode(familyPhoto)
        assertEquals("ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|1024|$SHA", encoded)
        assertEquals(familyPhoto, RecordPhotoCodec.decode(encoded))
        assertEquals(soloPhoto, RecordPhotoCodec.decode(RecordPhotoCodec.encode(soloPhoto)))
    }

    @Test
    fun `a legacy download URL or flat path is not a photo`() {
        val legacy = listOf(
            "https://firebasestorage.googleapis.com/v0/b/x.appspot.com/o/receipts%2Fe1.jpg?alt=media&token=t",
            "receipts/e1.jpg",
            "medical_photos/c1/p1.jpg",
            "pet_photos/uidA/pet-1/1.jpg",
            "",
        )
        legacy.forEach { entry ->
            assertNull(RecordPhotoCodec.decode(entry), entry)
            assertFalse(RecordPhotoCodec.isReference(entry), entry)
        }
        assertNull(RecordPhotoCodec.decode(null))
    }

    @Test
    fun `a malformed reference is dropped, not guessed at`() {
        val cases = listOf(
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|x|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|application/pdf|10|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|0|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|${RecordPhotoPolicy.MAX_BYTES}|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|10|ABC",
            "ph1|chat_attachments/alice__bob/m/a.jpg|image/jpeg|10|$SHA",
            "ph1|pet_photos/alice/pet-1/a.jpg|image/jpeg|10|$SHA",
            "ph1|pet_photos/solo_/pet-1/a.jpg|image/jpeg|10|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/x/a.jpg|image/jpeg|10|$SHA",
            "ph1|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|10|$SHA|extra",
            "ph2|pet_photos/alice__bob/pet-1/a.jpg|image/jpeg|10|$SHA",
        )
        cases.forEach { assertNull(RecordPhotoCodec.decode(it), it) }
    }

    @Test
    fun `either parent reads a family path, nobody else does`() {
        assertTrue(RecordPhotoPaths.mayRead(familyPath, ALICE))
        assertTrue(RecordPhotoPaths.mayRead(familyPath, BOB))
        assertFalse(RecordPhotoPaths.mayRead(familyPath, GUEST))
        assertFalse(RecordPhotoPaths.mayRead(familyPath, null))
        assertFalse(RecordPhotoPaths.mayRead(familyPath, ""))
    }

    @Test
    fun `only the uploader reads their own solo path`() {
        assertTrue(RecordPhotoPaths.mayRead(soloPath, ALICE))
        assertFalse(RecordPhotoPaths.mayRead(soloPath, BOB))
    }

    @Test
    fun `a guest, a friend or a professional sees the record without its photo`() {
        val reference = RecordPhotoCodec.encode(familyPhoto)
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", FAMILY, GUEST))
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", FAMILY, null))
        assertEquals(
            ViewablePhoto(listOf(familyPath), "image/jpeg", SHA),
            RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", FAMILY, BOB)
        )
    }

    @Test
    fun `a photo is only followed under its own record, kind and family`() {
        val reference = RecordPhotoCodec.encode(familyPhoto)
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-2", FAMILY, ALICE))
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.MEDICAL, "pet-1", FAMILY, ALICE))
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", "alice__carol", ALICE))
        // A record that names no family yet still shows its uploader's photo.
        assertEquals(
            listOf(familyPath),
            RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", null, ALICE)?.paths
        )
    }

    @Test
    fun `a solo photo resolves to the family's copy once the record has a family`() {
        val reference = RecordPhotoCodec.encode(soloPhoto)
        // The uploader tries the moved copy first and keeps their own as the fallback.
        assertEquals(
            listOf(familyPath, soloPath),
            RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", FAMILY, ALICE)?.paths
        )
        // The co-parent can only ever read the moved copy.
        assertEquals(
            listOf(familyPath),
            RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", FAMILY, BOB)?.paths
        )
        // Before there is a family only the uploader sees it.
        assertEquals(
            listOf(soloPath),
            RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", null, ALICE)?.paths
        )
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", null, BOB))
        // A family the uploader is not in is never where their photo moved to.
        assertNull(RecordPhotoAccess.viewable(reference, RecordPhotoKind.PET, "pet-1", "bob__carol", BOB))
    }

    @Test
    fun `an editor shows local picks as they are and drops what it may not show`() {
        val reference = RecordPhotoCodec.encode(familyPhoto)
        fun model(entry: String) = RecordPhotoAccess.modelFor(entry, RecordPhotoKind.PET, "pet-1", FAMILY, ALICE)
        assertEquals("content://media/1", model("content://media/1"))
        assertEquals("file:///cache/x.jpg", model("file:///cache/x.jpg"))
        assertNull(model("https://example.invalid/x.jpg"))
        assertEquals(ViewablePhoto(listOf(familyPath), "image/jpeg", SHA), model(reference))
    }

    @Test
    fun `saving keeps references and drops legacy values`() {
        val reference = RecordPhotoCodec.encode(familyPhoto)
        assertEquals(
            listOf(reference),
            RecordPhotoAccess.storedReferences(listOf("https://example.invalid/x.jpg", reference, "receipts/e.jpg"))
        )
    }

    @Test
    fun `the policy matches the numbers storage rules were written against`() {
        assertEquals(10L * 1024 * 1024, RecordPhotoPolicy.MAX_BYTES)
        assertEquals(
            setOf("image/jpeg", "image/png", "image/heic", "image/heif", "image/webp"),
            RecordPhotoPolicy.CONTENT_TYPES
        )
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val GUEST = "grandma"
        const val FAMILY = "alice__bob"
        val SHA = "0123456789abcdef".repeat(4)
    }
}
