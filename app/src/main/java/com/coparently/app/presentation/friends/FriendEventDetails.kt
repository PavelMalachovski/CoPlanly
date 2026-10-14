package com.coparently.app.presentation.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.event.eventTimeLine
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized

/**
 * The body of a family friend's event sheet (phase 2, spec §4.4): the event's title, when it is,
 * who added it, what it says, and whether the friend takes part. No action — a friend reads the
 * calendar and never writes to it, so there is no Edit, Delete or Request change to offer
 * (design item 8).
 *
 * Split from the sheet so the screenshot tests can render it, as `EventPreviewContent` is: a
 * `ModalBottomSheet` opens its own window, which a node capture does not see.
 *
 * @param event The event, or one occurrence of a recurring one.
 * @param addedBy The first name of the parent who added it, or null when the grant names nobody.
 * @param takesPart Whether the event names this friend as taking part.
 */
@Composable
internal fun FriendEventDetails(event: Event, addedBy: String?, takesPart: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL)
            .padding(bottom = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text(text = event.title, style = MaterialTheme.typography.titleLargeEmphasized)
        DetailRow(icon = Icons.Default.Schedule, text = eventTimeLine(event))
        addedBy?.let { name ->
            DetailRow(icon = Icons.Default.Person, text = stringResource(R.string.friend_calendar_added_by, name))
        }
        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            DetailRow(icon = Icons.Default.Description, text = description)
        }
        if (takesPart) TakesPartChip()
    }
}

/**
 * "You take part", marked with the friend's own colour — a calendar friend's teal, never a parent's
 * hue. The text-grade tone, because the plain teal is 3.36:1 on the dark surface and less on the
 * container a row sits on.
 */
@Composable
internal fun TakesPartChip() {
    PillChip(
        label = stringResource(R.string.friend_calendar_you_take_part),
        leadingDot = ParentColors.friendText,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** One line of the sheet behind its icon, as the parents' event preview lays its lines out. */
@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSizes.Small)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.M)
        )
    }
}
