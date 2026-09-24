package com.coparently.app.domain.journal

import java.time.LocalDate

/**
 * One entry in a parent's private journal (MON-22).
 *
 * **Local-only, and that is the product**: an entry lives in this phone's encrypted Room database
 * and nowhere else — no Firestore collection, no rule, no sync path, no backup. A journal about the
 * other parent that reached them would be a different and worse feature (ROADMAP MON-22). The one
 * way an entry leaves the phone is the parent choosing to put it in an export (MON-3), where it is
 * labelled as their own private note.
 *
 * @property entryDate The day the entry is *about*, which the parent picks — not when it was written.
 * @property createdAtMillis When it was first saved, by this phone's clock.
 * @property updatedAtMillis When it was last saved, by this phone's clock.
 * @property familyId The co-parenting relationship it was written in, stamped at create and never
 *   re-derived (CLAUDE.md item 18); null when written while unpaired. It decides which family's
 *   export may carry the entry, not who can read it — nobody else can.
 * @property createdByFirebaseUid The account that wrote it; every read is scoped to it.
 */
data class JournalEntry(
    val id: String,
    val entryDate: LocalDate,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val familyId: String?,
    val createdByFirebaseUid: String
)
