package com.coparently.app.presentation.calendar

import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.SwapOfferer
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.User
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinYearMonth
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CalendarViewModel.
 * Covers view mode, date selection, parent filter and event type filters.
 *
 * The ViewModel speaks kotlinx-datetime since MON-24 phase 2; these tests keep `java.time` as their
 * oracle — the months and days they expect are `java.time`'s, as before the move — and convert at
 * the ViewModel's edge (`ym`, `day`, `displayed`, `anchor`, `selected`), as the calendar screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var custodyScheduleDao: CustodyScheduleDao
    private lateinit var custodyModelRepository: CustodyModelRepository
    private lateinit var encryptedPreferences: SecurePreferences
    private lateinit var viewModel: CalendarViewModel

    /** Alice, signed in on this phone. */
    private val alice = User(
        id = "alice-uid",
        email = "alice@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "#FF4081"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        custodyScheduleDao = mockk {
            every { getAllActiveSchedules() } returns flowOf(emptyList())
        }
        custodyModelRepository = mockk {
            every { getActiveModel() } returns flowOf(null)
            every { observeShared() } returns flowOf(null)
            every { observeDayOverrides() } returns flowOf(emptyMap())
        }
        encryptedPreferences = mockk {
            every { getString(any(), any()) } returns null
            every { getString(any()) } returns null
            every { getBoolean(any(), any()) } answers { secondArg() }
            every { putString(any(), any()) } just Runs
            every { putBoolean(any(), any()) } just Runs
        }
        viewModel = calendarViewModel(testParentsSource())
    }

    private fun calendarViewModel(parentsSource: ParentsSource) = CalendarViewModel(
        custodyScheduleDao = custodyScheduleDao,
        custodyModelRepository = custodyModelRepository,
        encryptedPreferences = encryptedPreferences,
        friendRepository = noCalendarFriends(),
        parentsSource = parentsSource,
        userRepository = noSignedInUser()
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    /** [this] as the ViewModel takes a month. */
    private fun YearMonth.ym() = toKotlinYearMonth()

    /** [this] as the ViewModel takes a day. */
    private fun LocalDate.day() = toKotlinLocalDate()

    /** The month the grid shows, as `java.time` reads it. */
    private val CalendarViewModel.displayed: YearMonth get() = displayedMonth.value.toJavaYearMonth()

    /** The month the query is anchored on, as `java.time` reads it. */
    private val CalendarViewModel.anchor: YearMonth get() = queryAnchorMonth.value.toJavaYearMonth()

    /** The selected day, as `java.time` reads it. */
    private val CalendarViewModel.selected: LocalDate? get() = selectedDate.value?.toJavaLocalDate()

    /** [day] November 2026, as the swap offers take it: a kotlinx-datetime day (MON-24). */
    private fun swapDay(day: Int) = kotlinx.datetime.LocalDate(2026, 11, day)

    @Test
    fun `a single-day offer is its own group and passes the deadline on`() = runTest {
        val pick = AnswerByPick.Preset(AnswerByPreset.WEEK)
        val date = swapDay(20)
        val transform = slot<(Map<String, DayOverride>) -> Result<Map<String, DayOverride>>>()
        coEvery { custodyModelRepository.applyDayOverrides(any(), capture(transform), any(), any()) } returns
            Result.success(emptyMap())
        val signedIn = calendarViewModel(testParentsSource(me = alice))
        backgroundScope.launch { signedIn.parents.collect {} }
        signedIn.parents.first { it.me != null }

        signedIn.offerDaySwap(date, toParent = "dad", answerBy = pick)
        advanceUntilIdle()

        coVerify(exactly = 1) { custodyModelRepository.applyDayOverrides("2026-11-20", any(), pick, any()) }
        // A group of one: the id its clock is named by is on the entry the transform writes.
        val offered = transform.captured(emptyMap()).getOrThrow().getValue("2026-11-20")
        assertNotNull(offered.groupId)
        assertEquals(DayOverrideStatus.PENDING, offered.status)
        assertEquals("alice-uid", offered.requestedBy)
        assertNull(signedIn.swapError.value)
    }

    @Test
    fun `a run of days passes the deadline on, once for the whole group`() = runTest {
        val pick = AnswerByPick.Preset(AnswerByPreset.TOMORROW)
        coEvery { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), any()) } returns
            Result.success(emptyMap())
        val signedIn = calendarViewModel(testParentsSource(me = alice))
        backgroundScope.launch { signedIn.parents.collect {} }
        signedIn.parents.first { it.me != null }
        val first = swapDay(20)

        signedIn.offerDaySwapForDates(listOf(first, swapDay(21)), toParentFor = { "dad" }, answerBy = pick)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            custodyModelRepository.applyDayOverridesForDates(listOf("2026-11-20", "2026-11-21"), any(), pick, any())
        }
    }

    @Test
    fun `an offer that reached the server is said to have gone, naming its sender`() = runTest {
        val offerer = slot<SwapOfferer>()
        coEvery { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), capture(offerer)) } returns
            Result.success(emptyMap())
        val signedIn = calendarViewModel(testParentsSource(me = alice))
        backgroundScope.launch { signedIn.parents.collect {} }
        signedIn.parents.first { it.me != null }
        val first = swapDay(20)

        signedIn.offerDaySwapForDates(listOf(first, swapDay(21), swapDay(22)), toParentFor = { "dad" })
        advanceUntilIdle()

        assertEquals(SwapOfferer(slot = "mom", name = "Alice"), offerer.captured)
        assertEquals(3, signedIn.swapSent.value?.dayCount)
        assertNotNull(signedIn.swapSent.value?.groupId)
        signedIn.clearSwapSent()
        assertNull(signedIn.swapSent.value)
    }

    @Test
    fun `a refused offer is not said to have gone`() = runTest {
        coEvery { custodyModelRepository.applyDayOverrides(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("refused"))
        val signedIn = calendarViewModel(testParentsSource(me = alice))
        backgroundScope.launch { signedIn.parents.collect {} }
        signedIn.parents.first { it.me != null }

        signedIn.offerDaySwap(swapDay(20), toParent = "dad")
        advanceUntilIdle()

        assertNull(signedIn.swapSent.value)
        assertEquals(SwapError.Refused, signedIn.swapError.value)
    }

    @Test
    fun `default view mode is month`() = runTest {
        assertEquals(CalendarViewMode.MONTH, viewModel.viewMode.value)
    }

    @Test
    fun `setViewMode updates state`() = runTest {
        viewModel.setViewMode(CalendarViewMode.WEEK)
        assertEquals(CalendarViewMode.WEEK, viewModel.viewMode.value)

        viewModel.setViewMode(CalendarViewMode.DAY)
        assertEquals(CalendarViewMode.DAY, viewModel.viewMode.value)
    }

    @Test
    fun `setSelectedDate updates state`() = runTest {
        val date = LocalDate.of(2026, 7, 20)
        viewModel.setSelectedDate(date.day())
        assertEquals(date, viewModel.selected)
    }

    @Test
    fun `setSelectedDate moves the displayed month to the picked day`() = runTest {
        val otherMonthDay = YearMonth.now().plusMonths(2).atDay(5)
        viewModel.setSelectedDate(otherMonthDay.day())
        assertEquals(YearMonth.from(otherMonthDay), viewModel.displayed)
    }

    @Test
    fun `showMonth clears the selection for a month that is not today's`() = runTest {
        val nextMonth = YearMonth.now().plusMonths(1)
        viewModel.showMonth(nextMonth.ym())
        assertEquals(nextMonth, viewModel.displayed)
        assertNull(viewModel.selected)
    }

    @Test
    fun `showMonth re-selects today when paging back to today's month`() = runTest {
        viewModel.showMonth(YearMonth.now().plusMonths(1).ym())
        viewModel.showMonth(YearMonth.now().ym())
        assertEquals(LocalDate.now(), viewModel.selected)
    }

    @Test
    fun `default parent filter shows both parents`() = runTest {
        assertEquals(ParentFilter.BOTH, viewModel.parentFilter.value)
    }

    @Test
    fun `setParentFilter switches views`() = runTest {
        viewModel.setParentFilter(ParentFilter.MOM)
        assertEquals(ParentFilter.MOM, viewModel.parentFilter.value)

        viewModel.setParentFilter(ParentFilter.DAD)
        assertEquals(ParentFilter.DAD, viewModel.parentFilter.value)
    }

    @Test
    fun `toggleEventTypeVisibility hides and shows a type`() = runTest {
        assertTrue(viewModel.hiddenEventTypes.value.isEmpty())

        viewModel.toggleEventTypeVisibility("school")
        assertTrue("school" in viewModel.hiddenEventTypes.value)

        viewModel.toggleEventTypeVisibility("school")
        assertFalse("school" in viewModel.hiddenEventTypes.value)
    }

    @Test
    fun `addCustomEventType normalizes and deduplicates`() = runTest {
        viewModel.addCustomEventType("  Music Lessons ")
        assertEquals(listOf("music lessons"), viewModel.customEventTypes.value)

        // Duplicate and default types are ignored
        viewModel.addCustomEventType("music lessons")
        viewModel.addCustomEventType("school")
        viewModel.addCustomEventType("   ")
        assertEquals(listOf("music lessons"), viewModel.customEventTypes.value)
    }

    @Test
    fun `holidays are shown by default and can be disabled`() = runTest {
        assertTrue(viewModel.showHolidays.value)

        viewModel.setShowHolidays(false)
        assertFalse(viewModel.showHolidays.value)
    }

    @Test
    fun `the query anchor starts on the current month`() = runTest {
        assertEquals(YearMonth.now(), viewModel.anchor)
    }

    @Test
    fun `paging within tolerance leaves the query anchor alone`() = runTest {
        val start = YearMonth.now()
        viewModel.showMonth(start.plusMonths(1).ym())
        viewModel.showMonth(start.plusMonths(2).ym())
        assertEquals(start, viewModel.anchor)
        assertEquals(start.plusMonths(2), viewModel.displayed)
    }

    @Test
    fun `paging past tolerance re-anchors the query`() = runTest {
        val start = YearMonth.now()
        viewModel.showMonth(start.plusMonths(3).ym())
        assertEquals(start.plusMonths(3), viewModel.anchor)
    }

    @Test
    fun `paging backwards past tolerance re-anchors the query`() = runTest {
        val start = YearMonth.now()
        viewModel.showMonth(start.minusMonths(3).ym())
        assertEquals(start.minusMonths(3), viewModel.anchor)
    }

    @Test
    fun `tapping a day in a distant month re-anchors the query`() = runTest {
        val start = YearMonth.now()
        val distant = start.plusMonths(7).atDay(14)
        viewModel.setSelectedDate(distant.day())
        assertEquals(YearMonth.from(distant), viewModel.anchor)
    }

    /**
     * The general property the sticky window rests on, asserted after every step rather than at
     * four specific call sites: whatever moved the grid, the anchor may never be further than
     * [CalendarSelection.QUERY_ANCHOR_TOLERANCE_MONTHS] from it. `CalendarQueryRangeTest` proves
     * the window covers the grid *given* that bound; without this, a future method writing
     * `_displayedMonth` past `moveTo` would un-load months with every other test still green,
     * and the symptom is a silently empty grid.
     */
    @Test
    fun `the query anchor never drifts past tolerance however the grid is moved`() = runTest {
        val start = YearMonth.now()
        val steps: List<Pair<String, () -> Unit>> = listOf(
            // small steps forward
            "page +1" to { viewModel.showMonth(start.plusMonths(1).ym()) },
            "page +2" to { viewModel.showMonth(start.plusMonths(2).ym()) },
            "page +3" to { viewModel.showMonth(start.plusMonths(3).ym()) },
            "page +4" to { viewModel.showMonth(start.plusMonths(4).ym()) },
            // small steps backward, back through the origin
            "page +3 again" to { viewModel.showMonth(start.plusMonths(3).ym()) },
            "page +2 again" to { viewModel.showMonth(start.plusMonths(2).ym()) },
            "page +1 again" to { viewModel.showMonth(start.plusMonths(1).ym()) },
            "page 0" to { viewModel.showMonth(start.ym()) },
            "page -1" to { viewModel.showMonth(start.minusMonths(1).ym()) },
            "page -2" to { viewModel.showMonth(start.minusMonths(2).ym()) },
            "page -3" to { viewModel.showMonth(start.minusMonths(3).ym()) },
            // far jumps in both directions
            "jump +14" to { viewModel.showMonth(start.plusMonths(14).ym()) },
            "jump -20" to { viewModel.showMonth(start.minusMonths(20).ym()) },
            "back to today" to { viewModel.showMonth(start.ym()) },
            // across a year boundary, one month at a time
            "to December" to { viewModel.showMonth(YearMonth.of(start.year, 12).ym()) },
            "to January" to { viewModel.showMonth(YearMonth.of(start.year + 1, 1).ym()) },
            "to February" to { viewModel.showMonth(YearMonth.of(start.year + 1, 2).ym()) },
            "back to December" to { viewModel.showMonth(YearMonth.of(start.year, 12).ym()) },
            // day taps, which move the grid too
            "tap a day next month" to { viewModel.setSelectedDate(start.plusMonths(1).atDay(9).day()) },
            "tap a day far ahead" to { viewModel.setSelectedDate(start.plusMonths(9).atDay(28).day()) },
            "tap a day far behind" to { viewModel.setSelectedDate(start.minusMonths(11).atDay(1).day()) },
            "tap a day a year back" to { viewModel.setSelectedDate(start.minusMonths(12).atDay(15).day()) }
        )

        for ((label, step) in steps) {
            step()
            val drift = kotlin.math.abs(
                java.time.temporal.ChronoUnit.MONTHS.between(
                    viewModel.anchor,
                    viewModel.displayed
                )
            )
            assertTrue(
                drift <= CalendarSelection.QUERY_ANCHOR_TOLERANCE_MONTHS,
                "after '$label' the anchor ${viewModel.anchor} drifted $drift " +
                    "months from the displayed ${viewModel.displayed}"
            )
        }
    }
}
