package com.coparently.app.pushwording

/**
 * The plural quantity Android's `getQuantityString` picks for the whole number [count] in
 * [language] (`en`, `cs`, `de`, `ru`, `uk`, the languages the app ships; any other reads as
 * English): CLDR's integer rules, which Android's ICU applies. `many` in Czech is for fractions only,
 * so a whole number never takes it.
 *
 * The iPhone needs it because Kotlin/Native hands `NSBundle`'s localized string back as a plain
 * Kotlin `String`, which drops the `.stringsdict` configuration `NSString.localizedStringWithFormat`
 * would select by; `BundleWordingStrings` therefore selects here and takes the quantity's text from
 * the `.stringsdict` file itself.
 */
internal fun pluralQuantity(language: String, count: Int): String {
    val n = if (count < 0) -count.toLong() else count.toLong()
    val lastDigit = n % TEN
    val lastTwo = n % HUNDRED
    return when (language) {
        "cs" -> when (n) {
            1L -> "one"
            in FEW_DIGITS -> "few"
            else -> "other"
        }
        "ru", "uk" -> when {
            lastDigit == 1L && lastTwo != ELEVEN -> "one"
            lastDigit in FEW_DIGITS && lastTwo !in TEENS_AFTER_ELEVEN -> "few"
            else -> "many"
        }
        else -> if (n == 1L) "one" else "other"
    }
}

private const val TEN = 10L
private const val HUNDRED = 100L
private const val ELEVEN = 11L
private const val TWELVE = 12L
private const val FOURTEEN = 14L
private const val FOUR = 4L

/** 2 to 4: Czech `few`, and the last digit of a Russian or Ukrainian `few`. */
private val FEW_DIGITS = 2L..FOUR

/** 12 to 14: last two digits that keep a Russian or Ukrainian number out of `few`. */
private val TEENS_AFTER_ELEVEN = TWELVE..FOURTEEN
