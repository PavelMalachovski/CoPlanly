package com.coparently.app.e2e

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Budget
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The family's own records between two phones: children, pets and budgets, the photographs on a
 * child's medical notes and on a pet, and the two pushes the child record and the pairing backfill
 * produce.
 *
 * Alice writes through the production repositories, stamped as `ChildInfoViewModel` and
 * `PetsViewModel` stamp a save (`createdByFirebaseUid`, `lastModifiedBy`); Bob reads through his
 * own repositories — `pullOnce()` for children and pets, `observeRemote()`'s `familyId` listener
 * for budgets — so what is checked is what lands in *his* Room, against the real `firestore.rules`.
 * A deletion is a tombstone Bob's download answers by dropping the row (CLAUDE.md item 14, CQ-19).
 *
 * Two pushes. **`child_info_updated`** has two producers: `SyncService` queues it when it uploads
 * a child edit that had not reached the server, and the `onChildInfoUpdated` trigger in
 * `functions/index.js` queues one on every update of a `child_info` document. Both are exercised,
 * each where only it can have written the document. **`records_shared`** is the one push a
 * pairing backfill sends (item 22): records a parent made before pairing are re-uploaded for the
 * new co-parent silently, and announced once.
 *
 * Photographs go through `FirebaseImageStorage`, the class the ViewModels upload with, into the
 * Storage emulator under the real `storage.rules`; Bob reads them by path as himself and through
 * the download URL the record carries, which is what `AsyncImage` loads. **What this cannot
 * assert:** that a stranger is refused. The `pet_photos` and `medical_photos` blocks admit any
 * signed-in reader and writer — the unguessable photo id is obscurity, documented as such in
 * `storage.rules` — so a refusal test would fail, and a test that a stranger *can* read would pin
 * the weakness. The one refusal those blocks do make, a file that is not a JPEG, is asserted.
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

        // The client's own document: `FcmService` stamps `createdAt` as epoch millis, the
        // functions as a server timestamp, so this cannot be the trigger's copy of the same edit.
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
    fun anUpdatedChildDocumentMakesTheServerQueueChildInfoUpdatedForBob() = runBlocking<Unit> {
        val child = newChild(alice, "Ava")
        alice.childInfoRepository.upsertChildInfo(child)
        // A second save is an update of the document, which is what `onChildInfoUpdated` watches.
        // No sync runs here, so nothing on the client queues a push: whatever arrives is the
        // trigger's.
        alice.childInfoRepository.upsertChildInfo(
            child.copy(allergies = listOf("Peanuts", "Penicillin"), updatedAt = now())
        )

        val push = awaitPush(alice, bob) { doc ->
            dataOf(doc)[PushPayload.TYPE] == PushPayload.CHILD_INFO_UPDATED &&
                dataOf(doc)[PushPayload.CHILD_INFO_ID] == child.id
        }
        assertEquals(bob.uid, push[PushPayload.TARGET_USER_ID])
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
    fun aBudgetAliceSetsReachesBobWhoMayEditItAndAStrangerCannotReadIt() = runBlocking<Unit> {
        val budget = Budget(
            id = UUID.randomUUID().toString(),
            category = ExpenseCategory.EDUCATION,
            monthlyLimit = LIMIT,
            currency = "EUR"
        )
        alice.budgetRepository.addBudget(budget)

        val onBobsPhone = awaitBudget(bob, budget.id) { true }
        assertEquals(LIMIT, onBobsPhone.monthlyLimit, DELTA)
        assertEquals(ExpenseCategory.EDUCATION, onBobsPhone.category)
        assertEquals(FamilyKey.of(alice.uid, bob.uid), onBobsPhone.familyId)

        // A budget is the pair's shared plan: the co-parent may change it, and Alice sees it.
        bob.budgetRepository.updateBudget(onBobsPhone.copy(monthlyLimit = RAISED_LIMIT))
        awaitBudget(alice, budget.id) { it.monthlyLimit == RAISED_LIMIT }

        val carol = newParent("Carol")
        try {
            carol.firestore.collection("budgets").document(budget.id).get().await()
            fail("A stranger read the family's budget")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
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

    @Test
    fun aPetPhotoAliceAttachesOpensOnBobsPhone() = runBlocking<Unit> {
        val pet = newPet(alice, "Luna")
        val photoId = UUID.randomUUID().toString()
        val url = imageStorage(alice).uploadPetPhoto(pet.id, photoId, pictureUri())
        alice.petRepository.upsertPet(pet.copy(photos = listOf(url)))

        bob.petRepository.pullOnce()
        val onBobsPhone = checkNotNull(bob.petRepository.getPetById(pet.id))
        assertEquals(listOf(url), onBobsPhone.photos)
        assertPhotoOpensForBob("pet_photos/${pet.id}/$photoId.jpg", onBobsPhone.photos.single())
    }

    @Test
    fun aMedicalPhotoAliceAttachesOpensOnBobsPhone() = runBlocking<Unit> {
        val child = newChild(alice, "Ella")
        val photoId = UUID.randomUUID().toString()
        val url = imageStorage(alice).uploadMedicalPhoto(child.id, photoId, pictureUri())
        alice.childInfoRepository.upsertChildInfo(child.copy(medicalPhotos = listOf(url)))

        bob.childInfoRepository.pullOnce()
        val onBobsPhone = checkNotNull(bob.childInfoRepository.getChildInfoById(child.id))
        assertEquals(listOf(url), onBobsPhone.medicalPhotos)
        assertPhotoOpensForBob("medical_photos/${child.id}/$photoId.jpg", onBobsPhone.medicalPhotos.single())
    }

    @Test
    fun aPhotoThatIsNotAJpegIsRefused() = runBlocking<Unit> {
        val png = storageMetadata { contentType = "image/png" }
        for (path in listOf("pet_photos", "medical_photos")) {
            val ref = alice.storage.reference.child("$path/${UUID.randomUUID()}/${UUID.randomUUID()}.jpg")
            try {
                ref.putBytes(byteArrayOf(1, 2, 3), png).await()
                fail("$path accepted a file that is not a JPEG")
            } catch (e: StorageException) {
                assertEquals(StorageException.ERROR_NOT_AUTHORIZED, e.errorCode)
            }
        }
    }

    /**
     * Bob opens the photograph at [path] twice: through Storage as himself, and through [url],
     * the download URL on the record, which is what `AsyncImage` loads. Both must be the bytes
     * Alice stored, as the JPEG the upload re-encodes to.
     */
    private suspend fun assertPhotoOpensForBob(path: String, url: String) {
        val stored = alice.storage.reference.child(path).getBytes(MAX_PHOTO_BYTES).await()
        val bobsRef = bob.storage.reference.child(path)
        assertEquals("image/jpeg", bobsRef.metadata.await().contentType)
        assertArrayEquals(stored, bobsRef.getBytes(MAX_PHOTO_BYTES).await())
        assertArrayEquals(stored, httpGet(url))
    }

    /** The upload class the ViewModels use, over [parent]'s Storage client. */
    private fun imageStorage(parent: EmulatorParent) = FirebaseImageStorage(context, parent.storage)

    /** A small picture as the photo picker hands one over: a URI the content resolver can open. */
    private fun pictureUri(): String {
        val bitmap = Bitmap.createBitmap(PICTURE_PX, PICTURE_PX, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(PICTURE_RED, PICTURE_GREEN, PICTURE_BLUE))
        val dir = File(context.cacheDir, "e2e-picked/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "photo.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    /** Fetches [url] the way an image loader does: a plain GET, the token in the URL. */
    private fun httpGet(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            assertEquals("GET $url", HttpURLConnection.HTTP_OK, connection.responseCode)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /** Runs [parent]'s budget listener until [budgetId] is in their Room and satisfies [condition]. */
    private suspend fun awaitBudget(
        parent: EmulatorParent,
        budgetId: String,
        condition: (Budget) -> Boolean
    ): Budget = coroutineScope {
        val listener = launch { parent.budgetRepository.observeRemote() }
        try {
            withTimeout(EmulatorParent.WAIT_MS) {
                parent.budgetRepository.getAllBudgets()
                    .first { list -> list.any { it.id == budgetId && condition(it) } }
                    .single { it.id == budgetId }
            }
        } finally {
            listener.cancel()
        }
    }

    /**
     * Polls the pushes queued for [receiver] until one satisfies [predicate] — for a push a Cloud
     * Function writes, which lands some time after the write that triggered it.
     */
    private suspend fun awaitPush(
        sender: EmulatorParent,
        receiver: EmulatorParent,
        predicate: (Map<String, Any?>) -> Boolean
    ): Map<String, Any?> = withTimeout(EmulatorParent.WAIT_MS) {
        var found = sender.queuedFor(receiver.uid).firstOrNull(predicate)
        while (found == null) {
            delay(POLL_MS)
            found = sender.queuedFor(receiver.uid).firstOrNull(predicate)
        }
        found
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
        const val LIMIT = 200.0
        const val RAISED_LIMIT = 250.0
        const val DELTA = 0.001
        const val POLL_MS = 500L
        const val HTTP_TIMEOUT_MS = 10_000
        const val MAX_PHOTO_BYTES = 5L * 1024 * 1024
        const val PICTURE_PX = 64
        const val PICTURE_RED = 200
        const val PICTURE_GREEN = 120
        const val PICTURE_BLUE = 40
        const val PNG_QUALITY = 100
        const val BIRTH_YEAR = 2019
        const val BIRTH_MONTH = 5
        const val BIRTH_DAY = 4
        const val EVENT_HOUR = 16

        /** Pushes a record's own upload sends; a backfill must send none of them. */
        val PER_RECORD_TYPES = setOf(PushPayload.EVENT_CREATED, PushPayload.CHILD_INFO_UPDATED)
    }
}
