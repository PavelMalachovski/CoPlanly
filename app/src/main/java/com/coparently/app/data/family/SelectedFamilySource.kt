package com.coparently.app.data.family

import android.util.Log
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.domain.family.FamilyKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One co-parenting relationship this device can be looking at.
 *
 * @property familyId `FamilyKey.of(myUid, partnerUid)` — the id every shared record carries.
 * @property partnerUid The other adult in it.
 * @property partnerName What to call them, or blank when their profile could not be read or
 *   carries no name. Blank rather than the uid: a switcher offering "family with
 *   `x7Kq2mB...`" is worse than one offering an unnamed row the parent can still count and
 *   recognise by position — and `ParentLabels` already refuses to invent a name.
 */
data class FamilyOption(
    val familyId: String,
    val partnerUid: String,
    val partnerName: String = ""
)

/**
 * Which family this device is currently showing, out of the ones the signed-in parent is in.
 *
 * A person may co-parent with more than one other adult, and the app shows **one at a time** —
 * a switcher rather than a merged view, because the alternative asks questions that have no
 * answer: two families have two independent custody schedules, and "whose day is it" cannot be
 * merged (docs/DESIGN-multi-family.md).
 *
 * **The selection is projected onto `UserEntity.partnerId`, and that is the design, not a
 * shortcut.** Something like a hundred and forty places read that field, and every one of them
 * wants "the co-parent of the family I am showing" — the audience an event is uploaded with,
 * the id a chat thread is derived from, the pair a custody schedule belongs to, the family an
 * expense query filters on. Re-pointing the single field they already read is what makes
 * switching a family a one-row write instead of a hundred and forty call-site changes, and it
 * is why the real list lives beside it in [UserEntity.partnerIdsJson] rather than replacing it.
 *
 * Two rules keep the projection honest:
 *
 * - **It is local only.** `UserRepositoryImpl.updateUser` deliberately stops sending
 *   `partnerId` to Firestore — the field is server-managed by the pairing callables, and
 *   publishing a per-device UI choice into a document the co-parent reads would be a different
 *   kind of bug entirely. The write here goes straight to [UserDao].
 * - **The selection is validated on every read**, never trusted. A stored id survives an
 *   unpair, and a family the account is no longer in must not select itself; an unknown id
 *   falls back to the first real one.
 *
 * Scoped per user for the reason `PreferenceKeys.PARENT_SLOT_MARKER_PREFIX` gives: Room rows
 * survive sign-out, so a second account on the same device must not inherit the first one's
 * choice.
 */
