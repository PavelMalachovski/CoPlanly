package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/** Wide enough for a first name and a surname; a longer one ellipsises. */
private val CHIP_MAX_WIDTH = 160.dp

/** Space between a dialog row's name and its unread dot. */
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
 * **A dot, never a number, when another family's chat has something new** — the chat mirrors only
 * the family on screen, so the only honest cross-family signal is its conversation document
 * saying "newer than your read mark" ([FamilySwitcherState.otherFamilyHasUnread]). The family on
 * screen never raises it: its count is the Chat tab's badge.
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
    if (state.otherFamilyHasUnread) {
        val dotDescription = stringResource(R.string.family_switcher_unread_other)
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
            hasUnread = state::hasUnread,
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
 * @param hasUnread Whether a family's row carries the "something new in its chat" dot —
 *   [FamilySwitcherState.hasUnread], which never answers yes for the family on screen
 * @param onSelect Called with the family tapped
 * @param onDismiss Closes without switching
 */
@Composable
fun FamilySwitcherDialog(
    families: List<FamilyOption>,
    selectedFamilyId: String?,
    hasUnread: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dotDescription = stringResource(R.string.family_switcher_unread_row)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_family_switch)) },
        text = {
            Column {
                families.forEach { family ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = family.familyId == selectedFamilyId,
                                role = Role.RadioButton
                            ) { onSelect(family.familyId) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = family.familyId == selectedFamilyId,
                            onClick = null
                        )
                        Text(familyLabel(family))
                        if (hasUnread(family.familyId)) {
                            Spacer(Modifier.width(ROW_DOT_GAP))
                            Badge(Modifier.semantics { contentDescription = dotDescription })
                        }
                    }
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
