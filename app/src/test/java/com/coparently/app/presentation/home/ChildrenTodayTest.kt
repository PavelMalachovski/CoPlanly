package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.presentation.common.FamilyMember
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's handover hero on a day the children are apart (FAM-4): each child is named with the
 * parent they are with, and on every other day — and in every family below two children and one
 * override — the hero says nothing new.
 */
class ChildrenTodayTest {

    private val start = LocalDate.of(2026, 9, 7) // a Monday
    private val slotTwoWeek = start.plusDays(7)
    private val baby = ChildScheduleOverride("baby", patternDays = 1, momDayIndices = setOf(0), startDate = start)
    private val family = CustodyModel.weekOnWeekOff(id = "m1", startDate = start).copy(childOverrides = listOf(baby))
    private val members = listOf(
        FamilyMember(FamilyMemberRef.Child("baby"), "Ema"),
        FamilyMember(FamilyMemberRef.Child("big"), "Tomáš"),
        FamilyMember(FamilyMemberRef.Pet("rex"), "Rex")
    )

    @Test
    fun `a day the children are with different parents names each of them, pets never`() {
        val lines = ChildrenToday.of(slotTwoWeek, family, emptyMap(), members)

        assertEquals(listOf(ChildWithParent("Ema", "mom"), ChildWithParent("Tomáš", "dad")), lines)
    }

    @Test
    fun `a day they are all with the same parent adds nothing`() {
        assertTrue(ChildrenToday.of(start, family, emptyMap(), members).isEmpty())
    }

    @Test
    fun `an accepted swap moves the children who follow the family, and only them`() {
        val swaps = mapOf(
            start.toString() to DayOverride(
                toParent = "dad",
                requestedBy = "u",
                requestedAt = "2026-09-01T10:00:00",
                status = DayOverrideStatus.ACCEPTED
            )
        )

        val lines = ChildrenToday.of(start, family, swaps, members)

        assertEquals(listOf(ChildWithParent("Ema", "mom"), ChildWithParent("Tomáš", "dad")), lines)
    }

    @Test
    fun `one child, or no override, or no schedule, never grows the hero`() {
        assertTrue(ChildrenToday.of(slotTwoWeek, family, emptyMap(), members.take(1)).isEmpty())
        val noOverrides = family.copy(childOverrides = emptyList())
        assertTrue(ChildrenToday.of(slotTwoWeek, noOverrides, emptyMap(), members).isEmpty())
        assertTrue(ChildrenToday.of(slotTwoWeek, null, emptyMap(), members).isEmpty())
    }
}
