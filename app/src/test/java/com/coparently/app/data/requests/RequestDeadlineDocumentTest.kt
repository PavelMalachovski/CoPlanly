package com.coparently.app.data.requests

import com.coparently.app.data.requests.RequestDeadlineDocument.ServerTimes
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [RequestDeadlineDocument]: the wire form of a request's clock, both ways (phase 3a, spec §5.1).
 *
 * The create set and the shapes of the later writes are what `firestore.rules` accepts. The field
 * names are spelled out as strings here, on purpose, so a typo in one of the constants cannot pass.
 */
class RequestDeadlineDocumentTest {

    private val swapStart = RequestStart(
        kind = RequestKind.SWAP,
        ref = "g-1",
        familyId = "uidA__uidB",
        responderUid = "uidA",
        answerByMillis = 1_778_616_000_000L,
        capMillis = 1_778_889_600_000L,
        firstDate = LocalDate.of(2026, 5, 16),
        dayCount = 2
    )

    // ---- what a creator may write ------------------------------------------------------------

    @Test
    fun `a new clock is written with only the fields its creator may write`() {
        // The proposer sorts after the responder, so a participants list in call order would fail.
        val fields = RequestDeadlineDocument.createFields(swapStart, proposerUid = "uidB")

        assertEquals(
            "the create set, and not createdAt, which only the data source can stamp",
            setOf(
                "kind", "ref", "familyId", "proposerUid", "responderUid", "participants",
                "answerByMillis", "capMillis", "firstDate", "dayCount"
            ),
            fields.keys
        )
        assertEquals("swap", fields["kind"])
        assertEquals("g-1", fields["ref"])
        assertEquals("uidA__uidB", fields["familyId"])
        assertEquals("uidB", fields["proposerUid"])
        assertEquals("uidA", fields["responderUid"])
        assertEquals("sorted, whoever is the proposer", listOf("uidA", "uidB"), fields["participants"])
        assertEquals(1_778_616_000_000L, fields["answerByMillis"])
        assertEquals(1_778_889_600_000L, fields["capMillis"])
        assertEquals("ISO, the form every date in this schema takes", "2026-05-16", fields["firstDate"])
        assertEquals(2, fields["dayCount"])

        val bare = RequestDeadlineDocument.createFields(
            swapStart.copy(kind = RequestKind.CHANGE, capMillis = null, firstDate = null, dayCount = null),
            proposerUid = "uidB"
        )
        assertEquals(
            "an optional field is absent, never null: the rule refuses a key it did not expect",
            setOf("kind", "ref", "familyId", "proposerUid", "responderUid", "participants", "answerByMillis"),
            bare.keys
        )
        assertEquals("change", bare["kind"])
    }

    @Test
    fun `the plan is written as one write`() {
        // Three reminders: the most a plan holds, and the most the rule looks at.
        val reminders = listOf(1_778_086_400_000L, 1_778_172_800_000L, 1_778_252_000_000L)
        val plan = RequestDeadlineDocument.deliveredFields(
            deliveredAtMillis = 1_778_000_060_000L,
            reminderTimesMillis = reminders
        )

        assertEquals(
            "the rule wants all three together and nothing else",
            setOf("deliveredAtMillis", "remindersPlanned", "reminderTimesMillis"),
            plan.keys
        )
        assertEquals(1_778_000_060_000L, plan["deliveredAtMillis"])
        assertEquals("the count is the list's length, so the two cannot disagree", 3, plan["remindersPlanned"])
        assertEquals("every time, in the order planned", reminders, plan["reminderTimesMillis"])

        val late = RequestDeadlineDocument.deliveredFields(1_778_000_060_000L, emptyList())
        assertEquals("a clock received too late to plan still records the delivery", 0, late["remindersPlanned"])
        assertEquals(emptyList<Long>(), late["reminderTimesMillis"])
        assertEquals(plan.keys, late.keys)
    }

    // ---- what a reader makes of a stored clock -----------------------------------------------

