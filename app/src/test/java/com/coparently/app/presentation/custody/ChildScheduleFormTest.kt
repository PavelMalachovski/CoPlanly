package com.coparently.app.presentation.custody

import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The custody form as a child's own schedule (FAM-4), and back: what a save stores, and what the
 * editor opens on.
 */
class ChildScheduleFormTest {

    private val start = LocalDate.of(2026, 9, 7)
    private val afternoon = ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad")
    private val family = CustodyModel.weekOnWeekOff(id = "m1", startDate = start)

    @Test
    fun `a preset saved for a child is its days, its anchor and the windows its cycle reaches`() {
        val state = CustodySetupUiState(
            selectedModelType = CustodyModelType.WEEK_ON_WEEK_OFF,
            startDate = start,
            momFirst = false,
            contactWindows = listOf(afternoon, afternoon.copy(dayIndex = 20))
        )

        val override = state.toChildOverride("teen")

        assertEquals("teen", override.childId)
        assertEquals(14, override.patternDays)
        assertEquals((7..13).toSet(), override.momDayIndices)
        assertEquals(start, override.startDate)
        assertEquals(listOf(afternoon), override.contactWindows)
    }

    @Test
    fun `a child who is always with the other parent is a schedule, not an empty form`() {
        val state = CustodySetupUiState(
            selectedModelType = CustodyModelType.CUSTOM,
            startDate = start,
            customPatternDays = 7,
            customMomDays = emptySet(),
            childScope = ChildScope("baby", "Ema")
        )

        assertEquals(true, state.isValid)
        assertEquals(emptySet<Int>(), state.toChildOverride("baby").momDayIndices)
        // The family pattern still needs a slot-1 day.
        assertEquals(false, state.copy(childScope = null).isValid)
    }

    @Test
    fun `the editor opens on the child's own schedule, or on the family pattern without one`() {
        val own = ChildScheduleOverride("baby", 1, setOf(0), start, emptyList())
        val model = family.copy(childOverrides = listOf(own))

        val opened = model.scopedTo(ChildScope("baby", "Ema"))
        assertEquals(CustodyModelType.CUSTOM, opened.modelType)
        assertEquals(1, opened.patternDays)
        assertEquals(setOf(0), opened.momDayIndices)

        assertSame(model, model.scopedTo(ChildScope("big", "Tomáš")))
        assertSame(model, model.scopedTo(null))
    }
}
