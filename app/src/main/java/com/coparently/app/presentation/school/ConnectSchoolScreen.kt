package com.coparently.app.presentation.school

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ErrorState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized

/**
 * Connecting a child's Bakaláři account (MON-8), one step at a time: the town, the school (or an
 * address typed by hand), the sign-in, whose account it is and which child and family it is for,
 * and what will be imported. In reconnect mode it is the sign-in alone.
 *
 * The password field lives in plain `remember` — never `rememberSaveable`, which would write it
 * into the saved instance state — and is emptied the moment it has been handed to the sign-in.
 *
 * @param onNavigateUp Leaves the flow.
 * @param onDone Called once the connection is saved (or signed in again).
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSchoolScreen(
    onNavigateUp: () -> Unit,
    onDone: () -> Unit,
    viewModel: ConnectSchoolViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var password by remember { mutableStateOf("") }
    val goBack: () -> Unit = { if (!viewModel.back()) onNavigateUp() }

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state.reconnecting) {
                        R.string.school_connect_reconnect_title
                    } else {
                        R.string.school_connect_title
                    }
                    Text(stringResource(title))
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            PrimaryAction(
                state = state,
                passwordEntered = password.isNotEmpty(),
                onSignIn = {
                    viewModel.signIn(password)
                    password = ""
                },
                viewModel = viewModel
            )
        }
    ) { padding ->
        StepContent(
            state = state,
            viewModel = viewModel,
            password = password,
            onPassword = { password = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/** The step on screen. */
