package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestorePetDataSource
import com.coparently.app.domain.model.Pet
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * The pet delete path (CQ-19).
 *
 * A pet was deleted by removing the Firestore document and discarding the `Result`, which is
 * what `data/sync/Tombstone.kt` exists to forbid. Two failures came out of that, and this class
 * pins both fixes: a *successful* removal left the co-parent's phone nothing to learn from — a
 * vanished document is not a fact that can be delivered, and nothing reconciles by absence — so
 * they kept the pet for ever; and a *refused* one removed the local row anyway, leaving the
 * document alive for the next download to put back.
 *
 * The pet repository had no tests at all before this (CQ-13). These are the delete path only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetRepositoryImplTest {

    private lateinit var petDao: PetDao
    private lateinit var userDao: UserDao
    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var firestorePetDataSource: FirestorePetDataSource
    private lateinit var repository: PetRepositoryImpl

    @Before
    fun setup() {
        petDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        firebaseAuthService = mockk(relaxed = true)
        firestorePetDataSource = mockk(relaxed = true)

        val firebaseUser = mockk<FirebaseUser>(relaxed = true)
        every { firebaseUser.uid } returns ALICE
        every { firebaseAuthService.getCurrentUser() } returns firebaseUser

        repository = PetRepositoryImpl(
            petDao,
            userDao,
            firebaseAuthService,
            firestorePetDataSource
        )
    }

    @Test
    fun `deleting a pet tombstones the document instead of removing it`() = runTest {
        coEvery {
            firestorePetDataSource.tombstonePet(any(), any(), any())
        } returns Result.success(Unit)

        repository.deletePet(pet())

        coVerify { petDao.markDeleted(PET_ID, any()) }
        coVerify { firestorePetDataSource.tombstonePet(PET_ID, any(), ALICE) }
        // Delivered, so the outbox entry has done its job.
        coVerify { petDao.deletePetById(PET_ID) }
    }

    @Test
    fun `a refused tombstone leaves the row queued rather than gone`() = runTest {
        coEvery {
            firestorePetDataSource.tombstonePet(any(), any(), any())
        } returns Result.failure(IOException("offline"))

        repository.deletePet(pet())

        coVerify { petDao.markDeleted(PET_ID, any()) }
        coVerify(exactly = 0) { petDao.deletePetById(PET_ID) }
    }

    @Test
    fun `deleting while signed out queues the deletion for the next sync`() = runTest {
        every { firebaseAuthService.getCurrentUser() } returns null

        repository.deletePet(pet())

        coVerify { petDao.markDeleted(PET_ID, any()) }
        coVerify(exactly = 0) { firestorePetDataSource.tombstonePet(any(), any(), any()) }
        coVerify(exactly = 0) { petDao.deletePetById(any()) }
    }

    @Test
    fun `a pull keeps an edit that has not gone up instead of replacing it with the server's copy`() =
        runTest {
            val edited = with(repository) { pet().copy(name = "Rex (edited)").toEntity() }
            // The upload half runs first and fails, so the edit is still unsynced when the
            // download half meets the older document.
            coEvery { petDao.getUnsyncedPets() } returns listOf(edited)
            coEvery { firestorePetDataSource.upsertPet(any(), any()) } returns
                Result.failure(IOException("offline"))
            coEvery { petDao.getPetById(PET_ID) } returns edited
            val document = with(repository) { pet().toFirestoreMap(listOf(ALICE)) }
            every { firestorePetDataSource.getPetsForParent(ALICE) } returns flowOf(listOf(document))

            repository.pullOnce()

            coVerify(exactly = 0) { petDao.insertPet(any()) }
        }

    @Test
    fun `a pull skips a document that does not parse and takes the rest`() = runTest {
        coEvery { petDao.getUnsyncedPets() } returns emptyList()
        coEvery { petDao.getPetById(any()) } returns null
        val document = with(repository) { pet().toFirestoreMap(listOf(ALICE)) }
        every { firestorePetDataSource.getPetsForParent(ALICE) } returns flowOf(
            listOf(document + ("name" to 42L), document + ("id" to "pet-2"))
        )
        val inserted = mutableListOf<PetEntity>()
        coEvery { petDao.insertPet(capture(inserted)) } returns Unit

        repository.pullOnce()

        assertEquals(listOf("pet-2"), inserted.map { it.id })
    }

    private fun pet() = Pet(
        id = PET_ID,
        name = "Rex",
        createdAt = NOW,
        updatedAt = NOW,
        createdByFirebaseUid = ALICE,
        lastModifiedBy = ALICE
    )

    private companion object {
        const val ALICE = "alice-uid"
        const val PET_ID = "pet-1"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 1, 12, 0)
    }
}
