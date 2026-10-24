package com.coparently.app.presentation.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.coparently.app.R
import kotlin.math.abs

/**
 * One of the eighteen colours a parent may pick with one tap from the grid ([GRID]). Any other
 * colour is a custom one, picked on the wheel ([ParentColor.custom]).
 *
 * **The colour stopped being derived from the slot** (owner decision, Aug 2026). It used to be:
 * slot 1 pink, slot 2 blue, with pairing deciding who got which. That was already arbitrary —
 * nobody picks a slot — and it breaks outright once a person co-parents with two others, because
 * the same person can hold slot 1 in one family and slot 2 in the other, so their own events
 * would change colour as they switched families.
 *
 * So the colour is a property of the *person*, stored on their profile as
 * [com.coparently.app.domain.model.User.colorCode] — the field has existed all along and is
 * finally used for what it is named — and each parent picks their own.
 *
 * **Nothing stores a gender, and that is the point.** A man picking blue and a woman picking
 * pink gets the outcome that was asked for; two men simply pick differently. There is no special
 * case in the code, no inference to get wrong, and no new personal data to declare in the privacy
 * policy — for a field whose only use is deciding which dot is whose.
 *
 * The slot is untouched and keeps every job it has: `parentOwner`, custody, `momDayIndices`, the
 * Firestore schema. Only the *colour* stopped reading it.
 *
 * **Brighter since October 2026** (the owner: "the colours look dull — make them all bright").
 * Each preset is a Material accent rather than a 700/800 step, with a brighter partner for the
 * dark theme, and text tones placed to hold AA on the brighter custody wash. The four tones are
 * ARGB literals, so the hue the collision rule compares ([ParentColor.hue]) is read from them
 * without converting a Compose colour.
 *
 * @property storedCode The value written to `users/{uid}.colorCode`. **An identifier, not the
 *   colour:** it is a stored value two devices compare, so an entry is never re-lettered, and it
 *   kept its original hex when the tones it names were brightened.
 * @property labelRes What the picker calls it. A name and not a swatch alone, because a colour
 *   with no label is unusable to anyone who cannot tell two of them apart — and because a
 *   screen reader has nothing else to announce.
 */
