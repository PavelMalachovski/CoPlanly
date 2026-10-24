package com.coparently.app.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.presentation.common.CountryPicker
import com.coparently.app.presentation.common.STACK_CONTROLS_FONT_SCALE
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.hasNotificationPermission
import com.coparently.app.presentation.common.labelRes
import com.coparently.app.presentation.common.rememberNotificationPermissionRequester
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.custody.CustodyPatternActions
import com.coparently.app.presentation.custody.CustodyPatternForm
import com.coparently.app.presentation.custody.CustodySetupUiState
import com.coparently.app.presentation.custody.CustodySetupViewModel
import com.coparently.app.presentation.custody.OnboardingScheduleMode
import com.coparently.app.presentation.custody.SchedulePreviewMonth
import com.coparently.app.presentation.custody.createTempModel
import com.coparently.app.presentation.custody.custodyPreviewText
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing

/**
 * The first-run wizard: the co-parent link, notifications, the parent's name and country, and the
 * custody schedule with the month it draws (new-user review, October 2026 — four steps where there
 * were nine, so the schedule a parent installs the app for is on screen within a minute).
 *
 * **The link comes first**, so that everything after it can open on what the co-parent has
 * already set up — see [OnboardingStep]. The schedule step embeds the custody form itself
 * ([CustodyPatternForm]) with a [SchedulePreviewMonth] under it; when the pair already shares a
 * schedule it shows that one to confirm instead, and its Finish never proposes a change.
 *
 * Stateless: everything lives in [OnboardingViewModel] and, for the schedule, in
 * [CustodySetupViewModel]; this renders and forwards.
 *
 * @param onFinished Leaves the wizard once onboarding has been recorded as complete
 * @param onOpenCustodySetup Opens the full custody editor, where a change to a shared schedule is
 *   proposed with its deadline
 * @param onOpenPairing Opens the existing pairing screen — on code entry when the argument is
 *   true, on this account's own code otherwise
 * @param viewModel Wizard state and mutations
 * @param scheduleViewModel The schedule step's form and its never-proposing save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenCustodySetup: () -> Unit,
    onOpenPairing: (enterCode: Boolean) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
    scheduleViewModel: CustodySetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val schedule by scheduleViewModel.uiState.collectAsState()
    val scheduleMode by scheduleViewModel.onboardingMode.collectAsState()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished()
    }
    // Asked when the step is reached, not before: the pair's document is likelier to be on the
    // server by then, and a parent who never gets there causes no read.
    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.Custody) scheduleViewModel.checkOnboardingMode()
    }

    // The wizard is the start destination, so an unhandled back press would close the app from
    // step 2. Inside the wizard, back means "the previous question".
    BackHandler(enabled = !uiState.isFirstStep) { viewModel.back() }

    // On the schedule step the primary button saves the form (which only ever activates) or, over
    // a schedule the pair already shares, confirms it and writes nothing.
    val onCustodyStep = uiState.step == OnboardingStep.Custody
    val primary = when {
        !onCustodyStep -> PrimaryAction(R.string.onboarding_next, uiState.canAdvance, viewModel::next)
        scheduleMode is OnboardingScheduleMode.Existing ->
            PrimaryAction(R.string.onboarding_custody_looks_right, enabled = true, onClick = viewModel::finish)
        scheduleMode is OnboardingScheduleMode.Form -> PrimaryAction(
            R.string.onboarding_finish,
            enabled = schedule.isValid && !schedule.isLoading,
            onClick = { scheduleViewModel.saveFirstSchedule(onDone = viewModel::finish) }
        )
        else -> PrimaryAction(R.string.onboarding_finish, enabled = false, onClick = {})
    }

    Scaffold(
        topBar = { OnboardingTopBar(state = uiState) },
        bottomBar = {
            OnboardingBottomBar(
                state = uiState,
                primary = primary,
                onBack = viewModel::back,
                onSkip = viewModel::skip
            )
        }
    ) { padding ->
        OnboardingBody(
            state = uiState,
            viewModel = viewModel,
            schedule = ScheduleStepState(schedule, scheduleMode, scheduleViewModel),
            onOpenCustodySetup = onOpenCustodySetup,
            onOpenPairing = onOpenPairing,
            padding = padding
        )
    }
}

/**
 * The bottom bar's primary button on this step: its label, whether it may be pressed, and what it
 * does.
 */
private data class PrimaryAction(@StringRes val label: Int, val enabled: Boolean, val onClick: () -> Unit)

/** What the schedule step renders from, and the ViewModel its form writes to. */
private class ScheduleStepState(
    val form: CustodySetupUiState,
    val mode: OnboardingScheduleMode,
    val viewModel: CustodySetupViewModel
)

