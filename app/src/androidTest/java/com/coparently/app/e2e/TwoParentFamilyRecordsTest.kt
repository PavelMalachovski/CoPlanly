package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The family's own records between two phones: children and pets, and the two pushes the
 * child record and the pairing backfill produce. (Budgets had a case here until their client was
 * removed; the `budgets` rules are covered by `firestore-tests/` alone now.)
 *
 * Alice writes through the production repositories, stamped as `ChildInfoViewModel` and
 * `PetsViewModel` stamp a save (`createdByFirebaseUid`, `lastModifiedBy`); Bob reads through his
 * own repositories — `pullOnce()` for children and pets — so what is checked is what lands in
 * *his* Room, against the real `firestore.rules`.
 * A deletion is a tombstone Bob's download answers by dropping the row (CLAUDE.md item 14, CQ-19).
 *
 * Two pushes. **`child_info_updated`** is queued by `SyncService` when it uploads a child edit
 * that had not reached the server. (A second producer, the legacy `onChildInfoUpdated` trigger,
 * queued English text with the editor's e-mail address to whichever family the creator was
 * showing, and was deleted.) **`records_shared`** is the one push a
 * pairing backfill sends (item 22): records a parent made before pairing are re-uploaded for the
 * new co-parent silently, and announced once.
 *
 * The photographs on a child's medical notes and on a pet are `TwoParentRecordPhotosTest`'s (L-4).
 */
@RunWith(AndroidJUnit4::class)
class TwoParentFamilyRecordsTest : TwoParentTest() {

    @Test
    fun aChildAliceSavesReachesBobIntactAndItsDeletionArrivesAsATombstone() = runBlocking<Unit> {
        val child = newChild(alice, "Mia")
        alice.childInfoRepository.upsertChildInfo(child)

        bob.childInfoRepository.pullOnce()
        val onBobsPhone = checkNotNull(bob.childInfoRepository.getChildInfoById(child.id)) {
            "Bob's download did not bring Alice's child"
        }
        assertEquals(child.childName, onBobsPhone.childName)
        assertEquals(child.dateOfBirth, onBobsPhone.dateOfBirth)
        assertEquals(child.allergies, onBobsPhone.allergies)
        assertEquals(child.medications, onBobsPhone.medications)
        assertEquals(child.medicalNotes, onBobsPhone.medicalNotes)
        assertEquals(child.schoolInfo, onBobsPhone.schoolInfo)
        assertEquals(alice.uid, onBobsPhone.createdByFirebaseUid)
        assertEquals(FamilyKey.of(alice.uid, bob.uid), onBobsPhone.familyId)

        alice.childInfoRepository.deleteChildInfo(child)
        // A tombstone, never a removal: the document stays, readable by Bob, carrying the deletion.
        val remote = bob.firestore.collection("child_info").document(child.id).get().await().data
        assertNotNull("the tombstone is not readable by Bob", remote)
        assertTrue(Tombstone.isDeleted(remote!!))
        assertEquals(alice.uid, remote[Tombstone.DELETED_BY])

        bob.childInfoRepository.pullOnce()
        assertNull(bob.childInfoRepository.getChildInfoById(child.id))
        assertNull(bob.database.childInfoDao().getChildInfoById(child.id))
    }

    @Test
    fun aChildEditTheSyncUploadsQueuesChildInfoUpdatedForBob() = runBlocking<Unit> {
        // One pass first, so the pairing backfill has run and armed its marker: an upload it
        // re-queues is announced as `records_shared`, not per child.
        alice.syncService.performFullSync().getOrThrow()
        val child = newChild(alice, "Leo")
        alice.childInfoRepository.upsertChildInfo(child)

        // An edit whose upload did not land: what `upsertChildInfo` leaves in Room when its
        // `set()` fails — the row edited and still unsynced — for the sync to carry up.
        val dao = alice.database.childInfoDao()
        val stored = checkNotNull(dao.getChildInfoById(child.id))
        dao.updateChildInfo(stored.copy(medicalNotes = "Inhaler before sport", syncedToFirestore = false))

        alice.syncService.performFullSync().getOrThrow()

        // The client's own document: `FcmService` stamps `createdAt` as epoch millis.
        val push = alice.queuedFor(bob.uid).firstOrNull { doc ->
            doc["createdAt"] is Long && dataOf(doc)[PushPayload.TYPE] == PushPayload.CHILD_INFO_UPDATED &&
                dataOf(doc)[PushPayload.CHILD_INFO_ID] == child.id
        }
        assertNotNull("the sync queued no child_info_updated for Bob", push)
        val data = dataOf(push!!)
        assertEquals(child.childName, data[PushPayload.SUBJECT])
        assertEquals(FamilyKey.of(alice.uid, bob.uid), data[PushPayload.FAMILY_ID])
        assertTrue("a client push must not carry its own text (SEC-3)", "title" !in data && "body" !in data)

        val remote = checkNotNull(bob.firestore.collection("child_info").document(child.id).get().await().data)
        assertEquals("Inhaler before sport", remote["medicalNotes"])
    }