enum class ParentColorChoice(
    val storedCode: String,
    private val fillArgb: Long,
    private val darkFillArgb: Long,
    private val lightArgb: Long,
    private val darkArgb: Long,
    @StringRes val labelRes: Int
) {
    /** Material Pink 500, Pink A200 in the dark theme. The app's original slot-1 colour. */
    PINK("#E91E63", PINK_500, PINK_A200, PINK_TEXT_ON_DARK, PINK_TEXT_ON_LIGHT, R.string.parent_color_pink),

    /** Material Blue A400, Blue A200 in the dark theme. The original slot-2 colour, brighter. */
    BLUE("#1976D2", BLUE_A400, BLUE_A200, BLUE_TEXT_ON_DARK, BLUE_TEXT_ON_LIGHT, R.string.parent_color_blue),

    /**
     * Material Purple A700, Purple A200 in the dark theme.
     *
     * Purple and orange rather than green or teal: teal is the calendar friend's colour, and
     * green reads as "agreed" wherever this app already uses it. A third and fourth parent hue
     * that collided with either would undo what `DayCellFills` protects. (A parent may still
     * pick a green on the wheel; teal stays out of reach — see [CustomHueRange].)
     */
    PURPLE("#7B1FA2", PURPLE_A700, PURPLE_A200, PURPLE_TEXT_ON_DARK, PURPLE_700, R.string.parent_color_purple),

    /** Material Deep Orange 700, Deep Orange A200 in the dark theme. */
    ORANGE(
        "#D84315",
        DEEP_ORANGE_700,
        DEEP_ORANGE_A200,
        DEEP_ORANGE_TEXT_ON_DARK,
        DEEP_ORANGE_TEXT_ON_LIGHT,
        R.string.parent_color_orange
    ),

    // The grid's other fourteen (October 2026, owner decision: "more ready colours, as a grid like
    // Google Calendar's"). Each keeps its reference colour's hue and saturation and moves only as
    // far as every tone's luminance target needs (`ParentToneMath`'s targets, at the reference
    // saturation rather than full), so a pastel draws darker on the light theme than its name
    // suggests and keeps its softness in the dark one. `ParentColorsTest` holds them to what the
    // four above clear.

    /** Reference #D50000. */
    TOMATO(
        swatch("tomato"),
        TOMATO_FILL,
        TOMATO_DARK_FILL,
        TOMATO_TEXT_ON_DARK,
        TOMATO_TEXT_ON_LIGHT,
        R.string.parent_color_tomato
    ),

    /** Reference #E67C73; the light theme's fill is a deeper coral. */
    FLAMINGO(
        swatch("flamingo"),
        FLAMINGO_FILL,
        FLAMINGO_DARK_FILL,
        FLAMINGO_TEXT_ON_DARK,
        FLAMINGO_TEXT_ON_LIGHT,
        R.string.parent_color_flamingo
    ),

    /** Reference #F4511E. */
    TANGERINE(
        swatch("tangerine"),
        TANGERINE_FILL,
        TANGERINE_DARK_FILL,
        TANGERINE_TEXT_ON_DARK,
        TANGERINE_TEXT_ON_LIGHT,
        R.string.parent_color_tangerine
    ),

    /** Reference #F6BF26; ochre on the light theme, where a yellow could not show on white. */
    BANANA(
        swatch("banana"),
        BANANA_FILL,
        BANANA_DARK_FILL,
        BANANA_TEXT_ON_DARK,
        BANANA_TEXT_ON_LIGHT,
        R.string.parent_color_banana
    ),

    /** Reference #33B679. */
    SAGE(swatch("sage"), SAGE_FILL, SAGE_DARK_FILL, SAGE_TEXT_ON_DARK, SAGE_TEXT_ON_LIGHT, R.string.parent_color_sage),

    /** Reference #0B8043. */
    BASIL(
        swatch("basil"),
        BASIL_FILL,
        BASIL_DARK_FILL,
        BASIL_TEXT_ON_DARK,
        BASIL_TEXT_ON_LIGHT,
        R.string.parent_color_basil
    ),

    /** Reference #039BE5, hue 200° — outside the calendar friend's teal band. */
    PEACOCK(
        swatch("peacock"),
        PEACOCK_FILL,
        PEACOCK_DARK_FILL,
        PEACOCK_TEXT_ON_DARK,
        PEACOCK_TEXT_ON_LIGHT,
        R.string.parent_color_peacock
    ),

    /** Reference #3F51B5. */
    BLUEBERRY(
        swatch("blueberry"),
        BLUEBERRY_FILL,
        BLUEBERRY_DARK_FILL,
        BLUEBERRY_TEXT_ON_DARK,
        BLUEBERRY_TEXT_ON_LIGHT,
        R.string.parent_color_blueberry
    ),

    /** Reference #7986CB. */
    LAVENDER(
        swatch("lavender"),
        LAVENDER_FILL,
        LAVENDER_DARK_FILL,
        LAVENDER_TEXT_ON_DARK,
        LAVENDER_TEXT_ON_LIGHT,
        R.string.parent_color_lavender
    ),

    /** Reference #8E24AA. */
    GRAPE(
        swatch("grape"),
        GRAPE_FILL,
        GRAPE_DARK_FILL,
        GRAPE_TEXT_ON_DARK,
        GRAPE_TEXT_ON_LIGHT,
        R.string.parent_color_grape
    ),

    /** Reference #795548. */
    COCOA(
        swatch("cocoa"),
        COCOA_FILL,
        COCOA_DARK_FILL,
        COCOA_TEXT_ON_DARK,
        COCOA_TEXT_ON_LIGHT,
        R.string.parent_color_cocoa
    ),

    /** Reference #AD1457. */
    CHERRY(
        swatch("cherry"),
        CHERRY_FILL,
        CHERRY_DARK_FILL,
        CHERRY_TEXT_ON_DARK,
        CHERRY_TEXT_ON_LIGHT,
        R.string.parent_color_cherry
    ),

    /** Reference #827717. */
    OLIVE(
        swatch("olive"),
        OLIVE_FILL,
        OLIVE_DARK_FILL,
        OLIVE_TEXT_ON_DARK,
        OLIVE_TEXT_ON_LIGHT,
        R.string.parent_color_olive
    ),

    /** Reference #283593. */
    INDIGO(
        swatch("indigo"),
        INDIGO_FILL,
        INDIGO_DARK_FILL,
        INDIGO_TEXT_ON_DARK,
        INDIGO_TEXT_ON_LIGHT,
        R.string.parent_color_indigo
    );

    /** This preset as the palette draws it. */
    val color: ParentColor by lazy {
        ParentColor.preset(
            choice = this,
            tones = ParentTones(
                fill = fillArgb.toInt(),
                darkFill = darkFillArgb.toInt(),
                light = lightArgb.toInt(),
                dark = darkArgb.toInt()
            )
        )
    }

    companion object {

        /**
         * The preset a stored code names, or null when it names none — including a custom hue,
         * which [ParentColor.fromStored] reads.
         *
         * Case-insensitive because the value has been written by hand into fixtures and by
         * three different code paths. Null rather than a guess: the caller knows what to fall
         * back to, and silently mapping an unknown colour onto pink would make a co-parent's
         * deliberate choice look like the default.
         */
        fun fromStored(code: String?): ParentColorChoice? {
            val normalized = code?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.storedCode.uppercase() == normalized }
        }

        /**
         * What a parent holding [slot] is shown when they have not chosen.
         *
         * The colours the app has always used, so an account that upgrades sees no change until
         * somebody actually picks something.
         */
        fun defaultFor(slot: String): ParentColorChoice = if (slot == "dad") BLUE else PINK

        /**
         * The four colours the app had before the grid, in the order they were always offered.
         * Onboarding still shows these four; the grid in Settings shows them among [GRID].
         */
        val ORIGINAL: List<ParentColorChoice> = listOf(PINK, BLUE, PURPLE, ORANGE)

        /**
         * The order the Settings grid shows, five to a row, the hues running round the wheel.
         *
         * **Not the declaration order, on purpose.** When two parents' colours collide,
         * `ParentPalette` draws the yielding one in the first entry of [entries] clear of the
         * other. Declaring [ORIGINAL] first keeps that fallback exactly what it was — a family
         * where both chose pink still sees blue for the second parent — whatever order the grid
         * puts on screen.
         */
        val GRID: List<ParentColorChoice> = listOf(
            TOMATO, FLAMINGO, PINK, TANGERINE, BANANA,
            SAGE, BASIL, PEACOCK, BLUE, BLUEBERRY,
            LAVENDER, GRAPE, PURPLE, COCOA, ORANGE,
            CHERRY, OLIVE, INDIGO
        )
    }
}

