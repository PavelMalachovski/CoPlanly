package com.coparently.app.presentation.pets

import com.coparently.app.data.platform.Analytics
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.presentation.common.UiText
import com.coparently.app.resources.Res
import com.coparently.app.resources.pets_delete_failed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Saving a pet: the one-shot outcome the editor navigates on, who a save is stamped with, and the
 * photograph contract (uploaded on save, a failed delete keeps its reference, a legacy download
 * URL from before L-4 is dropped).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = LocalDateTime.parse("2026-09-01T09:00:00")
    private val petRepository = mockk<PetRepository>(relaxed = true) {
        every { getAllPets() } returns flowOf(emptyList())
    }
    private val photoStorage = mockk<RecordPhotoStorage>()
    private val authService = mockk<SignedInUser>()
    private val analytics = mockk<Analytics>(relaxed = true)
    private val signedIn = "u1"

    private val rex = Pet(id = "p1", name = "Rex", createdAt = now, updatedAt = now, familyId = FAMILY)

    private fun viewModel() = PetsViewModel(
        petRepository = petRepository,
        photoStorage = photoStorage,
        firebaseAuthService = authService,
        analyticsManager = analytics,
        crashlyticsManager = mockk(relaxed = true)
    )

    /**
     * Collects the one-shot outcomes; a `SharedFlow` without replay drops what nobody hears.
     *
     * On an unconfined dispatcher, so the collector subscribes before this returns:
     * `advanceUntilIdle()` does not run `backgroundScope` work, and a collector launched there on
     * the standard dispatcher never subscribed — every outcome was emitted to nobody.
     */
    private fun TestScope.outcomesOf(vm: PetsViewModel): List<PetSaveOutcome> {
        val outcomes = mutableListOf<PetSaveOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.saveOutcome.collect { outcomes += it }
        }
        return outcomes
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a save with nobody signed in fails and writes nothing`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns null
        val vm = viewModel()
        val outcomes = outcomesOf(vm)

        vm.upsertPet(rex, isNewPet = true)
        advanceUntilIdle()

        assertEquals(listOf(PetSaveOutcome.FAILED), outcomes)
        coVerify(exactly = 0) { petRepository.upsertPet(any()) }
    }

    @Test
    fun `an edit keeps the original creator, stamps the editor and queues the upload`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()
        val outcomes = outcomesOf(vm)

        vm.upsertPet(rex.copy(createdByFirebaseUid = "u2", syncedToFirestore = true), isNewPet = false)
        advanceUntilIdle()

        assertEquals(listOf(PetSaveOutcome.SAVED), outcomes)
        assertEquals("u2", saved.captured.createdByFirebaseUid)
        assertEquals("u1", saved.captured.lastModifiedBy)
        assertEquals(false, saved.captured.syncedToFirestore)
        verify(exactly = 1) { analytics.log("pet_updated", emptyMap()) }
    }

    @Test
    fun `a photo whose delete failed stays on the record`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        coEvery { photoStorage.delete(REF_A, FAMILY) } returns Unit
        coEvery { photoStorage.delete(REF_B, FAMILY) } throws IllegalStateException("denied")
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()
        val outcomes = outcomesOf(vm)

        vm.upsertPetWithPhotos(
            rex.copy(photos = listOf(REF_A, REF_B)),
            isNewPet = false,
            newPhotoUris = emptyList(),
            removedPhotoUrls = listOf(REF_A, REF_B)
        )
        advanceUntilIdle()

        // Dropping the reference anyway would leave an object in the bucket nobody can delete.
        assertEquals(listOf(REF_B), saved.captured.photos)
        assertEquals(PetPhotoError.DELETE_FAILED, vm.photoError.value)
        assertEquals(listOf(PetSaveOutcome.SAVED), outcomes)
    }

    @Test
    fun `a failed upload is reported and the pet is still saved without it`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        coEvery { photoStorage.upload(RecordPhotoKind.PET, "p1", FAMILY, "content://one") } returns REF_A
        coEvery {
            photoStorage.upload(RecordPhotoKind.PET, "p1", FAMILY, "content://two")
        } throws IllegalStateException("rules")
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()
        val outcomes = outcomesOf(vm)

        vm.upsertPetWithPhotos(
            rex,
            isNewPet = true,
            newPhotoUris = listOf("content://one", "content://two"),
            removedPhotoUrls = emptyList()
        )
        advanceUntilIdle()

        assertEquals(listOf(REF_A), saved.captured.photos)
        assertEquals(PetPhotoError.UPLOAD_FAILED, vm.photoError.value)
        assertEquals(listOf(PetSaveOutcome.SAVED), outcomes)
    }

    @Test
    fun `a legacy download URL is dropped on save and never deleted from the client`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()

        vm.upsertPetWithPhotos(
            rex.copy(photos = listOf("https://firebasestorage.googleapis.com/v0/b/x/o/pet?token=t", REF_B)),
            isNewPet = false,
            newPhotoUris = emptyList(),
            removedPhotoUrls = emptyList()
        )
        advanceUntilIdle()

        assertEquals(listOf(REF_B), saved.captured.photos)
        coVerify(exactly = 0) { photoStorage.delete(any(), any()) }
    }

    @Test
    fun `a failed delete is shown as a localised error`() = runTest(dispatcher) {
        coEvery { petRepository.deletePet(any()) } throws IllegalStateException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        vm.deletePet(rex)
        advanceUntilIdle()

        assertEquals(PetsUiState.Error(UiText.Res(Res.string.pets_delete_failed)), vm.uiState.value)
    }

    @Test
    fun `a new portrait goes first and is saved before the old first object is deleted`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        coEvery { photoStorage.upload(RecordPhotoKind.PET, "p1", FAMILY, "content://x") } returns REF_C
        coEvery { photoStorage.delete(REF_A, FAMILY) } returns Unit
        coEvery { petRepository.getPetById("p1") } returns rex.copy(photos = listOf(REF_A, REF_B))
        val vm = viewModel()

        vm.setPhoto(rex.copy(photos = listOf(REF_A, REF_B)), "content://x")
        advanceUntilIdle()

        coVerifyOrder {
            photoStorage.upload(RecordPhotoKind.PET, "p1", FAMILY, "content://x")
            petRepository.upsertPet(match { it.photos == listOf(REF_C, REF_B) })
            photoStorage.delete(REF_A, FAMILY)
        }
    }

    @Test
    fun `a first portrait lands on the record as it stands after the upload`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        coEvery { photoStorage.upload(any(), any(), any(), any()) } returns REF_C
        coEvery { petRepository.getPetById("p1") } returns rex.copy(name = "Rex edited")
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()

        vm.setPhoto(rex, "content://x")
        advanceUntilIdle()

        assertEquals(listOf(REF_C), saved.captured.photos)
        assertEquals("Rex edited", saved.captured.name)
        coVerify(exactly = 0) { photoStorage.delete(any(), any()) }
    }

    @Test
    fun `a portrait whose save failed is deleted again`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns null
        coEvery { photoStorage.upload(any(), any(), any(), any()) } returns REF_C
        coEvery { photoStorage.delete(REF_C, FAMILY) } returns Unit
        coEvery { petRepository.getPetById("p1") } returns rex.copy(photos = listOf(REF_A))
        val vm = viewModel()

        vm.setPhoto(rex.copy(photos = listOf(REF_A)), "content://x")
        advanceUntilIdle()

        coVerify(exactly = 1) { photoStorage.delete(REF_C, FAMILY) }
        coVerify(exactly = 0) { photoStorage.delete(REF_A, any()) }
    }

    @Test
    fun `a failed portrait upload changes nothing`() = runTest(dispatcher) {
        coEvery { photoStorage.upload(any(), any(), any(), any()) } throws IllegalStateException("rules")
        val vm = viewModel()

        vm.setPhoto(rex.copy(photos = listOf(REF_A)), "content://x")
        advanceUntilIdle()

        assertEquals(PetPhotoError.UPLOAD_FAILED, vm.photoError.value)
        coVerify(exactly = 0) { petRepository.upsertPet(any()) }
    }

    @Test
    fun `removing the portrait deletes its object then saves the other photos`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        coEvery { photoStorage.delete(REF_A, FAMILY) } returns Unit
        coEvery { petRepository.getPetById("p1") } returns rex.copy(photos = listOf(REF_A, REF_B))
        val vm = viewModel()

        vm.removePhoto(rex.copy(photos = listOf(REF_A, REF_B)))
        advanceUntilIdle()

        coVerifyOrder {
            photoStorage.delete(REF_A, FAMILY)
            petRepository.upsertPet(match { it.photos == listOf(REF_B) })
        }
    }

    @Test
    fun `a failed delete on removal keeps the portrait`() = runTest(dispatcher) {
        coEvery { photoStorage.delete(any(), any()) } throws IllegalStateException("offline")
        val vm = viewModel()

        vm.removePhoto(rex.copy(photos = listOf(REF_A)))
        advanceUntilIdle()

        assertEquals(PetPhotoError.DELETE_FAILED, vm.photoError.value)
        coVerify(exactly = 0) { petRepository.upsertPet(any()) }
    }

    @Test
    fun `a legacy first photo is dropped on removal without a delete`() = runTest(dispatcher) {
        every { authService.getCurrentUid() } returns signedIn
        val legacy = "https://firebasestorage.googleapis.com/v0/b/x/o/pet?token=t"
        coEvery { petRepository.getPetById("p1") } returns rex.copy(photos = listOf(legacy, REF_B))
        val saved = slot<Pet>()
        coEvery { petRepository.upsertPet(capture(saved)) } returns Unit
        val vm = viewModel()

        vm.removePhoto(rex.copy(photos = listOf(legacy, REF_B)))
        advanceUntilIdle()

        assertEquals(listOf(REF_B), saved.captured.photos)
        coVerify(exactly = 0) { photoStorage.delete(any(), any()) }
    }

    private companion object {
        const val FAMILY = "u1__u2"
        val REF_A = "ph1|pet_photos/u1__u2/p1/a.jpg|image/jpeg|100|" + "a".repeat(64)
        val REF_B = "ph1|pet_photos/u1__u2/p1/b.jpg|image/jpeg|100|" + "b".repeat(64)
        val REF_C = "ph1|pet_photos/u1__u2/p1/c.jpg|image/jpeg|100|" + "c".repeat(64)
    }
}
