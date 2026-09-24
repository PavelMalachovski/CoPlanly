package com.coparently.app.domain.parentingplan

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * From an agreed plan answer to a proposal (MON-21, CLAUDE.md item 32): which questions offer
 * it, when it is hidden, and how the reader's phone decides whether a citation still stands.
 */
class PlanScheduleLinkTest {

    private val custody = "care_weekday"
    private val summer = "holidays_school"
    private val weekOnWeekOff = "Week on, week off, changing on Monday at school"

    /** Two halves where both parents wrote [yours]/[theirs] and each ticked the other's. */
    private fun agreed(questionId: String, yours: String, theirs: String = yours) = Pair(
        ParentingPlanEntry(answers = mapOf(questionId to yours), agreedTo = mapOf(questionId to theirs)),
        ParentingPlanEntry(answers = mapOf(questionId to theirs), agreedTo = mapOf(questionId to yours))
    )

    private fun offers(
        questionId: String,
        yours: ParentingPlanEntry,
        theirs: ParentingPlanEntry?,
        paired: Boolean = true,
        pending: Boolean = false
    ) = PlanScheduleLink.offersProposal(questionId, yours, theirs, paired, pending)

    @Test
    fun `the schedule questions are the rotation and the two holiday questions, and every one exists`() {
        assertEquals(
            mapOf(
                "care_weekday" to PlanScheduleTarget.BASE_PATTERN,
                "holidays_school" to PlanScheduleTarget.SEASONAL_LAYER,
                "holidays_special" to PlanScheduleTarget.SEASONAL_LAYER
            ),
            PlanScheduleLink.SCHEDULE_QUESTIONS
        )
        // A renamed catalogue id would silently remove the action; fail here instead.
        assertTrue(ParentingPlanCatalogue.questionIds.containsAll(PlanScheduleLink.SCHEDULE_QUESTIONS.keys))
        assertNull(PlanScheduleLink.targetOf("care_handover"))
        assertNull(PlanScheduleLink.targetOf("residence_home"))
    }

    @Test
    fun `an agreed schedule answer offers a proposal`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)

        assertTrue(offers(custody, yours, theirs))
    }

    @Test
    fun `an agreed answer that is not about the schedule offers nothing`() {
        val (yours, theirs) = agreed("health_doctor", "Dr Novák")

        assertFalse(offers("health_doctor", yours, theirs))
    }

    @Test
    fun `an answer not yet agreed by both offers nothing`() {
        val yours = ParentingPlanEntry(
            answers = mapOf(custody to weekOnWeekOff),
            agreedTo = mapOf(custody to "2-2-3")
        )
        val theirs = ParentingPlanEntry(answers = mapOf(custody to "2-2-3"))

        assertFalse(offers(custody, yours, theirs))
        assertNull(PlanScheduleLink.reference(custody, yours, theirs))
    }

    @Test
    fun `unpaired, or while the co-parent's proposal waits, nothing is offered`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)

        assertFalse(offers(custody, yours, theirs, paired = false))
        assertFalse(offers(custody, yours, null))
        assertFalse(offers(custody, yours, theirs, pending = true))
    }

    @Test
    fun `the reference quotes both wordings and cites their hash, the same from either phone`() {
        val (yours, theirs) = agreed(summer, "July with me, August with you", "July to Olya, August to Pavel")

        val mine = assertNotNull(PlanScheduleLink.reference(summer, yours, theirs))
        val other = assertNotNull(PlanScheduleLink.reference(summer, theirs, yours))

        assertEquals(PlanScheduleTarget.SEASONAL_LAYER, mine.target)
        assertEquals("July with me, August with you", mine.yourAnswer)
        assertEquals("July to Olya, August to Pavel", mine.theirAnswer)
        assertFalse(mine.sameWording)
        // Both phones derive the same citation, or the reader would always see "changed".
        assertEquals(mine.citation, other.citation)
        assertTrue(mine.citationWire.startsWith("p1|holidays_school|"))
    }

    @Test
    fun `identical wording hashes exactly the words both parents see`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)

        val reference = assertNotNull(PlanScheduleLink.reference(custody, yours, theirs))

        assertTrue(reference.sameWording)
        assertEquals(PlanCitationCodec.hashOf(weekOnWeekOff), reference.citation.answerHash)
    }

    @Test
    fun `a citation that still matches the plan is current`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)
        val wire = assertNotNull(PlanScheduleLink.reference(custody, yours, theirs)).citationWire

        // Read on the co-parent's phone, where the halves are the other way round.
        assertEquals(CitationStatus.Current(custody), PlanScheduleLink.citationStatus(wire, theirs, yours))
    }

    @Test
    fun `a citation whose answer was edited since reads as changed`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)
        val wire = assertNotNull(PlanScheduleLink.reference(custody, yours, theirs)).citationWire

        // The proposer re-words the answer; both re-tick, so it is agreed again — on new words.
        val (editedYours, editedTheirs) = agreed(custody, "Week on, week off, changing on Friday")
        assertEquals(
            CitationStatus.Changed(custody),
            PlanScheduleLink.citationStatus(wire, editedTheirs, editedYours)
        )

        // An edit that has not been re-agreed is also a change: nothing is agreed any more.
        val lapsed = theirs.withAnswer(custody, "Something else", nowMillis = 1L)
        assertEquals(CitationStatus.Changed(custody), PlanScheduleLink.citationStatus(wire, lapsed, yours))
    }

    @Test
    fun `no citation, an unreadable one, or an unknown question says nothing`() {
        val (yours, theirs) = agreed(custody, weekOnWeekOff)

        assertEquals(CitationStatus.None, PlanScheduleLink.citationStatus(null, yours, theirs))
        assertEquals(CitationStatus.None, PlanScheduleLink.citationStatus("p2|whatever", yours, theirs))
        assertEquals(
            CitationStatus.None,
            PlanScheduleLink.citationStatus("p1|retired_question|0123456789abcdef", yours, theirs)
        )
    }
}