/**
 * A parent's colour as it is drawn: a preset, or a hue picked on the wheel.
 *
 * Each colour carries several tones for the reason `ParentColors` documents at length: the full
 * strength hue is **fill-only** — no hue clears 4.5:1 as a foreground in both themes — so text
 * needs the theme-aware [light]/[dark] partner instead, and a dark theme needs a fill of its own.
 * A custom colour's tones are derived from its hue alone by [ParentToneMath], placed by luminance
 * so that every hue clears what the presets clear; `ParentColorsTest` walks the whole wheel.
 *
 * Two colours are equal when they are stored the same: the tones follow from the code.
 *
 * @property storedCode What saving this colour writes to `users/{uid}.colorCode`.
 * @property hue Degrees on the wheel, which is what [closeTo] compares.
 * @property preset The preset this is, or null for a custom colour.
 */
@Immutable
class ParentColor private constructor(
    val storedCode: String,
    tones: ParentTones,
    val hue: Float,
    val preset: ParentColorChoice?
) {
    /** The full hue for the light theme: dots, bars, borders, and the custody wash. */
    val fill: Color = Color(tones.fill)

    /**
     * The full hue in the dark theme. Brighter than [fill]: a mid-tone over a near-black surface
     * reads as murky, and it must clear 3:1 on the dark weekend grey.
     */
    val darkFill: Color = Color(tones.darkFill)

    /** The text-grade tone on a dark surface. */
    val light: Color = Color(tones.light)

    /** The text-grade tone on a light surface, and the solid chip that carries a white label. */
    val dark: Color = Color(tones.dark)

    /**
     * What a dark-theme *wash* is made of: [darkFill], darkened at its own hue until it is no
     * brighter than [ParentToneMath.DARK_WASH_MAX_LUMINANCE]. A yellow parent's dark fill is
     * nearly white in luminance, and washed over a day it would take the day number under AA.
     */
    val darkWash: Color = Color(ParentToneMath.capLuminance(tones.darkFill, ParentToneMath.DARK_WASH_MAX_LUMINANCE))

    /**
     * The full hue for a theme — [fill], or [darkFill] in the dark — without the composition, for
     * a test or a draw lambda that has already resolved the theme; `ParentColors.fill` reads it.
     */
    fun fillIn(darkTheme: Boolean): Color = if (darkTheme) darkFill else fill

    /** The text-grade tone for a theme: [dark] on a light surface, [light] on a dark one. */
    fun textIn(darkTheme: Boolean): Color = if (darkTheme) light else dark

    /** What a wash of this colour is made of in a theme, before its alpha: [fill], or [darkWash]. */
    fun washIn(darkTheme: Boolean): Color = if (darkTheme) darkWash else fill

    /**
     * Whether [other] would read as the same colour beside this one: under [MIN_HUE_DISTANCE]
     * degrees apart. Every tone is placed by luminance, so the hue is the only thing that tells
     * two parents apart.
     */
    fun closeTo(other: ParentColor): Boolean {
        val distance = abs(hue - other.hue) % FULL_TURN
        return minOf(distance, FULL_TURN - distance) < MIN_HUE_DISTANCE
    }

    override fun equals(other: Any?): Boolean = other is ParentColor && other.storedCode == storedCode

    override fun hashCode(): Int = storedCode.hashCode()

    override fun toString(): String = "ParentColor($storedCode)"

    companion object {

        /**
         * Hues closer than this read as one colour to a parent glancing at a month. Below the
         * nearest pair of presets — pink and orange, about 35 degrees apart — so no family that
         * chose two presets is affected.
         */
        const val MIN_HUE_DISTANCE = 30f

        private const val FULL_TURN = 360f

        /**
         * The colour a stored code names — a preset or a custom hue — or null when it names
         * neither: nothing stored, the legacy `#FF4081` older builds wrote for "not chosen", or
         * text nobody meant as a colour. Null is a real answer; the caller draws the slot default.
         */
        fun fromStored(code: String?): ParentColor? =
            ParentColorChoice.fromStored(code)?.color ?: ParentColorCode.customHueOf(code)?.let(::custom)

        /**
         * A colour picked on the wheel. A hue inside [CustomHueRange.RESERVED] — the calendar
         * friend's teal, which the picker never offers — is drawn at the nearer edge of the band.
         */
        fun custom(hue: Int): ParentColor {
            val allowed = CustomHueRange.allowed(hue)
            return ParentColor(
                storedCode = ParentColorCode.custom(allowed),
                tones = ParentToneMath.deriveTones(allowed.toFloat()),
                hue = allowed.toFloat(),
                preset = null
            )
        }

        /** A preset's colour; [ParentColorChoice.color] is the only caller. */
        internal fun preset(choice: ParentColorChoice, tones: ParentTones): ParentColor =
            ParentColor(
                storedCode = choice.storedCode,
                tones = tones,
                hue = ParentToneMath.hueOf(tones.fill),
                preset = choice
            )
    }
}

