package com.coparently.app.data.sync

import android.util.Log
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.domain.family.FamilyKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Names the co-parenting relationship on local rows that were written before they could.
 *
 * Every shared record carries a [FamilyKey] id (docs/DESIGN-multi-family.md, M-2), stamped at
 * create. Two populations of rows have none: everything written before the column existed, and
 * everything written before its owner paired — for which null is the honest value, not a gap
 * (see `DatabaseMigrations.MIGRATION_29_30`). This turns the second kind into the first wherever
 * the family is **not a guess** — the rule the server's `stampOwnBlankFamilyIds` applies to the
 * documents, applied here to Room (October 2026 isolation audit, F6):
 *
 * - **A row a co-parent wrote, or a change request with them, belongs to the family with them**,
 *   whatever else this parent is in: it cannot be about any other family. Stamped for every live
 *   co-parent on every pass — a re-download of a document whose family is still blank clears the
 *   local stamp again.
 * - **This parent's own rows** belong to their one family only while they have **exactly one**
 *   co-parent **and** nothing on this phone names an earlier family. With two co-parents nothing
 *   says which family a row is about; after an unpair a row written alone may be about the family
 *   that ended. Those rows stay null — "only mine" until the parent assigns them ("Records without
 *   a family"). This used to stamp every null row in six tables, other people's included, with
 *   the one family there was — which moved a parent's records written after an unpair, and an
 *   ex-partner's, into the next family.
 * - A row of somebody who is no longer a co-parent stays null: it is history (`FamilyView`).
 *
 * Every statement is `WHERE familyId IS NULL`, so a pass repeats safely and a row that names a
 * family keeps it. It runs on every sync rather than once per co-parent: a handful of `UPDATE`s
 * that usually match nothing, and the price of not missing a row a re-download blanked.
 *
 * Local only, and deliberately: it does not clear `syncedToFirestore`, so nothing is re-uploaded
 * for it. The audience re-queue that runs in the same sync pass when a co-parent arrives uploads
 * this parent's events and children with their new family, and the server stamps the documents
 * themselves. Re-queuing every table would put a co-parent's own downloaded rows into this
 * device's outbox, where the create rule (`createdByFirebaseUid == auth.uid`) rejects them for
 * ever.
 */
@Singleton
// Six DAOs and a preference store: the six tables *are* the job, so the list is the scope of
// this class stated once rather than a signature anybody types. Taking the database instead
// would trade that for a coupling the tests would then have to mock around.
@Suppress("LongParameterList")
class FamilyIdBackfill @Inject constructor(
    private val eventDao: EventDao,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val childInfoDao: ChildInfoDao,
    private val petDao: PetDao,
    private val changeRequestDao: ChangeRequestDao,
    private val encryptedPreferences: EncryptedPreferences
) {

    /**
     * Stamps what can be stamped without a guess (see the class): every co-parent's rows with the
     * family with them, and this parent's own with their one family while there is exactly one and
     * no earlier one.
     *
     * @param userId The signed-in user's Firebase UID.
     * @param coParents Every co-parent [userId] has now (`FamilyAudience.coParents`); empty when
     *   unpaired.
     */
    suspend fun run(userId: String, coParents: Collection<String>) {
        val partners = coParents.filter { it.isNotBlank() && it != userId }.distinct()
        for (partner in partners) stampWrittenBy(partner, FamilyKey.of(userId, partner))
        stampOwn(userId, partners.singleOrNull())
    }

    /** A co-parent's rows of no family, and the change requests with them: the family with them. */
    private suspend fun stampWrittenBy(partner: String, familyId: String) {
        val stamped = eventDao.stampFamilyIdOfAuthor(familyId, partner) +
            expenseDao.stampFamilyIdOfAuthor(familyId, partner) +
            childInfoDao.stampFamilyIdOfAuthor(familyId, partner) +
            petDao.stampFamilyIdOfAuthor(familyId, partner) +
            changeRequestDao.stampFamilyIdOfCounterpart(familyId, partner)
        if (stamped > 0) Log.i(TAG, "Family id backfill: stamped $stamped row(s) of $partner as $familyId")
    }

    /**
     * This parent's own rows of no family: [onlyPartner]'s family, unless there is no single
     * co-parent or this phone holds a row of an earlier family.
     *
     * The marker under [PreferenceKeys.FAMILY_ID_BACKFILL_PREFIX] names the co-parent the own rows
     * were last stamped for, and is blanked when there is nobody, as before; it no longer gates the
     * pass, which is idempotent.
     */
    private suspend fun stampOwn(userId: String, onlyPartner: String?) {
        val key = "${PreferenceKeys.FAMILY_ID_BACKFILL_PREFIX}$userId"
        if (onlyPartner == null) {
            // Unpaired, or two families: nothing to stamp the own rows with. Blank rather than a
            // removed key: `EncryptedPreferences` has no generic remove. Written only when not
            // blank already, so an unpaired account does not re-encrypt the value on every tick.
            if (!encryptedPreferences.getString(key).isNullOrBlank()) {
                encryptedPreferences.putString(key, "")
            }
            return
        }
        val familyId = FamilyKey.of(userId, onlyPartner)
        if (hasEarlierFamily(familyId, userId)) {
            Log.i(TAG, "Family id backfill: an earlier family is on this phone; own rows stay unassigned")
            return
        }
        val stamped = eventDao.stampFamilyIdOfAuthor(familyId, userId) +
            expenseDao.stampFamilyIdOfAuthor(familyId, userId) +
            // Budgets carry no author; the client that wrote them is gone and nothing reads them.
            budgetDao.stampFamilyId(familyId) +
            childInfoDao.stampFamilyIdOfAuthor(familyId, userId) +
            petDao.stampFamilyIdOfAuthor(familyId, userId)
        if (encryptedPreferences.getString(key) != onlyPartner) encryptedPreferences.putString(key, onlyPartner)
        if (stamped > 0) Log.i(TAG, "Family id backfill for $userId: stamped $stamped own row(s) as $familyId")
    }

    /** Whether any row on this phone names a family of [userId]'s other than [familyId]. */
    private suspend fun hasEarlierFamily(familyId: String, userId: String): Boolean =
        eventDao.countRowsOfOtherFamilies(familyId, userId) +
            expenseDao.countRowsOfOtherFamilies(familyId, userId) +
            childInfoDao.countRowsOfOtherFamilies(familyId, userId) +
            petDao.countRowsOfOtherFamilies(familyId, userId) > 0

    private companion object {
        const val TAG = "FamilyIdBackfill"
    }
}
