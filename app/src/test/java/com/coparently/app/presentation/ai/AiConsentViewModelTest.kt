package com.coparently.app.presentation.ai

import app.cash.turbine.test
import com.coparently.app.R
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.domain.ai.AI_CONSENT_VERSION
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The Settings row for the AI-assist consent: it shows what is stored, turning it off withdraws
 * it and says how that ended, and with the assist off the row reads nothing at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiConsentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val stored = MutableStateFlow<AiConsent?>(AiConsent(AI_CONSENT_VERSION, GRANTED_AT))
    private var withdrawSucceeds = true
    private val manager = mockk<AiConsentManager> {
        every { observe() } returns stored
        coEvery { withdraw() } answers {
            if (withdrawSucceeds) stored.value = null
            withdrawSucceeds
        }
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
    fun `the row shows the stored consent`() = runTest(dispatcher) {
        val vm = AiConsentViewModel(AiAssistAvailability(true), manager)
        advanceUntilIdle()

        assertEquals(AiConsent(AI_CONSENT_VERSION, GRANTED_AT), vm.consent.value)
    }

    @Test
    fun `turning it off withdraws the consent and says so`() = runTest(dispatcher) {
        val vm = AiConsentViewModel(AiAssistAvailability(true), manager)
        advanceUntilIdle()

        vm.messages.test {
            vm.withdraw()
            advanceUntilIdle()
            assertEquals(UiText.Res(R.string.ai_consent_withdrawn), awaitItem())
        }
        assertNull(vm.consent.value)
        assertFalse(vm.withdrawing.value)
    }

    @Test
    fun `a withdrawal that did not land is reported and the consent stays`() = runTest(dispatcher) {
        withdrawSucceeds = false
        val vm = AiConsentViewModel(AiAssistAvailability(true), manager)
        advanceUntilIdle()

        vm.messages.test {
            vm.withdraw()
            advanceUntilIdle()
            assertEquals(UiText.Res(R.string.ai_consent_withdraw_failed), awaitItem())
        }
        assertEquals(AI_CONSENT_VERSION, vm.consent.value?.version)
    }

    @Test
    fun `with the assist off the row reads and writes nothing`() = runTest(dispatcher) {
        val vm = AiConsentViewModel(AiAssistAvailability(false), manager)
        advanceUntilIdle()

        vm.withdraw()
        advanceUntilIdle()

        assertFalse(vm.available)
        assertNull(vm.consent.value)
        verify(exactly = 0) { manager.observe() }
        coVerify(exactly = 0) { manager.withdraw() }
    }

    private companion object {
        const val GRANTED_AT = 1_790_000_000_000L
    }
}
