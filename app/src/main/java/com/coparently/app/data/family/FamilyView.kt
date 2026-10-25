package com.coparently.app.data.family

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
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
     * A record of a family that has ended: this parent's history. Never shared with a later
     * co-parent and never counted in a live family's totals. While the parent is in a live family
     * it is not drawn at all — it is read under Family → Earlier families (owner decision, 25
     * October 2026: "full isolation"); while they are in none it is drawn marked "From an earlier
     * family", because there is nothing to isolate it from.
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
 * - a record of a family that has **ended** ([RecordPlace.EARLIER_FAMILY]) is hidden while the
 *   parent is in a live family, and read under Family → Earlier families instead; with no live
 *   family it stays visible, marked, so an unpair never empties the app;
 * - a record of another live family is hidden.
 *
 * With one family, or none, nothing is hidden. [counts] says what a family's totals and balance
 * may add up: its own records only.
 *
 * @property me The signed-in parent, or null when there is nobody to place records for.
 * @property onScreen The family the switcher shows, or null when the account is in none.
 * @property others The parent's other live families.
 * @property partnersKnown False when the stored co-parent list could not be read: nothing then says
 *   which families have ended, so none is hidden as one.
 */
data class FamilyView(
    val me: String? = null,
    val onScreen: String?,
    val others: Set<String>,
    val partnersKnown: Boolean = true
) {

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
        when (placeOf(familyId, createdBy)) {
            RecordPlace.OTHER_FAMILY -> false
            RecordPlace.EARLIER_FAMILY -> liveFamilies() == 0 || !partnersKnown
            else -> true
        }

    /** Whether a record of [familyId], written by [createdBy], counts in this family's totals. */
    fun counts(familyId: String?, createdBy: String? = null): Boolean =
        placeOf(familyId, createdBy) == RecordPlace.ON_SCREEN

    /** How many live families the parent is in: the one on screen and the others. */
    fun liveFamilies(): Int = others.size + (if (onScreen != null) 1 else 0)

    companion object {
        /** Shows everything: signed out, or no row yet. */
        val EVERYTHING = FamilyView(me = null, onScreen = null, others = emptySet())

        private val gson = Gson()

        /** The view of the signed-in [uid] from their Room row. */
        fun of(uid: String, row: UserEntity?): FamilyView {
            if (row == null) return EVERYTHING
            val onScreen = FamilyKey.orNull(uid, row.partnerId)
            val parsed = runCatching { gson.fromJson(row.partnerIdsJson, Array<String>::class.java) }
            val partners = parsed.getOrNull()?.toList().orEmpty()
            val others = partners.mapNotNull { FamilyKey.orNull(uid, it) }.toSet() - setOfNotNull(onScreen)
            return FamilyView(me = uid, onScreen = onScreen, others = others, partnersKnown = parsed.isSuccess)
        }
    }
}

/**
 * The family an event is placed by on screen: its own, or none for a private event.
 *
 * A private event belongs to nobody but its author (CLAUDE.md item 3): it never syncs, no
 * co-parent reads it, and a Google Calendar import is one. Its stamp says only which family was
 * on screen when it was written, so placing it by that stamp hid a parent's whole imported
 * calendar the day that family ended (tester report, 25 October 2026). Placed by no family, it is
 * "only mine" and is shown in every family.
 */
val Event.placementFamilyId: String?
    get() = if (isPrivate) null else familyId

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
