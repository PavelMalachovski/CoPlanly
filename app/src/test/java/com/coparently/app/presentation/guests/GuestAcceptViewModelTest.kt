package com.coparently.app.presentation.guests

import com.coparently.app.R
import com.coparently.app.data.remote.firebase.AcceptGuestResult
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.repository.GuestRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Redeeming a guest invitation: a link never overwrites what is being typed, and each failure a
 * guest can act on gets its own sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuestAcceptViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<GuestRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a deep link fills an empty field`() {
        val vm = GuestAcceptViewModel(repository)

        vm.prefill("coplanly://guest?code=ABC234")

        assertEquals("ABC234", vm.uiState.value.code)
    }

    @Test
    fun `a deep link never overwrites a code being typed`() {
        val vm = GuestAcceptViewModel(repository)
        vm.onCodeChanged(" xyz789 ")

        vm.prefill("coplanly://guest?code=ABC234")

        assertEquals("XYZ789", vm.uiState.value.code)
    }

    @Test
    fun `an accepted invitation reports when the access ends`() = runTest(dispatcher) {
        coEvery { repository.acceptGuestInvite("ABC234") } returns
            Result.success(AcceptGuestResult(childInfoId = "c1", expiresAtMillis = 1_800_000_000_000L))
        val vm = GuestAcceptViewModel(repository)
        vm.onCodeChanged("ABC234")

        vm.accept()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1_800_000_000_000L, state.acceptedUntilMillis)
        assertFalse(state.isBusy)
        assertNull(state.errorRes)
    }

    @Test
    fun `an ended grant says so rather than that the code did not work`() = runTest(dispatcher) {
        coEvery { repository.acceptGuestInvite(any()) } returns
            Result.failure(PairingException(PairingError.GrantEnded))
        val vm = GuestAcceptViewModel(repository)
        vm.onCodeChanged("ABC234")

        vm.accept()
        advanceUntilIdle()

        assertEquals(R.string.guest_error_grant_ended, vm.uiState.value.errorRes)
        assertFalse(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.acceptedUntilMillis)
    }

    @Test
    fun `an unexpected failure falls back to the generic sentence`() = runTest(dispatcher) {
        coEvery { repository.acceptGuestInvite(any()) } returns Result.failure(IllegalStateException("boom"))
        val vm = GuestAcceptViewModel(repository)
        vm.onCodeChanged("ABC234")

        vm.accept()
        advanceUntilIdle()

        assertEquals(R.string.pairing_error_unknown, vm.uiState.value.errorRes)
    }

    @Test
    fun `nothing is sent without a code`() = runTest(dispatcher) {
        val vm = GuestAcceptViewModel(repository)

        vm.accept()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.acceptGuestInvite(any()) }
    }
}
