package com.coparently.app.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals

class HeadingLinesTest {
    @Test
    fun `the names take a line of their own under the words before them`() {
        assertEquals(
            listOf("Together, for", "marusya and sasha"),
            togetherForLines("Together, for $NAMES_MARK", "marusya and sasha")
        )
        assertEquals(listOf("Вместе ради:", "Маша и Саша"), togetherForLines("Вместе ради: $NAMES_MARK", "Маша и Саша"))
    }

    @Test
    fun `a sentence whose names do not end it stays on one line`() {
        assertEquals(listOf("Für Emma gemeinsam"), togetherForLines("Für $NAMES_MARK gemeinsam", "Emma"))
    }

    @Test
    fun `a sentence that starts with the names stays on one line`() {
        assertEquals(listOf("Emma together"), togetherForLines("$NAMES_MARK together", "Emma"))
    }
}
