package com.coparently.app.e2e

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.files.RecordPhoto
import com.coparently.app.domain.files.RecordPhotoAccess
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A record's photographs between two phones (L-4): a child's medical photos, a pet's photos, an
 * expense's receipt and an event's photo, through `FirebaseImageStorage` — the class the
 * ViewModels upload with — and `SharedFileCache`, the verified download the image loader uses,
 * against the real `storage.rules` and the real `onFamilyCreated` trigger on the emulators.
 *
 * What the rules suite proves offline (`firestore-tests/rules/storage-record-photos.test.js`) is
 * each rule alone. What only this proves is the client, the rules and the server agreeing: that a
 * path the client builds is one the rule accepts, that the reference the record carries is what
 * the co-parent's phone resolves and downloads with the right digest, that nobody outside the pair
 * reads it, and that a photograph taken before the pair formed is moved into the family's folder
 * by the server — and still resolves for the co-parent when the uploader's phone writes its stale
 * reference back.
 *
 * What it cannot do: draw a thumbnail, or show that a guest's screen leaves the photo out. Those
 * stay on `docs/DEVICE-CHECKLIST.md` §3.10.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentRecordPhotosTest : TwoParentTest() {

    @Test
    fun eachKindOfRecordPhotoOpensForBobWithItsDigestAndIsRefusedToAStranger() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val carol = newParent("Carol")

        EmulatorEnvironment.step("medical photo")
        val child = newChild(alice)
        val medical = upload(RecordPhotoKind.MEDICAL, child.id)
        alice.childInfoRepository.upsertChildInfo(child.copy(medicalPhotos = listOf(medical)))
        bob.childInfoRepository.pullOnce()
        assertEquals(listOf(medical), checkNotNull(bob.childInfoRepository.getChildInfoById(child.id)).medicalPhotos)
        assertOpensForBobOnly(medical, RecordPhotoKind.MEDICAL, child.id, familyId, carol)

        EmulatorEnvironment.step("pet photo")
        val pet = newPet(alice)
        val petPhoto = upload(RecordPhotoKind.PET, pet.id)
        alice.petRepository.upsertPet(pet.copy(photos = listOf(petPhoto)))
        bob.petRepository.pullOnce()
        assertEquals(listOf(petPhoto), checkNotNull(bob.petRepository.getPetById(pet.id)).photos)
        assertOpensForBobOnly(petPhoto, RecordPhotoKind.PET, pet.id, familyId, carol)

        EmulatorEnvironment.step("receipt")
        val expenseId = UUID.randomUUID().toString()
        val receipt = upload(RecordPhotoKind.RECEIPT, expenseId)
        alice.expenseRepository.addExpense(newExpense(expenseId, receipt))
        val remoteExpense = bob.firestore.collection("expenses").document(expenseId).get().await()
        assertEquals(receipt, remoteExpense.getString("receiptUrl"))
        assertOpensForBobOnly(receipt, RecordPhotoKind.RECEIPT, expenseId, familyId, carol)

        EmulatorEnvironment.step("event photo")
        val event = newEvent()
        val eventPhoto = upload(RecordPhotoKind.EVENT, event.id)
        alice.eventRepository.insertEvent(event.copy(imageUrl = eventPhoto))
        assertEquals(eventPhoto, bob.eventRepository.fetchRemoteEvent(event.id)?.imageUrl)
        assertOpensForBobOnly(eventPhoto, RecordPhotoKind.EVENT, event.id, familyId, carol)
    }

    @Test
    fun eitherParentDeletesAFamilyPhotoAndNothingIsEverOverwritten() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val pet = newPet(alice)
        val first = upload(RecordPhotoKind.PET, pet.id)
        val second = upload(RecordPhotoKind.PET, pet.id)
        val firstPath = pathOf(first)

        EmulatorEnvironment.step("An overwrite is refused, even by the uploader")
        assertRefused {
            alice.storage.reference.child(firstPath)
                .putBytes(byteArrayOf(1, 2, 3), photoMetadata(alice.uid))
                .await()
        }

        EmulatorEnvironment.step("A stranger may not delete")
        val carol = newParent("Carol")
        assertRefused { carol.storage.reference.child(firstPath).delete().await() }

        EmulatorEnvironment.step("The co-parent may, and so may the uploader")
        bob.photoStorage.delete(first, familyId)
        assertGone(firstPath, reader = alice)
        alice.photoStorage.delete(second, familyId)
        assertGone(pathOf(second), reader = bob)
    }

    @Test
    fun anUploadMissingItsStampOrOfTheWrongTypeOrUnderAnOldPathIsRefused() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val fresh = { kind: RecordPhotoKind ->
            val recordId = UUID.randomUUID().toString()
            RecordPhotoPaths.build(kind, familyId, alice.uid, recordId, "${UUID.randomUUID()}.jpg")
        }
        val jpeg = jpegBytes()

        assertRefused {
            alice.storage.reference.child(fresh(RecordPhotoKind.PET))
                .putBytes(jpeg, storageMetadata { contentType = "image/jpeg" })
                .await()
        }
        assertRefused {
            alice.storage.reference.child(fresh(RecordPhotoKind.MEDICAL))
                .putBytes(jpeg, photoMetadata(alice.uid, contentType = "application/pdf"))
                .await()
        }
        assertRefused {
            alice.storage.reference.child(fresh(RecordPhotoKind.RECEIPT))
                .putBytes(jpeg, photoMetadata(bob.uid))
                .await()
        }
        // The flat layouts from before L-4 are closed to everybody, uploader included.
        for (legacy in listOf("receipts/${UUID.randomUUID()}.jpg", "event_images/${UUID.randomUUID()}.jpg")) {
            assertRefused { alice.storage.reference.child(legacy).putBytes(jpeg, photoMetadata(alice.uid)).await() }
        }
        assertRefused {
            alice.storage.reference.child("pet_photos/${UUID.randomUUID()}/${UUID.randomUUID()}.jpg")
                .putBytes(jpeg, photoMetadata(alice.uid))
                .await()
        }
    }

    @Test
    fun aPhotoTakenBeforePairingMovesIntoTheFamilyWhenThePairForms() = runBlocking<Unit> {
        val carol = newParent("Carol")
        val dan = newParent("Dan")

        EmulatorEnvironment.step("Carol photographs her pet before there is anybody to share it with")
        val pet = newPet(carol)
        val solo = carol.photoStorage.upload(RecordPhotoKind.PET, pet.id, null, pictureUri())
        val soloPath = pathOf(solo)
        assertTrue(
            "an unpaired upload left the uploader's folder: $soloPath",
            soloPath.startsWith("pet_photos/solo_${carol.uid}/")
        )
        carol.petRepository.upsertPet(pet.copy(photos = listOf(solo)))
        val photo = checkNotNull(RecordPhotoCodec.decode(solo))
        val kept = carol.sharedFileCache.cached(RecordPhotoPaths.objectNameOf(soloPath), photo.sha256)
        val bytes = checkNotNull(kept) { "the uploader's own copy was not kept" }.readBytes()
        assertRefused { dan.storage.reference.child(soloPath).getBytes(MAX_PHOTO_BYTES).await() }

        pair(inviter = carol, accepter = dan)
        val familyId = FamilyKey.of(carol.uid, dan.uid)
        val familyPath = RecordPhotoPaths.build(
            RecordPhotoKind.PET,
            familyId,
            carol.uid,
            pet.id,
            RecordPhotoPaths.objectNameOf(soloPath)
        )

        EmulatorEnvironment.step("onFamilyCreated moves the photo and rewrites the reference")
        val moved = withTimeout(EmulatorParent.WAIT_MS) {
            var photos = photosOnServer(carol, pet.id)
            while (photos.singleOrNull()?.let { pathOf(it) } != familyPath) {
                delay(POLL_MS)
                photos = photosOnServer(carol, pet.id)
            }
            photos.single()
        }
        assertEquals(RecordPhotoCodec.encode(photo.copy(storagePath = familyPath)), moved)
        assertGone(soloPath, reader = carol)

        EmulatorEnvironment.step("Carol's phone writes its stale reference back; Dan still opens the photo")
        // Carol's Room row still names the solo path. Her next save of the pet writes it over the
        // server's rewritten reference — and widens the audience to Dan, which a save now does.
        carol.petRepository.upsertPet(
            pet.copy(photos = listOf(solo), familyId = familyId, lastModifiedBy = carol.uid, updatedAt = now())
        )
        assertEquals(listOf(solo), photosOnServer(carol, pet.id))
        dan.petRepository.pullOnce()
        val onDansPhone = checkNotNull(dan.petRepository.getPetById(pet.id))
        val viewable = checkNotNull(
            RecordPhotoAccess.viewable(onDansPhone.photos.single(), RecordPhotoKind.PET, pet.id, familyId, dan.uid)
        ) { "Dan cannot see the photograph Carol took before they paired" }
        assertEquals(familyPath, viewable.paths.first())
        val opened = dan.sharedFileCache.localCopy(familyPath, RecordPhotoPaths.objectNameOf(familyPath), photo.sha256)
        assertArrayEquals(bytes, opened.readBytes())
        assertEquals(carol.uid, dan.storage.reference.child(familyPath).metadata.await().getCustomMetadata("uploader"))
    }

    /** Uploads a fresh picture as Alice for [recordId], into the family's folder. */
    private suspend fun upload(kind: RecordPhotoKind, recordId: String): String {
        val reference = alice.photoStorage.upload(kind, recordId, null, pictureUri())
        val path = pathOf(reference)
        assertTrue(
            "$kind landed outside the family's folder: $path",
            path.startsWith("${kind.prefix}/${FamilyKey.of(alice.uid, bob.uid)}/$recordId/")
        )
        return reference
    }

    /**
     * Bob resolves [reference] as the screens do and downloads it through his own verified cache —
     * the bytes Alice stored, with the digest the reference names — and [stranger] is refused.
     */
    private suspend fun assertOpensForBobOnly(
        reference: String,
        kind: RecordPhotoKind,
        recordId: String,
        familyId: String,
        stranger: EmulatorParent
    ) {
        val photo: RecordPhoto = checkNotNull(RecordPhotoCodec.decode(reference)) { "not a reference: $reference" }
        val viewable = checkNotNull(RecordPhotoAccess.viewable(reference, kind, recordId, familyId, bob.uid))
        assertEquals(listOf(photo.storagePath), viewable.paths)
        assertEquals(null, RecordPhotoAccess.viewable(reference, kind, recordId, familyId, stranger.uid))

        val stored = alice.storage.reference.child(photo.storagePath).getBytes(MAX_PHOTO_BYTES).await()
        val metadata = bob.storage.reference.child(photo.storagePath).metadata.await()
        assertEquals("image/jpeg", metadata.contentType)
        assertEquals(alice.uid, metadata.getCustomMetadata("uploader"))
        assertEquals(photo.sha256, metadata.getCustomMetadata("sha256"))
        val opened = bob.sharedFileCache.localCopy(
            photo.storagePath,
            RecordPhotoPaths.objectNameOf(photo.storagePath),
            photo.sha256
        )
        assertArrayEquals(stored, opened.readBytes())
        assertEquals(photo.sizeBytes, stored.size.toLong())

        assertRefused { stranger.storage.reference.child(photo.storagePath).getBytes(MAX_PHOTO_BYTES).await() }
    }

    /** The photo references on [petId]'s server document, read as its creator. */
    private suspend fun photosOnServer(parent: EmulatorParent, petId: String): List<String> {
        val data = parent.firestore.collection("pets").document(petId).get().await().data
        assertNotNull("the pet never reached the server", data)
        return (data!!["photos"] as? List<*>).orEmpty().filterIsInstance<String>()
    }

    /** Fails unless nothing is stored at [path] any more, asked by [reader], who may read it. */
    private suspend fun assertGone(path: String, reader: EmulatorParent) {
        try {
            reader.storage.reference.child(path).metadata.await()
            fail("$path is still stored")
        } catch (e: StorageException) {
            assertEquals("unexpected Storage error for $path", StorageException.ERROR_OBJECT_NOT_FOUND, e.errorCode)
        }
    }

    /** Fails unless [block] is refused by the Storage rules. */
    private suspend fun assertRefused(block: suspend () -> Unit) {
        try {
            block()
            fail("A request the Storage rules forbid was allowed")
        } catch (e: StorageException) {
            assertTrue(
                "unexpected Storage error ${e.errorCode}",
                e.errorCode == StorageException.ERROR_NOT_AUTHORIZED ||
                    e.errorCode == StorageException.ERROR_NOT_AUTHENTICATED
            )
        }
    }

    private fun pathOf(reference: String): String =
        checkNotNull(RecordPhotoCodec.decode(reference)) { "not a reference: $reference" }.storagePath

    private fun photoMetadata(uploader: String, contentType: String = "image/jpeg") = storageMetadata {
        setContentType(contentType)
        setCustomMetadata("uploader", uploader)
        setCustomMetadata("sha256", "0".repeat(SHA256_HEX_LENGTH))
    }

    /** A small picture as the photo picker hands one over: a URI the content resolver can open. */
    private fun pictureUri(): String {
        val dir = File(context.cacheDir, "e2e-picked/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "photo.png")
        val bitmap = Bitmap.createBitmap(PICTURE_PX, PICTURE_PX, Bitmap.Config.ARGB_8888)
        // A different colour per picture, so no two uploads share a digest.
        bitmap.eraseColor(Color.rgb((0..COLOUR_MAX).random(), (0..COLOUR_MAX).random(), (0..COLOUR_MAX).random()))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(PICTURE_PX, PICTURE_PX, Bitmap.Config.ARGB_8888)
        return try {
            java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PNG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun newChild(parent: EmulatorParent) = ChildInfo(
        id = UUID.randomUUID().toString(),
        childName = "Ella",
        dateOfBirth = null,
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    private fun newPet(parent: EmulatorParent) = Pet(
        id = UUID.randomUUID().toString(),
        name = "Luna",
        species = PetSpecies.DOG,
        createdAt = now(),
        updatedAt = now(),
        createdByFirebaseUid = parent.uid,
        lastModifiedBy = parent.uid
    )

    private fun newExpense(id: String, receipt: String) = Expense(
        id = id,
        title = "Pharmacy",
        amount = RECEIPT_AMOUNT,
        currency = "EUR",
        category = ExpenseCategory.MEDICAL,
        paidBy = alice.uid,
        splitBetween = listOf(alice.uid, bob.uid),
        receiptUrl = receipt
    )

    private suspend fun newEvent(): Event {
        val start = now().plusDays(1).withHour(EVENT_HOUR).withMinute(0)
        return Event(
            id = UUID.randomUUID().toString(),
            title = "Class photo",
            startDateTime = start,
            endDateTime = start.plusHours(1),
            eventType = "activity",
            parentOwner = alice.database.userDao().getUserById(alice.uid)?.role ?: "mom",
            createdAt = now(),
            updatedAt = now()
        )
    }

    /** Whole seconds: the wire carries `updatedAt` as an instant and drops what is finer. */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

    private companion object {
        const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
        const val SHA256_HEX_LENGTH = 64
        const val PICTURE_PX = 64
        const val COLOUR_MAX = 255
        const val PNG_QUALITY = 100
        const val POLL_MS = 500L
        const val EVENT_HOUR = 16
        const val RECEIPT_AMOUNT = 12.5
    }
}
