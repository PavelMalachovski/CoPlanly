package com.coparently.app.data.export

import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.RecordLabels

/**
 * A file the share sheet can hand to another app.
 *
 * @property uri Where the platform saved it, as text: on Android a `content://` URI from the app's
 *   `FileProvider`, readable by whoever the parent shares it with and by nobody else (the app's
 *   `ExportedFile.contentUri` reads it back as a `Uri`).
 * @property mimeType The file's type, for the share sheet.
 */
data class ExportedFile(val uri: String, val mimeType: String)

/**
 * Renders a communication record and saves it for sharing, on this phone and nowhere else (MON-3,
 * CLAUDE.md item 26; MON-24 phase 2, Task 10: the export's ViewModel asks it, so it can live in
 * `:shared-ui`).
 *
 * Rendering and saving are separate so the export flow can hash precisely what it will save
 * (MON-16): the SHA-256 the server registers must be of [render]'s bytes and no others, and [save]
 * writes those same bytes. On Android it is the app's `ExportFileWriter` (the PDF drawn with
 * `PdfDocument`); the iPhone has none yet.
 */
interface RecordFileWriter {

    /**
     * Whether this phone draws and saves a record at all. The export screen offers its formats only
     * when it does (design item 8): the app's `ExportFileWriter` on Android always does; the iPhone's
     * stand-in does not yet (MON-24 phase 2, slice 11g).
     */
    val available: Boolean get() = true

    /** The file's exact bytes, before anything is written. */
    suspend fun render(record: CommunicationRecord, labels: RecordLabels, format: ExportFormat): ByteArray

    /** Writes [bytes] — already rendered, and already hashed if they were registered — for sharing. */
    suspend fun save(bytes: ByteArray, record: CommunicationRecord, format: ExportFormat): ExportedFile
}
