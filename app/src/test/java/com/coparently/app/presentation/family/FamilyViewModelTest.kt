package com.coparently.app.presentation.family

import com.coparently.app.data.family.testFamilyViewSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.testParentsSource
import com.coparently.app.presentation.home.CurrencyAmount
import com.coparently.app.presentation.home.MonthSpendDependencies
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Family tab: the month's money card and whether the children row asks for a first child. */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val children = MutableStateFlow<List<ChildInfo>>(emptyList())

    private val childInfoRepository = mockk<ChildInfoRepository> { every { getAllChildInfo() } returns children }
    private val expenseRepository = mockk<ExpenseRepository> { every { getAllExpenses() } returns flowOf(emptyList()) }
    private val preferences = mockk<PreferencesRepository> {
        every { getDefaultCurrencyFlow() } returns flowOf(SupportedCurrency.EUR)
    }
    private val userRepository = mockk<UserRepository> { coEvery { getCurrentUserId() } returns "u1" }
    private val activeSchedule = MutableStateFlow<CustodyModel?>(null)
    private val custodyRepository = mockk<CustodyModelRepository> { every { getActiveModel() } returns activeSchedule }

    private fun viewModel() = FamilyViewModel(
        MonthSpendDependencies(expenseRepository, preferences, testFamilyViewSource()),
        testParentsSource(),
        userRepository,
        childInfoRepository,
        custodyRepository
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an empty month is a zero in the parent's currency, not a missing card`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { merge(vm.money, vm.hasChildren).collect {} }
        advanceUntilIdle()

        assertEquals(listOf(CurrencyAmount("EUR", 0.0)), vm.money.value?.spend?.byCurrency)
    }

    @Test
    fun `the children row asks for a first child until there is one`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { merge(vm.money, vm.hasChildren).collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasChildren.value)

        children.value = listOf(
            ChildInfo(
                id = "c1",
                childName = "Emma",
                dateOfBirth = null,
                createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
                updatedAt = LocalDateTime.of(2026, 1, 1, 9, 0)
            )
        )
        advanceUntilIdle()

        assertTrue(vm.hasChildren.value)
    }

    @Test
    fun `the schedule row names the schedule in force, and asks for one only while there is none`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.schedule.collect {} }
            advanceUntilIdle()
            assertNull(vm.schedule.value)

            activeSchedule.value = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 10, 19))
            advanceUntilIdle()

            assertEquals(CustodyModelType.WEEK_ON_WEEK_OFF, vm.schedule.value)
        }
}
