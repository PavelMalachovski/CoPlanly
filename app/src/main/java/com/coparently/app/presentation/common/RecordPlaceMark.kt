package com.coparently.app.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coparently.app.R
import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.RecordPlace

/**
 * The signed-in parent's [FamilyView] for the whole tree, provided by `MainActivity` the way
 * `LocalParentPalette` is: a record drawn anywhere can say where it stands for the family on screen
 * ([placeOf]) without every screen threading the view through. The default places everything on
 * screen, which is what the app drew before records could be marked.
 */
val LocalFamilyView = staticCompositionLocalOf { FamilyView.EVERYTHING }

/** Where a record of [familyId], written by [createdBy], stands for the family on screen. */
@Composable
fun placeOf(familyId: String?, createdBy: String?): RecordPlace =
    LocalFamilyView.current.placeOf(familyId, createdBy)

/**
 * The one mark for a record that is not the family on screen's own (October 2026, owner
 * decision): "Only me" for this parent's record of no family, "From an earlier family" for one of a
 * family that has ended. Neutral on purpose — a parent colour names a person, and an earlier
 * family's slot colour would now name the wrong one. Draws nothing for a record of the family on
 * screen.
 */
@Composable
fun RecordPlaceMark(place: RecordPlace, modifier: Modifier = Modifier) {
    val (label, description) = when (place) {
        RecordPlace.ONLY_ME ->
            R.string.record_place_only_me to R.string.record_place_only_me_description
        RecordPlace.EARLIER_FAMILY ->
            R.string.record_place_earlier_family to R.string.record_place_earlier_family_description
        RecordPlace.ON_SCREEN, RecordPlace.OTHER_FAMILY -> return
    }
    val spoken = stringResource(description)
    PillChip(
        label = stringResource(label),
        modifier = modifier.semantics { contentDescription = spoken },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The spoken words of [place]'s mark, or null for a record of the family on screen. */
@Composable
fun placeDescription(place: RecordPlace): String? = when (place) {
    RecordPlace.ONLY_ME -> stringResource(R.string.record_place_only_me_description)
    RecordPlace.EARLIER_FAMILY -> stringResource(R.string.record_place_earlier_family_description)
    RecordPlace.ON_SCREEN, RecordPlace.OTHER_FAMILY -> null
}
