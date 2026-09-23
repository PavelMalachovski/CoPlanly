package com.coparently.app.screenshots

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.holidays.Holiday
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.IsoFields

/**
 * Fixed data for the screenshot tests.
 *
 * Every date is pinned to May 2026 rather than read from the clock, so an image recorded next
 * month is the same image — the precondition for ever switching the suite from recording to
 * verifying against committed baselines. May is chosen for its content: two Czech public holidays
 * (the 1st and the 8th), a weekend on each edge of the grid, and days borrowed from April and June
 * in the first and last rows.
 */
object ScreenshotFixtures {

    /** The month the grid shows, and the month everything else happens in. */
    val MONTH: YearMonth = YearMonth.of(2026, 5)

    /**
     * "Today" for the agenda card: a Wednesday in the middle of the month, so it carries the
     * co-parent's contact window as well as its own events.
     */
    val TODAY: LocalDate = MONTH.atDay(13)

    const val MY_UID = "u1"
    const val CO_PARENT_UID = "u2"

    /**
     * Both parents named, so labels show names rather than fallbacks. The fallbacks are English
     * literals on purpose: they are what `rememberParentNames` would have resolved, and with both
     * names present none of them is ever drawn.
     */
    val parentNames = ParentNames(
        parents = Parents(
            me = NamedParent(uid = MY_UID, slot = SLOT_ONE, name = "Olya"),
            coParent = NamedParent(uid = CO_PARENT_UID, slot = SLOT_TWO, name = "Pavel"),
            isPaired = true,
            loaded = true
        ),
        youFallback = "You",
        coParentFallback = "Co-parent",
        unknownFallback = "Parent"
    )

    private const val SLOT_ONE = "mom"
    private const val SLOT_TWO = "dad"

    /** Week-on, week-off by ISO week, handing over on Mondays — so the grid has handover days. */
    fun custodyFor(date: LocalDate): String =
        if (date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 0) SLOT_ONE else SLOT_TWO

    /** A Wednesday-afternoon contact window for whichever parent does not have the week. */
    fun contactWindowsFor(date: LocalDate): List<ContactWindow> =
        if (date.dayOfWeek == DayOfWeek.WEDNESDAY) {
            val other = if (custodyFor(date) == SLOT_ONE) SLOT_TWO else SLOT_ONE
            listOf(
                ContactWindow(
                    dayIndex = 2,
                    start = LocalTime.of(15, 0),
                    end = LocalTime.of(19, 0),
                    parent = other
                )
            )
        } else {
            emptyList()
        }

    /** May's two Czech public holidays, as `CzechHolidays` would produce them. */
    val holidays: Map<LocalDate, Holiday> = listOf(
        Holiday(MONTH.atDay(1), "Labour Day", "Svátek práce", "cs"),
        Holiday(MONTH.atDay(8), "Liberation Day", "Den vítězství", "cs")
    ).associateBy { it.date }

    private val stamp: LocalDateTime = MONTH.atDay(1).atStartOfDay()

    /** An event in May with the fields the screens read; everything else at its default. */
    fun event(
        id: String,
        title: String,
        start: LocalDateTime,
        owner: String,
        durationMinutes: Long = 60
    ) = Event(
        id = id,
        title = title,
        startDateTime = start,
        endDateTime = start.plusMinutes(durationMinutes),
        eventType = "other",
        parentOwner = owner,
        createdAt = stamp,
        updatedAt = stamp
    )

    /** Today's three events, one of them long enough to wrap at 1.5× text. */
    val todayEvents: List<Event> = listOf(
        event("e1", "School drop-off", TODAY.atTime(7, 45), SLOT_ONE, durationMinutes = 30),
        event("e2", "Swimming lesson at the city pool", TODAY.atTime(15, 30), SLOT_TWO),
        event("e3", "Dentist", TODAY.atTime(18, 0), SLOT_ONE, durationMinutes = 45)
    )

    /** Events spread over the month, for the grid's dots. */
    val monthEvents: Map<LocalDate, List<Event>> = listOf(
        event("m1", "Parents' evening", MONTH.atDay(5).atTime(18, 0), SLOT_ONE),
        event("m2", "Football", MONTH.atDay(12).atTime(16, 0), SLOT_TWO),
        event("m3", "Football", MONTH.atDay(19).atTime(16, 0), SLOT_TWO),
        event("m4", "Birthday party", MONTH.atDay(23).atTime(14, 0), SLOT_ONE),
        event("m5", "Vet", MONTH.atDay(23).atTime(10, 0), SLOT_TWO)
    ).plus(todayEvents).groupBy { it.startDateTime.toLocalDate() }

    /** The child and the pet an event can be about. */
    val members: List<FamilyMember> = listOf(
        FamilyMember(FamilyMemberRef.Child("c1"), "Mia"),
        FamilyMember(FamilyMemberRef.Pet("p1"), "Rex")
    )
}
