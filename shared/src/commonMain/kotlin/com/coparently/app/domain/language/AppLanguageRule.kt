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
 * The English fallback **keeps the phone's region** (`sk-SK` gives `en-SK`), so what reads the
 * region from the locale — the currency hint, the onboarding's country guess, number formats —
 * still sees the country the parent is in.
 *
 * Pure, over BCP 47 language tags, so the iPhone can apply the same rule; Android's half is the
 * app's `utils/AppLocale.kt`.
 */
object AppLanguageRule {

    /**
     * The languages the app ships: `values/` (English) and the four `values-<language>` folders
     * of `shared-ui/src/commonMain/composeResources/`, the same set as `res/xml/locales_config.xml`
     * and the `AppLanguage` picker (the app's `AppLanguageRuleTest` holds the three to one list).
     */
    val SHIPPED: Set<String> = setOf("en", "cs", "de", "ru", "uk")

    /** The language of `values/`, spoken when the phone's first language is not shipped. */
    const val FALLBACK: String = "en"

    /**
     * The tag the app speaks: [perAppChoice] when there is one; else [deviceFirst] when its
     * language is in [shipped]; else [FALLBACK] with [deviceFirst]'s region, or bare when it has
     * none.
     *
     * @param perAppChoice The first locale of the per-app choice, or null (or blank) for none.
     * @param deviceFirst The phone's first language, as the system lists it, not as any app
     *   resolved it.
     */
    fun effective(perAppChoice: String?, deviceFirst: String, shipped: Set<String> = SHIPPED): String {
        val choice = perAppChoice?.trim()?.takeIf { it.isNotEmpty() && languageOf(it) != UNDETERMINED }
        if (choice != null) return choice
        if (languageOf(deviceFirst) in shipped) return deviceFirst
        val region = regionOf(deviceFirst) ?: return FALLBACK
        return "$FALLBACK-$region"
    }

    /** The language subtag of [tag], lower case (`cs` of `cs-CZ`); empty for an empty tag. */
    fun languageOf(tag: String): String = tag.trim().split('-', '_').first().lowercase()

    /**
     * The region subtag of [tag] (`SK` of `sk-SK`, `RS` of `sr-Latn-RS`, `419` of `es-419`), or
     * null when it names none. Only the subtags before the first extension or private-use
     * singleton are read, so `cs-u-fw-mon` has no region.
     */
    fun regionOf(tag: String): String? {
        for (subtag in tag.trim().split('-', '_').drop(1)) {
            when {
                subtag.length == 1 -> return null
                subtag.length == REGION_LETTERS && subtag.all { it.isLetter() } -> return subtag.uppercase()
                subtag.length == REGION_DIGITS && subtag.all { it.isDigit() } -> return subtag
                // An extended language subtag (three letters) or a script (four) comes before the
                // region; a variant comes after it, so a tag that reaches one has no region.
                subtag.length == EXTLANG_LETTERS && subtag.all { it.isLetter() } -> continue
                subtag.length == SCRIPT_LETTERS && subtag.all { it.isLetter() } -> continue
                else -> return null
            }
        }
        return null
    }

    private const val UNDETERMINED = "und"
    private const val REGION_LETTERS = 2
    private const val REGION_DIGITS = 3
    private const val EXTLANG_LETTERS = 3
    private const val SCRIPT_LETTERS = 4
}
