package com.coparently.app.presentation.professionals

import com.coparently.app.R
import com.coparently.app.data.remote.firebase.AcceptProfessionalResult
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.professionals.ProfessionalAccessDuration
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Both ends of professional access (MON-18): a parent inviting, consenting and revoking; a professional redeeming. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfessionalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ProfessionalRepository> {
        every { observeFamilyGrants() } returns flowOf(emptyList())
        every { observeMyGrants() } returns flowOf(emptyList())
        every { currentUid() } returns "b"
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
    fun `the invitation carries the chosen role and an end under the ceiling`() = runTest(dispatcher) {
        val role = slot<ProfessionalRole>()
        val expiry = slot<Long>()
        coEvery { repository.invite(capture(role), capture(expiry)) } returns
            Result.success(GuestInvite("i", "ABC234", "", 1L, 2L))
        val vm = ProfessionalsViewModel(repository)
        val before = System.currentTimeMillis()

        vm.openInvite()
        vm.chooseRole(ProfessionalRole.LAWYER)
        vm.chooseDuration(ProfessionalAccessDuration.HALF_YEAR)
        vm.createInvite()
        advanceUntilIdle()

        assertEquals(ProfessionalRole.LAWYER, role.captured)
        assertTrue(expiry.captured > before)
        assertTrue(expiry.captured < before + 180L * 24 * 60 * 60 * 1000)
        assertEquals("ABC234", vm.invite.value.invite?.code)
    }

    @Test
    fun `role and length cannot change once a code exists`() = runTest(dispatcher) {
        coEvery { repository.invite(any(), any()) } returns Result.success(GuestInvite("i", "ABC234", "", 1L, 2L))
        val vm = ProfessionalsViewModel(repository)
        vm.openInvite()
        vm.createInvite()
        advanceUntilIdle()

        vm.chooseRole(ProfessionalRole.THERAPIST)

        assertEquals(ProfessionalRole.MEDIATOR, vm.invite.value.role)
    }

    @Test
    fun `a consent that landed closes the card`() = runTest(dispatcher) {
        coEvery { repository.consent("g") } returns Result.success(Unit)
        val vm = ProfessionalsViewModel(repository)
        vm.openGrant("g")

        vm.consent("g")
        advanceUntilIdle()

        assertNull(vm.openGrantId.value)
        coVerify(exactly = 1) { repository.consent("g") }
    }

    @Test
    fun `a refused revoke keeps the card open and says so`() = runTest(dispatcher) {
        coEvery { repository.revoke("g") } returns Result.failure(IllegalStateException("denied"))
        val vm = ProfessionalsViewModel(repository)
        vm.openGrant("g")

        vm.revoke("g")
        advanceUntilIdle()

        assertEquals("g", vm.openGrantId.value)
        assertEquals(R.string.professional_action_failed, vm.actionError.value)
    }

    @Test
    fun `a redeemed code is reported, a friend code is refused by name`() = runTest(dispatcher) {
        coEvery { repository.acceptInvite("ABC234") } returns Result.success(AcceptProfessionalResult("g", 5L))
        coEvery { repository.acceptInvite("FRI234") } returns
            Result.failure(PairingException(PairingError.NotProfessionalInvitation))
        val vm = ProfessionalsViewModel(repository)

        vm.updateCode("abc234")
        vm.redeemCode()
        advanceUntilIdle()
        assertTrue(vm.redeem.value.accepted)

        vm.updateCode("FRI234")
        vm.redeemCode()
        advanceUntilIdle()
        assertEquals(R.string.professional_error_not_professional_invitation, vm.redeem.value.errorRes)
    }
}
