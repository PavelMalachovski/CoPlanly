package com.coparently.app.presentation.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a change of the language the app speaks redraws the Today widget (design item 18): its
 * lines are worded in that language, and nothing in Room says it moved.
 */
class TodayWidgetLanguageTest {

    @Test
    fun `no widget placed redraws nothing`() {
        assertFalse(needsLanguageRedraw(emptyList(), "en-150"))
    }

    @Test
    fun `widgets already in the language stay as they are`() {
        assertFalse(needsLanguageRedraw(listOf("cs-CZ", "cs-CZ"), "cs-CZ"))
    }

    @Test
    fun `a widget in another language redraws`() {
        assertTrue(needsLanguageRedraw(listOf("cs-CZ", "de"), "de"))
    }

    @Test
    fun `a widget never redrawn by the app redraws once`() {
        assertTrue(needsLanguageRedraw(listOf(null), "en-US"))
    }
}
