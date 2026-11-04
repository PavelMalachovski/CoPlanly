package com.coparently.app.domain.language

/**
 * The language the app speaks, decided once for every reader (the owner's rule, 4 November 2026):
 * **the phone's first language when the app ships it, otherwise English**. A per-app choice
 * (Settings → Language, or the system's per-app language on Android 13 and later) still wins. The
 * phone's second and later languages are never used.
 *
 * Android's own resolution does otherwise, which is why the app decides itself: it matches the
 * whole list of the phone's languages against everything the APK translates, libraries included
 * (AppCompat ships Slovak, Polish, Belarusian, Serbian and more), so a phone in Irish then Czech
 * read Czech and a phone in Slovak then Czech read English only because AppCompat happens to ship
 * Slovak. Under this rule both read English, and a phone whose first language is Czech reads Czech
 * whatever follows it.
 *
 * The English fallback is **European English**, [FALLBACK_LOCALE] (`en-150`, CLDR's "English
 * (Europe)"; owner decision, 4 November 2026): English words with a 24-hour clock, the day before
 * the month and English month names ("4 Nov 2026", "18:00"). It used to keep the phone's region
 * (`sk-SK` gave `en-SK`), but CLDR had no data for most such locales before release 47 — none for
 * Slovakia, Poland, Spain or Hungary — so on every Android the app runs on they fell back to US
 * English: a 12-hour clock and the month before the day. The phone's region is not lost: what
 * needs it (the currency hint, the onboarding's guess at the country) reads it from the phone
 * itself (`DeviceRegion`), never from the locale the app speaks, whose region is now "150".
 * A per-app choice of English stays the choice made ("en"), and a phone whose first language is
 * English keeps its own ("en-US", "en-GB").
 *
 * Pure, over BCP 47 language tags, so the iPhone can apply the same rule; Android's half is the
 * app's `utils/AppLocale.kt`.
 */
object AppLanguageRule {

    /**
     * The languages the app ships: `values/` (English) and the four `values-<language>` folders
     * of `shared-ui/src/commonMain/composeResources/`, the same set as `res/xml/locales_config.xml`
     * and the `AppLanguage` picker (the app's `ShippedLanguagesTest` holds the four to one list).
     */
    val SHIPPED: Set<String> = setOf("en", "cs", "de", "ru", "uk")

    /** The language of `values/`, spoken when the phone's first language is not shipped. */
    const val FALLBACK: String = "en"

    /**
     * The locale the English fallback speaks: [FALLBACK] in Europe (`150`, the UN M.49 code), whose
     * CLDR data every Android ICU the app runs on carries (API 26–36) — `HH:mm`, `d MMM y`,
     * `dd/MM/y`, Monday first. Its number symbols changed with CLDR 36: a decimal comma and a
     * grouping dot before it (ICU 58–63, Android 8–10: "1.234,50 €"), a decimal point and a
     * grouping comma since (Android 11 and later: "1,234.50 €"), the symbol after the amount in
     * both.
     */
    const val FALLBACK_LOCALE: String = "en-150"

    /**
     * The tag the app speaks: [perAppChoice] when there is one; else [deviceFirst] when its
     * language is in [shipped]; else [FALLBACK_LOCALE], whatever [deviceFirst]'s region.
     *
     * @param perAppChoice The first locale of the per-app choice, or null (or blank) for none.
     * @param deviceFirst The phone's first language, as the system lists it, not as any app
     *   resolved it.
     */
    fun effective(perAppChoice: String?, deviceFirst: String, shipped: Set<String> = SHIPPED): String {
        val choice = perAppChoice?.trim()?.takeIf { it.isNotEmpty() && languageOf(it) != UNDETERMINED }
        return when {
            choice != null -> choice
            languageOf(deviceFirst) in shipped -> deviceFirst
            else -> FALLBACK_LOCALE
        }
    }

    /** The language subtag of [tag], lower case (`cs` of `cs-CZ`); empty for an empty tag. */
    fun languageOf(tag: String): String = tag.trim().split('-', '_').first().lowercase()

    private const val UNDETERMINED = "und"
}
