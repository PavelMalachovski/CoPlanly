package com.coparently.app.domain.custody

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Per-child custody overrides (FAM-4): the wire form, and the three questions the grid and Home
 * ask of them. The wire strings are the ones `firestore-tests/rules/custody-models.test.js` and
 * `functions/test/calendar-feed.test.js` use.
 */
class ChildScheduleOverrideTest {

    private val start = LocalDate.of(2026, 9, 7) // a Monday
    private val family = CustodyModel.weekOnWeekOff(id = "m1", startDate = start)
    private val familyResolver = CustodyResolver.resolver(family, emptyMap()) { null }

    private fun date(iso: String) = LocalDate.parse(iso)

    // ---- the wire form ------------------------------------------------------

    @Test
    fun `an override round-trips through its wire string, windows included`() {
        val override = ChildScheduleOverride(
            childId = "teen-1",
            patternDays = 14,
            momDayIndices = (7..13).toSet(),
            startDate = start,
            contactWindows = listOf(ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "mom"))
        )
        val wire = ChildOverrideCodec.encode(override)

        assertEquals(TEEN, wire)
        assertEquals(override, ChildOverrideCodec.decode(wire))
        assertEquals(FamilyMemberRef.Child("teen-1"), override.member)
    }

    @Test
    fun `the canonical list is sorted, de-duplicated and keeps unreadable entries verbatim`() {
        val decoded = ChildOverrideCodec.decodeAll(listOf(TEEN, "C2;from-the-future", BABY, TEEN, 42))

        assertEquals(listOf("baby-1", "teen-1"), decoded.overrides.map { it.childId })
        assertEquals(listOf("C2;from-the-future"), decoded.unreadable)
        val encoded = ChildOverrideCodec.encodeAll(decoded.overrides, decoded.unreadable)
        assertEquals(listOf(BABY, TEEN, "C2;from-the-future").sorted(), encoded)
        val again = ChildOverrideCodec.decodeAll(encoded)
        assertEquals(encoded, ChildOverrideCodec.encodeAll(again.overrides, again.unreadable))
    }

    @Test
    fun `a second entry for the same child is kept, not read`() {
        val other = "C1;child:baby-1;2026-09-07;1;;"
        val decoded = ChildOverrideCodec.decodeAll(listOf(BABY, other))

        assertEquals(1, decoded.overrides.size)
        assertEquals(1, decoded.unreadable.size)
        assertEquals(listOf(BABY, other).sorted(), ChildOverrideCodec.encodeAll(decoded.overrides, decoded.unreadable))
    }

    @Test
    fun `entries that are not a readable child override are unreadable, never guessed`() {
        listOf(
            "C2;child:baby-1;2026-09-07;1;0;",
            "C1;pet:rex;2026-09-07;1;0;",
            "C1;grandma:1;2026-09-07;1;0;",
            "C1;child:;2026-09-07;1;0;",
            "C1;child:a b;2026-09-07;1;0;",
            "C1;child:baby-1;2026-02-30;1;0;",
            "C1;child:baby-1;2026-09-07;0;;",
            "C1;child:baby-1;2026-09-07;+14;0;",
            "C1;child:baby-1;2026-09-07;400;0;",
            "C1;child:baby-1;2026-09-07;14;14;",
            "C1;child:baby-1;2026-09-07;14;0;14|15:00|19:00|dad",
            "C1;child:baby-1;2026-09-07;14;0;2|19:00|15:00|dad",
            "C1;child:baby-1;2026-09-07;1;0"
        ).forEach { assertNull(ChildOverrideCodec.decode(it), it) }
    }

    @Test
    fun `a null or empty list is no overrides, and none encodes as an empty list`() {
        assertEquals(DecodedChildOverrides(), ChildOverrideCodec.decodeAll(null))
        assertEquals(emptyList<String>(), ChildOverrideCodec.encodeAll(emptyList()))
    }

    @Test
    fun `complementing flips the days and the windows`() {
        val teen = requireNotNull(ChildOverrideCodec.decode(TEEN))
        val flipped = teen.withOtherParent()

        assertEquals((0..6).toSet(), flipped.momDayIndices)
        assertEquals("dad", flipped.contactWindows.single().parent)
        assertEquals(teen, flipped.withOtherParent())
    }

    // ---- resolution ---------------------------------------------------------

    @Test
    fun `an override answers alone on every date, before its anchor too`() {
        val baby = requireNotNull(ChildOverrideCodec.decode(BABY))
        listOf("2026-09-07", "2026-09-14", "2026-08-31", "2027-01-01").forEach {
            assertEquals("mom", baby.custodyFor(date(it)), it)
        }
    }

    @Test
    fun `the grid follows an override only when the filter is exactly one child who has one`() {
        val overrides = ChildOverrideCodec.decodeAll(listOf(BABY, TEEN)).overrides
        val baby = FamilyMemberRef.Child("baby-1")

        assertEquals("baby-1", ChildCustody.overrideForFilter(listOf(baby), overrides)?.childId)
        assertNull(ChildCustody.overrideForFilter(emptyList(), overrides))
        assertNull(ChildCustody.overrideForFilter(listOf(baby, FamilyMemberRef.Child("teen-1")), overrides))
        assertNull(ChildCustody.overrideForFilter(listOf(baby, FamilyMemberRef.Pet("rex")), overrides))
        assertNull(ChildCustody.overrideForFilter(listOf(FamilyMemberRef.Child("middle-1")), overrides))
    }

    @Test
    fun `without an override the grid keeps the family resolver itself`() {
        assertSame(familyResolver, ChildCustody.resolver(null, familyResolver))
    }

    @Test
    fun `neither an accepted swap nor a seasonal layer moves an overridden child`() {
        val swapped = date("2026-09-08")
        val lateSeptember = date("2026-09-20")..date("2026-09-30")
        val withSwapAndLayer = family.copy(
            seasonalLayers = listOf(SeasonalLayer.allWith("late-sept", "Late September", lateSeptember, "dad"))
        )
        val swaps = mapOf(
            swapped.toString() to DayOverride(
                toParent = "dad",
                requestedBy = "u",
                requestedAt = "2026-09-01T10:00:00",
                status = DayOverrideStatus.ACCEPTED
            )
        )
        val familyWithBoth = CustodyResolver.resolver(withSwapAndLayer, swaps) { null }
        val baby = ChildCustody.resolver(ChildOverrideCodec.decode(BABY), familyWithBoth)

        assertEquals("dad", familyWithBoth(swapped))
        assertEquals("mom", baby(swapped))
        assertEquals("dad", familyWithBoth(date("2026-09-22")))
        assertEquals("mom", baby(date("2026-09-22")))
    }

    @Test
    fun `an override's windows are drawn only when they name the other parent`() {
        val teen = requireNotNull(ChildOverrideCodec.decode(TEEN))
        val windows = ChildCustody.contactWindowsResolver(teen)

        // Index 2 of a cycle whose first week is slot 2's: Mum's afternoon on Dad's day shows.
        assertEquals(listOf("mom"), windows(date("2026-09-09")).map { it.parent })
        assertTrue(windows(date("2026-09-10")).isEmpty())
    }

    // ---- Home's hero --------------------------------------------------------

    @Test
    fun `the hero names each child when they are with different parents`() {
        val overrides = ChildOverrideCodec.decodeAll(listOf(BABY)).overrides
        // 2026-09-14 opens the family's slot-2 week; the baby stays with slot 1.
        val where = ChildCustody.whereaboutsOn(date("2026-09-14"), listOf("baby-1", "big-1"), overrides, familyResolver)

        assertEquals(listOf(ChildWhereabouts("baby-1", "mom"), ChildWhereabouts("big-1", "dad")), where)
    }

    @Test
    fun `the hero stays the family sentence when the children agree`() {
        val overrides = ChildOverrideCodec.decodeAll(listOf(BABY)).overrides
        // A slot-1 week: everybody is with the same parent.
        assertTrue(ChildCustody.whereaboutsOn(start, listOf("baby-1", "big-1"), overrides, familyResolver).isEmpty())
    }

    @Test
    fun `it appears at two children and one override, never at one`() {
        val overrides = ChildOverrideCodec.decodeAll(listOf(BABY)).overrides
        val day = date("2026-09-14")

        assertTrue(ChildCustody.whereaboutsOn(day, listOf("baby-1"), overrides, familyResolver).isEmpty())
        assertTrue(ChildCustody.whereaboutsOn(day, listOf("a-1", "b-1"), overrides, familyResolver).isEmpty())
        assertTrue(ChildCustody.whereaboutsOn(day, listOf("a-1", "b-1"), emptyList(), familyResolver).isEmpty())
    }

    // ---- on the model, in a proposal ----------------------------------------

    private fun withBaby() = family.copy(childOverrides = ChildOverrideCodec.decodeAll(listOf(BABY)).overrides)

    @Test
    fun `complementing the family pattern flips each child's own schedule too`() {
        val flipped = withBaby().complemented()

        assertEquals("dad", flipped.childOverrideFor("baby-1")?.custodyFor(start))
        assertEquals(listOf(BABY), flipped.complemented().childOverridesWire())
    }

    @Test
    fun `two patterns that differ only in a child's schedule are not equivalent`() {
        assertTrue(family.isEquivalentTo(family.copy()))
        assertFalse(family.isEquivalentTo(withBaby()))
        assertTrue(withBaby().isEquivalentTo(withBaby()))
    }

    @Test
    fun `a proposal that only moves a child is never described as changing nothing`() {
        val diff = CustodyPatternDiff.of(family, withBaby(), from = start)

        assertTrue(diff.childOverridesChanged)
        assertFalse(diff.identical)
        assertTrue(diff.movedDays.isEmpty())
    }

    @Test
    fun `a proposal states the children's schedules and accepting it makes them the agreed ones`() {
        val shared = SharedCustody(model = family, lastModifiedBy = "alice", lastModifiedAtMillis = 1L, createdAt = "")
        val proposed = CustodyProposalTransition.propose(
            current = shared,
            model = withBaby(),
            repeatYearly = true,
            byUid = "alice",
            atIso = "2026-09-07T10:00:00"
        ).getOrThrow()
        assertEquals(listOf(BABY), proposed.proposal?.childOverridesWire)
        // The agreed document's own list is untouched by a proposal write.
        assertNull(proposed.childOverridesWire)

        val accepted = CustodyProposalTransition.accept(proposed, "bob", "2026-09-08T10:00:00", 2L).getOrThrow()
        assertEquals(listOf(BABY), accepted.childOverridesWire)
        assertEquals("mom", accepted.model.childOverrideFor("baby-1")?.custodyFor(date("2026-09-14")))
    }

    private companion object {
        /** A child who is with slot 1 every day — the infant who stays put. */
        const val BABY = "C1;child:baby-1;2026-09-07;1;0;"

        /** Alternate weeks starting with slot 2, and slot 1's Wednesday afternoon in slot 2's week. */
        const val TEEN = "C1;child:teen-1;2026-09-07;14;7,8,9,10,11,12,13;2|15:00|19:00|mom"
    }
}
