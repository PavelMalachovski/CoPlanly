package com.coparently.app.domain.holidays

/**
 * The country a parent lives in, and the holiday calendar that follows from it (MON-13).
 *
 * MVP 1 asked for "holidays and vacations by country" and shipped **one** country. There was no
 * country field anywhere in the app — no picker, no stored value, not even a constant — so
 * `CalendarScreen` called `CzechHolidays` directly and a family in Germany, Russia or Ukraine got
 * Czech public holidays on their grid, in an app that ships in five languages. This is the stored
 * answer to that question.
 *
 * ## What each entry promises, and what it does not
 *
 * [coverage] is what the picker states on the row, and it is derived, not declared, so the row
 * cannot claim more than the provider draws:
 * - **Czechia** — public holidays and the nationwide MŠMT school vacations (computed).
 * - **Slovakia and Austria** — public holidays and the *nationwide* school vacations, as published
 *   per school year (a dated table, from 2025/26 to the last published year). Slovakia's spring
 *   holidays are set per kraj and come with a chosen kraj ([regions]); Austria's semester and
 *   summer breaks are set per Land and are left out — the source has no final dates for them past
 *   2025/26, so a Land picker would add nothing.
 * - **Germany** — the nine nationwide public holidays, and no school vacations, until the parent
 *   names a Land; then the Land's own public holidays *and* its school vacations (see [regions],
 *   [HolidayLocation] and [coverageIn]). There is no nationwide German school period to draw.
 * - **Russia** — public holidays only: the statutory list without the annual transfer decree.
 *   Its school vacations are set per school, not by the state.
 *
 * Inventing a nationwide school calendar where the real one is regional would be exactly the wrong
 * date this item is about. Each provider's KDoc says what it leaves out, and where the vacation
 * dates come from.
 * - **Ukraine** — none, and not because the table is missing. Under martial law (in force since
 *   24 February 2022) Ukraine's public holidays are not days off, and the reference data returns
 *   none from 2023 on. A provider that computed the pre-war list would put days off on the grid
 *   that nobody has; one that returned nothing would make the picker say "holidays are shown"
 *   over an empty calendar. So [provider] stays null and [holidaysSuspended] gives the picker the
 *   true reason. When martial law ends, the table to add is whatever the law says then.
 * - **Other** — none; there is nothing to compute.
 *
 * A country with no provider shows **no** public holidays, which is strictly better than showing
 * another country's — the bug that started MON-13 — and the picker says so rather than leaving a
 * blank calendar to be discovered.
 *
 * ## Where the tables come from
 *
 * A holiday table is a set of user-visible facts, and a wrong date is worse than no date. The
 * owner's first answer (Aug 2026) was therefore to wait for verified data rather than author the
 * tables from memory. They landed once an independent, maintained dataset was obtainable: the
 * Python `holidays` library (v0.105, which cites the legislation for each rule). The providers
 * are still plain computed Kotlin with no runtime dependency; `HolidayReferenceTest` compares each
 * of them with a fixture generated from the library by `tools/generate-holiday-fixture.py`, every
 * date and both names, 2020–2035. That comparison is what caught Slovakia's 2024–2026
 * consolidation packages, which moved several days off the non-working list while leaving their
 * formal names in place.
 *
 * ## Why a country and not just "which holidays to show"
 *
 * A country is a fact about the user that other features will want — currency defaults, which
 * legal text applies, whether a school-import integration is even available (MON-8). Storing
 * "holiday calendar = none" would answer today's question and lose that.
 *
 * @property code ISO 3166-1 alpha-2, the value stored on the profile. Stable: two devices
 *   compare it, so an entry is never re-lettered.
 * @property provider This country's holiday calendar, or null when the app draws none for it.
 * @property holidaysSuspended True when the country's public holidays are, by law, not days off
 *   at present — so a null [provider] is the answer, not a gap in the app.
 */
