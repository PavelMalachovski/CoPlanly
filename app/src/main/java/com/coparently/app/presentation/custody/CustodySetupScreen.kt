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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.animations.sectionEnter
import com.coparently.app.presentation.common.animations.sectionExit
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.parentingplan.PlanReferenceCard
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyLargeEmphasized
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.presentation.theme.labelMediumEmphasized
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.presentation.theme.titleSmallEmphasized
import java.time.DayOfWeek
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
    // Scoped to one child (FAM-4), Back returns to the family schedule rather than leaving.
    BackHandler(enabled = childScope != null) { viewModel.editSchedule(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    // Show error snackbar
    val context = LocalContext.current
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error.asString(context))
            viewModel.clearError()
        }
    }

    // Navigate back on successful save
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = childScope?.let { stringResource(R.string.custody_child_scope_title, it.name) }
                            ?: stringResource(R.string.custody_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (childScope != null) viewModel.editSchedule(null) else onNavigateBack() }
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
            StickyActionBar(
                label = stringResource(R.string.custody_save_button),
                onClick = { viewModel.save() },
                enabled = uiState.isValid,
                busy = uiState.isLoading
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.paddingMedium)
        ) {
            // Opened from an agreed parenting-plan answer (MON-21): quote it above the form the
            // parent fills in. Read-only — nothing below is filled from its words.
            childScope?.let { scope ->
                ChildScopeHeader(
                    childName = scope.name,
                    hasOwnSchedule = currentModel?.childOverrideFor(scope.childId) != null,
                    onFollowFamily = { viewModel.save(followFamily = true) }
                )
            }
            uiState.planReference?.takeIf { childScope == null }?.let { reference ->
                PlanReferenceCard(
                    reference = reference,
                    coParentName = parentNames.coParentLabel(),
                    modifier = Modifier.padding(vertical = dims.paddingSmall)
                )
            }

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
                        onClick = { viewModel.selectModelType(modelType) }
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

            // Mom first toggle (for non-custom models)
            if (uiState.selectedModelType != CustodyModelType.CUSTOM) {
                Text(
                    text = stringResource(rolesQuestionFor(uiState.selectedModelType)),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    ParentColors.fill(if (uiState.momFirst) "mom" else "dad"),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(Spacing.S))
                        Text(
                            text = stringResource(
                                rolesAnswerFor(uiState.selectedModelType),
                                parentNames.labelFor(if (uiState.momFirst) "mom" else "dad")
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Switch(
                        checked = uiState.momFirst,
                        onCheckedChange = { viewModel.setMomFirst(it) }
                    )
                }

                Spacer(modifier = Modifier.height(dims.paddingMedium))
            }

            // Midweek contact — only `výhradní péče se stykem` has one.
            AnimatedVisibility(
                visible = uiState.selectedModelType == CustodyModelType.EVERY_OTHER_WEEKEND,
                enter = sectionEnter(),
                exit = sectionExit()
            ) {
                MidweekContactSection(
                    uiState = uiState,
                    onEnabledChange = viewModel::setMidweekEnabled,
                    onDayChange = viewModel::setMidweekDay,
                    onEveryWeekChange = viewModel::setMidweekEveryWeek
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
                                    .clickable { viewModel.toggleCustomMomDay(dayIndex) },
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
                                viewModel.assignCustomWeekToMom(0)
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
                                viewModel.assignCustomWeekToMom(1)
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

            // Contact windows (MON-6b) — for every pattern type: an afternoon with the parent
            // who does not have that day, on top of the whole days chosen above.
            ContactWindowsSection(
                uiState = uiState,
                parentNames = parentNames,
                onAdd = viewModel::addContactWindows,
                onRemove = viewModel::removeContactWindow
            )

            // Preview section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dims.paddingMedium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(dims.paddingMedium)
                ) {
                    Text(
                        text = stringResource(R.string.custody_preview),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(dims.paddingSmall))
                    Text(
                        text = custodyPreviewText(uiState, parentNames),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(dims.paddingSmall))

                    // Visual preview - show next 14 days
                    Text(
                        text = stringResource(R.string.custody_next_14_days),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.XS))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XXS)
                    ) {
                        val tempModel = createTempModel(uiState)
                        repeat(14) { dayOffset ->
                            val date = uiState.startDate.plusDays(dayOffset.toLong())
                            val custody = tempModel?.getCustodyFor(date)
                            // The deep chip tone with a label colour picked for contrast — the
                            // week band's recipe. White on the full hue at 70 % failed AA for
                            // pink, and for any lighter colour a parent picks.
                            val color = when (custody) {
                                "mom" -> ParentColors.chipFill("mom")
                                "dad" -> ParentColors.chipFill("dad")
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    // `heightIn`, not `height`: a fixed box clips its own
                                    // label as soon as the reader's font scale grows.
                                    .heightIn(min = 24.dp)
                                    .background(color, CoPlanlyCorners.Tag),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParentColors.onFill(color),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dims.paddingSmall),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(ParentColors.chipFill("mom"), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(Spacing.XS))
                        Text(
                            text = parentNames.labelFor("mom"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(Spacing.L))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(ParentColors.chipFill("dad"), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(Spacing.XS))
                        Text(
                            text = parentNames.labelFor("dad"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // Seasonal schedules (MON-14) and holiday fairness (MON-20). Sent on their own, not by
            // the Save button below, which saves only the base pattern above. Both belong to the
            // family schedule, and so does "Different schedule for a child" (FAM-4), which appears
            // only at two children: none of the three is shown while the editor is on one child.
            if (childScope == null) {
                SeasonalScheduleSection()
                ChildSchedulesSection(
                    children = children,
                    childrenWithOwnSchedule = currentModel?.childOverrides.orEmpty().map { it.childId }.toSet(),
                    onEdit = { viewModel.editSchedule(it) }
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom bar
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        LocalDatePickerDialog(
            initialDate = uiState.startDate,
            confirmLabel = stringResource(R.string.custody_ok),
            dismissLabel = stringResource(R.string.custody_cancel),
            onConfirm = viewModel::setStartDate,
            onDismiss = { showDatePicker = false }
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
 * Which question the switch above the preview is actually asking, for the selected pattern.
 *
 * Three of the four patterns alternate blocks of time, so "who starts first" is the whole of
 * it. [CustodyModelType.EVERY_OTHER_WEEKEND] does not alternate: one parent has the child and
 * the other has every second weekend, and asking a parent who "starts" invites them to answer
 * about the first weekend — which sets the switch backwards and hands over the school days.
 * A schedule that is wrong in that direction is exactly the failure this app exists to prevent,
 * so the question changes with the pattern rather than the parent being expected to translate it.
 *
 * `CUSTOM` never reaches here: the switch is not rendered for it.
 */
@StringRes
private fun rolesQuestionFor(modelType: CustodyModelType): Int = when (modelType) {
    CustodyModelType.EVERY_OTHER_WEEKEND -> R.string.custody_who_is_resident
    else -> R.string.custody_who_starts_first
}

/** The matching answer line — see [rolesQuestionFor]. */
@StringRes
private fun rolesAnswerFor(modelType: CustodyModelType): Int = when (modelType) {
    CustodyModelType.EVERY_OTHER_WEEKEND -> R.string.custody_is_resident
    else -> R.string.custody_starts_first
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
private fun createTempModel(state: CustodySetupUiState): com.coparently.app.domain.model.CustodyModel? {
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
private fun custodyPreviewText(uiState: CustodySetupUiState, parentNames: ParentNames): String {
    val first = parentNames.labelFor(uiState.firstSlot)
    val second = parentNames.labelFor(uiState.secondSlot)
    return when (uiState.selectedModelType) {
        CustodyModelType.WEEK_ON_WEEK_OFF ->
            stringResource(R.string.custody_preview_week_on_week_off, first)
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
