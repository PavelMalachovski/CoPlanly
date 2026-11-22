package com.coparently.app.presentation.home

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNull

/**
 * Home's "N new" counts from when this parent last opened the requests from "See all", per family
 * (November 2026, board HomeG1): one family's look never hides the other's news.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsSeenViewModelTest {

    private val store = mutableMapOf<String, String>()
    private val view = MutableStateFlow(onScreen(BOB_FAMILY))
    private val familyViewSource = mockk<FamilyViewSource> { every { observe() } returns view }
    private val preferences: SecurePreferences = mockk(relaxed = true) {
        every { getString(any(), any()) } answers { store[firstArg()] ?: secondArg() }
        every { putString(any(), any()) } answers { store[firstArg()] = secondArg() }
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
    fun `a family never opened has no mark`() {
        assertNull(RequestsSeenViewModel(preferences, familyViewSource).seenAtMillis.value)
    }

    @Test
    fun `a look is stored under the family on screen and read back`() {
        val viewModel = RequestsSeenViewModel(preferences, familyViewSource)

        viewModel.markSeen(nowMillis = 1_000L)

        assertEquals(1_000L, viewModel.seenAtMillis.value)
        assertEquals("1000", store[PreferenceKeys.HOME_REQUESTS_SEEN_PREFIX + BOB_FAMILY])
        assertEquals(1_000L, RequestsSeenViewModel(preferences, familyViewSource).seenAtMillis.value)
    }

    @Test
    fun `another family keeps its own mark`() {
        val viewModel = RequestsSeenViewModel(preferences, familyViewSource)
        viewModel.markSeen(nowMillis = 1_000L)

        view.value = onScreen(CAROL_FAMILY)

        assertNull(viewModel.seenAtMillis.value)
        view.value = onScreen(BOB_FAMILY)
        assertEquals(1_000L, viewModel.seenAtMillis.value)
    }

    @Test
    fun `no family on screen stores nothing`() {
        view.value = FamilyView(me = "alice", onScreen = null, others = emptySet())
        val viewModel = RequestsSeenViewModel(preferences, familyViewSource)

        viewModel.markSeen(nowMillis = 1_000L)

        assertEquals(emptyMap(), store)
        assertNull(viewModel.seenAtMillis.value)
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