enum class HolidayCountry(
    val code: String,
    val provider: HolidayProvider?,
    val holidaysSuspended: Boolean = false
) {
    /** Public holidays and school vacations, and the default. */
    CZECHIA("CZ", CzechHolidays),

    /** Public holidays (Acts 530/2023, 261/2025 change the list by year), school vacations, a kraj's spring week. */
    SLOVAKIA("SK", SlovakHolidays),

    /** The nine nationwide public holidays, plus the chosen Land's own and its school vacations. */
    GERMANY("DE", GermanHolidays),

    /** The thirteen public holidays and the nationwide school vacations — so no region to choose. */
    AUSTRIA("AT", AustrianHolidays),

    /** No days off under martial law — see the class KDoc. */
    UKRAINE("UA", null, holidaysSuspended = true),

    /** Statutory public holidays (Labour Code art. 112), without the annual transfer decree. */
    RUSSIA("RU", RussianHolidays),

    /** Anywhere else. Public holidays are not shown; everything else works unchanged. */
    OTHER("ZZ", null);

    /** Whether picking this country actually puts holidays on the grid. */
    val hasHolidays: Boolean get() = provider != null

    /**
     * The regions whose own public holidays and school vacations this country's calendar can add,
     * as ISO 3166-2 suffixes — the sixteen Länder for Germany, the eight kraje for Slovakia, and
     * empty everywhere else. The region picker appears only when this is non-empty, so it can
     * never be offered where it changes nothing.
     */
    val regions: List<String> get() = provider?.regions.orEmpty()

    /**
     * [code] if it names one of this country's [regions], else null.
     *
     * Null rather than a fallback region: "no region" is a real answer (the nationwide days), and
     * a stored code that no longer fits — a parent who moved from Germany to Austria, or a newer
     * build's region read by an older one — must degrade to that rather than to a guess.
     */
    fun regionOrNull(code: String?): String? {
        val normalized = code?.trim()?.uppercase() ?: return null
        return normalized.takeIf { it in regions }
    }

    /** What picking this country, with no region, puts on the grid. */
    val coverage: HolidayCoverage get() = coverageIn(null)

    /**
     * What picking this country and [regionCode] puts on the grid — the sentence the picker shows
     * under it. Read from the calendar [HolidayLocation] would draw, so Germany with a Land says
     * "school vacations" and Germany without one does not; a code that is not one of [regions] is
     * read as no region, as everywhere else.
     */
    fun coverageIn(regionCode: String?): HolidayCoverage {
        val calendar = HolidayLocation(this, regionOrNull(regionCode)).provider
        return when {
            calendar?.hasSchoolVacations == true -> HolidayCoverage.PUBLIC_AND_SCHOOL
            calendar != null -> HolidayCoverage.PUBLIC_ONLY
            holidaysSuspended -> HolidayCoverage.SUSPENDED
            else -> HolidayCoverage.NONE
        }
    }

    companion object {

        /**
         * What an account that has never chosen is treated as.
         *
         * Czechia, and the same value the v32→v33 migration stamps on every row that already
         * exists. The app is Czech-first, so this is both the honest default and the one that
         * changes nothing for anybody already using it.
         */
        val Default: HolidayCountry = CZECHIA

        /**
         * The country a stored code names, falling back to [Default].
         *
         * A fallback rather than null: every call site draws a calendar and has to draw
         * *something*, and an unrecognised code — a newer build's country read by an older one —
         * is better answered with the default than with a crash or an empty grid. Case is
         * normalised because the value has been written by a migration default, by a picker and
         * by whatever a co-parent's build sends.
         */
        fun fromCode(code: String?): HolidayCountry {
            val normalized = code?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.code == normalized } ?: Default
        }
    }
}

/**
 * Where a parent is, as far as the holiday calendar is concerned: a country and, where the
 * country's holidays vary by region, the region (MON-13).
 *
 * The one value the calendar reads — `CalendarViewModel` builds it from the profile's
 * `countryCode` and `regionCode`, and [provider] is what the grid draws. Built through [of], which
 * drops a region that does not belong to the country, so a stale `regionCode` left behind by a
 * change of country can never select another country's table.
 *
 * @property country The parent's country.
 * @property regionCode One of [HolidayCountry.regions] for [country], or null for the nationwide
 *   calendar.
 */
data class HolidayLocation(
    val country: HolidayCountry,
    val regionCode: String? = null
) {
    /** The calendar to draw, or null when the country has none. */
    val provider: HolidayProvider? get() = country.provider?.forRegion(regionCode)

    companion object {
        /** Every account that has never chosen: [HolidayCountry.Default], nationwide. */
        val Default: HolidayLocation = HolidayLocation(HolidayCountry.Default)

        /** The location two stored codes name; see [HolidayCountry.fromCode] and [HolidayCountry.regionOrNull]. */
        fun of(countryCode: String?, regionCode: String?): HolidayLocation {
            val country = HolidayCountry.fromCode(countryCode)
            return HolidayLocation(country, country.regionOrNull(regionCode))
        }
    }
}

/**
 * What a [HolidayCountry] puts on the calendar, in the terms the picker has to state it in.
 *
 * Four answers rather than a boolean because each is a different sentence to the user, and
 * collapsing any two would make one of them false: "holidays and school vacations" said for
 * Germany without a Land promises strips that never appear, and "not in the app yet" said for Ukraine implies
 * days off that do not exist.
 */
enum class HolidayCoverage {
    /** Public holidays and school vacations (the nationwide ones, or a chosen region's). */
    PUBLIC_AND_SCHOOL,

    /** Public holidays; the school calendar is regional (and no region is chosen) or unknown. */
    PUBLIC_ONLY,

    /** The country's public holidays are not days off at present, so none are drawn. */
    SUSPENDED,

    /** The app has no holiday table for this country, so none are drawn. */
    NONE
}
