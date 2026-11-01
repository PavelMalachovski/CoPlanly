package com.coparently.app.domain.files

/**
 * A stored photograph this viewer may load, in the form the image loader takes (L-4).
 *
 * The app's Coil `ImageLoader` has a fetcher for this type (`RecordPhotoFetcher`) that downloads
 * through the Storage SDK as the signed-in user — never by download URL — into the verified
 * local cache, trying [paths] in order.
 *
 * @property paths Storage paths to try, in order; never empty.
 * @property contentType The image type.
 * @property sha256 The digest every download is checked against; also the memory-cache key.
 */
data class ViewablePhoto(
    val paths: List<String>,
    val contentType: String,
    val sha256: String
)

/**
 * Turns what a record stores into what a screen may draw (L-4).
 *
 * Pure, so every rule is a JVM test: which stored strings are photographs at all, which of them
 * this viewer may see, and which strings in an editor are photographs picked on this phone and
 * not uploaded yet.
 */
object RecordPhotoAccess {

    /**
     * What to draw for [reference], stored on the record [recordId] of [kind]: a [ViewablePhoto],
     * or null — no photo — for a legacy download URL or flat path, a malformed reference, a
     * photograph of another record, or a viewer who is not one of the two parents.
     */
    fun viewable(
        reference: String?,
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        viewerUid: String?
    ): ViewablePhoto? {
        val photo = RecordPhotoCodec.decode(reference) ?: return null
        val paths = RecordPhotoPaths.candidatesFor(photo, kind, recordId, recordFamilyId, viewerUid)
        if (paths.isEmpty()) return null
        return ViewablePhoto(paths = paths, contentType = photo.contentType, sha256 = photo.sha256)
    }

    /**
     * True for a photograph picked on this phone and not uploaded yet — a `content:` or `file:`
     * URI, which an editor shows directly. Anything else that is not a reference is a legacy
     * value and is never loaded.
     */
    fun isLocalPick(entry: String?): Boolean =
        entry != null && (entry.startsWith("content:") || entry.startsWith("file:"))

    /**
     * The model an image loader is given for one entry of an editor's photo list: the entry
     * itself for a local pick, a [ViewablePhoto] for a reference this viewer may see, else null.
     */
    fun modelFor(
        entry: String?,
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        viewerUid: String?
    ): Any? = if (isLocalPick(entry)) entry else viewable(entry, kind, recordId, recordFamilyId, viewerUid)

    /**
     * The entries of a stored photo list worth keeping on the next save: well-formed references.
     * A legacy download URL or flat path is dropped — it was never going to be shown, and
     * `purgeLegacyPhotoPaths` has removed (or will remove) the object it named.
     */
    fun storedReferences(entries: List<String>): List<String> = entries.filter { RecordPhotoCodec.isReference(it) }
}
