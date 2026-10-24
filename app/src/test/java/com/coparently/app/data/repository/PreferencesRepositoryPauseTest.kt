package com.coparently.app.data.repository

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.money.CurrencyHints
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Pause before sending" is on for a parent who never chose (new-user review, October 2026: the
 * one tool a high-conflict pair needs most sat switched off between Currency and Theme), and a
 * parent who switched it off keeps it off.
 */
class PreferencesRepositoryPauseTest {

    private val hints: CurrencyHints = mockk {
        every { countryCode() } returns flowOf(null)
        every { lastExpenseCurrency() } returns flowOf(null)
    }

    /** A store holding [stored] for the pause, or nothing, in which case a read gets its default. */
    private fun prefs(stored: Boolean?): EncryptedPreferences = mockk(relaxed = true) {
        every { getDarkTheme() } returns null
        every { getDefaultCurrency() } returns "CZK"
        every { getBoolean(PreferenceKeys.CHAT_PAUSE_BEFORE_SENDING, any()) } answers { stored ?: secondArg() }
    }

    @Test
    fun `pause before sending is on for a parent who never set it`() {
        val repository = PreferencesRepositoryImpl(prefs(stored = null), hints)

        runBlocking { assertTrue(repository.getPauseBeforeSendingFlow().first()) }
    }

    @Test
    fun `a stored off stays off`() {
        val repository = PreferencesRepositoryImpl(prefs(stored = false), hints)

        runBlocking { assertFalse(repository.getPauseBeforeSendingFlow().first()) }
    }
}
