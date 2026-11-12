package com.coparently.app.presentation.calendar

import com.coparently.app.presentation.calendar.components.childrenList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The children's names in the header's "Together, for …" line. */
class CalendarHeaderTextTest {

    @Test
    fun `no children - no line`() {
        assertNull(childrenList(emptyList(), "and"))
    }

    @Test
    fun `one child is named alone`() {
        assertEquals("Emma", childrenList(listOf("Emma"), "and"))
    }

    @Test
    fun `two children are joined by the word for and`() {
        assertEquals("Emma and Leo", childrenList(listOf("Emma", "Leo"), "and"))
        assertEquals("Emma und Leo", childrenList(listOf("Emma", "Leo"), "und"))
    }

    @Test
    fun `three children - commas then the word for and`() {
        assertEquals("Emma, Leo and Max", childrenList(listOf("Emma", "Leo", "Max"), "and"))
    }
}
