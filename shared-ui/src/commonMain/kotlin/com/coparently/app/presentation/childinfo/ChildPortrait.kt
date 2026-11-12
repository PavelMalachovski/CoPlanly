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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.ChildAvatar
import com.coparently.app.presentation.platform.LocalFilePicker
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.child_photo_add
import com.coparently.app.resources.child_photo_change
import com.coparently.app.resources.child_photo_private
import com.coparently.app.resources.child_photo_remove
import org.jetbrains.compose.resources.stringResource

/** Diameter of the portrait on a child's own screen. */
private val PORTRAIT_SIZE = 120.dp

/**
 * A child's portrait at the top of their screen (part 5b): 120 dp, with the picker beside it and
 * the line that says who sees it. The picker is offered only where the phone has one
 * (`FilePicker.available`); the removal only while there is a portrait.
 *
 * @param child The child.
 * @param onPick Receives the picked photo's content URI.
 * @param onRemove Removes the portrait.
 */
@Composable
fun ChildPortrait(child: ChildInfo, onPick: (String) -> Unit, onRemove: () -> Unit) {
    val picker = LocalFilePicker.current
    val launcher = picker.rememberImagePicker(onPick)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Box(contentAlignment = Alignment.Center) { ChildAvatar(child, PORTRAIT_SIZE) }
        if (picker.available) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val label = if (child.photo == null) Res.string.child_photo_add else Res.string.child_photo_change
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
                if (child.photo != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(Res.string.child_photo_remove)) }
                }
            }
        }
        Text(
            text = stringResource(Res.string.child_photo_private, child.childName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
