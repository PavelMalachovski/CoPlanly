@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.family.inFamilyOnScreen
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirestorePetDataSource
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.sync.PetAudience
import com.coparently.app.data.sync.StampedFamily
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.repository.PetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Implementation of [PetRepository].
 * Coordinates between the local database and Firestore for pets, mirroring
 * [ChildInfoRepositoryImpl]: Room is the source of truth, Firestore carries the record to
 * the co-parent, and `sharedWith` is derived at upload time by [PetAudience].
 *
 * All four mappers (entity/domain/document, both directions) live in one file, `PetMappers.kt`,
 * on purpose — the child record's document map exists in seven places across two files, and
 * that duplication has already shipped bugs twice. Keep the pet document's schema there.
 */
class PetRepositoryImpl(
    private val petDao: PetDao,
    private val userDao: UserDao,
    private val firebaseAuthService: SignedInUser,
    private val firestorePetDataSource: FirestorePetDataSource
) : PetRepository {

    override fun getAllPets(): Flow<List<Pet>> {
        return petDao.getAllPets().map { entities ->
            entities.map { it.toDomain() }
        }.inFamilyOnScreen(
            firebaseAuthService,
            userDao,
            creatorOf = { it.createdByFirebaseUid }
        ) { it.familyId }
    }

    override fun getAllPetsInEveryFamily(): Flow<List<Pet>> =
        petDao.getAllPets().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPetById(id: String): Pet? {
        // Filtered here rather than in the DAO: `getPetById` deliberately returns a pending
        // tombstone so the sync path can recognise one.
        return petDao.getPetById(id)?.takeIf { it.deletedAtMillis == null }?.toDomain()
    }

    override fun observePetById(id: String): Flow<Pet?> {
        return petDao.observePetById(id).map { it?.toDomain() }
    }

    override suspend fun upsertPet(pet: Pet) {
        val signedInUid = firebaseAuthService.getCurrentUid()
        val me = signedInUid?.let { userDao.getUserById(it) }
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")
        // Which relationship this pet belongs to — see the same stamp in
        // [ChildInfoRepositoryImpl.upsertChildInfo]. Null while unpaired, and never
        // re-derived once set.
        val owned = pet.copy(
            familyId = pet.familyId ?: signedInUid?.let { uid ->
                familyToStamp(pet.id, uid, livePartners, selectedPartner)
            }
        )
        val entity = owned.toEntity()
        petDao.insertPet(entity)

        if (signedInUid != null) {
            val audience = PetAudience.entitled(
                userId = signedInUid,
                creatorUid = owned.createdByFirebaseUid,
                // The pet's own family's co-parent, never the family on screen in its place.
                partnerId = FamilyAudience.recordPartnerFor(
                    familyId = owned.familyId,
                    myUid = signedInUid,
                    livePartners = livePartners,
                    selectedPartner = selectedPartner
                )
            )
            val result = firestorePetDataSource.upsertPet(owned.id, owned.toFirestoreMap(audience))

            // Mark as synced only when the remote write actually succeeded — the same rule
            // ChildInfoRepositoryImpl follows, so a failed write stays in the retry path.
            if (result.isSuccess) {
                petDao.updatePet(entity.copy(syncedToFirestore = true))
            }
        }
    }

    /**
     * Deletes a pet in a way the co-parent can actually find out about (CQ-19).
     *
     * The `child_info` twin — see `ChildInfoRepositoryImpl.deleteChildInfo` for the two failure
     * modes a hard delete had, both of which this ends: a discarded `Result` left the local row
     * gone and the document alive, and a *successful* removal left the co-parent's phone nothing
     * to learn from.
     */
    override suspend fun deletePet(pet: Pet) {
        val deletedAtMillis = Clock.System.now().toEpochMilliseconds()
        petDao.markDeleted(pet.id, deletedAtMillis)

        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        tombstoneRemotely(pet.id, deletedAtMillis, signedInUid)
    }

    /**
     * Writes one pending tombstone, and drops the local row only once it lands.
     *
     * Shared by the delete path and [pullOnce]'s retry so the two cannot drift on what "the
     * deletion was delivered" means.
     */
    private suspend fun tombstoneRemotely(id: String, deletedAtMillis: Long, deletedBy: String) {
        val tombstoned = firestorePetDataSource.tombstonePet(
            id = id,
            deletedAtMillis = deletedAtMillis,
            deletedBy = deletedBy
        )
        if (tombstoned.isSuccess) {
            petDao.deletePetById(id)
        } else {
            logger.w(
                "PetRepo",
                "Pet tombstone not written; the deletion stays queued for the next sync",
                tombstoned.exceptionOrNull()
            )
        }
    }

    override suspend fun pullOnce() {
        val signedInUid = firebaseAuthService.getCurrentUid() ?: return
        val me = userDao.getUserById(signedInUid)
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")
        // The co-parent a pet of [familyId] names: its own family's, the answer `upsertPet` and
        // the child record's writers give from the same inputs.
        val partnerFor = { familyId: String? ->
            FamilyAudience.recordPartnerFor(familyId, signedInUid, livePartners, selectedPartner)
        }

        // Deletions first — the half of this queue that used to have no path at all. A pending
        // tombstone is retried on every sync until the write lands, and only then does the row
        // go for real.
        val (pendingDeletions, unsyncedPets) =
            petDao.getUnsyncedPets().partition { it.deletedAtMillis != null }

        for (entity in pendingDeletions) {
            tombstoneRemotely(entity.id, entity.deletedAtMillis ?: continue, signedInUid)
        }

        // Upload before downloading, so a local edit is never overwritten by the pull.
        for (entity in unsyncedPets) {
            val pet = entity.toDomain()
            val audience = PetAudience.entitled(
                userId = signedInUid,
                creatorUid = entity.createdByFirebaseUid,
                partnerId = partnerFor(entity.familyId)
            )
            val result = firestorePetDataSource.upsertPet(entity.id, pet.toFirestoreMap(audience))
            if (result.isSuccess) {
                petDao.markAsSynced(entity.id)
            }
        }

        firestorePetDataSource.getPetsForParent(signedInUid)
            .catch { e -> logger.w("PetRepo", "Pet sync failed", e) }
            .collect { firestoreList ->
                for (firestoreData in firestoreList) {
                    if (applyDownloaded(firestoreData)) {
                        val familyId = (firestoreData["familyId"] as? String)?.takeIf { it.isNotEmpty() }
                        repairAudience(signedInUid, partnerFor(familyId), firestoreData)
                    }
                }
            }
    }

    /**
     * Takes one downloaded pet document into Room, unless this device's row must win.
     *
     * @return true when the document was taken in as a live pet.
     */
    private suspend fun applyDownloaded(firestoreData: Map<String, Any?>): Boolean {
        // A tombstone is the co-parent telling this device the pet is gone. Answered from the raw
        // document, before it is mapped: a deletion must not depend on the rest of the document
        // still parsing.
        if (Tombstone.isDeleted(firestoreData)) {
            (firestoreData["id"] as? String)?.let { petDao.deletePetById(it) }
            return false
        }
        // One document that does not parse must not end the whole pull: it is logged by id
        // (never by content) and skipped, as `ExpenseRepositoryImpl` does.
        val pet = runCatching { firestoreData.toPet() }
            .onFailure { e ->
                logger.w(
                    "PetRepo",
                    "Pet document ${firestoreData["id"]} could not be read " +
                        "(${e::class.simpleName}); skipped"
                )
            }
            .getOrNull() ?: return false
        val local = petDao.getPetById(pet.id)
        // Two local states the server's copy must not replace. A pending tombstone: this device
        // deleted the pet and the deletion has not been written yet. And an unsynced edit: the
        // upload half ran first and failed, so replacing the row would erase the edit before it
        // was ever sent — it stays queued for the next pull.
        val localWins = local != null && (local.deletedAtMillis != null || !local.syncedToFirestore)
        if (!localWins) {
            petDao.insertPet(pet.toEntity().copy(syncedToFirestore = true))
        } else if (local?.deletedAtMillis == null) {
            adoptServerStamp(local, pet.familyId)
        }
        return !localWins
    }

    /**
     * Gives an unsent edit of a pet this device still holds unstamped the family the server
     * stamped on it. `stampOwnBlankFamilyIds` stamps on the evidence `FamilyIdBackfill` reads, and
     * the rules refuse an upload that blanks a stamp — the creator's too — so the edit would
     * otherwise stay queued for ever. It stays queued here, to go up with its family next pass.
     */
    private suspend fun adoptServerStamp(local: PetEntity?, stamped: String?) {
        val family = StampedFamily.of(local?.familyId, stamped)
        if (local == null || family == local.familyId) return
        petDao.insertPet(local.copy(familyId = family))
    }

    /**
     * Widens a document's `sharedWith` when the live audience has outgrown the stored one.
     *
     * This is the pet record's answer to the stale-audience trap (CLAUDE.md item 14): a pet
     * created while unpaired is uploaded with an audience of one uid, and nothing else ever
     * revisits it. Because this runs on every sync pull and compares against **live** pairing
     * state, a partner who arrives later is added on the owner's next sync — and it naturally
     * re-arms on re-pairing, with no marker to key. Only this user's own documents are
     * touched, and only the one field, via `update()` — never a full `set()`, which could
     * overwrite the co-parent's newer edit of the record itself.
     *
     * **The only co-parent it ever adds is the pet's own family's** ([partnerId], from
     * `FamilyAudience.recordPartnerFor`). It used to add the co-parent of the family on screen,
     * on every pull: a parent in two families who switched to the second added its parent to
     * every pet of the first.
     *
     * @param partnerId The co-parent of the pet's own family, or null when it must name none.
     */
    private suspend fun repairAudience(
        userId: String,
        partnerId: String?,
        firestoreData: Map<String, Any?>
    ) {
        if (firestoreData["createdByFirebaseUid"] as? String != userId) return
        val stored = (firestoreData["sharedWith"] as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()
        val entitled = PetAudience.entitled(userId, userId, partnerId)
        if (!stored.containsAll(entitled)) {
            val id = firestoreData["id"] as? String ?: return
            firestorePetDataSource.updatePet(
                id,
                mapOf("sharedWith" to (stored + entitled).distinct())
            )
        }
    }

    /**
     * The family to stamp on a pet that names none, at save — the child record's rule
     * (`ChildInfoRepositoryImpl.familyToStamp`): the family on screen for a pet being created now,
     * or for any unstamped pet while there is exactly one co-parent; with two or more, a pet that
     * already exists without a family keeps none, because nothing says which family it is.
     */
    private suspend fun familyToStamp(
        id: String,
        myUid: String,
        livePartners: List<String>,
        selectedPartner: String?
    ): String? {
        val ambiguous = FamilyAudience.coParents(myUid, livePartners, selectedPartner).size > 1
        if (ambiguous && petDao.getPetById(id) != null) return null
        return FamilyKey.orNull(myUid, selectedPartner)
    }
}
