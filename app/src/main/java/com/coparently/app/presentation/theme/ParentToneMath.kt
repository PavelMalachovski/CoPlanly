package com.coparently.app.presentation.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The four tones a parent colour is drawn in, as packed ARGB ints — the same roles
 * [ParentColor] holds as Compose colours.
 *
 * @property fill The full hue in the light theme: dots, bars, borders, the custody wash.
 * @property darkFill The full hue in the dark theme.
 * @property light The text-grade tone on a dark surface.
 * @property dark The text-grade tone on a light surface, and the chip that carries a white label.
 */
internal data class ParentTones(val fill: Int, val darkFill: Int, val light: Int, val dark: Int)

/**
 * Derives a parent colour's tones from nothing but a hue, so a colour the parent picked on the
 * wheel is as readable as the four presets that were checked by hand.
 *
 * **Every tone is placed by its WCAG luminance, not by eye.** A hue is taken at full HSL
 * saturation and its lightness is searched until the tone's relative luminance reaches the target
 * its role needs: a fill dark enough to show on the light weekend grey, a dark-theme fill bright
 * enough to show on the dark one, text tones that clear AA on every surface of their theme. Hue
 * alone then decides nothing about contrast — a yellow and a blue at the same luminance read the
 * same — which is what lets `ParentToneMathTest` hold all 360 hues to the presets' guarantees.
 * The cost is honest rather than hidden: yellow has to be dark to show on white, so a yellow
 * parent is mustard in the light theme and bright yellow in the dark one.
 *
 * Pure Kotlin on ARGB ints, with no Compose or Android type, so the whole wheel is tested on the
 * JVM in milliseconds and both phones derive identical tones from the same stored hue.
 *
 * [luminance] is the sRGB transfer Compose's own `Color.luminance()` uses (a 0.04045 knee), so a
 * tone measured here and a colour measured by `ParentColors.contrastRatio` agree.
 */
internal object ParentToneMath {

    /** A light-theme fill sits here: 3.5:1 on the light weekend grey, 4.2:1 on white. */
    const val LIGHT_FILL_LUMINANCE = 0.20

    /** A dark-theme fill is at least this bright: 3.9:1 on the dark weekend grey. */
    const val DARK_FILL_MIN_LUMINANCE = 0.27

    /**
     * And at most this bright: yellows and greens are far brighter than this at full chroma, and a
     * dot at 0.9 luminance glares rather than identifies.
     */
    const val DARK_FILL_MAX_LUMINANCE = 0.45

    /**
     * The brightest a colour may be where it is *washed* over a dark surface — the custody band,
     * an event block. Above it the wash turns light enough to take the white day number and the
     * holiday red under AA; `ParentColors` caps a dark fill with [capLuminance] before washing.
     */
    const val DARK_WASH_MAX_LUMINANCE = 0.30

    /** Text on a dark surface: AA there, and AA on its own custody wash. */
    const val TEXT_ON_DARK_LUMINANCE = 0.50

    /** And never darker than a pastel, which is how a text tone on a dark surface reads as one. */
    const val TEXT_ON_DARK_MIN_LIGHTNESS = 0.70f

    /** Text on a light surface, and a chip under a white label: 7:1 on white. */
    const val TEXT_ON_LIGHT_LUMINANCE = 0.08

    /**
     * The tones for [hue], in degrees; any value is brought onto the wheel first.
     *
     * Deterministic: both phones draw the co-parent from the stored hue alone.
     */
    fun deriveTones(hue: Float): ParentTones {
        val h = normalizedHue(hue)
        val vividLuminance = luminance(hsl(h, FULL, HALF))
        val darkFillLightness = when {
            vividLuminance < DARK_FILL_MIN_LUMINANCE ->
                lightnessFor(h, FULL, DARK_FILL_MIN_LUMINANCE, atLeast = true)
            vividLuminance > DARK_FILL_MAX_LUMINANCE ->
                lightnessFor(h, FULL, DARK_FILL_MAX_LUMINANCE, atLeast = false)
            else -> HALF
        }
        val lightTextLightness = max(
            lightnessFor(h, FULL, TEXT_ON_DARK_LUMINANCE, atLeast = true),
            TEXT_ON_DARK_MIN_LIGHTNESS
        )
        return ParentTones(
            fill = hsl(h, FULL, lightnessFor(h, FULL, LIGHT_FILL_LUMINANCE, atLeast = false)),
            darkFill = hsl(h, FULL, darkFillLightness),
            light = hsl(h, FULL, lightTextLightness),
            dark = hsl(h, FULL, lightnessFor(h, FULL, TEXT_ON_LIGHT_LUMINANCE, atLeast = false))
        )
    }

