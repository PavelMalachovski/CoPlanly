package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.coparently.app.domain.files.RecordPhotoAccess
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.ViewablePhoto

/**
 * The record a set of photographs belongs to — which decides where they are stored and who may
 * see them (L-4).
 *
 * @property kind The kind of record.
 * @property recordId The record's id.
 * @property familyId The record's stored `familyId`, or null.
 */
data class PhotoOwner(
    val kind: RecordPhotoKind,
    val recordId: String,
    val familyId: String?
)

/**
 * The uid of the signed-in user, for deciding which record photographs this screen may draw
 * (L-4). Provided once by `MainActivity`.
 *
 * **The default is null, and null draws no photograph** — a tree that nobody provided (a preview,
 * a screenshot test, a Hilt test that never signs in) shows every record without its photos,
 * which is the safe way to be wrong.
 */
val LocalPhotoViewerUid = compositionLocalOf<String?> { null }

/**
 * What to draw for the stored photograph [reference] of the record [recordId]: a
 * [ViewablePhoto] the app's image loader fetches as the signed-in user, or null — draw nothing, no
 * placeholder — for a legacy download URL, a malformed reference, or a viewer who is not one of the
 * family's two parents (a guest, a calendar friend, a professional).
 */
@Composable
fun rememberRecordPhoto(
    reference: String?,
    kind: RecordPhotoKind,
    recordId: String,
    recordFamilyId: String?
): ViewablePhoto? {
    val viewer = LocalPhotoViewerUid.current
    return remember(reference, kind, recordId, recordFamilyId, viewer) {
        RecordPhotoAccess.viewable(reference, kind, recordId, recordFamilyId, viewer)
    }
}

/**
 * The image-loader model for one entry of an editor's photo list — a photograph picked on this
 * phone and not uploaded yet, or a stored reference this viewer may see — or null to leave the
 * entry out. See [RecordPhotoAccess.modelFor].
 */
@Composable
fun rememberPhotoEntryModel(
    entry: String,
    kind: RecordPhotoKind,
    recordId: String,
    recordFamilyId: String?
): Any? {
    val viewer = LocalPhotoViewerUid.current
    return remember(entry, kind, recordId, recordFamilyId, viewer) {
        RecordPhotoAccess.modelFor(entry, kind, recordId, recordFamilyId, viewer)
    }
}
