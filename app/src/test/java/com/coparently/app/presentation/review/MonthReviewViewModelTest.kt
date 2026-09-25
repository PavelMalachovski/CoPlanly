package com.coparently.app.presentation.review

import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.ai.AI_CONSENT_VERSION
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The month in review: figures for the month on screen, never past the current month, and the AI
 * summary only after a consent, from the figures alone, and dropped when the month changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = YearMonth.now()
    private val model = MutableStateFlow<CustodyModel?>(
        CustodyModel.weekOnWeekOff(id = "m1", startDate = today.atDay(1))
    )

    private val custodyRepository = mockk<CustodyModelRepository> {
        every { getActiveModel() } returns model
        every { observeDayOverrides() } returns flowOf(emptyMap<String, DayOverride>())
    }
    private val eventRepository = mockk<EventRepository> {
        every { getEventsByDateRange(any(), any()) } returns flowOf(emptyList())
    }
    private val expenseRepository = mockk<ExpenseRepository> {
        every { getAllExpenses() } returns flowOf(
            listOf(
                Expense(
                    id = "e1",
                    title = "Shoes",
                    amount = 80.0,
                    currency = "CZK",
                    category = ExpenseCategory.CLOTHING,
                    paidBy = ALICE,
                    splitBetween = listOf(ALICE, BOB),
                    date = today.atDay(2)
                )
            )
        )
    }
    private val userRepository = mockk<UserRepository> {
        every { observeCurrentUserId() } returns flowOf(ALICE)
        every { observeUserById(ALICE) } returns flowOf(null)
    }
    private val childInfoRepository = mockk<ChildInfoRepository> {
        every { getAllChildInfo() } returns flowOf(emptyList())
    }
    private val parents = Parents(
        me = NamedParent(uid = ALICE, slot = "mom", name = "Alice"),
        coParent = NamedParent(uid = BOB, slot = "dad", name = "Bob"),
        isPaired = true,
        loaded = true
    )
    private val parentsSource = mockk<ParentsSource> {
        every { observe() } returns flowOf(parents)
    }

    private var consent: AiConsent? = null
    private val consentManager = mockk<AiConsentManager>(relaxed = true) {
        coEvery { current() } answers { consent }
        coEvery { grant() } answers {
            consent = AiConsent(AI_CONSENT_VERSION, null)
            true
        }
    }
    private val stats = slot<Map<String, Any>>()
    private val repository = mockk<AiAssistRepository> {
        coEvery { summarizeMonth(any(), any(), capture(stats)) } returns AiAssistResult.Text("A calm month.")
    }

    private val names = ParentNames(parents, "You", "Co-parent", "Parent")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(enabled: Boolean = true) = MonthReviewViewModel(
        MonthReviewSources(
            custodyModelRepository = custodyRepository,
            eventRepository = eventRepository,
            expenseRepository = expenseRepository,
            userRepository = userRepository,
            childInfoRepository = childInfoRepository,
            parentsSource = parentsSource
        ),
        MonthReviewAssist(AiAssistAvailability(enabled), consentManager, repository)
    )

    @Test
    fun `the current month is reviewed and cannot go forward`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(today, state.month)
        assertFalse(state.canGoForward)
        val review = assertNotNull(state.review)
        assertEquals(today.lengthOfMonth(), review.daysBySlot.values.sum())
        assertEquals(80.0, review.money.single().total)

        vm.nextMonth()
        advanceUntilIdle()
        assertEquals(today, vm.state.value.month)
    }

    @Test
    fun `going back shows the month before, and forward returns`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.previousMonth()
        advanceUntilIdle()
        assertEquals(today.minusMonths(1), vm.state.value.month)
        assertTrue(vm.state.value.canGoForward)
        // The expense is dated this month, so the month before has none.
        assertTrue(assertNotNull(vm.state.value.review).money.isEmpty())

        vm.nextMonth()
        advanceUntilIdle()
        assertEquals(today, vm.state.value.month)
    }

    @Test
    fun `a summary asks for consent first, then sends the figures and names`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.summarize("cs", names)
        advanceUntilIdle()
        assertEquals(AiAssistState.AskingConsent, vm.summaryState.value)
        coVerify(exactly = 0) { repository.summarizeMonth(any(), any(), any()) }

        vm.agree()
        advanceUntilIdle()

        assertEquals("A calm month.", vm.summary.value)
        coVerify { repository.summarizeMonth(today, "cs", any()) }
        assertEquals(today.toString(), stats.captured["month"])
        val days = stats.captured["daysWithParent"] as List<*>
        assertTrue(days.all { (it as Map<*, *>)["name"] in setOf("Alice", "Bob") })
    }

    @Test
    fun `changing the month drops the summary`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.summarize("en", names)
        advanceUntilIdle()
        assertEquals("A calm month.", vm.summary.value)

        vm.previousMonth()
        advanceUntilIdle()

        assertNull(vm.summary.value)
        assertEquals(AiAssistState.Idle, vm.summaryState.value)
    }

    @Test
    fun `a month without a schedule is not sent`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        model.value = null
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.summarize("en", names)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.summarizeMonth(any(), any(), any()) }
        assertNull(vm.summary.value)
    }

    @Test
    fun `with the assist off the figures still show and nothing is sent`() = runTest(dispatcher) {
        consent = AiConsent(AI_CONSENT_VERSION, 1L)
        val vm = viewModel(enabled = false)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.summarize("en", names)
        advanceUntilIdle()

        assertFalse(vm.aiAvailable)
        assertNotNull(vm.state.value.review)
        coVerify(exactly = 0) { repository.summarizeMonth(any(), any(), any()) }
    }

    private companion object {
        const val ALICE = "uid-alice"
        const val BOB = "uid-bob"
    }
}
