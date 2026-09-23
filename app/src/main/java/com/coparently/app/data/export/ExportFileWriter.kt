package com.coparently.app.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.CommunicationRecordCsv
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.LineStyle
import com.coparently.app.domain.export.PageGeometry
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordLayout
import com.coparently.app.domain.export.RecordVerificationLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file the share sheet can hand to another app.
 *
 * @property uri A `content://` URI from this app's `FileProvider`, readable by whoever the parent
 *   shares it with and by nobody else.
 */
data class ExportedFile(val uri: Uri, val mimeType: String)

/**
 * Renders a [CommunicationRecord] and writes it to a file on this phone and nowhere else (MON-3).
 *
 * **On the device, with no network.** The record holds a family's messages; it is not sent to a
 * server to be rendered, and the PDF is drawn with the platform's own `PdfDocument` rather than a
 * library that would have to be trusted with it. What does reach the server (MON-16) is a SHA-256
 * of the bytes [render] returns, never the bytes.
 *
 * Files live in `cache/exports/`, which `res/xml/file_paths.xml` exposes to the share sheet and
 * nothing else exposes at all. Every write clears the previous export first: a court record left
 * lying in a cache directory is a copy nobody meant to keep.
 */
@Singleton
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * The file's exact bytes, before anything is written to disk.
     *
     * Rendering and saving are separate so the export flow can hash precisely what it will save
     * (MON-16): the SHA-256 the server registers must be of these bytes and no others.
     */
    suspend fun render(record: CommunicationRecord, labels: RecordLabels, format: ExportFormat): ByteArray =
        withContext(Dispatchers.IO) {
            when (format) {
                ExportFormat.CSV -> CommunicationRecordCsv.render(record, labels).toByteArray(Charsets.UTF_8)
                ExportFormat.PDF -> drawPdf(record, labels)
            }
        }

    /** Writes [bytes] — already rendered, and already hashed if they were registered — for sharing. */
    suspend fun save(bytes: ByteArray, record: CommunicationRecord, format: ExportFormat): ExportedFile =
        withContext(Dispatchers.IO) {
            val file = freshFile(record, format.extension)
            file.writeBytes(bytes)
            ExportedFile(uriFor(file), format.mimeType)
        }

    /** The record as an A4 PDF, every page carrying the footer that names its record id. */
    private fun drawPdf(record: CommunicationRecord, labels: RecordLabels): ByteArray {
        val paints = LineStyle.entries.associateWith { paintFor(it) }
        val measure: (String, LineStyle) -> Float = { text, style -> paints.getValue(style).measureText(text) }
        val geometry = RecordVerificationLayout.withFooterRoom(record, labels, PageGeometry(), measure)
        val pages = RecordLayout.paginate(RecordLayout.blocks(record, labels), geometry, measure)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val info = PdfDocument.PageInfo.Builder(
                    geometry.width.toInt(),
                    geometry.height.toInt(),
                    index + 1
                ).create()
                val page = document.startPage(info)
                val footer = RecordVerificationLayout.footer(record, labels, index + 1, pages.size, geometry, measure)
                (lines + footer).forEach { line ->
                    page.canvas.drawText(line.text, line.x, line.baseline, paints.getValue(line.style))
                }
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun freshFile(record: CommunicationRecord, extension: String): File {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY)
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, "coplanly-record-${record.from}-${record.to}.$extension")
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun paintFor(style: LineStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = style.size
        typeface = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
    }
}
