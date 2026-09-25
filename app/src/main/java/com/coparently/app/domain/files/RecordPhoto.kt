package com.coparently.app.domain.files

/**
 * The four kinds of record that carry photographs (L-4), each with its Storage prefix.
 *
 * The prefixes are part of the stored schema — `storage.rules`, `functions/index.js`
 * (`RECORD_PHOTO_LAYOUTS`) and every stored [RecordPhoto] name them — and are never renamed.
 *
 * @property prefix The first segment of every object path of this kind.
 */
enum class RecordPhotoKind(val prefix: String) {
    /** Photographs attached to a child's medical notes (`child_info.medicalPhotos`). */
    MEDICAL("medical_photos"),

    /** A pet's photographs (`pets.photos`). */
    PET("pet_photos"),

    /** An expense's receipt (`expenses.receiptUrl`). */
    RECEIPT("receipts"),

    /** An event's photo (`events.imageUrl`). */
    EVENT("event_images");

    companion object {
        /** The kind whose prefix is [prefix], or null for any other first segment. */
        fun ofPrefix(prefix: String): RecordPhotoKind? = entries.firstOrNull { it.prefix == prefix }
    }
}

/**
 * One stored photograph of a record (L-4): where its bytes are, what they are, and their digest.
 *
 * @property storagePath `{prefix}/{owner}/{recordId}/{objectName}` — see [RecordPhotoPaths]. The
 *   only address the photograph has. **Never a download URL**: a token URL bypasses
 *   `storage.rules` for whoever holds it, for as long as the object exists.
 * @property contentType One of [RecordPhotoPolicy.CONTENT_TYPES].
 * @property sizeBytes Size of the bytes at [storagePath].
 * @property sha256 Lowercase hex SHA-256 of those bytes; a download is checked against it.
 */
data class RecordPhoto(
    val storagePath: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * What a record's photograph may be. **The same numbers are written in `storage.rules`
 * (`isAcceptableRecordPhoto`) and must agree**; `RecordPhotoPolicyTest` pins them.
 *
 * Images only — a PDF belongs in the vault (MON-23). The cap is lower than the vault's because
 * the app re-encodes every photograph to a JPEG of at most 1600 px before upload
 * (`FirebaseImageStorage`), which comes out well under one megabyte; ten leaves room for a future
 * build that uploads the original without letting the bucket hold anything larger.
 */
object RecordPhotoPolicy {

    /** Exclusive upper bound, matching the rule's `size < 10 * 1024 * 1024`. */
    const val MAX_BYTES: Long = 10L * 1024L * 1024L

    /** The image types a phone's camera or gallery produces. */
    val CONTENT_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/heic",
        "image/heif",
        "image/webp"
    )
}

/**
 * The wire form of a [RecordPhoto]: one string, stored where the record kept its photo's download
 * URL before L-4 — `events.imageUrl`, `expenses.receiptUrl`, and the entries of
 * `child_info.medicalPhotos` and `pets.photos` — in Room and in Firestore alike.
 *
 * **The field names did not change**, so no Room column and no Firestore key moved and no schema
 * version was taken. What changed is what the string holds. An older build reads this string as a
 * URL it cannot load and shows no photo; this build reads an older build's download URL (or the
 * flat path of an even older one) as no photo and never fetches it. Before release both are test
 * data, and `purgeLegacyPhotoPaths` removes the old objects and references server-side.
 *
 * Format: `ph1|{storagePath}|{contentType}|{sizeBytes}|{sha256}`. Never Gson over the data class:
 * R8 renamed a Gson model's fields once already and it shipped.
 */
object RecordPhotoCodec {

    /** Marks a string as a photo reference; the `1` is the format version. */
    const val PREFIX = "ph1|"

    private const val FIELD_COUNT = 4
    private const val FIELD_PATH = 0
    private const val FIELD_TYPE = 1
    private const val FIELD_SIZE = 2
    private const val FIELD_SHA256 = 3
    private const val SEPARATOR = '|'
    private const val SHA256_HEX_LENGTH = 64
    private val HEX = Regex("^[0-9a-f]+$")

    /** The stored string for [photo]. */
    fun encode(photo: RecordPhoto): String = listOf(
        photo.storagePath,
        photo.contentType,
        photo.sizeBytes.toString(),
        photo.sha256
    ).joinToString(SEPARATOR.toString(), prefix = PREFIX)

    /**
     * The photograph [entry] describes, or null for anything else — a legacy download URL, a
     * legacy flat path, a blank, or a malformed reference. Null means "no photo", never "fetch
     * whatever this is".
     */
    fun decode(entry: String?): RecordPhoto? {
        val parts = entry?.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.split(SEPARATOR)
            ?.takeIf { it.size == FIELD_COUNT }
            ?: return null
        val size = parts[FIELD_SIZE].toLongOrNull() ?: return null
        return RecordPhoto(
            storagePath = parts[FIELD_PATH],
            contentType = parts[FIELD_TYPE],
            sizeBytes = size,
            sha256 = parts[FIELD_SHA256]
        ).takeIf { isWellFormed(it) }
    }

    /** True when [entry] is a well-formed reference of this format. */
    fun isReference(entry: String?): Boolean = decode(entry) != null

    private fun isWellFormed(photo: RecordPhoto): Boolean =
        RecordPhotoPaths.parse(photo.storagePath) != null &&
            photo.contentType in RecordPhotoPolicy.CONTENT_TYPES &&
            photo.sizeBytes > 0 &&
            photo.sizeBytes < RecordPhotoPolicy.MAX_BYTES &&
            photo.sha256.length == SHA256_HEX_LENGTH &&
            HEX.matches(photo.sha256)
}
