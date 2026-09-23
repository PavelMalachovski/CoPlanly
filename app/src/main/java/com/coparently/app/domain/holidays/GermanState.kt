package com.coparently.app.domain.holidays

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters

/**
 * The sixteen German Länder and the public holidays each adds to the nine nationwide ones
 * (MON-13, regional half).
 *
 * Public holidays in Germany are state law, which is why [GermanHolidays] on its own draws only
 * the nine every Land observes. A parent who names their state gets that state's own days on top
 * — Epiphany in Bavaria, Reformation Day in Saxony, Women's Day in Berlin — through
 * [GermanHolidays.forRegion].
 *
 * ## What is deliberately left out
 *
 * The reference library files three kinds of day under Germany that are **not** in these tables,
 * and each omission is a decision rather than a gap:
 * - **Assumption Day in Bavaria** and **Corpus Christi in parts of Saxony and Thuringia** are
 *   holidays only in municipalities with a Catholic majority (the library's `catholic`
 *   category). A state setting cannot say which municipality a family lives in, so drawing them
 *   would put a day off on the grid of every Protestant town in those states — the "complete list
 *   that is wrong for half the state" this project refuses nationally too.
 * - **Augsburg's Peace Festival (8 August)** is a *city* holiday. The library lists Augsburg as a
 *   pseudo-subdivision; it is not a Land and is not offered.
 * - **School holidays**, which the library also carries per state, are not taken from it: each
 *   Land's school vacations are a separate table, [GERMAN_SCHOOL_VACATIONS], held to the
 *   OpenHolidays dataset rather than to this library.
 *
 * Everything else the library's `public` category returns for a state is here, including the
 * two Sundays Brandenburg names by law (Easter and Whit Sunday — days off already, but the law
 * names them and the calendar says so) and Berlin's one-off anniversaries.
 *
 * `HolidayReferenceTest` holds every state's table to the library (v0.105), every date and both
 * names, 2020–2035, through `holidayRegionReference`. The `since` years below come from the
 * library's own rules and matter only before 2020, which the fixture does not reach.
 *
 * @property code The ISO 3166-2 subdivision suffix (`"BY"` for `DE-BY`). Stored on the profile
 *   as `users.regionCode`, so an entry is never re-lettered.
 */
internal enum class GermanState(val code: String, val rules: List<HolidayRule>) {
    BB("BB", listOf(easterSunday(), whitSunday(), reformationDay())),
    BE("BE", listOf(womensDay(since = BERLIN_WOMENS_DAY_SINCE)) + berlinAnniversaries()),
    BW("BW", listOf(epiphany(), corpusChristi(), allSaints())),
    BY("BY", listOf(epiphany(), corpusChristi(), allSaints())),
    HB("HB", listOf(reformationDay(since = NORTHERN_REFORMATION_DAY_SINCE))),
    HE("HE", listOf(corpusChristi())),
    HH("HH", listOf(reformationDay(since = NORTHERN_REFORMATION_DAY_SINCE))),
    MV("MV", listOf(womensDay(since = MV_WOMENS_DAY_SINCE), reformationDay())),
    NI("NI", listOf(reformationDay(since = NORTHERN_REFORMATION_DAY_SINCE))),
    NW("NW", listOf(corpusChristi(), allSaints())),
    RP("RP", listOf(corpusChristi(), allSaints())),
    SH("SH", listOf(reformationDay(since = NORTHERN_REFORMATION_DAY_SINCE))),
    SL("SL", listOf(corpusChristi(), assumptionDay(), allSaints())),
    SN("SN", listOf(reformationDay(), repentanceDay())),
    ST("ST", listOf(epiphany(), reformationDay())),
    TH("TH", listOf(worldChildrensDay(), reformationDay()));

