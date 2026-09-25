package com.coparently.app.presentation.sync

import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The start-destination decision: signed in or not, and whether the first-run questionnaire
 * still has to run. Null must never resolve to Home by accident, and only records this account
 * created count as evidence it has been through the wizard (CLAUDE.md item 22).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthStateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = LocalDateTime.parse("2026-09-01T09:00:00")

    private val firebaseUser = mockk<FirebaseUser> { every { uid } returns "u1" }
    private val firebaseAuthService = mockk<FirebaseAuthService>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val childInfoRepository = mockk<ChildInfoRepository>()
    private val petRepository = mockk<PetRepository>()
    private val fcmService = mockk<FcmService>(relaxed = true)
    private val schoolConnections = mockk<SchoolConnectionStore>(relaxed = true)

    private val namedAccount = User(id = "u1", email = "olya@example.com", name = "Olya", role = "mom", colorCode = "")

    private fun viewModel() = AuthStateViewModel(
        firebaseAuthService = firebaseAuthService,
        userRepository = userRepository,
        childInfoRepository = childInfoRepository,
        petRepository = petRepository,
        fcmService = fcmService,
        schoolConnections = schoolConnections
    )

    private fun child(createdBy: String?) = ChildInfo(
        id = "c1",
        childName = "Anna",
        dateOfBirth = null,
        createdAt = now,
        updatedAt = now,
        createdByFirebaseUid = createdBy
    )

    private fun stubRecords(children: List<ChildInfo> = emptyList(), pets: List<Pet> = emptyList()) {
        every { childInfoRepository.getAllChildInfo() } returns flowOf(children)
        every { petRepository.getAllPets() } returns flowOf(pets)
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
    fun `nobody signed in resolves to signed out, with nothing to onboard`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.waitForAuthReady(any()) } returns null

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.isAuthenticated.value)
        assertEquals(false, vm.needsOnboarding.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `waits for the account row rather than reading it once`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.waitForAuthReady(any()) } returns firebaseUser
        // Right after a sign-up the row is still being written: a single read would see null and
        // send a brand-new parent straight past the wizard.
        every { userRepository.observeUserById("u1") } returns flow {
            emit(null)
            emit(namedAccount.copy(name = ""))
        }
        stubRecords()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.isAuthenticated.value)
        assertEquals(true, vm.needsOnboarding.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `the co-parent's children are not evidence that this account was onboarded`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.waitForAuthReady(any()) } returns firebaseUser
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        stubRecords(children = listOf(child(createdBy = "partner")))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.needsOnboarding.value)
    }

    @Test
    fun `a named account with its own pet is not sent back through the wizard`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.waitForAuthReady(any()) } returns firebaseUser
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        val ownPet = Pet(id = "p1", name = "Rex", createdAt = now, updatedAt = now, createdByFirebaseUid = "u1")
        stubRecords(pets = listOf(ownPet))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.needsOnboarding.value)
    }

    @Test
    fun `sign-out drops the push token before the session ends`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.waitForAuthReady(any()) } returnsMany listOf(firebaseUser, null)
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        stubRecords(children = listOf(child(createdBy = "u1")))
        coEvery { firebaseAuthService.signOutCompletely() } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        coVerifyOrder {
            fcmService.unregisterToken()
            // The school tokens (MON-8) are forgotten with the session, before it ends.
            schoolConnections.clearAll()
            firebaseAuthService.signOutCompletely()
        }
        assertEquals(false, vm.isAuthenticated.value)
    }
}
