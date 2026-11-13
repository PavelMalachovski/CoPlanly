package com.coparently.app.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreshInstallTest {

    private class FakeMarker(var set: Boolean = false) : InstallMarker {
        override fun isSet() = set
        override fun set() {
            set = true
        }
    }

    /** Records each cleanup's name with whether the marker was already set when it ran. */
    private class Journal(private val marker: FakeMarker) {
        val entries = mutableListOf<String>()

        fun cleanup(what: String, failing: Boolean = false) = LeftoverCleanup(what) {
            entries += "$what marker=${marker.set}"
            if (failing) throw IllegalStateException("locked")
        }
    }

    @Test
    fun `the first launch clears the store and signs out before it marks the install`() {
        val marker = FakeMarker()
        val journal = Journal(marker)
        FreshInstall(marker, listOf(journal.cleanup("preferences"), journal.cleanup("account"))).clearIfFirstLaunch()
        assertEquals(listOf("preferences marker=false", "account marker=false"), journal.entries)
        assertTrue(marker.set)
    }

    @Test
    fun `the second launch touches nothing`() {
        val marker = FakeMarker(set = true)
        val journal = Journal(marker)
        FreshInstall(marker, listOf(journal.cleanup("preferences"), journal.cleanup("account"))).clearIfFirstLaunch()
        assertEquals(emptyList(), journal.entries)
    }

    @Test
    fun `a cleanup that throws is logged and the next one still runs before the mark`() {
        val marker = FakeMarker()
        val journal = Journal(marker)
        val cleanups = listOf(journal.cleanup("preferences", failing = true), journal.cleanup("account"))
        FreshInstall(marker, cleanups).clearIfFirstLaunch()
        assertEquals(listOf("preferences marker=false", "account marker=false"), journal.entries)
        assertTrue(marker.set)
    }

    @Test
    fun `a sign-out that throws still marks the install`() {
        val marker = FakeMarker()
        val journal = Journal(marker)
        val cleanups = listOf(journal.cleanup("preferences"), journal.cleanup("account", failing = true))
        FreshInstall(marker, cleanups).clearIfFirstLaunch()
        assertEquals(listOf("preferences marker=false", "account marker=false"), journal.entries)
        assertTrue(marker.set)
    }
}
