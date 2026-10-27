package com.coparently.app.presentation.calendar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** How many title lines an event block shows in Day and Week view (UX audit H2). */
class TitleLinesTest {

    @Test
    fun `a block shows as many title lines as it is tall`() {
        assertEquals(1, titleLinesFor(20.dp))
        assertEquals(2, titleLinesFor(33.dp))
        // An hour-long block in Week view: "Parent-teacher meeting" no longer reads "Pare…".
        assertEquals(3, titleLinesFor(56.dp))
    }

    @Test
    fun `a title takes at least one line and at most four`() {
        assertEquals(1, titleLinesFor(4.dp))
        assertEquals(4, titleLinesFor(300.dp))
    }
}
