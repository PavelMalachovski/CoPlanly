package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.ChildCustody
import com.coparently.app.domain.model.CustodyModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The first moment a request would change something (phase 3a, spec §4.2): the cap that its deadline,
 * and the last chance after it, must end before ([AnswerBy] compresses the chain to fit).
 *
 * Each function answers for one kind of request, and every answer is the epoch millis of a local
 * date-time in the zone passed in, or null when nothing ahead would change. A null cap is not a
 * refusal: the request is simply not bounded.
 */
object RequestCap {

    /**
     * How many days after today [forProposal] looks for the first change: a year and a leap day. A
     * change further out than that cannot bound a deadline, which is at most 31 days away.
     */
    const val PROPOSAL_SCAN_DAYS = 366

    /**
     * The cap of a day-swap offer: 00:00 of the earliest of [dates].
     *
     * An offer that includes today has a cap that is already behind, which [AnswerBy] reads as too
     * soon for a deadline; the offer goes out as a plain request. Null for no dates.
     */
    fun forSwap(dates: Collection<LocalDate>, zone: ZoneId): Long? =
        dates.minOrNull()?.let { startOfDay(it, zone) }

    /**
     * The cap of a change request: the earlier of the event's [currentStart] and the [proposedStart]
     * that is still ahead of [nowMillis], or null when neither is.
     *
     * Both count, because either moment is what the change touches: the time the event leaves and
     * the time it moves to. A change to a recurring series stores the series' first occurrence as
     * its start, which is long past, so only the proposed start is left.
     */
    fun forChange(currentStart: LocalDateTime, proposedStart: LocalDateTime, nowMillis: Long, zone: ZoneId): Long? =
        listOf(currentStart, proposedStart).map { millisOf(it, zone) }.filter { it > nowMillis }.minOrNull()

    /**
     * The cap of an event waiting for the other parent's acceptance: its [start] until it begins,
     * and null once it has.
     */
    fun forEvent(start: LocalDateTime, nowMillis: Long, zone: ZoneId): Long? =
        millisOf(start, zone).takeIf { it > nowMillis }

    /**
     * The cap of a custody proposal: 00:00 of the first day, from [today] on, on which [proposed]
     * differs from [agreed] in anything the calendar shows (ruling 10). That is the day's custody
     * ([CustodyModel.getCustodyFor], seasonal layers included), its contact windows, or where a
     * child is — for every child either model gives a schedule of their own (`ChildCustody`).
     *
     * A day's custody alone is not enough: a proposal that changes only tomorrow's afternoon, or one
     * child's days, moves no family day, and would otherwise be given a deadline that ends after the
     * change. Two patterns anchored a whole cycle apart describe the same days and have no cap.
     *
     * Null when there is no [agreed] schedule to change, when nothing differs within
     * [PROPOSAL_SCAN_DAYS], and when either schedule has no cycle to reduce a date into (a
     * document synced unvalidated from the other phone), which cannot be compared.
     */
    fun forProposal(agreed: CustodyModel?, proposed: CustodyModel, today: LocalDate, zone: ZoneId): Long? {
        if (agreed == null || agreed.patternDays < 1 || proposed.patternDays < 1) return null
        val children = (agreed.childOverrides + proposed.childOverrides).map { it.childId }.distinct()
        val before = children.map { whereabouts(agreed, it) }
        val after = children.map { whereabouts(proposed, it) }
        val firstChange = (0..PROPOSAL_SCAN_DAYS).asSequence()
            .map { today.plusDays(it.toLong()) }
            .firstOrNull { day ->
                agreed.getCustodyFor(day) != proposed.getCustodyFor(day) ||
                    !CustodyModel.sameContactWindows(agreed.contactWindowsOn(day), proposed.contactWindowsOn(day)) ||
                    before.indices.any { before[it](day) != after[it](day) }
            }
        return firstChange?.let { startOfDay(it, zone) }
    }

    /** Where [childId] is on a date under [model]: its own schedule when it has one, else the family's. */
    private fun whereabouts(model: CustodyModel, childId: String): (LocalDate) -> String? =
        ChildCustody.resolver(model.childOverrideFor(childId), model::getCustodyFor)

    private fun millisOf(local: LocalDateTime, zone: ZoneId): Long = local.atZone(zone).toInstant().toEpochMilli()

    private fun startOfDay(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
}
