package com.coparently.app.presentation.calendar

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Unit tests for CalendarViewModel.
 * Tests custody schedule management and calendar view mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var custodyScheduleDao: CustodyScheduleDao
    private lateinit var viewModel: CalendarViewModel

    private val testSchedule1 = CustodyScheduleEntity(
        id = "1",
        parentId = "parent1",
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusDays(7),
        pattern = "Every week",
        isActive = true,
        createdAt = LocalDate.now().atStartOfDay(),
        updatedAt = LocalDate.now().atStartOfDay()
    )

    private val testSchedule2 = CustodyScheduleEntity(
        id = "2",
        parentId = "parent2",
        startDate = LocalDate.now().plusDays(8),
        endDate = LocalDate.now().plusDays(14),
        pattern = "Every other week",
        isActive = true,
        createdAt = LocalDate.now().atStartOfDay(),
        updatedAt = LocalDate.now().atStartOfDay()
    )

    private val testSchedules = listOf(testSchedule1, testSchedule2)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        custodyScheduleDao = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `init loads custody schedules`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        // When
        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.custodySchedules.test {
            val schedules = awaitItem()
            assertEquals(testSchedules, schedules)
        }

        coVerify { custodyScheduleDao.getAllActiveSchedules() }
    }

    @Test
    fun `loadCustodySchedules loads all active schedules`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadCustodySchedules()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.custodySchedules.test {
            val schedules = awaitItem()
            assertEquals(testSchedules, schedules)
        }

        // Verify called at least twice (init + explicit call)
        coVerify(atLeast = 2) { custodyScheduleDao.getAllActiveSchedules() }
    }

    @Test
    fun `loadCustodySchedules handles empty list`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(emptyList())

        // When
        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.custodySchedules.test {
            val schedules = awaitItem()
            assertEquals(emptyList(), schedules)
        }
    }

    @Test
    fun `setViewMode changes view mode to WEEK`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setViewMode(CalendarViewMode.WEEK)

        // Then
        viewModel.viewMode.test {
            val mode = awaitItem()
            assertEquals(CalendarViewMode.WEEK, mode)
        }
    }

    @Test
    fun `setViewMode changes view mode to DAY`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setViewMode(CalendarViewMode.DAY)

        // Then
        viewModel.viewMode.test {
            val mode = awaitItem()
            assertEquals(CalendarViewMode.DAY, mode)
        }
    }

    @Test
    fun `initial view mode is MONTH`() = runTest {
        // Given
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        // When
        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.viewMode.test {
            val mode = awaitItem()
            assertEquals(CalendarViewMode.MONTH, mode)
        }
    }

    @Test
    fun `setSelectedDate changes selected date`() = runTest {
        // Given
        val testDate = LocalDate.now().plusDays(5)
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setSelectedDate(testDate)

        // Then
        viewModel.selectedDate.test {
            val date = awaitItem()
            assertEquals(testDate, date)
        }
    }

    @Test
    fun `initial selected date is today`() = runTest {
        // Given
        val today = LocalDate.now()
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        // When
        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.selectedDate.test {
            val date = awaitItem()
            assertEquals(today, date)
        }
    }

    @Test
    fun `setSelectedDate with past date`() = runTest {
        // Given
        val pastDate = LocalDate.now().minusDays(10)
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setSelectedDate(pastDate)

        // Then
        viewModel.selectedDate.test {
            val date = awaitItem()
            assertEquals(pastDate, date)
        }
    }

    @Test
    fun `setSelectedDate with future date`() = runTest {
        // Given
        val futureDate = LocalDate.now().plusMonths(3)
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setSelectedDate(futureDate)

        // Then
        viewModel.selectedDate.test {
            val date = awaitItem()
            assertEquals(futureDate, date)
        }
    }

    @Test
    fun `view mode persists across date changes`() = runTest {
        // Given
        val testDate = LocalDate.now().plusDays(1)
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns flowOf(testSchedules)

        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.setViewMode(CalendarViewMode.WEEK)
        viewModel.setSelectedDate(testDate)

        // Then
        viewModel.viewMode.test {
            val mode = awaitItem()
            assertEquals(CalendarViewMode.WEEK, mode)
        }

        viewModel.selectedDate.test {
            val date = awaitItem()
            assertEquals(testDate, date)
        }
    }

    @Test
    fun `custody schedules update when DAO emits new data`() = runTest {
        // Given
        val initialSchedules = listOf(testSchedule1)
        val updatedSchedules = testSchedules

        val schedulesFlow = flowOf(initialSchedules, updatedSchedules)
        coEvery { custodyScheduleDao.getAllActiveSchedules() } returns schedulesFlow

        // When
        viewModel = CalendarViewModel(custodyScheduleDao)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.custodySchedules.test {
            val first = awaitItem()
            // Flow should emit the last value
            assertEquals(2, first.size)
        }
    }
}

