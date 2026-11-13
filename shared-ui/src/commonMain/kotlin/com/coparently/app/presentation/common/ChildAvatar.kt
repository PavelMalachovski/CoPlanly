package com.coparently.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.platform.AppImage

/**
 * A child's avatar: the first letter of their name on a `primaryContainer` circle.
 *
 * When the child has a portrait this viewer may see (`RecordPhotoAccess.viewable`: only the
 * family's two parents), it is drawn over the circle through `AppImage` with the initial as its
 * placeholder; for a guest, a foreign family or a legacy value the initial is all there is.
 * Decorative: the child's name is given to TalkBack by the surface that draws the avatar (see
 * [BrandHeader]).
 *
 * @param child The child the avatar stands for
 * @param size Diameter of the circle
 * @param modifier Modifier for the circle
 */
@Composable
fun ChildAvatar(child: ChildInfo, size: Dp, modifier: Modifier = Modifier) {
    val photo = rememberRecordPhoto(child.photo, RecordPhotoKind.CHILD, child.id, child.familyId)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val initial: @Composable () -> Unit = {
            Text(
                text = child.childName.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (photo == null) {
            initial()
        } else {
            AppImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
                placeholder = initial
            )
        }
    }
}
