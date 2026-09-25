package com.coparently.app.domain.family

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which co-parent an event's audience names (MON-8): the other parent of the event's own family
 * while that relationship is live, otherwise the family on screen — so a school import into one
 * family never shares a child's timetable with the other family's adult.
 */
class FamilyAudienceTest {

    private val bobFamily = FamilyKey.of("alice", "bob")
    private val carolFamily = FamilyKey.of("alice", "carol")

    @Test
    fun `an event of a family not on screen is shared with that family's co-parent`() {
        val partner = FamilyAudience.partnerFor(carolFamily, "alice", listOf("bob", "carol"), selectedPartner = "bob")
        assertEquals("carol", partner)
    }

    @Test
    fun `an event of the family on screen is shared as before`() {
        val partner = FamilyAudience.partnerFor(bobFamily, "alice", listOf("bob", "carol"), selectedPartner = "bob")
        assertEquals("bob", partner)
    }

    @Test
    fun `a family that has ended never brings its ex-partner back`() {
        assertEquals("bob", FamilyAudience.partnerFor(carolFamily, "alice", listOf("bob"), selectedPartner = "bob"))
        assertNull(FamilyAudience.partnerFor(carolFamily, "alice", emptyList(), selectedPartner = null))
    }

    @Test
    fun `an event with no family, or one the signed-in parent is not in, falls back to the family on screen`() {
        assertEquals("bob", FamilyAudience.partnerFor(null, "alice", listOf("bob"), selectedPartner = "bob"))
        assertEquals("bob", FamilyAudience.partnerFor("", "alice", listOf("bob"), selectedPartner = "bob"))
        val strangers = FamilyKey.of("dan", "erin")
        assertEquals("bob", FamilyAudience.partnerFor(strangers, "alice", listOf("bob", "dan"), "bob"))
        assertNull(FamilyAudience.partnerFor(null, "alice", emptyList(), selectedPartner = ""))
    }
}
