package com.coparently.app.presentation.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.coparently.app.R
import kotlin.math.abs

/**
 * One of the four colours a parent may pick with one tap. Any other colour is a custom one,
 * picked on the wheel ([ParentColor.custom]).
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
         * wheel ([ParentColor.closeTo]) — must not have a calendar in one hue. Slot 1 keeps their
         * choice and slot 2 is moved to the first preset clear of it — not silently reassigned in
         * storage, only drawn differently, so the parent's own setting still says what they
         * picked and stops being overridden the moment the other one changes theirs.
         *
         * Slot 1 wins rather than "whoever chose first" because there is no reliable record of
         * who chose first: `colorCode` carries no timestamp, and inventing an order from two
         * profile documents that sync independently would pick a different winner on each
         * device.
         *
         * @param slot1Code `colorCode` of the parent in slot 1, or null when unknown.
         * @param slot2Code `colorCode` of the parent in slot 2, or null when unknown.
         */
        fun of(slot1Code: String?, slot2Code: String?): ParentPalette {
            val first = ParentColor.fromStored(slot1Code) ?: ParentColorChoice.defaultFor("mom").color
            val second = ParentColor.fromStored(slot2Code) ?: ParentColorChoice.defaultFor("dad").color
            if (!first.closeTo(second)) return ParentPalette(first, second)
            val free = ParentColorChoice.entries.map { it.color }.firstOrNull { !it.closeTo(first) }
                ?: ParentColorChoice.BLUE.color
            return ParentPalette(first, free)
        }
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
