package com.coparently.app.presentation.theme

import kotlin.math.roundToInt

/**
 * How a colour a parent picked on the wheel is written to `users/{uid}.colorCode`.
 *
 * The field already holds one of the four preset codes ([ParentColorChoice.storedCode]), and an
 * older build reads anything else as "not chosen" and draws its slot's default — which is the
 * honest fallback for a colour it cannot draw. So a custom colour goes in the same field, as
 * `hue1|<degrees>`: a versioned codec string like the app's other stored formats, never a bare
 * hex. A hex would be ambiguous: every profile an older build saved carries `#FF4081`, the
 * default it wrote for a parent who had *not* chosen, and reading that as a chosen pink would
 * recolour every parent who never touched the setting. Only the hue is stored because the hue is
 * all a custom colour is: [ParentToneMath] derives every tone from it, on both phones alike.
 *
 * Anything the reader does not recognise reads as "not chosen" — the field is written by the
 * co-parent's account as freely as by this one, so the reader is bounded and never throws.
 */
object ParentColorCode {

    /** The version tag of a custom colour. A new format gets a new tag, never a new meaning. */
    const val CUSTOM_PREFIX = "hue1|"

    /**
     * The version tag of a grid colour added in October 2026 (`sw1|tomato`), beside the four
     * original presets, which keep the hex they were always stored as.
     *
     * A tag and not a hex for [CUSTOM_PREFIX]'s reason: a hex reader cannot tell a chosen colour
     * from the `#FF4081` older builds wrote for nobody's choice. An older build reads the tag as
     * "not chosen" and draws its slot default, which is honest for a colour it cannot draw. The
     * name after the tag is an identifier: it is never renamed, whatever the label says.
     */
    const val SWATCH_PREFIX = "sw1|"

    /** The stored form of [hue], brought onto the wheel first. */
    fun custom(hue: Int): String = CUSTOM_PREFIX + hue.mod(FULL_TURN)

    /**
     * The hue a stored value names, or null when it names none: a preset code, the legacy
     * `#FF4081`, an empty field, another version, or text nobody meant as a colour.
     *
     * Case and surrounding spaces are forgiven, as [ParentColorChoice.fromStored] forgives them;
     * anything longer than three digits, or with a leading zero, is not a form this app writes.
     */
    fun customHueOf(stored: String?): Int? {
        val code = stored?.trim() ?: return null
        if (!code.startsWith(CUSTOM_PREFIX, ignoreCase = true)) return null
        val digits = code.substring(CUSTOM_PREFIX.length)
        val canonical = digits.length in 1..MAX_DIGITS &&
            digits.all { it in '0'..'9' } &&
            (digits.length == 1 || digits.first() != '0')
        return if (canonical) digits.toInt().takeIf { it < FULL_TURN } else null
    }

    private const val FULL_TURN = 360
    private const val MAX_DIGITS = 3
}

/**
 * The hues a custom colour may take, and the slider that picks one.
 *
 * Everything but the calendar friend's teal: teal identifies a friend and never a parent
 * (CLAUDE.md, item 16), so a parent who could pick it would make a grandmother's events look like
 * theirs. The band is [RESERVED]; the slider skips it rather than refusing it, so its track simply
 * runs from green straight to sky blue and no position can land there.
 *
 * A hue stored inside the band anyway — written by hand, or by a client that does not know the
 * rule — is drawn at the nearer edge ([allowed]); nothing is rewritten in storage.
 */
object CustomHueRange {

    /** `FriendTeal` sits at 173°; its light partner at 174°. Fifteen degrees either side. */
    val RESERVED: IntRange = 158..188

    private const val FULL_TURN = 360
    private val reservedSize = RESERVED.last - RESERVED.first + 1
    private val lastIndex = FULL_TURN - reservedSize - 1

    /** The hue at [position] along the slider, 0 at the start and 1 at the end. */
    fun hueAt(position: Float): Int {
        val index = (position.coerceIn(0f, 1f) * lastIndex).roundToInt()
        return if (index < RESERVED.first) index else index + reservedSize
    }

    /** Where [hue] sits along the slider; a reserved hue sits where [allowed] moves it. */
    fun positionOf(hue: Int): Float {
        val allowedHue = allowed(hue)
        val index = if (allowedHue < RESERVED.first) allowedHue else allowedHue - reservedSize
        return index / lastIndex.toFloat()
    }

    /** [hue] on the wheel, moved to the nearer edge of [RESERVED] when it falls inside it. */
    fun allowed(hue: Int): Int {
        val onWheel = hue.mod(FULL_TURN)
        if (onWheel !in RESERVED) return onWheel
        val middle = (RESERVED.first + RESERVED.last) / 2
        return if (onWheel <= middle) RESERVED.first - 1 else RESERVED.last + 1
    }
}