/**
 * The two colours in play for the family currently on screen.
 *
 * Keyed by *slot* rather than by uid because that is what every drawing call site already holds:
 * an event carries `parentOwner`, a custody day carries a slot, an expense is attributed to one.
 * Resolving uid → colour here, once, is what keeps [ParentColors] a lookup instead of nineteen
 * call sites each needing the two parents.
 *
 * @property slot1 The colour of whoever holds `"mom"`.
 * @property slot2 The colour of whoever holds `"dad"`.
 */
@Immutable
data class ParentPalette(
    val slot1: ParentColor,
    val slot2: ParentColor
) {
    /** The colour for a stored slot identifier; anything unrecognised reads as slot 1. */
    fun of(slot: String): ParentColor = if (slot == "dad") slot2 else slot1

    companion object {

        /** Pink and blue — what the app looked like before anyone could choose. */
        val Default = ParentPalette(ParentColorChoice.PINK.color, ParentColorChoice.BLUE.color)

        /**
         * Builds the palette from what the two parents actually chose.
         *
         * **Two parents who chose the same colour are not drawn the same**, and resolving that
         * is this function's real job: the whole point of a parent colour is telling the two
         * apart, so a family where both picked blue — or two blues a few degrees apart on the
         * wheel ([ParentColor.closeTo]) — must not have a calendar in one hue. One of the two is
         * moved to the first preset clear of the other — not silently reassigned in storage, only
         * drawn differently, so the parent's own setting still says what they picked and stops
         * being overridden the moment the other one changes theirs.
         *
         * **A pick beats a default.** When only one parent has chosen, the one who has not is
         * moved: their colour is a default nobody decided on, and moving the pick instead would
         * draw that parent in a colour their own settings never showed them. When both chose,
         * slot 1 keeps theirs rather than "whoever chose first", because there is no reliable
         * record of who chose first: `colorCode` carries no timestamp, and inventing an order
         * from two profile documents that sync independently would pick a different winner on
         * each device. Both rules read only the two codes, so both phones draw the same.
         *
         * @param slot1Code `colorCode` of the parent in slot 1, or null when unknown.
         * @param slot2Code `colorCode` of the parent in slot 2, or null when unknown.
         */
        fun of(slot1Code: String?, slot2Code: String?): ParentPalette {
            val chosen1 = ParentColor.fromStored(slot1Code)
            val chosen2 = ParentColor.fromStored(slot2Code)
            val first = chosen1 ?: ParentColorChoice.defaultFor("mom").color
            val second = chosen2 ?: ParentColorChoice.defaultFor("dad").color
            return when {
                !first.closeTo(second) -> ParentPalette(first, second)
                chosen1 == null && chosen2 != null -> ParentPalette(firstPresetClearOf(second), second)
                else -> ParentPalette(first, firstPresetClearOf(first))
            }
        }

        private fun firstPresetClearOf(colour: ParentColor): ParentColor =
            ParentColorChoice.entries.map { it.color }.firstOrNull { !it.closeTo(colour) }
                ?: ParentColorChoice.BLUE.color
    }
}

