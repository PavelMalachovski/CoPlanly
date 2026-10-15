package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The two parents behind one custody document.
 *
 * @property documentId The derived id, from [CustodyKey.of].
 * @property participants Both UIDs, sorted — the order `firestore.rules` requires the stored
 *   array to be in, and to stay in for every later update.
 * @property myUid The signed-in parent, stamped as `lastModifiedBy`.
 */
internal data class CustodyPair(
    val documentId: String,
    val participants: List<String>,
    val myUid: String
)

/**
 * The signed-in parent's place in their families, as `CustodyModelRepository` reads it: which
 * family is on screen, and every co-parent they have (schema 45).
 *
 * @property myUid The signed-in parent, or null.
 * @property pair The family on screen, or null while they have no co-parent.
 * @property coParents Every co-parent they have now.
 */
internal data class SignedInFamilies(
    val myUid: String?,
    val pair: CustodyPair?,
    val coParents: Set<String>
) {
    /** Exactly one co-parent: a row that names no family is that family's. */
    val soleFamily: Boolean get() = coParents.size == 1

    /** What [ActiveCustodyRow.pick] answers for. */
    val scope: CustodyScope get() = CustodyScope(pair?.documentId, soleFamily)

    /**
     * The document [row] may be written to: its own family's while the signed-in parent is still
     * in that family, or — for a row that names no family — the family on screen while it is their
     * only one. Null otherwise, and nothing is written: a row of a family that has ended, or one
     * nothing ties to a family, has no document to go to.
     *
     * Never simply the family on screen for a stamped row: a mirror pass can republish a row a
     * moment after the switcher has moved to another family, and that row is not the new family's.
     */
    fun pairFor(row: CustodyModelEntity): CustodyPair? {
        val familyId = row.familyId ?: return pair?.takeIf { soleFamily }
        val uid = myUid ?: return null
        return coParents.asSequence()
            .mapNotNull { coParent -> pairOf(uid, coParent) }
            .firstOrNull { it.documentId == familyId }
    }

    companion object {
        private const val TAG = "CustodyModelRepo"

        /**
         * The families of [uid], from their own Room row: `partnerId` is the family on screen
         * (M-8) and `partnerIds` the whole set, joined the way [FamilyAudience.coParents] joins
         * them for every other per-family decision.
         */
        fun of(uid: String?, user: User?): SignedInFamilies {
            if (uid.isNullOrBlank()) return SignedInFamilies(myUid = null, pair = null, coParents = emptySet())
            val coParents = user?.let { FamilyAudience.coParents(uid, it.partnerIds, it.partnerId) }.orEmpty()
            return SignedInFamilies(uid, pairOf(uid, user?.partnerId), coParents)
        }

        /**
         * The document id and the sorted participants for [uid]/[partnerId], or null when there is
         * no pair to share anything with.
         *
         * [CustodyKey.of] rejects a blank, self-referential or separator-bearing uid by throwing;
         * that is a programming error at its own call sites but merely bad stored data here, so it
         * degrades to "no shared document" rather than taking down whatever collected the flow.
         */
        fun pairOf(uid: String?, partnerId: String?): CustodyPair? {
            if (uid.isNullOrBlank() || partnerId.isNullOrBlank()) return null
            val documentId = runCatching { CustodyKey.of(uid, partnerId) }
                .onFailure { Log.w(TAG, "Cannot derive a custody document id for this pair", it) }
                .getOrNull() ?: return null
            return CustodyPair(documentId, listOf(uid, partnerId).sorted(), uid)
        }
    }
}

/**
 * Reads [SignedInFamilies] from [UserRepository], the domain interface for exactly this lookup.
 *
 * Never from the UI-layer `ParentsSource`, whose `observe()` would drag a whole pairing
 * subscription into the data layer across a boundary it has no business crossing.
 */
internal class SignedInFamiliesReader(private val userRepository: UserRepository) {

    /** The families now, read once. */
    suspend fun current(): SignedInFamilies {
        val uid = userRepository.getCurrentUserId()
        return SignedInFamilies.of(uid, uid?.let { userRepository.getUserById(it) })
    }

    /**
     * The families as a stream: re-derived on sign-in, sign-out, account switch, a family switch,
     * and the pairing being mirrored into the local profile row.
     */
    fun observe(): Flow<SignedInFamilies> = combine(
        userRepository.observeCurrentUserId(),
        userRepository.getAllUsers()
    ) { uid, users ->
        // Matched by uid, never "the only row": sign-out does not clear Room, so a device where
        // two accounts have signed in over time holds two rows.
        SignedInFamilies.of(uid, uid?.let { id -> users.firstOrNull { it.id == id } })
    }.distinctUntilChanged()
}
