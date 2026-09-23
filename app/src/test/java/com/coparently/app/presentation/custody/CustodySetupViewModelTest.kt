package com.coparently.app.presentation.custody

import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.MidweekContact
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun viewModel(active: CustodyModel? = null): CustodySetupViewModel {
        every { repository.getActiveModel() } returns flowOf(active)
        return CustodySetupViewModel(repository, testParentsSource())
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
}
