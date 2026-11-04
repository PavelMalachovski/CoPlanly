package com.coparently.app.presentation.childinfo.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coparently.app.presentation.common.PhotoOwner
import com.coparently.app.presentation.common.PhotoStrip
import com.coparently.app.presentation.common.PhotoStripStrings
import com.coparently.app.resources.Res
import com.coparently.app.resources.medical_photos_add
import com.coparently.app.resources.medical_photos_close
import com.coparently.app.resources.medical_photos_hint
import com.coparently.app.resources.medical_photos_none
import com.coparently.app.resources.medical_photos_open
import com.coparently.app.resources.medical_photos_remove
import com.coparently.app.resources.medical_photos_thumbnail_description
import com.coparently.app.resources.medical_photos_title

/**
 * The photographs attached to a child's medical notes.
 *
 * A thin binding of the shared [PhotoStrip] to the medical strings — the strip's behaviour
 * (read-only by default, removal is a request settled on save, full-screen open) lives in
 * `presentation/common/PhotoStrip.kt`, where the pet record shares it.
 *
 * @param photos What to show: references already on the record, and content URIs picked on
 *   this device and not yet uploaded.
 * @param owner The child record the photographs belong to; only its two parents see them.
 * @param modifier Modifier applied to the strip.
 * @param onAdd Opens the picker, or null for a read-only strip.
 * @param onRemove Asks for a photograph to be removed, or null for a read-only strip.
 * @param enabled Whether the controls accept input; ignored when read-only.
 */
// The strip's own parameters plus the record they belong to; a holder would only rename them.
@Suppress("LongParameterList")
@Composable
fun MedicalPhotoStrip(
    photos: List<String>,
    owner: PhotoOwner,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    enabled: Boolean = true
) {
    PhotoStrip(
        photos = photos,
        owner = owner,
        strings = PhotoStripStrings(
            title = Res.string.medical_photos_title,
            empty = Res.string.medical_photos_none,
            add = Res.string.medical_photos_add,
            hint = Res.string.medical_photos_hint,
            thumbnailDescription = Res.string.medical_photos_thumbnail_description,
            open = Res.string.medical_photos_open,
            remove = Res.string.medical_photos_remove,
            close = Res.string.medical_photos_close
        ),
        modifier = modifier,
        onAdd = onAdd,
        onRemove = onRemove,
        enabled = enabled
    )
}
