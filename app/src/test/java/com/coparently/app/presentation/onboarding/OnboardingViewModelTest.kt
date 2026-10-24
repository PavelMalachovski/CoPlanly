package com.coparently.app.presentation.onboarding

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.User
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wizard's rules: four steps, the link first, and the one field that blocks progress — a name
 * to put on the events (new-user review, October 2026). Everything else is collected later, so
 * nothing here may lock a parent out of their calendar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var userRepository: UserRepository
    private lateinit var childInfoRepository: ChildInfoRepository
    private lateinit var petRepository: PetRepository
    private lateinit var familySettingsRepository: FamilySettingsRepository
    private lateinit var pairingRepository: PairingRepository
    private lateinit var custodyModelRepository: CustodyModelRepository
    private lateinit var syncRequester: SyncRequester
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var pairing: MutableStateFlow<PairingState>
    private lateinit var viewModel: OnboardingViewModel

    private val storedUser = User(
        id = "u1",
        email = "olya@example.com",
        name = "",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = "bob"
    )

    private val bob = PartnerSummary(
        id = "bob",
        name = "Bob",
        email = "bob@example.com",
        pairedSinceMillis = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        userRepository = mockk(relaxed = true) {
            coEvery { getCurrentUser() } returns storedUser
            coEvery { getCurrentUserId() } returns "u1"
        }
        childInfoRepository = mockk(relaxed = true) {
            every { getAllChildInfo() } returns flowOf(emptyList())
        }
        petRepository = mockk(relaxed = true) {
            every { getAllPets() } returns flowOf(emptyList())
        }
        familySettingsRepository = mockk(relaxed = true) {
            coEvery { agreedRatioOnScreen() } returns SplitRatio.EVEN
            every { observeSettings() } returns flowOf(null)
        }
        pairing = MutableStateFlow(PairingState.NotPaired())
        pairingRepository = mockk(relaxed = true) {
            every { observePairingState() } returns pairing
        }
        custodyModelRepository = mockk(relaxed = true) {
            every { getActiveModel() } returns flowOf(null)
        }
        syncRequester = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        viewModel = newViewModel()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = OnboardingViewModel(
        userRepository,
        childInfoRepository,
        petRepository,
        familySettingsRepository,
        pairingRepository,
        custodyModelRepository,
        syncRequester,
        preferencesRepository
    )

    /** Walks to [step], answering the one required field on the way. */
    private fun walkTo(step: OnboardingStep) {
        viewModel.updateName("Olya")
        while (viewModel.uiState.value.step != step) viewModel.next()
    }

    private fun child(id: String, name: String, createdBy: String? = null) = ChildInfo(
        id = id,
        childName = name,
        dateOfBirth = null,
        createdAt = LocalDateTime.parse("2026-01-01T09:00:00"),
        updatedAt = LocalDateTime.parse("2026-01-01T09:00:00"),
        createdByFirebaseUid = createdBy
    )

    private fun pet(id: String, name: String, createdBy: String? = null) = Pet(
        id = id,
        name = name,
        species = PetSpecies.CAT,
        createdAt = LocalDateTime.parse("2026-01-01T09:00:00"),
        updatedAt = LocalDateTime.parse("2026-01-01T09:00:00"),
        createdByFirebaseUid = createdBy
    )



    // ---- the order of the walk --------------------------------------------------------------

    @Test
    fun `the co-parent link is the first step, and can be declined with Not now`() = runTest(dispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.CoParent, state.step)
        assertTrue(state.isFirstStep)
        assertTrue(state.canAdvance)
        assertTrue(state.canSkip, "a parent whose co-parent does not use the app yet still gets a calendar")
        assertEquals(CoParentLink.None, state.coParent)
        assertEquals(4, state.stepCount)
    }

    @Test
    fun `notifications are asked for right after the link, and may be declined`() = runTest(dispatcher) {
        viewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.Notifications, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.canAdvance)
        assertTrue(viewModel.uiState.value.canSkip)
    }

    @Test
    fun `a blank name is the only thing that blocks progress`() = runTest(dispatcher) {
        repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
        advanceUntilIdle()
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)

        viewModel.updateName("   ")
        assertFalse(viewModel.uiState.value.canAdvance)
        viewModel.next()
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)

        viewModel.updateName("Olya")
        assertTrue(viewModel.uiState.value.canAdvance)
        viewModel.next()
        assertEquals(OnboardingStep.Custody, viewModel.uiState.value.step)
    }

    @Test
    fun `skipping the schedule finishes, rather than doing nothing`() = runTest(dispatcher) {
        // Leaving the custody schedule unset is a supported outcome: it is reachable from the
        // Family tab, and a calendar with no pattern is still a calendar.
        walkTo(OnboardingStep.Custody)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSkip)

        viewModel.skip()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `back never leaves the wizard from its first step`() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.back()
        advanceUntilIdle()
        assertEquals(OnboardingStep.CoParent, viewModel.uiState.value.step)
    }

    // ---- the co-parent link -------------------------------------------------------------------

    @Test
    fun `linking asks for a sync and reports what came across`() = runTest(dispatcher) {
        val children = MutableStateFlow<List<ChildInfo>>(emptyList())
        every { childInfoRepository.getAllChildInfo() } returns children
        // The ViewModel from setUp keeps observing the same pairing flow and would ask for a
        // sync of its own; a fresh requester counts only this instance's.
        syncRequester = mockk(relaxed = true)
        viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(CoParentFetch.Idle, viewModel.uiState.value.fetch)

        pairing.value = PairingState.Paired(partner = bob)
        runCurrent()

        assertEquals(CoParentLink.Linked("Bob"), viewModel.uiState.value.coParent)
        assertEquals(CoParentFetch.Running, viewModel.uiState.value.fetch)
        verify(exactly = 1) { syncRequester.requestSyncNow() }

        // Bob's phone widens the audience of his records and this phone's sync brings one down;
        // a record of this parent's own is not counted as Bob's.
        children.value = listOf(child("c1", "Anya", createdBy = "bob"), child("c2", "Mine"))
        advanceUntilIdle()

        val fetch = assertIs<CoParentFetch.Done>(viewModel.uiState.value.fetch)
        assertEquals(1, fetch.found.children)
        assertFalse(fetch.found.isEmpty)
    }

    @Test
    fun `a fetch that finds nothing is a real answer, and asks once more on the way`() = runTest(dispatcher) {
        pairing.value = PairingState.Paired(partner = bob)
        advanceUntilIdle()

        val fetch = assertIs<CoParentFetch.Done>(viewModel.uiState.value.fetch)
        assertTrue(fetch.found.isEmpty, "the co-parent's phone has not synced yet")
        // Once on the transition, once part-way through the wait.
        verify(exactly = 2) { syncRequester.requestSyncNow() }
    }

    @Test
    fun `the fetch runs once, however often the pairing state re-emits`() = runTest(dispatcher) {
        pairing.value = PairingState.Paired(partner = bob)
        advanceUntilIdle()
        pairing.value = PairingState.Paired(partner = bob, incoming = emptyList())
        pairing.value = PairingState.Loading
        pairing.value = PairingState.Paired(partner = bob.copy(name = "Robert"))
        advanceUntilIdle()

        verify(exactly = 2) { syncRequester.requestSyncNow() }
        assertEquals(CoParentLink.Linked("Robert"), viewModel.uiState.value.coParent)
    }

    @Test
    fun `the co-parent step loses its Not now once there is a co-parent`() = runTest(dispatcher) {
        pairing.value = PairingState.Paired(partner = bob)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.CoParent, state.step)
        assertTrue(state.canAdvance)
        assertFalse(state.canSkip, "Not now would be declining a link that already exists")
    }

    @Test
    fun `a pet of the co-parent's and their split are reported too`() = runTest(dispatcher) {
        every { petRepository.getAllPets() } returns flowOf(listOf(pet("p1", "Max", createdBy = "bob")))
        every { familySettingsRepository.observeSettings() } returns flowOf(
            FamilySettings(
                ratio = SplitRatio.ofMomPercent(SIXTY),
                participants = listOf("bob", "u1"),
                lastModifiedBy = "bob",
                lastModifiedAtMillis = 1L
            )
        )
        viewModel = newViewModel()
        pairing.value = PairingState.Paired(partner = bob)
        advanceUntilIdle()

        val fetch = assertIs<CoParentFetch.Done>(viewModel.uiState.value.fetch)
        assertEquals(1, fetch.found.pets)
        assertTrue(fetch.found.hasSplitAgreement)
    }

    @Test
    fun `a custody schedule already on the phone is reported`() = runTest(dispatcher) {
        every { custodyModelRepository.getActiveModel() } returns flowOf(
            CustodyModel(
                id = "m1",
                modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
                patternDays = 14,
                momDayIndices = (0..6).toSet(),
                startDate = LocalDate.of(2026, 1, 5)
            )
        )
        viewModel = newViewModel()
        pairing.value = PairingState.Paired(partner = bob)
        advanceUntilIdle()

        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, viewModel.uiState.value.custodyType)
        val fetch = assertIs<CoParentFetch.Done>(viewModel.uiState.value.fetch)
        assertTrue(fetch.found.hasCustodySchedule)
    }

    // ---- the profile ---------------------------------------------------------------------------

    @Test
    fun `the profile write goes onto a freshly read row, so a stale pairing is never resurrected`() =
        runTest(dispatcher) {
            // Let the wizard's own prefill read land first, against the row as it then was.
            advanceUntilIdle()

            // The account unpairs while the wizard is open: the row this wizard loaded on init
            // said `partnerId = "bob"`, the row it is about to write onto says null.
            coEvery { userRepository.getCurrentUser() } returns storedUser.copy(partnerId = null)

            repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
            viewModel.updateName("Olya")
            viewModel.next()
            advanceUntilIdle()

            val saved = mutableListOf<User>()
            coVerify { userRepository.updateUser(capture(saved)) }
            val profileWrite = saved.last()
            assertEquals("Olya", profileWrite.name)
            assertNull(profileWrite.partnerId, "a held snapshot would have put the pairing back")
        }

    @Test
    fun `the profile step no longer touches the date of birth or the phone`() = runTest(dispatcher) {
        // Both left the wizard (they stay on My details); a profile save must keep what is stored.
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(
            dateOfBirth = LocalDate.of(1990, 3, 4),
            phone = "+420 777"
        )
        viewModel = newViewModel()
        advanceUntilIdle()

        repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
        viewModel.updateName("Olya")
        viewModel.next()
        advanceUntilIdle()

        val saved = mutableListOf<User>()
        coVerify { userRepository.updateUser(capture(saved)) }
        assertEquals(LocalDate.of(1990, 3, 4), saved.last().dateOfBirth)
        assertEquals("+420 777", saved.last().phone)
    }

    @Test
    fun `confirming the country on the profile step suggests its currency`() = runTest(dispatcher) {
        advanceUntilIdle()
        repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
        viewModel.updateName("Olya")
        viewModel.updateCountry(HolidayCountry.SLOVAKIA)
        viewModel.next()
        advanceUntilIdle()

        // A suggestion, not a choice: the repository keeps a currency picked in Settings.
        coVerify(exactly = 1) { preferencesRepository.suggestDefaultCurrency(SupportedCurrency.EUR) }
        coVerify(exactly = 0) { preferencesRepository.setDefaultCurrency(any()) }
    }

    @Test
    fun `a profile country whose currency the app does not offer suggests none`() = runTest(dispatcher) {
        advanceUntilIdle()
        repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
        viewModel.updateName("Olya")
        viewModel.updateCountry(HolidayCountry.UKRAINE)
        viewModel.next()
        advanceUntilIdle()

        coVerify(exactly = 0) { preferencesRepository.suggestDefaultCurrency(any()) }
    }

    @Test
    fun `a new account opens on the phone's country when this app has its holidays`() = runTest(dispatcher) {
        withRegion("DE") {
            viewModel = newViewModel()
            advanceUntilIdle()
            assertEquals(HolidayCountry.GERMANY, viewModel.uiState.value.country)
        }
    }

    @Test
    fun `a phone country without a holiday table keeps the stored one`() = runTest(dispatcher) {
        withRegion("US") {
            viewModel = newViewModel()
            advanceUntilIdle()
            assertEquals(HolidayCountry.fromCode(storedUser.countryCode), viewModel.uiState.value.country)
        }
    }

    @Test
    fun `a country this account already chose is never replaced by the phone's`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(countryCode = "SK")
        withRegion("DE") {
            viewModel = newViewModel()
            advanceUntilIdle()
            assertEquals(HolidayCountry.SLOVAKIA, viewModel.uiState.value.country)
        }
    }

    @Test
    fun `an account that already has a name does not have to retype it`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(name = "Olya")
        viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals("Olya", viewModel.uiState.value.name)
    }

    // ---- finishing ---------------------------------------------------------------------------

    @Test
    fun `finishing stamps the marker and only then releases the host`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Custody)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFinished)

        viewModel.finish()
        advanceUntilIdle()

        val saved = mutableListOf<User>()
        coVerify { userRepository.updateUser(capture(saved)) }
        assertTrue(
            saved.last().onboardingCompletedAt?.isNotBlank() == true,
            "the marker must be stamped, or the wizard reappears on the next launch"
        )
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `a step's save and the completion marker cannot overwrite each other`() =
        runTest(dispatcher) {
            // Both follow the same read-modify-write shape against the same row. Interleaved,
            // whichever wrote second would silently drop the other's field - and losing the
            // marker means the wizard reappears on the next launch over data already entered.
            var stored = storedUser
            coEvery { userRepository.getCurrentUser() } answers { stored }
            coEvery { userRepository.updateUser(any()) } answers { stored = firstArg() }

            repeat(STEPS_BEFORE_PROFILE) { viewModel.next() }
            viewModel.updateName("Olya")
            viewModel.next()
            viewModel.finish()
            advanceUntilIdle()

            assertEquals("Olya", stored.name)
            assertTrue(stored.onboardingCompletedAt?.isNotBlank() == true)
        }

    /** Runs [block] with the phone's region set to [region], then puts it back. */
    private fun withRegion(region: String, block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.Builder().setLanguage("en").setRegion(region).build())
        try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private companion object {
        /** The link and notifications: the steps the wizard walks before Profile. */
        const val STEPS_BEFORE_PROFILE = 2

        /** Slot 1's share in the co-parent's agreed split. */
        const val SIXTY = 60
    }
}
