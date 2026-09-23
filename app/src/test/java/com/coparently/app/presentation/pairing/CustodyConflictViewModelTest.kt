package com.coparently.app.presentation.pairing

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Writing the answer to a custody conflict at pairing: the chosen pattern is activated, the
 * rejected one survives, and a failed write keeps the screen open to retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustodyConflictViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val start = LocalDate.of(2026, 9, 7)
    private val mine = CustodyModel.weekOnWeekOff(id = "mine", startDate = start, momFirst = true)
    private val theirs = CustodyModel.weekOnWeekOff(id = "theirs", startDate = start, momFirst = false)
    private val repository = mockk<CustodyModelRepository>(relaxed = true)
    private val pending = PendingCustodyConflict()

    private fun viewModel() = CustodyConflictViewModel(pending, repository, testParentsSource())

    private fun ask(mine: CustodyModel, theirs: CustodyModel) =
        pending.set(CustodyConflictPrompt(CustodyConflict.Conflict(mine = mine, theirs = theirs), mySlot = "mom"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `choosing a pattern activates it and closes the screen`() = runTest(dispatcher) {
        ask(mine, theirs)
        val vm = viewModel()

        vm.choose(theirs)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveAndActivate(theirs) }
        // Different ids: deactivation alone keeps the rejected one, nothing to archive.
        coVerify(exactly = 0) { repository.archiveRejected(any()) }
        assertTrue(vm.resolved.value)
        assertFalse(vm.isSaving.value)
    }

    @Test
    fun `when both patterns share an id the rejected one is archived first`() = runTest(dispatcher) {
        // Room's insert REPLACEs on the primary key, so without a copy the rejected pattern is lost.
        val theirsSameId = theirs.copy(id = mine.id)
        ask(mine, theirsSameId)
        val vm = viewModel()

        vm.choose(mine)
        advanceUntilIdle()

        coVerifyOrder {
            repository.archiveRejected(theirsSameId)
            repository.saveAndActivate(mine)
        }
    }

    @Test
    fun `a failed write keeps the screen open and can be retried`() = runTest(dispatcher) {
        ask(mine, theirs)
        coEvery { repository.saveAndActivate(any()) } throws IllegalStateException("disk full")
        val vm = viewModel()

        vm.choose(mine)
        advanceUntilIdle()

        assertTrue(vm.saveFailed.value)
        assertFalse(vm.resolved.value)
        assertFalse(vm.isSaving.value)

        coEvery { repository.saveAndActivate(any()) } returns Unit
        vm.consumeSaveFailure()
        vm.choose(mine)
        advanceUntilIdle()

        assertTrue(vm.resolved.value)
        assertFalse(vm.saveFailed.value)
    }

    @Test
    fun `with nothing outstanding nothing is written`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.choose(mine)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.saveAndActivate(any()) }
        assertFalse(vm.resolved.value)
    }
}
