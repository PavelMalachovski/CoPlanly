package com.coparently.app.presentation.pets

import com.coparently.app.R
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.presentation.common.UiText
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
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
    private val authService = mockk<FirebaseAuthService>()
    private val analytics = mockk<AnalyticsManager>(relaxed = true)
    private val signedIn = mockk<FirebaseUser> { every { uid } returns "u1" }

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
        every { authService.getCurrentUser() } returns null
        val vm = viewModel()
        val outcomes = outcomesOf(vm)

        vm.upsertPet(rex, isNewPet = true)
        advanceUntilIdle()

        assertEquals(listOf(PetSaveOutcome.FAILED), outcomes)
        coVerify(exactly = 0) { petRepository.upsertPet(any()) }
    }

    @Test
    fun `an edit keeps the original creator, stamps the editor and queues the upload`() = runTest(dispatcher) {
        every { authService.getCurrentUser() } returns signedIn
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
        verify(exactly = 1) { analytics.logPetUpdated() }
    }

    @Test
    fun `a photo whose delete failed stays on the record`() = runTest(dispatcher) {
        every { authService.getCurrentUser() } returns signedIn
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
        every { authService.getCurrentUser() } returns signedIn
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
        every { authService.getCurrentUser() } returns signedIn
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

        assertEquals(PetsUiState.Error(UiText.Res(R.string.pets_delete_failed)), vm.uiState.value)
    }

    private companion object {
        const val FAMILY = "u1__u2"
        val REF_A = "ph1|pet_photos/u1__u2/p1/a.jpg|image/jpeg|100|" + "a".repeat(64)
        val REF_B = "ph1|pet_photos/u1__u2/p1/b.jpg|image/jpeg|100|" + "b".repeat(64)
    }
}