@Composable
private fun StepContent(
    state: ConnectSchoolUiState,
    viewModel: ConnectSchoolViewModel,
    password: String,
    onPassword: (String) -> Unit,
    modifier: Modifier
) {
    when (state.step) {
        ConnectStep.TOWN -> TownStep(state, viewModel, modifier)
        ConnectStep.SCHOOL -> SchoolStep(state, viewModel, modifier)
        ConnectStep.MANUAL -> StepColumn(state, modifier) {
            OutlinedTextField(
                value = state.manualUrl,
                onValueChange = viewModel::onManualUrl,
                label = { Text(stringResource(R.string.school_connect_url_label)) },
                supportingText = { Text(stringResource(R.string.school_connect_url_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ConnectStep.CREDENTIALS -> StepColumn(state, modifier) {
            CredentialsFields(
                state = state,
                password = password,
                onPassword = onPassword,
                onUsername = viewModel::onUsername
            )
        }
        ConnectStep.CHOOSE -> StepColumn(state, modifier) { ChooseStep(state, viewModel) }
        ConnectStep.CONFIRM -> StepColumn(state, modifier) { ConfirmStep(state) }
    }
}

/** The step's one primary action, pinned at the bottom; nothing on the two list steps. */
@Composable
private fun PrimaryAction(
    state: ConnectSchoolUiState,
    passwordEntered: Boolean,
    onSignIn: () -> Unit,
    viewModel: ConnectSchoolViewModel
) {
    when (state.step) {
        ConnectStep.TOWN, ConnectStep.SCHOOL -> Unit
        ConnectStep.MANUAL -> StickyActionBar(
            label = stringResource(R.string.school_connect_url_check),
            onClick = viewModel::checkManualUrl,
            enabled = state.manualUrl.isNotBlank(),
            busy = state.isBusy
        )
        ConnectStep.CREDENTIALS -> StickyActionBar(
            label = stringResource(R.string.school_connect_sign_in),
            onClick = onSignIn,
            enabled = !state.isLoading && state.username.isNotBlank() && passwordEntered,
            busy = state.isBusy
        )
        ConnectStep.CHOOSE -> StickyActionBar(
            label = stringResource(R.string.school_connect_continue),
            onClick = viewModel::toConfirm,
            enabled = state.canContinue
        )
        ConnectStep.CONFIRM -> StickyActionBar(
            label = stringResource(R.string.school_connect_save),
            onClick = viewModel::connect,
            busy = state.isBusy
        )
    }
}

/** A scrolling step with the error, if any, first. */
@Composable
private fun StepColumn(state: ConnectSchoolUiState, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        state.error?.let { InlineBanner(text = it.asString(), tone = BannerTone.ATTENTION) }
        content()
    }
}

/** The town search: a field, the manual route, and the matching towns. */
@Composable
private fun TownStep(state: ConnectSchoolUiState, viewModel: ConnectSchoolViewModel, modifier: Modifier) {
    if (state.towns.isEmpty() && state.error != null && !state.isLoading) {
        ErrorState(message = state.error.asString(), onRetry = viewModel::loadTowns, modifier = modifier)
        return
    }
    StepColumn(state.copy(error = null), modifier) {
        OutlinedTextField(
            value = state.townQuery,
            onValueChange = viewModel::onTownQuery,
            label = { Text(stringResource(R.string.school_connect_town_label)) },
            supportingText = { Text(stringResource(R.string.school_connect_town_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SectionGroup {
            SectionRow(
                icon = Icons.Default.EditNote,
                title = stringResource(R.string.school_connect_manual),
                onClick = viewModel::enterManually
            )
        }
        when {
            state.isLoading -> ListSkeleton()
            state.townQuery.isNotBlank() -> TownList(state, viewModel)
        }
    }
}

@Composable
private fun TownList(state: ConnectSchoolUiState, viewModel: ConnectSchoolViewModel) {
    val towns = state.visibleTowns
    if (towns.isEmpty()) {
        Text(
            text = stringResource(R.string.school_connect_no_towns),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    SectionGroup {
        towns.take(MAX_TOWNS).forEachIndexed { index, town ->
            if (index > 0) Divider()
            SectionRow(
                icon = Icons.Default.LocationCity,
                title = town.name,
                onClick = { viewModel.chooseTown(town.name) }
            )
        }
    }
    if (towns.size > MAX_TOWNS) {
        Text(
            text = stringResource(R.string.school_connect_more_towns),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The schools in the chosen town, and the manual route for one that is not listed. */
@Composable
private fun SchoolStep(state: ConnectSchoolUiState, viewModel: ConnectSchoolViewModel, modifier: Modifier) {
    val town = state.town.orEmpty()
    if (state.error != null && state.schools.isEmpty() && !state.isLoading) {
        ErrorState(message = state.error.asString(), onRetry = { viewModel.chooseTown(town) }, modifier = modifier)
        return
    }
    StepColumn(state, modifier) {
        Column {
            GroupLabel(stringResource(R.string.school_connect_school_label, town))
            when {
                state.isLoading -> ListSkeleton()
                state.schools.isEmpty() -> EmptyState(
                    icon = Icons.Default.School,
                    title = stringResource(R.string.school_connect_no_schools),
                    actionLabel = stringResource(R.string.school_connect_manual),
                    onAction = viewModel::enterManually
                )
                else -> SectionGroup {
                    state.schools.forEachIndexed { index, school ->
                        if (index > 0) Divider()
                        SectionRow(
                            icon = Icons.Default.School,
                            title = school.name,
                            onClick = { viewModel.chooseSchool(school) }
                        )
                    }
                }
            }
        }
        SectionGroup {
            SectionRow(
                icon = Icons.Default.EditNote,
                title = stringResource(R.string.school_connect_manual),
                onClick = viewModel::enterManually
            )
        }
    }
}

/** Username and password, and what happens to the password. */
@Composable
private fun CredentialsFields(
    state: ConnectSchoolUiState,
    password: String,
    onPassword: (String) -> Unit,
    onUsername: (String) -> Unit
) {
    Text(
        text = state.schoolName.ifBlank { state.baseUrl.orEmpty() },
        style = MaterialTheme.typography.titleMediumEmphasized
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = onUsername,
        label = { Text(stringResource(R.string.school_connect_username)) },
        readOnly = state.reconnecting,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        label = { Text(stringResource(R.string.school_connect_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    InlineBanner(
        text = stringResource(R.string.school_connect_password_note),
        icon = Icons.Default.Lock
    )
}

/** Whose account it is, what it may read, and which family and child it is for. */
@Composable
private fun ChooseStep(state: ConnectSchoolUiState, viewModel: ConnectSchoolViewModel) {
    val account = state.account ?: return
    Column {
        GroupLabel(stringResource(R.string.school_connect_signed_in_as))
        SectionGroup {
            SectionRow(icon = Icons.Default.AccountCircle, title = account.displayName)
        }
    }
    RightsNotice(account)
    if (state.choosesFamily) {
        Column {
            GroupLabel(stringResource(R.string.school_connect_family_label))
            SectionGroup {
                state.families.forEachIndexed { index, family ->
                    if (index > 0) Divider()
                    SectionRow(
                        icon = Icons.Default.FamilyRestroom,
                        title = familyName(family, index),
                        onClick = { viewModel.chooseFamily(family.familyId) },
                        trailing = { RadioButton(selected = family.familyId == state.familyId, onClick = null) }
                    )
                }
            }
        }
    }
    if (!state.choosesFamily || state.familyId != null) ChildChoices(state, viewModel)
}

@Composable
private fun RightsNotice(account: SignedInAccount) {
    val text = when {
        !account.canImport -> R.string.school_connect_no_rights
        !account.canReadTimetable -> R.string.school_connect_no_timetable
        !account.canReadEvents -> R.string.school_connect_no_events
        else -> null
    } ?: return
    InlineBanner(text = stringResource(text), tone = BannerTone.ATTENTION)
}

@Composable
private fun ChildChoices(state: ConnectSchoolUiState, viewModel: ConnectSchoolViewModel) {
    if (state.children.isEmpty()) {
        InlineBanner(text = stringResource(R.string.school_connect_no_children), tone = BannerTone.ATTENTION)
        return
    }
    Column {
        GroupLabel(stringResource(R.string.school_connect_child_label))
        SectionGroup {
            state.children.forEachIndexed { index, child ->
                if (index > 0) Divider()
                SectionRow(
                    title = child.name,
                    onClick = { viewModel.chooseChild(child.id) },
                    trailing = { RadioButton(selected = child.id == state.childId, onClick = null) }
                )
            }
        }
    }
}

/** What will be imported, and what will not. */
@Composable
private fun ConfirmStep(state: ConnectSchoolUiState) {
    val account = state.account ?: return
    val childName = state.children.firstOrNull { it.id == state.childId }?.name.orEmpty()
    Column {
        GroupLabel(stringResource(R.string.school_connect_confirm_label))
        SectionGroup {
            var first = true
            if (account.canReadEvents) {
                SectionRow(icon = Icons.Default.Event, title = stringResource(R.string.school_connect_confirm_events))
                first = false
            }
            if (account.canReadTimetable) {
                if (!first) Divider()
                SectionRow(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(R.string.school_connect_confirm_hours)
                )
                Divider()
                SectionRow(
                    icon = Icons.Default.EventBusy,
                    title = stringResource(R.string.school_connect_confirm_days_off)
                )
            }
        }
    }
    InlineBanner(text = stringResource(R.string.school_connect_confirm_shared, childName))
    Text(
        text = stringResource(R.string.school_connect_confirm_not_imported),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A family as the co-parent's name, or by its place in the list when the name is unknown. */
@Composable
private fun familyName(family: FamilyOption, index: Int): String =
    if (family.partnerName.isNotBlank()) {
        stringResource(R.string.school_connect_family_with, family.partnerName)
    } else {
        stringResource(R.string.school_connect_family_unnamed, index + 1)
    }

/** At most this many towns are listed; typing more narrows the list. */
private const val MAX_TOWNS = 50
