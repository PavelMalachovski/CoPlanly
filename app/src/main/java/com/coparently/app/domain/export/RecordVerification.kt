package com.coparently.app.domain.export

import java.security.MessageDigest

/**
 * The two files an export can be.
 *
 * @property wireName How `reserveExportRecordId` names the format (`functions/export-receipts.js`).
 * @property extension The file name's extension.
 * @property mimeType What the share sheet is told.
 */
enum class ExportFormat(val wireName: String, val extension: String, val mimeType: String) {
    CSV("csv", "csv", "text/csv"),
    PDF("pdf", "pdf", "application/pdf")
}

/**
 * What an exported file may say about checking it (MON-16).
 *
 * **Never more than is true** (design item 8). A file says [Registered] only when the server holds
 * the SHA-256 of exactly its bytes under [Registered.recordId]; everything else is [Unregistered],
 * and the file says so on its face. The id has to be *inside* the bytes it vouches for, so it is
 * reserved before the file is rendered, and a file whose hash then fails to register is rendered
 * again as [Unregistered] — see `ExportViewModel` and `docs/DESIGN-court-record.md` §10.
 */
sealed interface RecordVerification {

    /**
     * Registered: the server holds this file's hash.
     *
     * @property recordId The canonical id, 16 Crockford base-32 characters; printed grouped by
     *   [RecordId.display].
     * @property verifyUrl The verification page, or blank while none is hosted — then the file
     *   prints the id without an address rather than an address that does not resolve.
     */
    data class Registered(val recordId: String, val verifyUrl: String) : RecordVerification

    /** Not registered: nobody can check this file for changes, and it says so. */
    data object Unregistered : RecordVerification
}

/** Record ids as a person reads them. */
object RecordId {

    private const val GROUP = 4

    /** `7K3Q0ABCDEFGHJKM` → `7K3Q-0ABC-DEFG-HJKM`: easier to read aloud and to retype. */
    fun display(recordId: String): String = recordId.chunked(GROUP).joinToString("-")
}

/** The fingerprint the server registers and the verification page recomputes. */
object ExportFingerprint {

    private const val BYTE_MASK = 0xff
    private const val HEX_RADIX = 16

    /** SHA-256 of [bytes], as 64 lowercase hex characters — the form `registerExportReceipt` accepts. */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }
}