/** Title and how far along the wizard is, so no step feels open-ended. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTopBar(state: OnboardingUiState) {
    Column {
        TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) })
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
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S)
        )
    }
}

/** The scrolling body, switching on the current step. */
@Composable
@Suppress("LongParameterList") // the wizard's state, the schedule step's, its two hand-offs and the insets
private fun OnboardingBody(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    schedule: ScheduleStepState,
    onOpenCustodySetup: () -> Unit,
    onOpenPairing: (enterCode: Boolean) -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.XL)
    ) {
        when (state.step) {
            OnboardingStep.CoParent -> CoParentStep(state, onOpenPairing)
            OnboardingStep.Notifications -> NotificationsStep(onAnswered = viewModel::next)
            OnboardingStep.Profile -> ProfileStep(state, viewModel)
            OnboardingStep.Custody -> CustodyStep(state, schedule, onOpenCustodySetup)
        }
    }
}

/**
 * The first step: link with the co-parent, and see what the link brought back.
 *
 * Unlinked, it offers both halves of pairing as two buttons — "I have their code" and "Invite" —
 * because the second parent to install the app is holding a code. Linked, it names the co-parent
 * and reports the fetch: running, found (as rows, one per kind of record, so no locale has to
 * pluralise "2 children and 1 pet"), or nothing yet — which is a real answer, not an error.
 */
@Composable
private fun CoParentStep(
    state: OnboardingUiState,
    onOpenPairing: (enterCode: Boolean) -> Unit
) {
    when (val link = state.coParent) {
        is CoParentLink.Linked -> LinkedCoParent(
            name = link.name.ifBlank { stringResource(R.string.pairing_default_partner_name) },
            fetch = state.fetch
        )
        CoParentLink.None, CoParentLink.Unknown -> {
            StepHeading(title = R.string.onboarding_coparent_title, body = R.string.onboarding_coparent_body)
            Button(onClick = { onOpenPairing(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizes.Small)
                )
                Spacer(modifier = Modifier.width(Spacing.S))
                Text(stringResource(R.string.onboarding_coparent_enter_code))
            }
            OutlinedButton(onClick = { onOpenPairing(false) }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizes.Small)
                )
                Spacer(modifier = Modifier.width(Spacing.S))
                Text(stringResource(R.string.onboarding_coparent_invite))
            }
        }
    }
}

/**
 * Asks for the permission to post notifications (tester feedback, 21 October 2026): the button
 * opens the system's dialog, and either answer moves on. Already granted — or an Android older
 * than 13, which needs no permission — it says so and only Next is left to press.
 *
 * @param onAnswered Moves to the next step once the system dialog has been answered.
 */
