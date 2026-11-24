package com.coparently.app.presentation.calendar

/** The fill a day cell starts from, before anything is drawn over it. */
enum class DayCellBase {
    /** The screen's own surface. */
    SURFACE,

    /** Saturday or Sunday — a neutral grey, applied in every grid row. */
    WEEKEND
}

/** What is drawn over [DayCellBase], at its own alpha, or nothing. */
enum class DayCellOverlay {
    NONE,
    CUSTODY_MOM,
    CUSTODY_DAD,
    PUBLIC_HOLIDAY,

    /** Week view only; the month grid marks today on the day number instead. */
    TODAY
}

/**
 * A day cell's fills: the [base] it starts from and the [overlay] drawn over it.
 *
 * **A handover day is one colour** (owner decision, 24 November 2026): the parent the child is with
 * that day, as on every other day. The cell used to be split on a diagonal with yesterday's parent
 * in the top-left; the parents know their handovers, the schema holds no hour for one, and Home's
 * handover card still names the next one.
 *
 * @property base Always decided by the weekday alone, so the weekend band is continuous.
 * @property overlay The cell's meaning — custody, holiday or today — or [DayCellOverlay.NONE].
 * @property pendingProposalFor The parent a **pending, unanswered** custody proposal would give
 *   this day to, when that differs from whose day it is now; null on every other day. Only ever
 *   [DayCellOverlay.CUSTODY_MOM] or [DayCellOverlay.CUSTODY_DAD]. It is drawn *over* [overlay],
 *   at a lower alpha, so the agreed pattern keeps its full strength underneath: a proposal is a
 *   preview, and a grid that showed only the proposal would tell a parent their days had changed
 *   when they had not. The two translucent hues do blend into something that is neither parent's
 *   colour, and that is the intended reading — the day is being argued about. The cell's spoken
 *   description says so in words, which is what actually disambiguates it.
 * @property isAdjacentMonth Whether this cell is one of the leading or trailing days borrowed
 *   from a neighbouring month. It is a **fact about the day, not a colour**: the caller draws the
 *   same fills at a reduced strength, the way the day number and the event dots are already
 *   dimmed. Encoded here rather than left to the view so that every consequence of a day being
 *   borrowed is decided in this file, which is the whole point of it.
 * @property schoolVacation Whether the day falls in a school vacation (MON-13). It is a **line,
 *   not a fill**: a thin neutral hairline along the cell's bottom edge, in the theme's `outline`
 *   role, drawn over every other layer and taking no height. Not a colour channel — pink and blue
 *   are the parents, teal is a calendar friend, grey is the weekend, red is a public holiday — and
 *   not a tint, because a summer break covers every cell of July and August and the teal strip that
 *   used to do this was removed for washing a whole month. It crosses into the borrowed days like
 *   the custody band does: a vacation is a run of days, and a run that stops mid-row is the defect
 *   that band was fixed for. Nothing on it can be acted on, so it has none of the reasons the
 *   holiday tint has to stop at the month's edge.
 */
data class DayCellFill(
    val base: DayCellBase,
    val overlay: DayCellOverlay,
    val pendingProposalFor: DayCellOverlay? = null,
    val isAdjacentMonth: Boolean = false,
    val schoolVacation: Boolean = false
)

/**
 * Decides what a calendar day cell is filled with, kept out of Compose so the branching can be
 * unit tested without a composition.
 *
 * **The weekend is a base, not a competitor.** The month cell used to pick exactly one
 * background, with custody ahead of the weekend; because
 * [com.coparently.app.domain.model.CustodyModel.getCustodyFor] reduces any date into the pattern
 * cycle and always answers `"mom"` or `"dad"`, every in-month cell matched a custody branch and
 * the weekend branch could not be reached at all. The only weekends that kept a tint were the
 * ones belonging to a neighbouring month, which is why the band appeared in some grid rows and
 * not others with no rule a reader could infer.
 *
 * Weekend deliberately does **not** win over custody: weekends are the days a separated parent
 * checks first, and replacing the parent hue there with grey would remove the answer from exactly
 * the cells the screen exists to give it in.
 *
 * **Every case a cell can be in is decided here, in one place**: weekend base, custody overlay,
 * public holiday, today, the pending-proposal preview and the
 * school-vacation line. Two functions
 * that each knew some of the cases is precisely how the weekend band became unreachable, so a new
 * case folds in rather than growing a second decision beside this one. (Pending *swap* arrows are
 * the deliberate exception: they are a glyph, not a fill, and `MonthView` draws them from a set of
 * dates — a swap changes nothing about whose day it is, so it has no business in a colour.)
 */
object DayCellFills {