    companion object {
        /** The state a stored code names, or null for none or a code this build does not know. */
        fun fromCode(code: String?): GermanState? {
            val normalized = code?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

/** Reformation Day became a holiday in Bremen, Hamburg, Lower Saxony and Schleswig-Holstein in 2018. */
private const val NORTHERN_REFORMATION_DAY_SINCE = 2018

/** Berlin has observed International Women's Day since 2019. */
private const val BERLIN_WOMENS_DAY_SINCE = 2019

/** Mecklenburg-Western Pomerania has observed International Women's Day since 2023. */
private const val MV_WOMENS_DAY_SINCE = 2023

/** Thuringia has observed World Children's Day since 2019. */
private const val WORLD_CHILDRENS_DAY_SINCE = 2019

/** Saxony kept the Day of Repentance and Prayer when the other Länder gave it up in 1995. */
private const val REPENTANCE_DAY_SINCE = 1995

/** Easter Sunday itself, as an offset from [gregorianEasterSunday]. */
private const val EASTER_SUNDAY = 0L

/** Whit Sunday (Pentecost): the fiftieth day of Easter. */
private const val WHIT_SUNDAY = 49L

/** Berlin's 75th anniversary of the end of the Second World War in Europe. */
private const val BERLIN_LIBERATION_75_YEAR = 2020

/** Berlin's 80th anniversary of the end of the Second World War in Europe. */
private const val BERLIN_LIBERATION_80_YEAR = 2025

/** Berlin's 75th anniversary of the East German uprising of 17 June 1953. */
private const val BERLIN_UPRISING_75_YEAR = 2028

/** The Day of Repentance and Prayer is the last Wednesday on or before this date. */
private val REPENTANCE_DAY_LATEST: MonthDay = MonthDay.parse("--11-22")

private fun epiphany() = HolidayRule("Epiphany", "Heilige Drei Könige", fixed("--01-06"))

private fun corpusChristi() = HolidayRule("Corpus Christi", "Fronleichnam", easter(CORPUS_CHRISTI))

private fun allSaints() = HolidayRule("All Saints' Day", "Allerheiligen", fixed("--11-01"))

private fun assumptionDay() = HolidayRule("Assumption Day", "Mariä Himmelfahrt", fixed("--08-15"))

private fun easterSunday() = HolidayRule("Easter Sunday", "Ostersonntag", easter(EASTER_SUNDAY))

private fun whitSunday() = HolidayRule("Pentecost", "Pfingstsonntag", easter(WHIT_SUNDAY))

private fun reformationDay(since: Int? = null) = HolidayRule(
    "Reformation Day",
    "Reformationstag",
    fixed("--10-31"),
    inYear = { year -> since == null || year >= since }
)

private fun womensDay(since: Int) = HolidayRule(
    "Women's Day",
    "Frauentag",
    fixed("--03-08"),
    inYear = { year -> year >= since }
)

private fun worldChildrensDay() = HolidayRule(
    "World Children's Day",
    "Weltkindertag",
    fixed("--09-20"),
    inYear = { year -> year >= WORLD_CHILDRENS_DAY_SINCE }
)

/**
 * Buß- und Bettag: the Wednesday on or before 22 November, i.e. eleven days before the first
 * Sunday of Advent. A day off in Saxony only since 1995.
 */
private fun repentanceDay() = HolidayRule(
    "Repentance and Prayer Day",
    "Buß- und Bettag",
    { year, _ -> repentanceDayIn(year) },
    inYear = { year -> year >= REPENTANCE_DAY_SINCE }
)

private fun repentanceDayIn(year: Int): LocalDate =
    REPENTANCE_DAY_LATEST.atYear(year).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))

/** Berlin's one-off public holidays, each passed by the Abgeordnetenhaus for its year alone. */
private fun berlinAnniversaries() = listOf(
    HolidayRule(
        "75th anniversary of the liberation from Nazism and the end of the Second World War in Europe",
        "75. Jahrestag der Befreiung vom Nationalsozialismus und der Beendigung des Zweiten Weltkriegs in Europa",
        fixed("--05-08"),
        inYear = { it == BERLIN_LIBERATION_75_YEAR }
    ),
    HolidayRule(
        "80th anniversary of the liberation from Nazism and the end of the Second World War in Europe",
        "80. Jahrestag der Befreiung vom Nationalsozialismus und der Beendigung des Zweiten Weltkriegs in Europa",
        fixed("--05-08"),
        inYear = { it == BERLIN_LIBERATION_80_YEAR }
    ),
    HolidayRule(
        "75th anniversary of the East German uprising of 1953",
        "75. Jahrestag des Aufstandes vom 17. Juni 1953",
        fixed("--06-17"),
        inYear = { it == BERLIN_UPRISING_75_YEAR }
    )
)
