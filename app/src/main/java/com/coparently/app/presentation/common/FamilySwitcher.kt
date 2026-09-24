package com.coparently.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.FamilySignal
import com.coparently.app.presentation.theme.LayoutConstants

/** Wide enough for a first name and a surname; a longer one ellipsises. */
private val CHIP_MAX_WIDTH = 160.dp

/** Space between a dialog row's text and its dot. */
private val ROW_DOT_GAP = 8.dp

/**
 * The family switcher in a tab's top bar (M-8): the family on screen, named by its co-parent,
 * and a tap that opens the same dialog the Settings row opens.
 *
 * **It appears at two, not at one** — the rule the child filter and the Settings row follow. A
 * parent with a single co-parent sees the top bar they always saw; a chip offering a choice of
 * one is design item 8 in miniature. With two families the Settings-only route cost three taps
 * a day, which is what this is for.
 *
 * **A dot, never a number, when another family has something waiting** — new chat, a change
 * request or a schedule proposal to answer ([FamilySwitcherState.otherFamilySignals]). Everything
 * else follows the family on screen, so the only honest cross-family signal is a yes or no from
 * the server; the content description names which kinds. The family on screen never raises it:
 * its own news is on its own screens.
 *
 * Self-contained: it collects its own [FamilySwitcherViewModel], so a screen adds it with one
 * line in `actions` and gains no parameter.
 *
 * @param modifier Modifier applied to the chip
 * @param viewModel The switcher's state and its one action
 */
@Composable
fun FamilySwitcherChip(
    modifier: Modifier = Modifier,
    viewModel: FamilySwitcherViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (!state.canSwitch) return

    val chip = @Composable {
        PillChip(
            label = familyLabel(state.selected),
            modifier = Modifier.widthIn(max = CHIP_MAX_WIDTH),
            icon = Icons.Default.SwapHoriz,
            // The label is a person's name, which on its own says nothing about what a tap does.
            iconDescription = stringResource(R.string.settings_family_switch),
            onClick = { showDialog = true }
        )
    }
    val waiting = state.otherFamilySignals
    if (waiting.isNotEmpty()) {
        // One sentence per kind, so TalkBack says what is waiting and not only that something is.
        val dotDescription = waiting.map { stringResource(it.otherFamilyText) }.joinToString(SENTENCE_JOIN)
        BadgedBox(
            badge = { Badge(Modifier.semantics { contentDescription = dotDescription }) },
            modifier = modifier
        ) { chip() }
    } else {
        Box(modifier) { chip() }
    }
    if (showDialog) {
        FamilySwitcherDialog(
            families = state.families,
            selectedFamilyId = state.selectedFamilyId,
            signalsOf = state::signalsOf,
            onSelect = { familyId ->
                viewModel.select(familyId)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * What to call [family]: its co-parent's name, or "not named yet" when their profile could not
 * be read or carries none — never their uid.
 */
@Composable
fun familyLabel(family: FamilyOption?): String =
    family?.partnerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_family_unnamed)

/**
 * Which family this device is showing.
 *
 * Named by the co-parent, because that is what a parent recognises — the family id is a pair of
 * uids and means nothing to anyone. A relationship whose profile could not be read shows as
 * unnamed rather than being dropped: a switcher missing a row is worse than one with a row the
 * parent can still recognise by position.
 *
 * One dialog for the Settings row and the top-bar chip ([FamilySwitcherChip]), so the two
 * entry points cannot drift apart.
 *
 * @param families Every family the parent is in
 * @param selectedFamilyId The one on screen
 * @param signalsOf What a family's row says is waiting, drawn as a dot and a line naming each
 *   kind — [FamilySwitcherState.signalsOf], which is always empty for the family on screen
 * @param onSelect Called with the family tapped
 * @param onDismiss Closes without switching
 */
@Composable
fun FamilySwitcherDialog(
    families: List<FamilyOption>,
    selectedFamilyId: String?,
    signalsOf: (String) -> Set<FamilySignal>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_family_switch)) },
        text = {
            Column {
                families.forEach { family ->
                    FamilySwitcherRow(
                        family = family,
                        selected = family.familyId == selectedFamilyId,
                        signals = signalsOf(family.familyId),
                        onSelect = { onSelect(family.familyId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_family_kind_cancel))
            }
        }
    )
}

/**
 * One family in [FamilySwitcherDialog]: a radio button, the co-parent's name and, when something
 * is waiting there, a line saying what (one phrase per kind) and a dot. The line is the row's
 * description, so the dot itself carries none — TalkBack would otherwise read it twice.
 */
@Composable
private fun FamilySwitcherRow(
    family: FamilyOption,
    selected: Boolean,
    signals: Set<FamilySignal>,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = LayoutConstants.MIN_TOUCH_TARGET)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A radio button with no click of its own has no padding of its own either, so the name
        // sat against it (docs/AUDIT-2026-10-design.md D-23). The row is the target.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(ROW_LABEL_GAP))
        Column(Modifier.weight(1f)) {
            Text(familyLabel(family))
            if (signals.isNotEmpty()) {
                Text(
                    text = signals.map { stringResource(it.rowText) }.joinToString(PHRASE_JOIN),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (signals.isNotEmpty()) {
            Spacer(Modifier.width(ROW_DOT_GAP))
            Badge()
        }
    }
}

/** Between a row's radio button and the family's name. */
private val ROW_LABEL_GAP = 16.dp

/** Joins the chip's per-kind sentences into one content description. */
private const val SENTENCE_JOIN = ". "

/** Joins a dialog row's per-kind phrases. */
private const val PHRASE_JOIN = " · "

/** The chip's sentence for one kind: "… in another family". */
@get:StringRes
private val FamilySignal.otherFamilyText: Int
    get() = when (this) {
        FamilySignal.CHAT -> R.string.family_switcher_unread_other
        FamilySignal.CHANGE_REQUEST -> R.string.family_switcher_request_other
        FamilySignal.SCHEDULE -> R.string.family_switcher_schedule_other
    }

/** A dialog row's phrase for one kind; the row already names the family. */
@get:StringRes
private val FamilySignal.rowText: Int
    get() = when (this) {
        FamilySignal.CHAT -> R.string.family_switcher_unread_row
        FamilySignal.CHANGE_REQUEST -> R.string.family_switcher_request_row
        FamilySignal.SCHEDULE -> R.string.family_switcher_schedule_row
    }
