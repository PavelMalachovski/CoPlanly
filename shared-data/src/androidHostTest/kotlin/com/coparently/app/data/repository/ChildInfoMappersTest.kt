package com.coparently.app.data.repository

import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.model.ChildInfo
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A child's portrait (`photo`, schema 47) through the four mappers: Room and Firestore keep it,
 * and a document or a row without one reads as none.
 */
@OptIn(InternalSharedApi::class)
class ChildInfoMappersTest {

    @Test
    fun `the portrait survives Room and Firestore`() {
        val child = child(photo = PORTRAIT)

        val throughRoom = child.toEntity().toDomain()
        val document = throughRoom.toFirestoreMap(listOf("alice", "bob"))
        val throughFirestore = document.toChildInfo()

        assertEquals(PORTRAIT, child.toEntity().photo)
        assertEquals(PORTRAIT, throughRoom.photo)
        assertEquals(PORTRAIT, document["photo"])
        assertEquals(PORTRAIT, throughFirestore.photo)
        // The medical photos are a field of their own.
        assertEquals(listOf(MEDICAL), throughFirestore.medicalPhotos)
    }

    @Test
    fun `a child without a portrait is written as none`() {
        val document = child(photo = null).toFirestoreMap(listOf("alice"))

        assertNull(document["photo"])
        assertNull(child(photo = null).toEntity().photo)
    }

    @Test
    fun `a document an older build wrote reads with no portrait`() {
        val document = child(photo = PORTRAIT).toFirestoreMap(listOf("alice")) - "photo"

        assertFalse("photo" in document)
        assertNull(document.toChildInfo().photo)
    }

    @Test
    fun `a blank portrait reads as none`() {
        val document = child(photo = null).toFirestoreMap(listOf("alice")) + ("photo" to "")

        assertNull(document.toChildInfo().photo)
    }

    private fun child(photo: String?) = ChildInfo(
        id = "c1",
        childName = "Emma",
        dateOfBirth = null,
        medicalPhotos = listOf(MEDICAL),
        createdAt = CREATED,
        updatedAt = CREATED,
        createdByFirebaseUid = "alice",
        lastModifiedBy = "alice",
        familyId = "alice__bob",
        photo = photo
    )

    private companion object {
        val CREATED = LocalDateTime(2026, 5, 1, 9, 0)
        val PORTRAIT = "ph1|child_photos/alice__bob/c1/p.jpg|image/jpeg|100|" + "3".repeat(64)
        val MEDICAL = "ph1|medical_photos/alice__bob/c1/m.jpg|image/jpeg|100|" + "1".repeat(64)
    }
}
