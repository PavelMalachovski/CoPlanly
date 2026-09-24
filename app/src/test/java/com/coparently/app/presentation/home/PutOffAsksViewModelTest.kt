package com.coparently.app.presentation.home

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test
import kotlin.test.assertEquals

/** "Later" on Home's asks holds until the ask changes (release audit R-2). */
class PutOffAsksViewModelTest {

    private val stored = slot<String>()
    private fun preferences(initial: String?): EncryptedPreferences = mockk(relaxed = true) {
        every { getString(PreferenceKeys.PUT_OFF_ASKS, null) } answers {
            if (stored.isCaptured) stored.captured else initial
        }
        every { putString(PreferenceKeys.PUT_OFF_ASKS, capture(stored)) } returns Unit
    }

    @Test
    fun `a put-off ask survives a new view model`() {
        val prefs = preferences(initial = null)
        PutOffAsksViewModel(prefs).putOff("swap_a", waiting = setOf("swap_a"))

        assertEquals(setOf("swap_a"), PutOffAsksViewModel(prefs).putOff.value)
    }

    @Test
    fun `an ask no longer waiting is forgotten on the next write`() {
        val viewModel = PutOffAsksViewModel(preferences(initial = "swap_old|requests_2"))

        viewModel.putOff("swap_new", waiting = setOf("swap_new", "requests_2"))

        assertEquals(setOf("requests_2", "swap_new"), viewModel.putOff.value)
        assertEquals(setOf("requests_2", "swap_new"), stored.captured.split("|").toSet())
    }

    @Test
    fun `nothing stored is nothing put off`() {
        assertEquals(emptySet(), PutOffAsksViewModel(preferences(initial = null)).putOff.value)
    }
}
