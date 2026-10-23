package com.coparently.app.presentation.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.theme.Spacing

/**
 * "My stickers" (Settings → Family, October 2026): how this parent's stickers address them, the
 * stickers they received — opening the screen counts as seeing them — and the ones they sent,
 * each saying whether the co-parent has seen it yet. Kept to the family on screen, as Home is.
 *
 * @param onNavigateUp Returns to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStickersScreen(
    onNavigateUp: () -> Unit,
    viewModel: StickersViewModel = hiltViewModel()
) {
    val parents by viewModel.parents.collectAsState()
    val coParentName = rememberParentNames(parents).labelForUid(parents.coParent?.uid)
    val received by viewModel.received.collectAsState()
    val sent by viewModel.sent.collectAsState()
    val role by viewModel.role.collectAsState()
    val roleFailed by viewModel.roleSaveFailed.collectAsState()
    var editingRole by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(received) {
        received.filter { it.seenAtMillis == null }.forEach(viewModel::markSeen)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stickers_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        StickerShelf(
            shelf = Shelf(received, sent, role, coParentName),
            padding = padding,
            onEditRole = { editingRole = true }
        )
    }

    if (editingRole) {
        ParentRoleDialog(
            initial = role.roleOrNull(),
            failed = roleFailed,
            onSave = {
                viewModel.setRole(it)
                editingRole = false
            },
            onDismiss = { editingRole = false }
        )
    }
}

/** What the shelf draws. */
private data class Shelf(
    val received: List<Sticker>,
    val sent: List<Sticker>,
    val role: ParentRoleAnswer,
    val coParentName: String
)

@Composable
private fun StickerShelf(shelf: Shelf, padding: PaddingValues, onEditRole: () -> Unit) {
    val received = shelf.received
    val sent = shelf.sent
    val role = shelf.role
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.L,
            end = Spacing.L,
            top = padding.calculateTopPadding() + Spacing.S,
            bottom = padding.calculateBottomPadding() + Spacing.XL
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        item {
            SectionGroup {
                SectionRow(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.stickers_role_row),
                    supporting = role.roleOrNull()?.let { stringResource(StickerText.roleName(it)) }
                        ?: stringResource(R.string.stickers_role_unanswered),
                    onClick = onEditRole.takeIf { role != ParentRoleAnswer.Loading }
                )
            }
        }
        if (received.isEmpty() && sent.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Favorite,
                    title = stringResource(R.string.stickers_empty_title),
                    description = stringResource(R.string.stickers_empty_body),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (received.isNotEmpty()) {
            item { GroupLabel(stringResource(R.string.stickers_received_heading)) }
            items(received, key = { "r_${it.id}" }) { sticker -> ReceivedSticker(sticker, role, shelf.coParentName) }
        }
        if (sent.isNotEmpty()) {
            item { GroupLabel(stringResource(R.string.stickers_sent_heading)) }
            items(sent, key = { "s_${it.id}" }) { sticker -> SentSticker(sticker) }
        }
    }
}

@Composable
private fun ReceivedSticker(sticker: Sticker, role: ParentRoleAnswer, coParentName: String) {
    SectionGroup {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            StickerBadge(sticker.kind, role.roleOrNull())
            Text(
                text = senderLine(sticker, coParentName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SentSticker(sticker: Sticker) {
    val status = when {
        sticker.seenAtMillis != null -> R.string.sticker_status_seen
        sticker.createdAtMillis != null -> R.string.sticker_status_delivered
        else -> R.string.sticker_status_sending
    }
    SectionGroup {
        SectionRow(
            icon = StickerText.icon(sticker.kind),
            title = stringResource(StickerText.phrase(sticker.kind)),
            supporting = stringResource(status)
        )
    }
}
