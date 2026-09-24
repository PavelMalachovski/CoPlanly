package com.coparently.app.presentation.common

import com.coparently.app.utils.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The offline banner's state (docs/AUDIT-2026-10-design.md D-16): shown only after the network
 * has been gone for the grace period, hidden the moment it is back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val online = MutableStateFlow(true)
    private val monitor = mockk<NetworkMonitor> {
        every { networkStatus } returns online
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `losing the network shows the banner after the grace period and coming back hides it at once`() =
        runTest(dispatcher) {
            val viewModel = ConnectivityViewModel(monitor)
            // WhileSubscribed: something has to be collecting, as the root Scaffold is.
            backgroundScope.launch { viewModel.isOffline.collect {} }
            runCurrent()
            assertFalse(viewModel.isOffline.value)

            online.value = false
            advanceTimeBy(GRACE_MS - 1)
            runCurrent()
            assertFalse(viewModel.isOffline.value, "shown before the grace period ran out")

            advanceTimeBy(2)
            runCurrent()
            assertTrue(viewModel.isOffline.value)

            online.value = true
            runCurrent()
            assertFalse(viewModel.isOffline.value, "still shown after the network came back")
        }

    @Test
    fun `a drop shorter than the grace period never shows the banner`() = runTest(dispatcher) {
        val viewModel = ConnectivityViewModel(monitor)
        backgroundScope.launch { viewModel.isOffline.collect {} }
        runCurrent()

        // A Wi-Fi-to-mobile handover, or a lift ride.
        online.value = false
        advanceTimeBy(GRACE_MS / 2)
        runCurrent()
        online.value = true
        advanceTimeBy(GRACE_MS * 2)
        runCurrent()

        assertFalse(viewModel.isOffline.value)
    }

    private companion object {
        /** Mirrors `ConnectivityViewModel.OFFLINE_GRACE_MS`. */
        const val GRACE_MS = 2_000L
    }
}