    /**
     * [argb] darkened, at its own hue and saturation, until its luminance is at most [cap] — or
     * [argb] itself, untouched, when it already is.
     */
    fun capLuminance(argb: Int, cap: Double): Int {
        if (luminance(argb) <= cap) return argb
        val (hue, saturation) = hslOf(argb)
        return hsl(hue, saturation, lightnessFor(hue, saturation, cap, atLeast = false))
    }

    /** WCAG relative luminance of an opaque colour, 0 for black to 1 for white. */
    fun luminance(argb: Int): Double =
        RED_WEIGHT * linear(argb shr RED_SHIFT and CHANNEL) +
            GREEN_WEIGHT * linear(argb shr GREEN_SHIFT and CHANNEL) +
            BLUE_WEIGHT * linear(argb and CHANNEL)

    /** The WCAG 2.x contrast ratio between two opaque colours, from 1 to 21, in either order. */
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + WCAG_FLARE) / (min(a, b) + WCAG_FLARE)
    }

    /** The HSL hue of [argb] in degrees, `[0, 360)`; 0 for a grey, which has none. */
    fun hueOf(argb: Int): Float = hslOf(argb).first

    /**
     * An opaque colour from HSL, every channel rounded to 8 bits.
     *
     * @param hue Degrees; any value is brought onto the wheel.
     * @param saturation 0..1.
     * @param lightness 0..1.
     */
    fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val sector = normalizedHue(hue) / SECTOR_DEGREES
        val second = chroma * (1f - abs(sector % 2f - 1f))
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val offset = lightness - chroma / 2f
        return argb(r + offset, g + offset, b + offset)
    }

    /**
     * The lightness at which [hue] and [saturation] reach [target] luminance.
     *
     * Luminance never falls as HSL lightness rises — every channel is non-decreasing in it — so a
     * bisection finds the crossing. [atLeast] picks which side of it to stand on: the side at or
     * above the target for a tone that must be *at least* that bright, the side below for one that
     * must be at most.
     */
    private fun lightnessFor(hue: Float, saturation: Float, target: Double, atLeast: Boolean): Float {
        var low = 0f
        var high = 1f
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2f
            if (luminance(hsl(hue, saturation, mid)) < target) low = mid else high = mid
        }
        return if (atLeast) high else low
    }

    /** Hue and saturation of [argb] (lightness is not needed by any caller). */
    private fun hslOf(argb: Int): Pair<Float, Float> {
        val r = (argb shr RED_SHIFT and CHANNEL) / CHANNEL_MAX
        val g = (argb shr GREEN_SHIFT and CHANNEL) / CHANNEL_MAX
        val b = (argb and CHANNEL) / CHANNEL_MAX
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val delta = maxChannel - minChannel
        if (delta == 0f) return 0f to 0f
        val lightness = (maxChannel + minChannel) / 2f
        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue = when (maxChannel) {
            r -> SECTOR_DEGREES * (((g - b) / delta) % SECTOR_COUNT)
            g -> SECTOR_DEGREES * ((b - r) / delta + 2f)
            else -> SECTOR_DEGREES * ((r - g) / delta + 4f)
        }
        return normalizedHue(hue) to saturation.coerceIn(0f, 1f)
    }

    private fun normalizedHue(hue: Float): Float = ((hue % FULL_TURN) + FULL_TURN) % FULL_TURN

    private fun linear(channel: Int): Double {
        val c = channel / CHANNEL_MAX.toDouble()
        return if (c <= SRGB_KNEE) c / SRGB_LINEAR_SLOPE else ((c + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
    }

    private fun argb(r: Float, g: Float, b: Float): Int =
        OPAQUE or (channel(r) shl RED_SHIFT) or (channel(g) shl GREEN_SHIFT) or channel(b)

    private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()

    private const val FULL = 1f
    private const val HALF = 0.5f
    private const val FULL_TURN = 360f
    private const val SECTOR_DEGREES = 60f
    private const val SECTOR_COUNT = 6f
    private const val SEARCH_STEPS = 32
    private const val CHANNEL = 0xFF
    private const val CHANNEL_MAX = 255f
    private const val OPAQUE = -0x1000000 // 0xFF000000
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val SRGB_KNEE = 0.04045
    private const val SRGB_LINEAR_SLOPE = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
    private const val WCAG_FLARE = 0.05
}
