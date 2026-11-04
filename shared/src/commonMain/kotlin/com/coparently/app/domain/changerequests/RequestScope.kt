package com.coparently.app.domain.changerequests

import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event

/**
 * The family the requests on screen belong to: this parent, the co-parent of the family on screen,
 * and that pair's family id (phase 2, spec §6.3).
 *
 * A parent in two families sees one family's requests at a time — the inbox, Home's Requests row
 * and the calendar banner all answer for the family on screen, and another family's waiting items
 * are what the family switcher's dot says. A request or event from a family that has ended (an
 * ex-partner) is covered by no scope at all: it is no longer answerable, and the export keeps it.
 *
 * @property myUid The signed-in parent.
 * @property coParentUid The co-parent of the family on screen.
 * @property familyId `FamilyKey.of(myUid, coParentUid)`.
 * @property soleFamily Whether this parent has one co-parent only. An event that names no family
 *   is counted in only then: with two families, nothing says which of them it belongs to.
 */
data class RequestScope(
    val myUid: String,
    val coParentUid: String,
    val familyId: String,
    val soleFamily: Boolean
) {

    /** Whether [request] is between this parent and the co-parent on screen, in either direction. */
    fun covers(request: ChangeRequest): Boolean =
        (request.requestedBy == myUid && request.requestedTo == coParentUid) ||
            (request.requestedBy == coParentUid && request.requestedTo == myUid)

    /**
     * Whether [event] belongs to this family: one of the two parents created it, and it names this
     * family — or names none while this parent has no other family.
     */
    fun covers(event: Event): Boolean {
        if (!isPair(event.createdByFirebaseUid)) return false
        val stamped = event.familyId?.takeIf { it.isNotBlank() } ?: return soleFamily
        return stamped == familyId
    }

    /** Whether [uid] is one of the two parents of this family. */
    fun isPair(uid: String?): Boolean = uid != null && (uid == myUid || uid == coParentUid)

    /** The other parent of this family, seen from [uid]: the co-parent for me, and me for them. */
    fun otherOf(uid: String): String = if (uid == myUid) coParentUid else myUid

    companion object {
        /**
         * The scope of the family on screen, or null while there is no co-parent to name — a blank
         * uid on either side, or both the same.
         *
         * @param coParents Every co-parent this parent has now, the family on screen included.
         */
        fun of(myUid: String?, coParentUid: String?, coParents: Set<String>): RequestScope? {
            val familyId = FamilyKey.orNull(myUid, coParentUid) ?: return null
            return RequestScope(
                myUid = myUid.orEmpty(),
                coParentUid = coParentUid.orEmpty(),
                familyId = familyId,
                soleFamily = coParents.size <= 1
            )
        }
    }
}
