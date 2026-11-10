package com.coparently.app.presentation.consent

import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.telemetry.TelemetryConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The analytics and crash-reporting answer (REL-5) is stored as the answer given.
 *
 * `UNANSWERED` is a third state on purpose (CLAUDE.md, "Telemetry has exactly one switch"): "said
 * no" and "was never asked" collect the same nothing, but only one of them still owes the user a
 * question — so a refusal must be stored as `DENIED`, never left as the initial value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryConsentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val stored = MutableStateFlow(TelemetryConsent.UNANSWERED)
    private val preferences = mockk<PreferencesRepository> {
        every { getTelemetryConsentFlow() } returns stored
        coEvery { setTelemetryConsent(any()) } coAnswers { stored.value = firstArg() }
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
    fun `the stored answer is shown without anybody subscribing first`() = runTest(dispatcher) {
        stored.value = TelemetryConsent.DENIED

        val vm = TelemetryConsentViewModel(preferences)
        advanceUntilIdle()

        // Eagerly shared: the Settings row must never render "unanswered" for a parent who said no.
        assertEquals(TelemetryConsent.DENIED, vm.consent.value)
    }

    @Test
    fun `agreeing stores GRANTED`() = runTest(dispatcher) {
        val vm = TelemetryConsentViewModel(preferences)
        advanceUntilIdle()

        vm.answer(granted = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setTelemetryConsent(TelemetryConsent.GRANTED) }
        assertEquals(TelemetryConsent.GRANTED, vm.consent.value)
    }

    @Test
    fun `declining stores DENIED, not the unanswered state`() = runTest(dispatcher) {
        stored.value = TelemetryConsent.GRANTED
        val vm = TelemetryConsentViewModel(preferences)
        advanceUntilIdle()

        vm.answer(granted = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setTelemetryConsent(TelemetryConsent.DENIED) }
        assertEquals(TelemetryConsent.DENIED, vm.consent.value)
    }
}
