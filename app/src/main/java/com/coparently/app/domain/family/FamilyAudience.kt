package com.coparently.app.domain.family

/**
 * Which co-parent a record's audience names: the other parent of the family the record belongs
 * to, while that relationship is live — and nobody otherwise.
 *
 * `UserEntity.partnerId` is the family the device is **showing** (M-8), and the write paths used to
 * read it for every record. That is right for a record made on screen — it is made in the family on
 * screen — and wrong for everything else: a school import (MON-8) runs in the background for the
 * family its connection names, an event of a family that has **ended** belongs to that family's
 * history, and a record written before any pairing belongs to no family yet. Naming the shown
 * family's co-parent for those is how one family's records reached the other family's adult
 * (October 2026 isolation audit: an ended family's whole event history was re-uploaded to the next
 * co-parent on the first sync after a new pairing, with no action by the parent).
 *
 * So events, children's and pets' records follow **one** rule, [recordPartnerFor]; [partnerFor] is
 * the name the event paths call it by. Who reads a record is decided by the record, never by the
 * screen — except for a record of no family while the parent has exactly one co-parent, where the
 * family on screen is the only family there is.
 */
object FamilyAudience {

    /**
     * The co-parent to share an event of [familyId] with, or null when the event must name none.
     *
     * The same answer as [recordPartnerFor], and deliberately so: an event of an ended family names
     * nobody (it stays this parent's history), and an event of no family names nobody while there
     * are two or more co-parents (the parent assigns it — "Records without a family"). It used to
     * fall back to the family on screen in both cases.
     *
     * @param familyId The event's family, or null for an event that belongs to nobody yet.
     * @param myUid The signed-in parent.
     * @param livePartners Every co-parent the signed-in parent is paired with now.
     * @param selectedPartner The co-parent of the family on screen.
     */
    fun partnerFor(
        familyId: String?,
        myUid: String,
        livePartners: Collection<String>,
        selectedPartner: String?
    ): String? = recordPartnerFor(familyId, myUid, livePartners, selectedPartner)

    /**
     * The co-parent to share a child's or a pet's record of [familyId] with, or null when the
     * record's audience must name no co-parent at all.
     *
     * The record's own family decides, and **nothing else ever does**: unlike [partnerFor] there
     * is no fallback to the family on screen. A child's record carries health data (GDPR Art. 9 —
     * medications, allergies, the medical profile), and the fallback is how a parent in two
     * families leaked it: every upload named the co-parent of the family the phone was showing, so
     * switching to the second family and syncing rewrote every child of the first with the second
     * family's parent in `sharedWith`. So:
     *
     * - **A record of a family** names that family's other parent while they are one of
     *   [coParents], and nobody once the relationship has ended — an ex-partner is not re-admitted
     *   by an upload, and the family on screen is never a stand-in for the record's own.
     * - **A record of no family** (written before its parent paired, and not stamped since) names
     *   the family on screen while the parent has exactly one co-parent, which is what it always
     *   did and is unambiguous. With two or more it names **nobody**: nothing says which family it
     *   belongs to, and a guess hands a child's record to an adult it was never about. The server
     *   refuses the same guess (`stampOwnBlankFamilyIds`, `ambiguous`).
     * - **A family the signed-in parent is not in** names nobody.
     *
     * Takes the same inputs as [partnerFor], read the same way, so the two cannot disagree about
     * who the co-parents are.
     *
     * @param familyId The record's family, or null for a record that names none.
     * @param myUid The signed-in parent.
     * @param livePartners Every co-parent the signed-in parent is paired with now, as their row
     *   stores the list.
     * @param selectedPartner The co-parent of the family on screen.
     */
    fun recordPartnerFor(
        familyId: String?,
        myUid: String,
        livePartners: Collection<String>,
        selectedPartner: String?
    ): String? {
        val coParents = coParents(myUid, livePartners, selectedPartner)
        val stamped = familyId?.takeIf { it.isNotBlank() }
            ?: return if (coParents.size > 1) null else selectedPartner?.takeIf { it in coParents }
        return otherParent(stamped, myUid)?.takeIf { it in coParents }
    }

    /**
     * Every co-parent the signed-in parent has now, without blanks and without themselves.
     *
     * [livePartners] is the answer, and the family on screen is added to it: the selection is one
     * of them by construction (`SelectedFamilySource` selects only among them), and a row written
     * before the list existed (schema 31) carries the selection with an empty list until its first
     * sync brings the list down. Without the union, such a row would read as nobody's co-parent.
     *
     * @param myUid The signed-in parent.
     * @param livePartners Every co-parent the signed-in parent is paired with now.
     * @param selectedPartner The co-parent of the family on screen, or null.
     */
    fun coParents(
        myUid: String,
        livePartners: Collection<String>,
        selectedPartner: String?
    ): Set<String> =
        (livePartners + listOfNotNull(selectedPartner))
            .filter { it.isNotBlank() && it != myUid }
            .toSet()

    /** The other member of [familyId] seen from [myUid], or null when the id does not name them. */
    private fun otherParent(familyId: String, myUid: String): String? {
        val (first, second) = FamilyKey.membersOf(familyId) ?: return null
        return when (myUid) {
            first -> second
            second -> first
            else -> null
        }
    }
}
