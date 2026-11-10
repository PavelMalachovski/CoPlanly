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

    private class FakeStore(private val failing: Boolean = false) : SealedTextStore {
        var deletes = 0
        override fun read(): StoredText = StoredText.Absent
        override fun write(text: String) = Unit
        override fun delete() {
            deletes++
            if (failing) throw IllegalStateException("locked")
        }
    }

    @Test
    fun `the first launch deletes the old item then marks the install`() {
        val marker = FakeMarker()
        val store = FakeStore()
        FreshInstall(marker, store).clearIfFirstLaunch()
        assertEquals(1, store.deletes)
        assertTrue(marker.set)
    }

    @Test
    fun `the second launch touches nothing`() {
        val marker = FakeMarker(set = true)
        val store = FakeStore()
        FreshInstall(marker, store).clearIfFirstLaunch()
        assertEquals(0, store.deletes)
    }

    @Test
    fun `a delete that throws still marks the install`() {
        val marker = FakeMarker()
        val store = FakeStore(failing = true)
        FreshInstall(marker, store).clearIfFirstLaunch()
        assertEquals(1, store.deletes)
        assertTrue(marker.set)
    }
}
