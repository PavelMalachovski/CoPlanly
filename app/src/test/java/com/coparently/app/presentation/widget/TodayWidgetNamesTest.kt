package com.coparently.app.presentation.widget

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.Parents
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names the widget draws with no screen open.
 *
 * The property that matters is the uid guard: a snapshot is an account's, and the home screen
 * must never name the previous account's family after a switch.
 */
class TodayWidgetNamesTest {

    private val stored = mutableMapOf<String, String>()
    private val preferences = mockk<EncryptedPreferences> {
        every { getString(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
        every { putString(any(), any()) } answers { stored[firstArg<String>()] = secondArg() }
    }
    private val names = TodayWidgetNames(preferences)

    private val alex = NamedParent(uid = "uid-alex", slot = "mom", name = "Alex", colorCode = "#7B1FA2")
    private val sam = NamedParent(uid = "uid-sam", slot = "dad", name = "Sam", colorCode = "#D84315")

    @Test
    fun `both parents come back for the account that stored them`() {
        names.remember(Parents(me = alex, coParent = sam, isPaired = true, loaded = true))

        val recalled = names.recall("uid-alex")

        assertEquals(alex, recalled?.me)
        assertEquals(sam, recalled?.coParent)
    }

    @Test
    fun `another account's snapshot is refused`() {
        names.remember(Parents(me = alex, coParent = sam, isPaired = true, loaded = true))

        assertNull(names.recall("uid-someone-else"))
    }

    @Test
    fun `the synthetic starting value is never stored`() {
        assertFalse(names.remember(Parents()))
        assertNull(names.recall("uid-alex"))
    }

    @Test
    fun `only a real change asks for a redraw`() {
        val parents = Parents(me = alex, coParent = sam, isPaired = true, loaded = true)

        assertTrue(names.remember(parents))
        assertFalse(names.remember(parents))
        assertTrue(names.remember(parents.copy(coParent = sam.copy(name = "Samuel"))))
    }

    @Test
    fun `an unpaired parent is remembered without a co-parent`() {
        names.remember(Parents(me = alex, loaded = true))

        val recalled = names.recall("uid-alex")

        assertEquals(alex, recalled?.me)
        assertNull(recalled?.coParent)
    }
}
