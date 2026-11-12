package com.coparently.app.presentation.childinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.ChildAvatar
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.platform.LocalFilePicker
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.child_photo_add
import com.coparently.app.resources.child_photo_change
import com.coparently.app.resources.child_photo_private
import com.coparently.app.resources.child_photo_private_alone
import com.coparently.app.resources.child_photo_remove
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Diameter of the portrait on a child's own screen. */
private val PORTRAIT_SIZE = 120.dp

/**
 * A child's portrait at the top of their screen (part 5b): 120 dp, with the picker beside it and
 * the line that says who sees it. Add and Change are offered only where the phone has a picker
 * (`FilePicker.available`); Remove only while there is a portrait, picker or not.
 *
 * @param child The child.
 * @param coParentName The co-parent's name, who sees the photo too; null with no co-parent, when
 *   the line says that only this parent sees it.
 * @param onPick Receives the picked photo's content URI.
 * @param onRemove Removes the portrait.
 */
@Composable
fun ChildPortrait(
    child: ChildInfo,
    coParentName: String?,
    onPick: (String) -> Unit,
    onRemove: () -> Unit
) {
    val picker = LocalFilePicker.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Box(contentAlignment = Alignment.Center) { ChildAvatar(child, PORTRAIT_SIZE) }
        if (picker.available || child.photo != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (picker.available) PickPortraitButton(hasPhoto = child.photo != null, onPick = onPick)
                if (child.photo != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(Res.string.child_photo_remove)) }
                }
            }
        }
        Text(
            text = if (coParentName.isNullOrBlank()) {
                stringResource(Res.string.child_photo_private_alone)
            } else {
                stringResource(Res.string.child_photo_private, coParentName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Add or Change, which opens the phone's picker; drawn only where there is one. */
@Composable
private fun PickPortraitButton(hasPhoto: Boolean, onPick: (String) -> Unit) {
    val launcher = LocalFilePicker.current.rememberImagePicker(onPick)
    val label = if (hasPhoto) Res.string.child_photo_change else Res.string.child_photo_add
    TextButton(onClick = { launcher.launch() }) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(IconSizes.Small)
        )
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(start = Spacing.XS)
        )
    }
}

/**
 * The co-parent's name for the portrait's privacy line, from the family on screen
 * ([ParentsSource]); null while there is no co-parent (or their name is not known yet).
 */
@Composable
fun rememberCoParentName(source: ParentsSource = koinInject()): String? {
    val parents by remember(source) { source.observe() }.collectAsState(initial = Parents())
    return parents.coParent?.name?.takeIf { it.isNotBlank() }
}
