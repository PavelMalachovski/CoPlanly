package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.export.RecordFixtures.ALICE
import com.coparently.app.domain.export.RecordFixtures.BOB
import com.coparently.app.domain.export.RecordFixtures.MARCH_10_0900
import com.coparently.app.domain.export.RecordFixtures.expense
import com.coparently.app.domain.export.RecordFixtures.facts
import com.coparently.app.domain.export.RecordFixtures.revision
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CommunicationRecordBuilder] — what a communication record contains (MON-3).
 *
 * The export is only worth what it leaves out as much as what it puts in, so both directions are
 * pinned: a private event, a medical record and another family's revisions never appear; an
 * edit that moved an entry out of the period, a revision still on this phone and a message this
 * phone never sent all do, labelled as what they are.
 */
class CommunicationRecordBuilderTest {

    @Test
    fun `revisions are ordered by the server's clock and numbered from one`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(
                    revision("v2", kind = EventVersionKind.UPDATED, editor = BOB, recordedAt = 3_000L),
                    revision("v1", kind = EventVersionKind.CREATED, recordedAt = 1_000L),
                    revision("v3", kind = EventVersionKind.DELETED, recordedAt = 5_000L)
                )
            ),
            scope()
        )

        val revisions = record.events.single().revisions
        assertEquals(listOf(1, 2, 3), revisions.map { it.number })
        assertEquals(
            listOf(EventVersionKind.CREATED, EventVersionKind.UPDATED, EventVersionKind.DELETED),
            revisions.map { it.kind }
        )
        assertEquals(listOf("Alice", "Bob", "Alice"), revisions.map { it.byName })
    }

    @Test
    fun `a revision the server has not seen goes last and is marked undelivered`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(
                    revision("pending", recordedAt = null, deviceTime = 1L),
                    revision("landed", recordedAt = 9_000L, deviceTime = 2L)
                )
            ),
            scope()
        )

        val revisions = record.events.single().revisions
        assertEquals(listOf(true, false), revisions.map { it.delivered })
        assertNull(revisions.last().recordedAtMillis)
    }

    @Test
    fun `the same revision from the server and the outbox is printed once, with the server time`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(
                    revision("v1", recordedAt = null),
                    revision("v1", recordedAt = 7_000L)
                )
            ),
            scope()
        )

        val only = record.events.single().revisions.single()
        assertEquals(7_000L, only.recordedAtMillis)
    }

    @Test
    fun `an edit that moved an entry out of the period keeps the whole history in the record`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(
                    revision("v1", recordedAt = 1_000L, facts = facts(start = LocalDateTime.of(2026, 3, 20, 10, 0))),
                    revision("v2", recordedAt = 2_000L, facts = facts(start = LocalDateTime.of(2026, 5, 2, 10, 0)))
                )
            ),
            scope()
        )

        assertEquals(2, record.events.single().revisions.size)
    }

    @Test
    fun `an entry never inside the period is left out`() {
        val record = CommunicationRecordBuilder.build(
            sources(revisions = listOf(revision("v1", facts = facts(start = LocalDateTime.of(2026, 6, 1, 10, 0))))),
            scope()
        )

        assertTrue(record.events.isEmpty())
    }

    @Test
    fun `a series that began before the period and still runs is in it`() {
        val weekly = facts(
            start = LocalDateTime.of(2025, 9, 1, 16, 0),
            end = LocalDateTime.of(2025, 9, 1, 17, 0),
            recurrence = "WEEKLY"
        )
        val ended = weekly.copy(recurrenceEnd = LocalDate.of(2026, 1, 31))

        val running = CommunicationRecordBuilder.build(
            sources(revisions = listOf(revision("v1", facts = weekly))),
            scope()
        )
        val over = CommunicationRecordBuilder.build(
            sources(revisions = listOf(revision("v1", facts = ended))),
            scope()
        )

        assertEquals(1, running.events.size)
        assertTrue(over.events.isEmpty())
    }

    @Test
    fun `another family's revisions are not in this family's record, unshared ones are`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(
                    revision("mine", eventId = "e1"),
                    revision("unshared", eventId = "e2", familyId = ""),
                    revision("other", eventId = "e3", familyId = "alice-uid__carol-uid")
                )
            ),
            scope()
        )

        assertEquals(setOf("e1", "e2"), record.events.map { it.eventId }.toSet())
    }

    @Test
    fun `an entry saved before revisions were kept is printed as its current state, never as a creation`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                currentEvents = listOf(
                    CurrentEventInput(
                        "old",
                        RecordFixtures.FAMILY,
                        isPrivate = false,
                        creatorUid = BOB,
                        facts = facts()
                    )
                )
            ),
            scope()
        )

        val event = record.events.single()
        assertFalse(event.hasHistory)
        val state = event.revisions.single()
        assertNull(state.kind)
        assertNull(state.deviceTimeMillis)
        assertEquals("Bob", state.byName)
    }

    @Test
    fun `an entry with revisions is not printed a second time as its current state`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(revision("v1", eventId = "e1")),
                currentEvents = listOf(
                    CurrentEventInput(
                        "e1",
                        RecordFixtures.FAMILY,
                        isPrivate = false,
                        creatorUid = ALICE,
                        facts = facts()
                    )
                )
            ),
            scope()
        )

        assertEquals(1, record.events.size)
        assertTrue(record.events.single().hasHistory)
    }

    @Test
    fun `a private event is never in the record, whatever the source hands over`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                currentEvents = listOf(
                    CurrentEventInput("secret", "", isPrivate = true, creatorUid = ALICE, facts = facts())
                )
            ),
            scope()
        )

        assertTrue(record.events.isEmpty())
    }

    @Test
    fun `the custody slot is printed as the parent's name, never as a role`() {
        val record = CommunicationRecordBuilder.build(sources(revisions = listOf(revision("v1"))), scope())

        assertEquals("Alice", record.events.single().revisions.single().parentName)
    }

    @Test
    fun `messages are cut at the period's edges in the reader's zone and keep their delivery state`() {
        val beforeMidnight = 1_772_319_599_000L // 2026-02-28 23:59:59 in Prague
        val lastSecond = 1_774_994_399_000L // 2026-03-31 23:59:59 in Prague (after the DST change)
        val record = CommunicationRecordBuilder.build(
            sources(
                messages = listOf(
                    MessageInput("m0", ALICE, beforeMidnight, "too early", delivered = true),
                    MessageInput("m2", BOB, lastSecond, "just in", delivered = true),
                    MessageInput("m1", ALICE, MARCH_10_0900, "never left", delivered = false)
                )
            ),
            scope()
        )

        assertEquals(listOf("m1", "m2"), record.messages.map { it.messageId })
        assertEquals(listOf(false, true), record.messages.map { it.delivered })
        assertEquals(listOf("Alice", "Bob"), record.messages.map { it.senderName })
    }

    @Test
    fun `expenses are cut by their own date and by family`() {
        val record = CommunicationRecordBuilder.build(
            sources(
                expenses = listOf(
                    expense("x1", LocalDate.of(2026, 3, 31)),
                    expense("x2", LocalDate.of(2026, 4, 1)),
                    expense("x3", LocalDate.of(2026, 3, 5), familyId = null),
                    expense("x4", LocalDate.of(2026, 3, 5), familyId = "alice-uid__carol-uid")
                )
            ),
            scope()
        )

        assertEquals(listOf("x3", "x1"), record.expenses.map { it.expenseId })
        assertEquals("Bob", record.expenses.first().paidByName)
    }

    @Test
    fun `a record assembled without the server says so`() {
        val record = CommunicationRecordBuilder.build(sources(serverReached = false), scope())

        assertFalse(record.complete)
    }

    @Test
    fun `a document's facts are read without guessing`() {
        val facts = CommunicationRecordBuilder.factsOf(
            mapOf(
                "title" to "Swimming",
                "startDateTime" to "2026-03-12T16:00:00",
                "endDateTime" to "",
                "parentOwner" to "dad",
                "isRecurring" to true,
                "recurrencePattern" to "WEEKLY",
                "recurrenceEndDate" to "not a date"
            )
        )

        assertEquals(LocalDateTime.of(2026, 3, 12, 16, 0), facts.start)
        assertNull(facts.end)
        assertEquals("WEEKLY", facts.recurrence)
        assertNull(facts.recurrenceEnd)
        assertEquals("dad", facts.parentSlot)
    }
}
