package com.coparently.app.data.consent

import com.coparently.app.domain.consent.HEALTH_CONSENT_VERSION
import com.coparently.app.domain.consent.HealthConsent
import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.MedicalPhotoStorage
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The child-health consent (GDPR Art. 9(2)(a)): agreeing records the wording version and the
 * moment, and withdrawing clears the consent and the health data of exactly the records this
 * parent created — never the co-parent's, which rest on the co-parent's own consent.
 */
class HealthConsentManagerTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val childInfoRepository = mockk<ChildInfoRepository>(relaxed = true)
    private val photoStorage = mockk<MedicalPhotoStorage>(relaxed = true)
    private val manager = HealthConsentManager(userRepository, childInfoRepository, photoStorage)

    @Test
    fun `agreeing records the current wording version and the moment`() = runTest {
        val before = System.currentTimeMillis()

        val granted = manager.grant()

        val after = System.currentTimeMillis()
        val stored = slot<HealthConsent>()
        coVerify(exactly = 1) { userRepository.setHealthConsent(capture(stored)) }
        assertEquals(granted, stored.captured)
        assertEquals(HEALTH_CONSENT_VERSION, stored.captured.version)
        assertTrue(stored.captured.atMillis in before..after, "the time is not the moment of agreeing")
    }

    @Test
    fun `withdrawing clears the consent and only this parent's children's health data`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns ME
        val mine = child("mine", createdBy = ME, photos = listOf(PHOTO))
        val mineWithNothing = child("mine-empty", createdBy = ME).withoutHealthData()
        val theirs = child("theirs", createdBy = CO_PARENT)
        every { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(mine, mineWithNothing, theirs))

        val outcome = manager.withdraw()

        assertEquals(HealthConsentWithdrawal.WITHDRAWN, outcome)
        coVerify(exactly = 1) { userRepository.setHealthConsent(null) }
        coVerify(exactly = 1) { photoStorage.deleteMedicalPhoto(PHOTO) }

        val saved = mutableListOf<ChildInfo>()
        coVerify { childInfoRepository.upsertChildInfo(capture(saved)) }
        assertEquals(listOf("mine"), saved.map { it.id }, "only this parent's record with data is written")
        val cleared = saved.single()
        assertTrue(cleared.medications.isEmpty())
        assertTrue(cleared.allergies.isEmpty())
        assertNull(cleared.medicalNotes)
        assertEquals(MedicalProfile(), cleared.medicalProfile)
        assertTrue(cleared.medicalPhotos.isEmpty())
        // Everything the consent does not cover stays, and the write is queued as this parent's.
        assertEquals(mine.childName, cleared.childName)
        assertEquals(mine.emergencyContacts, cleared.emergencyContacts)
        assertEquals(ME, cleared.lastModifiedBy)
        assertEquals(false, cleared.syncedToFirestore)
    }

    @Test
    fun `a photo that cannot be deleted stops the withdrawal and keeps the consent`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns ME
        val mine = child("mine", createdBy = ME, photos = listOf(PHOTO, OTHER_PHOTO))
        every { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(mine))
        coEvery { photoStorage.deleteMedicalPhoto(OTHER_PHOTO) } throws IllegalStateException("offline")

        val outcome = manager.withdraw()

        assertEquals(HealthConsentWithdrawal.PHOTOS_NOT_DELETED, outcome)
        coVerify(exactly = 0) { userRepository.setHealthConsent(any()) }
        // The photo whose object is gone leaves the record; nothing else changes.
        val saved = slot<ChildInfo>()
        coVerify(exactly = 1) { childInfoRepository.upsertChildInfo(capture(saved)) }
        assertEquals(listOf(OTHER_PHOTO), saved.captured.medicalPhotos)
        assertEquals(mine.allergies, saved.captured.allergies)
        assertEquals(mine.medicalProfile, saved.captured.medicalProfile)
    }

    @Test
    fun `withdrawing while signed out changes nothing`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns null

        assertEquals(HealthConsentWithdrawal.SIGNED_OUT, manager.withdraw())

        coVerify(exactly = 0) { userRepository.setHealthConsent(any()) }
        coVerify(exactly = 0) { childInfoRepository.upsertChildInfo(any()) }
    }

    private fun child(id: String, createdBy: String, photos: List<String> = emptyList()) = ChildInfo(
        id = id,
        childName = "Child $id",
        dateOfBirth = null,
        medications = listOf(Medication("Salbutamol", "2 puffs", "as needed")),
        allergies = listOf("peanuts"),
        medicalNotes = "Inhaler in the school bag",
        emergencyContacts = listOf(EmergencyContact("Grandma", "grandmother", "+420123456789")),
        medicalProfile = MedicalProfile(bloodType = BloodType.A_POSITIVE, intolerances = listOf("lactose")),
        medicalPhotos = photos,
        createdAt = CREATED,
        updatedAt = CREATED,
        createdByFirebaseUid = createdBy,
        lastModifiedBy = createdBy,
        syncedToFirestore = true
    )

    private companion object {
        const val ME = "alice"
        const val CO_PARENT = "bob"
        const val PHOTO = "https://storage.example/medical_photos/mine/1.jpg"
        const val OTHER_PHOTO = "https://storage.example/medical_photos/mine/2.jpg"
        val CREATED: LocalDateTime = LocalDateTime.of(2026, 5, 1, 9, 0)
    }
}
