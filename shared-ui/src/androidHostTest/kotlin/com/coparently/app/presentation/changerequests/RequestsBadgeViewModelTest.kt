package com.coparently.app.presentation.changerequests

import app.cash.turbine.test
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.Waiting
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The Home tab's badge counts what Home's Requests row counts, and nothing while the requests are
 * not known (redesign, part 2a).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsBadgeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val state = MutableStateFlow<RequestsState>(RequestsState.Loading)
    private val source = mockk<RequestsSource> { every { observe() } returns state }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ready(waiting: Waiting) = RequestsState.Ready(
        scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = true),
        inbox = RequestInbox(waitingOnMe = waiting),
        changeRequests = emptyList(),
        events = emptyList()
    )

    @Test
    fun `badge is zero while loading and with no co-parent`() = runTest(dispatcher) {
        RequestsBadgeViewModel(source).waitingOnMe.test {
            assertEquals(0, awaitItem())
            state.value = RequestsState.NoCoParent
            expectNoEvents()
        }
    }

    @Test
    fun `badge follows the waiting on me count - a split proposal alone is one`() = runTest(dispatcher) {
        RequestsBadgeViewModel(source).waitingOnMe.test {
            assertEquals(0, awaitItem())
            state.value = ready(Waiting(split = SplitRatioProposal(SplitRatio(6000), "u2", 1L)))
            assertEquals(1, awaitItem())
            state.value = ready(Waiting())
            assertEquals(0, awaitItem())
        }
    }
}
