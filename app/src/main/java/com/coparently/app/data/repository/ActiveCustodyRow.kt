package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.CustodyModelEntity

/**
 * Which family's custody rows the signed-in parent is asking about.
 *
 * @property familyId The family on screen — `FamilyKey.of` the two parents, the id of the family's
 *   `custody_models` document — or null while the parent has no co-parent.
 * @property soleFamily True while the parent has exactly one co-parent, so a row that names no
 *   family is unambiguously that family's.
 */
internal data class CustodyScope(val familyId: String?, val soleFamily: Boolean)

/**
 * The active custody row that answers for one family (schema 45).
 *
 * The phone holds one active row per family it has followed, plus any row that names no family —
 * saved while the parent was unpaired, or written before rows carried their family. It used to
 * hold one active row for every family, so a parent in two families read the first family's
 * schedule as the second's, and the mirror, the sync's publish and the pattern editor then wrote it
 * into the second family's document. Every custody question the repository answers from Room goes
 * through [pick], and every write that could reach a family's document checks [belongsTo].
 */
internal object ActiveCustodyRow {

    /** By the instant that orders two phones' writes, then by id so that a tie is stable. */
    private val NEWEST = compareBy<CustodyModelEntity>({ it.lastModifiedAtMillis }, { it.id })

    /**
     * The newest active row that answers for [scope]'s family, or null.
     *
     * - **A family on screen** reads its own rows, and a row that names no family while it is the
     *   parent's only one. Never another family's row, however new: the newest row on the phone
     *   is how the wrong family's schedule was read before.
     * - **No family** (unpaired, or signed out) reads a row saved without one, else the newest
     *   row of any family: what unpairing always left on the calendar. It is read, never pushed —
     *   there is no family to push it to.
     */
    fun pick(rows: List<CustodyModelEntity>, scope: CustodyScope): CustodyModelEntity? {
        val active = rows.filter { it.isActive }
        val family = scope.familyId
        val candidates = if (family == null) {
            active.filter { it.familyId == null }.ifEmpty { active }
        } else {
            active.filter { belongsTo(it, family, scope.soleFamily) }
        }
        return candidates.maxWithOrNull(NEWEST)
    }

    /**
     * Whether [row] is [familyId]'s schedule: stamped with it, or naming no family while the
     * parent has exactly one ([soleFamily]). With two, nothing says which family an unstamped row
     * was, and a guess is how one family's schedule reaches the other's document — the rule
     * `FamilyAudience.recordPartnerFor` applies to a child's record, for the same reason.
     */
    fun belongsTo(row: CustodyModelEntity, familyId: String, soleFamily: Boolean): Boolean =
        row.familyId == familyId || (row.familyId == null && soleFamily)
}
