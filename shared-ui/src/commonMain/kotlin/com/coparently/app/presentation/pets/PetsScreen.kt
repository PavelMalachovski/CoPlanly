package com.coparently.app.presentation.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.Pet
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ErrorState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.PetAvatar
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.addBarAction
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.labelRes
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.pets_add
import com.coparently.app.resources.pets_back
import com.coparently.app.resources.pets_empty_state
import com.coparently.app.resources.pets_group_label
import com.coparently.app.resources.pets_screen_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The pets list: one row per pet, led by its photo, tapping opens the pet's own screen
 * ([PetDetailScreen], 24 November 2026), the FAB adds a new one in the editor.
 *
 * This comment used to say the child screen showed only the first record, "a documented
 * limitation". That stopped being true when `ChildInfoScreen` became a genuine list, and the
 * onboarding wizard — the last place in the app that insisted on exactly one of anything — has
 * since caught up too.
 *
 * @param onNavigateBack Callback for navigation back
 * @param onEditPet Opens the editor for a pet id (or "new")
 * @param onOpenPet Opens one pet's own screen, by id
 * @param viewModel ViewModel for managing pets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetsScreen(
    onNavigateBack: () -> Unit,
    onEditPet: (String) -> Unit,
    onOpenPet: (String) -> Unit,
    viewModel: PetsViewModel = koinViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    // On the iPhone the "+" is the bar's (MON-24 phase 3, part B), shown exactly when the floating
    // button would be.
    val inShellBar = shellDrawsBars()
    val hasPets = (uiState as? PetsUiState.Success)?.pets?.isNotEmpty() == true
    val addLabel = stringResource(Res.string.pets_add)
    val addPet: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onEditPet("new")
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = {
            PushedTopBar(
                title = stringResource(Res.string.pets_screen_title),
                backLabel = stringResource(Res.string.pets_back),
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateBack()
                },
                actions = listOfNotNull(addBarAction(addLabel, addPet).takeIf { inShellBar && hasPets })
            )
        },
        floatingActionButton = {
            if (hasPets && !inShellBar) {
                ExtendedFloatingActionButton(
                    onClick = addPet,
                    icon = { AppIcon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(addLabel) }
                )
            }
        }
    ) { paddingValues ->
        PetsContent(
            state = uiState,
            onRetry = viewModel::loadPets,
            onAddPet = addPet,
            onOpenPet = onOpenPet,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

/** The list, or what stands in for it: a spinner while loading, the error with Retry, or the empty state. */
@Composable
private fun PetsContent(
    state: PetsUiState,
    onRetry: () -> Unit,
    onAddPet: () -> Unit,
    onOpenPet: (String) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        when (state) {
            is PetsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is PetsUiState.Error -> {
                ErrorState(
                    message = state.message.asString(),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is PetsUiState.Success -> {
                if (state.pets.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Pets,
                        title = stringResource(Res.string.pets_empty_state),
                        actionLabel = stringResource(Res.string.pets_add),
                        onAction = onAddPet,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PetsList(pets = state.pets, onOpenPet = onOpenPet)
                }
            }
        }
    }
}

/**
 * One row per pet: its round photo (or its initial) leading, the name as the title, species (and
 * breed when known) as the summary line. A tap opens the pet's own screen, as a child's row does.
 */
@Composable
private fun PetsList(pets: List<Pet>, onOpenPet: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item {
            Column {
                GroupLabel(stringResource(Res.string.pets_group_label))
                SectionGroup {
                    pets.forEachIndexed { index, pet ->
                        SectionRow(
                            leading = { PetAvatar(pet, LIST_AVATAR_SIZE) },
                            title = pet.name,
                            supporting = petSummary(pet),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenPet(pet.id)
                            }
                        )
                        if (index != pets.lastIndex) Divider()
                    }
                }
            }
        }
    }
}

/** The pet's species, and its breed after it when one is known ("Dog · Beagle"). */
@Composable
internal fun petSummary(pet: Pet): String {
    val species = stringResource(pet.species.labelRes())
    return pet.breed?.takeIf { it.isNotBlank() }?.let { "$species · $it" } ?: species
}

/** Diameter of a pet's photo at the start of its row on the list. */
private val LIST_AVATAR_SIZE = 40.dp