    @Test
    fun aPetAliceSavesReachesBobAndItsDeletionArrivesAsATombstone() = runBlocking<Unit> {
        val pet = newPet(alice, "Rex")
        alice.petRepository.upsertPet(pet)

        bob.petRepository.pullOnce()
        val onBobsPhone = checkNotNull(bob.petRepository.getPetById(pet.id)) {
            "Bob's download did not bring Alice's pet"
        }
        assertEquals(pet.name, onBobsPhone.name)
        assertEquals(pet.species, onBobsPhone.species)
        assertEquals(pet.breed, onBobsPhone.breed)
        assertEquals(pet.medications, onBobsPhone.medications)
        assertEquals(pet.vaccinations, onBobsPhone.vaccinations)
        assertEquals(pet.feedingNotes, onBobsPhone.feedingNotes)
        assertEquals(pet.vetPhone, onBobsPhone.vetPhone)
        assertEquals(FamilyKey.of(alice.uid, bob.uid), onBobsPhone.familyId)

        alice.petRepository.deletePet(pet)
        val remote = bob.firestore.collection("pets").document(pet.id).get().await().data
        assertNotNull("the tombstone is not readable by Bob", remote)
        assertTrue(Tombstone.isDeleted(remote!!))

        bob.petRepository.pullOnce()
        assertNull(bob.petRepository.getPetById(pet.id))
        assertNull(bob.database.petDao().getPetById(pet.id))
    }

    @Test
    fun recordsMadeBeforePairingAreSharedWithTheNewCoParentAndAnnouncedOnce() = runBlocking<Unit> {
        val carol = newParent("Carol")
        val dan = newParent("Dan")
        // Carol fills in her family before there is anybody to share it with.
        val child = newChild(carol, "Noah")
        carol.childInfoRepository.upsertChildInfo(child)
        val event = newEvent(carol, "Swimming lesson")
        carol.eventRepository.insertEvent(event)

        pair(inviter = carol, accepter = dan)
        EmulatorEnvironment.step("records_shared: Carol's first sync as a pair")
        carol.syncService.performFullSync().getOrThrow()

        val announced = carol.queuedFor(dan.uid).filter { dataOf(it)[PushPayload.TYPE] == PushPayload.RECORDS_SHARED }
        assertEquals("the backfill must be announced exactly once", 1, announced.size)
        assertEquals(carol.name, dataOf(announced.single())[PushPayload.ACTOR])
        // …and not record by record: no per-record push from the client for what was re-uploaded.
        val perRecord = carol.queuedFor(dan.uid).filter { doc ->
            doc["createdAt"] is Long && dataOf(doc)[PushPayload.TYPE].let { it is String && it in PER_RECORD_TYPES }
        }
        assertTrue("re-uploaded records were announced one by one: $perRecord", perRecord.isEmpty())

        // What the push announces is true: Dan can now read both.
        dan.childInfoRepository.pullOnce()
        assertNotNull(dan.childInfoRepository.getChildInfoById(child.id))
        assertNotNull(dan.eventRepository.fetchRemoteEvent(event.id))

        // A second pass has nothing new to share, and says nothing.
        carol.syncService.performFullSync().getOrThrow()
        val again = carol.queuedFor(dan.uid).count { dataOf(it)[PushPayload.TYPE] == PushPayload.RECORDS_SHARED }
        assertEquals(1, again)
    }

    /** The `data` payload of a queued push document. */
    private fun dataOf(doc: Map<String, Any?>): Map<*, *> = doc["data"] as? Map<*, *> ?: emptyMap<String, Any?>()

    /** A child as `ChildInfoViewModel` saves one for [parent]: stamped with their uid. */
    private fun newChild(parent: EmulatorParent, name: String): ChildInfo = ChildInfo(
        id = UUID.randomUUID().toString(),
        childName = name,
        dateOfBirth = LocalDateTime.of(BIRTH_YEAR, BIRTH_MONTH, BIRTH_DAY, 0, 0),
        medications = listOf(Medication(name = "Salbutamol", dosage = "100 mcg", frequency = "as needed")),
        allergies = listOf("Peanuts"),
        medicalNotes = "Mild asthma",
        schoolInfo = SchoolInfo(name = "Elm Street Primary", teacherName = "Ms Novak", grade = "2"),
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    /** A pet as `PetsViewModel` saves one for [parent]: stamped with their uid. */
    private fun newPet(parent: EmulatorParent, name: String): Pet = Pet(
        id = UUID.randomUUID().toString(),
        name = name,
        species = PetSpecies.DOG,
        breed = "Beagle",
        medications = listOf(Medication(name = "Heartworm tablet", dosage = "1", frequency = "monthly")),
        vaccinations = listOf(Vaccination(name = "Rabies", date = LocalDate.of(BIRTH_YEAR, BIRTH_MONTH, BIRTH_DAY))),
        feedingNotes = "Twice a day, no chicken",
        vetPhone = "+420 555 000 111",
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    private suspend fun newEvent(parent: EmulatorParent, title: String): Event {
        val start = now().plusDays(1).withHour(EVENT_HOUR).withMinute(0)
        return Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusHours(1),
            eventType = "activity",
            parentOwner = parent.database.userDao().getUserById(parent.uid)?.role ?: "mom",
            createdAt = now(),
            updatedAt = now()
        )
    }

    /** Whole seconds: the wire carries `updatedAt` as an instant and drops what is finer. */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

    private companion object {
        const val BIRTH_YEAR = 2019
        const val BIRTH_MONTH = 5
        const val BIRTH_DAY = 4
        const val EVENT_HOUR = 16

        /** Pushes a record's own upload sends; a backfill must send none of them. */
        val PER_RECORD_TYPES = setOf(PushPayload.EVENT_CREATED, PushPayload.CHILD_INFO_UPDATED)
    }
}
