package com.coparently.app.domain.money

/**
 * Currencies the app offers when logging an expense or a budget.
 *
 * @property code ISO 4217 code stored on [com.coparently.app.domain.model.Expense]
 * @property symbol Short symbol for compact UI where a full code would not fit
 */
enum class SupportedCurrency(val code: String, val symbol: String) {
    CZK("CZK", "Kč"),
    EUR("EUR", "€"),
    PLN("PLN", "zł"),
    USD("USD", "$"),
    GBP("GBP", "£"),
    CHF("CHF", "CHF"),
    HUF("HUF", "Ft"),
    SEK("SEK", "kr"),
    DKK("DKK", "kr"),
    NOK("NOK", "kr");

    companion object {
        /** Used when nothing is stored and the region is unknown; the app's primary market. */
        val DEFAULT: SupportedCurrency = CZK

        /**
         * Resolves a stored ISO code back to an entry.
         *
         * @param code ISO 4217 code, or null when nothing has been stored yet
         * @return The matching entry, or null when the code is unknown
         */
        fun fromCode(code: String?): SupportedCurrency? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Picks a sensible currency for a device region, e.g. Czechia gets crowns and Poland zloty.
 *
 * @param countryCode ISO 3166-1 alpha-2 country code; case-insensitive, may be empty
 * @return The region's currency, or [SupportedCurrency.DEFAULT] when it is not mapped
 */
fun defaultCurrencyForRegion(countryCode: String): SupportedCurrency =
    currencyOfRegion(countryCode) ?: SupportedCurrency.DEFAULT

/**
 * The currency a region pays in, when the app offers it.
 *
 * Unlike [defaultCurrencyForRegion] this does not fall back: a caller replacing one guess with a
 * better one needs to know when there is no better one. Russia and Ukraine, whose holidays the
 * calendar draws (MON-13), are the two such regions the app is used in — it offers neither
 * rouble nor hryvnia.
 *
 * @param countryCode ISO 3166-1 alpha-2 country code; case-insensitive, may be empty
 * @return The region's currency, or null when the app does not offer it
 */
fun currencyOfRegion(countryCode: String): SupportedCurrency? =
    when (countryCode.uppercase()) {
        "CZ" -> SupportedCurrency.CZK
        "PL" -> SupportedCurrency.PLN
        "DE", "ES", "AT", "SK", "FR", "IT", "NL", "BE", "PT", "IE", "FI", "GR" ->
            SupportedCurrency.EUR
        "US" -> SupportedCurrency.USD
        "GB" -> SupportedCurrency.GBP
        "CH" -> SupportedCurrency.CHF
        "HU" -> SupportedCurrency.HUF
        "SE" -> SupportedCurrency.SEK
        "DK" -> SupportedCurrency.DKK
        "NO" -> SupportedCurrency.NOK
        else -> null
    }

/**
 * The default currency for a new expense when the parent has not chosen one (release audit R-5):
 * the country's currency, else the currency of the family's last expense, else [deviceGuess] —
 * the device region's, resolved once and stored.
 *
 * A country whose currency the app does not offer (Russia, Ukraine) falls through to the last
 * expense rather than to the device, because what the family has actually been paying in is the
 * better evidence of the two.
 *
 * @param countryCode The parent's country, ISO 3166-1 alpha-2, or null when unknown
 * @param lastExpenseCurrency The code of the family's most recent expense, or null
 * @param deviceGuess The device region's currency
 */
fun resolveDefaultCurrency(
    countryCode: String?,
    lastExpenseCurrency: String?,
    deviceGuess: SupportedCurrency
): SupportedCurrency =
    countryCode?.let(::currencyOfRegion)
        ?: SupportedCurrency.fromCode(lastExpenseCurrency)
        ?: deviceGuess
