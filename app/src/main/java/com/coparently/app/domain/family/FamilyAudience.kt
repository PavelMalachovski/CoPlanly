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
        val (first, second) = familyId?.takeIf { it.isNotBlank() }?.let(FamilyKey::membersOf)
            ?: (null to null)
        val other = when (myUid) {
            first -> second
            second -> first
            else -> null
        }
        return other?.takeIf { it in livePartners } ?: selectedPartner?.takeIf { it.isNotBlank() }
    }
}
