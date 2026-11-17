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
            in 2L..4L -> "few"
            else -> "other"
        }
        "ru", "uk" -> when {
            lastDigit == 1L && lastTwo != 11L -> "one"
            lastDigit in 2L..4L && lastTwo !in 12L..14L -> "few"
            else -> "many"
        }
        else -> if (n == 1L) "one" else "other"
    }
}

private const val TEN = 10L
private const val HUNDRED = 100L
