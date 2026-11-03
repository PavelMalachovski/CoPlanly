package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.serialization.builtins.ListSerializer

// The pet document's four mappers, entity and domain and document in both directions, in one file
// on purpose (see `PetRepositoryImpl`): the child record's document map exists in seven places
// across two files, and that duplication has already shipped bugs twice. Top-level rather than
// members of the repository since MON-24 phase 2 (Task 8d), so the pets wire contract reaches them
// without building a repository; they read no repository state. Public behind `InternalSharedApi`
// for that contract alone: it sits in commonTest's `wire/`, which the app's unit tests compile too,
// and `internal` would stop at this module.

/**
 * Converts PetEntity to domain Pet.
 */
@InternalSharedApi
fun PetEntity.toDomain(): Pet {
    return Pet(
        id = id,
        name = name,
        species = PetSpecies.fromStored(species),
        breed = breed,
        dateOfBirth = dateOfBirth,
        medications = StoredJson.decodeOrNull(MEDICATIONS, medicationsJson) ?: emptyList(),
        vaccinations = StoredJson.decodeOrNull(VACCINATIONS, vaccinationsJson) ?: emptyList(),
        specialNeeds = specialNeeds,
        feedingNotes = feedingNotes,
        vetName = vetName,
        vetPhone = vetPhone,
        photos = StoredJson.decodeOrNull(StoredJson.strings, photosJson) ?: emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdByFirebaseUid = createdByFirebaseUid,
        lastModifiedBy = lastModifiedBy,
        syncedToFirestore = syncedToFirestore,
        familyId = familyId
    )
}

/**
 * Converts domain Pet to PetEntity.
 */
@InternalSharedApi
fun Pet.toEntity(): PetEntity {
    return PetEntity(
        id = id,
        name = name,
        species = species.name,
        breed = breed,
        dateOfBirth = dateOfBirth,
        medicationsJson = StoredJson.encode(MEDICATIONS, medications),
        vaccinationsJson = StoredJson.encode(VACCINATIONS, vaccinations),
        specialNeeds = specialNeeds,
        feedingNotes = feedingNotes,
        vetName = vetName,
        vetPhone = vetPhone,
        photosJson = StoredJson.encode(StoredJson.strings, photos),
        createdAt = createdAt,
        updatedAt = updatedAt,
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
 * Converts a Pet to a Firestore map.
 *
 * @param audience The `sharedWith` UIDs this write should publish to, from
 *   [PetAudience.entitled] — a parameter so every writer goes through the one policy.
 */
@InternalSharedApi
fun Pet.toFirestoreMap(audience: List<String>): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "species" to species.name,
        "breed" to breed,
        "dateOfBirth" to dateOfBirth?.let(IsoText::dateTime),
        "medications" to medications.map {
            mapOf(
                "name" to it.name,
                "dosage" to it.dosage,
                "frequency" to it.frequency,
                "notes" to it.notes
            )
        },
        "vaccinations" to vaccinations.map {
            mapOf(
                "name" to it.name,
                "date" to it.date?.let(IsoText::date)
            )
        },
        "specialNeeds" to specialNeeds,
        "feedingNotes" to feedingNotes,
        "vetName" to vetName,
        "vetPhone" to vetPhone,
        "photos" to photos,
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
 * Converts a Firestore map to a Pet.
 *
 * Defensive on every field the rule does not require: a document written by another
 * build must degrade to defaults rather than throw and poison the whole pull.
 */
@Suppress("UNCHECKED_CAST")
@InternalSharedApi
fun Map<String, Any?>.toPet(): Pet {
    return Pet(
        id = this["id"] as String,
        name = this["name"] as String,
        species = PetSpecies.fromStored(this["species"] as? String),
        breed = this["breed"] as? String,
        dateOfBirth = (this["dateOfBirth"] as? String)?.let(IsoText::parseDateTime),
        medications = (this["medications"] as? List<Map<String, Any?>>)?.mapNotNull {
            val name = it["name"] as? String ?: return@mapNotNull null
            Medication(
                name = name,
                dosage = it["dosage"] as? String ?: "",
                frequency = it["frequency"] as? String ?: "",
                notes = it["notes"] as? String
            )
        } ?: emptyList(),
        vaccinations = (this["vaccinations"] as? List<Map<String, Any?>>)?.mapNotNull {
            val name = it["name"] as? String ?: return@mapNotNull null
            Vaccination(
                name = name,
                date = (it["date"] as? String)?.let { date ->
                    runCatching { IsoText.parseDate(date) }.getOrNull()
                }
            )
        } ?: emptyList(),
        specialNeeds = this["specialNeeds"] as? String,
        feedingNotes = this["feedingNotes"] as? String,
        vetName = this["vetName"] as? String,
        vetPhone = this["vetPhone"] as? String,
        photos = (this["photos"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        createdAt = IsoText.parseDateTime(this["createdAt"] as String),
        // The instant the document names, shown in this phone's zone (see `EventTimestamp`).
        updatedAt = EventTimestamp.toWallClock(EventTimestamp.fromWire(this["updatedAt"] as String)),
        createdByFirebaseUid = this["createdByFirebaseUid"] as? String,
        lastModifiedBy = this["lastModifiedBy"] as? String,
        syncedToFirestore = true,
        familyId = (this["familyId"] as? String)?.takeIf { it.isNotEmpty() }
    )
}

/** The stored forms of a pet's medications and vaccinations, as [StoredJson] reads and writes them. */
private val MEDICATIONS = ListSerializer(Medication.serializer())
private val VACCINATIONS = ListSerializer(Vaccination.serializer())
