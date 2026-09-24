package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transition table behind "a schedule change the other parent has to agree to".
 *
 * The property every case here exists to protect: a proposal changes nobody's calendar. Before
 * this, a save was last-write-wins with no consent step, and the losing parent found out from a
 * dismissible banner after the fact.
 */
class CustodyProposalTransitionTest {

    private val agreed = CustodyModel(
        id = "agreed",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 8, 3)
    )
    private val wanted = agreed.copy(id = "wanted", momDayIndices = (7..13).toSet())
    private val current = SharedCustody(
        model = agreed,
        lastModifiedBy = MOM,
        lastModifiedAtMillis = AGREED_AT_MILLIS,
        createdAt = "2026-08-01T09:00:00"
    )

    @Test
    fun `proposing leaves the pattern and its authorship completely alone`() {
        val next = CustodyProposalTransition
            .propose(current, wanted, repeatYearly = true, byUid = DAD, atIso = NOW).getOrThrow()

        assertEquals(agreed, next.model)
        assertEquals(MOM, next.lastModifiedBy)
        assertEquals(AGREED_AT_MILLIS, next.lastModifiedAtMillis)
        assertEquals(wanted, next.proposal?.model)
        assertEquals(DAD, next.proposal?.proposedBy)
        assertEquals(NOW, next.proposal?.proposedAt)
    }

    @Test
    fun `a parent may not overwrite the other's pending proposal`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val result = CustodyProposalTransition.propose(pending, agreed, true, MOM, LATER)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a parent may replace their own pending proposal`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition
            .propose(pending, agreed, true, DAD, LATER).getOrThrow()

        assertEquals(LATER, next.proposal?.proposedAt)
        assertEquals(agreed, next.proposal?.model)
    }

    @Test
    fun `accepting promotes the proposal and names the accepter`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition
            .accept(pending, byUid = MOM, atIso = LATER, atMillis = LATER_MILLIS).getOrThrow()

        assertEquals(wanted, next.model)
        assertEquals(MOM, next.lastModifiedBy)
        assertEquals(LATER_MILLIS, next.lastModifiedAtMillis)
        assertNull(next.proposal)
        assertEquals(CustodyDecisionOutcome.ACCEPTED, next.lastDecision?.outcome)
        assertEquals(NOW, next.lastDecision?.proposalAt)
    }

    @Test
    fun `a proposal carries its contact windows, and accepting makes them the agreed ones`() {
        // MON-6b. A proposal always says what the windows become, "none" included, so a
        // co-parent's build can tell it from a proposal an older build wrote without the key.
        val window = ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), DAD_SLOT)
        val withWindow = wanted.copy(contactWindows = listOf(window))

        val pending = CustodyProposalTransition
            .propose(current, withWindow, true, DAD, NOW).getOrThrow()
        assertEquals(listOf("2|15:00|19:00|dad"), pending.proposal?.contactWindowsWire)
        // Proposing moves nothing: the agreed document keeps what it had.
        assertNull(pending.contactWindowsWire)

        val accepted = CustodyProposalTransition.accept(pending, MOM, LATER, LATER_MILLIS).getOrThrow()
        assertEquals(listOf(window), accepted.model.contactWindows)
        assertEquals(listOf("2|15:00|19:00|dad"), accepted.contactWindowsWire)

        val none = CustodyProposalTransition.propose(current, wanted, true, DAD, NOW).getOrThrow()
        assertEquals(emptyList(), none.proposal?.contactWindowsWire)
    }

    @Test
    fun `declining leaves the stored windows exactly as they were`() {
        // A decline re-sends the document; `firestore.rules` refuses one that changes the list.
        val stored = current.copy(contactWindowsWire = listOf("2|15:00|19:00|dad"))
        val pending = CustodyProposalTransition.propose(stored, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition.decline(pending, MOM, LATER, null).getOrThrow()

        assertEquals(stored.contactWindowsWire, next.contactWindowsWire)
    }

    @Test
    fun `accepting does not re-date the arrangement itself`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition.accept(pending, MOM, LATER, LATER_MILLIS).getOrThrow()

        assertEquals("2026-08-01T09:00:00", next.createdAt)
    }

    @Test
    fun `declining keeps the agreed pattern and carries the note`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition
            .decline(pending, byUid = MOM, atIso = LATER, note = "School run").getOrThrow()

        assertEquals(agreed, next.model)
        assertEquals(MOM, next.lastModifiedBy)
        assertEquals(AGREED_AT_MILLIS, next.lastModifiedAtMillis)
        assertNull(next.proposal)
        assertEquals(CustodyDecisionOutcome.DECLINED, next.lastDecision?.outcome)
        assertEquals("School run", next.lastDecision?.note)
    }

    @Test
    fun `a blank decline note is stored as no note at all`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        val next = CustodyProposalTransition.decline(pending, MOM, LATER, "   ").getOrThrow()

        assertNull(next.lastDecision?.note)
    }

    @Test
    fun `the proposer may not decide their own proposal`() {
        // Self-accepting is exactly the unilateral change this replaces, arriving through the
        // new door instead of the old one.
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        assertTrue(CustodyProposalTransition.accept(pending, DAD, LATER, LATER_MILLIS).isFailure)
        assertTrue(CustodyProposalTransition.decline(pending, DAD, LATER, null).isFailure)
    }

    @Test
    fun `only the proposer may withdraw, and withdrawing records no decision`() {
        val pending = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW).getOrThrow()

        assertTrue(CustodyProposalTransition.withdraw(pending, MOM).isFailure)

        val next = CustodyProposalTransition.withdraw(pending, DAD).getOrThrow()
        assertNull(next.proposal)
        assertNull(next.lastDecision)
    }

    @Test
    fun `a proposal carries the plan citation it was given, and none by default`() {
        val cited = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW, planCitation = CITATION).getOrThrow()
        val plain = CustodyProposalTransition.propose(current, wanted, true, DAD, NOW).getOrThrow()

        assertEquals(CITATION, cited.proposal?.planCitationWire)
        assertNull(plain.proposal?.planCitationWire)
        // A citation is never an input: the proposed pattern is exactly what was built.
        assertEquals(wanted, cited.proposal?.model)
    }

    @Test
    fun `replacing one's own cited proposal without a citation drops it`() {
        val cited = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW, planCitation = CITATION).getOrThrow()

        val corrected = CustodyProposalTransition.propose(cited, agreed, true, DAD, LATER).getOrThrow()

        assertNull(corrected.proposal?.planCitationWire)
    }

    @Test
    fun `every answer to a cited proposal clears the citation with it`() {
        val cited = CustodyProposalTransition
            .propose(current, wanted, true, DAD, NOW, planCitation = CITATION).getOrThrow()

        assertNull(CustodyProposalTransition.accept(cited, MOM, LATER, LATER_MILLIS).getOrThrow().proposal)
        assertNull(CustodyProposalTransition.decline(cited, MOM, LATER, null).getOrThrow().proposal)
        assertNull(CustodyProposalTransition.withdraw(cited, DAD).getOrThrow().proposal)
    }

    @Test
    fun `only the other parent's proposal counts as waiting for an answer`() {
        val pending = CustodyProposalTransition.propose(current, wanted, true, DAD, NOW).getOrThrow()

        assertTrue(CustodyProposalTransition.pendingFromCoParent(pending, MOM))
        assertFalse(CustodyProposalTransition.pendingFromCoParent(pending, DAD))
        assertFalse(CustodyProposalTransition.pendingFromCoParent(current, MOM))
        assertFalse(CustodyProposalTransition.pendingFromCoParent(null, MOM))
    }

    @Test
    fun `deciding when nothing is pending is a failure, not a no-op`() {
        assertTrue(CustodyProposalTransition.accept(current, MOM, NOW, NOW_MILLIS).isFailure)
        assertTrue(CustodyProposalTransition.decline(current, MOM, NOW, null).isFailure)
        assertTrue(CustodyProposalTransition.withdraw(current, MOM).isFailure)
    }

    private companion object {
        const val MOM = "uid-mom"
        const val DAD = "uid-dad"

        /** The schema slot id a window names — a slot, not a uid. */
        const val DAD_SLOT = "dad"
        const val NOW = "2026-08-09T08:00:00"
        const val LATER = "2026-08-09T09:00:00"
        const val CITATION = "p1|care_weekday|0123456789abcdef"

        /**
         * The document's own dates as instants.
         *
         * Built through [CustodyTimestamp] rather than written as bare millis so the fixture
         * still reads as a date. What the projection itself does is pinned directly, and
         * separately, by `CustodyTimestampTest`.
         */
        val AGREED_AT_MILLIS = CustodyTimestamp.fromWire("2026-08-03T10:00:00")
        val LATER_MILLIS = CustodyTimestamp.fromWire(LATER)
        val NOW_MILLIS = CustodyTimestamp.fromWire(NOW)
    }
}
