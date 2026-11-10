package com.coparently.app.domain.family

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The two parents' slots **in one family** (per-family slots, 25 October 2026, owner decision):
 * `families/{id}.slots`, as this phone last read it.
 *
 * A slot used to be one per person (`users/{uid}.role`), which is why two parents who both
 * already co-parented elsewhere in the same slot could not link. The family is the truth now; the
 * profile's `role` is this parent's slot in their first family, the mirror older builds read.
 *
 * @property bySlotOwner The slot of each of the family's two uids.
 */
data class FamilySlots(val bySlotOwner: Map<String, String>) {
    /** [uid]'s slot in this family, or null when the family does not say. */
    fun of(uid: String?): String? = uid?.let { bySlotOwner[it] }
}

/** Where a family's slots are read from: `known_families` on the phone. */
interface FamilySlotsSource {

    /** [familyId]'s slots, followed; null while the phone has not read them. */
    fun observe(familyId: String): Flow<FamilySlots?>

    /** [familyId]'s slots now, or null while the phone has not read them. */
    suspend fun get(familyId: String): FamilySlots?

    /** No slots known: every caller falls back to the profile's `role`. For tests and fakes. */
    object None : FamilySlotsSource {
        override fun observe(familyId: String): Flow<FamilySlots?> = flowOf(null)
        override suspend fun get(familyId: String): FamilySlots? = null
    }
}
