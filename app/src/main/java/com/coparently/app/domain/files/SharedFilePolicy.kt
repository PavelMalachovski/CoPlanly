package com.coparently.app.domain.files

import java.util.Locale

/**
 * What a file shared between the two parents may be — the vault's documents and chat
 * attachments alike (MON-23).
 *
 * **The same numbers are written three times, and must agree**: here, in `storage.rules`
 * (`isAcceptableSharedFile`) and in `firestore.rules` (`vaultContentTypes`, `validVaultFile`).
 * The rules are the gate; this is what lets the app refuse a file *before* uploading twenty
 * megabytes the server will then turn away, and say why in words. `SharedFilePolicyTest` pins the
 * values the rules were written against.
 */
object SharedFilePolicy {

    /** Exclusive upper bound, matching the rules' `size < 20 * 1024 * 1024`. */
    const val MAX_BYTES: Long = 20L * 1024L * 1024L

    /** Longest file name kept, matching `validVaultFile`'s `isValidLength(fileName, 1, 120)`. */
    const val MAX_NAME_LENGTH = 120

    /** PDF, and the image types a phone's camera or gallery produces. */
    val CONTENT_TYPES: Set<String> = setOf(
        PDF,
        "image/jpeg",
        "image/png",
        "image/heic",
        "image/heif",
        "image/webp"
    )

    private val EXTENSIONS: Map<String, String> = mapOf(
        "pdf" to PDF,
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "webp" to "image/webp"
    )

    /** Why a file is refused before anything is uploaded. */
    enum class Rejection {
        /** Not a PDF or one of the accepted image types. */
        TYPE,

        /** Empty, or at or over [MAX_BYTES]. */
        SIZE
    }

    /** True for an accepted image type; a PDF is the only other kind. */
    fun isImage(contentType: String): Boolean = contentType.startsWith("image/")

    /**
     * The file's type: the one the provider reported when it is accepted, otherwise whatever the
     * name's extension says, otherwise null.
     *
     * The extension is a fallback, not an override, because some providers report
     * `application/octet-stream` for a perfectly ordinary PDF.
     */
    fun resolveContentType(reported: String?, fileName: String): String? {
        val normalized = reported?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()
        if (normalized != null && normalized in CONTENT_TYPES) return normalized
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return EXTENSIONS[extension]
    }

    /** Null when a file of this type and size may be shared, else the reason it may not. */
    fun check(contentType: String?, sizeBytes: Long): Rejection? = when {
        contentType == null || contentType !in CONTENT_TYPES -> Rejection.TYPE
        sizeBytes <= 0 || sizeBytes >= MAX_BYTES -> Rejection.SIZE
        else -> null
    }

    /**
     * [raw] as a single, safe path segment.
     *
     * The name becomes the last segment of a Storage path that a rule rebuilds by concatenation,
     * so a `/` would change which folder it is in, and a `|` would break the chat reference
     * format (`ChatAttachmentCodec`). Control characters and backslashes go too. A name with
     * nothing left, or none at all, becomes "file" with the type's extension, and an over-long
     * one is shortened from the front of its stem so the extension survives.
     */
    fun safeFileName(raw: String?, contentType: String): String {
        val cleaned = raw.orEmpty()
            .map { char -> if (char.isISOControl() || char in FORBIDDEN_CHARS) '_' else char }
            .joinToString("")
            .trim()
            .trimStart('.')
        val named = cleaned.ifBlank { "file.${extensionFor(contentType)}" }
        if (named.length <= MAX_NAME_LENGTH) return named
        val extension = named.substringAfterLast('.', "")
        val suffix = if (extension.isNotEmpty() && extension.length < MAX_NAME_LENGTH / 2) ".$extension" else ""
        return named.take(MAX_NAME_LENGTH - suffix.length) + suffix
    }

    /** The usual extension for an accepted [contentType]. */
    fun extensionFor(contentType: String): String = when (contentType) {
        PDF -> "pdf"
        "image/jpeg" -> "jpg"
        else -> contentType.substringAfter('/', "bin")
    }

    private const val PDF = "application/pdf"

    private val FORBIDDEN_CHARS = setOf('/', '\\', '|')
}
