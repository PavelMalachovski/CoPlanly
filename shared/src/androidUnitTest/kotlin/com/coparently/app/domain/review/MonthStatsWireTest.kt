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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The month in review as the AI summary's callable receives it ([MonthStatsWire]): the exact key
 * set the server validates, names in place of slots, and nothing sent for a month without a
 * schedule.
 *
 * Split from `MonthReviewCalculatorTest` when the calculator moved to `:shared` (MON-24), with the
 * same month the calculator's tests use. The month is `IsoText.yearMonth`, `java.time.YearMonth`'s
 * own text, which `YearMonthTextParityTest` holds for every year.
 */
class MonthStatsWireTest {

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
    fun `the wire form is exactly the shape the server validates`() {
        val result = review(
            overrides = mapOf(
                "2026-09-08" to swap("dad", DayOverrideStatus.ACCEPTED),
                "2026-09-15" to swap("mom", DayOverrideStatus.PENDING)
            ),
            events = listOf(event("a", LocalDateTime(2026, 9, 3, 10, 0))),
            expenses = listOf(
                expense(paidBy = ME, amount = 10.0, currency = "CZK"),
                expense(paidBy = ME, amount = 3.0, currency = "EUR"),
                expense(paidBy = CO_PARENT, amount = 3.0, currency = "EUR")
            )
        )

        val wire = MonthStatsWire.of(result, { names.getValue(it) }, { names.getValue(it) })

        // The server refuses an unknown key, so the set is pinned, not merely included.
        assertEquals(
            setOf(
                "month", "daysWithParent", "handovers", "swapsProposed", "swapsAccepted", "eventsCount",
                "expensesByCurrency", "balanceByCurrency", "holidayFairness"
            ),
            wire.keys
        )
        assertEquals(
            mapOf(
                "month" to "2026-09",
                "daysWithParent" to listOf(
                    mapOf("name" to "Bob", "days" to 17),
                    mapOf("name" to "Alice", "days" to 13)
                ),
                "handovers" to 6,
                "swapsProposed" to 2,
                "swapsAccepted" to 1,
                "eventsCount" to 1,
                "expensesByCurrency" to listOf(
                    mapOf("currency" to "CZK", "total" to 10.0),
                    mapOf("currency" to "EUR", "total" to 6.0)
                ),
                "balanceByCurrency" to listOf(
                    mapOf("currency" to "CZK", "amount" to 5.0, "owedBy" to "Bob", "owedTo" to "Alice"),
                    // Even: no transfer, so no names.
                    mapOf("currency" to "EUR", "amount" to 0.0, "owedBy" to "", "owedTo" to "")
                ),
                // 28 September, Czech Statehood Day, is a slot-2 day.
                "holidayFairness" to listOf(mapOf("name" to "Bob", "days" to 1))
            ),
            wire
        )
        // No slot identifier reaches the model.
        assertTrue(!wire.toString().contains("mom") && !wire.toString().contains("dad"))
    }

    @Test
    fun `holiday fairness is left out when the month had none, and names are cleaned`() {
        // September without its one holiday, as a month with none would be.
        val result = review().copy(specialDaysBySlot = emptyMap(), specialDays = 0)
        val long = "<b>" + "x".repeat(80) + "</b>"

        val wire = MonthStatsWire.of(result, { long }, { long })

        assertTrue("holidayFairness" !in wire.keys)
        val first = (wire["daysWithParent"] as List<*>).first() as Map<*, *>
        val name = first["name"] as String
        assertEquals(MonthStatsWire.NAME_MAX, name.length)
        assertTrue('<' !in name && '>' !in name)
    }

    @Test
    fun `a month without a schedule cannot be summarised`() {
        assertTrue(MonthStatsWire.canSend(review()))
        assertTrue(!MonthStatsWire.canSend(review(hasSchedule = false)))
    }

    private val names = mapOf("mom" to "Alice", "dad" to "Bob", ME to "Alice", CO_PARENT to "Bob")

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
    }
}
