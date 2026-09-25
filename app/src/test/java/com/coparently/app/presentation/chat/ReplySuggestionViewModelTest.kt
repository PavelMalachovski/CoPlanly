package com.coparently.app.presentation.chat

import com.coparently.app.R
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.domain.ai.AI_CONSENT_VERSION
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * "Suggest a reply": nothing is sent before a consent, the draft is handed to the composer and
 * never sent, a refusal for want of consent asks again, and every other refusal is worded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplySuggestionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var consent: AiConsent? = null
    private val consentManager = mockk<AiConsentManager>(relaxed = true) {
        coEvery { current() } answers { consent }
        coEvery { grant() } answers {
            consent = AiConsent(AI_CONSENT_VERSION, null)
            true
        }
        every { forget() } answers { consent = null }
    }
    private val repository = mockk<AiAssistRepository> {
        coEvery { suggestReply(any(), any(), any()) } returns AiAssistResult.Text("Friday works for me.")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(enabled: Boolean = true) =
        ReplySuggestionViewModel(AiAssistAvailability(enabled), consentManager, repository)

    @Test
    fun `without a consent the dialog opens and nothing is sent`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.suggest(THREAD, "cs", draftHint = "")
        advanceUntilIdle()

        assertEquals(AiAssistState.AskingConsent, vm.state.value)
        coVerify(exactly = 0) { repository.suggestReply(any(), any(), any()) }
    }

    @Test
    fun `cancelling the dialog records and sends nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.suggest(THREAD, "cs", draftHint = null)
        advanceUntilIdle()

        vm.decline()
        advanceUntilIdle()

        assertEquals(AiAssistState.Idle, vm.state.value)
        coVerify(exactly = 0) { consentManager.grant() }
        coVerify(exactly = 0) { repository.suggestReply(any(), any(), any()) }
    }

    @Test
    fun `agreeing records the consent, then the draft arrives for the composer`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.suggest(THREAD, "de-AT", draftHint = "Hi")
        advanceUntilIdle()

        vm.agree()
        advanceUntilIdle()

        coVerify(exactly = 1) { consentManager.grant() }
        coVerify(exactly = 1) { repository.suggestReply(THREAD, "de-AT", "Hi") }
        assertEquals("Friday works for me.", vm.suggestion.value)
        assertEquals(AiAssistState.Idle, vm.state.value)

        vm.consumeSuggestion()
        assertNull(vm.suggestion.value)
    }

    @Test
    fun `with a consent the request goes straight out`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        val vm = viewModel()

        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        assertEquals("Friday works for me.", vm.suggestion.value)
    }

    @Test
    fun `a consent to an older wording is asked again`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION - 1, 1L)
        val vm = viewModel()

        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        assertEquals(AiAssistState.AskingConsent, vm.state.value)
    }

    @Test
    fun `a consent the server does not hold is asked for again, not reported`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        coEvery { repository.suggestReply(any(), any(), any()) } returns AiAssistResult.ConsentRequired
        val vm = viewModel()

        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        assertEquals(AiAssistState.AskingConsent, vm.state.value)
        io.mockk.verify { consentManager.forget() }
    }

    @Test
    fun `a refusal is worded and the row can be tapped again`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        coEvery { repository.suggestReply(any(), any(), any()) } returns AiAssistResult.EmptyThread
        val vm = viewModel()

        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        assertEquals(AiAssistState.Failed(UiText.Res(R.string.ai_error_empty_thread)), vm.state.value)
        assertNull(vm.suggestion.value)
        vm.dismissFailure()
        assertEquals(AiAssistState.Idle, vm.state.value)
    }

    @Test
    fun `a consent that could not be saved is said, and nothing is sent`() = runTest(dispatcher) {
        coEvery { consentManager.grant() } returns false
        val vm = viewModel()
        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        vm.agree()
        advanceUntilIdle()

        assertEquals(AiAssistState.Failed(UiText.Res(R.string.ai_consent_not_saved)), vm.state.value)
        coVerify(exactly = 0) { repository.suggestReply(any(), any(), any()) }
    }

    @Test
    fun `closing the sheet abandons the request`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        val vm = viewModel()

        vm.suggest(THREAD, "en", draftHint = null)
        vm.cancel()
        advanceUntilIdle()

        assertEquals(AiAssistState.Idle, vm.state.value)
        assertNull(vm.suggestion.value)
    }

    @Test
    fun `with the assist off nothing happens at all`() = runTest(dispatcher) {
        val vm = viewModel(enabled = false)

        vm.suggest(THREAD, "en", draftHint = null)
        advanceUntilIdle()

        assertFalse(vm.available)
        assertEquals(AiAssistState.Idle, vm.state.value)
        coVerify(exactly = 0) { consentManager.current() }
        coVerify(exactly = 0) { repository.suggestReply(any(), any(), any()) }
    }

    private companion object {
        const val THREAD = "uid-alice_uid-bob"
    }
}
