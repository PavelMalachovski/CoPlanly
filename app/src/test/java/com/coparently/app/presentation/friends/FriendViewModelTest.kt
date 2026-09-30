package com.coparently.app.presentation.friends

import com.coparently.app.R
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.friends.FriendRole
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.repository.FriendRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * The calendar friend's two ends (item 16): a parent revoking access, and the friend redeeming a
 * code and saving their own profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val grant = CalendarFriendGrant(
        friendUid = "f1",
        name = "Grandma",
        familyParents = listOf("u1", "u2"),
        grantedBy = "u1",
        grantedAtMillis = 1L,
        expiresAtMillis = 2L
    )
    private val repository = mockk<FriendRepository> {
        every { observeFamilyFriends() } returns flowOf(emptyList())
        every { observeMyGrants() } returns flowOf(listOf(grant))
        every { observeMyProfile() } returns flowOf(null)
        coEvery { myGrants() } returns listOf(grant)
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
    fun `a refused revoke is reported as a failure, so the screen stays open`() = runTest(dispatcher) {
        coEvery { repository.revokeFriend("f1") } returns Result.failure(IllegalStateException("denied"))
        val vm = FriendViewModel(repository)
        var result: Boolean? = null

        vm.revoke("f1") { result = it }
        advanceUntilIdle()

        assertEquals(false, result)
    }

    @Test
    fun `a revoke that landed is reported as success`() = runTest(dispatcher) {
        coEvery { repository.revokeFriend("f1") } returns Result.success(Unit)
        val vm = FriendViewModel(repository)
        var result: Boolean? = null

        vm.revoke("f1") { result = it }
        advanceUntilIdle()

        assertEquals(true, result)
    }

    @Test
    fun `a profile saved where nothing collects the grant still carries the gate`() = runTest(dispatcher) {
        // Item 17: `myGrants` is WhileSubscribed and the profile screen never collects it, so
        // reading its `.value` here would send an empty `familyParents` — the read gate itself.
        val saved = slot<FriendProfile>()
        coEvery { repository.saveMyProfile(capture(saved)) } returns Result.success(Unit)
        val vm = FriendViewModel(repository)

        vm.saveProfile(
            name = "  Grandma  ",
            role = FriendRole.GRANDPARENT,
            phones = listOf(" 123 ", "  "),
            bloodGroup = " ",
            photoUrl = null
        )
        advanceUntilIdle()

        assertEquals(listOf("u1", "u2"), saved.captured.familyParents)
        assertEquals("Grandma", saved.captured.name)
        assertEquals(listOf("123"), saved.captured.phones)
        assertNull(saved.captured.bloodGroup)
    }

    @Test
    fun `a friend of two families creates a profile both families can read`() = runTest(dispatcher) {
        // L-5: one grant per family. The gate is every parent of every family, each once, oldest
        // grant first — a profile gated on the first grant alone would hide the friend's name and
        // phone number from the second family that admitted her.
        val second = grant.copy(
            familyParents = listOf("u3", "u4"),
            familyId = "u3__u4",
            grantedAtMillis = 5L
        )
        coEvery { repository.myGrants() } returns listOf(second, grant)
        val saved = slot<FriendProfile>()
        coEvery { repository.saveMyProfile(capture(saved)) } returns Result.success(Unit)
        val vm = FriendViewModel(repository)

        vm.saveProfile("Grandma", FriendRole.GRANDPARENT, emptyList(), null, null)
        advanceUntilIdle()

        assertEquals(listOf("u1", "u2", "u3", "u4"), saved.captured.familyParents)
    }

    @Test
    fun `a friend's grants in every family are exposed, not only one`() = runTest(dispatcher) {
        val second = grant.copy(familyParents = listOf("u3", "u4"), familyId = "u3__u4")
        every { repository.observeMyGrants() } returns flowOf(listOf(grant, second))
        val vm = FriendViewModel(repository)
        backgroundScope.launch { vm.myGrants.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(grant, second), vm.myGrants.value)
    }

    @Test
    fun `a re-save keeps the gate the stored profile already holds`() = runTest(dispatcher) {
        // The update rule requires `familyParents` to equal the stored value; a grant that has
        // since changed must not be preferred over it.
        every { repository.observeMyProfile() } returns flowOf(
            FriendProfile(uid = "f1", name = "Grandma", familyParents = listOf("u1", "u3"))
        )
        val saved = slot<FriendProfile>()
        coEvery { repository.saveMyProfile(capture(saved)) } returns Result.success(Unit)
        val vm = FriendViewModel(repository)
        backgroundScope.launch { vm.myProfile.collect {} }
        advanceUntilIdle()

        vm.saveProfile("Grandma", FriendRole.GRANDPARENT, emptyList(), null, null)
        advanceUntilIdle()

        assertEquals(listOf("u1", "u3"), saved.captured.familyParents)
    }

    @Test
    fun `a parent's code offered as a friend code is named, not a generic failure`() = runTest(dispatcher) {
        coEvery { repository.acceptFriendInvite("ABC234") } returns
            Result.failure(PairingException(PairingError.NotFriendInvitation))
        val vm = FriendViewModel(repository)

        vm.updateCode("abc234")
        vm.redeemCode()
        advanceUntilIdle()

        val state = vm.redeem.value
        assertEquals("ABC234", state.code)
        assertEquals(R.string.pairing_error_not_friend_invitation, state.errorRes)
        assertFalse(state.isBusy)
        assertFalse(state.accepted)
    }

    @Test
    fun `a failed profile save is surfaced`() = runTest(dispatcher) {
        coEvery { repository.saveMyProfile(any()) } returns
            Result.failure(PairingException(PairingError.Network))
        val vm = FriendViewModel(repository)

        vm.saveProfile("Grandma", FriendRole.FRIEND, emptyList(), null, null)
        advanceUntilIdle()

        assertEquals(R.string.pairing_error_network, vm.saveError.value)
        vm.clearSaveError()
        assertNull(vm.saveError.value)
    }
}
