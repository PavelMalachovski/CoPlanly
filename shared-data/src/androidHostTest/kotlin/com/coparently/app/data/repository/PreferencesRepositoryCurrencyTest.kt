package com.coparently.app.data.repository

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.money.CurrencyHints
import com.coparently.app.data.platform.DeviceRegion
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.domain.money.SupportedCurrency
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

/** Tests for the region-seeded default currency preference. */
class PreferencesRepositoryCurrencyTest {

    private fun prefs(storedCurrency: String?): SecurePreferences = mockk(relaxed = true) {
        every { getDarkTheme() } returns null
        every { getDefaultCurrency() } returns storedCurrency
    }

    private fun hints(country: String?, lastExpense: String?): CurrencyHints = mockk {
        every { countryCode() } returns flowOf(country)
        every { lastExpenseCurrency() } returns flowOf(lastExpense)
    }

    private val noHints = hints(country = null, lastExpense = null)

    /** The phone's own region, as `AndroidDeviceRegion` reads it. */
    private var phoneRegion = ""
    private val phone = DeviceRegion { phoneRegion }

    /**
     * Runs [block] on a phone in [country]. The process's default locale is the English fallback,
     * European English: the guess must come from the phone's region and never from the locale the
     * app speaks, whose region is Europe's ("150") on an English fallback.
     */
    private fun onPhoneIn(country: String, block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en-150"))
        phoneRegion = country
        try {
            block()
        } finally {
            phoneRegion = ""
            Locale.setDefault(original)
        }
    }

    @Test
    fun `seeds the currency from the device region when nothing is stored`() {
        onPhoneIn("CZ") {
            val preferences = prefs(storedCurrency = null)
            val repository = PreferencesRepositoryImpl(preferences, noHints, phone)

            runBlocking {
                assertEquals(SupportedCurrency.CZK, repository.getDefaultCurrencyFlow().first())
            }
            verify { preferences.putDefaultCurrency("CZK") }
        }
    }

    @Test
    fun `a Slovak phone is offered euros though the app speaks European English`() {
        // The owner's rule reads English on a Slovak phone, in en-150, whose region is "150".
        onPhoneIn("SK") {
            val preferences = prefs(storedCurrency = null)
            val repository = PreferencesRepositoryImpl(preferences, noHints, phone)

            runBlocking {
                assertEquals(SupportedCurrency.EUR, repository.getDefaultCurrencyFlow().first())
            }
            verify { preferences.putDefaultCurrency("EUR") }
        }
    }

    @Test
    fun `prefers the stored currency over the device region`() {
        onPhoneIn("CZ") {
            val repository = PreferencesRepositoryImpl(prefs(storedCurrency = "EUR"), noHints, phone)

            runBlocking {
                assertEquals(SupportedCurrency.EUR, repository.getDefaultCurrencyFlow().first())
            }
        }
    }

    @Test
    fun `ignores an unrecognised stored code and reseeds from the region`() {
        onPhoneIn("PL") {
            val repository = PreferencesRepositoryImpl(prefs(storedCurrency = "XYZ"), noHints, phone)

            runBlocking {
                assertEquals(SupportedCurrency.PLN, repository.getDefaultCurrencyFlow().first())
            }
        }
    }

    @Test
    fun `setting the currency persists it and updates the flow`() {
        onPhoneIn("CZ") {
            val preferences = prefs(storedCurrency = null)
            val repository = PreferencesRepositoryImpl(preferences, noHints, phone)

            runBlocking {
                repository.setDefaultCurrency(SupportedCurrency.EUR)
                assertEquals(SupportedCurrency.EUR, repository.getDefaultCurrencyFlow().first())
            }
            verify { preferences.putDefaultCurrency("EUR") }
            verify { preferences.putBoolean(PreferenceKeys.DEFAULT_CURRENCY_CHOSEN, true) }
        }
    }

    @Test
    fun `a suggestion replaces the device region's guess`() {
        // A Czech parent whose phone runs in English (United States): the first default is dollars.
        onPhoneIn("US") {
            val preferences = prefs(storedCurrency = null)
            val repository = PreferencesRepositoryImpl(preferences, noHints, phone)

            runBlocking {
                repository.suggestDefaultCurrency(SupportedCurrency.CZK)
                assertEquals(SupportedCurrency.CZK, repository.getDefaultCurrencyFlow().first())
            }
            verify { preferences.putDefaultCurrency("CZK") }
        }
    }

    @Test
    fun `a suggestion never replaces a currency the parent chose`() {
        onPhoneIn("CZ") {
            val preferences = prefs(storedCurrency = "EUR").also {
                every { it.getBoolean(PreferenceKeys.DEFAULT_CURRENCY_CHOSEN, false) } returns true
            }
            val repository = PreferencesRepositoryImpl(preferences, noHints, phone)

            runBlocking {
                repository.suggestDefaultCurrency(SupportedCurrency.CZK)
                assertEquals(SupportedCurrency.EUR, repository.getDefaultCurrencyFlow().first())
            }
            verify(exactly = 0) { preferences.putDefaultCurrency("CZK") }
        }
    }

    @Test
    fun `the parent's country comes before the device region`() {
        // R-5: a Czech account on an English (United States) phone, never asked to confirm.
        onPhoneIn("US") {
            val repository = PreferencesRepositoryImpl(prefs(storedCurrency = null), hints("CZ", "EUR"), phone)

            runBlocking {
                assertEquals(SupportedCurrency.CZK, repository.getDefaultCurrencyFlow().first())
            }
        }
    }

    @Test
    fun `a country without an offered currency falls to the last expense`() {
        onPhoneIn("US") {
            val repository = PreferencesRepositoryImpl(prefs(storedCurrency = null), hints("UA", "EUR"), phone)

            runBlocking {
                assertEquals(SupportedCurrency.EUR, repository.getDefaultCurrencyFlow().first())
            }
        }
    }

    @Test
    fun `a chosen currency outranks the country`() {
        onPhoneIn("US") {
            val preferences = prefs(storedCurrency = "GBP").also {
                every { it.getBoolean(PreferenceKeys.DEFAULT_CURRENCY_CHOSEN, false) } returns true
            }
            val repository = PreferencesRepositoryImpl(preferences, hints("CZ", "EUR"), phone)

            runBlocking {
                assertEquals(SupportedCurrency.GBP, repository.getDefaultCurrencyFlow().first())
            }
        }
    }

    @Test
    fun `choosing a currency takes over from the country at once`() {
        onPhoneIn("US") {
            val repository = PreferencesRepositoryImpl(prefs(storedCurrency = null), hints("CZ", null), phone)

            runBlocking {
                repository.setDefaultCurrency(SupportedCurrency.CHF)
                assertEquals(SupportedCurrency.CHF, repository.getDefaultCurrencyFlow().first())
            }
        }
    }
}
