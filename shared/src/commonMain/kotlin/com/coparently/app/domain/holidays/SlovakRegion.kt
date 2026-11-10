package com.coparently.app.domain.holidays

import com.coparently.app.domain.holidays.SchoolBreak.JARNE_PRAZDNINY

/**
 * The eight Slovak regions (kraje) and the spring holidays each has (MON-13, regional half).
 *
 * Slovakia's school calendar is nationwide except for one week: the **spring holidays** (jarné
 * prázdniny), which the ministry staggers across the kraje in three consecutive weeks each year.
 * [SlovakHolidays] without a region draws the nationwide periods and leaves the spring week out
 * rather than guessing which of the three it is; a parent who names their kraj gets that week on
 * top, through [SlovakHolidays.forRegion].
 *
 * **Public holidays do not vary by kraj** — Act 241/1993 is national — so a region adds school
 * vacations and nothing else. `HolidayReferenceTest` checks every kraj draws exactly the
 * nationwide public holidays.
 *
 * **Source:** the OpenHolidays dataset (github.com/openpotato/openholidaysapi.data, ODbL 1.0),
 * whose Slovak rows follow the ministry's "Termíny prázdnin" (minedu.sk). The 2025/26 grouping was
 * checked against the ministry before this table was written; `SchoolVacationReferenceTest` holds
 * every kraj's full list — nationwide periods and spring week, both names — to the fixture
 * `tools/generate-school-vacation-fixture.py` writes from a pinned commit of the dataset.
 *
 * **Coverage:** school years 2025/26 to 2027/28 — every spring week the dataset had published at
 * that commit, none of them provisional. Nothing is extrapolated: the rotation looks regular, but
 * the ministry decides it per year, and a year past the table draws no spring week.
 *
 * @property code The ISO 3166-2 subdivision suffix (`"BL"` for `SK-BL`), as the dataset's
 *   `sk/subdivisions.csv` names it. Stored on the profile as `users.regionCode`, so an entry is
 *   never re-lettered. `NI` is Nitra here and Lower Saxony in Germany: a code only means something
 *   together with its country, which is why `HolidayCountry.regionOrNull` checks it against the
 *   country's own list and the UI names a region by country and code.
 */
internal enum class SlovakRegion(val code: String) {
    /** Banskobystrický kraj. */
    BC("BC"),

    /** Bratislavský kraj. */
    BL("BL"),

    /** Košický kraj. */
    KI("KI"),

    /** Nitriansky kraj. */
    NI("NI"),

    /** Prešovský kraj. */
    PV("PV"),

    /** Trnavský kraj. */
    TA("TA"),

    /** Trenčiansky kraj. */
    TC("TC"),

    /** Žilinský kraj. */
    ZI("ZI");

    companion object {
        /** The kraj a stored code names, or null for none or a code this build does not know. */
        fun fromCode(code: String?): SlovakRegion? {
            val normalized = code?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

/** Bratislava, Nitra and Trnava — one of the ministry's three spring-holiday groups. */
private val WEST = setOf(SlovakRegion.BL, SlovakRegion.NI, SlovakRegion.TA)

/** Banská Bystrica, Žilina and Trenčín — the central group. */
private val CENTRAL = setOf(SlovakRegion.BC, SlovakRegion.ZI, SlovakRegion.TC)

/** Košice and Prešov — the eastern group. */
private val EAST = setOf(SlovakRegion.KI, SlovakRegion.PV)

/**
 * Each spring week and the kraje it applies to, in date order. Written per week rather than per
 * kraj because that is how the ministry publishes it — three rows a year — so a row can be read
 * against the source at a glance.
 */
private val SPRING_WEEKS: List<Pair<SchoolVacation, Set<SlovakRegion>>> = listOf(
    JARNE_PRAZDNINY.on("2026-02-16", "2026-02-20") to WEST,
    JARNE_PRAZDNINY.on("2026-02-23", "2026-02-27") to CENTRAL,
    JARNE_PRAZDNINY.on("2026-03-02", "2026-03-06") to EAST,
    JARNE_PRAZDNINY.on("2027-02-15", "2027-02-19") to CENTRAL,
    JARNE_PRAZDNINY.on("2027-02-22", "2027-02-26") to EAST,
    JARNE_PRAZDNINY.on("2027-03-01", "2027-03-05") to WEST,
    JARNE_PRAZDNINY.on("2028-02-21", "2028-02-25") to EAST,
    JARNE_PRAZDNINY.on("2028-02-28", "2028-03-03") to WEST,
    JARNE_PRAZDNINY.on("2028-03-06", "2028-03-10") to CENTRAL
)

/** Each kraj's spring holidays; see [SlovakRegion] for the source and the coverage. */
internal val SLOVAK_SPRING_VACATIONS: Map<SlovakRegion, List<SchoolVacation>> =
    SlovakRegion.entries.associateWith { region ->
        SPRING_WEEKS.filter { (_, regions) -> region in regions }.map { (week, _) -> week }
    }
