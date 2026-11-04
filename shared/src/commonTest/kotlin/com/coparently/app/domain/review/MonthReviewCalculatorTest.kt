package com.coparently.app.domain.review

import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.HolidayFairnessCalculator
import com.coparently.app.domain.holidays.CzechHolidays
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The month in review: custody counted through the calendar's own lookup (swaps included),
 * special days counted once, private events left out, and money per currency with the transfer
 * that evens it out — never a single cross-currency total.
 */
class MonthReviewCalculatorTest {

    // Week on, week off from Monday 2026-09-07; slot 1 ("mom") has the first week. September 2026:
    // 1–6 slot 2, 7–13 slot 1, 14–20 slot 2, 21–27 slot 1, 28–30 slot 2 → 14 and 16 days.
    private val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate(2026, 9, 7))
    private val september = YearMonth(2026, 9)
    private val roles = mapOf(ME to "mom", CO_PARENT to "dad")

    // One default per fact of the month, so each test names only the one it is about.
    @Suppress("LongParameterList")
    private fun review(
        overrides: Map<String, DayOverride> = emptyMap(),
        hasSchedule: Boolean = true,
        events: List<Event> = emptyList(),
        expenses: List<Expense> = emptyList(),
        birthdays: List<Pair<String, LocalDate>> = emptyList(),
        roleByUid: Map<String, String> = roles
    ): MonthReview {
        val custodyFor = CustodyResolver.resolver(model, overrides) { null }
        return MonthReviewCalculator.of(
            month = september,
            custodyFor = custodyFor,
            hasSchedule = hasSchedule,
            overrides = overrides,
            fairness = HolidayFairnessCalculator.of(2026, custodyFor, CzechHolidays, birthdays),
            events = events,
            expenses = expenses,
            currentUserId = ME,
            roleByUid = roleByUid
        )
    }

    @Test
    fun `days and handovers follow the pattern`() {
        val result = review()

        assertEquals(mapOf("mom" to 14, "dad" to 16), result.daysBySlot)
        assertEquals(0, result.daysWithoutSchedule)
        // Mornings of 7, 14, 21 and 28 September.
        assertEquals(4, result.handovers)
    }

    @Test
    fun `an accepted swap moves a day and adds its handovers - a pending one only counts as offered`() {
        val result = review(
            overrides = mapOf(
                "2026-09-08" to swap("dad", DayOverrideStatus.ACCEPTED),
                "2026-09-15" to swap("mom", DayOverrideStatus.PENDING),
                "2026-10-02" to swap("mom", DayOverrideStatus.ACCEPTED)
            )
        )

        assertEquals(mapOf("mom" to 13, "dad" to 17), result.daysBySlot)
        // 7, 8 and 9 September now each change hands, plus 14, 21 and 28.
        assertEquals(6, result.handovers)
        assertEquals(2, result.swapDaysOffered)
        assertEquals(1, result.swapDaysAgreed)
    }

    @Test
    fun `without a schedule no day is anybody's`() {
        val result = review(hasSchedule = false)

        assertTrue(result.daysBySlot.isEmpty())
        assertEquals(30, result.daysWithoutSchedule)
        assertEquals(0, result.handovers)
        assertTrue(result.specialDaysBySlot.isEmpty())
    }

    @Test
    fun `special days are the month's holidays and birthdays - each date once`() {
        // 28 September is Czech Statehood Day, a slot-2 day; the birthday on the 10th is slot 1's,
        // and a second birthday on the holiday itself adds no date.
        val result = review(
            birthdays = listOf(
                "Emma" to LocalDate(2018, 9, 10),
                "Leo" to LocalDate(2020, 9, 28)
            )
        )

        assertEquals(2, result.specialDays)
        assertEquals(mapOf("mom" to 1, "dad" to 1), result.specialDaysBySlot)
    }

    @Test
    fun `shared events count occurrences in the month and leave private ones out`() {
        val events = listOf(
            event("a", LocalDateTime(2026, 9, 3, 10, 0)),
            // Two occurrences of one recurring event share its id.
            event("weekly", LocalDateTime(2026, 9, 4, 17, 0)),
            event("weekly", LocalDateTime(2026, 9, 11, 17, 0)),
            // Started in August, still running on 1 September.
            event("trip", LocalDateTime(2026, 8, 30, 9, 0), end = LocalDateTime(2026, 9, 1, 18, 0)),
            event("mine", LocalDateTime(2026, 9, 5, 9, 0), hidden = true),
            event("october", LocalDateTime(2026, 10, 1, 9, 0))
        )

        assertEquals(4, review(events = events).sharedEvents)
    }

    @Test
    fun `money is summarised per currency with the transfer that evens it out`() {
        val result = review(
            expenses = listOf(
                expense(paidBy = ME, amount = 300.0, currency = "CZK"),
                expense(paidBy = CO_PARENT, amount = 100.0, currency = "CZK"),
                expense(paidBy = CO_PARENT, amount = 40.0, currency = "EUR"),
                expense(paidBy = ME, amount = 999.0, currency = "CZK", date = LocalDate(2026, 8, 31))
            )
        )

        assertEquals(listOf("CZK", "EUR"), result.money.map { it.currency })
        val czk = result.money.first { it.currency == "CZK" }
        assertEquals(400.0, czk.total, DELTA)
        assertEquals(mapOf(ME to 300.0, CO_PARENT to 100.0), czk.paidByUid)
        // Half each: I paid 300 and owe 200, so the co-parent's 100 evens it out.
        assertEquals(Settlement(fromUid = CO_PARENT, toUid = ME, amount = 100.0), czk.settlement)
        val eur = result.money.first { it.currency == "EUR" }
        assertEquals(Settlement(fromUid = ME, toUid = CO_PARENT, amount = 20.0), eur.settlement)
    }

    @Test
    fun `an even month and an unpaired account carry no transfer`() {
        val even = review(
            expenses = listOf(
                expense(paidBy = ME, amount = 50.0, currency = "CZK"),
                expense(paidBy = CO_PARENT, amount = 50.0, currency = "CZK")
            )
        )
        assertNull(even.money.single().settlement)

        val unpaired = review(
            expenses = listOf(expense(paidBy = ME, amount = 50.0, currency = "CZK", splitBetween = listOf(ME))),
            roleByUid = mapOf(ME to "mom")
        )
        assertNull(unpaired.money.single().settlement)
        assertEquals(mapOf(ME to 50.0), unpaired.money.single().paidByUid)
    }

    private fun swap(to: String, status: DayOverrideStatus) =
        DayOverride(toParent = to, requestedBy = CO_PARENT, requestedAt = "2026-09-01T10:00:00", status = status)

    private fun event(
        id: String,
        start: LocalDateTime,
        end: LocalDateTime? = null,
        hidden: Boolean = false
    ) = Event(
        id = id,
        title = "Event $id",
        startDateTime = start,
        endDateTime = end,
        eventType = "other",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start,
        isPrivate = hidden
    )

    private fun expense(
        paidBy: String,
        amount: Double,
        currency: String,
        date: LocalDate = LocalDate(2026, 9, 12),
        splitBetween: List<String> = listOf(ME, CO_PARENT)
    ) = Expense(
        id = "$paidBy-$amount-$currency-$date",
        title = "Expense",
        amount = amount,
        currency = currency,
        category = ExpenseCategory.EDUCATION,
        paidBy = paidBy,
        splitBetween = splitBetween,
        date = date
    )

    private companion object {
        const val ME = "uid-alice"
        const val CO_PARENT = "uid-bob"
        const val DELTA = 0.0001
    }
}