// The presets' tones as ARGB, named after their Material steps where they are one. The text tones
// are placed between steps on purpose: deep enough (light theme) or light enough (dark theme) to
// hold AA on the parent's own custody wash, which `ParentColorsTest` checks.
private const val PINK_500 = 0xFFE91E63
private const val PINK_A200 = 0xFFFF4081
private const val PINK_TEXT_ON_DARK = 0xFFFF99BB
private const val PINK_TEXT_ON_LIGHT = 0xFFA0124F
private const val BLUE_A400 = 0xFF2979FF
private const val BLUE_A200 = 0xFF448AFF
private const val BLUE_TEXT_ON_DARK = 0xFF9EC2FF
private const val BLUE_TEXT_ON_LIGHT = 0xFF1550C0
private const val PURPLE_A700 = 0xFFAA00FF
private const val PURPLE_A200 = 0xFFE040FB
private const val PURPLE_TEXT_ON_DARK = 0xFFEEA2FF
private const val PURPLE_700 = 0xFF7B1FA2
private const val DEEP_ORANGE_700 = 0xFFE64A19
private const val DEEP_ORANGE_A200 = 0xFFFF6E40
private const val DEEP_ORANGE_TEXT_ON_DARK = 0xFFFFA98F
private const val DEEP_ORANGE_TEXT_ON_LIGHT = 0xFFA02F00

