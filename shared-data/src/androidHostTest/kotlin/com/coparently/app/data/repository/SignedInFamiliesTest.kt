package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which custody document a stored row may be written to (schema 45): its own family's, and only
 * while the signed-in parent is still in that family.
 */
class SignedInFamiliesTest {

    @Test
    fun `a row goes to its own family's document, whichever family is on screen`() {
        val families = SignedInFamilies.of(ALICE, alice(onScreen = CAROL, partnerIds = listOf(BOB, CAROL)))

        val pair = families.pairFor(row(familyId = BOB_FAMILY))

        assertEquals(BOB_FAMILY, pair?.documentId)
        assertEquals(listOf(ALICE, BOB), pair?.participants)
        assertEquals(ALICE, pair?.myUid)
    }

    @Test
    fun `a row of a family the parent has left goes nowhere`() {
        val families = SignedInFamilies.of(ALICE, alice(onScreen = CAROL, partnerIds = listOf(CAROL)))

        assertNull(families.pairFor(row(familyId = BOB_FAMILY)))
    }

    @Test
    fun `a row naming no family goes to the family on screen only while it is the only one`() {
        val one = SignedInFamilies.of(ALICE, alice(onScreen = BOB, partnerIds = listOf(BOB)))
        val two = SignedInFamilies.of(ALICE, alice(onScreen = BOB, partnerIds = listOf(BOB, CAROL)))

        assertEquals(BOB_FAMILY, one.pairFor(row(familyId = null))?.documentId)
        assertNull(two.pairFor(row(familyId = null)))
    }

    @Test
    fun `an unpaired or signed-out parent's row goes nowhere`() {
        assertNull(SignedInFamilies.of(ALICE, alice(onScreen = null)).pairFor(row(familyId = null)))
        assertNull(SignedInFamilies.of(null, null).pairFor(row(familyId = BOB_FAMILY)))
    }

    @Test
    fun `the co-parents are the stored list with the family on screen`() {
        // A row from before the list existed carries the family on screen with an empty list.
        val older = SignedInFamilies.of(ALICE, alice(onScreen = BOB))
        val both = SignedInFamilies.of(ALICE, alice(onScreen = CAROL, partnerIds = listOf(BOB, CAROL)))

        assertTrue(older.soleFamily)
        assertEquals(CustodyScope(BOB_FAMILY, soleFamily = true), older.scope)
        assertFalse(both.soleFamily)
        assertEquals(CustodyScope(CAROL_FAMILY, soleFamily = false), both.scope)
    }

    private fun alice(onScreen: String?, partnerIds: List<String> = emptyList()) = User(
        id = ALICE,
        email = "alice@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = onScreen,
        partnerIds = partnerIds
    )

    private fun row(familyId: String?) = CustodyModelEntity(
        id = "model-1",
        modelType = "week_on_week_off",
        patternDays = 14,
        momDaysPattern = "[0,1,2,3,4,5,6]",
        startDate = "2026-08-03",
        createdAt = "2026-08-01T09:00:00",
        familyId = familyId
    )

    private companion object {
        const val ALICE = "uidA"
        const val BOB = "uidB"
        const val CAROL = "uidC"
        const val BOB_FAMILY = "uidA__uidB"
        const val CAROL_FAMILY = "uidA__uidC"
    }
}
