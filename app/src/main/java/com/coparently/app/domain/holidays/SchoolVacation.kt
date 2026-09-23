package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * One published school vacation: its first and last day, inclusive, and what it is called.
 *
 * The data type of MON-13's school-vacation tables outside Czechia. Czech vacations are computed
 * (`CzechHolidays`); Slovakia's, Austria's and each German Land's are not computable — a ministry
 * publishes them per school year — so they are written as dated rows. Dates are ISO strings in the
 * tables ([on]) for the reason `HolidayRule` gives for its month-days: they say what they mean and
 * keep the tables clear of detekt's MagicNumber without a suppression.
 *
 * Every table built from these is held, period by period and name by name, to a fixture generated
 * from the OpenHolidays dataset by `tools/generate-school-vacation-fixture.py`
 * (`SchoolVacationReferenceTest`). Change a table by regenerating the fixture and reading the diff,
 * never by editing both sides to agree.
 */
internal data class SchoolVacation(
    val dates: ClosedRange<LocalDate>,
    val kind: SchoolBreak
)

/** A vacation of this kind from [first] to [last] inclusive (ISO dates); one day when [last] is omitted. */
internal fun SchoolBreak.on(first: String, last: String = first): SchoolVacation =
    SchoolVacation(LocalDate.parse(first)..LocalDate.parse(last), this)

/**
 * The periods overlapping [year], in the shape [HolidayProvider.schoolVacations] returns.
 *
 * Overlapping rather than starting in: a Christmas break that begins in December is still school
 * vacation on 2 January, and the grid asks by the year of the day it draws.
 */
internal fun List<SchoolVacation>.overlapping(
    year: Int
): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> {
    val first = LocalDate.of(year, 1, 1)
    val last = first.withDayOfYear(first.lengthOfYear())
    return filter { it.dates.start <= last && it.dates.endInclusive >= first }
        .map { it.dates to (it.kind.nameEn to it.kind.nameLocal) }
}

/**
 * What a published school vacation is called, in English and in its country's language.
 *
 * The local names are the dataset's own, which are the ministries' terms. The English names are
 * this project's: the dataset words the same German break differently from Land to Land
 * ("Pentecost Holidays", "Pentecost holiday"), and one break should read as one thing on the grid.
 * They follow `CzechHolidays` ("Summer vacation"). `tools/generate-school-vacation-fixture.py`
 * holds the same pairs in its `NAMES` table and refuses a name it does not know, so a new kind of
 * day arrives as a decision rather than as an unnamed strip.
 */
internal enum class SchoolBreak(val nameEn: String, val nameLocal: String) {
    // German and Austrian (German-language) breaks.
    HERBSTFERIEN("Autumn vacation", "Herbstferien"),
    WEIHNACHTSFERIEN("Christmas vacation", "Weihnachtsferien"),
    WINTERFERIEN("Winter vacation", "Winterferien"),
    FRUEHJAHRSFERIEN("Spring vacation", "Frühjahrsferien"),
    FASTNACHTSFERIEN("Carnival vacation", "Fastnachtsferien"),
    HALBJAHRESFERIEN("Mid-year vacation", "Halbjahresferien"),
    HALBJAHRESPAUSE("Mid-year break", "Halbjahrespause"),
    OSTERFERIEN("Easter vacation", "Osterferien"),
    PFINGSTFERIEN("Whitsun vacation", "Pfingstferien"),
    SOMMERFERIEN("Summer vacation", "Sommerferien"),
    ALLERSEELEN("All Souls' Day", "Allerseelen"),

    // Single school-free days a German Land publishes in its own list.
    BUSS_UND_BETTAG("Repentance and Prayer Day", "Buß- und Bettag"),
    REFORMATIONSFEST("Reformation Day", "Reformationsfest"),
    GRUENDONNERSTAG("Maundy Thursday", "Gründonnerstag"),
    HIMMELFAHRT("Ascension break", "Himmelfahrt"),
    TAG_NACH_HIMMELFAHRT("Day after Ascension", "Tag nach Himmelfahrt"),
    TAG_VOR_DEM_1_MAI("Day before 1 May", "Tag vor dem 1. Mai"),
    TAG_VOR_DEM_3_OKTOBER("Day before 3 October", "Tag vor dem 3. Oktober"),
    TAGE_NACH_DEM_3_OKTOBER("Days after 3 October", "Tage nach dem 3. Oktober"),
    BRUECKENTAG("Bridge day", "Brückentag"),
    FERIENTAG("Day off school", "Ferientag"),
    SCHULFREI("Day off school", "Schulfrei"),
    SCHULFREIER_TAG("Day off school", "Schulfreier Tag"),
    UNTERRICHTSFREIER_TAG("Day off school", "Unterrichtsfreier Tag"),
    VARIABLER_FERIENTAG("Day off school", "Variabler Ferientag"),
    ZUSAETZLICHER_FERIENTAG("Day off school", "Zusätzlicher Ferientag"),

    // Slovak breaks.
    JESENNE_PRAZDNINY("Autumn vacation", "Jesenné prázdniny"),
    VIANOCNE_PRAZDNINY("Christmas vacation", "Vianočné prázdniny"),
    VELKONOCNE_PRAZDNINY("Easter vacation", "Veľkonočné prázdniny"),
    LETNE_PRAZDNINY("Summer vacation", "Letné prázdniny")
}
