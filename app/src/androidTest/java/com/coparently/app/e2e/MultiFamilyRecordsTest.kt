package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * One parent in two families (M-8): a child's and a pet's record stay in the family they were
 * entered for, whichever family the phone is showing when it writes or syncs.
 *
 * The leak this pins. Alice co-parents with Bob and, later, with Carol. With the Carol family on
 * screen, every writer of a child's or a pet's audience named Carol — the co-parent of the family
 * being looked at — so one `performFullSync` after the switch re-uploaded every child Alice had
 * entered for the Bob family with `sharedWith: [alice, carol]`: a child's medical details readable
 * by Carol and gone from Bob, while the pet pull added Carol beside Bob. The rules accepted it.
 *
 * What runs is what ships, against the real `firestore.rules`: Alice's `ChildInfoRepositoryImpl`,
 * `PetRepositoryImpl` and `SyncService` — the edit made on screen and the sync after it, the two
 * writers that leaked — and Bob's and Carol's own Firestore clients and repositories reading as
 * their phones would. Each record must end up with the Bob family's audience **and** be marked
 * synced on Alice's phone: the second half is what proves her phone computed an audience the rules
 * accept, rather than the rules quietly refusing a leaking client.
 *
 * Not `MultiFamilyTest`, which is about events and the thread an announcement lands in.
 */
@RunWith(AndroidJUnit4::class)
class MultiFamilyRecordsTest : TwoParentTest() {

    @Test
    fun aBobFamilyChildStaysOutOfCarolsReadsAfterAliceSwitchesAndSyncs() = runBlocking<Unit> {
        val bobFamily = FamilyKey.of(alice.uid, bob.uid)
        val child = newChild(alice, "Mia")
        alice.childInfoRepository.upsertChildInfo(child)
        // One pass while the Bob family is Alice's only one: its backfill runs for Bob.
        alice.syncService.performFullSync().getOrThrow()

        val carol = joinCarolAndShowHerFamily()

        EmulatorEnvironment.step("child: an edit and a sync with the Carol family on screen")
        val stored = checkNotNull(alice.childInfoRepository.getChildInfoById(child.id))
        alice.childInfoRepository.upsertChildInfo(
            stored.copy(
                medicalNotes = "Inhaler before sport",
                lastModifiedBy = alice.uid,
                syncedToFirestore = false,
                updatedAt = now()
            )
        )
        alice.syncService.performFullSync().getOrThrow()

        val remote = checkNotNull(bob.firestore.collection("child_info").document(child.id).get().await().data) {
            "Bob can no longer read the child Alice entered for his family"
        }
        assertEquals(bobFamily, remote["familyId"])
        assertEquals(setOf(alice.uid, bob.uid), (remote["sharedWith"] as List<*>).toSet())
        assertEquals("Inhaler before sport", remote["medicalNotes"])
        assertTrue(
            "Alice's upload was refused: her phone named somebody outside the Bob family",
            checkNotNull(alice.database.childInfoDao().getChildInfoById(child.id)).syncedToFirestore
        )

        assertRefused("Carol reading the Bob family's child") {
            carol.firestore.collection("child_info").document(child.id).get().await()
        }
        carol.childInfoRepository.pullOnce()
        assertNull("the child reached Carol's phone", carol.childInfoRepository.getChildInfoById(child.id))

        // The switch is not news for Carol: no backfill announcement, no edit push with the name.
        val toCarol = alice.queuedFor(carol.uid).map { dataOf(it)[PushPayload.TYPE] }
        assertTrue(
            "Carol was told about the Bob family's records: $toCarol",
            toCarol.none { it == PushPayload.RECORDS_SHARED || it == PushPayload.CHILD_INFO_UPDATED }
        )
    }

