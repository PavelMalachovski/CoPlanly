@file:OptIn(InternalSharedApi::class)

package com.coparently.app.wire

import com.coparently.app.data.repository.toDomain
import com.coparently.app.data.repository.toEntity
import com.coparently.app.data.repository.toFirestoreMap
import com.coparently.app.data.repository.toPet
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.Vaccination
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * `pets`: read by `toPet` (`PetRepositoryImpl`'s pull), stored through Room (`toEntity`/
 * `toDomain`) and written back by `toFirestoreMap` with `set()` — the mappers in `PetMappers.kt`,
 * top-level since MON-24 phase 2 (Task 8d), so this contract needs no repository and runs on the
 * iPhone's simulator too.
 *
 * It reads in any zone: a pet's `updatedAt` is a UTC instant on the wire and a wall clock in the
 * domain, so the current write's wall clock is made from that instant in the zone it runs in, and
 * the read reports the instant. The app's run, in UTC, writes the same document as before.
 */
internal object PetWireContract : WireContract {

    override val collection = "pets"

    override val alwaysWrites = setOf("id", "name", "species", "createdAt", "updatedAt", "sharedWith", "familyId")

    override val blankMeansAbsent = setOf("familyId")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val pet = runCatching { document.toPet() }.getOrNull() ?: return null
        return mapOf(
            "id" to pet.id,
            "name" to pet.name,
            "species" to pet.species.name,
            "vaccinations" to pet.vaccinations.map { "${it.name}@${it.date}" },
            "photos" to pet.photos,
            "updatedAtMillis" to EventTimestamp.ofWallClock(pet.updatedAt),
            "createdByFirebaseUid" to pet.createdByFirebaseUid,
            "familyId" to pet.familyId,
            "deleted" to false
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return null
        val audience = (document["sharedWith"] as? List<*>).orEmpty().filterIsInstance<String>()
        return document.toPet().toEntity().toDomain().toFirestoreMap(audience)
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "PetRepositoryImpl's upsert document: a medication, a dated vaccination, a photo.",
            document = pet().toFirestoreMap(listOf("uidA", "uidB"))
        )
    )

    private fun pet() = Pet(
        id = "pet-current-1",
        name = "Rex",
        species = PetSpecies.DOG,
        breed = "Beagle",
        dateOfBirth = LocalDateTime(2021, 3, 14, 0, 0),
        medications = listOf(Medication(name = "Wormer", dosage = "1 tablet", frequency = "quarterly")),
        vaccinations = listOf(Vaccination(name = "Rabies", date = LocalDate(2026, 2, 1))),
        vetName = "Dr. Novak",
        photos = listOf(
            "ph1|pet_photos/uidA__uidB/pet-current-1/5e2f7d31.jpg|image/jpeg|48213|" +
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        ),
        createdAt = LocalDateTime(2026, 5, 1, 8, 0),
        // 2026-05-02T18:45:10Z, as this phone's wall clock (see the KDoc).
        updatedAt = EventTimestamp.toWallClock(EventTimestamp.fromWire("2026-05-02T18:45:10")),
        createdByFirebaseUid = "uidA",
        lastModifiedBy = "uidA",
        familyId = "uidA__uidB"
    )
}
