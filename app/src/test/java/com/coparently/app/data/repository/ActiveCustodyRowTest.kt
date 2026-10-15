package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.CustodyModelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which stored custody row answers for the family on screen (schema 45).
 *
 * The phone used to hold one active row for every family, so a parent in two families read, and
 * then wrote, one family's schedule as the other's. These are the rules the repository reads every
 * custody question through.
 */
class ActiveCustodyRowTest {

    @Test
    fun `the family on screen reads its own row, never a newer one of another family`() {
        val bob = row("bob", BOB_FAMILY, at = 1)
        val carol = row("carol", CAROL_FAMILY, at = 2)

        assertEquals(bob, ActiveCustodyRow.pick(listOf(bob, carol), CustodyScope(BOB_FAMILY, soleFamily = false)))
        assertEquals(carol, ActiveCustodyRow.pick(listOf(bob, carol), CustodyScope(CAROL_FAMILY, soleFamily = false)))
    }

    @Test
    fun `a family with no row of its own reads nothing`() {
        val bob = row("bob", BOB_FAMILY, at = 1)

        assertNull(ActiveCustodyRow.pick(listOf(bob), CustodyScope(CAROL_FAMILY, soleFamily = false)))
    }

    @Test
    fun `with one co-parent a row naming no family is that family's`() {
        // Saved before pairing, or before rows named their family: one family is unambiguous.
        val unstamped = row("before-pairing", null, at = 1)

        assertEquals(unstamped, ActiveCustodyRow.pick(listOf(unstamped), CustodyScope(BOB_FAMILY, soleFamily = true)))
    }

    @Test
    fun `with two co-parents a row naming no family is nobody's`() {
        // Nothing says which family it was; reading it as either is how one family's schedule
        // reached the other's document.
        val unstamped = row("before-pairing", null, at = 1)

        assertNull(ActiveCustodyRow.pick(listOf(unstamped), CustodyScope(BOB_FAMILY, soleFamily = false)))
    }

    @Test
    fun `the newest of the family's rows answers`() {
        val agreed = row("agreed", BOB_FAMILY, at = 1)
        val savedSince = row("saved-since", null, at = 2)

        assertEquals(
            savedSince,
            ActiveCustodyRow.pick(listOf(agreed, savedSince), CustodyScope(BOB_FAMILY, soleFamily = true))
        )
    }

    @Test
    fun `an unpaired parent reads the schedule saved without a family, else the newest they had`() {
        val bob = row("bob", BOB_FAMILY, at = 3)
        val carol = row("carol", CAROL_FAMILY, at = 2)
        val mine = row("mine", null, at = 1)
        val unpaired = CustodyScope(familyId = null, soleFamily = false)

        assertEquals(mine, ActiveCustodyRow.pick(listOf(bob, carol, mine), unpaired))
        assertEquals(bob, ActiveCustodyRow.pick(listOf(bob, carol), unpaired))
    }

    @Test
    fun `an inactive row never answers`() {
        val archived = row("archived", BOB_FAMILY, at = 1).copy(isActive = false)

        assertNull(ActiveCustodyRow.pick(listOf(archived), CustodyScope(BOB_FAMILY, soleFamily = false)))
        assertNull(ActiveCustodyRow.pick(listOf(archived), CustodyScope(familyId = null, soleFamily = false)))
    }

    @Test
    fun `a row belongs to its own family, and one naming none only to a sole family`() {
        assertTrue(ActiveCustodyRow.belongsTo(row("b", BOB_FAMILY, at = 1), BOB_FAMILY, soleFamily = false))
        assertFalse(ActiveCustodyRow.belongsTo(row("c", CAROL_FAMILY, at = 1), BOB_FAMILY, soleFamily = true))
        assertTrue(ActiveCustodyRow.belongsTo(row("u", null, at = 1), BOB_FAMILY, soleFamily = true))
        assertFalse(ActiveCustodyRow.belongsTo(row("u", null, at = 1), BOB_FAMILY, soleFamily = false))
    }

    private fun row(id: String, familyId: String?, at: Long) = CustodyModelEntity(
        id = id,
        modelType = "week_on_week_off",
        patternDays = 14,
        momDaysPattern = "[0,1,2,3,4,5,6]",
        startDate = "2026-08-03",
        createdAt = "2026-08-01T09:00:00",
        lastModifiedAtMillis = at,
        familyId = familyId
    )

    private companion object {
        const val BOB_FAMILY = "uidA__uidB"
        const val CAROL_FAMILY = "uidA__uidC"
    }
}
