package com.coparently.app.presentation.pets

import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.Vaccination
import java.time.LocalDateTime

/**
 * The values the pet form edits, compared with what it was seeded with to tell whether Back
 * would drop an edit (D-11). A photo picked or marked for removal counts as an edit.
 */
internal data class PetFields(
    val name: String,
    val species: PetSpecies,
    val breed: String,
    val dateOfBirth: LocalDateTime?,
    val medications: List<Medication>,
    val vaccinations: List<Vaccination>,
    val specialNeeds: String,
    val feedingNotes: String,
    val vetName: String,
    val vetPhone: String,
    val pickedPhotos: List<String>,
    val removedPhotos: List<String>
) {
    /** A blank form, and the form seeded from a stored record. */
    companion object {
        val EMPTY = PetFields(
            name = "",
            species = PetSpecies.OTHER,
            breed = "",
            dateOfBirth = null,
            medications = emptyList(),
            vaccinations = emptyList(),
            specialNeeds = "",
            feedingNotes = "",
            vetName = "",
            vetPhone = "",
            pickedPhotos = emptyList(),
            removedPhotos = emptyList()
        )

        fun of(pet: Pet) = EMPTY.copy(
            name = pet.name,
            species = pet.species,
            breed = pet.breed ?: "",
            dateOfBirth = pet.dateOfBirth,
            medications = pet.medications,
            vaccinations = pet.vaccinations,
            specialNeeds = pet.specialNeeds ?: "",
            feedingNotes = pet.feedingNotes ?: "",
            vetName = pet.vetName ?: "",
            vetPhone = pet.vetPhone ?: ""
        )
    }
}
