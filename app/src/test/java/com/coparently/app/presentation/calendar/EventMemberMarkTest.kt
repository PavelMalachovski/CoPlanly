package com.coparently.app.presentation.calendar

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.presentation.common.FamilyMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * FAM-5: a Day or Week chip names who its event is about — at two members, never at one, and
 * only by name.
 */
class EventMemberMarkTest {

    private val emma = FamilyMember(FamilyMemberRef.Child("c1"), "Emma")
    private val leo = FamilyMember(FamilyMemberRef.Child("c2"), "leo")
    private val max = FamilyMember(FamilyMemberRef.Pet("p1"), "Max")
    private val family = listOf(emma, leo, max)

    @Test
    fun `one member names the event by that member's initial`() {
        val mark = EventMemberMark.of(listOf(emma.ref), family, Locale.ENGLISH)

        assertEquals(EventMemberMark(initial = "E", names = listOf("Emma")), mark)
    }

    @Test
    fun `several members show the first one's initial and a plus, and name them all`() {
        val mark = EventMemberMark.of(listOf(leo.ref, max.ref), family, Locale.ENGLISH)

        assertEquals(EventMemberMark(initial = "L+", names = listOf("leo", "Max")), mark)
    }

    @Test
    fun `a family of one marks nothing`() {
        assertNull(EventMemberMark.of(listOf(emma.ref), listOf(emma), Locale.ENGLISH))
    }

    @Test
    fun `an event about the whole family marks nothing`() {
        assertNull(EventMemberMark.of(emptyList(), family, Locale.ENGLISH))
    }

    @Test
    fun `references the family does not hold are not drawn`() {
        val unknown = FamilyMemberRef.Unknown("goat:g1")
        val deleted = FamilyMemberRef.Child("gone")

        assertNull(EventMemberMark.of(listOf(unknown, deleted), family, Locale.ENGLISH))
        assertEquals(
            EventMemberMark(initial = "M", names = listOf("Max")),
            EventMemberMark.of(listOf(unknown, max.ref), family, Locale.ENGLISH)
        )
    }

    @Test
    fun `a repeated reference counts once`() {
        assertEquals(
            EventMemberMark(initial = "E", names = listOf("Emma")),
            EventMemberMark.of(listOf(emma.ref, emma.ref), family, Locale.ENGLISH)
        )
    }

    @Test
    fun `a Cyrillic name gives a Cyrillic initial`() {
        val sasha = FamilyMember(FamilyMemberRef.Child("c3"), " саша")
        val mark = EventMemberMark.of(listOf(sasha.ref), family + sasha, Locale.forLanguageTag("ru"))

        assertEquals("С", mark?.initial)
        assertEquals(listOf("саша"), mark?.names)
    }
}
