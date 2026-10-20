package com.coparently.app.presentation.family

import com.coparently.app.data.family.AssignOutcome
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.family.UnassignedKind
import com.coparently.app.data.family.UnassignedRecord
import com.coparently.app.data.family.UnassignedRecords
import com.coparently.app.data.family.UnassignedSnapshot
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
import kotlin.test.assertTrue

/**
 * "Records without a family" as the screen, the Settings row and Home's question see it: the
 * count and the question name nobody, the list names each family's co-parent once, a schedule is
 * offered only the family on screen, and every answer says what it did.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnassignedRecordsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val snapshots = MutableStateFlow(UnassignedSnapshot())
    private val records = mockk<UnassignedRecords>(relaxed = true)
    private val source = mockk<SelectedFamilySource>()
    private val userRepository = mockk<UserRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { userRepository.observeCurrentUserId() } returns flowOf(ALICE)
        coEvery { userRepository.getCurrentUserId() } returns ALICE
        every { records.observe(ALICE) } returns snapshots
        coEvery { source.named(any()) } answers {
            firstArg<List<FamilyOption>>().map { it.copy(partnerName = NAMES.getValue(it.partnerUid)) }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the count and the question name nobody`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.summary.collect {} }
        snapshots.value = UnassignedSnapshot(listOf(DENTIST, EMMA), FAMILIES, BOB_FAMILY.familyId, askOnHome = true)
        advanceUntilIdle()

        assertEquals(UnassignedSummary(count = 2, askOnHome = true), vm.summary.value)
        coVerify(exactly = 0) { source.named(any()) }
    }

    @Test
    fun `the list groups the records and names each family once`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        snapshots.value = UnassignedSnapshot(listOf(EMMA, DENTIST, SCHEDULE), FAMILIES, BOB_FAMILY.familyId)
        advanceUntilIdle()
        snapshots.value = UnassignedSnapshot(listOf(EMMA, DENTIST), FAMILIES, BOB_FAMILY.familyId)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(listOf("Bob", "Carol"), state.families.map { it.partnerName })
        assertEquals(
            listOf(UnassignedGroup.CHILDREN_AND_PETS, UnassignedGroup.EVENTS),
            state.groups.map { it.first }
        )
        coVerify(exactly = 1) { source.named(any()) }
    }

    @Test
    fun `a schedule is offered only the family on screen, anything else every family`() {
        val state = UnassignedRecordsUiState(
            loading = false,
            records = listOf(DENTIST, SCHEDULE),
            families = FAMILIES,
            onScreenFamilyId = CAROL_FAMILY.familyId
        )

        assertEquals(listOf(CAROL_FAMILY), state.choicesFor(SCHEDULE))
        assertEquals(FAMILIES, state.choicesFor(DENTIST))
    }

    @Test
    fun `nothing to answer is not loading, and names nobody`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        snapshots.value = UnassignedSnapshot(emptyList(), FAMILIES, BOB_FAMILY.familyId)
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.records.isEmpty())
        coVerify(exactly = 0) { source.named(any()) }
    }

    @Test
    fun `an assignment goes to the data layer and says how it landed`() = runTest(dispatcher) {
        coEvery { records.assign(ALICE, DENTIST, CAROL_FAMILY.familyId) } returns AssignOutcome.ASSIGNED
        val vm = viewModel()

        vm.assign(DENTIST, CAROL_FAMILY.familyId)
        advanceUntilIdle()

        assertEquals(UnassignedNotice.Assigned(AssignOutcome.ASSIGNED, CAROL_FAMILY.familyId), vm.notice.value)
        assertFalse(vm.busy.value)
        vm.noticeShown()
        assertNull(vm.notice.value)
    }

    @Test
    fun `a failed assignment says it was not sent rather than ending the screen`() = runTest(dispatcher) {
        coEvery { records.assign(any(), any(), any()) } throws IllegalStateException("disk full")
        val vm = viewModel()

        vm.assign(DENTIST, CAROL_FAMILY.familyId)
        advanceUntilIdle()

        assertEquals(UnassignedNotice.Assigned(AssignOutcome.NOT_SENT, CAROL_FAMILY.familyId), vm.notice.value)
        assertFalse(vm.busy.value)
    }

    @Test
    fun `keeping a record remembers it for the signed-in parent`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.keep(DENTIST)
        advanceUntilIdle()

        verify { records.keep(ALICE, DENTIST) }
        assertEquals(UnassignedNotice.Kept, vm.notice.value)
    }

    @Test
    fun `answering Home's question marks it asked`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.promptAnswered()
        advanceUntilIdle()

        coVerify { records.markAsked(ALICE) }
    }

    @Test
    fun `signed out, there is nothing to answer`() = runTest(dispatcher) {
        every { userRepository.observeCurrentUserId() } returns flowOf(null)
        val vm = viewModel()
        backgroundScope.launch { vm.summary.collect {} }
        advanceUntilIdle()

        assertEquals(UnassignedSummary(), vm.summary.value)
    }

    private fun viewModel() = UnassignedRecordsViewModel(records, source, userRepository)

    private companion object {
        const val ALICE = "alice-uid"
        val BOB_FAMILY = FamilyOption("alice-uid__bob-uid", "bob-uid")
        val CAROL_FAMILY = FamilyOption("alice-uid__carol-uid", "carol-uid")
        val FAMILIES = listOf(BOB_FAMILY, CAROL_FAMILY)
        val NAMES = mapOf("bob-uid" to "Bob", "carol-uid" to "Carol")

        val DENTIST = UnassignedRecord(UnassignedKind.EVENT, "dentist", "Dentist")
        val EMMA = UnassignedRecord(UnassignedKind.CHILD, "emma", "Emma")
        val SCHEDULE = UnassignedRecord(UnassignedKind.CUSTODY, "mine", "")
    }
}
