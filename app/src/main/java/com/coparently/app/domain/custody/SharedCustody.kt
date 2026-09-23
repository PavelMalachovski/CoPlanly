package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel

/**
 * The pair's shared custody document: the pattern, plus what only the shared copy knows.
 *
 * [CustodyModel] is the pattern and deliberately stays that — it is what the calendar asks
 * "who has the child on this date". Who last changed it and when are facts about the
 * *document*, not about the schedule, and they exist only because two people write to it.
 *
 * @property model The custody pattern itself, carrying the id its writer gave it.
 * @property lastModifiedBy Firebase UID of whoever wrote the document last. Compared against
 *   the signed-in uid to tell "the co-parent changed the schedule" from this device's own echo.
 * @property lastModifiedAtMillis When that write happened, epoch millis. Not a wall clock:
 *   `CustodyModelRepository.isNewer` compares it and re-pushes the side it judges newer over
 *   the other, so it has to mean the same thing on both parents' phones (SEC-4). It still
 *   crosses the wire as an ISO string, in UTC — see [CustodyTimestamp] for why the field's name
 *   and type had to stay exactly as they were.
 * @property createdAt ISO date-time string of when the pair's arrangement was first written.
 *   Preserved across updates, so editing the pattern does not re-date the arrangement.
 * @property repeatYearly Mirrors `CustodyModelEntity.repeatYearly`. Always true for MVP; it
 *   lives on the entity rather than on [CustodyModel], which is why it travels here.
 * @property proposal A pattern awaiting the co-parent's answer, or null when nothing is pending.
 *   Deliberately orthogonal to [lastModifiedBy] and [lastModifiedAtMillis]: a proposal write touches
 *   neither, so proposing right after the co-parent changed the pattern cannot make their
 *   not-yet-dismissed change read as this device's own echo and swallow its banner. See
 *   [CustodyProposalTransition].
 * @property lastDecision The most recent answer to a proposal, or null until the first one. Kept
 *   so the proposer learns the outcome; only the latest is retained.
 * @property dayOverrides One-off day swaps keyed by ISO date. Absent from the document reads as
 *   an empty map, never null: every document written before this field existed has no such key,
 *   and a null would make every caller test for two shapes of "none". See [DayOverride].
 * @property lastSwapDate The ISO date the last swap write touched, or null when the last write
 *   was not a swap. It exists for `firestore.rules`: Rules cannot iterate a map, so a swap write
 *   names the one date it changes and the rule then requires the diff to affect only that key —
 *   which makes naming the wrong date self-defeating rather than merely useless.
 * @property lastModifiedKind What the last write to this document actually changed. It exists
 *   because `firestore.rules` requires every update to stamp [lastModifiedBy] with the caller,
 *   so a swap write cannot leave the field alone — and without this marker the co-parent's
 *   device would read that stamp as a pattern change and raise the "the schedule changed under
 *   you" banner for a day nobody has agreed to yet. See [CustodyWriteKind].
 * @property contactWindowsWire The document's `contactWindows` list **exactly as stored**, or null
 *   when the document has no such key (MON-6b). [model]'s `contactWindows` are its decoded form;
 *   this is kept beside them for two reasons, both about builds that disagree:
 *   - **A proposal or swap write must send the list back byte for byte.** `firestore.rules`
 *     refuses such a write if it changes `contactWindows`, and those writes re-send the whole
 *     document, so a list re-encoded from the model would be refused the day a newer build wrote
 *     an entry this one cannot decode. Only a pattern write (`CustodyModelRepository.
 *     pushToFirestore`, or accepting a proposal) replaces it, from the model.
 *   - **Null is not "no windows".** A build that predates the field rewrites the whole document
 *     without the key on every save. Reading that as "the windows were removed" would let an
 *     older co-parent's ordinary swap erase the contact afternoons from this device's calendar,
 *     so the mirror keeps its own copy when the key is absent — and this build always writes the
 *     key, as `[]` when there are none, so a removal it makes is a real, explicit empty list.
 * @property seasonalLayersWire The document's `seasonalLayers` list **exactly as stored**, or null
 *   when the document has no such key (MON-14). The same two rules as [contactWindowsWire], for
 *   the same reasons: a proposal or swap write sends it back byte for byte (`firestore.rules`'
 *   `seasonalLayersKeptOrDropped`), and a missing key is an older build's write, never "no
 *   layers" — the mirror keeps its own copy, and this build always writes the key on a pattern
 *   write. [model]'s `seasonalLayers`/`unreadableLayers` are the decoded form.
 */
data class SharedCustody(
    val model: CustodyModel,
    val lastModifiedBy: String,
    val lastModifiedAtMillis: Long,
    val createdAt: String,
    val repeatYearly: Boolean = true,
    val proposal: CustodyProposal? = null,
    val lastDecision: CustodyDecision? = null,
    val dayOverrides: Map<String, DayOverride> = emptyMap(),
    val lastSwapDate: String? = null,
    val lastModifiedKind: CustodyWriteKind = CustodyWriteKind.PATTERN,
    val contactWindowsWire: List<String>? = null,
    val seasonalLayersWire: List<String>? = null
)

/**
 * What a write to the shared custody document changed.
 *
 * Needed because two rules pull in opposite directions. `firestore.rules` requires every update
 * to carry `lastModifiedBy == request.auth.uid`, so **no** write can leave that field as it was;
 * meanwhile `CustodyChangeAnnouncement` reads exactly that field to decide whether the co-parent
 * changed the agreed schedule. Without a marker, offering or answering a one-off swap — which
 * changes no pattern at all — would raise "your co-parent changed the schedule" on the other
 * phone, about a day neither parent has agreed to. A swap has its own channel: the inbox, and the
 * arrows on the grid.
 *
 * [PATTERN] is the default, and that is the right default for a document written by a build that
 * predates this field: those builds only ever wrote patterns.
 */
enum class CustodyWriteKind {
    /** The agreed pattern itself changed. The banner's business. */
    PATTERN,

    /** Only [SharedCustody.dayOverrides] changed — an offer, or an answer to one. */
    SWAP
}
