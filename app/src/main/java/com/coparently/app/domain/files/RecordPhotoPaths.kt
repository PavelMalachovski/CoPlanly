package com.coparently.app.domain.files

import com.coparently.app.domain.family.FamilyKey

/**
 * A parsed record-photo path: `{prefix}/{owner}/{recordId}/{objectName}`.
 *
 * @property kind Which record kind the prefix names.
 * @property owner Either a family id (`FamilyKey.of`, the two parents' uids) or
 *   `solo_{uid}` — the uploader's own folder while they have no family yet.
 * @property recordId The id of the record the photograph belongs to.
 * @property objectName A fresh random name per upload, so no upload ever overwrites another.
 */
data class RecordPhotoPath(
    val kind: RecordPhotoKind,
    val owner: String,
    val recordId: String,
    val objectName: String
) {
    /** The path as stored and as `storage.rules` sees it. */
    val value: String get() = "${kind.prefix}/$owner/$recordId/$objectName"

    /** The uploader's uid when this is a personal (`solo_`) path, else null. */
    val soloUid: String?
        get() = owner.takeIf { it.startsWith(RecordPhotoPaths.SOLO_PREFIX) }
            ?.removePrefix(RecordPhotoPaths.SOLO_PREFIX)
            ?.takeIf { it.isNotBlank() }

    /** The two parents when this is a family path, else null. */
    val familyMembers: Pair<String, String>?
        get() = if (soloUid != null) null else FamilyKey.membersOf(owner)
}

/**
 * Where a record's photographs live, and who may read them (L-4).
 *
 * **The gate is the path, as in MON-23.** `storage.rules` reads the owner segment and nothing
 * else: a family path is readable, creatable and deletable by exactly the two uids its family id
 * joins (`isOneOfPair`), a `solo_{uid}` path by that uid alone. No document read and no
 * cross-service call, so the Storage emulator runs every branch. The cost is the same as MON-23's:
 * a path names its pair for ever, so it does not narrow at unpair.
 *
 * **Guests, calendar friends and professionals never read a photograph** — an owner decision. The
 * rules refuse them by construction (they are neither of the pair), and [candidatesFor] answers
 * nothing for them, so a screen shows the record without the photograph rather than a broken
 * image that promises one.
 */
object RecordPhotoPaths {

    /** The owner segment of a personal path is this followed by the uploader's uid. */
    const val SOLO_PREFIX = "solo_"

    private const val SEGMENTS = 4
    private const val PREFIX_SEGMENT = 0
    private const val OWNER_SEGMENT = 1
    private const val RECORD_SEGMENT = 2
    private const val OBJECT_SEGMENT = 3

    /**
     * The path for a new upload.
     *
     * @param familyId The record's family, or null while the uploader has none — the photograph
     *   then goes to the uploader's own `solo_` folder, and the server moves it to the family's
     *   when a pair forms (`movePhotosToFamily` in `functions/index.js`).
     * @param objectName A fresh random name, so the rule's no-overwrite check never fires.
     */
    fun build(
        kind: RecordPhotoKind,
        familyId: String?,
        uploaderUid: String,
        recordId: String,
        objectName: String
    ): String = RecordPhotoPath(
        kind = kind,
        owner = familyId ?: (SOLO_PREFIX + uploaderUid),
        recordId = recordId,
        objectName = objectName
    ).value

    /** [path] parsed, or null when it is not a well-formed record-photo path. */
    fun parse(path: String): RecordPhotoPath? {
        val parts = path.split('/')
        if (parts.size != SEGMENTS || parts.any { it.isBlank() }) return null
        val kind = RecordPhotoKind.ofPrefix(parts[PREFIX_SEGMENT]) ?: return null
        val parsed = RecordPhotoPath(kind, parts[OWNER_SEGMENT], parts[RECORD_SEGMENT], parts[OBJECT_SEGMENT])
        val validOwner = parsed.soloUid?.let { !it.contains(FamilyKey.SEPARATOR) }
            ?: (parsed.familyMembers != null)
        return parsed.takeIf { validOwner }
    }

    /**
     * True when [viewerUid] is allowed to read [path] — the same question `storage.rules` asks.
     */
    fun mayRead(path: String, viewerUid: String?): Boolean {
        val viewer = viewerUid?.takeIf { it.isNotBlank() }
        val parsed = parse(path)
        return when {
            viewer == null || parsed == null -> false
            parsed.soloUid != null -> parsed.soloUid == viewer
            else -> parsed.familyMembers?.toList()?.contains(viewer) == true
        }
    }

    /**
     * The paths [viewerUid] may try, in order, to read [photo] of the record [recordId] — empty
     * when the viewer may not see it at all, which a screen shows as "no photo".
     *
     * A photograph is only ever followed under **its own record** and **its own kind**: a
     * reference pointing at another record's folder is ignored, like `ChatAttachmentCodec.
     * belongsTo`. A family path is followed only when it names the record's family (when the
     * record names one).
     *
     * A `solo_` path is the one with two answers. Once the record belongs to a family that
     * includes the uploader, the server has moved (or will move) the object to the family folder
     * under the same name, so that path comes first — and it is the only one the co-parent can
     * read. The uploader also keeps their own `solo_` path as a fallback, for the moments before
     * the move has run. This is also what keeps an older copy of the record correct: a phone whose
     * Room row still names the `solo_` path, and writes it back, still resolves to the moved file.
     */
    fun candidatesFor(
        photo: RecordPhoto,
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        viewerUid: String?
    ): List<String> {
        val viewer = viewerUid?.takeIf { it.isNotBlank() }
        val parsed = parse(photo.storagePath)?.takeIf { it.kind == kind && it.recordId == recordId }
        val family = recordFamilyId?.takeIf { it.isNotBlank() }
        return when {
            viewer == null || parsed == null -> emptyList()
            parsed.soloUid == null -> listOf(parsed.value)
                .filter { (family == null || parsed.owner == family) && mayRead(it, viewer) }
            else -> soloCandidates(parsed, family, viewer)
        }
    }

    /** [candidatesFor] for a `solo_` path: the family's copy first, then the uploader's own. */
    private fun soloCandidates(parsed: RecordPhotoPath, family: String?, viewer: String): List<String> {
        val solo = parsed.soloUid
        val members = family?.let { FamilyKey.membersOf(it) }?.toList().orEmpty()
        return buildList {
            if (family != null && solo in members && viewer in members) add(parsed.copy(owner = family).value)
            if (viewer == solo) add(parsed.value)
        }
    }

    /** The last segment of [path]: the object's own name. */
    fun objectNameOf(path: String): String = path.substringAfterLast('/')
}
