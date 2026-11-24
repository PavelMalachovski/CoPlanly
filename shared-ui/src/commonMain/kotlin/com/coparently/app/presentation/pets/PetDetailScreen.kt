package com.coparently.app.presentation.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.presentation.common.AppSnackbarHost
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.PetAvatar
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.RECORD_PORTRAIT_SIZE
import com.coparently.app.presentation.common.RecordPortrait
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.rememberCoParentName
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.medical_vaccination_no_date
import com.coparently.app.resources.pet_dob_value
import com.coparently.app.resources.pet_edit
import com.coparently.app.resources.pet_medication_summary
import com.coparently.app.resources.pet_photos_delete_failed
import com.coparently.app.resources.pet_photos_upload_failed
import com.coparently.app.resources.pet_record_gone_description
import com.coparently.app.resources.pet_record_gone_title
import com.coparently.app.resources.pet_section_basic
import com.coparently.app.resources.pet_section_feeding
import com.coparently.app.resources.pet_section_medications
import com.coparently.app.resources.pet_section_special_needs
import com.coparently.app.resources.pet_section_vaccinations
import com.coparently.app.resources.pet_section_vet
import com.coparently.app.resources.pets_back
import com.coparently.app.resources.pets_screen_title
import com.coparently.app.utils.readerMediumDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * One pet's record: its portrait and everything the family holds about it, read-only, every row
 * opening the editor (24 November 2026, owner: "the pet screen like the child's").
 *
 * Modelled on `ChildDetailScreen`: the pet is looked up **by id** from the list [PetsViewModel]
 * already holds, never from `currentPet`, which belongs to the editor — a read-only screen needs no
 * per-id observation of its own, the list flow re-emits on every write. The portrait is the first
 * of `Pet.photos` ([PetsViewModel.setPhoto], [PetsViewModel.removePhoto]); the editor's photo strip
 * keeps the rest.
 *
 * @param petId The pet to show.
 * @param onNavigateBack Up-arrow.
 * @param onEditClick Opens the editor, by id.
 * @param viewModel ViewModel for the pets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: PetsViewModel = koinViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    PhotoErrorMessages(viewModel, snackbarHostState)
    val pet = (uiState as? PetsUiState.Success)?.pets?.firstOrNull { it.id == petId }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            PetDetailTopBar(
                pet = pet,
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateBack()
                },
                onEditClick = onEditClick
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState is PetsUiState.Loading -> ListSkeleton(rows = 3)
                pet != null -> PetDetailContent(
                    pet = pet,
                    onEditClick = onEditClick,
                    portrait = {
                        RecordPortrait(
                            hasPhoto = pet.photos.isNotEmpty(),
                            coParentName = rememberCoParentName(),
                            onPick = { uri -> viewModel.setPhoto(pet, uri) },
                            onRemove = { viewModel.removePhoto(pet) }
                        ) { PetAvatar(pet, RECORD_PORTRAIT_SIZE) }
                    }
                )
                // The pet was deleted, here or on the co-parent's phone, while this screen was open
                // (or the list could not be read): saying so beats an empty page.
                else -> EmptyState(
                    icon = Icons.Default.Pets,
                    title = stringResource(Res.string.pet_record_gone_title),
                    description = stringResource(Res.string.pet_record_gone_description),
                    actionLabel = stringResource(Res.string.pets_back),
                    onAction = onNavigateBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Says on the snackbar why a photo did not make it on or off the record, once per failure. */
@Composable
private fun PhotoErrorMessages(viewModel: PetsViewModel, snackbarHostState: SnackbarHostState) {
    val photoError by viewModel.photoError.collectAsState()
    val uploadFailed = stringResource(Res.string.pet_photos_upload_failed)
    val deleteFailed = stringResource(Res.string.pet_photos_delete_failed)
    LaunchedEffect(photoError) {
        val message = when (photoError) {
            PetPhotoError.UPLOAD_FAILED -> uploadFailed
            PetPhotoError.DELETE_FAILED -> deleteFailed
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearPhotoError()
        }
    }
}

/**
 * The pet's name and the up arrow. Every row of the summary opens the editor; on the iPhone the bar
 * also carries Edit (`pencil`) while the record is there to edit, as a child's does. Android's bar
 * keeps no pencil: the rows are its way in.
 */
@Composable
private fun PetDetailTopBar(pet: Pet?, onBack: () -> Unit, onEditClick: (String) -> Unit) {
    val inShellBar = shellDrawsBars()
    val editLabel = stringResource(Res.string.pet_edit)
    val edit = pet?.takeIf { inShellBar }?.let { shown ->
        BarAction(
            label = editLabel,
            icon = Icons.Default.Edit,
            sfSymbol = "pencil",
            onClick = { onEditClick(shown.id) }
        )
    }
    PushedTopBar(
        title = pet?.name ?: stringResource(Res.string.pets_screen_title),
        backLabel = stringResource(Res.string.pets_back),
        onBack = onBack,
        actions = listOfNotNull(edit)
    )
}

/**
 * The pet's record as labelled groups: the basic information always, then feeding, medications,
 * vaccinations, the vet and special needs, each left out while it holds nothing — as the child's
 * summary leaves out an empty group. Every row opens the editor.
 */
@Composable
private fun PetDetailContent(
    pet: Pet,
    onEditClick: (String) -> Unit,
    portrait: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val onRowClick: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onEditClick(pet.id)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        item { portrait() }
        item { BasicInfoGroup(pet = pet, onClick = onRowClick) }
        pet.feedingNotes.presentOrNull()?.let { notes ->
            item { TextGroup(Res.string.pet_section_feeding, Icons.Default.Restaurant, notes, onRowClick) }
        }
        if (pet.medications.isNotEmpty()) {
            item { MedicationsGroup(medications = pet.medications, onClick = onRowClick) }
        }
        if (pet.vaccinations.isNotEmpty()) {
            item { VaccinationsGroup(vaccinations = pet.vaccinations, onClick = onRowClick) }
        }
        val vetName = pet.vetName.presentOrNull()
        val vetPhone = pet.vetPhone.presentOrNull()
        if (vetName != null || vetPhone != null) {
            item { VetGroup(name = vetName, phone = vetPhone, onClick = onRowClick) }
        }
        pet.specialNeeds.presentOrNull()?.let { needs ->
            item { TextGroup(Res.string.pet_section_special_needs, Icons.Default.Info, needs, onRowClick) }
        }
    }
}

