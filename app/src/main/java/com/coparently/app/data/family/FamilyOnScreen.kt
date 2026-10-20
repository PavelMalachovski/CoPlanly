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
 * What the family on screen may show: everything except the records of the parent's **other**
 * live families.
 *
 * A parent in two families sees one at a time (M-8). Room holds both families' events and
 * expenses on the phone, and the calendar, Home and Expenses read Room — so without this the
 * family with Carol showed the family with Igor's dentist and shoes (tester report, October
 * 2026). The rule hides only what belongs to another family the parent is still in:
 *
 * - a record of the family on screen is shown;
 * - a record naming no family ("mine alone", item 18) is shown, as it always was — it is this
 *   parent's own and reaches nobody;
 * - a record of a family that has **ended** is shown, as it always was, so an unpair or a new
 *   co-parent never makes a parent's history disappear from their own calendar.
 *
 * With one family, or none, nothing is hidden.
 *
 * @property onScreen The family the switcher shows, or null when the account is in none.
 * @property others The parent's other live families.
 */
data class FamilyView(val onScreen: String?, val others: Set<String>) {

    /** Whether a record of [familyId] belongs on this screen. */
    fun shows(familyId: String?): Boolean =
        familyId.isNullOrBlank() || familyId == onScreen || familyId !in others

    companion object {
        /** Shows everything: signed out, or no row yet. */
        val EVERYTHING = FamilyView(onScreen = null, others = emptySet())

        private val gson = Gson()

        /** The view of the signed-in [uid] from their Room row. */
        fun of(uid: String, row: UserEntity?): FamilyView {
            if (row == null) return EVERYTHING
            val onScreen = FamilyKey.orNull(uid, row.partnerId)
            val partners = runCatching { gson.fromJson(row.partnerIdsJson, Array<String>::class.java) }
                .getOrNull()?.toList().orEmpty()
            val others = partners.mapNotNull { FamilyKey.orNull(uid, it) }.toSet() - setOfNotNull(onScreen)
            return FamilyView(onScreen, others)
        }
    }
}

/**
 * This list kept to the family on screen ([FamilyView]), re-filtered when the switcher moves.
 *
 * The family is read from the signed-in parent's Room row, which is where the switcher writes it,
 * so a switch re-filters without any Room write to the records themselves. Signed out, nothing is
 * filtered.
 */
fun <T> Flow<List<T>>.inFamilyOnScreen(
    auth: FirebaseAuthService,
    userDao: UserDao,
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
        emitAll(combine(records, views) { list, view -> list.filter { view.shows(familyOf(it)) } })
    }
}
