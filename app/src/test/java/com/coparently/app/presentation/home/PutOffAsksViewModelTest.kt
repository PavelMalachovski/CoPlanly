package com.coparently.app.presentation.home

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * "Later" on Home's asks holds until the ask changes (release audit R-2), and only in the family it
 * was said in (isolation audit, October 2026).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PutOffAsksViewModelTest {

    private val stored = slot<String>()
    private val view = MutableStateFlow(onScreen(BOB_FAMILY))
    private val familyViewSource = mockk<FamilyViewSource> { every { observe() } returns view }

    private fun preferences(initial: String?): EncryptedPreferences = mockk(relaxed = true) {
        every { getString(PreferenceKeys.PUT_OFF_ASKS, null) } answers {
            if (stored.isCaptured) stored.captured else initial
        }
        every { putString(PreferenceKeys.PUT_OFF_ASKS, capture(stored)) } returns Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a put-off ask survives a new view model`() {
        val prefs = preferences(initial = null)
        PutOffAsksViewModel(prefs, familyViewSource).putOff(setOf("swap_a"), waiting = setOf("swap_a"))

        assertEquals(setOf("swap_a"), PutOffAsksViewModel(prefs, familyViewSource).putOff.value)
    }

    @Test
    fun `an ask no longer waiting is forgotten on the next write`() {
        val viewModel = PutOffAsksViewModel(
            preferences(initial = "$BOB_FAMILY/swap_old|$BOB_FAMILY/requests_2"),
            familyViewSource
        )

        viewModel.putOff(setOf("swap_new"), waiting = setOf("swap_new", "requests_2"))

        assertEquals(setOf("requests_2", "swap_new"), viewModel.putOff.value)
        assertEquals(
            setOf("$BOB_FAMILY/requests_2", "$BOB_FAMILY/swap_new"),
            stored.captured.split("|").toSet()
        )
    }

    @Test
    fun `several revisions put off at once are all kept, and answered ones forgotten next time`() {
        val viewModel = PutOffAsksViewModel(preferences(initial = null), familyViewSource)

        val both = setOf("request_change:a", "request_change:b")
        viewModel.putOff(both, waiting = both)
        assertEquals(setOf("request_change:a", "request_change:b"), viewModel.putOff.value)

        // change:a was answered; a swap is put off while change:b still waits.
        viewModel.putOff(setOf("swap_c"), waiting = setOf("request_change:b", "swap_c"))
        assertEquals(setOf("request_change:b", "swap_c"), viewModel.putOff.value)
    }

    @Test
    fun `nothing stored is nothing put off`() {
        assertEquals(emptySet(), PutOffAsksViewModel(preferences(initial = null), familyViewSource).putOff.value)
    }

    @Test
    fun `one family's Later never silences the other family's ask, nor forgets its put-offs`() {
        // Two requests wait in both families: putting off the family with Bob's says nothing about
        // the family with Carol's, and a dismissal there keeps Bob's family's put-off.
        val viewModel = PutOffAsksViewModel(preferences(initial = null), familyViewSource)
        viewModel.putOff(setOf("requests_2"), waiting = setOf("requests_2"))

        view.value = onScreen(CAROL_FAMILY)
        assertEquals(emptySet(), viewModel.putOff.value)
        viewModel.putOff(setOf("swap_c"), waiting = setOf("swap_c", "requests_2"))
        assertEquals(setOf("swap_c"), viewModel.putOff.value)

        view.value = onScreen(BOB_FAMILY)
        assertEquals(setOf("requests_2"), viewModel.putOff.value)
    }

    @Test
    fun `a put-off stored before families were named is dropped, not shown in either family`() {
        val viewModel = PutOffAsksViewModel(preferences(initial = "requests_2"), familyViewSource)
        assertEquals(emptySet(), viewModel.putOff.value)

        viewModel.putOff(setOf("swap_a"), waiting = setOf("swap_a"))

        assertEquals(setOf("$BOB_FAMILY/swap_a"), stored.captured.split("|").toSet())
    }

    private fun onScreen(familyId: String) = FamilyView(
        me = "alice",
        onScreen = familyId,
        others = setOf(BOB_FAMILY, CAROL_FAMILY) - familyId
    )

    private companion object {
        const val BOB_FAMILY = "alice__bob"
        const val CAROL_FAMILY = "alice__carol"
    }
}
