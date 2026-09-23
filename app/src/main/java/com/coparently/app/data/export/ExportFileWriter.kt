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
import com.coparently.app.domain.export.LineStyle
import com.coparently.app.domain.export.PageGeometry
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Writes a [CommunicationRecord] to a file on this phone and nowhere else (MON-3).
 *
 * **On the device, with no network.** The record holds a family's messages; it is not sent to a
 * server to be rendered, and the PDF is drawn with the platform's own `PdfDocument` rather than a
 * library that would have to be trusted with it.
 *
 * Files live in `cache/exports/`, which `res/xml/file_paths.xml` exposes to the share sheet and
 * nothing else exposes at all. Every write clears the previous export first: a court record left
 * lying in a cache directory is a copy nobody meant to keep.
 */
@Singleton
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The record as CSV. */
    suspend fun writeCsv(record: CommunicationRecord, labels: RecordLabels): ExportedFile =
        withContext(Dispatchers.IO) {
            val file = freshFile(record, "csv")
            file.writeText(CommunicationRecordCsv.render(record, labels), Charsets.UTF_8)
            ExportedFile(uriFor(file), MIME_CSV)
        }

    /** The record as an A4 PDF. */
    suspend fun writePdf(record: CommunicationRecord, labels: RecordLabels): ExportedFile =
        withContext(Dispatchers.IO) {
            val file = freshFile(record, "pdf")
            val geometry = PageGeometry()
            val paints = LineStyle.entries.associateWith { paintFor(it) }
            val pages = RecordLayout.paginate(RecordLayout.blocks(record, labels), geometry) { text, style ->
                paints.getValue(style).measureText(text)
            }
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, lines ->
                    val info = PdfDocument.PageInfo.Builder(
                        geometry.width.toInt(),
                        geometry.height.toInt(),
                        index + 1
                    ).create()
                    val page = document.startPage(info)
                    lines.forEach { line ->
                        page.canvas.drawText(line.text, line.x, line.baseline, paints.getValue(line.style))
                    }
                    page.canvas.drawText(
                        "${labels.title} · ${labels.page} ${index + 1} / ${pages.size}",
                        geometry.margin,
                        geometry.height - geometry.margin / 2,
                        paints.getValue(LineStyle.SMALL)
                    )
                    document.finishPage(page)
                }
                file.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
            ExportedFile(uriFor(file), MIME_PDF)
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
        const val MIME_CSV = "text/csv"
        const val MIME_PDF = "application/pdf"
    }
}
