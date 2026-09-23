package com.coparently.app.domain.holidays

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A school vacation offered as the dates of a seasonal layer (MON-14, "Fill from school
 * holidays").
 *
 * @property dates The break, first and last day inclusive.
 * @property nameEn English name, the fallback in every locale.
 * @property nameLocal The name in [localLanguage].
 * @property localLanguage ISO 639-1 code of [nameLocal].
 */
data class VacationSuggestion(
    val dates: ClosedRange<LocalDate>,
    val nameEn: String,
    val nameLocal: String,
    val localLanguage: String
)

/**
 * The school vacations worth offering as a layer's dates: the upcoming breaks from the parent's
 * own holiday calendar, soonest first.
 *
 * **A suggestion, never a schedule.** The parent picks one, the dates fill the layer's range, and
 * the layer then goes through the proposal flow like every other change — nothing here is applied
 * to anybody's calendar. Only a calendar that publishes school vacations offers any
 * ([HolidayProvider.hasSchoolVacations]); for Czechia that is the nationwide breaks, which is why
 * the district-dependent spring break never appears (`CzechHolidays` leaves it out on purpose).
 */
object SchoolVacationSuggestions {

    /** How many breaks are offered at once: a school year's worth is plenty to choose from. */
    const val DEFAULT_LIMIT = 6

    /**
     * Breaks shorter than this, weekends and public holidays included, are school-free days
     * rather than something a layer is for — a German Brückentag is a long weekend.
     */
    private const val MIN_DAYS = 4L

    /** How far a break is widened over adjacent weekends and public holidays, per side. */
    private const val MAX_WIDEN_DAYS = 4

    /**
     * The breaks that have not ended by [today], from [provider]'s tables for this year and the
     * next, soonest first. A break split across New Year (the Czech Christmas vacation is two
     * ranges, 23–31 December and 1–2 January) is joined into one.
     *
     * Each break is widened over the weekends and public holidays that touch it, because those
     * are days off school too and a parent plans them as one stretch: the Czech Easter vacation is
     * a single Thursday in the table, and Thursday to Monday on the calendar.
     */
    fun upcoming(
        provider: HolidayProvider?,
        today: LocalDate,
        limit: Int = DEFAULT_LIMIT
    ): List<VacationSuggestion> {
        if (provider == null || !provider.hasSchoolVacations) return emptyList()
        val ranges = (today.year..today.year + 1)
            .flatMap { provider.schoolVacations(it) }
            .distinctBy { it.first.start to it.first.endInclusive }
            .sortedBy { it.first.start }
        val joined = mutableListOf<VacationSuggestion>()
        ranges.forEach { (range, names) ->
            val last = joined.lastOrNull()
            if (last != null && last.nameEn == names.first &&
                !range.start.isAfter(last.dates.endInclusive.plusDays(1))
            ) {
                val end = maxOf(last.dates.endInclusive, range.endInclusive)
                joined[joined.lastIndex] = last.copy(dates = last.dates.start..end)
            } else {
                joined += VacationSuggestion(range, names.first, names.second, provider.localLanguage)
            }
        }
        return joined
            .map { it.copy(dates = widen(provider, it.dates)) }
            .filter { !it.dates.endInclusive.isBefore(today) }
            .filter { it.dates.start.plusDays(MIN_DAYS - 1) <= it.dates.endInclusive }
            .take(limit)
    }

    private fun widen(provider: HolidayProvider, dates: ClosedRange<LocalDate>): ClosedRange<LocalDate> {
        var start = dates.start
        var end = dates.endInclusive
        repeat(MAX_WIDEN_DAYS) { if (isDayOff(provider, start.minusDays(1))) start = start.minusDays(1) }
        repeat(MAX_WIDEN_DAYS) { if (isDayOff(provider, end.plusDays(1))) end = end.plusDays(1) }
        return start..end
    }

    private fun isDayOff(provider: HolidayProvider, date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY ||
            provider.publicHolidays(date.year).any { it.date == date }
}
