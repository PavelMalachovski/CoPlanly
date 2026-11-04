package com.coparently.app.utils

/**
 * Money as the reader writes it, for the shared screens (MON-24 phase 2, Task 11, slice 11b): an
 * amount the app carries as plain decimal text (a chat card's `amount`, "1234.50") in the reader's
 * conventions for its currency — "1 234,50 Kč", "€1,234.50".
 *
 * On Android it is the `java.text.NumberFormat` call the screens made before they moved
 * (`currencyFormat`, in `Locale.getDefault()`, the language the app speaks), so the text is what
 * it was. On the iPhone it is Foundation's currency style in the phone's current locale.
 */
expect object ReaderMoneyText {

    /**
     * [amount] in [currency], or null when [amount] is not a decimal number. Without a currency, the
     * number as plain text ("12.50"); an unknown currency code is written in the locale's own.
     */
    fun amount(amount: String, currency: String?): String?

    /**
     * [value] in [currency], in the reader's conventions: the expense screens' totals, rows and
     * balances, which hold an amount as a `Double` (MON-24 phase 2, slice 11e). On Android
     * `currencyFormat(currency).format(value)`, the call those screens and Home's money tile made, so
     * the text and its rounding are what they were.
     */
    fun amount(value: Double, currency: String): String

    /**
     * [fraction] (0.34) as a whole percent in the reader's conventions, "34 %" or "34%": a share of a
     * month's spend, which nobody reads to a decimal place. On Android `NumberFormat.getPercentInstance`
     * with no fraction digits, as the analytics wrote it.
     */
    fun percent(fraction: Double): String
}
