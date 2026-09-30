package com.coparently.app.domain.holidays

/**
 * The recurring kinds of Czech nationwide school break, each with the two names
 * [CzechHolidays.schoolVacations] has always carried (L-8, September 2026).
 *
 * A Czech break used to reach a German, Russian or Ukrainian reader in English: [Holiday.nameLocal]
 * is Czech and is shown only when the device speaks Czech. These breaks recur every year under the
 * same names, so they are the one part of the table the app can word in all five languages from
 * its own resources — the presentation layer maps a kind to a string
 * (`presentation/common/HolidayNames.kt`). The table itself is unchanged: [CzechHolidays] builds
 * its names from these entries, so `HolidayReferenceTest` compares exactly what it compared before.
 *
 * The district-dependent spring break is not in the table (see [CzechHolidays]) and so has no kind
 * here; a kind is added with its table row.
 *
 * @property nameEn The English name, as the table stores it.
 * @property nameCs The Czech name, as the table stores it.
 */
enum class CzechSchoolBreak(val nameEn: String, val nameCs: String) {
    /** Hlavní prázdniny, 1 July – 31 August. */
    SUMMER("Summer vacation", "Hlavní prázdniny"),

    /** Vánoční prázdniny, around 23 December – 2 January. */
    CHRISTMAS("Christmas vacation", "Vánoční prázdniny"),

    /** Podzimní prázdniny, beside 28 October. */
    AUTUMN("Autumn vacation", "Podzimní prázdniny"),

    /** Velikonoční prázdniny, the Thursday before Good Friday. */
    EASTER("Easter vacation", "Velikonoční prázdniny");

    /** The (English, Czech) pair [HolidayProvider.schoolVacations] carries. */
    val names: Pair<String, String> get() = nameEn to nameCs

    /** Lookup of a stored name back to its kind. */
    companion object {
        /**
         * The kind a school vacation named [nameLocal] in [localLanguage] is, or null for anything
         * that is not one of Czechia's own breaks — another country's vacation, a public holiday,
         * or a name this build does not know.
         */
        fun of(localLanguage: String, nameLocal: String): CzechSchoolBreak? =
            if (localLanguage != CzechHolidays.localLanguage) {
                null
            } else {
                entries.firstOrNull { it.nameCs == nameLocal }
            }
    }
}
