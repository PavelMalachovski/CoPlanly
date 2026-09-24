package com.coparently.app.wire

import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.Vaccination
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `pets`: read by `PetRepositoryImpl.toPet` (the pull), stored through Room (`toEntity`/
 * `toDomain`) and written back by `toFirestoreMap` with `set()`.
 */
internal object PetWireContract : WireContract {

    private val repository by lazy { PetRepositoryImpl(mockk(), mockk(), mockk(), mockk()) }

    override val collection = "pets"

    override val alwaysWrites = setOf("id", "name", "species", "createdAt", "updatedAt", "sharedWith", "familyId")

    override val blankMeansAbsent = setOf("familyId")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val pet = runCatching { with(repository) { document.toPet() } }.getOrNull() ?: return null
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
        return with(repository) { document.toPet().toEntity().toDomain().toFirestoreMap(audience) }
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "PetRepositoryImpl's upsert document: a medication, a dated vaccination, a photo.",
            document = with(repository) { pet().toFirestoreMap(listOf("uidA", "uidB")) }
        )
    )

    private fun pet() = Pet(
        id = "pet-current-1",
        name = "Rex",
        species = PetSpecies.DOG,
        breed = "Beagle",
        dateOfBirth = LocalDateTime.of(2021, 3, 14, 0, 0),
        medications = listOf(Medication(name = "Wormer", dosage = "1 tablet", frequency = "quarterly")),
        vaccinations = listOf(Vaccination(name = "Rabies", date = LocalDate.of(2026, 2, 1))),
        vetName = "Dr. Novak",
        photos = listOf("pet_photos/uidA/pet-current-1/1.jpg"),
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime.of(2026, 5, 2, 18, 45, 10),
        createdByFirebaseUid = "uidA",
        lastModifiedBy = "uidA",
        familyId = "uidA__uidB"
    )
}
