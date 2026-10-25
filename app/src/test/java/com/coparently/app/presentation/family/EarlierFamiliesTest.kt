package com.coparently.app.presentation.family

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.placementFamilyId
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Family → Earlier families (owner decision, 25 October 2026): which families ended, and what
 * each holds — the tester case of Zuzana's expenses showing in the family with Lana.
 */
class EarlierFamiliesTest {

    private val zuzanaFamily = FamilyKey.of(ME, ZUZANA)
    private val lanaFamily = FamilyKey.of(ME, LANA)
    private val withLana = FamilyView(me = ME, onScreen = lanaFamily, others = emptySet())

    @Test
    fun `an ended family is listed with its own records and its kept name`() {
        val records = EveryFamilyRecords(
            events = listOf(event("e1", zuzanaFamily, ME, day = 3), event("e2", lanaFamily, ME, day = 9)),
            expenses = listOf(expense("x1", zuzanaFamily, ZUZANA), expense("x2", lanaFamily, LANA))
        )
        val known = listOf(KnownFamilyEntity(zuzanaFamily, ZUZANA, coParentName = "Zuzana", updatedAtMillis = 1L))

        val families = EarlierFamilies.of(withLana, records, known)

        assertEquals(listOf(zuzanaFamily), families.map { it.familyId })
        val zuzana = families.single()
        assertEquals("Zuzana", zuzana.coParentName)
        assertEquals(ZUZANA, zuzana.coParentUid)
        assertEquals(listOf("e1"), zuzana.events.map { it.id })
        assertEquals(listOf("x1"), zuzana.expenses.map { it.id })
        assertEquals(LocalDate.of(2026, 5, 3), zuzana.lastDay)
    }

    @Test
    fun `a co-parent's record of no family belongs to the family with them, this parent's own to none`() {
        val records = EveryFamilyRecords(
            expenses = listOf(expense("hers", familyId = null, createdBy = ZUZANA), expense("mine", null, ME))
        )

        val families = EarlierFamilies.of(withLana, records, emptyList())

        assertEquals(listOf("hers"), families.single().expenses.map { it.id })
        assertNull(families.single().coParentName)
    }

    @Test
    fun `a family kept with no record left here is still listed, and a live or foreign one never`() {
        val known = listOf(
            KnownFamilyEntity(zuzanaFamily, ZUZANA, coParentName = "Zuzana", updatedAtMillis = 1L),
            KnownFamilyEntity(lanaFamily, LANA, coParentName = "Lana", updatedAtMillis = 1L)
        )
        val foreign = EveryFamilyRecords(expenses = listOf(expense("x", FamilyKey.of("dan", "erin"), "dan")))

        val families = EarlierFamilies.of(withLana, foreign, known)

        assertEquals(listOf(zuzanaFamily), families.map { it.familyId })
        assertTrue(families.single().expenses.isEmpty())
    }

    @Test
    fun `a private event, a Google import, stays out of the earlier family and is shown in the live one`() {
        val imported = event("google-1", zuzanaFamily, ME, day = 4).copy(isPrivate = true)
        val records = EveryFamilyRecords(events = listOf(imported, event("e1", zuzanaFamily, ME, day = 3)))

        val families = EarlierFamilies.of(withLana, records, emptyList())

        assertEquals(listOf("e1"), families.single().events.map { it.id })
        assertTrue(withLana.shows(imported.placementFamilyId, imported.createdByFirebaseUid))
    }

    private fun event(id: String, familyId: String?, createdBy: String, day: Int) = Event(
        id = id,
        title = id,
        startDateTime = LocalDateTime.of(2026, 5, day, 10, 0),
        eventType = "other",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 5, 1, 9, 0),
        updatedAt = LocalDateTime.of(2026, 5, 1, 9, 0),
        createdByFirebaseUid = createdBy,
        familyId = familyId
    )

    private fun expense(id: String, familyId: String?, createdBy: String) = Expense(
        id = id,
        title = id,
        amount = 10.0,
        currency = "CZK",
        category = ExpenseCategory.entries.first(),
        paidBy = createdBy,
        date = LocalDate.of(2026, 5, 1),
        createdByFirebaseUid = createdBy,
        familyId = familyId
    )

    private companion object {
        const val ME = "uid-me"
        const val ZUZANA = "uid-zuzana"
        const val LANA = "uid-lana"
    }
}
