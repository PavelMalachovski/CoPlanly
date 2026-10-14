package com.coparently.app.domain.family

/**
 * Which co-parent an event's audience names: the other parent of the family the event belongs
 * to, while that relationship is live.
 *
 * `UserEntity.partnerId` is the family the device is **showing** (M-8), and the event write
 * paths used to read it for every event. That is right for an event made on screen — it is made
 * in the family on screen — and wrong for one that belongs to another family: a school import
 * (MON-8) runs in the background for the family its connection names, whichever one the switcher
 * shows at that moment, and naming the shown family's co-parent would hand one family's
 * child's timetable to the other family's adult.
 *
 * The family's other parent is used **only while they are one of [livePartners]**. After an
 * unpair the pair's id still names them, and re-admitting an ex-partner is exactly what the
 * write paths' intersecting audience exists to prevent (`EventRepositoryImpl.shareTargets`);
 * such an event falls back to what the paths did before, [selectedPartner].
 *
 * A child's or a pet's record is answered by [recordPartnerFor] instead, which never falls back
 * to the family on screen — see there.
 */
object FamilyAudience {

    /**
     * The co-parent to share an event of [familyId] with, or null when there is none.
     *
     * @param familyId The event's family, or null for an event that belongs to nobody yet.
     * @param myUid The signed-in parent.
     * @param livePartners Every co-parent the signed-in parent is paired with now.
     * @param selectedPartner The co-parent of the family on screen; the answer for an event with
     *   no family, or one whose family has ended.
     */
    fun partnerFor(
        familyId: String?,
        myUid: String,
        livePartners: Collection<String>,
        selectedPartner: String?
    ): String? {
        val other = familyId?.takeIf { it.isNotBlank() }?.let { otherParent(it, myUid) }
        return other?.takeIf { it in livePartners } ?: selectedPartner?.takeIf { it.isNotBlank() }
    }

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