    @Test
    fun aBobFamilyPetStaysOutOfCarolsReadsAfterAliceSwitchesAndSyncs() = runBlocking<Unit> {
        val bobFamily = FamilyKey.of(alice.uid, bob.uid)
        val pet = newPet(alice, "Rex")
        alice.petRepository.upsertPet(pet)
        alice.syncService.performFullSync().getOrThrow()

        val carol = joinCarolAndShowHerFamily()

        EmulatorEnvironment.step("pet: an edit and a sync with the Carol family on screen")
        val stored = checkNotNull(alice.petRepository.getPetById(pet.id))
        alice.petRepository.upsertPet(
            stored.copy(
                feedingNotes = "Once a day",
                lastModifiedBy = alice.uid,
                syncedToFirestore = false,
                updatedAt = now()
            )
        )
        // The pull's audience repair runs inside the sync: it used to add Carol beside Bob.
        alice.syncService.performFullSync().getOrThrow()

        val remote = checkNotNull(bob.firestore.collection("pets").document(pet.id).get().await().data) {
            "Bob can no longer read the pet Alice entered for his family"
        }
        assertEquals(bobFamily, remote["familyId"])
        assertEquals(setOf(alice.uid, bob.uid), (remote["sharedWith"] as List<*>).toSet())
        assertEquals("Once a day", remote["feedingNotes"])
        assertTrue(
            "Alice's upload was refused: her phone named somebody outside the Bob family",
            checkNotNull(alice.database.petDao().getPetById(pet.id)).syncedToFirestore
        )

        assertRefused("Carol reading the Bob family's pet") {
            carol.firestore.collection("pets").document(pet.id).get().await()
        }
        carol.petRepository.pullOnce()
        assertNull("the pet reached Carol's phone", carol.petRepository.getPetById(pet.id))
    }

    /**
     * Carol accepts Alice's second invitation, Alice's phone learns of both families, and the
     * Carol family goes on screen — the state the leak needed.
     */
    private suspend fun joinCarolAndShowHerFamily(): EmulatorParent {
        val carol = newParent("Carol")
        // Alice's own snapshot keeps naming Bob — the server pins `partnerId` to the first
        // relationship — so only Carol's phone can be awaited here (as in `MultiFamilyTest`).
        val invite = alice.pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(alice.uid)

        learnFamiliesFromServer(alice)
        assertEquals(2, alice.selectedFamilySource.families().size)
        assertNotNull(alice.selectedFamilySource.select(FamilyKey.of(alice.uid, carol.uid)))
        assertEquals(carol.uid, alice.parentsSource.coParentUid())
        return carol
    }

    /**
     * The step of `SyncService.syncUserData` that brings a second co-parent onto this phone, done
     * before the switch so there is a second family to switch to. `MultiFamilyTest` repeats the
     * same two statements for the same reason; keep both in step with `syncUserData`.
     */
    private suspend fun learnFamiliesFromServer(parent: EmulatorParent) {
        val remote = checkNotNull(parent.userRepository.getRemoteUserProfile(parent.uid))
        val local = checkNotNull(parent.database.userDao().getUserById(parent.uid))
        val withEveryFamily = local.copy(partnerIdsJson = Gson().toJson(remote.partnerIds))
        parent.database.userDao().updateUser(withEveryFamily)
        parent.selectedFamilySource.reconcile()
    }

    /** Fails unless [block] is refused by the security rules. */
    private suspend fun assertRefused(what: String, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue(
            "$what was not refused (got $failure)",
            failure is FirebaseFirestoreException &&
                failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    /** The `data` payload of a queued push document. */
    private fun dataOf(doc: Map<String, Any?>): Map<*, *> = doc["data"] as? Map<*, *> ?: emptyMap<String, Any?>()

    /** A child as `ChildInfoViewModel` saves one for [parent], with health details to leak. */
    private fun newChild(parent: EmulatorParent, name: String): ChildInfo = ChildInfo(
        id = UUID.randomUUID().toString(),
        childName = name,
        dateOfBirth = LocalDateTime.of(BIRTH_YEAR, BIRTH_MONTH, BIRTH_DAY, 0, 0),
        medications = listOf(Medication(name = "Salbutamol", dosage = "100 mcg", frequency = "as needed")),
        allergies = listOf("Peanuts"),
        medicalNotes = "Mild asthma",
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    /** A pet as `PetsViewModel` saves one for [parent]. */
    private fun newPet(parent: EmulatorParent, name: String): Pet = Pet(
        id = UUID.randomUUID().toString(),
        name = name,
        species = PetSpecies.DOG,
        medications = listOf(Medication(name = "Heartworm tablet", dosage = "1", frequency = "monthly")),
        feedingNotes = "Twice a day, no chicken",
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    /** Whole seconds: the wire carries `updatedAt` as an instant and drops what is finer. */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

    private companion object {
        const val BIRTH_YEAR = 2019
        const val BIRTH_MONTH = 5
        const val BIRTH_DAY = 4
    }
}
