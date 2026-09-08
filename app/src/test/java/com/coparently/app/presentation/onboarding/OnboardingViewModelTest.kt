package com.coparently.app.presentation.onboarding

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wizard's rules, which are almost entirely about what a parent is allowed to leave out —
 * and, since the co-parent link moved to the front, about what a second parent is allowed to
 * inherit rather than retype.
 *
 * The questionnaire asks for a great deal — a blood group, hereditary conditions, a phone number
 * for an aunt — and it is collected for the parent's own benefit in an emergency. Data gathered
 * for someone's benefit must not lock them out of their calendar, so the only thing that blocks
 * progress is the one field the app genuinely cannot work without: a name to put on the events.
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
            every { agreedRatioOrDefault() } returns SplitRatio.EVEN
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
        syncRequester
    )

    /** Walks to [step], answering the one required field on the way. */
    private fun walkTo(step: OnboardingStep) {
        viewModel.updateName("Olya")
        while (viewModel.uiState.value.step != step) viewModel.next()
    }

    /** The draft the child step opens with — the wizard always has one to fill in. */
    private fun firstChildId(): String = viewModel.uiState.value.children.first().id

    /** The draft the pet step opens with. */
    private fun firstPetId(): String = viewModel.uiState.value.pets.first().id

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

    private fun settings(momPercent: Int) = FamilySettings(
        ratio = SplitRatio.ofMomPercent(momPercent),
        participants = listOf("bob", "u1"),
        lastModifiedBy = "bob",
        lastModifiedAtMillis = 1L
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
    }

    @Test
    fun `the intro can always be advanced, because it asks for nothing`() = runTest(dispatcher) {
        viewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.Intro, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.canAdvance)
        assertFalse(viewModel.uiState.value.canSkip)
    }

    @Test
    fun `a blank name is the only thing that blocks progress`() = runTest(dispatcher) {
        // The link, the intro, then the family question, then the profile.
        viewModel.next()
        viewModel.next()
        viewModel.next()
        advanceUntilIdle()
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)

        viewModel.updateName("   ")
        assertFalse(viewModel.uiState.value.canAdvance)
        viewModel.next()
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)

        viewModel.updateName("Olya")
        assertTrue(viewModel.uiState.value.canAdvance)
    }

    @Test
    fun `nothing medical is ever required`() = runTest(dispatcher) {
        viewModel.next()
        viewModel.next()
        viewModel.next()
        viewModel.updateName("Olya")
        advanceUntilIdle()

        // No date of birth, no phone, no blood type, no allergies - and still advanceable.
        assertTrue(viewModel.uiState.value.canAdvance)
        assertNull(viewModel.uiState.value.dateOfBirth)
    }

    @Test
    fun `every step after the profile can be skipped outright`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Child)
        advanceUntilIdle()

        listOf(
            OnboardingStep.Child,
            OnboardingStep.Relatives,
            OnboardingStep.Split,
            OnboardingStep.Custody
        ).forEach { expected ->
            assertEquals(expected, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.canSkip, "$expected must be skippable")
            viewModel.skip()
            advanceUntilIdle()
        }
    }

    @Test
    fun `skipping the last step finishes, rather than doing nothing`() = runTest(dispatcher) {
        // Leaving the custody schedule unset is a supported outcome: it is reachable from
        // Settings, and a calendar with no pattern is still a calendar.
        walkTo(OnboardingStep.Custody)
        advanceUntilIdle()

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

        // Bob's phone widens the audience of his records and this phone's sync brings one down.
        children.value = listOf(child("c1", "Anya", createdBy = "bob"))
        advanceUntilIdle()

        val fetch = assertIs<CoParentFetch.Done>(viewModel.uiState.value.fetch)
        assertEquals(1, fetch.found.children)
        assertFalse(fetch.found.isEmpty)
        val draft = viewModel.uiState.value.children.single()
        assertEquals("Anya", draft.name)
        assertTrue(draft.byCoParent, "the step must say whose record this is")
        assertTrue(viewModel.uiState.value.childrenFromCoParent)
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
    fun `records that arrive from the co-parent never overwrite a form the parent has started`() =
        runTest(dispatcher) {
            val children = MutableStateFlow<List<ChildInfo>>(emptyList())
            every { childInfoRepository.getAllChildInfo() } returns children
            viewModel = newViewModel()
            walkTo(OnboardingStep.Child)
            viewModel.updateChildName(firstChildId(), "Mine")
            advanceUntilIdle()

            children.value = listOf(child("c1", "Anya", createdBy = "bob"))
            advanceUntilIdle()

            assertEquals(listOf("Mine"), viewModel.uiState.value.children.map { it.name })
        }

    @Test
    fun `a record left as the co-parent entered it is not written again on Next`() = runTest(dispatcher) {
        val anya = child("c1", "Anya", createdBy = "bob")
        every { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(anya))
        coEvery { childInfoRepository.getChildInfoById("c1") } returns anya
        every { petRepository.getAllPets() } returns flowOf(listOf(pet("p1", "Murka", createdBy = "bob")))
        coEvery { petRepository.getPetById("p1") } returns pet("p1", "Murka", createdBy = "bob")
        viewModel = newViewModel()
        viewModel.setCaresFor(setOf(FamilyKind.CHILDREN, FamilyKind.PETS))
        advanceUntilIdle()

        walkTo(OnboardingStep.Child)
        viewModel.next()
        advanceUntilIdle()
        walkTo(OnboardingStep.Pet)
        viewModel.next()
        advanceUntilIdle()

        // Re-uploading Bob's child under Olya's name would announce an edit that never happened.
        coVerify(exactly = 0) { childInfoRepository.upsertChildInfo(any()) }
        coVerify(exactly = 0) { petRepository.upsertPet(any()) }
    }

    @Test
    fun `an edit to the co-parent's record is written, onto that record`() = runTest(dispatcher) {
        val anya = child("c1", "Anya", createdBy = "bob")
        every { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(anya))
        coEvery { childInfoRepository.getChildInfoById("c1") } returns anya
        val written = mutableListOf<ChildInfo>()
        coEvery { childInfoRepository.upsertChildInfo(capture(written)) } returns Unit
        viewModel = newViewModel()
        advanceUntilIdle()

        walkTo(OnboardingStep.Child)
        viewModel.updateChildAllergies("c1", listOf("peanuts"))
        viewModel.next()
        advanceUntilIdle()

        val write = written.single()
        assertEquals("c1", write.id)
        assertEquals("bob", write.createdByFirebaseUid, "ownership survives an edit by the other parent")
        assertEquals("u1", write.lastModifiedBy)
        assertEquals(listOf("peanuts"), write.allergies)
    }

    @Test
    fun `the family answer is seeded from the co-parent's when this parent never answered`() =
        runTest(dispatcher) {
            pairing.value = PairingState.Paired(partner = bob.copy(caresFor = setOf(FamilyKind.PETS)))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(setOf(FamilyKind.PETS), state.caresFor)
            assertTrue(OnboardingStep.Pet in state.steps)
            assertFalse(OnboardingStep.Child in state.steps)

            // The parent's own tap still wins.
            viewModel.setCaresFor(setOf(FamilyKind.CHILDREN))
            assertEquals(setOf(FamilyKind.CHILDREN), viewModel.uiState.value.caresFor)
        }

    @Test
    fun `this parent's own stored answer wins over the co-parent's`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(caresFor = setOf(FamilyKind.CHILDREN))
        pairing.value = PairingState.Paired(partner = bob.copy(caresFor = setOf(FamilyKind.PETS)))
        viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(setOf(FamilyKind.CHILDREN), viewModel.uiState.value.caresFor)
    }

    @Test
    fun `a custody schedule already on the phone is reported and the custody step names it`() =
        runTest(dispatcher) {
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

    // ---- the split -------------------------------------------------------------------------

    @Test
    fun `the split step writes nothing unless the slider was moved`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Split)
        viewModel.next()
        advanceUntilIdle()

        // The value an unconditional write would have sent: the untouched slider's half each.
        // A concrete value rather than `any()`, because MockK builds a matcher's signature for
        // a value class from a random Int and `SplitRatio` refuses one outside 0..10000.
        coVerify(exactly = 0) { familySettingsRepository.submitRatio(SplitRatio.EVEN) }
    }

    @Test
    fun `a moved slider is submitted as slot 1's share`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Split)
        viewModel.setSplitMyPercent(70)
        viewModel.next()
        advanceUntilIdle()

        // Olya holds slot 1, so her share is the stored one.
        coVerify(exactly = 1) { familySettingsRepository.submitRatio(SplitRatio.ofMomPercent(70)) }
    }

    @Test
    fun `the slider shows this parent's share, whichever slot they hold`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(role = "dad")
        every { familySettingsRepository.agreedRatioOrDefault() } returns SplitRatio.ofMomPercent(70)
        viewModel = newViewModel()
        advanceUntilIdle()

        // The stored figure is slot 1's 70; this parent holds slot 2 and pays 30.
        assertEquals(30, viewModel.uiState.value.splitMyPercent)

        walkTo(OnboardingStep.Split)
        viewModel.setSplitMyPercent(40)
        viewModel.next()
        advanceUntilIdle()

        coVerify(exactly = 1) { familySettingsRepository.submitRatio(SplitRatio.ofMomPercent(60)) }
    }

    @Test
    fun `the split step opens on the pair's agreement and only a change is proposed`() =
        runTest(dispatcher) {
            every { familySettingsRepository.observeSettings() } returns flowOf(settings(momPercent = 70))
            pairing.value = PairingState.Paired(partner = bob)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.splitAgreed)
            assertEquals(70, state.splitMyPercent, "the slider opens on what the pair agreed")
            val fetch = assertIs<CoParentFetch.Done>(state.fetch)
            assertTrue(fetch.found.hasSplitAgreement)

            // Moving the slider back to the agreed value is not a proposal. (A concrete value
            // rather than `any()` — see the untouched-slider test for why.)
            walkTo(OnboardingStep.Split)
            viewModel.setSplitMyPercent(60)
            viewModel.setSplitMyPercent(70)
            viewModel.next()
            advanceUntilIdle()
            coVerify(exactly = 0) { familySettingsRepository.submitRatio(SplitRatio.ofMomPercent(70)) }

            viewModel.back()
            viewModel.setSplitMyPercent(60)
            viewModel.next()
            advanceUntilIdle()
            coVerify(exactly = 1) { familySettingsRepository.submitRatio(SplitRatio.ofMomPercent(60)) }
        }

    // ---- writes ----------------------------------------------------------------------------

    @Test
    fun `the profile write goes onto a freshly read row, so a stale pairing is never resurrected`() =
        runTest(dispatcher) {
            // Let the wizard's own prefill read land first, against the row as it then was.
            advanceUntilIdle()

            // The account unpairs while the wizard is open: the row this wizard loaded on init
            // said `partnerId = "bob"`, the row it is about to write onto says null.
            //
            // Staged by *when* the stored row changes, not by an `andThen` sequence: the latter
            // silently depends on the wizard making exactly one read before this one, so a
            // second read added anywhere in `init` would hand the save the wrong element of the
            // sequence and fail here, for a reason that has nothing to do with what this test
            // is about.
            coEvery { userRepository.getCurrentUser() } returns storedUser.copy(partnerId = null)

            // The link, the intro, then Family, then Profile: the name is entered on the step that
            // asks for it, and the three Nexts before it are the three steps that precede it.
            viewModel.next()
            viewModel.next()
            viewModel.next()
            viewModel.updateName("Olya")
            viewModel.next()
            advanceUntilIdle()

            // A list, not a `slot`: the Family step writes first and MockK refuses a slot
            // capture across more than one matched call rather than silently keeping the last.
            val saved = mutableListOf<User>()
            coVerify { userRepository.updateUser(capture(saved)) }
            val profileWrite = saved.last()
            assertEquals("Olya", profileWrite.name)
            assertNull(profileWrite.partnerId, "a held snapshot would have put the pairing back")
        }

    @Test
    fun `skipping a step writes nothing, because a skip is not a deletion`() = runTest(dispatcher) {
        coEvery { childInfoRepository.getChildInfoById("c1") } returns null
        every { childInfoRepository.getAllChildInfo() } returns flowOf(
            listOf(child("c1", "Anya").copy(allergies = listOf("peanuts")))
        )
        viewModel = newViewModel()
        advanceUntilIdle()

        walkTo(OnboardingStep.Child)
        advanceUntilIdle()
        viewModel.skip()
        advanceUntilIdle()

        coVerify(exactly = 0) { childInfoRepository.upsertChildInfo(any()) }
    }

    @Test
    fun `the child and the relatives land on one record, not two`() = runTest(dispatcher) {
        val written = mutableListOf<ChildInfo>()
        coEvery { childInfoRepository.upsertChildInfo(capture(written)) } returns Unit
        coEvery { childInfoRepository.getChildInfoById(any()) } answers {
            written.lastOrNull { it.id == firstArg<String>() }
        }

        walkTo(OnboardingStep.Child)
        val anya = firstChildId()
        viewModel.updateChildName(anya, "Anya")
        viewModel.next()
        advanceUntilIdle()

        viewModel.updateRelatives(
            anya,
            listOf(EmergencyContact("Baba Vera", "grandmother", "+420 111"))
        )
        viewModel.next()
        advanceUntilIdle()

        assertEquals(2, written.size)
        assertEquals(written[0].id, written[1].id, "the relatives step must edit the same child")
        assertTrue(written[0].emergencyContacts.isEmpty())
        assertEquals("Baba Vera", written[1].emergencyContacts.single().name)
    }

    @Test
    fun `a nameless child is never created, however many relatives are entered`() =
        runTest(dispatcher) {
            walkTo(OnboardingStep.Relatives)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canEditRelatives)
            viewModel.updateRelatives(
                firstChildId(),
                listOf(EmergencyContact("Baba Vera", "grandmother", "+420"))
            )
            viewModel.next()
            advanceUntilIdle()

            coVerify(exactly = 0) { childInfoRepository.upsertChildInfo(any()) }
        }

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

            viewModel.next()
            viewModel.next()
            viewModel.next()
            viewModel.updateName("Olya")
            viewModel.next()
            viewModel.finish()
            advanceUntilIdle()

            assertEquals("Olya", stored.name)
            assertTrue(stored.onboardingCompletedAt?.isNotBlank() == true)
        }

    @Test
    fun `an account that already has answers does not have to retype them`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns storedUser.copy(
            name = "Olya",
            phone = "+420 777"
        )
        every { childInfoRepository.getAllChildInfo() } returns flowOf(
            listOf(
                child("c1", "Anya").copy(
                    dateOfBirth = LocalDateTime.parse("2018-05-04T00:00:00"),
                    allergies = listOf("peanuts")
                )
            )
        )
        viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Olya", state.name)
        assertEquals("+420 777", state.phone)
        val anya = state.children.single()
        assertEquals("Anya", anya.name)
        assertEquals(listOf("peanuts"), anya.allergies)
        assertEquals(2018, anya.dateOfBirth?.year)
        assertEquals("c1", anya.id, "the stored record is edited, not duplicated")
        assertFalse(anya.byCoParent, "an unstamped record is this parent's own")
    }

    @Test
    fun `two children are written as two records, not one`() = runTest(dispatcher) {
        val written = mutableListOf<ChildInfo>()
        coEvery { childInfoRepository.upsertChildInfo(capture(written)) } returns Unit
        coEvery { childInfoRepository.getChildInfoById(any()) } answers {
            written.lastOrNull { it.id == firstArg<String>() }
        }

        walkTo(OnboardingStep.Child)
        viewModel.updateChildName(firstChildId(), "Anya")
        viewModel.addChild()
        viewModel.updateChildName(viewModel.uiState.value.children[1].id, "Petr")
        viewModel.next()
        advanceUntilIdle()

        assertEquals(listOf("Anya", "Petr"), written.map { it.childName })
        assertEquals(2, written.map { it.id }.toSet().size, "each child needs its own record")
    }

    @Test
    fun `the relatives land on the child they were entered for, not the first`() =
        runTest(dispatcher) {
            // `stored` stands in for Room and outlives `written`, which the test clears to look
            // at the second step's writes alone — a record must not vanish with the list.
            val stored = mutableMapOf<String, ChildInfo>()
            val written = mutableListOf<ChildInfo>()
            coEvery { childInfoRepository.upsertChildInfo(any()) } answers {
                val child = firstArg<ChildInfo>()
                stored[child.id] = child
                written += child
            }
            coEvery { childInfoRepository.getChildInfoById(any()) } answers { stored[firstArg()] }

            walkTo(OnboardingStep.Child)
            viewModel.updateChildName(firstChildId(), "Anya")
            viewModel.addChild()
            val petr = viewModel.uiState.value.children[1].id
            viewModel.updateChildName(petr, "Petr")
            viewModel.next()
            advanceUntilIdle()
            written.clear()

            // The step defaults to the first named child; picking the second is what the chip
            // row does, and is the whole point of the picker existing.
            viewModel.selectRelativesChild(petr)
            viewModel.updateRelatives(petr, listOf(EmergencyContact("Baba Vera", "grandmother", "+420")))
            viewModel.next()
            advanceUntilIdle()

            // Anya was left as written, so she is not written again; Petr's record carries the
            // contact, which is what the chip row is for.
            val petrWrite = written.single()
            assertEquals("Petr", petrWrite.childName)
            assertEquals("Baba Vera", petrWrite.emergencyContacts.single().name)
        }

    @Test
    fun `the relatives step follows a child whose name is cleared`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Child)
        val anya = firstChildId()
        viewModel.updateChildName(anya, "Anya")
        viewModel.addChild()
        val petr = viewModel.uiState.value.children[1].id
        viewModel.updateChildName(petr, "Petr")
        viewModel.selectRelativesChild(petr)

        viewModel.updateChildName(petr, "")

        // Not left pointing at a draft that no longer takes contacts.
        assertEquals(anya, viewModel.uiState.value.relativesChild?.id)
    }

    @Test
    fun `removing a draft nobody named never reaches Room`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Child)
        viewModel.addChild()
        val blank = viewModel.uiState.value.children[1].id

        viewModel.removeChild(blank)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.children.size)
        coVerify(exactly = 0) { childInfoRepository.deleteChildInfo(any()) }
    }

    @Test
    fun `removing a child the wizard already wrote deletes its record`() = runTest(dispatcher) {
        val stored = child("c1", "Anya")
        every { childInfoRepository.getAllChildInfo() } returns flowOf(listOf(stored))
        coEvery { childInfoRepository.getChildInfoById("c1") } returns stored
        viewModel = newViewModel()
        advanceUntilIdle()

        // Dropping the draft alone would leave a child the parent explicitly removed sitting in
        // the child list, because the step saves on Next.
        viewModel.removeChild("c1")
        advanceUntilIdle()

        coVerify(exactly = 1) { childInfoRepository.deleteChildInfo(stored) }
    }

    @Test
    fun `a removed last draft is replaced, so the step always has a form`() = runTest(dispatcher) {
        walkTo(OnboardingStep.Child)
        viewModel.removeChild(firstChildId())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.children.size)
        assertTrue(viewModel.uiState.value.children.single().isBlank)
    }

    @Test
    fun `two pets are written as two records`() = runTest(dispatcher) {
        val written = mutableListOf<Pet>()
        coEvery { petRepository.upsertPet(capture(written)) } returns Unit
        coEvery { petRepository.getPetById(any()) } answers {
            written.lastOrNull { it.id == firstArg<String>() }
        }
        viewModel.setCaresFor(setOf(FamilyKind.PETS))

        walkTo(OnboardingStep.Pet)
        viewModel.setPetName(firstPetId(), "Rex")
        viewModel.setPetSpecies(firstPetId(), PetSpecies.DOG)
        viewModel.addPet()
        val second = viewModel.uiState.value.pets[1].id
        viewModel.setPetName(second, "Murka")
        viewModel.setPetSpecies(second, PetSpecies.CAT)
        viewModel.next()
        advanceUntilIdle()

        assertEquals(listOf("Rex", "Murka"), written.map { it.name })
        assertEquals(listOf(PetSpecies.DOG, PetSpecies.CAT), written.map { it.species })
    }

    @Test
    fun `an account with two stored children opens with both`() = runTest(dispatcher) {
        every { childInfoRepository.getAllChildInfo() } returns flowOf(
            listOf(child("c1", "Anya"), child("c2", "Petr"))
        )
        viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("Anya", "Petr"), viewModel.uiState.value.children.map { it.name })
    }
}
