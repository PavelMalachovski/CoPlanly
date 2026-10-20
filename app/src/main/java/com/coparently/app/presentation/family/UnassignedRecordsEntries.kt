package com.coparently.app.presentation.family

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.SectionGroupScope
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.IconSizes

/**
 * Settings → Family's row for "Records without a family", with how many wait, followed by its
 * divider — and nothing at all while none wait or the parent is in no family, so a parent with
 * nothing to answer sees the group they always saw.
 *
 * Self-contained, like `FamilySwitcherChip`: it collects its own [UnassignedRecordsViewModel], so
 * Settings adds it with one line and its own ViewModel gains nothing. The count names nobody, so
 * the row costs no Firestore read.
 *
 * @param onOpen Opens the list.
 * @param viewModel The count.
 */
@Composable
fun SectionGroupScope.UnassignedRecordsRow(
    onOpen: () -> Unit,
    viewModel: UnassignedRecordsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsState()
    if (summary.count == 0) return
    SectionRow(
        icon = Icons.Default.VisibilityOff,
        title = stringResource(R.string.unassigned_records_title),
        supporting = pluralStringResource(R.plurals.unassigned_records_count, summary.count, summary.count),
        onClick = onOpen,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    )
    Divider()
}

/**
 * Home's one question about records without a family (owner decision, October 2026): asked once,
 * when the parent has two families or more and records that name neither, and once more only when
 * the set of co-parents changes. "Choose" opens the list; "Later" — or a tap outside — puts it off,
 * and the Settings row stays the way back.
 *
 * Self-contained: it collects its own [UnassignedRecordsViewModel], so Home adds it with one line.
 *
 * @param onChoose Opens the list.
 * @param viewModel Whether to ask, and the answer.
 */
@Composable
fun UnassignedRecordsPrompt(
    onChoose: () -> Unit,
    viewModel: UnassignedRecordsViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsState()
    if (!summary.askOnHome) return
    ConfirmationDialog(
        title = stringResource(R.string.unassigned_records_prompt_title),
        message = pluralStringResource(R.plurals.unassigned_records_prompt_body, summary.count, summary.count),
        confirmText = stringResource(R.string.unassigned_records_prompt_choose),
        dismissText = stringResource(R.string.unassigned_records_prompt_later),
        onConfirm = {
            viewModel.promptAnswered()
            onChoose()
        },
        onDismiss = viewModel::promptAnswered
    )
}
