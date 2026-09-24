package com.coparently.app.presentation.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coparently.app.R
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.FullScreenImageDialog
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime

/**
 * Read-only preview of an event shown before the editor: tapping an event in
 * the calendar opens this sheet; the full editor is one tap further (Edit).
 *
 * @param event Event (or expanded occurrence) to preview
 * @param parentNames Resolves a slot to that parent's name
 * @param members The family's children and pets, for naming who the event is about
 * @param onEdit Open the full editor for this event
 * @param onDelete Delete the event, or null where the surface has no delete-with-undo of its
 *   own (Home) — the button is then left out rather than shown doing something else
 * @param onDismiss Close the sheet without action
 */
@Suppress("LongParameterList") // the sheet's API: event, names, members and three actions
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPreviewSheet(
    event: Event,
    parentNames: ParentNames,
    members: List<FamilyMember> = emptyList(),
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens fully, like every other sheet in the app: a half-open action sheet is one more
        // resting state to drag through (audit 2026-09 §3.3).
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        EventPreviewContent(
            event = event,
            parentNames = parentNames,
            members = members,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

/**
 * The body of [EventPreviewSheet], without the sheet around it.
 *
 * Split out so the JVM screenshot tests can render it: a `ModalBottomSheet` opens its own window,
 * which a single-view capture does not see. Everything the sheet shows lives here.
 *
 * @param event Event (or expanded occurrence) to preview
 * @param parentNames Resolves the event's slot to that parent's name
 * @param members The family's children and pets, for naming who the event is about
 * @param onEdit Open the full editor for this event
 * @param onDelete Delete the event, or null to leave the button out
 */
@Suppress("LongMethod") // linear declarative layout, no logic to extract
@Composable
internal fun EventPreviewContent(
    event: Event,
    parentNames: ParentNames,
    members: List<FamilyMember>,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val parentColor = when (event.parentOwner) {
        "mom" -> ParentColors.fill("mom")
        "dad" -> ParentColors.fill("dad")
        else -> MaterialTheme.colorScheme.tertiary
    }
    val parentLabel = parentNames.labelFor(event.parentOwner)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL)
            .padding(bottom = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(parentColor, CircleShape)
            )
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                // Long titles used to wrap mid-word and push the sheet's actions down.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.M)
            )
        }

        PreviewRow(icon = Icons.Default.Schedule) {
            val dateFormat = localizedDate("yMMMEEEd")
            val timeFormat = shortTime()
            val dateText = event.startDateTime.format(dateFormat)
            val timeText = buildString {
                append(event.startDateTime.format(timeFormat))
                event.endDateTime?.let { end ->
                    append(" – ")
                    // An overnight or multi-day event names its end day too; "22:00 – 07:00"
                    // under the start date read as ending before it began.
                    if (end.toLocalDate() != event.startDateTime.toLocalDate()) {
                        append(end.format(dateFormat))
                        append(' ')
                    }
                    append(end.format(timeFormat))
                }
            }
            Text(
                text = "$dateText · $timeText",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        PreviewRow(icon = Icons.Default.Person) {
            Text(text = parentLabel, style = MaterialTheme.typography.bodyMedium)
        }

        // Who the event is about, named rather than coloured — the parent slot above already
        // owns the one colour this sheet spends. An event that names nobody is the whole
        // family's and gets no row at all, rather than a row saying "everyone", which would
        // add a line to every event in the app to state the default.
        //
        // Resolved against the family's current records, so a reference this build does not
        // understand, or one whose record is gone, is simply not named. It stays on the
        // event either way — see `FamilyMemberRef.Unknown`.
        val memberNames = event.forMembers.mapNotNull { ref ->
            members.firstOrNull { it.ref == ref }?.name
        }
        if (memberNames.isNotEmpty()) {
            PreviewRow(icon = Icons.Default.Groups) {
                Text(
                    text = memberNames.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            PreviewRow(icon = Icons.Default.Description) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (event.isPrivate) {
            PreviewRow(icon = Icons.Default.Lock) {
                Text(
                    text = stringResource(R.string.event_preview_private),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        event.imageUrl?.let { url ->
            // Cropped to 180dp here, so the photo is a hint at what is attached rather than
            // the thing itself; tapping opens the zoomable viewer that shows all of it.
            var viewingPhoto by rememberSaveable { mutableStateOf(false) }
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.image_viewer_open),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { viewingPhoto = true }
            )
            if (viewingPhoto) {
                FullScreenImageDialog(
                    model = url,
                    contentDescription = stringResource(R.string.event_preview_photo),
                    onDismiss = { viewingPhoto = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.XS))

        PreviewActions(onEdit = onEdit, onDelete = onDelete)
    }
}

/**
 * Delete and Edit, side by side — or, from [STACK_ACTIONS_FONT_SCALE], one above the other with
 * Edit first. Side by side at 150 % each half was too narrow for its word, and German broke
 * "Löschen" and "Bearbeiten" in the middle (docs/AUDIT-2026-10-design.md, visual note V2).
 */
@Composable
private fun PreviewActions(onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    val deleteButton: @Composable (Modifier, () -> Unit) -> Unit = { modifier, delete ->
        OutlinedButton(
            onClick = delete,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
            Text(
                text = stringResource(R.string.event_preview_delete),
                modifier = Modifier.padding(start = Spacing.S)
            )
        }
    }
    val editButton: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onEdit, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
            Text(
                text = stringResource(R.string.event_preview_edit),
                modifier = Modifier.padding(start = Spacing.S)
            )
        }
    }
    if (LocalDensity.current.fontScale >= STACK_ACTIONS_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            editButton(Modifier.fillMaxWidth())
            if (onDelete != null) deleteButton(Modifier.fillMaxWidth(), onDelete)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            if (onDelete != null) deleteButton(Modifier.weight(1f), onDelete)
            editButton(Modifier.weight(1f))
        }
    }
}

/** From this font scale the preview's two actions stack instead of sharing a row. */
private const val STACK_ACTIONS_FONT_SCALE = 1.3f

@Composable
private fun PreviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSizes.Small)
        )
        Box(modifier = Modifier.padding(start = Spacing.M)) {
            content()
        }
    }
}
