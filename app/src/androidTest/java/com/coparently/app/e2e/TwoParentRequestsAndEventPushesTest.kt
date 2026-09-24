package com.coparently.app.e2e

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/**
 * Change requests, the pushes around them and around events, the immutable event history, and
 * event photos — between two phones, through the production writers and the real rules.
 *
 * **Change requests.** Alice builds a request exactly as `RequestChangeViewModel` does (her own
 * uid as requester, her Room row's `partnerId` as addressee) and sends it through
 * `ChangeRequestRepositoryImpl.createChangeRequest`. Bob's phone receives it through
 * `observeRemote` — the listener the app runs — and answers with `updateStatus`, which Alice's
 * listener brings back. Each transition leaves the push `ChangeRequestRepositoryImpl` queues in
 * `notification_queue`, addressed as the repository decides: creation and cancellation to the
 * addressee, acceptance and decline to the requester.
 *
 * **Event pushes.** `event_created` is written by the `onEventCreated` Cloud Function when the
 * event document appears (and by `SyncService` for an event whose own upload failed). Nothing in
 * the client or the functions produces `event_updated` or `event_deleted` any more:
 * `SyncService.notifyEventUpdate` is only ever called with `"created"`, and no function writes
 * either type. They remain in `PushPayload` and the rules' allow-list for older builds.
 *
 * **Revisions (CLAUDE.md item 25).** A create and an edit of a shared event each leave a phone
 * revision in `event_versions`, readable by Bob through the export's own query
 * (`FirestoreEventVersionDataSource.readableBy`); neither parent can change or delete one; a
 * private event leaves none.
 *
 * **Event photos.** `FirebaseImageStorage.uploadEventImage` — the upload the event form makes —
 * stores Alice's photo at `event_images/{eventId}.jpg`; Bob downloads it as himself. What the
 * rules refuse is a signed-out read and a non-JPEG upload. They do **not** refuse a signed-in
 * stranger: `storage.rules` admits any authenticated user on `event_images`, a limit that file
 * documents at length, so no test here claims otherwise.
 *
 * What this cannot cover: the pushes' delivery (FCM has no emulator) and anything drawn on screen.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentRequestsAndEventPushesTest : TwoParentTest() {

    @Test
    fun bobAcceptsAlicesChangeRequestAndEachSideIsNotified() = runBlocking<Unit> {
        val event = insertSharedEvent("Dentist")
        val request = sendRequest(event)

        val onBobsPhone = awaitRequest(bob, request.id, ChangeRequestStatus.PENDING)
        assertEquals(alice.uid, onBobsPhone.requestedBy)
        assertEquals(event.id, onBobsPhone.eventId)
        assertEquals(request.proposedStartDateTime, onBobsPhone.proposedStartDateTime)

        val created = awaitPush(alice, bob, PushPayload.CHANGE_REQUEST_CREATED, request.id)
        assertEquals(event.id, created[PushPayload.EVENT_ID])
        assertEquals(FamilyKey.of(alice.uid, bob.uid), created[PushPayload.FAMILY_ID])

        bob.changeRequestRepository.updateStatus(request.id, ChangeRequestStatus.ACCEPTED)

        val onAlicesPhone = awaitRequest(alice, request.id, ChangeRequestStatus.ACCEPTED)
        assertNotNull(onAlicesPhone.respondedAt)
        awaitPush(bob, alice, PushPayload.CHANGE_REQUEST_ACCEPTED, request.id)
    }

    @Test
    fun bobDeclinesAChangeRequestAndAliceIsNotified() = runBlocking<Unit> {
        val request = sendRequest(insertSharedEvent("Swimming"))
        awaitRequest(bob, request.id, ChangeRequestStatus.PENDING)

        bob.changeRequestRepository.updateStatus(request.id, ChangeRequestStatus.DECLINED)

        awaitRequest(alice, request.id, ChangeRequestStatus.DECLINED)
        awaitPush(bob, alice, PushPayload.CHANGE_REQUEST_DECLINED, request.id)
    }

    @Test
    fun aliceCancelsHerChangeRequestAndBobIsNotified() = runBlocking<Unit> {
        val request = sendRequest(insertSharedEvent("Parents' evening"))
        awaitRequest(bob, request.id, ChangeRequestStatus.PENDING)

        alice.changeRequestRepository.updateStatus(request.id, ChangeRequestStatus.CANCELLED)

        awaitRequest(bob, request.id, ChangeRequestStatus.CANCELLED)
        // Cancellation goes to the addressee, not back to the requester who withdrew it.
        awaitPush(alice, bob, PushPayload.CHANGE_REQUEST_CANCELLED, request.id)
    }

    @Test
    fun anEventAliceCreatesQueuesEventCreatedForBobFromTheServer() = runBlocking<Unit> {
        val event = insertSharedEvent("Football match")

        // Written by `onEventCreated` as admin, a moment after the document lands.
        val push = withTimeout(EmulatorParent.WAIT_MS) {
            pollPush(bob) { it[PushPayload.TYPE] == PushPayload.EVENT_CREATED && it[PushPayload.EVENT_ID] == event.id }
        }
        assertEquals(PushPayload.EVENT_CREATED, push[PushPayload.TYPE])
    }

    @Test
    fun revisionsOfASharedEventReachBobAndNeitherParentCanChangeThem() = runBlocking<Unit> {
        val event = insertSharedEvent("Doctor")
        val stored = checkNotNull(alice.eventRepository.getEventById(event.id))
        alice.eventRepository.updateEvent(
            stored.copy(title = "Doctor (moved)", updatedAt = LocalDateTime.now().plusSeconds(1))
        )
        alice.eventVersionRecorder.flush(alice.uid)
        assertTrue(
            "a revision of the event is still in Alice's outbox",
            alice.eventVersionRecorder.undelivered(alice.uid).none { it.eventId == event.id }
        )

        val bobsVersions = FirestoreEventVersionDataSource(bob.firestore)
        val revisions = withTimeout(EmulatorParent.WAIT_MS) {
            var found = phoneRevisions(bobsVersions, event.id)
            while (found.map { it.kind }.toSet() != setOf(EventVersionKind.CREATED, EventVersionKind.UPDATED)) {
                delay(POLL_MS)
                found = phoneRevisions(bobsVersions, event.id)
            }
            found
        }
        revisions.forEach {
            assertEquals(alice.uid, it.editorUid)
            assertNotNull("the server did not stamp recordedAt", it.recordedAtMillis)
        }
        val edit = revisions.single { it.kind == EventVersionKind.UPDATED }
        assertEquals("Doctor (moved)", edit.snapshot["title"])

        // `allow update, delete: if false` — for the co-parent and for the author alike.
        val path = "${EventVersionDocument.COLLECTION}/${edit.versionId}"
        assertFirestoreRefused {
            bob.firestore.document(path).update(EventVersionDocument.KIND, EventVersionKind.DELETED.wire).await()
        }
        assertFirestoreRefused { alice.firestore.document(path).delete().await() }
        assertTrue(EmulatorEnvironment.documentExists(path))
    }

    @Test
    fun aPrivateEventRecordsNoRevision() = runBlocking<Unit> {
        val event = newEvent("Therapy", isPrivate = true)
        alice.eventRepository.insertEvent(event)
        val stored = checkNotNull(alice.eventRepository.getEventById(event.id))
        alice.eventRepository.updateEvent(stored.copy(title = "Therapy (moved)", updatedAt = LocalDateTime.now()))
        alice.eventVersionRecorder.flush(alice.uid)

        assertTrue(alice.eventVersionRecorder.undelivered(alice.uid).none { it.eventId == event.id })
        assertTrue(
            "a private event produced a revision",
            EmulatorEnvironment.queryAsAdmin(EventVersionDocument.COLLECTION, EventVersionDocument.EVENT_ID, event.id)
                .isEmpty()
        )
    }

    @Test
    fun anEventPhotoAliceUploadsDownloadsOnBobsPhone() = runBlocking<Unit> {
        val eventId = UUID.randomUUID().toString()
        val url = FirebaseImageStorage(context, alice.storage).uploadEventImage(eventId, jpegUri())
        alice.eventRepository.insertEvent(newEvent("Class photo").copy(id = eventId, imageUrl = url))

        assertEquals(url, bob.eventRepository.fetchRemoteEvent(eventId)?.imageUrl)
        val path = "event_images/$eventId.jpg"
        val bytes = bob.storage.reference.child(path).getBytes(MAX_IMAGE_BYTES).await()
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "not an image" }
        assertEquals(IMAGE_WIDTH, bitmap.width)
        assertEquals(IMAGE_HEIGHT, bitmap.height)
    }

    @Test
    fun anEventPhotoIsRefusedSignedOutAndAsAnythingButJpeg() = runBlocking<Unit> {
        val eventId = UUID.randomUUID().toString()
        FirebaseImageStorage(context, alice.storage).uploadEventImage(eventId, jpegUri())
        val path = "event_images/$eventId.jpg"

        assertStorageRefused {
            alice.storage.reference.child(path)
                .putBytes("not a photo".toByteArray(), storageMetadata { contentType = "text/plain" })
                .await()
        }

        val signedOut = newParent("Mallory")
        signedOut.auth.signOut()
        assertStorageRefused { signedOut.storage.reference.child(path).getBytes(MAX_IMAGE_BYTES).await() }
    }

    /** A shared event of Alice's, saved through her repository and therefore already uploaded. */
    private suspend fun insertSharedEvent(title: String): Event {
        val event = newEvent(title)
        alice.eventRepository.insertEvent(event)
        return checkNotNull(alice.eventRepository.getEventById(event.id))
    }

    /** A request about [event], built as `RequestChangeViewModel` builds one, sent by Alice. */
    private suspend fun sendRequest(event: Event): ChangeRequest {
        val me = checkNotNull(alice.userRepository.getCurrentUser())
        val partnerId = checkNotNull(me.partnerId) { "Alice's phone does not know her co-parent" }
        assertEquals(bob.uid, partnerId)
        val request = ChangeRequest(
            id = UUID.randomUUID().toString(),
            eventId = event.id,
            eventTitle = event.title,
            requestedBy = me.id,
            requestedTo = partnerId,
            currentStartDateTime = event.startDateTime,
            currentEndDateTime = event.endDateTime,
            proposedStartDateTime = event.startDateTime.plusHours(2),
            proposedEndDateTime = event.endDateTime?.plusHours(2),
            note = "Could we move it?",
            createdAt = LocalDateTime.now()
        )
        alice.changeRequestRepository.createChangeRequest(request)
        return request
    }

    /** Runs [parent]'s change-request listener until [requestId] is in its Room at [status]. */
    private suspend fun awaitRequest(
        parent: EmulatorParent,
        requestId: String,
        status: ChangeRequestStatus
    ): ChangeRequest = coroutineScope {
        val listener = launch { parent.changeRequestRepository.observeRemote() }
        try {
            withTimeout(EmulatorParent.WAIT_MS) {
                parent.changeRequestRepository.getAllChangeRequests()
                    .first { list -> list.any { it.id == requestId && it.status == status } }
                    .single { it.id == requestId }
            }
        } finally {
            listener.cancel()
        }
    }

    /** The push of [type] about [requestId] that [from]'s phone queued for [to]. */
    private suspend fun awaitPush(
        from: EmulatorParent,
        to: EmulatorParent,
        type: String,
        requestId: String
    ): Map<String, Any?> = withTimeout(EmulatorParent.WAIT_MS) {
        pollPush(to) { it[PushPayload.TYPE] == type && it[PushPayload.CHANGE_REQUEST_ID] == requestId }
    }.also { assertEquals(from.name, it[PushPayload.ACTOR]) }

    /** Polls `notification_queue` until a payload addressed to [to] matches [matches]. */
    private suspend fun pollPush(to: EmulatorParent, matches: (Map<String, Any?>) -> Boolean): Map<String, Any?> {
        while (true) {
            val found = payloadsFor(to).firstOrNull(matches)
            if (found != null) return found
            delay(POLL_MS)
        }
    }

    /** The `data` payload of every push queued for [to], whoever queued it. */
    private fun payloadsFor(to: EmulatorParent): List<Map<String, Any?>> =
        alice.queuedFor(to.uid).mapNotNull { document ->
            (document["data"] as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }
        }

    /** The revisions of [eventId] a phone recorded, as Bob's export query returns them. */
    private suspend fun phoneRevisions(
        versions: FirestoreEventVersionDataSource,
        eventId: String
    ): List<EventVersionDocument.Parsed> =
        versions.readableBy(bob.uid).filter { it.eventId == eventId && !it.recordedByServer }

    /** A small JPEG on this phone, as the photo picker would hand one over. */
    private fun jpegUri(): String {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val dir = File(context.cacheDir, "e2e-picked/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "photo.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    private suspend fun newEvent(title: String, isPrivate: Boolean = false): Event {
        val start = LocalDateTime.now().plusDays(1).withHour(START_HOUR).withMinute(0)
            .withSecond(0).withNano(0)
        return Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusHours(1),
            eventType = "appointment",
            // Alice's own slot, as the event form defaults it.
            parentOwner = alice.database.userDao().getUserById(alice.uid)?.role ?: "mom",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            isPrivate = isPrivate
        )
    }

    /** Fails unless [block] is refused by `firestore.rules`. */
    private suspend fun assertFirestoreRefused(block: suspend () -> Unit) {
        try {
            block()
            fail("A write the rules forbid was allowed")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    /** Fails unless [block] is refused by `storage.rules`. */
    private suspend fun assertStorageRefused(block: suspend () -> Unit) {
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

    private companion object {
        const val START_HOUR = 10
        const val POLL_MS = 250L
        const val IMAGE_WIDTH = 64
        const val IMAGE_HEIGHT = 48
        const val JPEG_QUALITY = 90
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    }
}
