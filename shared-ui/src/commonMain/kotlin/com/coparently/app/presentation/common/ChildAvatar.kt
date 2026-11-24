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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.ViewablePhoto
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Pet
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
    RecordAvatar(
        name = child.childName,
        photo = rememberRecordPhoto(child.photo, RecordPhotoKind.CHILD, child.id, child.familyId),
        size = size,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    )
}

/**
 * A pet's avatar: its first photo when it has one this viewer may see (only the family's two
 * parents, as for a child's portrait), else the first letter of its name on a `secondaryContainer`
 * circle, a pet's neutral tone beside a child's. The pet's photo is the first of `Pet.photos`
 * (24 November 2026): no field of its own, so an older build's record and wire form are untouched.
 *
 * @param pet The pet the avatar stands for
 * @param size Diameter of the circle
 * @param modifier Modifier for the circle
 */
@Composable
fun PetAvatar(pet: Pet, size: Dp, modifier: Modifier = Modifier) {
    RecordAvatar(
        name = pet.name,
        photo = rememberRecordPhoto(pet.photos.firstOrNull(), RecordPhotoKind.PET, pet.id, pet.familyId),
        size = size,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    )
}

@Composable
@Suppress("LongParameterList") // a name, a photo, its size, its two colours and the caller's modifier
private fun RecordAvatar(
    name: String,
    photo: ViewablePhoto?,
    size: Dp,
    container: Color,
    content: Color,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        val style = MaterialTheme.typography.titleMedium
        val initial: @Composable () -> Unit = {
            // The disc does not grow with the font, so neither does its letter past half its side.
            CappedFontScale(max = fontScaleToFit(size, style.fontSize, INITIAL_SHARE)) {
                Text(text = name.trim().take(1).uppercase(), style = style, color = content)
            }
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

/** How much of an avatar's side its initial may take at most, at any font scale. */
internal const val INITIAL_SHARE = 0.5f
