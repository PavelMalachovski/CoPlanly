package com.coparently.app.pushwording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FormatArgsTest {
    @Test
    fun aStringAndANumberAreReplacedByTheirText() {
        assertEquals("Bob pays 3", formatArgs("%1\$s pays %2\$d", arrayOf("Bob", 3)))
    }

    @Test
    fun aTemplateWithNoArgumentsIsUnchanged() {
        assertEquals("New event", formatArgs("New event", arrayOf()))
        assertEquals("50 % and %% stay", formatArgs("50 % and %% stay", arrayOf("unused")))
    }

    @Test
    fun argumentsAreTakenByTheirPositionNotTheirOrder() {
        assertEquals("Leo goes to Ann", formatArgs("%2\$s goes to %1\$s", arrayOf("Ann", "Leo")))
    }

    @Test
    fun aLongIsPrintedByItsText() {
        assertEquals("in 12 days", formatArgs("in %1\$d days", arrayOf(12L)))
    }

    @Test
    fun anArgumentTheTemplateNamesButNobodyGaveThrows() {
        assertFailsWith<IndexOutOfBoundsException> { formatArgs("%2\$s", arrayOf("one")) }
    }
}
