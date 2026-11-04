package com.coparently.app.presentation.calendar

import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which custody band the calendar grid draws (FAM-4): the family's, unless the member filter is
 * exactly one child who follows a schedule of their own.
 */
class ChildCustodyBandTest {

    private val start = LocalDate(2026, 9, 7) // a Monday
    private val slotTwoWeek = start.plus(7, DateTimeUnit.DAY)
    private val baby = ChildScheduleOverride(
        childId = "baby",
        patternDays = 1,
        momDayIndices = setOf(0),
        startDate = start,
        contactWindows = listOf(ContactWindow(0, LocalTime(15, 0), LocalTime(17, 0), "dad"))
    )
    private val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = start)
        .copy(childOverrides = listOf(baby))
    private val familyCustody = CustodyResolver.resolver(model, emptyMap()) { null }
    private val familyWindows = CustodyResolver.contactWindowsResolver(model, familyCustody)
    private val family = GridCustody(
        custody = familyCustody,
        proposed = { null },
        windows = familyWindows
    )

    @Test
    fun `without a filter or with two children or with a pet the grid keeps the family band`() {
        listOf(
            emptyList(),
            listOf(FamilyMemberRef.Child("baby"), FamilyMemberRef.Child("big")),
            listOf(FamilyMemberRef.Child("baby"), FamilyMemberRef.Pet("rex")),
            listOf(FamilyMemberRef.Child("big"))
        ).forEach { filter ->
            assertSame(family, ChildCustodyBand.of(model, null, filter, family), filter.toString())
        }
    }

    @Test
    fun `filtered to the one child with an override the band is that child's`() {
        val grid = ChildCustodyBand.of(model, null, listOf(FamilyMemberRef.Child("baby")), family)

        assertEquals("dad", family.custody(slotTwoWeek))
        assertEquals("mom", grid.custody(slotTwoWeek))
        assertFalse(grid.followsFamily)
        assertTrue(family.followsFamily)
        // The child's own afternoon with the other parent is drawn on the child's band.
        assertEquals(listOf("dad"), grid.windows(slotTwoWeek).map { it.parent })
        assertNull(grid.proposed(slotTwoWeek))
    }

    @Test
    fun `a pending proposal previews the child's proposed schedule or the family's when it drops it`() {
        val filter = listOf(FamilyMemberRef.Child("baby"))
        val dropsIt = model.copy(childOverrides = emptyList())
        val movesIt = model.copy(childOverrides = listOf(baby.copy(momDayIndices = emptySet())))

        assertEquals("dad", ChildCustodyBand.of(model, dropsIt, filter, family).proposed(slotTwoWeek))
        assertEquals("mom", ChildCustodyBand.of(model, dropsIt, filter, family).proposed(start))
        assertEquals("dad", ChildCustodyBand.of(model, movesIt, filter, family).proposed(start))
    }
}
