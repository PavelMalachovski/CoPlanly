package com.coparently.app.data.family

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.family.FamilyKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Where a record stands for the family on screen ([FamilyView.placeOf]).
 */
enum class RecordPlace {
    /** The family on screen's own record — or anything at all while the parent is in no family. */
    ON_SCREEN,

    /**
     * This parent's own record of no family while they are in a family: shared with nobody
     * (`FamilyAudience`), shown in every family marked "Only me" and counted in no family's
     * totals until it has a family. `FamilyIdBackfill` gives it the one family where that is not a
     * guess; otherwise the parent assigns it ("Records without a family", owner decision October
     * 2026).
     */
    ONLY_ME,

    /**
     * A record of a family that has ended: this parent's history. Shown marked "From an earlier
     * family", never shared with a later co-parent and never counted in a live family's totals
     * (owner decision October 2026).
     */
    EARLIER_FAMILY,

    /** A record of another live family, or of a family this parent is not in: not shown. */
    OTHER_FAMILY
}

/**
 * What the family on screen may show: everything except the records of the parent's **other**
 * live families.
 *
 * A parent in two families sees one at a time (M-8). Room holds both families' records on the
 * phone, and the calendar, Home and Expenses read Room — so without this the family with Carol
 * showed the family with Igor's dentist and shoes (tester report, October 2026). [placeOf] says
 * where each record stands:
 *
 * - a record of the family on screen is shown;
 * - a record of **no family written by a co-parent** belongs to the family with that co-parent —
 *   the record cannot be about any other family, and showing it everywhere put Igor's records into
 *   the family with Carol, in Carol's name and colour (isolation audit, October 2026);
 * - this parent's **own** record of no family is "only mine" ([RecordPlace.ONLY_ME]) — its audience
 *   names nobody until it has a family, and the screen says so rather than drawing it as the
 *   family's;
 * - a record of a family that has **ended** stays visible as history
 *   ([RecordPlace.EARLIER_FAMILY]), so an unpair never makes a parent's past disappear;
 * - a record of another live family is hidden.
 *
 * With one family, or none, nothing is hidden. [counts] says what a family's totals and balance
 * may add up: its own records only.
 *
 * @property me The signed-in parent, or null when there is nobody to place records for.
 * @property onScreen The family the switcher shows, or null when the account is in none.
 * @property others The parent's other live families.
 */
data class FamilyView(val me: String? = null, val onScreen: String?, val others: Set<String>) {

    /** Where a record of [familyId], written by [createdBy], stands on this screen. */
    fun placeOf(familyId: String?, createdBy: String? = null): RecordPlace {
        val owner = me ?: return RecordPlace.ON_SCREEN
        val family = familyId?.takeIf { it.isNotBlank() }
            ?: createdBy?.takeIf { it.isNotBlank() && it != owner }?.let { FamilyKey.orNull(owner, it) }
            ?: return if (liveFamilies() > 0) RecordPlace.ONLY_ME else RecordPlace.ON_SCREEN
        return when {
            family == onScreen -> RecordPlace.ON_SCREEN
            family in others -> RecordPlace.OTHER_FAMILY
            FamilyKey.membersOf(family)?.toList()?.contains(owner) != true -> RecordPlace.OTHER_FAMILY
            else -> RecordPlace.EARLIER_FAMILY
        }
    }

    /** Whether a record of [familyId], written by [createdBy], belongs on this screen. */
    fun shows(familyId: String?, createdBy: String? = null): Boolean =
        placeOf(familyId, createdBy) != RecordPlace.OTHER_FAMILY

    /** Whether a record of [familyId], written by [createdBy], counts in this family's totals. */
    fun counts(familyId: String?, createdBy: String? = null): Boolean =
        placeOf(familyId, createdBy) == RecordPlace.ON_SCREEN

    private fun liveFamilies(): Int = others.size + (if (onScreen != null) 1 else 0)

    companion object {
        /** Shows everything: signed out, or no row yet. */
        val EVERYTHING = FamilyView(me = null, onScreen = null, others = emptySet())

        private val gson = Gson()

        /** The view of the signed-in [uid] from their Room row. */
        fun of(uid: String, row: UserEntity?): FamilyView {
            if (row == null) return EVERYTHING
            val onScreen = FamilyKey.orNull(uid, row.partnerId)
            val partners = runCatching { gson.fromJson(row.partnerIdsJson, Array<String>::class.java) }
                .getOrNull()?.toList().orEmpty()
            val others = partners.mapNotNull { FamilyKey.orNull(uid, it) }.toSet() - setOfNotNull(onScreen)
            return FamilyView(me = uid, onScreen = onScreen, others = others)
        }
    }
}

/**
 * This list kept to the family on screen ([FamilyView]), re-filtered when the switcher moves.
 *
 * The family is read from the signed-in parent's Room row, which is where the switcher writes it,
 * so a switch re-filters without any Room write to the records themselves. Signed out, nothing is
 * filtered.
 *
 * @param creatorOf Who wrote a record: a co-parent's record of no family belongs to the family with
 *   them ([FamilyView.placeOf]). Every repository passes it; the default exists for lists that
 *   carry no author.
 * @param familyOf The record's family, or null for none.
 */
fun <T> Flow<List<T>>.inFamilyOnScreen(
    auth: FirebaseAuthService,
    userDao: UserDao,
    creatorOf: (T) -> String? = { null },
    familyOf: (T) -> String?
): Flow<List<T>> {
    val records = this
    return flow {
        val uid = auth.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }
        if (uid == null) {
            emitAll(records)
            return@flow
        }
        // The first view is read once before the row is observed, so a records flow never waits
        // on the row's first emission (and a test's mocked DAO, which emits nothing, still works).
        val views = userDao.observeUserById(uid)
            .map { FamilyView.of(uid, it) }
            .onStart { emit(FamilyView.of(uid, userDao.getUserById(uid))) }
            .distinctUntilChanged()
        emitAll(combine(records, views) { list, view -> list.filter { view.shows(familyOf(it), creatorOf(it)) } })
    }
}