    @Test
    fun `every field reads back, whatever number type the store hands over`() {
        // Values small enough for an Int, so the same clock can be handed over as each number type.
        val expected = RequestDeadline(
            id = ID,
            kind = RequestKind.SWAP,
            ref = "g-1",
            familyId = "uidA__uidB",
            proposerUid = "uidA",
            responderUid = "uidB",
            answerByMillis = 1_500_000_000L,
            capMillis = 1_900_000_000L,
            createdAtMillis = 1_000_000_000L,
            firstDate = LocalDate.of(2026, 5, 16),
            dayCount = 2,
            deliveredAtMillis = 1_010_000_000L,
            remindersPlanned = 3,
            reminderTimesMillis = listOf(1_100_000_000L, 1_200_000_000L, 1_300_000_000L),
            seenAtMillis = 1_020_000_000L,
            remindersShown = 2,
            lastChanceAtMillis = 1_550_000_000L,
            lastChanceUntilMillis = 1_600_000_000L,
            outcome = RequestClockOutcome.NO_ANSWER,
            closedAtMillis = 1_650_000_000L
        )
        val times = ServerTimes(
            createdAtMillis = 1_000_000_000L,
            lastChanceAtMillis = 1_550_000_000L,
            closedAtMillis = 1_650_000_000L
        )

        val numberTypes: Map<String, (Long) -> Number> = mapOf(
            "Long" to { n: Long -> n },
            "Int" to { n: Long -> n.toInt() },
            "Double" to { n: Long -> n.toDouble() }
        )
        numberTypes.forEach { (type, number) ->
            val parsed = RequestDeadlineDocument.parse(ID, storedFull(number), times)
            assertEquals("numbers handed over as $type", expected, parsed)
        }
    }

    @Test
    fun `a clock this build cannot read is skipped`() {
        val readable = storedMinimal()
        assertNotNull("the control: this document reads", RequestDeadlineDocument.parse(ID, readable, NO_TIMES))

        val cannotRead = mapOf(
            "a kind this build does not know" to (readable + ("kind" to "handover")),
            "a kind that is not text" to (readable + ("kind" to 3)),
            "a blank reference" to (readable + ("ref" to "  ")),
            "no reference" to (readable - "ref"),
            "no deadline" to (readable - "answerByMillis"),
            "a deadline that is not a number" to (readable + ("answerByMillis" to "soon")),
            "no responder" to (readable - "responderUid"),
            "no proposer" to (readable - "proposerUid"),
            "no family" to (readable - "familyId")
        )
        cannotRead.forEach { (why, document) ->
            assertNull("skipped for $why", RequestDeadlineDocument.parse(ID, document, NO_TIMES))
        }
    }

    @Test
    fun `an unknown field is ignored and an unknown outcome is not a closure`() {
        val plain = RequestDeadlineDocument.parse(ID, storedMinimal(), NO_TIMES)
        val newer = storedMinimal() + mapOf(
            "escalation" to mapOf("channel" to "sms", "level" to 2),
            "outcome" to "withdrawn"
        )

        // The server stamped a closure time beside the outcome this build cannot name.
        val parsed = RequestDeadlineDocument.parse(ID, newer, ServerTimes(closedAtMillis = 1_650_000_000L))

        assertNotNull(parsed)
        assertNull("an outcome this build does not know closes nothing", parsed?.outcome)
        assertNull("and a closure time without a closure is not kept", parsed?.closedAtMillis)
        assertEquals("the unknown field changed nothing else", plain, parsed)
    }

    @Test
    fun `the server's times come from the caller, never from the document`() {
        val times = ServerTimes(createdAtMillis = 42L, lastChanceAtMillis = 43L, closedAtMillis = 44L)

        // The map holds a Firestore Timestamp, or nothing while the write is pending. Were it ever to
        // hold a number, that would not be the server's time either: only the caller's is.
        listOf<Any?>(Any(), null, 7L).forEach { inTheMap ->
            val stored = storedMinimal() + mapOf(
                "createdAt" to inTheMap,
                "lastChanceAt" to inTheMap,
                "closedAt" to inTheMap,
                "outcome" to "no_answer"
            )

            val parsed = RequestDeadlineDocument.parse(ID, stored, times)

            assertEquals("$inTheMap in the map", 42L, parsed?.createdAtMillis)
            assertEquals("$inTheMap in the map", 43L, parsed?.lastChanceAtMillis)
            assertEquals("$inTheMap in the map", 44L, parsed?.closedAtMillis)
        }

        val withoutOutcome = RequestDeadlineDocument.parse(ID, storedMinimal(), times)
        assertEquals(42L, withoutOutcome?.createdAtMillis)
        assertNull("no outcome in the document, so no closure: a time alone is not one", withoutOutcome?.closedAtMillis)
        assertNull(RequestDeadlineDocument.parse(ID, storedMinimal(), NO_TIMES)?.createdAtMillis)
    }

