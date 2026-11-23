package com.coparently.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.MonthView
import com.coparently.app.presentation.calendar.SwapDaysButton
import com.coparently.app.presentation.calendar.SwapMark
import com.coparently.app.presentation.calendar.components.CalendarGridCard
import com.coparently.app.presentation.calendar.components.CalendarHeader
import com.coparently.app.presentation.calendar.components.CalendarHeaderActions
import com.coparently.app.presentation.calendar.components.CalendarHeaderState
import com.coparently.app.presentation.calendar.components.CalendarLegendContent
import com.coparently.app.presentation.calendar.components.CalendarLegendState
import com.coparently.app.presentation.calendar.components.CalendarMonthBar
import com.coparently.app.presentation.calendar.components.ChangeRequestBanner
import com.coparently.app.presentation.calendar.components.CustodyChangedBanner
import com.coparently.app.presentation.calendar.components.CustodyLegend
import com.coparently.app.presentation.calendar.components.DaySwapSelectionBar
import com.coparently.app.presentation.calendar.components.DaySwapSheetContent
import com.coparently.app.presentation.calendar.components.SwapSheetActions
import com.coparently.app.presentation.calendar.components.SwapSheetOffer
import com.coparently.app.presentation.calendar.components.TodayCard
import com.coparently.app.presentation.calendar.components.TodayModel
import com.coparently.app.presentation.calendar.components.VacationBanner
import com.coparently.app.presentation.calendar.swapMiniMonths
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.testing.RobolectricTestApplication
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.unbreakableDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinYearMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The calendar's month grid with every layer `DayCellFills` stacks — the weekend base, the custody
 * band with its Monday handover diagonal, the two public holidays, event dots, a pending swap,
 * the Wednesday contact-window corners (MON-6b) and the school-vacation line across the month's
 * end (MON-13) — plus the three banners that sit over it.
 *
 * The grid is where a raw `MomPink`/`DadBlue` would be most visible, and where light theme was
 * least reviewed; its weekday header and banners are translated, so it takes the full set.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = RobolectricTestApplication::class)
class CalendarScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun monthGrid() = snap("calendar_month_grid") { MonthGridFixture() }

    /**
     * The header's one row (calendar K1, November 2026): the month with its year, "?" with the dot
     * an active filter gives it, the children's avatars as the member filter with Emma chosen, and
     * the gear.
     */
    @Test
    fun header() = snap("calendar_header") {
        val members = HEADER_CHILDREN.map { FamilyMember(FamilyMemberRef.Child(it.id), it.childName, it) }
        CalendarHeader(
            state = CalendarHeaderState(
                month = ScreenshotFixtures.MONTH.atDay(1).toKotlinLocalDate(),
                members = members,
                selectedMembers = listOf(members.first().ref),
                filtersActive = true
            ),
            actions = CalendarHeaderActions(onJumpToDate = {}, onLegend = {}, onToggleMember = {}, onSettings = {})
        )
    }

    /** The grid in its white card: the M | W | D control, Today, "⇄" and "+", then the month. */
    @Test
    fun gridCard() = snap("calendar_grid_card") {
        CalendarGridCard {
            CalendarMonthBar(
                viewMode = CalendarViewMode.MONTH,
                onViewModeChange = {},
                onToday = {},
                onSwapDays = {},
                onAddEvent = {}
            )
            MonthGridFixture()
        }
    }

    /** The header's "?" sheet: what the colours mean, and the Filters row while a filter is on. */
    @Test
    fun legendSheet() = snap("calendar_legend_sheet") {
        CalendarLegendContent(
            state = CalendarLegendState(
                hasSchedule = true,
                showContactWindows = true,
                showPendingSwap = true,
                filtersActive = true
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onFilters = {}
        )
    }

    /** The Today card: the children with the reader, and the next event. */
    @Test
    fun todayCard() = snap("calendar_today_card") {
        val today = ScreenshotFixtures.TODAY.toKotlinLocalDate()
        val start = today.atTime(TODAY_EVENT_HOUR, 0)
        TodayCard(
            today = TodayModel(
                date = today,
                dayParent = "mom",
                childNames = HEADER_CHILDREN.map { it.childName },
                nextEvent = Event(
                    id = "dance",
                    title = "Dancing class",
                    startDateTime = start,
                    endDateTime = start,
                    eventType = "general",
                    parentOwner = "mom",
                    createdAt = start,
                    updatedAt = start
                ),
                hasEvents = true
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onOpen = {}
        )
    }

    @Test
    fun banners() = snap("calendar_banners") {
        Column(verticalArrangement = Arrangement.spacedBy(BANNER_GAP)) {
            ChangeRequestBanner(pendingCount = PENDING_REQUESTS, onReview = {})
            // The layer's name is the family's own words; its dates are the reader's, each kept whole and
            // the dash kept with the first, so a narrow line breaks only after the dash.
            val from = unbreakableDate(ReaderDateText.date(LocalDate(VACATION_YEAR, 6, 27), "dMMM"))
            val to = unbreakableDate(ReaderDateText.date(LocalDate(VACATION_YEAR, 8, 31), "dMMM"))
            VacationBanner(label = "Summer holidays · $from\u00A0– $to")
            CustodyChangedBanner(byName = "Pavel", onDismiss = {})
        }
    }

    /**
     * The legend over the grid: both parents, the handover diagonal, the contact-time corner, a
     * swap waiting for an answer, and the "⇄" that starts a swap.
     */
    @Test
    fun legend() = snap("calendar_legend") {
        CustodyLegend(
            parentNames = ScreenshotFixtures.parentNames,
            showContactWindows = true,
            showPendingSwap = true,
            trailing = { SwapDaysButton(onClick = {}) }
        )
    }

    /** The bar while days are chosen: a line each way, and a day nobody is recorded for. */
    @Test
    fun selectionBar() = snap("calendar_swap_selection_bar") {
        val month = ScreenshotFixtures.MONTH
        DaySwapSelectionBar(
            sides = SwapSides(
                toOther = listOf(SWAP_GIVE_DAY, SWAP_GIVE_DAY + 1).map { month.atDay(it).toKotlinLocalDate() },
                toOfferer = listOf(month.atDay(SWAP_TAKE_DAY).toKotlinLocalDate()),
                unknown = listOf(month.atDay(SWAP_TAKE_DAY + 1).toKotlinLocalDate())
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onNext = {},
            onCancel = {}
        )
    }

    /** The review sheet for an offer that gives two days and takes one, with its month. */
    @Test
    fun swapSheet() = snap("calendar_swap_sheet") {
        val month = ScreenshotFixtures.MONTH
        val give = listOf(month.atDay(SWAP_GIVE_DAY), month.atDay(SWAP_GIVE_DAY + 1))
        val take = listOf(month.atDay(SWAP_TAKE_DAY))
        DaySwapSheetContent(
            offer = SwapSheetOffer(
                sides = SwapSides(
                    toOther = give.map { it.toKotlinLocalDate() },
                    toOfferer = take.map { it.toKotlinLocalDate() }
                ),
                // The fixture family's co-parent, whom the rows name too ("→ Pavel").
                coParentName = ScreenshotFixtures.parentNames.parents.coParent?.name.orEmpty(),
                months = swapMiniMonths(
                    days = (give.associateWith { "dad" } + take.associateWith { "mom" })
                        .mapKeys { (day, _) -> day.toKotlinLocalDate() },
                    familyEvents = emptyList(),
                    custodyFor = ScreenshotFixtures::custodyOn
                )
            ),
            parentNames = ScreenshotFixtures.parentNames,
            actions = SwapSheetActions(onOffer = { _, _, _ -> }, onCancel = {}, onChangeDays = {})
        )
    }

    companion object {
        private val BANNER_GAP = 8.dp
        private const val TODAY_EVENT_HOUR = 16
        private val CHILD_STAMP = LocalDateTime(2026, 5, 1, 9, 0)
        private val HEADER_CHILDREN = listOf("Emma", "Leo").map {
            ChildInfo(id = it, childName = it, dateOfBirth = null, createdAt = CHILD_STAMP, updatedAt = CHILD_STAMP)
        }
        private const val PENDING_REQUESTS = 2
        private const val SWAP_GIVE_DAY = 13
        private const val SWAP_TAKE_DAY = 20
        private const val VACATION_YEAR = 2026

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}

private val MONTH_GRID_HEIGHT = 560.dp
private const val PENDING_SWAP_DAY = 27

/** The day the fixture shows being chosen for a swap. */
private const val CHOSEN_SWAP_DAY = 20

/**
 * May 2026's grid with every `DayCellFills` layer: custody with contact windows, a holiday,
 * school vacation, a swap waiting for an answer, a day being chosen for one, and events. Shared
 * with [ContrastScreenshots].
 */
@Composable
internal fun MonthGridFixture() {
    Box(modifier = Modifier.height(MONTH_GRID_HEIGHT)) {
        // The fixtures' days are `java.time`; the grid's are kotlinx-datetime since it moved to
        // `:shared-ui` (MON-24 phase 2, slice 11c), so they are converted here, at the call.
        MonthView(
            selectedMonth = ScreenshotFixtures.MONTH.toKotlinYearMonth(),
            eventsByDay = ScreenshotFixtures.monthEvents.mapKeys { (day, _) -> day.toKotlinLocalDate() },
            getCustody = ScreenshotFixtures::custodyOn,
            getContactWindows = { day -> ScreenshotFixtures.contactWindowsFor(day.toJavaLocalDate()) },
            parentNames = ScreenshotFixtures.parentNames,
            onDayClick = {},
            onMonthChange = {},
            holidays = ScreenshotFixtures.holidays.mapKeys { (day, _) -> day.toKotlinLocalDate() },
            schoolVacationDays = ScreenshotFixtures.schoolVacationDays.mapTo(mutableSetOf()) { it.toKotlinLocalDate() },
            // Both forms of a swap mark: a day waiting for an answer, and one being chosen.
            swapMarks = mapOf(
                ScreenshotFixtures.MONTH.atDay(PENDING_SWAP_DAY).toKotlinLocalDate() to
                    SwapMark(toSlot = "dad", pending = true),
                ScreenshotFixtures.MONTH.atDay(CHOSEN_SWAP_DAY).toKotlinLocalDate() to
                    SwapMark(toSlot = "mom", pending = false)
            )
        )
    }
}
