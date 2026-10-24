package com.coparently.app.presentation.custody

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.R
import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.FirstSchedule
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.MidweekContact
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.parentingplan.PlanCitation
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.domain.parentingplan.PlanScheduleTarget
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testFamilyMembersSource
import com.coparently.app.presentation.common.testParentsSource
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The custody setup form: the custom-pattern week shortcut, reading a saved schedule back into
 * the form, and what a save reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustodySetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val start = LocalDate.of(2026, 9, 7)
    private val repository = mockk<CustodyModelRepository>()

    private val planReferences = mockk<PlanReferenceSource>()
    private val custodyReference = PlanReference(
        questionId = "care_weekday",
        target = PlanScheduleTarget.BASE_PATTERN,
        yourAnswer = "Week on, week off, changing on Mondays",
        theirAnswer = "Week on, week off, changing on Mondays",
        citation = PlanCitation("care_weekday", "0123456789abcdef")
    )

    /** The family on screen; one family unless a test switches it. */
    private val familyView = MutableStateFlow(FamilyView(me = "u1", onScreen = "u1__u2", others = emptySet()))
    private val familyViewSource = mockk<FamilyViewSource> { every { observe() } returns familyView }

    private fun viewModel(
        active: CustodyModel? = null,
        planQuestion: String? = null,
        parents: ParentsSource = testParentsSource()
    ): CustodySetupViewModel {
        every { repository.getActiveModel() } returns flowOf(active)
        val args = planQuestion?.let { mapOf("planQuestion" to it) }.orEmpty()
        return CustodySetupViewModel(
            repository,
            parents,
            SavedStateHandle(args),
            planReferences,
            testFamilyMembersSource(childNames = listOf("Ema", "Tomas")),
            familyViewSource
        )
    }

    @Test
    fun `a switch to a family with no schedule empties the form rather than keeping the last family's`() =
        runTest(dispatcher) {
            // The family with u2 has every other weekend; the family with u3 has no schedule yet.
            val saved = CustodyModel.everyOtherWeekend(
                id = "m1",
                startDate = start,
                momIsResident = true,
                midweek = MidweekContact(DayOfWeek.MONDAY, everyWeek = true)
            )
            val vm = viewModel(active = saved)
            every { repository.getActiveModel() } returnsMany listOf(flowOf(saved), flowOf(null))
            advanceUntilIdle()
            assertEquals(CustodyModelType.EVERY_OTHER_WEEKEND, vm.uiState.value.selectedModelType)

            familyView.value = FamilyView(me = "u1", onScreen = "u1__u3", others = setOf("u1__u2"))
            advanceUntilIdle()

            assertEquals(CustodySetupUiState().selectedModelType, vm.uiState.value.selectedModelType)
            assertFalse(vm.uiState.value.midweekEnabled)
            assertNull(vm.currentModel.value)
        }

    /** Alice, linked to Bob: a co-parent to put a change to. */
    private val paired = testParentsSource(
        me = User(id = "u1", email = "alice@example.com", name = "Alice", role = "mom", colorCode = "#FF4081"),
        partner = PartnerSummary(
            id = "u2",
            name = "Bob",
            email = "bob@example.com",
            pairedSinceMillis = 1L,
            role = "dad"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a week shortcut assigns the whole week, even when some days were already assigned`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.CUSTOM)
        vm.toggleCustomMomDay(0)
        vm.toggleCustomMomDay(2)

        vm.assignCustomWeekToMom(0)

        // It used to toggle each day, which un-assigned Monday and Wednesday here.
        assertEquals((0..6).toSet(), vm.uiState.value.customMomDays)
    }

    @Test
    fun `a week shortcut never assigns days beyond the pattern`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.CUSTOM)
        vm.setCustomPatternDays(10)

        vm.assignCustomWeekToMom(1)

        assertEquals(setOf(7, 8, 9), vm.uiState.value.customMomDays)
    }

    @Test
    fun `a saved every-other-weekend schedule with a Monday midweek reopens as it was`() = runTest(dispatcher) {
        // A Monday midweek puts fortnight index 0 with the contact parent. Reading "does slot 1
        // hold day 0" flipped the resident parent and offered to save the schedule the other way.
        val saved = CustodyModel.everyOtherWeekend(
            id = "m1",
            startDate = start,
            momIsResident = true,
            midweek = MidweekContact(DayOfWeek.MONDAY, everyWeek = true)
        )

        val vm = viewModel(active = saved)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CustodyModelType.EVERY_OTHER_WEEKEND, state.selectedModelType)
        assertTrue(state.momFirst)
        assertTrue(state.midweekEnabled)
        assertEquals(DayOfWeek.MONDAY, state.midweekDay)
        assertTrue(state.midweekEveryWeek)
    }

    @Test
    fun `a midweek day is saved only with the pattern that has one`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any()) } returns PatternSubmission.ACTIVATED
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.WEEK_ON_WEEK_OFF)
        vm.setStartDate(start)
        vm.setMidweekEnabled(true)

        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createWeekOnWeekOff(start, true) }
        coVerify(exactly = 0) { repository.createEveryOtherWeekend(any(), any(), any()) }
        assertNull(vm.uiState.value.midweek)
    }

    // ---- onboarding's schedule step (new-user review, October 2026) ------------------------------

    @Test
    fun `onboarding shows the shared schedule when the pair already has one`() = runTest(dispatcher) {
        val agreed = CustodyModel.weekOnWeekOff(id = "agreed", startDate = start)
        coEvery { repository.readShared() } returns SharedCustodyRead.Found(sharedCustody(agreed))
        val vm = viewModel()

        vm.checkOnboardingMode()
        advanceUntilIdle()

        assertEquals("agreed", assertIs<OnboardingScheduleMode.Existing>(vm.onboardingMode.value).model.id)
    }

    @Test
    fun `onboarding shows the form when nothing is shared or the server cannot answer`() = runTest(dispatcher) {
        listOf(SharedCustodyRead.Absent, SharedCustodyRead.Unavailable).forEach { read ->
            coEvery { repository.readShared() } returns read
            val vm = viewModel()

            vm.checkOnboardingMode()
            advanceUntilIdle()

            assertEquals(OnboardingScheduleMode.Form, vm.onboardingMode.value, "for $read")
        }
    }

    @Test
    fun `saving the first schedule never proposes`() = runTest(dispatcher) {
        // The pair's schedule reached the server after the step showed the form: nothing is
        // written, nothing is proposed, and the step switches to that schedule.
        val agreed = CustodyModel.weekOnWeekOff(id = "agreed", startDate = start)
        coEvery { repository.activateFirstSchedule(any(), any()) } returns FirstSchedule.SharedExists(agreed)
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.WEEK_ON_WEEK_OFF)
        var done = false

        vm.saveFirstSchedule { done = true }
        advanceUntilIdle()

        assertFalse(done)
        assertEquals("agreed", assertIs<OnboardingScheduleMode.Existing>(vm.onboardingMode.value).model.id)
        coVerify(exactly = 0) { repository.submitPattern(any(), any(), any()) }
    }

    @Test
    fun `a first schedule activated finishes the step`() = runTest(dispatcher) {
        coEvery { repository.activateFirstSchedule(any(), any()) } returns FirstSchedule.Activated
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.TWO_TWO_THREE)
        vm.setStartDate(start)
        var done = false

        vm.saveFirstSchedule { done = true }
        advanceUntilIdle()

        assertTrue(done)
        coVerify(exactly = 1) {
            repository.activateFirstSchedule(
                match { it.modelType == CustodyModelType.TWO_TWO_THREE && it.startDate == start },
                false
            )
        }
    }

    @Test
    fun `a linked parent's first schedule says so, and keeps the stored contact windows`() = runTest(dispatcher) {
        // Onboarding's form hides the windows section, so a save that dropped them would erase
        // afternoons the parent never saw; and the link step's answer reaches the repository.
        val window = ContactWindow(dayIndex = 2, start = LocalTime.of(15, 0), end = LocalTime.of(19, 0), parent = "dad")
        coEvery { repository.activateFirstSchedule(any(), any()) } returns FirstSchedule.Activated
        val vm = viewModel(
            active = CustodyModel.weekOnWeekOff(id = "stored", startDate = start).copy(contactWindows = listOf(window))
        )
        advanceUntilIdle()

        vm.saveFirstSchedule(linkedToCoParent = true) {}
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.activateFirstSchedule(match { it.contactWindows == listOf(window) }, true) }
    }

    @Test
    fun `a schedule already found stays found when the step is entered again`() = runTest(dispatcher) {
        val agreed = CustodyModel.weekOnWeekOff(id = "agreed", startDate = start)
        coEvery { repository.readShared() } returns SharedCustodyRead.Found(sharedCustody(agreed))
        val vm = viewModel()
        vm.checkOnboardingMode()
        advanceUntilIdle()
        coEvery { repository.readShared() } returns SharedCustodyRead.Unavailable

        vm.checkOnboardingMode()
        advanceUntilIdle()

        assertEquals("agreed", assertIs<OnboardingScheduleMode.Existing>(vm.onboardingMode.value).model.id)
    }

    @Test
    fun `a first schedule the server could not check is a failed save, and the form stays`() = runTest(dispatcher) {
        coEvery { repository.activateFirstSchedule(any(), any()) } returns FirstSchedule.Unavailable
        val vm = viewModel()
        advanceUntilIdle()
        var done = false

        vm.saveFirstSchedule { done = true }
        advanceUntilIdle()

        assertFalse(done)
        assertEquals(UiText.Res(R.string.custody_setup_save_failed), vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    /** The pair's document holding [model], written by Bob. */
    private fun sharedCustody(model: CustodyModel) = SharedCustody(
        model = model,
        lastModifiedBy = "u2",
        lastModifiedAtMillis = 1L,
        createdAt = "2026-09-01T10:00:00"
    )

    @Test
    fun `a save sent as a proposal says so`() = runTest(dispatcher) {
        coEvery { repository.createCustom(any(), any(), any()) } returns PatternSubmission.PROPOSED
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectModelType(CustodyModelType.CUSTOM)
        vm.setStartDate(start)
        vm.assignCustomWeekToMom(0)
        var succeeded = false

        vm.save { succeeded = true }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createCustom(start, 14, (0..6).toSet()) }
        val state = vm.uiState.value
        assertTrue(state.isSaved)
        assertTrue(state.proposedForApproval)
        assertFalse(state.isLoading)
        assertTrue(succeeded)
    }

    @Test
    fun `a failed save reports a localised error and leaves the form usable`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any()) } throws IllegalStateException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        var succeeded = false

        vm.save { succeeded = true }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UiText.Res(R.string.custody_setup_save_failed), state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isSaved)
        assertFalse(succeeded)
    }

    @Test
    fun `a save refused for the co-parent's waiting proposal says so and stays open`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any()) } returns
            PatternSubmission.COPARENT_PROPOSAL_WAITING
        val vm = viewModel()
        advanceUntilIdle()
        var succeeded = false

        vm.save { succeeded = true }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UiText.Res(R.string.custody_setup_answer_pending_first), state.error)
        assertFalse(state.isSaved)
        assertFalse(state.isLoading)
        assertFalse(succeeded)
    }

    @Test
    fun `a proposal that could not be sent reads as a failed save`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any()) } returns PatternSubmission.NOT_SENT
        val vm = viewModel()
        advanceUntilIdle()
        var succeeded = false

        vm.save { succeeded = true }
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.custody_setup_save_failed), vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaved)
        assertFalse(succeeded)
    }

    @Test
    fun `opened from the agreed custody answer, the form quotes it and the save cites it`() =
        runTest(dispatcher) {
            coEvery { planReferences.referenceFor("care_weekday") } returns custodyReference
            coEvery { repository.createWeekOnWeekOff(any(), any(), any(), any()) } returns PatternSubmission.PROPOSED
            val vm = viewModel(planQuestion = "care_weekday")
            advanceUntilIdle()

            assertEquals(custodyReference, vm.uiState.value.planReference)
            vm.selectModelType(CustodyModelType.WEEK_ON_WEEK_OFF)
            vm.setStartDate(start)
            vm.save()
            advanceUntilIdle()

            // The pattern is the one the parent built; the answer only rides along as a citation.
            coVerify(exactly = 1) {
                repository.createWeekOnWeekOff(start, true, emptyList(), "p1|care_weekday|0123456789abcdef")
            }
            assertTrue(vm.uiState.value.proposedForApproval)
        }

    @Test
    fun `opened any other way, the save cites nothing and no plan is read`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any(), any(), any()) } returns PatternSubmission.ACTIVATED
        val vm = viewModel()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertNull(vm.uiState.value.planReference)
        coVerify(exactly = 1) { repository.createWeekOnWeekOff(any(), any(), any(), null) }
        coVerify(exactly = 0) { planReferences.referenceFor(any()) }
    }

    @Test
    fun `a holiday answer is left to the seasonal editor, so the base form neither quotes nor cites it`() =
        runTest(dispatcher) {
            coEvery { planReferences.referenceFor("holidays_school") } returns custodyReference.copy(
                questionId = "holidays_school",
                target = PlanScheduleTarget.SEASONAL_LAYER
            )
            coEvery { repository.createWeekOnWeekOff(any(), any(), any(), any()) } returns PatternSubmission.PROPOSED
            val vm = viewModel(planQuestion = "holidays_school")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertNull(vm.uiState.value.planReference)
            coVerify(exactly = 1) { repository.createWeekOnWeekOff(any(), any(), any(), null) }
        }

    @Test
    fun `an answer that is no longer agreed opens the plain form`() = runTest(dispatcher) {
        coEvery { planReferences.referenceFor("care_weekday") } returns null
        val vm = viewModel(planQuestion = "care_weekday")
        advanceUntilIdle()

        assertNull(vm.uiState.value.planReference)
    }

    // ---- a child's own schedule (FAM-4) --------------------------------------

    private val ema = FamilyMember(FamilyMemberRef.Child("c-Ema"), "Ema")

    /** Ema is with slot 2 every day of a one-day cycle. */
    private val emaAlwaysWithSlotTwo = ChildScheduleOverride(
        childId = "c-Ema",
        patternDays = 1,
        momDayIndices = emptySet(),
        startDate = start
    )

    private val family = CustodyModel.weekOnWeekOff(id = "m1", startDate = start)

    @Test
    fun `the children are offered for a schedule of their own`() = runTest(dispatcher) {
        val vm = viewModel(active = family)
        val collected = mutableListOf<List<FamilyMember>>()
        backgroundScope.launch { vm.children.collect { collected += it } }
        advanceUntilIdle()

        assertEquals(listOf("Ema", "Tomas"), collected.last().map { it.name })
    }

    @Test
    fun `scoped to a child, the form opens on their own schedule and saves it as theirs`() =
        runTest(dispatcher) {
            val active = family.copy(childOverrides = listOf(emaAlwaysWithSlotTwo))
            coEvery { repository.submitChildOverride(any(), any()) } returns PatternSubmission.PROPOSED
            val vm = viewModel(active = active)
            advanceUntilIdle()

            vm.editSchedule(ema)

            val state = vm.uiState.value
            assertEquals(ChildScope("c-Ema", "Ema"), state.childScope)
            assertEquals(CustodyModelType.CUSTOM, state.selectedModelType)
            assertEquals(1, state.customPatternDays)
            // "Always with the other parent" is a valid child schedule, though not a family one.
            assertTrue(state.isValid)

            vm.save()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.submitChildOverride("c-Ema", emaAlwaysWithSlotTwo) }
            coVerify(exactly = 0) { repository.createCustom(any(), any(), any(), any(), any()) }
            assertTrue(vm.uiState.value.proposedForApproval)
        }

    @Test
    fun `a child with no schedule of their own starts from the family pattern`() = runTest(dispatcher) {
        val vm = viewModel(active = family)
        advanceUntilIdle()

        vm.editSchedule(ema)

        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, vm.uiState.value.selectedModelType)
        assertTrue(vm.uiState.value.momFirst)
    }

    @Test
    fun `following the family schedule again removes the child's override`() = runTest(dispatcher) {
        coEvery { repository.submitChildOverride(any(), any()) } returns PatternSubmission.ACTIVATED
        val vm = viewModel(active = family.copy(childOverrides = listOf(emaAlwaysWithSlotTwo)))
        advanceUntilIdle()
        vm.editSchedule(ema)

        vm.save(followFamily = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitChildOverride("c-Ema", null) }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `leaving the child's scope refills the form from the family pattern`() = runTest(dispatcher) {
        coEvery { repository.createWeekOnWeekOff(any(), any(), any(), any()) } returns PatternSubmission.ACTIVATED
        val vm = viewModel(active = family.copy(childOverrides = listOf(emaAlwaysWithSlotTwo)))
        advanceUntilIdle()
        vm.editSchedule(ema)

        vm.editSchedule(null)
        vm.save()
        advanceUntilIdle()

        assertNull(vm.uiState.value.childScope)
        assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, vm.uiState.value.selectedModelType)
        coVerify(exactly = 1) { repository.createWeekOnWeekOff(start, true, emptyList(), null) }
        coVerify(exactly = 0) { repository.submitChildOverride(any(), any()) }
    }

    @Test
    fun `a child's schedule with no family schedule under it is a failed save, not a crash`() =
        runTest(dispatcher) {
            coEvery { repository.submitChildOverride(any(), any()) } returns null
            val vm = viewModel(active = family)
            advanceUntilIdle()
            vm.editSchedule(ema)

            vm.save()
            advanceUntilIdle()

            assertEquals(UiText.Res(R.string.custody_setup_save_failed), vm.uiState.value.error)
            assertFalse(vm.uiState.value.isSaved)
        }

    // ---- the deadline of the proposal a save makes (phase 3a) -----------------

    @Test
    fun `save passes the deadline on`() = runTest(dispatcher) {
        val pick = AnswerByPick.Preset(AnswerByPreset.WEEK)
        coEvery { repository.createWeekOnWeekOff(any(), any(), any(), any(), any()) } returns PatternSubmission.PROPOSED
        coEvery { repository.submitChildOverride(any(), any(), any()) } returns PatternSubmission.PROPOSED
        val vm = viewModel(active = family)
        advanceUntilIdle()

        vm.save(answerBy = pick)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.createWeekOnWeekOff(start, true, emptyList(), null, pick) }

        // A child's own schedule is a proposal too, with the same deadline.
        vm.editSchedule(ema)
        vm.save(answerBy = pick)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.submitChildOverride("c-Ema", any(), pick) }
    }

    @Test
    fun `the cap preview is the first day the edited pattern changes`() = runTest(dispatcher) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Anchored today, so day 10 of the cycle is ten days from now.
        val vm = viewModel(active = CustodyModel.custom("m1", today, patternDays = 14, momDayIndices = (0..6).toSet()))
        backgroundScope.launch { vm.answerByCap.collect {} }
        advanceUntilIdle()
        // Unedited, the form describes the agreed schedule: nothing ahead changes.
        assertNull(vm.answerByCap.value)

        vm.toggleCustomMomDay(10)
        advanceUntilIdle()

        assertEquals(today.plusDays(10).atStartOfDay(zone).toInstant().toEpochMilli(), vm.answerByCap.value)
    }

    @Test
    fun `a child's schedule's cap is the first day that child's days change`() = runTest(dispatcher) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val vm = viewModel(active = CustodyModel.custom("m1", today, patternDays = 14, momDayIndices = (0..6).toSet()))
        backgroundScope.launch { vm.answerByCap.collect {} }
        advanceUntilIdle()
        // Ema has no schedule of her own, so the editor opens on the family's: nothing changes yet.
        vm.editSchedule(ema)
        advanceUntilIdle()
        assertNull(vm.answerByCap.value)

        // Day 12 becomes slot 1's for Ema alone; the family's own days do not move.
        vm.toggleCustomMomDay(12)
        advanceUntilIdle()

        assertEquals(today.plusDays(12).atStartOfDay(zone).toInstant().toEpochMilli(), vm.answerByCap.value)
    }

    @Test
    fun `with no co-parent or no agreed schedule a save does not propose`() = runTest(dispatcher) {
        // No co-parent: a schedule, but nobody to ask.
        val alone = viewModel(active = family)
        backgroundScope.launch { alone.proposes.collect {} }
        alone.parents.first { it.loaded }
        advanceUntilIdle()
        assertFalse(alone.proposes.value)

        // A co-parent, but no agreed schedule yet: the first one applies at once.
        val first = viewModel(active = null, parents = paired)
        backgroundScope.launch { first.proposes.collect {} }
        first.parents.first { it.loaded }
        advanceUntilIdle()
        assertFalse(first.proposes.value)

        // Both: a save is a proposal, and the form asks when it should be answered by.
        val both = viewModel(active = family, parents = paired)
        backgroundScope.launch { both.proposes.collect {} }
        assertTrue(both.proposes.first { it })
    }
}
