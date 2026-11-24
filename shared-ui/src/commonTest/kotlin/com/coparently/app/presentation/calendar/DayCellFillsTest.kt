package com.coparently.app.presentation.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The branching that decides a day cell's fill.
 *
 * Before this existed, the month cell picked exactly one background with custody ahead of the
 * weekend, and `CustodyModel.getCustodyFor` never returns null — so on any account with an
 * active custody model the weekend branch was unreachable and Saturday/Sunday were tinted only
 * in the grid rows that reach into a neighbouring month.
 */
class DayCellFillsTest {

    @Test
    fun `a weekend under a custody wash keeps its grey base`() {
        assertEquals(
            DayCellFill(DayCellBase.WEEKEND, DayCellOverlay.CUSTODY_MOM),
            DayCellFills.monthCell(
                isWeekend = true,
                isCurrentMonth = true,
                custody = "mom",
                isPublicHoliday = false
            )
        )
    }

    @Test
    fun `a day in a neighbouring month keeps the custody band marked as borrowed`() {
        // It used to keep nothing, and the band stopped in the middle of a grid row: the last
        // days of the month were coloured and the cells beside them were blank, with no rule a
        // reader could infer. `isAdjacentMonth` is how the caller knows to draw it recessively.
        assertEquals(
            DayCellFill(
                base = DayCellBase.SURFACE,
                overlay = DayCellOverlay.CUSTODY_MOM,
                isAdjacentMonth = true
            ),
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = false,
                custody = "mom",
                isPublicHoliday = false
            )
        )
    }

    @Test
    fun `a borrowed weekend keeps its grey base under the band`() {
        assertEquals(
            DayCellFill(
                base = DayCellBase.WEEKEND,
                overlay = DayCellOverlay.CUSTODY_DAD,
                isAdjacentMonth = true
            ),
            DayCellFills.monthCell(
                isWeekend = true,
                isCurrentMonth = false,
                custody = "dad",
                isPublicHoliday = false
            )
        )
    }

    @Test
    fun `a borrowed day still refuses the holiday tint`() {
        // The band is a pattern and has to run to the edge of the grid; a holiday marks one day,
        // and one day missing from a cell you cannot act on breaks nothing. Custody is absent
        // here, so the holiday branch is the one under test.
        assertEquals(
            DayCellFill(
                base = DayCellBase.SURFACE,
                overlay = DayCellOverlay.NONE,
                isAdjacentMonth = true
            ),
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = false,
                custody = null,
                isPublicHoliday = true
            )
        )
    }

    @Test
    fun `a borrowed day is not marked as a pending proposal`() {
        // A proposal is a day you would answer, and a borrowed cell refuses every gesture.
        assertNull(
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = false,
                custody = "mom",
                isPublicHoliday = false,
                proposedCustody = "dad"
            ).pendingProposalFor
        )
    }

    @Test
    fun `custody still beats a public holiday`() {
        assertEquals(
            DayCellFill(DayCellBase.SURFACE, DayCellOverlay.CUSTODY_DAD),
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = true,
                custody = "dad",
                isPublicHoliday = true
            )
        )
    }

    @Test
    fun `a public holiday on a weekend tints over the grey base`() {
        assertEquals(
            DayCellFill(DayCellBase.WEEKEND, DayCellOverlay.PUBLIC_HOLIDAY),
            DayCellFills.monthCell(
                isWeekend = true,
                isCurrentMonth = true,
                custody = null,
                isPublicHoliday = true
            )
        )
    }

    @Test
    fun `a plain weekday has no overlay`() {
        assertEquals(
            DayCellFill(DayCellBase.SURFACE, DayCellOverlay.NONE),
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = true,
                custody = null,
                isPublicHoliday = false
            )
        )
    }

    @Test
    fun `an unknown custody slot is not treated as a parent`() {
        assertEquals(
            DayCellFill(DayCellBase.SURFACE, DayCellOverlay.NONE),
            DayCellFills.monthCell(
                isWeekend = false,
                isCurrentMonth = true,
                custody = "grandma",
                isPublicHoliday = false
            )
        )
    }

    @Test
    fun `a week hour cell on a weekend keeps its grey base under custody`() {
        assertEquals(
            DayCellFill(DayCellBase.WEEKEND, DayCellOverlay.CUSTODY_MOM),
            DayCellFills.weekHourCell(isWeekend = true, isToday = false, custody = "mom")
        )
    }

    @Test
    fun `custody still beats today in the week view`() {
        assertEquals(
            DayCellFill(DayCellBase.SURFACE, DayCellOverlay.CUSTODY_DAD),
            DayCellFills.weekHourCell(isWeekend = false, isToday = true, custody = "dad")
        )
    }

    @Test
    fun `today tints a weekend hour cell over the grey base`() {
        assertEquals(
            DayCellFill(DayCellBase.WEEKEND, DayCellOverlay.TODAY),
            DayCellFills.weekHourCell(isWeekend = true, isToday = true, custody = null)
        )
    }

    @Test
    fun `a day the child changes hands on is one fill in its own parent's colour`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "dad",
            isPublicHoliday = false
        )
        assertEquals(DayCellOverlay.CUSTODY_DAD, fill.overlay)
        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `a day a pending proposal would move draws as a preview over the agreed day`() {
        // The agreed pattern keeps its full strength underneath: `overlay` is still the parent
        // whose day it is *now*. A grid that showed only the proposal would tell a parent their
        // days had changed when they had not.
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "mom",
            isPublicHoliday = false,
            proposedCustody = "dad"
        )

        assertEquals(DayCellOverlay.CUSTODY_MOM, fill.overlay)
        assertEquals(DayCellOverlay.CUSTODY_DAD, fill.pendingProposalFor)
    }

    @Test
    fun `the same day with no proposal draws at full strength and nothing else`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "mom",
            isPublicHoliday = false
        )

        assertEquals(DayCellOverlay.CUSTODY_MOM, fill.overlay)
        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `an accepted proposal is indistinguishable from an ordinary agreed day`() {
        // Accepting makes the proposal the agreed pattern and clears it from the document, so
        // the lookup answers "dad" and nothing is pending. Nothing about the cell should say a
        // proposal was ever involved.
        val accepted = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "dad",
            isPublicHoliday = false,
            proposedCustody = null
        )
        val ordinary = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "dad",
            isPublicHoliday = false
        )

        assertEquals(ordinary, accepted)
    }

    @Test
    fun `a day the proposal does not move is not marked`() {
        // A proposal is a whole pattern, and most of its days agree with the agreed one. Marking
        // those too would wash the month and say nothing.
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "mom",
            isPublicHoliday = false,
            proposedCustody = "mom"
        )

        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `a day nothing answers for is never previewed`() {
        // Previewing a move away from nothing would invent the arrangement it claims to preview.
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = null,
            isPublicHoliday = false,
            proposedCustody = "dad"
        )

        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `a neighbouring month's day is never previewed matching its dimmed number`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = false,
            custody = "mom",
            isPublicHoliday = false,
            proposedCustody = "dad"
        )

        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `the week view previews the same day the month grid does`() {
        // The week is the one view a parent renegotiating a specific week is looking at, and a
        // pending proposal has no other form in this layout — unlike a handover, which the week
        // already shows as the boundary between two runs of its custody band.
        val fill = DayCellFills.weekHourCell(
            isWeekend = false,
            isToday = false,
            custody = "mom",
            proposedCustody = "dad"
        )

        assertEquals(DayCellOverlay.CUSTODY_MOM, fill.overlay)
        assertEquals(DayCellOverlay.CUSTODY_DAD, fill.pendingProposalFor)
    }

    @Test
    fun `an unknown slot in a proposal is not treated as a parent`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "mom",
            isPublicHoliday = false,
            proposedCustody = "guardian"
        )

        assertNull(fill.pendingProposalFor)
    }

    @Test
    fun `a school-vacation day is a line over the custody band not a fill`() {
        // MON-13: the vacation must not take the cell's colour away from whose day it is — the
        // teal strip it replaces was removed for washing every cell of August.
        assertEquals(
            DayCellFill(
                base = DayCellBase.WEEKEND,
                overlay = DayCellOverlay.CUSTODY_DAD,
                schoolVacation = true
            ),
            DayCellFills.monthCell(
                isWeekend = true,
                isCurrentMonth = true,
                custody = "dad",
                isPublicHoliday = false,
                isSchoolVacation = true
            )
        )
    }

    @Test
    fun `a public holiday inside a school vacation keeps both its tint and the line`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = null,
            isPublicHoliday = true,
            isSchoolVacation = true
        )

        assertEquals(DayCellOverlay.PUBLIC_HOLIDAY, fill.overlay)
        assertTrue(fill.schoolVacation)
    }

    @Test
    fun `a borrowed day keeps the school-vacation line like the custody band`() {
        // A vacation is a run of days, and nothing on it can be acted on, so it crosses the month
        // boundary the way the band does; the caller draws it recessively.
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = false,
            custody = "mom",
            isPublicHoliday = false,
            isSchoolVacation = true
        )

        assertTrue(fill.schoolVacation)
        assertTrue(fill.isAdjacentMonth)
    }

    @Test
    fun `an ordinary day has no school-vacation line`() {
        val fill = DayCellFills.monthCell(
            isWeekend = false,
            isCurrentMonth = true,
            custody = "mom",
            isPublicHoliday = false
        )

        assertFalse(fill.schoolVacation)
    }
}