// The grid colours' tones: fill (light theme), dark-theme fill, text on the dark theme, text on the
// light theme (also the chip). Placed by `ParentToneMath`'s luminance targets at each reference
// colour's own saturation.
private const val TOMATO_FILL = 0xFFD50000
private const val TOMATO_DARK_FILL = 0xFFFF4D4D
private const val TOMATO_TEXT_ON_DARK = 0xFFFFA3A3
private const val TOMATO_TEXT_ON_LIGHT = 0xFFA50000
private const val FLAMINGO_FILL = 0xFFDC463A
private const val FLAMINGO_DARK_FILL = 0xFFE67C73
private const val FLAMINGO_TEXT_ON_DARK = 0xFFEFABA5
private const val FLAMINGO_TEXT_ON_LIGHT = 0xFF97251B
private const val TANGERINE_FILL = 0xFFE33F0B
private const val TANGERINE_DARK_FILL = 0xFFF55B2A
private const val TANGERINE_TEXT_ON_DARK = 0xFFF9A88E
private const val TANGERINE_TEXT_ON_LIGHT = 0xFF952907
private const val BANANA_FILL = 0xFF9D7507
private const val BANANA_DARK_FILL = 0xFFE3A909
private const val BANANA_TEXT_ON_DARK = 0xFFF9D46C
private const val BANANA_TEXT_ON_LIGHT = 0xFF654C04
private const val SAGE_FILL = 0xFF278C5D
private const val SAGE_DARK_FILL = 0xFF33B679
private const val SAGE_TEXT_ON_DARK = 0xFF87DEB5
private const val SAGE_TEXT_ON_LIGHT = 0xFF195A3C
private const val BASIL_FILL = 0xFF0B8043
private const val BASIL_DARK_FILL = 0xFF0EA456
private const val BASIL_TEXT_ON_DARK = 0xFF72F3B0
private const val BASIL_TEXT_ON_LIGHT = 0xFF085C30
private const val PEACOCK_FILL = 0xFF0382C1
private const val PEACOCK_DARK_FILL = 0xFF039BE5
private const val PEACOCK_TEXT_ON_DARK = 0xFF68CCFD
private const val PEACOCK_TEXT_ON_LIGHT = 0xFF02557D
private const val BLUEBERRY_FILL = 0xFF3F51B5
private const val BLUEBERRY_DARK_FILL = 0xFF7D8AD2
private const val BLUEBERRY_TEXT_ON_DARK = 0xFFB2BAE4
private const val BLUEBERRY_TEXT_ON_LIGHT = 0xFF3847A0
private const val LAVENDER_FILL = 0xFF6876C4
private const val LAVENDER_DARK_FILL = 0xFF7E8ACD
private const val LAVENDER_TEXT_ON_DARK = 0xFFB2BAE1
private const val LAVENDER_TEXT_ON_LIGHT = 0xFF3B4998
private const val GRAPE_FILL = 0xFF8E24AA
private const val GRAPE_DARK_FILL = 0xFFC667DF
private const val GRAPE_TEXT_ON_DARK = 0xFFDEA9ED
private const val GRAPE_TEXT_ON_LIGHT = 0xFF802199
private const val COCOA_FILL = 0xFF795548
private const val COCOA_DARK_FILL = 0xFFAE8576
private const val COCOA_TEXT_ON_DARK = 0xFFCFB7AE
private const val COCOA_TEXT_ON_LIGHT = 0xFF67493E
private const val CHERRY_FILL = 0xFFAD1457
private const val CHERRY_DARK_FILL = 0xFFEC5898
private const val CHERRY_TEXT_ON_DARK = 0xFFF5A4C7
private const val CHERRY_TEXT_ON_LIGHT = 0xFF9B124E
private const val OLIVE_FILL = 0xFF827717
private const val OLIVE_DARK_FILL = 0xFF9D901C
private const val OLIVE_TEXT_ON_DARK = 0xFFE8DD7D
private const val OLIVE_TEXT_ON_LIGHT = 0xFF585110
private const val INDIGO_FILL = 0xFF283593
private const val INDIGO_DARK_FILL = 0xFF7C88DB
private const val INDIGO_TEXT_ON_DARK = 0xFFB2B9EA
private const val INDIGO_TEXT_ON_LIGHT = 0xFF3242B7

/** The stored code of a grid colour: see [ParentColorCode.SWATCH_PREFIX]. */
private fun swatch(name: String): String = ParentColorCode.SWATCH_PREFIX + name