/** The pet's identity: species and breed, and its date of birth when recorded. */
@Composable
private fun BasicInfoGroup(pet: Pet, onClick: () -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.pet_section_basic))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.Pets,
                title = petSummary(pet),
                // The reader's date, as the child's summary prints it — not ISO (release audit R-6).
                supporting = pet.dateOfBirth?.let { dob ->
                    stringResource(Res.string.pet_dob_value, readerMediumDate(dob.date))
                },
                onClick = onClick
            )
        }
    }
}

/** A free-text field (feeding, special needs) as one row under its label. */
@Composable
private fun TextGroup(label: StringResource, icon: ImageVector, text: String, onClick: () -> Unit) {
    Column {
        GroupLabel(stringResource(label))
        SectionGroup {
            SectionRow(icon = icon, title = text, onClick = onClick)
        }
    }
}

/** One row per medication: its name as the title, dosage and frequency as the summary line. */
@Composable
private fun MedicationsGroup(medications: List<Medication>, onClick: () -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.pet_section_medications))
        SectionGroup {
            medications.forEachIndexed { index, medication ->
                SectionRow(
                    icon = Icons.Default.Favorite,
                    title = medication.name,
                    supporting = stringResource(
                        Res.string.pet_medication_summary,
                        medication.dosage,
                        medication.frequency
                    ),
                    onClick = onClick
                )
                if (index != medications.lastIndex) Divider()
            }
        }
    }
}

/** One row per vaccination: its name as the title, its date (or "not recorded") as the summary line. */
@Composable
private fun VaccinationsGroup(vaccinations: List<Vaccination>, onClick: () -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.pet_section_vaccinations))
        SectionGroup {
            vaccinations.forEachIndexed { index, vaccination ->
                SectionRow(
                    icon = Icons.Default.Vaccines,
                    title = vaccination.name,
                    supporting = vaccination.date?.let(::readerMediumDate)
                        ?: stringResource(Res.string.medical_vaccination_no_date),
                    onClick = onClick
                )
                if (index != vaccinations.lastIndex) Divider()
            }
        }
    }
}

/** The vet: the clinic's or vet's name as the title and the phone under it, whichever are known. */
@Composable
private fun VetGroup(name: String?, phone: String?, onClick: () -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.pet_section_vet))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.MedicalServices,
                title = name ?: phone.orEmpty(),
                supporting = phone.takeIf { name != null },
                onClick = onClick
            )
        }
    }
}

/** The text when it holds anything but whitespace, else null. */
private fun String?.presentOrNull(): String? = this?.takeIf { it.isNotBlank() }
