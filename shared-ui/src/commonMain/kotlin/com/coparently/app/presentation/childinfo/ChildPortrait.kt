package com.coparently.app.presentation.childinfo

import androidx.compose.runtime.Composable
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.ChildAvatar
import com.coparently.app.presentation.common.RECORD_PORTRAIT_SIZE
import com.coparently.app.presentation.common.RecordPortrait

/**
 * A child's portrait at the top of their screen (part 5b): 120 dp, with the picker beside it and
 * the line that says who sees it — [RecordPortrait], the anatomy a pet's screen shares. Add and
 * Change are offered only where the phone has a picker (`FilePicker.available`); Remove only while
 * there is a portrait, picker or not.
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
    RecordPortrait(
        hasPhoto = child.photo != null,
        coParentName = coParentName,
        onPick = onPick,
        onRemove = onRemove
    ) { ChildAvatar(child, RECORD_PORTRAIT_SIZE) }
}