@Singleton
class SelectedFamilySource @Inject constructor(
    private val userDao: UserDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val firestoreUserDataSource: FirestoreUserDataSource,
    private val encryptedPreferences: EncryptedPreferences
) {

    /**
     * Reads the stored co-parent list, which is a JSON array of plain uid strings.
     *
     * Room and Firebase Auth directly, rather than `UserRepository`: this is injected into
     * `PairingRepositoryImpl` and `SyncService`, and reaching back through a repository whose
     * own graph reaches those would be a dependency cycle waiting to close. All this needs is
     * one row and one uid.
     */
    private val gson = Gson()

    /**
     * A switch [select] was asked for before this device knew the family, waiting for the
     * [reconcile] that brings the family in — or for [PENDING_SWITCH_MILLIS] to pass.
     *
     * The tap on a `pairing_accepted` push names the family the pairing just made
     * (`MainActivity.readLaunchIntent`), and it can arrive before the partner list in Room holds
     * that family: the push goes out the moment the server writes the link, and this device learns
     * the new co-parent from its own pairing listener or its next sync, either of which may run
     * after the tap. Refused outright, the tap opened on the family already on screen. Kept in
     * memory only, for the account that asked; any later [select] replaces it, and a reconcile
     * that still does not know the family keeps it, because a cached snapshot older than the
     * pairing can reconcile first.
     */
    private val pendingSwitch = AtomicReference<PendingSwitch?>(null)

    /** See [pendingSwitch]. */
    private data class PendingSwitch(val uid: String, val familyId: String, val untilMillis: Long)

    /**
     * The families the signed-in parent is in, oldest relationship first.
     *
     * Derived from the stored co-parent list rather than from `families/{id}`: this has to work
     * offline, and the id is a pure function of the two uids anyway. Blank and self-referential
     * entries are dropped by [FamilyKey.orNull] rather than becoming a family of one.
     */
    suspend fun families(): List<FamilyOption> {
        val uid = currentUid() ?: return emptyList()
        val me = userDao.getUserById(uid) ?: return emptyList()
        return optionsFor(uid, me.partnerIdsJson)
    }

    /**
     * [families], as a stream that follows the signed-in parent's row.
     *
     * For the top-bar switcher (M-8), which has to appear the moment a second family exists and
     * go away the moment one ends, without anybody opening Settings to refresh it. Room's own
     * invalidation drives it, like [observe], so it adds no Firestore listener; and it is
     * distinct on the list, so the unrelated column writes that re-emit the row cost nothing
     * downstream — which matters, because what a subscriber does with a new list is
     * [named], one Firestore read per family.
     */
    fun observeFamilies(uid: String): Flow<List<FamilyOption>> =
        userDao.observeUserById(uid)
            .map { row -> row?.let { optionsFor(uid, it.partnerIdsJson) }.orEmpty() }
            .distinctUntilChanged()

    private fun optionsFor(uid: String, partnerIdsJson: String?): List<FamilyOption> =
        gson.fromJson(partnerIdsJson, Array<String>::class.java)
            ?.toList().orEmpty()
            .mapNotNull { partnerUid ->
                FamilyKey.orNull(uid, partnerUid)?.let { FamilyOption(it, partnerUid) }
            }

    /**
     * The family this device is showing, or null when the account is in none.
     *
     * Falls back to the first when nothing is stored or the stored id names a relationship that
     * has since ended — which is the ordinary case after an unpair, not an error.
     */
    suspend fun selected(): FamilyOption? {
        val options = families()
        if (options.isEmpty()) return null
        val stored = currentUid()?.let { encryptedPreferences.getString(key(it)) }
        return options.firstOrNull { it.familyId == stored } ?: options.first()
    }

    /**
     * Points this device at [familyId].
     *
     * Changes nothing now for a family this device does not know: a stale switcher tap must not
     * blank the co-parent every downstream reader depends on. The request waits, briefly, for a
     * [reconcile] that brings the family in ([pendingSwitch]) — a family the account is not in
     * never arrives, so nothing ever comes of it.
     *
     * @return The family now selected, or null when [familyId] named none of them.
     */
    suspend fun select(familyId: String): FamilyOption? {
        val uid = currentUid() ?: return null
        val target = families().firstOrNull { it.familyId == familyId }
        if (target == null) {
            pendingSwitch.set(PendingSwitch(uid, familyId, System.currentTimeMillis() + PENDING_SWITCH_MILLIS))
            Log.w(TAG, "A switch to a family this device does not know yet waits for the next reconcile")
            return null
        }
        pendingSwitch.set(null)
        encryptedPreferences.putString(key(uid), familyId)
        applyProjection(uid, target)
        return target
    }

    /**
     * Re-applies the projection without changing the choice — unless a switch [select] could
     * not make yet names a family this device now knows ([pendingSwitch]).
     *
     * Called after anything that can move the set of families under the selection — a sync that
     * brings a new co-parent down, an unpair, a fresh sign-in. Without it a device that had
     * been showing a family it has just left would keep `partnerId` pointing at the ex-partner
     * until the parent happened to open the switcher.
     */
    suspend fun reconcile() {
        val uid = currentUid() ?: return
        val target = askedFor(uid) ?: selected()
        if (target == null) {
            applyProjection(uid, null)
            return
        }
        encryptedPreferences.putString(key(uid), target.familyId)
        applyProjection(uid, target)
    }

    /**
     * The family a waiting switch names, once [families] holds it — the switch is then spent.
     * Null while it does not, and for a switch another account asked for or one that has lapsed,
     * which are dropped.
     */
    private suspend fun askedFor(uid: String): FamilyOption? {
        val pending = pendingSwitch.get() ?: return null
        val live = pending.uid == uid && System.currentTimeMillis() <= pending.untilMillis
        val target = if (live) families().firstOrNull { it.familyId == pending.familyId } else null
        if (target != null || !live) pendingSwitch.compareAndSet(pending, null)
        return target
    }

    /**
     * Emits the selected family whenever the signed-in parent's row changes.
     *
     * Room's own invalidation drives this — the projection is a column on that row — so no
     * Firestore listener is added for it. `distinctUntilChanged` because the row re-emits when
     * any unrelated column moves.
     */
    fun observe(uid: String): Flow<FamilyOption?> =
        userDao.observeUserById(uid)
            .map { row ->
                val partnerUid = row?.partnerId
                FamilyKey.orNull(uid, partnerUid)?.let { FamilyOption(it, partnerUid.orEmpty()) }
            }
            .distinctUntilChanged()

    /**
     * The families this parent is in, each with the co-parent's name resolved.
     *
     * A separate method from [families] because it costs one Firestore read per relationship
     * and only the switcher needs it — every other caller wants the ids, which come off the
     * local row for free. A read that fails leaves the name blank rather than failing the
     * list: a switcher that disappears because one profile was unreachable is worse than one
     * with an unnamed row in it.
     */
    suspend fun namedFamilies(): List<FamilyOption> = named(families())

    /** [options] with each co-parent's name resolved; see [namedFamilies] for what it costs. */
    suspend fun named(options: List<FamilyOption>): List<FamilyOption> = options.map { option ->
        val name = runCatching {
            firestoreUserDataSource.getUserById(option.partnerUid)?.get("name") as? String
        }.getOrNull().orEmpty()
        option.copy(partnerName = name)
    }

    /** Writes the selection onto the local row, and nowhere else. */
    private suspend fun applyProjection(uid: String, target: FamilyOption?) {
        val row = userDao.getUserById(uid) ?: return
        val partnerId = target?.partnerUid
        if (row.partnerId == partnerId) return
        userDao.updateUser(row.copy(partnerId = partnerId))
    }

    private fun currentUid(): String? = firebaseAuthService.getCurrentUser()?.uid

    private fun key(uid: String) = "${PreferenceKeys.SELECTED_FAMILY_PREFIX}$uid"

    private companion object {
        const val TAG = "SelectedFamily"

        /**
         * How long a switch to a family this device does not know yet may wait for it. Long enough
         * for a cold start's pairing listener or sync to answer, short enough that a tap is never
         * applied long after the parent has moved on.
         */
        const val PENDING_SWITCH_MILLIS = 60_000L
    }
}
