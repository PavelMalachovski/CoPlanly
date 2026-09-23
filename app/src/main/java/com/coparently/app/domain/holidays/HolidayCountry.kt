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
 * - **Czechia** — public holidays and the nationwide MŠMT school vacations.
 * - **Slovakia, Germany, Austria, Russia** — public holidays only. Their school calendars are set
 *   per region, and inventing a nationwide one would be exactly the wrong date this item is about.
 *   Germany's table is the nine nationwide days (there is no state setting), and Russia's is the
 *   statutory list without the annual transfer decree; each provider's KDoc says what it leaves out.
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

    /** Public holidays only; the list changes by year (Acts 530/2023 and 261/2025). */
    SLOVAKIA("SK", SlovakHolidays),

    /** The nine nationwide public holidays; state holidays need a state setting. */
    GERMANY("DE", GermanHolidays),

    /** The thirteen nationwide public holidays. */
    AUSTRIA("AT", AustrianHolidays),

    /** No days off under martial law — see the class KDoc. */
    UKRAINE("UA", null, holidaysSuspended = true),

    /** Statutory public holidays (Labour Code art. 112), without the annual transfer decree. */
    RUSSIA("RU", RussianHolidays),

    /** Anywhere else. Public holidays are not shown; everything else works unchanged. */
    OTHER("ZZ", null);

    /** Whether picking this country actually puts holidays on the grid. */
    val hasHolidays: Boolean get() = provider != null

    /** What picking this country puts on the grid — the sentence the picker shows under it. */
    val coverage: HolidayCoverage
        get() = when {
            provider?.hasSchoolVacations == true -> HolidayCoverage.PUBLIC_AND_SCHOOL
            provider != null -> HolidayCoverage.PUBLIC_ONLY
            holidaysSuspended -> HolidayCoverage.SUSPENDED
            else -> HolidayCoverage.NONE
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
 * What a [HolidayCountry] puts on the calendar, in the terms the picker has to state it in.
 *
 * Four answers rather than a boolean because each is a different sentence to the user, and
 * collapsing any two would make one of them false: "holidays and school vacations" said for
 * Germany promises strips that never appear, and "not in the app yet" said for Ukraine implies
 * days off that do not exist.
 */
enum class HolidayCoverage {
    /** Public holidays and nationwide school vacations. */
    PUBLIC_AND_SCHOOL,

    /** Public holidays; the country's school calendar is regional or unknown to the app. */
    PUBLIC_ONLY,

    /** The country's public holidays are not days off at present, so none are drawn. */
    SUSPENDED,

    /** The app has no holiday table for this country, so none are drawn. */
    NONE
}
