package com.coparently.app.presentation.custody

import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.MidweekContact
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [candidateModel] is the schedule the deadline block caps (phase 3a, spec §4.2): it must be the
 * schedule a save sends, or the form would offer a deadline for one schedule and send another.
 */
class CandidateModelTest {

    private val start = LocalDate.of(2026, 9, 7)

    /** Out of order, one twice, and one past a fortnight: a save sends them canonical and in the cycle. */
    private val windows = listOf(
        ContactWindow(9, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad"),
        ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad"),
        ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad"),
        ContactWindow(20, LocalTime.of(16, 0), LocalTime.of(18, 0), "dad")
    )

    /** Two children with schedules of their own: with slot 1 every day, and with slot 2 every day. */
    private val ema = ChildScheduleOverride("c-Ema", patternDays = 1, momDayIndices = setOf(0), startDate = start)
    private val tomas = ChildScheduleOverride("c-Tomas", patternDays = 1, momDayIndices = emptySet(), startDate = start)

    /** The agreed schedule, with everything a save carries over from it. */
    private val active = CustodyModel.weekOnWeekOff("agreed", start).copy(
        seasonalLayers = listOf(
            SeasonalLayer.allWith("summer", "Summer", LocalDate.of(2027, 7, 1)..LocalDate.of(2027, 7, 31), "mom")
        ),
        unreadableLayers = listOf("L2;written-by-a-newer-build"),
        childOverrides = listOf(ema, tomas),
        unreadableChildOverrides = listOf("C2;written-by-a-newer-build")
    )

    @Test
    fun `the candidate is the pattern a save would send`() {
        // What each `createXxx` builds from the form below, by the factory it calls.
        val midweek = MidweekContact(DayOfWeek.WEDNESDAY, everyWeek = true)
        val sent = mapOf(
            CustodyModelType.WEEK_ON_WEEK_OFF to CustodyModel.weekOnWeekOff("x", start, momFirst = false),
            CustodyModelType.EVERY_OTHER_WEEKEND to
                CustodyModel.everyOtherWeekend("x", start, momIsResident = false, midweek = midweek),
            CustodyModelType.TWO_TWO_THREE to CustodyModel.twoTwoThree("x", start, momStartsFirst = false),
            CustodyModelType.THREE_FOUR_FOUR_THREE to
                CustodyModel.threeFourFourThree("x", start, momStartsFirst = false),
            CustodyModelType.CUSTOM to
                CustodyModel.custom("x", start, patternDays = 21, momDayIndices = setOf(0, 3, 20))
        )
        assertEquals(CustodyModelType.entries.toSet(), sent.keys)

        for ((type, expected) in sent) {
            val form = CustodySetupUiState(
                selectedModelType = type,
                startDate = start,
                momFirst = false,
                customPatternDays = 21,
                customMomDays = setOf(0, 3, 20),
                midweekEnabled = true,
                midweekDay = DayOfWeek.WEDNESDAY,
                midweekEveryWeek = true,
                contactWindows = windows
            )

            val candidate = requireNotNull(form.candidateModel(active))

            assertEquals(expected.modelType, candidate.modelType, "$type")
            assertEquals(expected.patternDays, candidate.patternDays, "$type")
            assertEquals(expected.momDayIndices, candidate.momDayIndices, "$type")
            assertEquals(expected.startDate, candidate.startDate, "$type")
            assertEquals(
                ContactWindowCodec.canonical(windows.filter { it.dayIndex < expected.patternDays }),
                candidate.contactWindows,
                "$type"
            )
            // The agreed layers and children's schedules ride along, unreadable entries included.
            assertEquals(active.seasonalLayers, candidate.seasonalLayers, "$type")
            assertEquals(active.unreadableLayers, candidate.unreadableLayers, "$type")
            assertEquals(active.childOverrides, candidate.childOverrides, "$type")
            assertEquals(active.unreadableChildOverrides, candidate.unreadableChildOverrides, "$type")
        }

        // A family with no agreed schedule yet sends the pattern alone.
        val first = CustodySetupUiState(startDate = start).candidateModel(active = null)
        assertEquals(CustodyModel.weekOnWeekOff("x", start).momDayIndices, first?.momDayIndices)
        assertEquals(emptyList<SeasonalLayer>(), first?.seasonalLayers)

        // A child's own schedule is the agreed one with that child's override replaced, as
        // `submitChildOverride` sends it; the other child's stays as it was.
        val emaForm = CustodySetupUiState(
            selectedModelType = CustodyModelType.CUSTOM,
            startDate = start,
            customPatternDays = 7,
            customMomDays = setOf(0, 1),
            childScope = ChildScope("c-Ema", "Ema")
        )
        val forEma = requireNotNull(emaForm.candidateModel(active))
        assertEquals(active.copy(childOverrides = listOf(tomas, emaForm.toChildOverride("c-Ema"))), forEma)
        // With no family schedule there is nothing to give a child a schedule of their own over.
        assertNull(emaForm.candidateModel(active = null))
    }
}
