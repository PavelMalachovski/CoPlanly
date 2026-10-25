package com.coparently.app.data.family

import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.domain.family.FamilySlots
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A family's slots as this phone kept them (per-family slots, 25 October 2026). */
class KnownFamilySlotsTest {

    private val dao = mockk<KnownFamilyDao>()
    private val slots = KnownFamilySlots(dao)

    @Test
    fun `a row with both slots names each parent`() = runTest {
        coEvery { dao.get("u1__u2") } returns row(mine = "dad", theirs = "mom")
        assertEquals(FamilySlots(mapOf("u1" to "dad", "u2" to "mom")), slots.get("u1__u2"))
    }

    @Test
    fun `a row missing a slot, or holding one slot twice, answers nothing`() = runTest {
        coEvery { dao.get("u1__u2") } returns row(mine = "dad", theirs = null)
        assertNull(slots.get("u1__u2"))
        coEvery { dao.get("u1__u2") } returns row(mine = "mom", theirs = "mom")
        assertNull(slots.get("u1__u2"))
        coEvery { dao.get("u1__u2") } returns null
        assertNull(slots.get("u1__u2"))
    }

    private fun row(mine: String?, theirs: String?) =
        KnownFamilyEntity("u1__u2", "u2", mySlot = mine, coParentSlot = theirs, updatedAtMillis = 1L)
}
