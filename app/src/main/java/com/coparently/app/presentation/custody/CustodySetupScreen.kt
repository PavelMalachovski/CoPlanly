package com.coparently.app.presentation.custody

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.changerequests.AnswerByOffer
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.animations.sectionEnter
import com.coparently.app.presentation.common.animations.sectionExit
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.parentingplan.PlanReferenceCard
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyLargeEmphasized
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.presentation.theme.labelMediumEmphasized
import com.coparently.app.presentation.theme.titleMediumEmphasized
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Screen for setting up custody model/pattern.
 * Allows selection of predefined patterns or custom configuration.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustodySetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: CustodySetupViewModel = hiltViewModel()
) {
    val dims = dimensions()
    val uiState by viewModel.uiState.collectAsState()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    val currentModel by viewModel.currentModel.collectAsState()
    val children by viewModel.children.collectAsState()
    val childScope = uiState.childScope
    // The deadline for the co-parent's answer (phase 3a): asked only while a save would be a proposal.
    val proposes by viewModel.proposes.collectAsState()
    val answerByCap by viewModel.answerByCap.collectAsState()
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }
    // Scoped to one child (FAM-4), Back returns to the family schedule rather than leaving.
    BackHandler(enabled = childScope != null) { viewModel.editSchedule(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar
    val context = LocalContext.current
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error.asString(context))
            viewModel.clearError()
        }
    }

    // Navigate back on successful save — saying first when the save became a proposal. It used to
    // leave silently, so a parent whose choice was sent for approval believed it was the schedule,
    // and the two phones seemed to disagree (tester report, October 2026).
    val appMessages = LocalAppMessages.current
    val sentForApproval = stringResource(R.string.seasonal_sent_for_approval)
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            if (uiState.proposedForApproval) appMessages?.show(sentForApproval)
            onNavigateBack()
        }
    }

    // One part of the screen at a time, opened from the overview (UX audit H3). A child's own
    // schedule is edited in the pattern part, and an editor opened from the parenting plan goes
    // straight to the part its answer is about.
    val seasonalViewModel: SeasonalScheduleViewModel = hiltViewModel()
    val layersState by seasonalViewModel.layersState.collectAsState()
    var section by rememberCustodySection(
        openPattern = uiState.planReference != null,
        openSeasonal = layersState.planReference != null
    )
    val shown = if (childScope != null) CustodySection.PATTERN else section
    BackHandler(enabled = childScope == null && section != null) { section = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = custodyTitle(childScope, section),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        // Out of a child's schedule, then out of a part, then off the screen.
                        onClick = {
                            if (childScope != null || section != null) {
                                section = stepBack(childScope != null, section, viewModel)
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.custody_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Only the parts this button sends: the seasonal ones go on their own, and the overview
            // sends nothing (the old "the Save button below saves only the pattern above").
            if (shown == CustodySection.PATTERN || shown == CustodySection.WINDOWS) {
                StickyActionBar(
                    // A paired family's save is a proposal (second audit: "Save" read as done).
                    label = stringResource(
                        if (proposes) R.string.custody_send_for_approval else R.string.custody_save_button
                    ),
                    onClick = { viewModel.save(answerBy = answerBy.takeIf { proposes }) },
                    enabled = uiState.isValid,
                    busy = uiState.isLoading
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.paddingMedium)
        ) {
            childScope?.let { scope ->
                ChildScopeHeader(
                    childName = scope.name,
                    hasOwnSchedule = currentModel?.childOverrideFor(scope.childId) != null,
                    // The deadline block previews the form's own schedule, not this removal, so the removal
                    // asks for the default deadline (as deleting a seasonal layer does, ruling 9).
                    onFollowFamily = {
                        viewModel.save(followFamily = true, answerBy = AnswerByPick.DEFAULT.takeIf { proposes })
                    }
                )
            }
            CustodySectionBody(
                shown = shown,
                state = CustodyBodyState(
                    uiState = uiState,
                    parentNames = parentNames,
                    layers = layersState.layers,
                    children = children,
                    childrenWithOwnSchedule = currentModel?.childOverrides.orEmpty().map { it.childId }.toSet(),
                    proposes = proposes
                ),
                answerBy = AnswerByBlock(
                    offer = rememberAnswerByOffer(answerByCap),
                    selected = answerBy,
                    onSelect = { answerBy = it }
                ),
                viewModel = viewModel,
                seasonalViewModel = seasonalViewModel,
                onOpen = { section = it }
            )

            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom bar
        }
    }
}

/** What [CustodySectionBody] draws from: the form, the names, the layers and the children. */
internal data class CustodyBodyState(
    val uiState: CustodySetupUiState,
    val parentNames: ParentNames,
    val layers: List<SeasonalLayer>,
    val children: List<FamilyMember>,
    val childrenWithOwnSchedule: Set<String>,
    val proposes: Boolean
)

/** The deadline the co-parent is asked to answer by, while a save would be a proposal. */
internal data class AnswerByBlock(
    val offer: AnswerByOffer,
    val selected: AnswerByPick,
    val onSelect: (AnswerByPick) -> Unit
)

/**
 * The part of the custody screen on show: the overview, or one part of it (UX audit H3). The month
 * preview leads the overview and the pattern, where it shows what the choices below it draw.
 */
@Composable
@Suppress("LongParameterList") // the part, what it draws, the deadline, its two view models, the way out
private fun CustodySectionBody(
    shown: CustodySection?,
    state: CustodyBodyState,
    answerBy: AnswerByBlock,
    viewModel: CustodySetupViewModel,
    seasonalViewModel: SeasonalScheduleViewModel,
    onOpen: (CustodySection) -> Unit
) {
    val dims = dimensions()
    val uiState = state.uiState
    val parentNames = state.parentNames
    // Opened from an agreed parenting-plan answer (MON-21): quote it above the form the parent fills
    // in. Read-only — nothing below is filled from its words.
    if (shown == CustodySection.PATTERN && uiState.childScope == null) {
        uiState.planReference?.let { reference ->
            PlanReferenceCard(
                reference = reference,
                coParentName = parentNames.coParentLabel(),
                modifier = Modifier.padding(vertical = dims.paddingSmall)
            )
        }
    }
    if (shown == null || shown == CustodySection.PATTERN) {
        createTempModel(uiState)?.let { model ->
            SchedulePreviewMonth(
                model = model,
                parentNames = parentNames,
                description = custodyPreviewText(uiState, parentNames),
                modifier = Modifier.padding(vertical = dims.paddingMedium)
            )
        }
    }
    when (shown) {
        null -> CustodyOverview(
            uiState = uiState,
            layers = state.layers,
            childCount = state.children.count { it.ref is FamilyMemberRef.Child },
            childrenWithOwnSchedule = state.childrenWithOwnSchedule.size,
            coParentName = parentNames.coParentLabel().takeIf { state.proposes },
            onOpen = onOpen
        )
        CustodySection.PATTERN -> {
            CustodyPatternForm(
                uiState = uiState,
                parentNames = parentNames,
                actions = CustodyPatternActions.of(viewModel)
            )
            AnswerByIfProposing(state, answerBy)
        }
        // Contact windows (MON-6b) — for every pattern type: an afternoon with the parent who does
        // not have that day, on top of the whole days of the pattern.
        CustodySection.WINDOWS -> {
            ContactWindowsSection(
                uiState = uiState,
                parentNames = parentNames,
                onAdd = viewModel::addContactWindows,
                onRemove = viewModel::removeContactWindow
            )
            AnswerByIfProposing(state, answerBy)
        }
        // Seasonal schedules (MON-14) and holiday fairness (MON-20), each change sent on its own.
        CustodySection.SEASONAL -> SeasonalScheduleSection(seasonalViewModel)
        CustodySection.CHILDREN -> ChildSchedulesSection(
            children = state.children,
            childrenWithOwnSchedule = state.childrenWithOwnSchedule,
            onEdit = { viewModel.editSchedule(it) }
        )
    }
}

/** The deadline block, while a save would be a proposal (phase 3a). */
@Composable
private fun AnswerByIfProposing(state: CustodyBodyState, answerBy: AnswerByBlock) {
    if (!state.proposes) return
    AnswerByField(
        offer = answerBy.offer,
        selected = answerBy.selected,
        onSelect = answerBy.onSelect,
        coParentName = state.parentNames.coParentLabel(),
        modifier = Modifier.padding(vertical = dimensions().paddingMedium)
    )
}

/**
 * Which part of the custody screen is open, saved across a rotation; null is the overview. An editor
 * opened from the parenting plan goes straight to the part its answer is about.
 */
@Composable
private fun rememberCustodySection(openPattern: Boolean, openSeasonal: Boolean): MutableState<CustodySection?> {
    val section = rememberSaveable { mutableStateOf<CustodySection?>(null) }
    LaunchedEffect(openPattern) { if (openPattern) section.value = CustodySection.PATTERN }
    LaunchedEffect(openSeasonal) { if (openSeasonal) section.value = CustodySection.SEASONAL }
    return section
}

/** One step back: out of a child's own schedule first, else out of the open part. */
private fun stepBack(
    inChildScope: Boolean,
    section: CustodySection?,
    viewModel: CustodySetupViewModel
): CustodySection? {
    if (!inChildScope) return null
    viewModel.editSchedule(null)
    return section
}

/** The bar's title: the child's schedule, the open part, or the screen's own. */
@Composable
private fun custodyTitle(childScope: ChildScope?, section: CustodySection?): String =
    childScope?.let { stringResource(R.string.custody_child_scope_title, it.name) }
        ?: stringResource(section?.titleRes() ?: R.string.custody_title)

/** The top bar's title while [this] part is open. */
private fun CustodySection.titleRes(): Int = when (this) {
    CustodySection.PATTERN -> R.string.custody_overview_pattern
    CustodySection.WINDOWS -> R.string.custody_windows_title
    CustodySection.SEASONAL -> R.string.seasonal_title
    CustodySection.CHILDREN -> R.string.custody_child_schedules_title
}

/**
 * What a family pattern is: its type, the day it starts and who starts, plus the midweek contact
 * of every-other-weekend and the custom days. One form for the custody screen and onboarding's
 * schedule step (new-user review, October 2026), so the two can never describe a fortnight
 * differently. Contact windows, a child's own schedule and the answer-by stay on the custody
 * screen.
 *
 * @param uiState The form's state
 * @param parentNames Names each parent
 * @param actions What each choice does
 * @param showMidweek Whether every-other-weekend offers its midweek contact; onboarding keeps the
 *   step short and leaves it to the custody screen
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod") // the form's sections in order, moved here whole from the custody screen
internal fun CustodyPatternForm(
    uiState: CustodySetupUiState,
    parentNames: ParentNames,
    actions: CustodyPatternActions,
    modifier: Modifier = Modifier,
    showMidweek: Boolean = true
) {
    val dims = dimensions()
    var showDatePicker by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        // Model type selection
        Text(
            text = stringResource(R.string.custody_select_schedule_type),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = dims.paddingSmall)
        )

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
        ) {
            CustodyModelType.entries.forEach { modelType ->
                ModelTypeCard(
                    modelType = modelType,
                    isSelected = uiState.selectedModelType == modelType,
                    onClick = { actions.selectType(modelType) }
                )
            }
        }

        Spacer(modifier = Modifier.height(dims.paddingMedium))

        // Start date picker
        Text(
            text = stringResource(R.string.custody_pattern_start_date),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = dims.paddingSmall)
        )
        Text(
            text = stringResource(R.string.custody_start_date_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.paddingSmall)
                .clickable { showDatePicker = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dims.paddingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(dims.paddingMedium))
                Text(
                    // Locale-aware, not a hardcoded US pattern — day and month names
                    // already follow the app language, their order must too.
                    text = uiState.startDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(dims.paddingMedium))

        // Who starts first (for non-custom models)
        if (uiState.selectedModelType != CustodyModelType.CUSTOM) {
            Text(
                text = stringResource(rolesQuestionFor(uiState.selectedModelType)),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(vertical = dims.paddingSmall)
            )

            // Two names, one chosen — a switch said only "on" for one of them (second audit).
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dims.paddingSmall)
            ) {
                listOf(true, false).forEachIndexed { index, momFirst ->
                    val slot = if (momFirst) "mom" else "dad"
                    SegmentedButton(
                        selected = uiState.momFirst == momFirst,
                        onClick = { actions.setMomFirst(momFirst) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(ParentColors.fill(slot), CircleShape)
                            )
                        },
                        label = { Text(parentNames.labelFor(slot)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.paddingMedium))
        }

        // Midweek contact — only `výhradní péče se stykem` has one.
        AnimatedVisibility(
            visible = showMidweek && uiState.selectedModelType == CustodyModelType.EVERY_OTHER_WEEKEND,
            enter = sectionEnter(),
            exit = sectionExit()
        ) {
            MidweekContactSection(
                uiState = uiState,
                onEnabledChange = actions.setMidweekEnabled,
                onDayChange = actions.setMidweekDay,
                onEveryWeekChange = actions.setMidweekEveryWeek
            )
        }

        // Custom pattern editor
        AnimatedVisibility(
            visible = uiState.selectedModelType == CustodyModelType.CUSTOM,
            enter = sectionEnter(),
            exit = sectionExit()
        ) {
            Column {
                Text(
                    text = stringResource(R.string.custody_custom_pattern),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(vertical = dims.paddingSmall)
                )
                Text(
                    text = stringResource(
                        R.string.custody_custom_pattern_hint,
                        parentNames.labelFor("mom"),
                        parentNames.labelFor("dad")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(dims.paddingSmall))

                // Pattern days grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    repeat(uiState.customPatternDays) { dayIndex ->
                        val isMomDay = uiState.customMomDays.contains(dayIndex)
                        val weekNumber = dayIndex / 7 + 1
                        val dayInWeek = dayIndex % 7 + 1

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    ParentColors.container(
                                        if (isMomDay) "mom" else "dad",
                                        alpha = 0.3f
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    color = ParentColors.fill(if (isMomDay) "mom" else "dad"),
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .clickable { actions.toggleCustomDay(dayIndex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // `MomPink`/`DadBlue` are fill-only — `Color.kt` says so in
                                // as many words, and they measure 3.7:1-4.6:1, below AA.
                                // Here they were foreground text *over a 30% wash of the
                                // same hue*, at 8sp, on the one screen a Czech parent
                                // cannot skip. `ParentColors.text()` picks the theme-aware
                                // partner; the type scale's own smallest size is 11sp, so
                                // the override goes rather than being nudged.
                                Text(
                                    text = stringResource(R.string.custody_week_abbrev, weekNumber),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParentColors.text(if (isMomDay) "mom" else "dad")
                                )
                                Text(
                                    text = stringResource(R.string.custody_day_abbrev, dayInWeek),
                                    style = MaterialTheme.typography.labelMediumEmphasized,
                                    color = ParentColors.text(if (isMomDay) "mom" else "dad")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dims.paddingSmall))

                // Quick selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                ) {
                    TextButton(
                        onClick = {
                            actions.assignCustomWeek(0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                R.string.custody_week_to,
                                1,
                                parentNames.labelFor("mom")
                            )
                        )
                    }
                    TextButton(
                        onClick = {
                            actions.assignCustomWeek(1)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                R.string.custody_week_to,
                                2,
                                parentNames.labelFor("mom")
                            )
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initialDate = uiState.startDate,
            confirmLabel = stringResource(R.string.custody_ok),
            dismissLabel = stringResource(R.string.custody_cancel),
            onConfirm = actions.setStartDate,
            onDismiss = { showDatePicker = false }
        )
    }
}

/**
 * The choices [CustodyPatternForm] makes, as callbacks, so the form holds no ViewModel.
 */
internal data class CustodyPatternActions(
    val selectType: (CustodyModelType) -> Unit,
    val setStartDate: (LocalDate) -> Unit,
    val setMomFirst: (Boolean) -> Unit,
    val setMidweekEnabled: (Boolean) -> Unit,
    val setMidweekDay: (DayOfWeek) -> Unit,
    val setMidweekEveryWeek: (Boolean) -> Unit,
    val toggleCustomDay: (Int) -> Unit,
    val assignCustomWeek: (Int) -> Unit
) {
    companion object {
        /** The form's choices, made on [viewModel]. */
        fun of(viewModel: CustodySetupViewModel) = CustodyPatternActions(
            selectType = viewModel::selectModelType,
            setStartDate = viewModel::setStartDate,
            setMomFirst = viewModel::setMomFirst,
            setMidweekEnabled = viewModel::setMidweekEnabled,
            setMidweekDay = viewModel::setMidweekDay,
            setMidweekEveryWeek = viewModel::setMidweekEveryWeek,
            toggleCustomDay = viewModel::toggleCustomMomDay,
            assignCustomWeek = viewModel::assignCustomWeekToMom
        )
    }
}

/**
 * Card for displaying a custody model type option.
 */
@Composable
private fun ModelTypeCard(
    modelType: CustodyModelType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dims = dimensions()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null // Handled by card
            )
            Spacer(modifier = Modifier.width(dims.paddingSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelTypeLabel(modelType),
                    style = if (isSelected) {
                        MaterialTheme.typography.bodyLargeEmphasized
                    } else {
                        MaterialTheme.typography.bodyLarge
                    }
                )
                Text(
                    text = getModelTypeDescription(modelType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The name of a custody model, in the reader's language.
 *
 * `CustodyModelType.displayName` — which this screen used to render — is an English literal
 * on the enum, so the custody picker showed "Week On / Week Off" to every user in every
 * locale. The translations already existed in all five `custody_strings.xml` files and simply
 * were not wired up, which made this the untranslated screen on the one flow a Czech parent
 * cannot skip: `střídavá péče` is the arrangement most of them are here to describe.
 *
 * `displayName` is left on the enum for logs and debugging, where an English constant is what
 * you want; it must not reach the UI.
 */
/**
 * The midweek-contact controls of `výhradní péče se stykem`.
 *
 * Three decisions, in the order a parent makes them: is there one, which weekday, and is it
 * every week or only the week without the contact weekend.
 *
 * The overnight warning is not decoration. This model assigns a date to exactly one parent, so
 * a midweek "afternoon" — what most Czech orders actually say — becomes a whole day here,
 * handover to handover. Saying it plainly is what keeps the schedule from quietly claiming an
 * overnight the court did not give.
 *
 * @param uiState The current form state
 * @param onEnabledChange Turns the midweek day on or off
 * @param onDayChange Picks the weekday
 * @param onEveryWeekChange Every week, or only the week without the contact weekend
 */
@Composable
private fun MidweekContactSection(
    uiState: CustodySetupUiState,
    onEnabledChange: (Boolean) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onEveryWeekChange: (Boolean) -> Unit
) {
    val dims = dimensions()
    Column {
        Text(
            text = stringResource(R.string.custody_midweek_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = dims.paddingSmall)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.paddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.custody_midweek_enable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.midweekEnabled,
                onCheckedChange = onEnabledChange
            )
        }

        Text(
            text = stringResource(R.string.custody_midweek_overnight_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.midweekEnabled) {
            Spacer(modifier = Modifier.height(dims.paddingSmall))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall)
            ) {
                WEEKDAYS.forEach { day ->
                    FilterChip(
                        selected = uiState.midweekDay == day,
                        onClick = { onDayChange(day) },
                        label = {
                            Text(
                                // The device (or app) language decides the name, never a
                                // hardcoded array — the Localization rule in CLAUDE.md.
                                day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dims.paddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        if (uiState.midweekEveryWeek) {
                            R.string.custody_midweek_every_week
                        } else {
                            R.string.custody_midweek_off_week_only
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.midweekEveryWeek,
                    onCheckedChange = onEveryWeekChange
                )
            }
        }

        Spacer(modifier = Modifier.height(dims.paddingMedium))
    }
}

/** Monday to Friday, the only days a midweek contact may fall on. */
private val WEEKDAYS = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

@Composable
private fun modelTypeLabel(modelType: CustodyModelType): String = stringResource(modelType.labelRes())

/**
 * Which question the two-name choice is actually asking, for the selected pattern.
 *
 * Three of the four patterns alternate blocks of time, so "who starts first" is the whole of
 * it. [CustodyModelType.EVERY_OTHER_WEEKEND] does not alternate: one parent has the child and
 * the other has every second weekend, and asking a parent who "starts" invites them to answer
 * about the first weekend — which sets the choice backwards and hands over the school days.
 * A schedule that is wrong in that direction is exactly the failure this app exists to prevent,
 * so the question changes with the pattern rather than the parent being expected to translate it.
 *
 * `CUSTOM` never reaches here: the choice is not rendered for it.
 */
@StringRes
private fun rolesQuestionFor(modelType: CustodyModelType): Int = when (modelType) {
    CustodyModelType.EVERY_OTHER_WEEKEND -> R.string.custody_who_is_resident
    else -> R.string.custody_who_starts_first
}

/**
 * A brief description of each model type, in the reader's language. See [modelTypeLabel] —
 * these four strings were hardcoded in English beside translations that already existed.
 */
@Composable
private fun getModelTypeDescription(modelType: CustodyModelType): String = stringResource(
    when (modelType) {
        CustodyModelType.WEEK_ON_WEEK_OFF -> R.string.custody_model_week_on_week_off_desc
        CustodyModelType.EVERY_OTHER_WEEKEND -> R.string.custody_model_every_other_weekend_desc
        CustodyModelType.TWO_TWO_THREE -> R.string.custody_model_two_two_three_desc
        CustodyModelType.THREE_FOUR_FOUR_THREE -> R.string.custody_model_three_four_four_three_desc
        CustodyModelType.CUSTOM -> R.string.custody_model_custom_desc
    }
)

/**
 * Creates a temporary CustodyModel from the UI state for preview purposes.
 */
internal fun createTempModel(state: CustodySetupUiState): com.coparently.app.domain.model.CustodyModel? {
    // A custom pattern with no slot-1 day yet has nothing to preview for the family schedule; a
    // child's own schedule (FAM-4) may mean exactly that — every day with the other parent.
    val nothingToDraw = state.selectedModelType == CustodyModelType.CUSTOM && state.customMomDays.isEmpty()
    return if (nothingToDraw && state.childScope == null) {
        null
    } else {
        state.toPatternModel("preview")
    }
}

/**
 * The Preview card's one-sentence description of the selected pattern, with both parents named.
 *
 * Formatted here rather than in the ViewModel for the usual reason: a ViewModel has no `Context`
 * and must not acquire one to resolve a string. [CustodySetupUiState] supplies the two slots and
 * this turns them into names, the same shape the "starts first" row two cards up already uses.
 *
 * @param uiState The selected model and its parameters
 * @param parentNames Resolves a slot to that parent's name
 */
@Composable
internal fun custodyPreviewText(uiState: CustodySetupUiState, parentNames: ParentNames): String {
    val first = parentNames.labelFor(uiState.firstSlot)
    val second = parentNames.labelFor(uiState.secondSlot)
    return when (uiState.selectedModelType) {
        // Dated, because "first week" read as the first week drawn, which may be the other
        // parent's (second audit).
        CustodyModelType.WEEK_ON_WEEK_OFF -> stringResource(
            R.string.custody_preview_week_on_week_off,
            first,
            uiState.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        )
        CustodyModelType.EVERY_OTHER_WEEKEND -> {
            val midweek = uiState.midweek
            if (midweek == null) {
                stringResource(R.string.custody_preview_every_other_weekend, first, second)
            } else {
                stringResource(
                    if (midweek.everyWeek) {
                        R.string.custody_preview_every_other_weekend_midweek
                    } else {
                        R.string.custody_preview_every_other_weekend_midweek_off_week
                    },
                    first,
                    second,
                    midweek.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                )
            }
        }
        CustodyModelType.TWO_TWO_THREE ->
            stringResource(R.string.custody_preview_two_two_three, first, second)
        CustodyModelType.THREE_FOUR_FOUR_THREE ->
            stringResource(R.string.custody_preview_three_four_four_three, first, second)
        CustodyModelType.CUSTOM -> stringResource(
            R.string.custody_preview_custom,
            uiState.customMomDays.size,
            uiState.customPatternDays,
            parentNames.labelFor("mom")
        )
    }
}
