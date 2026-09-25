package com.coparently.app.presentation.common

import com.coparently.app.domain.school.SchoolImportPlanner
import com.coparently.app.presentation.calendar.CalendarViewModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Every built-in event type is named by its own string resource, so neither the event form nor
 * the calendar's filter sheet prints a stored identifier as a label.
 */
class EventTypeLabelTest {

    @Test
    fun `every default type has a distinct label`() {
        val resources = CalendarViewModel.DEFAULT_EVENT_TYPES.map { eventTypeLabelRes(it) }

        resources.forEach { assertNotNull(it) }
        assertEquals(resources.size, resources.distinct().size, "two types share a label resource")
    }

    @Test
    fun `the school import's type is a built-in type`() {
        assertNotNull(eventTypeLabelRes(SchoolImportPlanner.SCHOOL_EVENT_TYPE))
    }

    @Test
    fun `a parent's own type has no resource`() {
        assertNull(eventTypeLabelRes("swimming"))
        assertNull(eventTypeLabelRes("School"))
    }
}
