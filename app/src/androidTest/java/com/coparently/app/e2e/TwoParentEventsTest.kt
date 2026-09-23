package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

/**
 * Events between two phones: what one parent writes, the other can read — through the same
 * query and the same parser the app uses — and what must never leave a phone does not.
 *
 * Alice writes through `EventRepositoryImpl`, so the document is `toFirestoreMap()`'s and its
 * audience is `shareTargets`' (CLAUDE.md items 5 and 16). Bob reads through
 * `FirestoreEventDataSource.observeEventsSharedWith` — the sync's own `array-contains sharedWith`
 * query, which the rules validate as a *query* (item 12) — and through
 * `EventRepositoryImpl.fetchRemoteEvent`, which parses with `EventDocument` exactly as the sync does.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentEventsTest : TwoParentTest() {

    @Test
    fun anEventAliceCreatesIsReadableByBobThroughTheSyncQuery() = runBlocking<Unit> {
        val event = newEvent("Dentist")
        alice.eventRepository.insertEvent(event)

        val shared = bob.eventDataSource.observeEventsSharedWith(bob.uid, changedAfter = null).first()
        val document = shared.documents.firstOrNull { it["id"] == event.id }
        assertNotNull("Bob's sync query did not return Alice's event", document)
        val audience = (document!!["sharedWith"] as List<*>).map { it.toString() }.toSet()
        assertEquals(setOf(alice.uid, bob.uid), audience)
        assertEquals(FamilyKey.of(alice.uid, bob.uid), document["familyId"])

        val onBobsPhone = assertNotNullAndGet(bob.eventRepository.fetchRemoteEvent(event.id))
        assertEquals("Dentist", onBobsPhone.title)
        assertEquals(alice.uid, onBobsPhone.createdByFirebaseUid)
        assertEquals(event.startDateTime, onBobsPhone.startDateTime)
    }

    @Test
    fun anEventWithNoEndTimeStillReachesBob() = runBlocking<Unit> {
        // `toFirestoreMap()` writes "" for a missing end, which the reader used to throw on —
        // and the reading side skips a document it cannot parse, so Bob never got the event.
        val event = newEvent("School pickup").copy(endDateTime = null)
        alice.eventRepository.insertEvent(event)

        val onBobsPhone = assertNotNullAndGet(bob.eventRepository.fetchRemoteEvent(event.id))
        assertNull(onBobsPhone.endDateTime)
    }

    @Test
    fun aPrivateEventNeverReachesFirestore() = runBlocking<Unit> {
        val event = newEvent("Therapy", isPrivate = true)
        alice.eventRepository.insertEvent(event)

        // Saved on Alice's phone…
        assertNotNull(alice.eventRepository.getEventById(event.id))
        // …and nowhere else: not even the creator's own copy exists on the server (item 3).
        assertFalse(EmulatorEnvironment.documentExists("events/${event.id}"))
        assertNull(bob.eventRepository.fetchRemoteEvent(event.id))
    }

    @Test
    fun aDeletionReachesBobAsATombstone() = runBlocking<Unit> {
        val event = newEvent("Swimming")
        alice.eventRepository.insertEvent(event)
        assertNotNull(bob.eventRepository.fetchRemoteEvent(event.id))

        val stored = assertNotNullAndGet(alice.eventRepository.getEventById(event.id))
        alice.eventRepository.deleteEvent(stored)

        // The document stays, marked — never removed — so Bob's sync can learn of it (item 14).
        val shared = bob.eventDataSource.observeEventsSharedWith(bob.uid, changedAfter = null).first()
        val document = shared.documents.firstOrNull { it["id"] == event.id }
        assertNotNull("the tombstone left Bob's sync query", document)
        assertTrue(Tombstone.isDeleted(document!!))
        assertEquals(alice.uid, document[Tombstone.DELETED_BY])
        // The read path Bob's app takes treats it as gone.
        assertNull(bob.eventRepository.fetchRemoteEvent(event.id))
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

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    private companion object {
        const val START_HOUR = 10
    }
}
