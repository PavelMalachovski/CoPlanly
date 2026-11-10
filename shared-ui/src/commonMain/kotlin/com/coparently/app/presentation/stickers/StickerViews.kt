package com.coparently.app.presentation.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.HeroIcon
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionGroupScope
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.parent_role_body
import com.coparently.app.resources.parent_role_save
import com.coparently.app.resources.parent_role_save_failed
import com.coparently.app.resources.parent_role_title
import com.coparently.app.resources.sticker_from
import com.coparently.app.resources.sticker_from_app
import com.coparently.app.resources.sticker_got_it
import com.coparently.app.resources.sticker_new_heading
import com.coparently.app.resources.stickers_close
import com.coparently.app.resources.stickers_send
import com.coparently.app.resources.stickers_send_failed
import com.coparently.app.resources.stickers_send_note
import com.coparently.app.resources.stickers_send_supporting
import com.coparently.app.resources.stickers_send_title
import com.coparently.app.resources.stickers_sent
import com.coparently.app.resources.stickers_settings_description
import com.coparently.app.resources.stickers_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The role this parent's stickers address them by, or null while none is known. */
fun ParentRoleAnswer.roleOrNull(): ParentRole? = (this as? ParentRoleAnswer.Answered)?.role

/**
 * A sticker's badge: its picture on the tonal disc every hero icon sits on, and its sentence as
 * the receiver reads it.
 *
 * @param kind What the sticker says.
 * @param role How its receiver's stickers address them; null words it neutrally.
 */
@Composable
fun StickerBadge(kind: StickerKind, role: ParentRole?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroIcon(StickerText.icon(kind))
        Text(
            text = stringResource(StickerText.sentence(kind, role)),
            style = MaterialTheme.typography.titleMediumEmphasized
        )
    }
}

/** Who a received [sticker] is from: the app, or the co-parent named [coParentName]. */
@Composable
fun senderLine(sticker: Sticker, coParentName: String): String =
    if (sticker.fromUid == null) {
        stringResource(Res.string.sticker_from_app)
    } else {
        stringResource(Res.string.sticker_from, coParentName)
    }

/**
 * Home's stickers (October 2026): the newest sticker this parent has not seen, on a card they
 * close with "Got it", and the row that sends the co-parent one. Self-contained, like
 * `FamilySwitcherChip`: Home adds it with one line and its own ViewModel gains nothing.
 *
 * @param coParentName The co-parent of the family on screen, by name.
 */
@Composable
fun HomeStickers(coParentName: String, viewModel: StickersViewModel = koinViewModel()) {
    val unseen by viewModel.unseen.collectAsState()
    val role by viewModel.role.collectAsState()
    val family by viewModel.familyOnScreen.collectAsState()
    val sending by viewModel.sending.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        unseen.firstOrNull()?.let { sticker ->
            SectionGroup {
                Column(
                    modifier = Modifier.padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M)
                ) {
                    GroupLabel(stringResource(Res.string.sticker_new_heading))
                    StickerBadge(sticker.kind, role.roleOrNull())
                    Text(
                        text = senderLine(sticker, coParentName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.markSeen(sticker) }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(Res.string.sticker_got_it))
                    }
                }
            }
        }
        if (family != null) {
            SectionGroup {
                SectionRow(
                    icon = Icons.Default.Favorite,
                    title = stringResource(Res.string.stickers_send),
                    supporting = stringResource(Res.string.stickers_send_supporting, coParentName),
                    onClick = { sheetOpen = true },
                    trailing = { Chevron() }
                )
            }
        }
    }
    if (sheetOpen) {
        SendStickerSheet(
            coParentName = coParentName,
            sending = sending,
            onSend = viewModel::send,
            onDismiss = {
                sheetOpen = false
                viewModel.sendingShown()
            }
        )
    }
}

/**
 * The fixed phrases a parent may send — no free text, so nothing can be turned into sarcasm —
 * and where the sending stands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendStickerSheet(
    coParentName: String,
    sending: StickersViewModel.SendState,
    onSend: (StickerKind) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L)
                .padding(bottom = Spacing.XL),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            Text(
                text = stringResource(Res.string.stickers_send_title, coParentName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(Res.string.stickers_send_note, coParentName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (sending) {
                StickersViewModel.SendState.Sent -> {
                    Text(
                        text = stringResource(Res.string.stickers_sent, coParentName),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(Res.string.stickers_close))
                    }
                }
                else -> {
                    if (sending == StickersViewModel.SendState.Sending) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (sending == StickersViewModel.SendState.Failed) {
                        Text(
                            text = stringResource(Res.string.stickers_send_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SectionGroup {
                        StickerKind.PARENT_KINDS.forEachIndexed { index, kind ->
                            if (index > 0) Divider()
                            SectionRow(
                                icon = StickerText.icon(kind),
                                title = stringResource(StickerText.phrase(kind)),
                                onClick = { onSend(kind) }.takeIf { sending != StickersViewModel.SendState.Sending }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home's one question about how stickers address this parent (owner decision, October 2026): asked
 * once the server says there is no answer, with no "later" and no way round it but an answer. It
 * stays until the answer is saved.
 */
@Composable
fun ParentRolePrompt(viewModel: StickersViewModel = koinViewModel()) {
    val role by viewModel.role.collectAsState()
    val failed by viewModel.roleSaveFailed.collectAsState()
    if (role != ParentRoleAnswer.Unanswered) return
    ParentRoleDialog(initial = null, failed = failed, onSave = viewModel::setRole, onDismiss = null)
}

/**
 * The three answers — dad, mom, or a neutral "parent" — and what the answer is for.
 *
 * @param initial The answer to start from; null starts from none, and Save waits for one.
 * @param failed Whether the last save did not land.
 * @param onDismiss Closes the dialog; null for Home's question, which only an answer closes.
 */
@Composable
fun ParentRoleDialog(
    initial: ParentRole?,
    failed: Boolean,
    onSave: (ParentRole) -> Unit,
    onDismiss: (() -> Unit)?
) {
    var chosen by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null
        ),
        title = { Text(stringResource(Res.string.parent_role_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(Res.string.parent_role_body))
                Column(modifier = Modifier.selectableGroup()) {
                    ParentRole.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = chosen == option, role = Role.RadioButton) { chosen = option }
                                .padding(vertical = Spacing.XS),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = chosen == option, onClick = null)
                            Text(
                                text = stringResource(StickerText.roleName(option)),
                                modifier = Modifier.padding(start = Spacing.M)
                            )
                        }
                    }
                }
                if (failed) {
                    Text(
                        text = stringResource(Res.string.parent_role_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let(onSave) }, enabled = chosen != null) {
                Text(stringResource(Res.string.parent_role_save))
            }
        },
        dismissButton = if (onDismiss != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.stickers_close)) } }
        } else {
            null
        }
    )
}

/**
 * Settings → Family's row for "My stickers", followed by its divider.
 *
 * @param onOpen Opens the screen.
 */
@Composable
fun SectionGroupScope.StickersRow(onOpen: () -> Unit) {
    SectionRow(
        icon = Icons.Default.Favorite,
        title = stringResource(Res.string.stickers_title),
        supporting = stringResource(Res.string.stickers_settings_description),
        onClick = onOpen,
        trailing = { Chevron() }
    )
    Divider()
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(IconSizes.Standard)
    )
}