    /**
     * The fill for a cell in the month grid.
     *
     * @param isWeekend Saturday or Sunday.
     * @param isCurrentMonth False for the leading and trailing days borrowed from the
     *   neighbouring months. **They still carry the custody band**, at the reduced strength
     *   [DayCellFill.isAdjacentMonth] asks the caller for. They used to carry nothing at all, and
     *   a band that stops in the middle of a grid row is the same defect as a weekend band that
     *   appears in some rows and not others: custody is a *pattern*, and a reader cannot infer a
     *   pattern from a row that goes blank halfway across. What they still do not take is
     *   anything they cannot be acted on for — the holiday tint, the proposal preview, the swap
     *   arrows — because those mark a day you would do something about, and a borrowed cell
     *   refuses every gesture.
     * @param custody `"mom"`, `"dad"`, or null when no custody model or legacy schedule applies.
     *   Any other value is treated as no custody rather than guessed at.
     * @param isPublicHoliday A public holiday, not a school vacation — school vacation is never a
     *   cell fill.
     * @param proposedCustody Whose day this would be under a **pending** custody proposal, or
     *   null when nothing is pending. Resolved from the proposal's own pattern, never from the
     *   agreed one. See [pendingProposal].
     * @param isSchoolVacation Whether the day falls in a school vacation — on a public holiday
     *   inside one too, which is why it is its own input and not read off the day's `Holiday`.
     *   Passed through unchanged on a borrowed day; see [DayCellFill.schoolVacation].
     */
    @Suppress("LongParameterList") // one cell's inputs, expressed as one parameter list
    fun monthCell(
        isWeekend: Boolean,
        isCurrentMonth: Boolean,
        custody: String?,
        isPublicHoliday: Boolean,
        proposedCustody: String? = null,
        isSchoolVacation: Boolean = false
    ): DayCellFill = DayCellFill(
        base = baseFor(isWeekend),
        // The holiday tint is the one overlay the borrowed days give up: it marks a single day,
        // and a single day missing from a borrowed cell breaks no pattern. The custody band is a
        // pattern, so it runs to the edge of the grid.
        overlay = custodyOverlay(custody)
            ?: if (isPublicHoliday && isCurrentMonth) {
                DayCellOverlay.PUBLIC_HOLIDAY
            } else {
                DayCellOverlay.NONE
            },
        pendingProposalFor = if (isCurrentMonth) pendingProposal(custody, proposedCustody) else null,
        isAdjacentMonth = !isCurrentMonth,
        // Independent of every layer above: a vacation neither changes whose day it is nor hides
        // the holiday tint, and it runs to the grid's edge like the band.
        schoolVacation = isSchoolVacation
    )

    /**
     * The parent a pending proposal would move this day to, or null when the day is not moving.
     *
     * Null when the proposal agrees with the agreed pattern, which is most of the grid: a
     * proposal is a whole pattern, so marking every day it covers would wash the month and say
     * nothing. Only the days that would actually change are drawn as a preview.
     *
     * Null when either side is unknown: an unanswered day is not a change, it is an unknown, and
     * previewing a move away from nothing would invent the arrangement it claims to be previewing.
     */
    private fun pendingProposal(custody: String?, proposedCustody: String?): DayCellOverlay? {
        if (custody == null || proposedCustody == null || custody == proposedCustody) return null
        return custodyOverlay(proposedCustody)
    }

    /**
     * The fill for one hour cell in the week or day view.
     *
     * Custody beats today for the same reason it does in the month grid: today already reads as
     * today from its coloured column header, and the old order hid custody on the one column
     * parents check first.
     *
     * @param isWeekend Saturday or Sunday.
     * @param isToday Whether this column is today.
     * @param custody `"mom"`, `"dad"`, or null.
     * @param proposedCustody Whose day this would be under a pending proposal, or null when
     *   nothing is pending. The week view gets the same preview as the month grid: unlike the
     *   swap arrows, which package C kept to the month, a pending proposal has no other form in
     *   this layout. Leaving it out would make the week
     *   the one view that shows a schedule as settled while the month says it is not.
     */
    fun weekHourCell(
        isWeekend: Boolean,
        isToday: Boolean,
        custody: String?,
        proposedCustody: String? = null
    ): DayCellFill =
        DayCellFill(
            base = baseFor(isWeekend),
            overlay = custodyOverlay(custody)
                ?: if (isToday) DayCellOverlay.TODAY else DayCellOverlay.NONE,
            pendingProposalFor = pendingProposal(custody, proposedCustody)
        )

    private fun baseFor(isWeekend: Boolean) =
        if (isWeekend) DayCellBase.WEEKEND else DayCellBase.SURFACE

    private fun custodyOverlay(custody: String?): DayCellOverlay? = when (custody) {
        "mom" -> DayCellOverlay.CUSTODY_MOM
        "dad" -> DayCellOverlay.CUSTODY_DAD
        else -> null
    }
}
