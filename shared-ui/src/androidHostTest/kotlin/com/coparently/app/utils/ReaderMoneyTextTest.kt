package com.coparently.app.utils

import com.coparently.app.presentation.expenses.currencyFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * The shared screens' money seam writes, on Android, what the chat's money cards wrote before they
 * moved (MON-24 phase 2, Task 11, slice 11b): `BigDecimal` through `currencyFormat`, in every
 * language the app ships; and what the expense screens and Home's money tile wrote (slice 11e):
 * a `Double` through `currencyFormat`, and a share through the percent instance.
 */
class ReaderMoneyTextTest {

    private lateinit var saved: Locale

    @Before
    fun saveLocale() {
        saved = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(saved)
    }

    @Test
    fun `an amount in a currency is what currencyFormat wrote`() {
        for (tag in listOf("en-150", "en", "cs", "de", "ru", "uk")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            for (currency in listOf("CZK", "EUR", "UAH", "XYZ")) {
                for (amount in listOf("1234.50", "0.5", "12", "-3.25")) {
                    assertEquals(
                        "$tag $currency $amount",
                        currencyFormat(currency).format(BigDecimal(amount)),
                        ReaderMoneyText.amount(amount, currency)
                    )
                }
            }
        }
    }

    @Test
    fun `without a currency it is the plain number - and no number is null`() {
        assertEquals("12.50", ReaderMoneyText.amount("12.50", null))
        assertEquals("1000", ReaderMoneyText.amount("1e3", null))
        assertNull(ReaderMoneyText.amount("twelve", "EUR"))
        assertNull(ReaderMoneyText.amount("", null))
    }

    @Test
    fun `a Double in a currency and a share are what the expense screens wrote`() {
        for (tag in listOf("en-150", "en", "cs", "de", "ru", "uk")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            for (currency in listOf("CZK", "EUR", "UAH", "XYZ")) {
                for (value in listOf(1234.5, 0.005, 2.675, 12.0, -3.25, 1.0E7)) {
                    assertEquals(
                        "$tag $currency $value",
                        currencyFormat(currency).format(value),
                        ReaderMoneyText.amount(value, currency)
                    )
                }
            }
            for (share in listOf(0.0, 0.335, 0.345, 0.5, 1.0)) {
                val percent = NumberFormat.getPercentInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }
                assertEquals("$tag $share", percent.format(share), ReaderMoneyText.percent(share))
            }
        }
    }
}
