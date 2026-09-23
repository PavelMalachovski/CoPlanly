package com.coparently.app.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.presentation.childinfo.components.AllergyEditor
import com.coparently.app.presentation.childinfo.components.DatePickerDialog
import com.coparently.app.presentation.childinfo.components.EmergencyContactEditor
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.CountryPicker
import com.coparently.app.presentation.common.MedicalProfileEditor
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.labelRes
import com.coparently.app.presentation.custody.labelRes
import com.coparently.app.presentation.theme.PARENT_COLOUR_PICKER_ENABLED
import com.coparently.app.presentation.theme.ParentColorChoice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The first-run questionnaire: the co-parent link, then the parent's own details, their
 * children's, the people who could help in an emergency, the cost split and the custody schedule.
 *
 * **The link comes first**, so that everything after it can open on what the co-parent has
 * already entered — see [OnboardingStep]. Two steps render no form of their own: the link and the
 * custody schedule hand off to `PairingScreen` and `CustodySetupScreen`, which already do those
 * jobs properly and are reachable from Settings anyway; duplicating them here would give the app
 * two custody editors to keep in step.
 *
 * Every data-collecting step carries the same one-line footnote, and the intro carries it in
 * full. It is what makes the questionnaire acceptable rather than intrusive: a parent asked for
 * their blood type without being told why would be right to close the app. The wording
 * deliberately does not claim the data is encrypted — it is not — and does not claim only they
 * can see it, because the co-parent can, by design.
 *
 * Stateless: everything lives in [OnboardingViewModel]; this renders and forwards.
 *
 * @param onFinished Leaves the wizard once onboarding has been recorded as complete
 * @param onOpenCustodySetup Opens the existing custody schedule editor
 * @param onOpenPairing Opens the existing pairing screen — on code entry when the argument is
 *   true, on this account's own code otherwise
 * @param viewModel Wizard state and mutations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenCustodySetup: () -> Unit,
    onOpenPairing: (enterCode: Boolean) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished()
    }

    // The wizard is the start destination, so an unhandled back press would close the app from
    // step 4. Inside the wizard, back means "the previous question".
    BackHandler(enabled = !uiState.isFirstStep) { viewModel.back() }

    Scaffold(
        topBar = { OnboardingTopBar(state = uiState) },
        bottomBar = {
            OnboardingBottomBar(
                state = uiState,
                onBack = viewModel::back,
                onSkip = viewModel::skip,
                onNext = viewModel::next
            )
        }
    ) { padding ->
        OnboardingBody(
            state = uiState,
            viewModel = viewModel,
            onOpenCustodySetup = onOpenCustodySetup,
            onOpenPairing = onOpenPairing,
            padding = padding
        )
    }
}

/** Title and how far along the wizard is, so no step feels open-ended. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTopBar(state: OnboardingUiState) {
    Column {
        TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) })
        // Counted over the steps this run will actually walk, not over the enum: a pets-only
        // family skips two steps, and "Step 3 of 8" over a six-step flow is a lie the progress
        // bar tells at the exact moment the parent is deciding whether to finish.
        LinearProgressIndicator(
            progress = { state.displayIndex.toFloat() / state.stepCount },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                R.string.onboarding_progress,
                state.displayIndex,
                state.stepCount
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** The scrolling body, switching on the current step. */
@Composable
private fun OnboardingBody(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onOpenCustodySetup: () -> Unit,
    onOpenPairing: (enterCode: Boolean) -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        when (state.step) {
            OnboardingStep.CoParent -> CoParentStep(state, onOpenPairing)
            OnboardingStep.Intro -> IntroStep()
            OnboardingStep.Family -> FamilyKindStep(state, viewModel)
            OnboardingStep.Pet -> PetStep(state, viewModel)
            OnboardingStep.Split -> SplitStep(state, viewModel)
            OnboardingStep.Profile -> ProfileStep(state, viewModel)
            OnboardingStep.Child -> ChildStep(state, viewModel)
            OnboardingStep.Relatives -> RelativesStep(state, viewModel)
            OnboardingStep.Custody -> CustodyStep(state, onOpenCustodySetup)
        }
    }
}

/**
 * The first step: link with the co-parent, and see what the link brought back.
 *
 * Unlinked, it offers both halves of pairing as two buttons — "I have their code" and "Invite" —
 * because the second parent to install the app is holding a code, and showing them their own code
 * first is the mix-up the pairing screen's two modes were built to prevent. Linked, it names the
 * co-parent and reports the fetch: running, found (as rows, one per kind of record, so no locale
 * has to pluralise "2 children and 1 pet"), or nothing yet — which is a real answer, not an error:
 * the co-parent's phone sends its records on its next sync, and the following steps keep listening.
 */
