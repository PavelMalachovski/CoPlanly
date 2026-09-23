package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel

/**
 * A custody pattern one parent has put to the other, which changes nobody's calendar until it is
 * accepted.
 *
 * Carried on the pair's shared document rather than in a collection of its own: one document is
 * one listener and one rule block, and accepting is then a single write rather than two that need
 * a transaction to stay consistent. The listener is the component that has already produced one
 * production defect on this path, so the design does not add a second.
 *
 * @property model The proposed pattern. Expressed in the same slot terms as the agreed pattern
 *   (`momDayIndices` means "the days slot 1 has custody"), so `ParentSlotMigrator`'s complement
 *   has to reach it too — see `CustodyModelRepository`.
 * @property repeatYearly Mirrors `CustodyModelEntity.repeatYearly`, which lives on the entity
 *   rather than on [CustodyModel] and so travels alongside it.
 * @property proposedBy Firebase UID of the parent who proposed it. `firestore.rules` requires
 *   this to be the caller, and it is what stops either parent deciding their own proposal.
 * @property proposedAt ISO date-time string, as everywhere else in this Firestore schema — dates
 *   cross the wire as strings, not as Firestore timestamps.
 * @property contactWindowsWire The proposal's own `contactWindows` list exactly as stored, or null
 *   when the proposal sub-map has none — which is what a proposal from a build that predates
 *   MON-6b looks like. Carried verbatim for the reason [SharedCustody.contactWindowsWire] is: a
 *   swap write re-sends the proposal too, and must not change it. [model]'s windows are the
 *   decoded list, or, when this is null, the agreed pattern's own windows — a proposal that could
 *   not express windows is not a proposal to remove them.
 * @property seasonalLayersWire The proposal's own `seasonalLayers` list exactly as stored, or null
 *   when the sub-map has none — a proposal from a build that predates MON-14. Same rule as
 *   [contactWindowsWire]: carried verbatim by a swap write, and when null [model]'s layers are
 *   the agreed pattern's, because a proposal that could not express layers is not a proposal to
 *   remove them.
 */
data class CustodyProposal(
    val model: CustodyModel,
    val repeatYearly: Boolean,
    val proposedBy: String,
    val proposedAt: String,
    val contactWindowsWire: List<String>? = null,
    val seasonalLayersWire: List<String>? = null
)
