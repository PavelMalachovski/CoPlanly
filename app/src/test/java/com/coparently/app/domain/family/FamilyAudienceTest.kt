package com.coparently.app.domain.family

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which co-parent a record's audience names — an event, a child's or a pet's record alike: its own
 * family's other parent while that relationship is live, and never the family on screen in its
 * place. That fallback is how a parent in two families leaked one family's children to the other
 * family's adult, and how an ended family's events reached the next co-parent (October 2026).
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
    fun `an event of a family that has ended names nobody, not the family on screen`() {
        // The leak: Bob unpaired, Alice paired with Carol, and the first sync re-uploaded every
        // event of the Bob family to Carol.
        assertNull(FamilyAudience.partnerFor(carolFamily, "alice", listOf("bob"), selectedPartner = "bob"))
        assertNull(FamilyAudience.partnerFor(carolFamily, "alice", emptyList(), selectedPartner = null))
    }

    @Test
    fun `an event with no family names the one co-parent there is, and nobody once there are two`() {
        assertEquals("bob", FamilyAudience.partnerFor(null, "alice", listOf("bob"), selectedPartner = "bob"))
        assertEquals("bob", FamilyAudience.partnerFor("", "alice", listOf("bob"), selectedPartner = "bob"))
        assertNull(FamilyAudience.partnerFor(null, "alice", listOf("bob", "carol"), selectedPartner = "carol"))
        assertNull(FamilyAudience.partnerFor(null, "alice", emptyList(), selectedPartner = ""))
    }

    @Test
    fun `an event of a family the signed-in parent is not in names nobody`() {
        val strangers = FamilyKey.of("dan", "erin")
        assertNull(FamilyAudience.partnerFor(strangers, "alice", listOf("bob", "dan"), "bob"))
    }

    // ---- A child's or a pet's record: the record's own family, and never the one on screen ----

    @Test
    fun `a child of the Bob family names Bob while Alice is looking at the Carol family`() {
        // The leak this exists for: Alice switched to the Carol family and every child of the Bob
        // family went up naming Carol — medical profile, allergies and medications included.
        val partner = FamilyAudience.recordPartnerFor(
            bobFamily,
            "alice",
            listOf("bob", "carol"),
            selectedPartner = "carol"
        )
        assertEquals("bob", partner)
    }

    @Test
    fun `a record of a family that has ended names nobody, never the family on screen`() {
        // `partnerFor` answers "carol" here. For a child's record that is the leak itself: the
        // ended family's child is not Carol's to read.
        assertNull(FamilyAudience.recordPartnerFor(bobFamily, "alice", listOf("carol"), "carol"))
        assertNull(FamilyAudience.recordPartnerFor(bobFamily, "alice", emptyList(), null))
    }

    @Test
    fun `a record of no family names nobody while there are two co-parents`() {
        // Written before Alice paired and never stamped: nothing says whether it is the Bob
        // family's or the Carol family's, and a guess hands a child's record to the wrong adult.
        assertNull(FamilyAudience.recordPartnerFor(null, "alice", listOf("bob", "carol"), "carol"))
        assertNull(FamilyAudience.recordPartnerFor("", "alice", listOf("bob", "carol"), "bob"))
    }

    @Test
    fun `a record of no family keeps the family on screen while there is one co-parent`() {
        assertEquals("bob", FamilyAudience.recordPartnerFor(null, "alice", listOf("bob"), "bob"))
        assertNull(FamilyAudience.recordPartnerFor(null, "alice", emptyList(), selectedPartner = null))
    }

    @Test
    fun `a row from before the co-parent list counts the family on screen as live`() {
        // Schema 31 added the list empty; until the first sync fills it, the selection is the only
        // record of the pairing, and a family's record must not lose its co-parent meanwhile.
        assertEquals("bob", FamilyAudience.recordPartnerFor(bobFamily, "alice", emptyList(), "bob"))
        assertEquals(setOf("bob"), FamilyAudience.coParents("alice", emptyList(), "bob"))
    }

    @Test
    fun `a record of a family the signed-in parent is not in names nobody`() {
        val strangers = FamilyKey.of("dan", "erin")
        assertNull(FamilyAudience.recordPartnerFor(strangers, "alice", listOf("bob", "dan"), "bob"))
    }

    @Test
    fun `the co-parent set has no blanks and never the signed-in parent`() {
        assertEquals(
            setOf("bob", "carol"),
            FamilyAudience.coParents("alice", listOf("bob", "", "alice", "carol", "bob"), "carol")
        )
        assertEquals(emptySet<String>(), FamilyAudience.coParents("alice", emptyList(), ""))
    }
}