@Composable
private fun CoParentStep(state: OnboardingUiState, onOpenPairing: (enterCode: Boolean) -> Unit) {
    when (val link = state.coParent) {
        is CoParentLink.Linked -> LinkedCoParent(
            name = link.name.ifBlank { stringResource(R.string.pairing_default_partner_name) },
            fetch = state.fetch
        )
        CoParentLink.None, CoParentLink.Unknown -> {
            StepHeading(title = R.string.onboarding_coparent_title, body = R.string.onboarding_coparent_body)
            Button(onClick = { onOpenPairing(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_coparent_enter_code))
            }
            OutlinedButton(onClick = { onOpenPairing(false) }, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_coparent_invite))
            }
        }
    }
}

/** The linked half of [CoParentStep]: who, and what has come across so far. */
@Composable
private fun LinkedCoParent(name: String, fetch: CoParentFetch) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.onboarding_coparent_linked_title, name),
            style = MaterialTheme.typography.headlineSmall
        )
    }
    Text(
        text = stringResource(R.string.onboarding_coparent_linked_body, name),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (fetch) {
        CoParentFetch.Idle, CoParentFetch.Running -> StatusRow(
            text = stringResource(R.string.onboarding_coparent_fetching, name)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        is CoParentFetch.Done -> if (fetch.found.isEmpty) {
            StatusRow(text = stringResource(R.string.onboarding_coparent_nothing_yet, name)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            FoundRecords(name = name, found = fetch.found)
        }
    }
}

/** One line of status beside an icon or a spinner. */
@Composable
private fun StatusRow(text: String, leading: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading()
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the fetch found, one row per kind of record that came across. */
@Composable
private fun FoundRecords(name: String, found: CoParentData) {
    SectionHeading(text = stringResource(R.string.onboarding_coparent_found_title, name))
    val setUp = stringResource(R.string.onboarding_coparent_found_set)
    SectionGroup {
        val rows = buildList {
            if (found.children > 0) {
                add(
                    Triple(
                        Icons.Default.ChildCare,
                        R.string.onboarding_coparent_found_children,
                        found.children.toString()
                    )
                )
            }
            if (found.pets > 0) {
                add(Triple(Icons.Default.Pets, R.string.onboarding_coparent_found_pets, found.pets.toString()))
            }
            if (found.hasCustodySchedule) {
                add(Triple(Icons.Default.CalendarMonth, R.string.onboarding_coparent_found_custody, setUp))
            }
            if (found.hasSplitAgreement) {
                add(Triple(Icons.Default.Balance, R.string.onboarding_coparent_found_split, setUp))
            }
        }
        rows.forEachIndexed { index, (icon, title, value) ->
            SectionRow(
                icon = icon,
                title = stringResource(title),
                trailing = {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            if (index != rows.lastIndex) Divider()
        }
    }
}

/**
 * The only screen that exists purely to explain, and it earns its place: a questionnaire that
 * asks a separated parent for a blood group without saying why reads as intrusive. It carries
 * the note in full, so the later steps need only the one-line footnote.
 */
@Composable
private fun IntroStep() {
    StepHeading(title = R.string.onboarding_intro_title)
    Text(
        text = stringResource(R.string.onboarding_intro_body),
        style = MaterialTheme.typography.bodyLarge
    )
}

/**
 * The four parent colours as a row of swatches.
 *
 * Nothing is pre-selected. A wizard that opened with a colour already ticked would be claiming
 * an answer nobody gave, and `saveProfile` reads exactly that difference: untouched leaves the
 * stored colour alone, so a parent who set one in Settings and later re-runs the wizard does not
 * silently lose it.
 *
 * Each swatch carries its colour's name as a content description — a circle of colour is
 * unusable to anyone who cannot tell two of them apart, and a screen reader has nothing else to
 * announce.
 */
@Composable
private fun ParentColorSwatches(
    selected: ParentColorChoice?,
    onSelect: (ParentColorChoice) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ParentColorChoice.entries.forEach { choice ->
            val label = stringResource(choice.labelRes)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(choice.fill)
                    .border(
                        width = if (selected == choice) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                    .clickable(onClickLabel = label) { onSelect(choice) }
                    .semantics { contentDescription = label }
            )
        }
    }
}

/** The parent's own details — the only step with a field that blocks progress. */
@Composable
private fun ProfileStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(title = R.string.onboarding_profile_title)

    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::updateName,
        label = { Text(stringResource(R.string.profile_name_label)) },
        // Explained, not flagged: a field nobody has touched yet must not be rendered in error
        // colours on the first screen a new parent sees. The disabled Next carries the rule.
        supportingText = { Text(stringResource(R.string.onboarding_profile_name_required)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // Beside the name rather than on a step of its own: this is "who you are" — what you are
    // called and how you are marked — and a whole wizard step for four swatches would be a step
    // most people tap straight through.
    // Hidden with the Settings row until the palette reaches the screens.
    if (PARENT_COLOUR_PICKER_ENABLED) {
        SectionHeading(title = R.string.settings_parent_color)
        ParentColorSwatches(
            selected = state.parentColor,
            onSelect = viewModel::updateParentColor
        )
    }

    // Also "who you are", and for the same reason as the colour above: a whole wizard step for
    // one row of chips is a step most people tap straight through. It is here rather than in
    // Settings alone because the calendar draws holidays from the very first launch, and until
    // MON-13 it drew Czech ones for everybody.
    SectionHeading(title = R.string.country_label)
    CountryPicker(
        selected = state.country,
        onSelect = viewModel::updateCountry
    )

    DateOfBirthField(
        date = state.dateOfBirth,
        onDateChange = viewModel::updateDateOfBirth
    )

    OutlinedTextField(
        value = state.phone,
        onValueChange = viewModel::updatePhone,
        label = { Text(stringResource(R.string.profile_phone_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )

    SectionHeading(title = R.string.profile_allergies_label)
    AllergyEditor(
        allergies = state.allergies,
        onAdd = { viewModel.updateAllergies(state.allergies + it) },
        onRemove = { index ->
            viewModel.updateAllergies(state.allergies.toMutableList().apply { removeAt(index) })
        }
    )

    MedicalProfileEditor(
        profile = state.medicalProfile,
        onChange = viewModel::updateMedicalProfile,
        enabled = true
    )

    Footnote()
}

/**
 * The children this family is setting up — one form each, and a button for another.
 *
 * A repeatable list rather than the single form this used to be. The wizard asked how a family
 * works and then could not express one with two children: the second had to be found in the
 * child list afterwards, and the emergency contacts collected on the next step landed on
 * whichever child happened to be written first.
 *
 * **Nobody is asked how many children they have.** The step asks for names and the count falls
 * out of them, because a stored "one or several" would be a fact that goes stale the day a
 * second child arrives and would then need a settings toggle to correct. Everything downstream
 * reads `children.size` instead, so a family with one child sees the form they saw before —
 * no heading, no remove action, nothing new but the Add button that makes a second reachable.
 *
 * When the forms opened on the co-parent's records, the step says so: a form full of somebody
 * else's answers without a word about whose reads as a glitch rather than as help.
 *
 * Deliberately not the whole `AddEditChildInfoScreen`: medications, activities and school are
 * not first-run questions, and a wizard that asks for a teacher's email before the calendar has
 * been seen once will be abandoned. They stay one tap away in Settings.
 */
@Composable
private fun ChildStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(title = R.string.onboarding_child_title, body = R.string.onboarding_child_body)
    if (state.childrenFromCoParent) FromCoParentNote(state)

    state.children.forEachIndexed { index, draft ->
        ChildDraftForm(
            draft = draft,
            index = index,
            // One child is the case this wizard has always served, and it must look exactly as
            // it did: no heading, no remove action, just the form.
            showHeader = state.children.size > 1,
            viewModel = viewModel
        )
    }

    AddAnotherButton(label = R.string.onboarding_child_add, onClick = viewModel::addChild)

    Footnote()
}

/** "Entered by {co-parent}" — shown over forms that opened on their records. */
@Composable
private fun FromCoParentNote(state: OnboardingUiState) {
    val name = state.coParentName?.ifBlank { null }
        ?: stringResource(R.string.pairing_default_partner_name)
    StatusRow(text = stringResource(R.string.onboarding_from_coparent, name)) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** One child's form, with the heading and remove action that only a second child needs. */
@Composable
private fun ChildDraftForm(
    draft: ChildDraft,
    index: Int,
    showHeader: Boolean,
    viewModel: OnboardingViewModel
) {
    var confirmRemove by remember(draft.id) { mutableStateOf(false) }

    if (confirmRemove) {
        ConfirmationDialog(
            title = stringResource(R.string.childinfo_delete_title, draft.name),
            message = stringResource(R.string.childinfo_delete_message),
            confirmText = stringResource(R.string.childinfo_delete_confirm),
            dismissText = stringResource(R.string.childinfo_delete_cancel),
            isDestructive = true,
            onDismiss = { confirmRemove = false },
            onConfirm = {
                confirmRemove = false
                viewModel.removeChild(draft.id)
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeader) {
            DraftHeader(
                title = draft.name.ifBlank {
                    stringResource(R.string.onboarding_child_unnamed, index + 1)
                },
                removeDescription = stringResource(R.string.childinfo_delete_action),
                // Only a named child can have reached Room, so only that one is worth
                // interrupting for. A blank draft is removed outright.
                onRemove = { if (draft.isBlank) viewModel.removeChild(draft.id) else confirmRemove = true }
            )
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { viewModel.updateChildName(draft.id, it) },
            label = { Text(stringResource(R.string.childinfo_child_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        DateOfBirthField(
            date = draft.dateOfBirth,
            onDateChange = { viewModel.updateChildDateOfBirth(draft.id, it) }
        )

        SectionHeading(title = R.string.childinfo_section_allergies)
        AllergyEditor(
            allergies = draft.allergies,
            onAdd = { viewModel.updateChildAllergies(draft.id, draft.allergies + it) },
            onRemove = { position ->
                viewModel.updateChildAllergies(
                    draft.id,
                    draft.allergies.toMutableList().apply { removeAt(position) }
                )
            }
        )

        MedicalProfileEditor(
            profile = draft.medicalProfile,
            onChange = { viewModel.updateChildMedicalProfile(draft.id, it) },
            enabled = true
        )
    }
}

/** The name of a draft in a list of them, and the action that takes it back out. */
@Composable
private fun DraftHeader(title: String, removeDescription: String, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = removeDescription,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Appends one more blank form. Shown from the first draft on — that is how a second is reached. */
@Composable
private fun AddAnotherButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

/**
 * Children, pets, or both — the question that decides what the rest of the wizard asks.
 *
 * A multi-select rather than a choice of one: a separated family with a child and a dog has both,
 * and making them pick would hide a section they are already using. Nothing is hidden by silence
 * either — an account that never answered reads as "show everything", which is what every
 * upgrade is.
 *
 * Changeable afterwards from Settings → Family. Without that route, a family that gets a dog a
 * year later could never reach the pet records, which is design item 8 in reverse: not an
 * affordance promising a missing feature, but a built feature with no affordance.
 */
@Composable
private fun FamilyKindStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(title = R.string.onboarding_family_title, body = R.string.onboarding_family_body)

    SectionGroup {
        FamilyKind.entries.forEachIndexed { index, kind ->
            val selected = kind in state.caresFor
            SectionRow(
                icon = if (kind == FamilyKind.CHILDREN) Icons.Default.ChildCare else Icons.Default.Pets,
                title = stringResource(
                    if (kind == FamilyKind.CHILDREN) {
                        R.string.onboarding_family_children
                    } else {
                        R.string.onboarding_family_pets
                    }
                ),
                supporting = stringResource(
                    if (kind == FamilyKind.CHILDREN) {
                        R.string.onboarding_family_children_hint
                    } else {
                        R.string.onboarding_family_pets_hint
                    }
                ),
                onClick = {
                    viewModel.setCaresFor(
                        if (selected) state.caresFor - kind else state.caresFor + kind
                    )
                },
                trailing = {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { checked ->
                            viewModel.setCaresFor(
                                if (checked) state.caresFor + kind else state.caresFor - kind
                            )
                        }
                    )
                }
            )
            if (index != FamilyKind.entries.lastIndex) Divider()
        }
    }
}

/**
 * The pets — the pet equivalent of [ChildStep], repeatable on the same terms and as short.
 *
 * The pets screen has always been a genuine list; until this step became one, the wizard was
 * the only place in the app that insisted a family had exactly one pet.
 *
 * Deliberately not the whole pet record: vaccinations, feeding notes and the vet's number are
 * collected on the pet screen afterwards, the same trade [ChildStep] makes by leaving out school
 * and activities. A first run that asks for everything is a first run people abandon.
 */
@Composable
private fun PetStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(title = R.string.onboarding_pet_title, body = R.string.onboarding_pet_body)
    if (state.petsFromCoParent) FromCoParentNote(state)

    state.pets.forEachIndexed { index, draft ->
        PetDraftForm(
            draft = draft,
            index = index,
            showHeader = state.pets.size > 1,
            viewModel = viewModel
        )
    }

    AddAnotherButton(label = R.string.onboarding_pet_add, onClick = viewModel::addPet)

    Footnote()
}

/** One pet's form. Same anatomy as [ChildDraftForm], down to the confirmation. */
@Composable
private fun PetDraftForm(
    draft: PetDraft,
    index: Int,
    showHeader: Boolean,
    viewModel: OnboardingViewModel
) {
    var confirmRemove by remember(draft.id) { mutableStateOf(false) }

    if (confirmRemove) {
        ConfirmationDialog(
            title = stringResource(R.string.pet_delete_title, draft.name),
            message = stringResource(R.string.pet_delete_message),
            confirmText = stringResource(R.string.pet_delete_confirm),
            dismissText = stringResource(R.string.pet_delete_cancel),
            isDestructive = true,
            onDismiss = { confirmRemove = false },
            onConfirm = {
                confirmRemove = false
                viewModel.removePet(draft.id)
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeader) {
            DraftHeader(
                title = draft.name.ifBlank {
                    stringResource(R.string.onboarding_pet_unnamed, index + 1)
                },
                removeDescription = stringResource(R.string.pet_delete_confirm),
                onRemove = { if (draft.isBlank) viewModel.removePet(draft.id) else confirmRemove = true }
            )
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { viewModel.setPetName(draft.id, it) },
            label = { Text(stringResource(R.string.pet_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PetSpecies.entries.forEach { species ->
                FilterChip(
                    selected = draft.species == species,
                    onClick = { viewModel.setPetSpecies(draft.id, species) },
                    label = { Text(stringResource(species.labelRes())) }
                )
            }
        }
    }
}

/**
 * How a shared expense divides between the two parents.
 *
 * Easier to agree now than after a month of expenses to re-argue, which is why it is here and
 * not only in Settings. What the step says depends on the link: with no co-parent the answer
 * applies outright; linked with no agreement yet it becomes the pair's split; linked with one,
 * the slider opens on it and moving it is a proposal the co-parent confirms.
 *
 * The slider holds **this parent's** share, whichever slot they turn out to hold — that is the
 * number a person has an opinion about, and the ViewModel does the slot arithmetic on save.
 *
 * Skippable, like everything after the profile. Half each is what a family splits by until they
 * say otherwise, and that is what a skip — or an untouched Next — leaves in place.
 */
@Composable
private fun SplitStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val coParent = state.coParentName?.ifBlank { null }
        ?: stringResource(R.string.pairing_default_partner_name)
    StepHeading(title = R.string.onboarding_split_title)
    Text(
        text = when {
            state.splitAgreed -> stringResource(R.string.onboarding_split_agreed_body, coParent)
            state.coParent is CoParentLink.Linked ->
                stringResource(R.string.onboarding_split_body_linked, coParent)
            else -> stringResource(R.string.onboarding_split_body)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Named once there is a name: two bare numbers do not say which half is yours.
    Text(
        text = if (state.coParent is CoParentLink.Linked) {
            stringResource(
                R.string.onboarding_split_value_named,
                state.splitMyPercent,
                coParent,
                SPLIT_WHOLE_PERCENT - state.splitMyPercent
            )
        } else {
            stringResource(
                R.string.onboarding_split_value,
                state.splitMyPercent,
                SPLIT_WHOLE_PERCENT - state.splitMyPercent
            )
        },
        style = MaterialTheme.typography.headlineSmall
    )
    Slider(
        value = state.splitMyPercent.toFloat(),
        onValueChange = { viewModel.setSplitMyPercent(it.toInt()) },
        valueRange = 0f..SPLIT_WHOLE_PERCENT.toFloat(),
        steps = SPLIT_SLIDER_STEPS
    )

    Footnote()
}

/** A whole share, as a percent. */
private const val SPLIT_WHOLE_PERCENT = 100

/** Stops on the slider: every 5 %, which is nineteen stops between the two ends. */
private const val SPLIT_SLIDER_STEPS = 19

/**
 * The people who could collect the child or be called in an emergency.
 *
 * These are saved onto the **child's** record, not the parent's, because that is the document
 * both parents may write — so the co-parent can add to them. With no child named yet there is
 * nowhere honest to put a contact, so the step says so instead of collecting contacts it would
 * then drop.
 */
@Composable
private fun RelativesStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        title = R.string.onboarding_relatives_title,
        body = R.string.onboarding_relatives_body
    )

    val child = state.relativesChild
    if (child != null) {
        // The picker earns its place only when there is a choice to make. With one child every
        // contact belongs to them, and a chip row would be an affordance for nothing — design
        // item 8. With two it is the difference between filing a contact and mis-filing it,
        // which is what a single flat list of contacts used to do.
        if (state.namedChildren.size > 1) {
            SectionHeading(title = R.string.onboarding_relatives_whose)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.namedChildren.forEach { candidate ->
                    FilterChip(
                        selected = candidate.id == child.id,
                        onClick = { viewModel.selectRelativesChild(candidate.id) },
                        label = { Text(candidate.name) }
                    )
                }
            }
        }

        EmergencyContactEditor(
            contacts = child.relatives,
            onAdd = { viewModel.updateRelatives(child.id, child.relatives + it) },
            onEdit = { index, contact ->
                viewModel.updateRelatives(
                    child.id,
                    child.relatives.toMutableList().apply { this[index] = contact }
                )
            },
            onRemove = { index ->
                viewModel.updateRelatives(
                    child.id,
                    child.relatives.toMutableList().apply { removeAt(index) }
                )
            }
        )
        Footnote()
    } else {
        Text(
            text = stringResource(R.string.onboarding_relatives_needs_child),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The last step: the custody schedule, which hands off to the existing editor.
 *
 * With a schedule already on the phone — the pair's, brought across by the link, or one this
 * parent set up on an earlier run — the step names it and offers a look rather than a setup:
 * "set up the schedule" over a schedule that exists would invite the second parent to write over
 * what the first one agreed. No footnote: nothing is collected here, the screen it opens owns its
 * own copy.
 */
@Composable
private fun CustodyStep(state: OnboardingUiState, onOpen: () -> Unit) {
    val existing = state.custodyType
    if (existing == null) {
        StepHeading(title = R.string.onboarding_custody_title, body = R.string.onboarding_custody_body)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_custody_open))
        }
    } else {
        StepHeading(title = R.string.onboarding_custody_title)
        Text(
            text = stringResource(R.string.onboarding_custody_set_body, stringResource(existing.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_custody_review))
        }
    }
}

/** A step's title, and the sentence under it where one exists. */
@Composable
private fun StepHeading(@StringRes title: Int, @StringRes body: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall
        )
        if (body != null) {
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A label over an editor that brings no label of its own. */
@Composable
private fun SectionHeading(@StringRes title: Int) {
    SectionHeading(text = stringResource(title))
}

/** [SectionHeading] for a label that carries a name and so cannot be a bare resource. */
@Composable
private fun SectionHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/**
 * Item 4 of the brief, in one line, under every step that collects something.
 *
 * It must not say the data is encrypted (it is not) and must not say only the user can see it
 * (the co-parent can, by design). Either claim would be a promise the app does not keep.
 */
@Composable
private fun Footnote() {
    Text(
        text = stringResource(R.string.onboarding_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A date-of-birth button and its picker, shared by the parent step and the child step. */
@Composable
private fun DateOfBirthField(date: LocalDate?, onDateChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(date?.format(formatter) ?: stringResource(R.string.profile_dob_label))
    }
    if (showPicker) {
        DatePickerDialog(
            onDateSelected = {
                onDateChange(it.toLocalDate())
                showPicker = false
            },
            onDismiss = { showPicker = false },
            initialDate = date?.atStartOfDay()
        )
    }
}

/**
 * Back, Skip and Next.
 *
 * Skip is present on every step that may be left unanswered — which is every step except the
 * intro, which asks nothing, and the profile, whose name field the app cannot work without. On
 * the co-parent step it reads "Not now", because that is what it means there.
 */
@Composable
private fun OnboardingBottomBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isFirstStep) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.canSkip) {
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(
                            if (state.step == OnboardingStep.CoParent) {
                                R.string.onboarding_coparent_later
                            } else {
                                R.string.onboarding_skip
                            }
                        )
                    )
                }
            }
            Button(
                onClick = onNext,
                enabled = state.canAdvance && !state.isSaving,
                modifier = Modifier.height(48.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        stringResource(
                            if (state.isLastStep) {
                                R.string.onboarding_finish
                            } else {
                                R.string.onboarding_next
                            }
                        )
                    )
                }
            }
        }
    }
}
