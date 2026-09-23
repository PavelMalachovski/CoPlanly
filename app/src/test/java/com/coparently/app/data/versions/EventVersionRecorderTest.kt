package com.coparently.app.data.versions

import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.EventVersionOutboxEntity
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [EventVersionRecorder] — the outbox that makes every saved revision of an event reach the
 * immutable `event_versions` collection (MON-4).
 *
 * What is pinned here is what the export's honesty rests on: a private event queues nothing, a
 * revision leaves Room only once the server has it, a lost acknowledgement is not mistaken for a
 * refusal, and an outage stops the pass rather than burning every row's attempts.
 */
class EventVersionRecorderTest {

    private lateinit var outboxDao: EventVersionOutboxDao
    private lateinit var userDao: UserDao
    private lateinit var remote: FirestoreEventVersionDataSource
    private lateinit var recorder: EventVersionRecorder

    @Before
    fun setup() {
        outboxDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        remote = mockk(relaxed = true)
        recorder = EventVersionRecorder(outboxDao, userDao, remote)
        coEvery { userDao.getUserById(ALICE) } returns UserEntity(
            id = ALICE,
            email = "alice@example.com",
            name = "Alice",
            role = "mom",
            colorCode = "#FF4081",
            partnerId = BOB
        )
    }

    @Test
    fun `a revision is queued with the snapshot, the audience and the device time`() = runTest {
        val queued = slot<EventVersionOutboxEntity>()
        coEvery { outboxDao.insert(capture(queued)) } returns Unit

        recorder.record(
            eventId = "e1",
            kind = EventVersionKind.UPDATED,
            editorUid = ALICE,
            isPrivate = false,
            audience = listOf(ALICE, BOB),
            familyId = FAMILY,
            snapshot = mapOf("id" to "e1", "title" to "Dentist", "reminderMinutes" to 15),
            deviceTimeMillis = SAVED_AT
        )

        val row = queued.captured
        assertEquals("e1", row.eventId)
        assertEquals("updated", row.kind)
        assertEquals(ALICE, row.editorUid)
        assertEquals(SAVED_AT, row.deviceTimeMillis)
        assertEquals(FAMILY, row.familyId)
        assertEquals(0, row.attempts)
        assertEquals(listOf(ALICE, BOB), EventVersionDocument.decodeAudience(row.audienceJson))
        assertEquals("Dentist", EventVersionDocument.decodeSnapshot(row.snapshotJson)["title"])
    }

    @Test
    fun `a private event queues nothing`() = runTest {
        recorder.record(
            eventId = "e1",
            kind = EventVersionKind.CREATED,
            editorUid = ALICE,
            isPrivate = true,
            audience = listOf(ALICE),
            familyId = null,
            snapshot = mapOf("id" to "e1")
        )

        coVerify(exactly = 0) { outboxDao.insert(any()) }
    }

    @Test
    fun `a delivered revision leaves the outbox`() = runTest {
        coEvery { outboxDao.pending(ALICE, any()) } returns listOf(row("v1"))

        recorder.flush(ALICE)

        coVerify { remote.create("v1", any()) }
        coVerify { outboxDao.delete("v1") }
    }

    @Test
    fun `the uploaded document carries every field the rule requires`() = runTest {
        coEvery { outboxDao.pending(ALICE, any()) } returns listOf(row("v1"))
        val sent = slot<Map<String, Any?>>()
        coEvery { remote.create("v1", capture(sent)) } returns Unit

        recorder.flush(ALICE)

        val document = sent.captured
        assertEquals("e1", document[EventVersionDocument.EVENT_ID])
        assertEquals("updated", document[EventVersionDocument.KIND])
        assertEquals(ALICE, document[EventVersionDocument.EDITOR_UID])
        assertEquals(SAVED_AT, document[EventVersionDocument.DEVICE_TIME_MILLIS])
        assertEquals(FAMILY, document[EventVersionDocument.FAMILY_ID])
        assertEquals(EventVersionDocument.CURRENT_FORMAT, document[EventVersionDocument.FORMAT_VERSION])
        @Suppress("UNCHECKED_CAST")
        val snapshot = document[EventVersionDocument.EVENT] as Map<String, Any?>
        // Whole numbers come back out of Room as Long, not 15.0 — the value the event holds.
        assertEquals(15L, snapshot["reminderMinutes"])
    }

