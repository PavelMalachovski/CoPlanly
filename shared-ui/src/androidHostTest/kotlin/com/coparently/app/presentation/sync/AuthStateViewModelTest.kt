package com.coparently.app.presentation.sync

import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.User
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
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
    private val now = LocalDateTime(2026, 9, 1, 9, 0)

    private val firebaseAuthService = mockk<AuthSession>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val childInfoRepository = mockk<ChildInfoRepository>()
    private val petRepository = mockk<PetRepository>()
    private val fcmService = mockk<PushRegistration>(relaxed = true)
    private val schoolConnections = mockk<SchoolConnectionStore>(relaxed = true)
    private val reminderScheduler = mockk<ReminderScheduler>(relaxed = true)
    private val requestClockScheduler = mockk<RequestClockScheduler>(relaxed = true)
    private val deviceTraces = mockk<AccountTraces>(relaxed = true)

    private val namedAccount = User(id = "u1", email = "olya@example.com", name = "Olya", role = "mom", colorCode = "")

    private fun viewModel() = AuthStateViewModel(
        firebaseAuthService = firebaseAuthService,
        userRepository = userRepository,
        childInfoRepository = childInfoRepository,
        petRepository = petRepository,
        fcmService = fcmService,
        schoolConnections = schoolConnections,
        reminderScheduler = reminderScheduler,
        requestClockScheduler = requestClockScheduler,
        deviceTraces = deviceTraces
    )

    private fun child(createdBy: String?) = ChildInfo(
        id = "c1",
        childName = "Anna",
        dateOfBirth = null,
        createdAt = now,
        updatedAt = now,
        createdByFirebaseUid = createdBy
    )

    /**
     * The records on this phone, in every family. The family on screen's lists are not stubbed: a
     * parent whose own children are in the other family has been through the wizard all the same.
     */
    private fun stubRecords(children: List<ChildInfo> = emptyList(), pets: List<Pet> = emptyList()) {
        every { childInfoRepository.getAllChildInfoInEveryFamily() } returns flowOf(children)
        every { petRepository.getAllPetsInEveryFamily() } returns flowOf(pets)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The server's confirmation of a row that says the wizard is owed; by default it changes
        // nothing, as for a brand-new account whose document holds no marker either.
        coEvery { userRepository.ensureProfile() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nobody signed in resolves to signed out, with nothing to onboard`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns null

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.isAuthenticated.value)
        assertEquals(false, vm.needsOnboarding.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `waits for the account row rather than reading it once`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
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
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        stubRecords(children = listOf(child(createdBy = "partner")))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.needsOnboarding.value)
    }

    @Test
    fun `own children in the family not on screen are evidence all the same`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        stubRecords(children = listOf(child(createdBy = "u1").copy(familyId = "u1__u3")))
        // With the family with u2 on screen, the family with u3's child is not in the scoped list.
        every { childInfoRepository.getAllChildInfo() } returns flowOf(emptyList())
        every { petRepository.getAllPets() } returns flowOf(emptyList())

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.needsOnboarding.value)
    }

    @Test
    fun `a named account with its own pet is not sent back through the wizard`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        val ownPet = Pet(id = "p1", name = "Rex", createdAt = now, updatedAt = now, createdByFirebaseUid = "u1")
        stubRecords(pets = listOf(ownPet))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.needsOnboarding.value)
    }

    @Test
    fun `a row without the marker is confirmed with the server before the wizard runs`() = runTest(dispatcher) {
        // The iPhone's shell walk: the row was seeded while `users/{uid}` could not be read, so it
        // lacks the marker the server holds; the parent finished onboarding long ago.
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        val row = MutableStateFlow<User?>(namedAccount)
        every { userRepository.observeUserById("u1") } returns row
        stubRecords()
        coEvery { userRepository.ensureProfile() } answers {
            row.value = namedAccount.copy(onboardingCompletedAt = "2026-11-01T09:00:00")
        }

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.needsOnboarding.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `a row that already has the marker asks the server nothing`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        every { userRepository.observeUserById("u1") } returns
            flowOf(namedAccount.copy(onboardingCompletedAt = "2026-11-01T09:00:00"))
        stubRecords()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.needsOnboarding.value)
        coVerify(exactly = 0) { userRepository.ensureProfile() }
    }

    @Test
    fun `a server that cannot confirm in time leaves the row's answer standing`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returns "u1"
        every { userRepository.observeUserById("u1") } returns flowOf(namedAccount)
        stubRecords()
        coEvery { userRepository.ensureProfile() } coAnswers { delay(60_000) }

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.needsOnboarding.value)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `sign-out drops the push token before the session ends`() = runTest(dispatcher) {
        coEvery { firebaseAuthService.awaitSignedInUid() } returnsMany listOf("u1", null)
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
            // Room survives sign-out; a reminder left armed would announce the account's titles.
            reminderScheduler.cancelAll()
            // So would a request clock's reminder or end (phase 3a).
            requestClockScheduler.cancelAll()
            firebaseAuthService.signOutCompletely()
        }
        assertEquals(false, vm.isAuthenticated.value)
    }
}
