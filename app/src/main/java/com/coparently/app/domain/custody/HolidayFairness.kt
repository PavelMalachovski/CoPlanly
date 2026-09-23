package com.coparently.app.domain.custody

import com.coparently.app.domain.holidays.Holiday
import com.coparently.app.domain.holidays.HolidayProvider
import com.coparently.app.domain.holidays.gregorianEasterSunday
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year

/**
 * What one row of the fairness summary is about (MON-20). Data, not text: the screen words the
 * fixed occasions from its own resources, and shows a holiday's or a vacation's name by the rule
 * `Holiday.nameLocal` documents and a child's name as the parents wrote it.
 */
sealed interface FairnessOccasion {
    /** 24 December. */
    data object ChristmasEve : FairnessOccasion

    /** 25 December. */
    data object ChristmasDay : FairnessOccasion

    /** 1 January. */
    data object NewYearsDay : FairnessOccasion

    /** Easter Sunday and Easter Monday (Western computus, as every table in the app uses). */
    data object Easter : FairnessOccasion

    /** Any other public holiday in the parent's holiday calendar. */
    data class PublicHoliday(val holiday: Holiday) : FairnessOccasion

    /** A child's birthday; 29 February falls on 28 February in a common year. */
    data class Birthday(val childName: String) : FairnessOccasion

    /** A school vacation from the parent's holiday calendar. */
    data class SchoolVacation(val nameEn: String, val nameLocal: String, val localLanguage: String) :
        FairnessOccasion
}

/**
 * One occasion and who has it.
 *
 * @property occasion What it is.
 * @property dates Its days, inclusive; one day for most.
 * @property daysBySlot How many of those days each slot has — `"mom"`/`"dad"`, never a name. A
 *   day nobody has (no schedule) is in no slot.
 */
data class FairnessRow(
    val occasion: FairnessOccasion,
    val dates: ClosedRange<LocalDate>,
    val daysBySlot: Map<String, Int>
) {
    /** The slot that has every day of it, or null when it is split or unknown. */
    val wholeTo: String?
        get() = daysBySlot.entries.singleOrNull()?.key
}

/**
 * The year's summary.
 *
 * @property year The calendar year summarised.
 * @property nightsBySlot Days — each day's overnight — per slot, from the whole-day schedule.
 *   **Contact windows are not nights** and do not count: an afternoon is not an overnight (item 24).
 * @property rows Holidays, birthdays and vacations, in date order.
 */
data class HolidayFairness(
    val year: Int,
    val nightsBySlot: Map<String, Int>,
    val rows: List<FairnessRow>
)

/** An occasion and its days, before anybody's days are counted. */
private typealias Dated = Pair<FairnessOccasion, ClosedRange<LocalDate>>

/**
 * Who has which holidays, and how many nights each parent has, over one year (MON-20).
 *
 * **Read-only arithmetic over the same lookup the calendar draws.** It takes `custodyFor` — bound
 * through `CustodyResolver.resolver`, so accepted swaps and seasonal layers (MON-14) count exactly
 * as the grid shows them — and never proposes, suggests or changes anything. A parent who wants to
 * rebalance goes through the ordinary proposal flow.
 */
object HolidayFairnessCalculator {

    private const val CHRISTMAS_MONTH = 12
    private const val CHRISTMAS_EVE_DAY = 24
    private const val CHRISTMAS_DAY = 25
    private const val FEBRUARY = 2
    private const val LEAP_DAY = 29
    private const val FEBRUARY_LAST_COMMON_DAY = 28

    /**
     * The summary for [year].
     *
     * @param custodyFor Whose day a date is, or null when nothing answers.
     * @param provider The parent's holiday calendar, or null when their country has none — then
     *   only the fixed occasions, Easter, birthdays and nights are summarised.
     * @param birthdays Each child's name and date of birth.
     */
    fun of(
        year: Int,
        custodyFor: (LocalDate) -> String?,
        provider: HolidayProvider?,
        birthdays: List<Pair<String, LocalDate>>
    ): HolidayFairness {
        val first = LocalDate.of(year, 1, 1)
        val nights = mutableMapOf<String, Int>()
        for (offset in 0 until Year.of(year).length()) {
            custodyFor(first.plusDays(offset.toLong()))?.let { nights[it] = (nights[it] ?: 0) + 1 }
        }
        val rows = fixedRows(year) + publicHolidayRows(year, provider) + birthdayRows(year, birthdays) +
            vacationRows(year, provider)
        return HolidayFairness(
            year = year,
            nightsBySlot = nights,
            rows = rows.map { (occasion, dates) -> FairnessRow(occasion, dates, count(dates, custodyFor)) }
                .sortedBy { it.dates.start }
        )
    }

    private fun fixedRows(year: Int): List<Dated> {
        val easter = gregorianEasterSunday(year)
        val eve = LocalDate.of(year, CHRISTMAS_MONTH, CHRISTMAS_EVE_DAY)
        val christmas = LocalDate.of(year, CHRISTMAS_MONTH, CHRISTMAS_DAY)
        val newYear = LocalDate.of(year, 1, 1)
        return listOf(
            FairnessOccasion.NewYearsDay to newYear..newYear,
            FairnessOccasion.Easter to easter..easter.plusDays(1),
            FairnessOccasion.ChristmasEve to eve..eve,
            FairnessOccasion.ChristmasDay to christmas..christmas
        )
    }

    /** The calendar's other public holidays — the fixed rows already cover their own dates. */
    private fun publicHolidayRows(year: Int, provider: HolidayProvider?): List<Dated> {
        val covered = fixedRows(year).flatMap { (_, dates) -> listOf(dates.start, dates.endInclusive) }.toSet()
        return provider?.publicHolidays(year).orEmpty()
            .filter { it.date !in covered }
            .distinctBy { it.date }
            .map { FairnessOccasion.PublicHoliday(it) to it.date..it.date }
    }

    private fun birthdayRows(year: Int, birthdays: List<Pair<String, LocalDate>>) =
        birthdays.map { (name, born) ->
            val leapDay = born.monthValue == FEBRUARY && born.dayOfMonth == LEAP_DAY
            val day = if (leapDay && !Year.isLeap(year.toLong())) {
                LocalDate.of(year, FEBRUARY, FEBRUARY_LAST_COMMON_DAY)
            } else {
                MonthDay.from(born).atYear(year)
            }
            FairnessOccasion.Birthday(name) to day..day
        }

    /** Each school vacation, clipped to the year so a Christmas break is not counted twice. */
    private fun vacationRows(year: Int, provider: HolidayProvider?): List<Dated> {
        if (provider == null || !provider.hasSchoolVacations) return emptyList()
        val first = LocalDate.of(year, 1, 1)
        val last = first.withDayOfYear(first.lengthOfYear())
        return provider.schoolVacations(year)
            .distinctBy { it.first.start to it.first.endInclusive }
            .map { (range, names) ->
                val clipped = maxOf(range.start, first)..minOf(range.endInclusive, last)
                FairnessOccasion.SchoolVacation(names.first, names.second, provider.localLanguage) to clipped
            }
    }

    private fun count(dates: ClosedRange<LocalDate>, custodyFor: (LocalDate) -> String?): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        var day = dates.start
        while (!day.isAfter(dates.endInclusive)) {
            custodyFor(day)?.let { result[it] = (result[it] ?: 0) + 1 }
            day = day.plusDays(1)
        }
        return result
    }
}