    @Test
    fun `the audience is narrowed to live pairing and always keeps the editor`() = runTest {
        // Queued while paired with Carol; Alice is now paired with Bob. The rule would refuse
        // Carol for ever, and the revision must stay readable to the parent who saved it.
        coEvery { outboxDao.pending(ALICE, any()) } returns listOf(
            row("v1").copy(audienceJson = EventVersionDocument.encodeAudience(listOf(CAROL)))
        )
        val sent = slot<Map<String, Any?>>()
        coEvery { remote.create("v1", capture(sent)) } returns Unit

        recorder.flush(ALICE)

        assertEquals(listOf(ALICE), sent.captured[EventVersionDocument.SHARED_WITH])
    }

    @Test
    fun `a refused create that the server already holds is a lost acknowledgement, not a refusal`() =
        runTest {
            coEvery { outboxDao.pending(ALICE, any()) } returns listOf(row("v1"))
            coEvery { remote.create("v1", any()) } throws denied()
            coEvery { remote.exists("v1") } returns true

            recorder.flush(ALICE)

            coVerify { outboxDao.delete("v1") }
            coVerify(exactly = 0) { outboxDao.recordRefusal(any()) }
        }

    @Test
    fun `a genuine refusal is counted, kept, and does not stop the pass`() = runTest {
        coEvery { outboxDao.pending(ALICE, any()) } returns listOf(row("v1"), row("v2"))
        coEvery { remote.create("v1", any()) } throws denied()
        coEvery { remote.exists("v1") } returns false

        recorder.flush(ALICE)

        coVerify { outboxDao.recordRefusal("v1") }
        coVerify(exactly = 0) { outboxDao.delete("v1") }
        coVerify { remote.create("v2", any()) }
    }

    @Test
    fun `an outage stops the pass and keeps every row`() = runTest {
        coEvery { outboxDao.pending(ALICE, any()) } returns listOf(row("v1"), row("v2"))
        coEvery { remote.create("v1", any()) } throws FirebaseFirestoreException(
            "offline",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )

        recorder.flush(ALICE)

        coVerify(exactly = 0) { remote.create("v2", any()) }
        coVerify(exactly = 0) { outboxDao.delete(any()) }
        coVerify(exactly = 0) { outboxDao.recordRefusal(any()) }
    }

    @Test
    fun `only rows under the retry limit are asked for`() = runTest {
        recorder.flush(ALICE)

        coVerify { outboxDao.pending(ALICE, EventVersionRecorder.MAX_REFUSALS) }
    }

    @Test
    fun `a flush never throws, even when the database does`() = runTest {
        coEvery { outboxDao.pending(any(), any()) } throws IllegalStateException("database closed")

        recorder.flush(ALICE)

        assertTrue(true, "reached: the failure was contained")
    }

    private fun row(id: String) = EventVersionOutboxEntity(
        id = id,
        eventId = "e1",
        kind = "updated",
        editorUid = ALICE,
        deviceTimeMillis = SAVED_AT,
        snapshotJson = EventVersionDocument.encodeSnapshot(
            mapOf("id" to "e1", "title" to "Dentist", "reminderMinutes" to 15)
        ),
        audienceJson = EventVersionDocument.encodeAudience(listOf(ALICE, BOB)),
        familyId = FAMILY,
        attempts = 0
    )

    private fun denied() = FirebaseFirestoreException(
        "Missing or insufficient permissions.",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
    )

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"
        const val FAMILY = "alice-uid__bob-uid"
        const val SAVED_AT = 1_787_000_000_000L
    }
}
