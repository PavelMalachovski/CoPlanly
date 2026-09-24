package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * A public holiday or school vacation day shown in the calendar.
 *
 * @property date The concrete date.
 * @property nameEn English display name — the fallback every locale falls back to.
 * @property nameLocal The holiday's name in the language of the country it belongs to. This was
 *   `nameCs` while Czechia was the only country the app knew; the field kept its meaning and
 *   lost the assumption.
 * @property localLanguage The ISO 639-1 code [nameLocal] is written in (`"cs"`, `"de"`, …).
 *   A screen shows [nameLocal] when the device language matches and [nameEn] otherwise, which
 *   is the rule `MonthView` already applied — hardcoded to Czech.
 * @property isSchoolVacation True for school vacation days, false for public holidays.
 */
data class Holiday(
    val date: LocalDate,
    val nameEn: String,
    val nameLocal: String,
    val localLanguage: String,
    val isSchoolVacation: Boolean = false
)

/**
 * One country's public holidays and school vacations.
 *
 * Extracted from `CzechHolidays` in MON-13, which is where the app's single hardcoded country
 * lived: `CalendarScreen` called `CzechHolidays.holidaysInRange` directly, at the calendar's one
 * holiday call site, and the stored preference is still named "show Czech holidays". A German,
 * Russian or Ukrainian user got Czech public holidays with no way to say otherwise, in an app
 * that ships in five languages.
 *
 * A provider supplies the two lists; the lookups are derived here so no implementation can get
 * the precedence or the range walk subtly different from another's.
 *
 * **Public holidays and school vacations are separate questions, and a country may answer only
 * the first.** A public holiday is national and computable; a school calendar often is not —
 * Germany's is set per Land (so only a Land's calendar has one), Austria's semester and summer
 * breaks and Slovakia's spring holidays are set per region, and Czechia's own district-dependent
 * spring break is left out for exactly that reason. Outside Czechia the vacations are published
 * per school year rather than computed, so they are dated tables ([SchoolVacation]) that end
 * where the published data ends. Returning an empty list from [schoolVacations] is a legitimate
 * answer and must not be read as "this country has no school holidays".
 */
interface HolidayProvider {

    /** Every public holiday falling in [year]. */
    fun publicHolidays(year: Int): List<Holiday>

    /**
     * School vacation periods overlapping [year], as (range, (English, local) names) — the
     * nationwide ones, or a region's when this is a region's calendar ([forRegion]).
     *
     * Empty when the calendar's school vacations are regional or not known to the app, and for a
     * year past the end of a published table.
     */
    fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>>

    /** The ISO 639-1 language [Holiday.nameLocal] is written in for this country. */
    val localLanguage: String

    /**
     * Whether [schoolVacations] ever returns anything for this calendar.
     *
     * Stated per provider rather than probed from the list, because it is what the country picker
     * tells the user: saying "school vacations" for Germany without a Land, which has none, would
     * be design rule 8's affordance that does not exist.
     */
    val hasSchoolVacations: Boolean

    /**
     * The subdivisions whose own public holidays or school vacations this calendar can add, as
     * ISO 3166-2 suffixes
     * (`"BY"` for `DE-BY`), or empty for a calendar that is the same everywhere in the country.
     *
     * Non-empty for Germany (a Land adds public holidays and school vacations) and Slovakia (a
     * kraj adds its spring holidays). Empty is the honest answer everywhere else, **Austria
     * included**: its thirteen public holidays are nationwide, the per-Land patron-saint days the
     * reference library carries are bank holidays, not days off, and its per-Land school breaks
     * have no final dates past 2025/26 in the source (see `AustrianHolidays`). A region picker
     * that changed nothing on the grid would be design rule 8's affordance that promises a
     * feature (MON-13).
     */
    val regions: List<String> get() = emptyList()

    /**
     * This calendar with [regionCode]'s own public holidays and school vacations, or this provider itself for
     * null, for a country with no [regions], or for a code this build does not know — a newer
     * build's region read by an older one draws the nationwide days rather than nothing.
     */
    // The default is for a nationwide calendar, which has nothing to add whatever the region;
    // the parameter is the overrides' (GermanHolidays, SlovakHolidays), not this body's.
    @Suppress("UnusedParameter")
    fun forRegion(regionCode: String?): HolidayProvider = this

    /**
     * The holiday for [date] — a public holiday first, then a school vacation — or null on an
     * ordinary day.
     *
     * The precedence is not arbitrary: 24–26 December are both public holidays and inside the
     * school break, and a parent looking at the grid wants to be told it is Christmas rather
     * than that school is out.
     */
    fun holidayFor(date: LocalDate): Holiday? {
        publicHolidays(date.year).firstOrNull { it.date == date }?.let { return it }

        schoolVacations(date.year).firstOrNull { (range, _) -> date in range }?.let { (_, names) ->
            return Holiday(
                date = date,
                nameEn = names.first,
                nameLocal = names.second,
                localLanguage = localLanguage,
                isSchoolVacation = true
            )
        }
        return null
    }

    /**
     * Every holiday between [start] and [end] inclusive, keyed by date for the grid's lookup.
     */
    fun holidaysInRange(start: LocalDate, end: LocalDate): Map<LocalDate, Holiday> {
        val result = mutableMapOf<LocalDate, Holiday>()
        var date = start
        while (!date.isAfter(end)) {
            holidayFor(date)?.let { result[date] = it }
            date = date.plusDays(1)
        }
        return result
    }

    /**
     * Every day between [start] and [end] inclusive that falls inside a school vacation,
     * **whether or not it is also a public holiday**.
     *
     * A separate question from [holidaysInRange], which keys one [Holiday] per date and so answers
     * "Christmas" rather than "school is out" on 24–26 December — the right answer for a name,
     * and the wrong one for the month grid's school-vacation line, which would otherwise break on
     * exactly the days a vacation is most obviously on. Empty wherever [schoolVacations] is.
     */
    fun schoolVacationDaysInRange(start: LocalDate, end: LocalDate): Set<LocalDate> {
        val periods = (start.year..end.year).flatMap { schoolVacations(it) }.map { it.first }
        val result = mutableSetOf<LocalDate>()
        var date = start
        while (!date.isAfter(end)) {
            val day = date
            if (periods.any { day in it }) result += day
            date = date.plusDays(1)
        }
        return result
    }
}

/**
 * Easter Sunday, shared by every Western-Christian country's provider.
 *
 * The anonymous Gregorian computus, lifted out of `CzechHolidays` unchanged. It is here rather
 * than copied per provider because Good Friday, Easter Monday, Ascension, Whit Monday and Corpus
 * Christi are all offsets from it, and a country whose Easter drifted from its neighbour's by a
 * day would be a bug nobody would look for.
 *
 * Orthodox Easter is a *different* computation (Julian reckoning) and does not belong in this
 * function. A provider that needs it should say so in its own file rather than adding a flag
 * here.
 *
 * `MagicNumber` is suppressed because the literals *are* the published algorithm (Meeus,
 * *Astronomical Algorithms*, ch. 8): naming them would only make it harder to check against the
 * source. `HolidayReferenceTest` pins every Easter from 2020 to 2035.
 */
@Suppress("MagicNumber")
fun gregorianEasterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}