    @Test
    fun `a delivery or opening time a little before the clock was created is read as it is`() {
        // The rules allow a phone whose clock runs slow to stamp up to five minutes before createdAt.
        val created = 1_778_000_000_000L
        val stored = storedMinimal() + mapOf(
            "deliveredAtMillis" to created - 4 * MINUTE,
            "seenAtMillis" to created - 3 * MINUTE
        )

        val parsed = RequestDeadlineDocument.parse(ID, stored, ServerTimes(createdAtMillis = created))

        assertEquals(created - 4 * MINUTE, parsed?.deliveredAtMillis)
        assertEquals(created - 3 * MINUTE, parsed?.seenAtMillis)
    }

    @Test
    fun `an optional field that cannot be read is absent and the clock still reads`() {
        val stored = storedMinimal() + mapOf(
            "firstDate" to "the sixteenth",
            "dayCount" to "two",
            "capMillis" to true,
            "remindersPlanned" to "three",
            "remindersShown" to null,
            "reminderTimesMillis" to listOf(1_100L, "noon", null, 1_300.0)
        )

        val parsed = RequestDeadlineDocument.parse(ID, stored, NO_TIMES)

        assertNotNull(parsed)
        assertNull(parsed?.firstDate)
        assertNull(parsed?.dayCount)
        assertNull(parsed?.capMillis)
        assertNull(parsed?.remindersPlanned)
        assertEquals("no count reads as none shown", 0, parsed?.remindersShown)
        assertEquals("the entries that are numbers are kept", listOf(1_100L, 1_300L), parsed?.reminderTimesMillis)
    }

    @Test
    fun `a clock written by the create fields reads back as the clock that was started`() {
        val id = RequestDeadlineId.of(RequestKind.SWAP, "uidB", "g-1", "uidA__uidB")
        val fields: Map<String, Any?> = RequestDeadlineDocument.createFields(swapStart, proposerUid = "uidB")

        val parsed = RequestDeadlineDocument.parse(id, fields, ServerTimes(createdAtMillis = 1_778_000_000_000L))

        assertEquals(
            "nothing the create does not write is invented",
            RequestDeadline(
                id = id,
                kind = RequestKind.SWAP,
                ref = "g-1",
                familyId = "uidA__uidB",
                proposerUid = "uidB",
                responderUid = "uidA",
                answerByMillis = swapStart.answerByMillis,
                capMillis = swapStart.capMillis,
                createdAtMillis = 1_778_000_000_000L,
                firstDate = swapStart.firstDate,
                dayCount = swapStart.dayCount
            ),
            parsed
        )
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private fun storedMinimal(): Map<String, Any?> = mapOf(
        "kind" to "change",
        "ref" to "cr-1",
        "familyId" to "uidA__uidB",
        "proposerUid" to "uidA",
        "responderUid" to "uidB",
        "participants" to listOf("uidA", "uidB"),
        "answerByMillis" to 1_500_000_000L
    )

    /** Every stored field, each number as [number] builds it. */
    private fun storedFull(number: (Long) -> Number): Map<String, Any?> = mapOf(
        "kind" to "swap",
        "ref" to "g-1",
        "familyId" to "uidA__uidB",
        "proposerUid" to "uidA",
        "responderUid" to "uidB",
        "participants" to listOf("uidA", "uidB"),
        "answerByMillis" to number(1_500_000_000L),
        "capMillis" to number(1_900_000_000L),
        "firstDate" to "2026-05-16",
        "dayCount" to number(2L),
        "deliveredAtMillis" to number(1_010_000_000L),
        "remindersPlanned" to number(3L),
        "reminderTimesMillis" to listOf(1_100_000_000L, 1_200_000_000L, 1_300_000_000L).map(number),
        "seenAtMillis" to number(1_020_000_000L),
        "remindersShown" to number(2L),
        "lastChanceUntilMillis" to number(1_600_000_000L),
        "outcome" to "no_answer",
        // The three server stamps are not in the document's map as numbers; the data source reads them.
        "createdAt" to Any(),
        "lastChanceAt" to Any(),
        "closedAt" to Any()
    )

    private companion object {
        const val ID = "swap_uidA_g-1"
        const val MINUTE = 60_000L
        val NO_TIMES = ServerTimes()
    }
}
