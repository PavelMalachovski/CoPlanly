package com.coparently.app.presentation.expenses

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Budget
import com.coparently.app.domain.model.BudgetAlert
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.User
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.BudgetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.testFamilyMembersSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Budgets: a new one takes the app's default currency, an edit is a `copy()` of the loaded budget
 * (the rule event editing follows), and every write refreshes the alerts the screen shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val me = User(id = "u1", email = "olya@example.com", name = "Olya", role = "mom", colorCode = "")
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true) {
        every { getActiveBudgets() } returns flowOf(emptyList())
        coEvery { getBudgetAlerts() } returns emptyList()
    }
    private val userRepository = mockk<UserRepository>()

    private fun viewModel(signedIn: User? = me): BudgetViewModel {
        coEvery { userRepository.getCurrentUser() } returns signedIn
        val preferences = mockk<PreferencesRepository> {
            every { getDefaultCurrencyFlow() } returns flowOf(SupportedCurrency.EUR)
        }
        return BudgetViewModel(budgetRepository, userRepository, preferences, testFamilyMembersSource())
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
    fun `a new budget carries the app's default currency and whom it is for`() = runTest(dispatcher) {
        val saved = slot<Budget>()
        coEvery { budgetRepository.addBudget(capture(saved)) } returns Unit
        val vm = viewModel()
        advanceUntilIdle()

        vm.addBudget(ExpenseCategory.MEDICAL, 250.0, listOf(FamilyMemberRef.Pet("p1")))
        advanceUntilIdle()

        // Budget's own default is "USD"; the app default is what a new budget must take.
        assertEquals("EUR", saved.captured.currency)
        assertEquals(listOf<FamilyMemberRef>(FamilyMemberRef.Pet("p1")), saved.captured.forMembers)
        assertEquals(250.0, saved.captured.monthlyLimit)
    }

    @Test
    fun `an edit changes the limit and members and keeps everything else`() = runTest(dispatcher) {
        val original = Budget(
            id = "b1",
            category = ExpenseCategory.EDUCATION,
            monthlyLimit = 100.0,
            currency = "CZK",
            alertThreshold = 0.5,
            createdAt = LocalDateTime.parse("2026-01-02T03:04:05"),
            familyId = "u1__u2"
        )
        val saved = slot<Budget>()
        coEvery { budgetRepository.updateBudget(capture(saved)) } returns Unit
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateBudget(original, 300.0, listOf(FamilyMemberRef.Child("c1")))
        advanceUntilIdle()

        assertEquals(
            original.copy(monthlyLimit = 300.0, forMembers = listOf(FamilyMemberRef.Child("c1"))),
            saved.captured
        )
    }

    @Test
    fun `a write refreshes the alerts`() = runTest(dispatcher) {
        val alert = BudgetAlert(
            budgetId = "b1",
            currentSpent = 90.0,
            limit = 100.0,
            percentage = 0.9,
            category = ExpenseCategory.FOOD
        )
        coEvery { budgetRepository.getBudgetAlerts() } returnsMany listOf(emptyList(), listOf(alert))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(emptyList<BudgetAlert>(), vm.activeAlerts.value)

        vm.deleteBudget("b2")
        advanceUntilIdle()

        coVerify(exactly = 1) { budgetRepository.deleteBudget("b2") }
        assertEquals(listOf(alert), vm.activeAlerts.value)
    }

    @Test
    fun `the remote mirror starts only for a signed-in account`() = runTest(dispatcher) {
        viewModel(signedIn = null)
        advanceUntilIdle()

        coVerify(exactly = 0) { budgetRepository.observeRemote() }
    }
}
