package com.coparently.app.domain.review

import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.HolidayFairness
import com.coparently.app.domain.expenses.calculateExpenseBalance
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * What evening out one currency's expenses would take: [amount] from one parent to the other.
 * Stated as a transfer, never as a debt or a fault — it is arithmetic over what both recorded.
 *
 * @property fromUid Who would pay it
 * @property toUid Who would receive it
 * @property amount How much, always positive
 */
data class Settlement(
    val fromUid: String,
    val toUid: String,
    val amount: Double
)

/**
 * One currency's expenses over the month. No conversion between currencies (spec §10): each is
 * summarised on its own, as the Expenses screen does.
 *
 * @property currency ISO 4217 code
 * @property total Everything recorded in it
 * @property paidByUid What each parent paid, by uid; a payer who is neither parent is left out
 * @property settlement What evens the month out, or null when it is already even or the two
 *   parents cannot be told apart (unpaired, or both still in one slot)
 */
data class MonthMoney(
    val currency: String,
    val total: Double,
    val paidByUid: Map<String, Double>,
    val settlement: Settlement?
)

/**
 * A month in figures — whose days they were, how often the children changed hands, the swaps, the
 * shared events and the money — computed from what this phone already holds.
 *
 * **Counts, never a judgement.** Nothing here scores a parent, compares a month with an agreement
 * or names anyone as late, short or owing in a way the records do not: a day is counted for whoever
 * the calendar shows, and the money is what each paid and what would even it out.
 *
 * @property month The month summarised
 * @property hasSchedule Whether a custody schedule is agreed; without one the day counts are empty
 * @property daysBySlot Days with each slot (`"mom"`/`"dad"`), the calendar's own lookup
 * @property daysWithoutSchedule Days nothing answers for
 * @property handovers Mornings the children change hands, as the grid draws them
 * @property specialDaysBySlot Public holidays, school-vacation days and children's birthdays in the
 *   month, by whose day each was; a date that is two of these counts once
 * @property specialDays How many such dates the month has, with anybody or nobody
 * @property swapDaysOffered Days in the month either parent offered to swap, whatever the answer
 * @property swapDaysAgreed Of those, the days that were agreed and moved
 * @property sharedEvents Events in the month both parents can see — occurrences of a recurring one
 *   counted each; private events are left out, as they are of everything shared
 * @property money One entry per currency, largest total first; empty when nothing was spent
 */
data class MonthReview(
    val month: YearMonth,
    val hasSchedule: Boolean,
    val daysBySlot: Map<String, Int>,
    val daysWithoutSchedule: Int,
    val handovers: Int,
    val specialDaysBySlot: Map<String, Int>,
    val specialDays: Int,
    val swapDaysOffered: Int,
    val swapDaysAgreed: Int,
    val sharedEvents: Int,
    val money: List<MonthMoney>
)

/**
 * Builds a [MonthReview]. Pure: every input is something a screen already reads, and custody goes
 * through the same bound [CustodyResolver] lookup the calendar and Home use, so accepted swaps and
 * seasonal layers count exactly as they are drawn.
 */
object MonthReviewCalculator {

    /** Below half a cent a currency is even; floating-point sums leave dust. */
    private const val EVEN_EPSILON = 0.005

    /**
     * The review of [month].
     *
     * @param custodyFor Whose day a date is — `CustodyResolver.resolver(model, overrides) { null }`
     * @param hasSchedule Whether an agreed custody model exists
     * @param overrides The pair's one-off swaps, keyed by ISO date
     * @param fairness The holiday summary of [month]'s year, or null without a schedule
     * @param events Events overlapping the month, recurring ones expanded to occurrences
     * @param expenses Expenses; only those dated in the month are counted
     * @param currentUserId The signed-in parent's uid
     * @param roleByUid uid to slot for whichever parents are known
     */
    // Every input is one of the month's facts; bundling them would only move the list elsewhere.
    @Suppress("LongParameterList")
    fun of(
        month: YearMonth,
        custodyFor: (LocalDate) -> String?,
        hasSchedule: Boolean,
        overrides: Map<String, DayOverride>,
        fairness: HolidayFairness?,
        events: List<Event>,
        expenses: List<Expense>,
        currentUserId: String,
        roleByUid: Map<String, String>
    ): MonthReview {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val lookup: (LocalDate) -> String? = if (hasSchedule) custodyFor else { _ -> null }
        val bySlot = days.mapNotNull(lookup).groupingBy { it }.eachCount()
        val specialDates = specialDates(month, fairness)
        val swapDays = overrides.mapNotNull { (iso, override) -> parseDate(iso)?.let { it to override } }
            .filter { (date, _) -> YearMonth.from(date) == month }
        return MonthReview(
            month = month,
            hasSchedule = hasSchedule,
            daysBySlot = bySlot,
            daysWithoutSchedule = days.size - bySlot.values.sum(),
            handovers = days.count { CustodyResolver.isHandoverDay(lookup, it) },
            specialDaysBySlot = specialDates.mapNotNull(lookup).groupingBy { it }.eachCount(),
            specialDays = specialDates.size,
            swapDaysOffered = swapDays.size,
            swapDaysAgreed = swapDays.count { (_, override) -> override.isAccepted },
            sharedEvents = sharedEvents(month, events),
            money = money(month, expenses, currentUserId, roleByUid)
        )
    }

    /** Holidays, vacation days and birthdays inside [month], each date once. */
    private fun specialDates(month: YearMonth, fairness: HolidayFairness?): List<LocalDate> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        return fairness?.rows.orEmpty()
            .flatMap { row ->
                val from = maxOf(row.dates.start, first)
                val to = minOf(row.dates.endInclusive, last)
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
            }
            .distinct()
            .sorted()
    }

    private fun sharedEvents(month: YearMonth, events: List<Event>): Int {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        return events.asSequence()
            .filterNot { it.isPrivate }
            .filter { event ->
                val start = event.startDateTime.toLocalDate()
                val end = event.endDateTime?.toLocalDate() ?: start
                !start.isAfter(last) && !end.isBefore(first)
            }
            // Occurrences of one recurring event share its id, so the start tells them apart.
            .distinctBy { it.id to it.startDateTime }
            .count()
    }

    private fun money(
        month: YearMonth,
        expenses: List<Expense>,
        currentUserId: String,
        roleByUid: Map<String, String>
    ): List<MonthMoney> {
        val coParentUid = roleByUid.keys.firstOrNull { it != currentUserId }
        return expenses.filter { YearMonth.from(it.date) == month }
            .groupBy { it.currency }
            .map { (currency, inCurrency) ->
                val balance = calculateExpenseBalance(inCurrency, currentUserId, roleByUid)
                val net = balance.netForCurrentUser
                val settlement = when {
                    !balance.splitKnown || coParentUid == null || abs(net) < EVEN_EPSILON -> null
                    net > 0 -> Settlement(fromUid = coParentUid, toUid = currentUserId, amount = net)
                    else -> Settlement(fromUid = currentUserId, toUid = coParentUid, amount = -net)
                }
                MonthMoney(
                    currency = currency,
                    total = inCurrency.sumOf { it.amount },
                    paidByUid = inCurrency.filter { it.paidBy in roleByUid }
                        .groupBy { it.paidBy }
                        .mapValues { (_, paid) -> paid.sumOf { it.amount } },
                    settlement = settlement
                )
            }
            .sortedByDescending { it.total }
    }

    private fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
}