@Composable
private fun NotificationsStep(onAnswered: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val requester = rememberNotificationPermissionRequester(onDenied = onAnswered)
    StepHeading(title = R.string.onboarding_notifications_title, body = R.string.onboarding_notifications_body)
    if (granted) {
        StatusRow(text = stringResource(R.string.onboarding_notifications_on)) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Button(
            onClick = {
                requester.request {
                    granted = true
                    onAnswered()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
            Spacer(modifier = Modifier.width(Spacing.S))
            Text(stringResource(R.string.onboarding_notifications_allow))
        }
        Text(
            text = stringResource(R.string.onboarding_notifications_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The linked half of [CoParentStep]: who, and what has come across so far. */
@Composable
private fun LinkedCoParent(name: String, fetch: CoParentFetch) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
        CoParentFetch.Idle, CoParentFetch.Running -> {
            StatusRow(text = stringResource(R.string.onboarding_coparent_fetching, name)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            // A spinner alone reads as "wait here", and on a slow network it can spin for a
            // while (D-24). Nothing on this step needs the fetch to finish: the later steps keep
            // listening and fill in what arrives.
            Text(
                text = stringResource(R.string.onboarding_coparent_fetching_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is CoParentFetch.Done -> if (fetch.found.isEmpty) {
            StatusRow(text = stringResource(R.string.onboarding_coparent_nothing_yet, name)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSizes.Small)
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        ParentColorChoice.ORIGINAL.forEach { choice ->
            val label = stringResource(choice.labelRes)
            Box(
                modifier = Modifier
                    .size(LayoutConstants.MIN_TOUCH_TARGET)
                    .clip(CircleShape)
                    .background(ParentColors.choiceFill(choice))
                    // Only the chosen swatch gets a ring: a zero-width border still draws a
                    // hairline, which ringed every swatch as if all four were picked.
                    .then(
                        if (selected == choice) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = selected == choice,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice) }
                    )
                    .semantics { contentDescription = label }
            )
        }
    }
}

/**
 * Who the parent is: their name — the only field that blocks progress — their colour and their
 * country. The date of birth and the phone left the wizard (new-user review); they stay on My
 * details.
 */
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
    // called and how you are marked.
    SectionHeading(title = R.string.settings_parent_color)
    ParentColorSwatches(
        selected = state.parentColor,
        onSelect = viewModel::updateParentColor
    )

    // The calendar draws holidays from the very first launch, so the country is asked here; it
    // opens on the phone's own when this app has its holidays.
    SectionHeading(title = R.string.country_label)
    CountryPicker(
        selected = state.country,
        onSelect = viewModel::updateCountry,
        selectedRegion = state.region,
        onSelectRegion = viewModel::updateRegion
    )
}

/**
 * The custody schedule, chosen inside the wizard with the month it draws under it.
 *
 * Three states, from [CustodySetupViewModel.onboardingMode]: checking whether the pair already
 * shares a schedule; the form, with its preview redrawn on every choice; or the shared schedule to
 * confirm, with a way to the full editor where a change is proposed and answered. The step's
 * Finish never proposes (see `CustodyModelRepository.activateFirstSchedule`). Midweek contact,
 * contact time, a child's own schedule and seasonal layers stay on the full editor.
 */
@Composable
private fun CustodyStep(state: OnboardingUiState, schedule: ScheduleStepState, onOpenEditor: () -> Unit) {
    StepHeading(title = R.string.onboarding_custody_title)
    val parentNames = rememberParentNames(schedule.viewModel.parents.collectAsState().value)
    when (val mode = schedule.mode) {
        OnboardingScheduleMode.Checking -> StatusRow(text = stringResource(R.string.onboarding_custody_checking)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        OnboardingScheduleMode.Form -> {
            Text(
                text = stringResource(R.string.onboarding_custody_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CustodyPatternForm(
                uiState = schedule.form,
                parentNames = parentNames,
                actions = CustodyPatternActions.of(schedule.viewModel),
                showMidweek = false
            )
            createTempModel(schedule.form)?.let { model ->
                SchedulePreviewMonth(
                    model = model,
                    parentNames = parentNames,
                    description = custodyPreviewText(schedule.form, parentNames)
                )
            }
            schedule.form.error?.let { error ->
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is OnboardingScheduleMode.Existing -> {
            Text(
                text = stringResource(
                    R.string.onboarding_custody_existing_body,
                    state.coParentName?.ifBlank { null } ?: stringResource(R.string.pairing_default_partner_name)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SchedulePreviewMonth(model = mode.model, parentNames = parentNames, description = null)
            TextButton(onClick = onOpenEditor) {
                Text(stringResource(R.string.onboarding_custody_propose_change))
            }
        }
    }
}

/** A step's title, and the sentence under it where one exists. */
@Composable
private fun StepHeading(@StringRes title: Int, @StringRes body: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
 * Back, Skip and the primary button.
 *
 * Skip is present on every step that may be left unanswered — every step except the profile,
 * whose name field the app cannot work without. On the co-parent step it reads "Not now", because
 * that is what it means there.
 *
 * One row at the default size. From [STACK_CONTROLS_FONT_SCALE] the primary button takes a row
 * of its own at full width, with Back and Skip under it: sharing one row at 150 %, German broke
 * the primary label inside the word ("Weit|er", "Ferti|g").
 */
@Composable
private fun OnboardingBottomBar(
    state: OnboardingUiState,
    primary: PrimaryAction,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val back: @Composable () -> Unit = {
        if (!state.isFirstStep) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
    }
    val skip: @Composable () -> Unit = {
        if (state.canSkip) OnboardingSkipButton(state.step, onSkip)
    }
    val next: @Composable (Modifier) -> Unit = { modifier ->
        OnboardingPrimaryButton(primary, saving = state.isSaving, modifier = modifier)
    }
    Surface(shadowElevation = 8.dp) {
        if (stacksAtLargeFont()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                next(Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth()) {
                    back()
                    Spacer(modifier = Modifier.weight(1f))
                    skip()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.L),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                back()
                Spacer(modifier = Modifier.weight(1f))
                skip()
                next(Modifier)
            }
        }
    }
}

/** Skip — "Not now" on the co-parent step, because that is what it means there. */
@Composable
private fun OnboardingSkipButton(step: OnboardingStep, onSkip: () -> Unit) {
    TextButton(onClick = onSkip) {
        Text(
            stringResource(
                if (step == OnboardingStep.CoParent) {
                    R.string.onboarding_coparent_later
                } else {
                    R.string.onboarding_skip
                }
            )
        )
    }
}

/** The step's primary button; a spinner while the wizard saves. */
@Composable
private fun OnboardingPrimaryButton(primary: PrimaryAction, saving: Boolean, modifier: Modifier) {
    Button(
        onClick = primary.onClick,
        enabled = primary.enabled && !saving,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(primary.label))
        }
    }
}
