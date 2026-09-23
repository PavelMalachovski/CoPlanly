package com.coparently.app.screenshots

import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentPalette
import kotlin.math.roundToInt

/** The five shipped languages, as the resource qualifier and the `java.util.Locale` tag. */
enum class ScreenshotLocale(val tag: String) {
    EN("en"),
    CS("cs"),
    DE("de"),
    RU("ru"),
    UK("uk")
}

/**
 * The two parent palettes every colour-bearing screenshot is taken in.
 *
 * [DEFAULT] is what an account that never opened the picker sees. [CHOSEN] is a pair that shares
 * no hue with it, so a surface still drawing the raw `CoPlanlyColors.MomPink`/`DadBlue` instead of
 * going through `LocalParentPalette` (design refresh item 12) shows up as pink or blue in an image
 * that should contain neither.
 */
enum class ScreenshotPalette(val palette: ParentPalette, val label: String) {
    DEFAULT(ParentPalette.Default, "pinkblue"),
    CHOSEN(ParentPalette(ParentColorChoice.PURPLE, ParentColorChoice.ORANGE), "purpleorange")
}

/**
 * One cell of the screenshot matrix: language, theme, font scale and palette.
 *
 * Its [fileName] is the image's name inside the component's directory, and its [toString] is the
 * parameterised test's name, so a failure in CI names the variant that failed.
 *
 * @param locale Language the resources and `Locale.getDefault()` resolve to
 * @param dark Dark theme (and the `night` resource qualifier) when true
 * @param fontScale The system font scale; 1.5 is where clipping and ellipsis show up
 * @param palette The family's two parent colours
 */
data class ScreenshotVariant(
    val locale: ScreenshotLocale,
    val dark: Boolean,
    val fontScale: Float,
    val palette: ScreenshotPalette
) {
    /** E.g. `de_light_fs150_pinkblue`. */
    val fileName: String
        get() = listOf(
            locale.tag,
            if (dark) "dark" else "light",
            "fs${(fontScale * PERCENT).roundToInt()}",
            palette.label
        ).joinToString("_")

    /**
     * Robolectric qualifiers: the language, a phone-sized window and the night mode. `xhdpi`
     * rather than a real device's 440dpi keeps each image around 800px wide, which is plenty to
     * judge clipping and keeps the artefact small.
     */
    val qualifiers: String
        get() = "${locale.tag}-w${WIDTH_DP}dp-h${HEIGHT_DP}dp-${if (dark) "night" else "notnight"}-xhdpi"

    override fun toString(): String = fileName

    private companion object {
        const val PERCENT = 100
        const val WIDTH_DP = 393
        const val HEIGHT_DP = 851
    }
}

/**
 * The two variant sets. The full cross product — 5 languages × 2 themes × 2 font scales × 2
 * palettes — is 40 images per component and ~500 in all, which nobody would page through, so
 * each set is a deliberate sample instead:
 *
 * - [TEXT_HEAVY] (9): every language at least once and in at least one theme it shares with
 *   English; 1.5× text on the languages with the longest words (German, Ukrainian) and on English
 *   as the reference; both themes; and the chosen palette in both themes.
 * - [COLOUR_ONLY] (4): components whose text is a name or a number — the question there is
 *   theme × palette, not translation.
 */
object ScreenshotVariants {

    private const val NORMAL_TEXT = 1f
    private const val LARGE_TEXT = 1.5f

    private fun variant(
        locale: ScreenshotLocale,
        dark: Boolean,
        fontScale: Float = NORMAL_TEXT,
        palette: ScreenshotPalette = ScreenshotPalette.DEFAULT
    ) = ScreenshotVariant(locale, dark, fontScale, palette)

    /** Nine variants for anything that carries translated text. */
    val TEXT_HEAVY: List<ScreenshotVariant> = listOf(
        variant(ScreenshotLocale.EN, dark = false),
        variant(ScreenshotLocale.EN, dark = true),
        variant(ScreenshotLocale.EN, dark = false, fontScale = LARGE_TEXT),
        variant(ScreenshotLocale.CS, dark = false),
        variant(ScreenshotLocale.DE, dark = false, fontScale = LARGE_TEXT),
        variant(ScreenshotLocale.RU, dark = true),
        variant(ScreenshotLocale.UK, dark = true, fontScale = LARGE_TEXT),
        variant(ScreenshotLocale.EN, dark = false, palette = ScreenshotPalette.CHOSEN),
        variant(ScreenshotLocale.CS, dark = true, palette = ScreenshotPalette.CHOSEN)
    )

    /** Four variants for components whose words are names and numbers. */
    val COLOUR_ONLY: List<ScreenshotVariant> = listOf(
        variant(ScreenshotLocale.EN, dark = false),
        variant(ScreenshotLocale.EN, dark = true),
        variant(ScreenshotLocale.EN, dark = false, palette = ScreenshotPalette.CHOSEN),
        variant(ScreenshotLocale.EN, dark = true, fontScale = LARGE_TEXT, palette = ScreenshotPalette.CHOSEN)
    )

    /** The shape `ParameterizedRobolectricTestRunner.Parameters` returns: one array per run. */
    fun parameters(variants: List<ScreenshotVariant>): List<Array<Any>> = variants.map { arrayOf<Any>(it) }
}
