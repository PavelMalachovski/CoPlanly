package com.coparently.app.presentation.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.model.Pet
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ErrorState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.labelRes
import com.coparently.app.presentation.theme.Spacing

/**
 * The pets list: one row per pet, tapping opens the editor, the FAB adds a new one.
 *
 * This comment used to say the child screen showed only the first record, "a documented
 * limitation". That stopped being true when `ChildInfoScreen` became a genuine list, and the
 * onboarding wizard — the last place in the app that insisted on exactly one of anything — has
 * since caught up too.
 *
 * @param onNavigateBack Callback for navigation back
 * @param onEditPet Opens the editor for a pet id (or "new")
 * @param viewModel ViewModel for managing pets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetsScreen(
    onNavigateBack: () -> Unit,
    onEditPet: (String) -> Unit,
    viewModel: PetsViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pets_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pets_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.syncPets()
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.pets_sync)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            val state = uiState
            if (state is PetsUiState.Success && state.pets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEditPet("new")
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.pets_add)) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is PetsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PetsUiState.Error -> {
                    ErrorState(
                        message = state.message.asString(),
                        onRetry = viewModel::loadPets,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is PetsUiState.Success -> {
                    if (state.pets.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Pets,
                            title = stringResource(R.string.pets_empty_state),
                            actionLabel = stringResource(R.string.pets_add),
                            onAction = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEditPet("new")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PetsList(pets = state.pets, onEditPet = onEditPet)
                    }
                }
            }
        }
    }
}

/** One row per pet: name as the title, species (and breed when known) as the summary line. */
@Composable
private fun PetsList(pets: List<Pet>, onEditPet: (String) -> Unit) {
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
                GroupLabel(stringResource(R.string.pets_group_label))
                SectionGroup {
                    pets.forEachIndexed { index, pet ->
                        val species = stringResource(pet.species.labelRes())
                        SectionRow(
                            icon = Icons.Default.Pets,
                            title = pet.name,
                            supporting = pet.breed?.takeIf { it.isNotBlank() }
                                ?.let { "$species · $it" } ?: species,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onEditPet(pet.id)
                            }
                        )
                        if (index != pets.lastIndex) Divider()
                    }
                }
            }
        }
    }
}
