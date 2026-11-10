package com.coparently.app.presentation.common

import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.resources.Res
import com.coparently.app.resources.pet_species_bird
import com.coparently.app.resources.pet_species_cat
import com.coparently.app.resources.pet_species_dog
import com.coparently.app.resources.pet_species_fish
import com.coparently.app.resources.pet_species_other
import com.coparently.app.resources.pet_species_reptile
import com.coparently.app.resources.pet_species_rodent
import org.jetbrains.compose.resources.StringResource

/**
 * The string resource naming a species in the reader's language.
 *
 * Same shape as `BloodType.labelRes()` — not a property on the enum, so the domain model
 * stays free of Android, and the stored constant name never reaches the screen.
 *
 * @return the resource of the localized name for this species
 */
fun PetSpecies.labelRes(): StringResource = when (this) {
    PetSpecies.DOG -> Res.string.pet_species_dog
    PetSpecies.CAT -> Res.string.pet_species_cat
    PetSpecies.BIRD -> Res.string.pet_species_bird
    PetSpecies.RODENT -> Res.string.pet_species_rodent
    PetSpecies.FISH -> Res.string.pet_species_fish
    PetSpecies.REPTILE -> Res.string.pet_species_reptile
    PetSpecies.OTHER -> Res.string.pet_species_other
}
