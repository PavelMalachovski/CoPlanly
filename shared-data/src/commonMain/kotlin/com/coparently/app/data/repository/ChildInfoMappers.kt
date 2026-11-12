package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.sync.ChildInfoGuests
import com.coparently.app.data.sync.ChildInfoPhotos
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.serialization.builtins.ListSerializer

// The child record's four mappers, entity and domain and document in both directions. Top-level
// rather than members of `ChildInfoRepositoryImpl` since MON-24 phase 2 (Task 8e), with their
// bodies unchanged, so the `child_info` wire contract reaches them without building a repository;
// they read no repository state. Public behind `InternalSharedApi` for that contract and the app's
// repository tests alone: the contract runs from the app's list, because `SyncService`, still in
// the app, writes `child_info` too, and `internal` would stop at this module.

/**
 * Converts ChildInfoEntity to domain ChildInfo.
 */
@InternalSharedApi
fun ChildInfoEntity.toDomain(): ChildInfo {
    return ChildInfo(
        id = id,
        childName = childName,
        dateOfBirth = dateOfBirth,
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
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdByFirebaseUid = createdByFirebaseUid,
        lastModifiedBy = lastModifiedBy,
        syncedToFirestore = syncedToFirestore,
        familyId = familyId,
        photo = photo
    )
}

/**
 * Converts domain ChildInfo to ChildInfoEntity.
 */
@InternalSharedApi
fun ChildInfo.toEntity(): ChildInfoEntity {
    return ChildInfoEntity(
        id = id,
        childName = childName,
        dateOfBirth = dateOfBirth,
        medicationsJson = StoredJson.encode(MEDICATIONS, medications),
        activitiesJson = StoredJson.encode(ACTIVITIES, activities),
        allergiesJson = StoredJson.encode(StoredJson.strings, allergies),
        medicalNotes = medicalNotes,
        emergencyContactsJson = StoredJson.encode(EMERGENCY_CONTACTS, emergencyContacts),
        schoolInfoJson = schoolInfo?.let { StoredJson.encode(SCHOOL_INFO, it) },
        medicalProfileJson = StoredJson.encode(MedicalProfile.serializer(), medicalProfile),
        medicalPhotosJson = StoredJson.encode(StoredJson.strings, medicalPhotos),
        guestsJson = StoredJson.encodeValue(ChildInfoGuests.encode(guests)),
        createdAt = createdAt,
        updatedAt = updatedAt,
        // Derived at the one boundary every save crosses, from the wall clock each save path
        // already stamps — so no path can forget it (schema 40, see `EventTimestamp`).
        updatedAtMillis = EventTimestamp.ofWallClock(updatedAt),
        createdByFirebaseUid = createdByFirebaseUid,
        lastModifiedBy = lastModifiedBy,
        syncedToFirestore = syncedToFirestore,
        familyId = familyId,
        photo = photo
    )
}

/**
 * Converts ChildInfo to a Firestore map.
 *
 * @param audience The `sharedWith` UIDs this write should publish to, from
 *   [ChildInfoAudience.entitled]. Taken as a parameter rather than derived in here so
 *   both callers (`ChildInfoRepositoryImpl.upsertChildInfo` and `pullOnce`) go through the single
 *   policy instead of each computing their own.
 */
@InternalSharedApi
fun ChildInfo.toFirestoreMap(audience: List<String>): Map<String, Any?> {
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
        // A top-level key an older build drops on its next `set()` (wire contract, item 5):
        // accepted, the portrait is set again.
        "photo" to photo,
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
@InternalSharedApi
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.toChildInfo(): ChildInfo {
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
        photo = ChildInfoPhotos.decodePortrait(this["photo"]),
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

/** The stored forms of a child's lists and school details, as [StoredJson] reads and writes them. */
private val MEDICATIONS = ListSerializer(com.coparently.app.domain.model.Medication.serializer())
private val ACTIVITIES = ListSerializer(com.coparently.app.domain.model.Activity.serializer())
private val EMERGENCY_CONTACTS = ListSerializer(com.coparently.app.domain.model.EmergencyContact.serializer())
private val SCHOOL_INFO = com.coparently.app.domain.model.SchoolInfo.serializer()
