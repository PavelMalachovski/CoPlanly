package com.coparently.app.presentation.consent

import app.cash.turbine.test
import com.coparently.app.data.consent.HealthConsentManager
import com.coparently.app.data.consent.HealthConsentWithdrawal
import com.coparently.app.domain.consent.HEALTH_CONSENT_VERSION
import com.coparently.app.domain.consent.HealthConsent
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The child-health consent gate: a child's medical section is locked without a consent, unlocked
 * by one to the dialog as worded today, and locked again by one to an older wording.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthConsentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val stored = MutableStateFlow<HealthConsent?>(null)
    private val manager = mockk<HealthConsentManager> {
        every { observe() } returns stored
        coEvery { grant() } coAnswers {
            HealthConsent(HEALTH_CONSENT_VERSION, AGREED_AT).also { stored.value = it }
        }
        coEvery { withdraw() } coAnswers {
            stored.value = null
            HealthConsentWithdrawal.WITHDRAWN
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
    fun `no consent keeps the medical section locked`() = runTest(dispatcher) {
        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        assertFalse(vm.unlocked.value)
    }

    @Test
    fun `a consent to the current wording unlocks it`() = runTest(dispatcher) {
        stored.value = HealthConsent(HEALTH_CONSENT_VERSION, AGREED_AT)

        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        assertTrue(vm.unlocked.value)
    }

    @Test
    fun `a consent to an older wording locks it again`() = runTest(dispatcher) {
        stored.value = HealthConsent(HEALTH_CONSENT_VERSION - 1, AGREED_AT)

        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        assertFalse(vm.unlocked.value)
        // Still shown in Settings, so the details entered under it can be withdrawn.
        assertEquals(HEALTH_CONSENT_VERSION - 1, vm.consent.value?.version)
    }

    @Test
    fun `asking opens the dialog and not now closes it without recording anything`() =
        runTest(dispatcher) {
            val vm = HealthConsentViewModel(manager)
            advanceUntilIdle()

            vm.ask()
            assertTrue(vm.asking.value)
            vm.notNow()
            advanceUntilIdle()

            assertFalse(vm.asking.value)
            assertFalse(vm.unlocked.value)
            coVerify(exactly = 0) { manager.grant() }
        }

    @Test
    fun `agreeing records the consent and unlocks the section`() = runTest(dispatcher) {
        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        vm.ask()
        vm.agree()
        advanceUntilIdle()

        coVerify(exactly = 1) { manager.grant() }
        assertFalse(vm.asking.value)
        assertTrue(vm.unlocked.value)
        assertEquals(HealthConsent(HEALTH_CONSENT_VERSION, AGREED_AT), vm.consent.value)
    }

    @Test
    fun `withdrawing reports how it ended and locks the section`() = runTest(dispatcher) {
        stored.value = HealthConsent(HEALTH_CONSENT_VERSION, AGREED_AT)
        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        vm.withdrawal.test {
            vm.withdraw()
            assertEquals(HealthConsentWithdrawal.WITHDRAWN, awaitItem())
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { manager.withdraw() }
        assertFalse(vm.withdrawing.value)
        assertFalse(vm.unlocked.value)
    }

    @Test
    fun `a withdrawal that throws is reported as failed rather than crashing`() = runTest(dispatcher) {
        coEvery { manager.withdraw() } throws IllegalStateException("room")
        val vm = HealthConsentViewModel(manager)
        advanceUntilIdle()

        vm.withdrawal.test {
            vm.withdraw()
            assertEquals(HealthConsentWithdrawal.FAILED, awaitItem())
        }
        advanceUntilIdle()

        assertFalse(vm.withdrawing.value)
    }

    private companion object {
        const val AGREED_AT = 1_787_000_000_000L
    }
}
