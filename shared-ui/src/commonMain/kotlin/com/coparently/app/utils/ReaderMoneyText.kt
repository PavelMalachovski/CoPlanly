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
}
