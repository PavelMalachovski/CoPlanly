package com.coparently.app.data.repository

import com.coparently.app.data.family.inFamilyOnScreen
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.sync.ChildInfoAudience
import com.coparently.app.data.sync.ChildInfoGuests
import com.coparently.app.data.sync.ChildInfoPhotos
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.guests.GuestGrantPolicy
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/**
 * Implementation of [ChildInfoRepository].
 * Coordinates between local database and Firestore for child information.
 */
class ChildInfoRepositoryImpl(
    private val childInfoDao: ChildInfoDao,
    private val userDao: UserDao,
    private val firebaseAuthService: FirebaseAuthService,
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
        val firebaseUser = firebaseAuthService.getCurrentUser()
        val me = firebaseUser?.let { userDao.getUserById(it.uid) }
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")
        // Which relationship this child belongs to, and therefore whose family screens list
        // them. Null while the account is unpaired: the record is this parent's alone until
        // there is somebody to share it with. Kept once stamped, so a re-pairing to a
        // different co-parent cannot silently move a child between families.
        val owned = childInfo.copy(
            familyId = childInfo.familyId ?: firebaseUser?.let { user ->
                familyToStamp(childInfo.id, user.uid, livePartners, selectedPartner)
            }
        )
        val entity = owned.toEntity()
        childInfoDao.insertChildInfo(entity)

        // Sync to Firestore if authenticated
        if (firebaseUser != null) {
            val audience = ChildInfoAudience.entitled(
                userId = firebaseUser.uid,
                creatorUid = owned.createdByFirebaseUid,
                // The child's own family's co-parent — never the family on screen in its place,
                // which for a parent in two families handed this record to the other family's
                // parent (`FamilyAudience.recordPartnerFor`).
                partnerId = FamilyAudience.recordPartnerFor(
                    familyId = owned.familyId,
                    myUid = firebaseUser.uid,
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
        val deletedAtMillis = System.currentTimeMillis()
        childInfoDao.markDeleted(childInfo.id, deletedAtMillis)

        val firebaseUser = firebaseAuthService.getCurrentUser() ?: return
        tombstoneRemotely(childInfo.id, deletedAtMillis, firebaseUser.uid)
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
        val firebaseUser = firebaseAuthService.getCurrentUser() ?: return
        val me = userDao.getUserById(firebaseUser.uid)
        val selectedPartner = me?.partnerId?.takeIf { it.isNotBlank() }
        val livePartners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]")

        // Deletions first — they are the half of this queue that used to have no path at all.
        // A pending tombstone is retried here on every sync until the write lands, and only
        // then does the row go for real.
        val (pendingDeletions, unsyncedChildInfo) =
            childInfoDao.getUnsyncedChildInfo().partition { it.deletedAtMillis != null }

        for (entity in pendingDeletions) {
            tombstoneRemotely(entity.id, entity.deletedAtMillis ?: continue, firebaseUser.uid)
        }

        // Upload to Firestore, each child under its own family's audience — the same answer
        // `upsertChildInfo` and `SyncService` give, from the same inputs.
        for (entity in unsyncedChildInfo) {
            val childInfo = entity.toDomain()
            val audience = ChildInfoAudience.entitled(
                userId = firebaseUser.uid,
                creatorUid = entity.createdByFirebaseUid,
                partnerId = FamilyAudience.recordPartnerFor(
                    familyId = entity.familyId,
                    myUid = firebaseUser.uid,
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
        firestoreChildInfoDataSource.getChildInfoForParent(firebaseUser.uid)
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
                        "(${e.javaClass.simpleName}); skipped"
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
        GuestGrantPolicy.active(childInfo.guests.values.toList(), Instant.now()).map { it.uid }

    /**
     * Converts ChildInfoEntity to domain ChildInfo.
     */
    internal fun ChildInfoEntity.toDomain(): ChildInfo {
        return ChildInfo(
            id = id,
            childName = childName,
            dateOfBirth = dateOfBirth?.toKotlinLocalDateTime(),
            medications = StoredJson.decodeOrNull(MEDICATIONS, medicationsJson).orEmpty(),
            activities = StoredJson.decodeOrNull(ACTIVITIES, activitiesJson).orEmpty(),
            allergies = StoredJson.decodeOrNull(StoredJson.strings, allergiesJson).orEmpty(),
            medicalNotes = medicalNotes,
            emergencyContacts = StoredJson.decodeOrNull(EMERGENCY_CONTACTS, emergencyContactsJson).orEmpty(),
            schoolInfo = StoredJson.decodeOrNull(SCHOOL_INFO, schoolInfoJson),
            medicalProfile = (
                StoredJson.decodeOrNull(MedicalProfile.serializer(), medicalProfileJson) ?: MedicalProfile()
                ).withSanitizedVaccinationNames(),
            medicalPhotos = ChildInfoPhotos.decode(
                StoredJson.decodeOrNull(StoredJson.strings, medicalPhotosJson)
            ),
            guests = ChildInfoGuests.decode(StoredJson.decodeValue(guestsJson)),
            createdAt = createdAt.toKotlinLocalDateTime(),
            updatedAt = updatedAt.toKotlinLocalDateTime(),
            createdByFirebaseUid = createdByFirebaseUid,
            lastModifiedBy = lastModifiedBy,
            syncedToFirestore = syncedToFirestore,
            familyId = familyId
        )
    }

    /**
     * Converts domain ChildInfo to ChildInfoEntity.
     */
    internal fun ChildInfo.toEntity(): ChildInfoEntity {
        return ChildInfoEntity(
            id = id,
            childName = childName,
            dateOfBirth = dateOfBirth?.toJavaLocalDateTime(),
            medicationsJson = StoredJson.encode(MEDICATIONS, medications),
            activitiesJson = StoredJson.encode(ACTIVITIES, activities),
            allergiesJson = StoredJson.encode(StoredJson.strings, allergies),
            medicalNotes = medicalNotes,
            emergencyContactsJson = StoredJson.encode(EMERGENCY_CONTACTS, emergencyContacts),
            schoolInfoJson = schoolInfo?.let { StoredJson.encode(SCHOOL_INFO, it) },
            medicalProfileJson = StoredJson.encode(MedicalProfile.serializer(), medicalProfile),
            medicalPhotosJson = StoredJson.encode(StoredJson.strings, medicalPhotos),
            guestsJson = StoredJson.encodeValue(ChildInfoGuests.encode(guests)),
            createdAt = createdAt.toJavaLocalDateTime(),
            updatedAt = updatedAt.toJavaLocalDateTime(),
            // Derived at the one boundary every save crosses, from the wall clock each save path
            // already stamps — so no path can forget it (schema 40, see `EventTimestamp`).
            updatedAtMillis = EventTimestamp.ofWallClock(updatedAt),
            createdByFirebaseUid = createdByFirebaseUid,
            lastModifiedBy = lastModifiedBy,
            syncedToFirestore = syncedToFirestore,
            familyId = familyId
        )
    }

    /**
     * Converts ChildInfo to a Firestore map.
     *
     * @param audience The `sharedWith` UIDs this write should publish to, from
     *   [ChildInfoAudience.entitled]. Taken as a parameter rather than derived in here so
     *   both callers ([upsertChildInfo] and [pullOnce]) go through the single
     *   policy instead of each computing their own.
     */
    internal fun ChildInfo.toFirestoreMap(audience: List<String>): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "childName" to childName,
            "dateOfBirth" to dateOfBirth?.let(IsoText::dateTime),
            "medications" to medications.map {
                mapOf(
                    "name" to it.name,
                    "dosage" to it.dosage,
                    "frequency" to it.frequency,
                    "notes" to it.notes
                )
            },
            "activities" to activities.map {
                mapOf(
                    "name" to it.name,
                    "schedule" to it.schedule,
                    "location" to it.location,
                    "contactPerson" to it.contactPerson,
                    "contactPhone" to it.contactPhone
                )
            },
            "allergies" to allergies,
            "medicalNotes" to medicalNotes,
            "emergencyContacts" to emergencyContacts.map {
                mapOf(
                    "name" to it.name,
                    "relationship" to it.relationship,
                    "phone" to it.phone,
                    "alternatePhone" to it.alternatePhone
                )
            },
            "schoolInfo" to schoolInfo?.let {
                mapOf(
                    "name" to it.name,
                    "address" to it.address,
                    "phone" to it.phone,
                    "teacherName" to it.teacherName,
                    "teacherEmail" to it.teacherEmail,
                    "grade" to it.grade
                )
            },
            "medicalProfile" to StoredJson.decodeObject(StoredJson.encode(MedicalProfile.serializer(), medicalProfile)),
            "medicalPhotos" to medicalPhotos,
            "guests" to ChildInfoGuests.encode(guests),
            "createdAt" to IsoText.dateTime(createdAt),
            // UTC, offset-free: the field keeps its name and type so an older build still parses
            // it, and only the zone it expresses changed (schema 40, see `EventTimestamp`).
            "updatedAt" to EventTimestamp.toWire(EventTimestamp.ofWallClock(updatedAt)),
            "createdByFirebaseUid" to createdByFirebaseUid,
            "lastModifiedBy" to lastModifiedBy,
            "sharedWith" to audience,
            "familyId" to (familyId ?: "")
        )
    }

    /**
     * Converts Firestore map to ChildInfo.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun Map<String, Any?>.toChildInfo(): ChildInfo {
        return ChildInfo(
            id = this["id"] as String,
            childName = this["childName"] as String,
            dateOfBirth = (this["dateOfBirth"] as? String)?.let(IsoText::parseDateTime),
            medications = (this["medications"] as? List<Map<String, Any?>>)?.map {
                com.coparently.app.domain.model.Medication(
                    name = it["name"] as String,
                    dosage = it["dosage"] as String,
                    frequency = it["frequency"] as String,
                    notes = it["notes"] as? String
                )
            } ?: emptyList(),
            activities = (this["activities"] as? List<Map<String, Any?>>)?.map {
                com.coparently.app.domain.model.Activity(
                    name = it["name"] as String,
                    schedule = it["schedule"] as String,
                    location = it["location"] as? String,
                    contactPerson = it["contactPerson"] as? String,
                    contactPhone = it["contactPhone"] as? String
                )
            } ?: emptyList(),
            allergies = (this["allergies"] as? List<String>) ?: emptyList(),
            medicalNotes = this["medicalNotes"] as? String,
            emergencyContacts = (this["emergencyContacts"] as? List<Map<String, Any?>>)?.map {
                com.coparently.app.domain.model.EmergencyContact(
                    name = it["name"] as String,
                    relationship = it["relationship"] as String,
                    phone = it["phone"] as String,
                    alternatePhone = it["alternatePhone"] as? String
                )
            } ?: emptyList(),
            schoolInfo = (this["schoolInfo"] as? Map<String, Any?>)?.let {
                com.coparently.app.domain.model.SchoolInfo(
                    name = it["name"] as String,
                    address = it["address"] as? String,
                    phone = it["phone"] as? String,
                    teacherName = it["teacherName"] as? String,
                    teacherEmail = it["teacherEmail"] as? String,
                    grade = it["grade"] as? String
                )
            },
            medicalProfile = (
                (this["medicalProfile"] as? Map<*, *>)?.let {
                    StoredJson.decodeOrNull(MedicalProfile.serializer(), StoredJson.encodeValue(it))
                } ?: MedicalProfile()
                ).withSanitizedVaccinationNames(),
            medicalPhotos = ChildInfoPhotos.decode(this["medicalPhotos"]),
            guests = ChildInfoGuests.decode(this["guests"]),
            createdAt = IsoText.parseDateTime(this["createdAt"] as String),
            // The instant the document names, shown in this phone's zone (see `EventTimestamp`).
            updatedAt = EventTimestamp.toWallClock(EventTimestamp.fromWire(this["updatedAt"] as String)),
            createdByFirebaseUid = this["createdByFirebaseUid"] as? String,
            lastModifiedBy = this["lastModifiedBy"] as? String,
            syncedToFirestore = true,
            familyId = (this["familyId"] as? String)?.takeIf { it.isNotEmpty() }
        )
    }
}

/** The stored forms of a child's lists and school details, as [StoredJson] reads and writes them. */
private val MEDICATIONS = ListSerializer(com.coparently.app.domain.model.Medication.serializer())
private val ACTIVITIES = ListSerializer(com.coparently.app.domain.model.Activity.serializer())
private val EMERGENCY_CONTACTS = ListSerializer(com.coparently.app.domain.model.EmergencyContact.serializer())
private val SCHOOL_INFO = com.coparently.app.domain.model.SchoolInfo.serializer()
