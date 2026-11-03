// The child mappers are public in `:shared-data` only for the wire contract and the app's tests (MON-24).
@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.family.inFamilyOnScreen
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.sync.ChildInfoAudience
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.guests.GuestGrantPolicy
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.repository.ChildInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Implementation of [ChildInfoRepository].
 * Coordinates between local database and Firestore for child information.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8e), when `GuestGrantPolicy` moved to `kotlin.time`:
 * it asks [SignedInUser] for the uid it used to read off the Android SDK's `FirebaseUser`, and its
 * four mappers are top-level (`ChildInfoMappers.kt`).
 */
class ChildInfoRepositoryImpl(
    private val childInfoDao: ChildInfoDao,
    private val userDao: UserDao,
    private val firebaseAuthService: SignedInUser,
    private val firestoreChildInfoDataSource: FirestoreChildInfoDataSource
) : ChildInfoRepository {

    override fun getAllChildInfo(): Flow<List<ChildInfo>> =
        getAllChildInfoInEveryFamily().inFamilyOnScreen(
            firebaseAuthService,
            userDao,
            creatorOf = { it.createdByFirebaseUid }
        ) { it.familyId }

    override fun getAllChildInfoInEveryFamily(): Flow<List<ChildInfo>> {
        return childInfoDao.getAllChildInfo().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChildInfoById(id: String): ChildInfo? {
        // Filtered here rather than in the DAO: `getChildInfoById` deliberately returns a
        // pending tombstone so the sync path can recognise one, and a caller answering a user's
        // question must not see a record that has been deleted.
        return childInfoDao.getChildInfoById(id)?.takeIf { it.deletedAtMillis == null }?.toDomain()
    }

    override fun observeChildInfoById(id: String): Flow<ChildInfo?> {
        return childInfoDao.observeChildInfoById(id).map { it?.toDomain() }
    }

    override suspend fun upsertChildInfo(childInfo: ChildInfo) {
        val myUid = firebaseAuthService.getCurrentUid()
        val me = myUid?.let { userDao.getUserById(it) }
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")
        // Which relationship this child belongs to, and therefore whose family screens list
        // them. Null while the account is unpaired: the record is this parent's alone until
        // there is somebody to share it with. Kept once stamped, so a re-pairing to a
        // different co-parent cannot silently move a child between families.
        val owned = childInfo.copy(
            familyId = childInfo.familyId ?: myUid?.let { uid ->
                familyToStamp(childInfo.id, uid, livePartners, selectedPartner)
            }
        )
        val entity = owned.toEntity()
        childInfoDao.insertChildInfo(entity)

        // Sync to Firestore if authenticated
        if (myUid != null) {
            val audience = ChildInfoAudience.entitled(
                userId = myUid,
                creatorUid = owned.createdByFirebaseUid,
                // The child's own family's co-parent — never the family on screen in its place,
                // which for a parent in two families handed this record to the other family's
                // parent (`FamilyAudience.recordPartnerFor`).
                partnerId = FamilyAudience.recordPartnerFor(
                    familyId = owned.familyId,
                    myUid = myUid,
                    livePartners = livePartners,
                    selectedPartner = selectedPartner
                ),
                guestUids = activeGuestUids(owned)
            )
            val childInfoData = owned.toFirestoreMap(audience)
            val result = firestoreChildInfoDataSource.upsertChildInfo(owned.id, childInfoData)

            // Mark as synced only when the remote write actually succeeded. Marking it
            // unconditionally (the previous behaviour) stranded a failed write out of
            // `getUnsyncedChildInfo()`'s retry path forever, with nothing left to say the
            // document never reached Firestore.
            if (result.isSuccess) {
                val syncedEntity = entity.copy(syncedToFirestore = true)
                childInfoDao.updateChildInfo(syncedEntity)
            }
        }
    }

    /**
     * The family to stamp on a record that names none, at save: the family on screen for a child
     * being created now — it is created in that family — or, with exactly one co-parent, for any
     * unstamped record, as `FamilyIdBackfill` would stamp it.
     *
     * With two or more co-parents a record that **already exists** without a family stays
     * without one. It was written before its parent paired, and nothing on this phone says which
     * of the two families it is about; stamping the one on screen at the next edit would be the
     * guess `FamilyIdBackfill` and the server both decline, and would hand its health details to
     * that family's parent.
     */
    private suspend fun familyToStamp(
        id: String,
        myUid: String,
        livePartners: List<String>,
        selectedPartner: String?
    ): String? {
        val ambiguous = FamilyAudience.coParents(myUid, livePartners, selectedPartner).size > 1
        if (ambiguous && childInfoDao.getChildInfoById(id) != null) return null
        return FamilyKey.orNull(myUid, selectedPartner)
    }

    /**
     * Deletes a child record in a way the co-parent can actually find out about (CQ-19).
     *
     * The row is **tombstoned** rather than removed: every query hides it immediately, so the
     * parent sees it go at once, but it survives in Room as an outbox entry until the deletion
     * has been written to Firestore. Before this, the row was removed and the remote document
     * deleted on a call whose `Result` was discarded — so a refused or offline delete left the
     * local row gone and the document alive, and the next download put the child back, while a
     * *successful* delete made the document vanish with nothing left for the co-parent's phone
     * to act on. Nothing reconciles by absence, correctly (`data/sync/Tombstone.kt`), so the
     * co-parent kept the record forever.
     *
     * Signed out, the deletion simply waits: the row stays a tombstone and [pullOnce] retries it
     * on every sync until the write lands.
     */
    override suspend fun deleteChildInfo(childInfo: ChildInfo) {
        val deletedAtMillis = Clock.System.now().toEpochMilliseconds()
        childInfoDao.markDeleted(childInfo.id, deletedAtMillis)

        val myUid = firebaseAuthService.getCurrentUid() ?: return
        tombstoneRemotely(childInfo.id, deletedAtMillis, myUid)
    }

    /**
     * Writes one pending tombstone, and drops the local row only once it lands.
     *
     * Shared by the delete path and [pullOnce]'s retry so the two cannot drift on what "the
     * deletion was delivered" means.
     */
    private suspend fun tombstoneRemotely(id: String, deletedAtMillis: Long, deletedBy: String) {
        val tombstoned = firestoreChildInfoDataSource.tombstoneChildInfo(
            id = id,
            deletedAtMillis = deletedAtMillis,
            deletedBy = deletedBy
        )
        if (tombstoned.isSuccess) {
            childInfoDao.deleteChildInfoById(id)
        } else {
            logger.w(
                "ChildInfoRepo",
                "Child tombstone not written; the deletion stays queued for the next sync",
                tombstoned.exceptionOrNull()
            )
        }
    }

    override suspend fun pullOnce() {
        val myUid = firebaseAuthService.getCurrentUid() ?: return
        val me = userDao.getUserById(myUid)
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")

        // Deletions first — they are the half of this queue that used to have no path at all.
        // A pending tombstone is retried here on every sync until the write lands, and only
        // then does the row go for real.
        val (pendingDeletions, unsyncedChildInfo) =
            childInfoDao.getUnsyncedChildInfo().partition { it.deletedAtMillis != null }

        for (entity in pendingDeletions) {
            tombstoneRemotely(entity.id, entity.deletedAtMillis ?: continue, myUid)
        }

        // Upload to Firestore, each child under its own family's audience — the same answer
        // `upsertChildInfo` and `SyncService` give, from the same inputs.
        for (entity in unsyncedChildInfo) {
            val childInfo = entity.toDomain()
            val audience = ChildInfoAudience.entitled(
                userId = myUid,
                creatorUid = entity.createdByFirebaseUid,
                partnerId = FamilyAudience.recordPartnerFor(
                    familyId = entity.familyId,
                    myUid = myUid,
                    livePartners = livePartners,
                    selectedPartner = selectedPartner
                ),
                guestUids = activeGuestUids(childInfo)
            )
            val childInfoData = childInfo.toFirestoreMap(audience)
            val result = firestoreChildInfoDataSource.upsertChildInfo(entity.id, childInfoData)

            if (result.isSuccess) {
                childInfoDao.markAsSynced(entity.id)
            }
        }

        // Download from Firestore
        firestoreChildInfoDataSource.getChildInfoForParent(myUid)
            .catch { e -> logger.w("ChildInfoRepo", "Child info sync failed", e) }
            .collect { firestoreList ->
                for (firestoreData in firestoreList) {
                    applyDownloaded(firestoreData)
                }
            }
    }

    /** Takes one downloaded child document into Room, unless this device's row must win. */
    private suspend fun applyDownloaded(firestoreData: Map<String, Any?>) {
        // A tombstone is the co-parent telling this device the child record is gone. Answered
        // from the raw document, before it is mapped: a deletion is the one thing that must not
        // depend on the rest of the document still parsing.
        if (Tombstone.isDeleted(firestoreData)) {
            (firestoreData["id"] as? String)?.let { childInfoDao.deleteChildInfoById(it) }
            return
        }
        // One document that does not parse must not end the whole pull: it is logged by id
        // (never by content) and skipped, as `ExpenseRepositoryImpl` does.
        val childInfo = runCatching { firestoreData.toChildInfo() }
            .onFailure { e ->
                logger.w(
                    "ChildInfoRepo",
                    "Child info document ${firestoreData["id"]} could not be read " +
                        "(${e::class.simpleName}); skipped"
                )
            }
            .getOrNull() ?: return
        val local = childInfoDao.getChildInfoById(childInfo.id)
        // Two local states the server's copy must not replace. A pending tombstone: this device
        // deleted the record and the deletion has not been written yet, so inserting the live
        // document would undo the parent's own delete. And an unsynced edit: the upload half ran
        // first and failed, so replacing the row would erase the edit before it was ever sent —
        // it stays queued, and the server's version comes down once the edit has gone up.
        val localWins = local != null && (local.deletedAtMillis != null || !local.syncedToFirestore)
        if (!localWins) {
            childInfoDao.insertChildInfo(childInfo.toEntity().copy(syncedToFirestore = true))
        }
    }

    /**
     * The uids of [childInfo]'s guests whose grants are still good.
     *
     * Filtered rather than passed whole, so an expired guest leaves `sharedWith` on the very next
     * upload rather than waiting for the scheduled sweep. That is the sweep's work being done
     * early, which is the direction to fail in; the reverse — a guest whose grant has run out
     * still sitting in the audience — is what the sweep exists to clean up.
     */
    private fun activeGuestUids(childInfo: ChildInfo): List<String> =
        GuestGrantPolicy.active(childInfo.guests.values.toList(), Clock.System.now()).map { it.uid }
}
