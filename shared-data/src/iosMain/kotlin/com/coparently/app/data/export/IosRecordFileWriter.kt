@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.export

import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.CommunicationRecordCsv
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.LineStyle
import com.coparently.app.domain.export.PageGeometry
import com.coparently.app.domain.export.PlacedLine
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordLayout
import com.coparently.app.domain.export.RecordVerificationLayout
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes
import platform.posix.memcpy

/**
 * [RecordFileWriter] on the iPhone (MON-24 phase 3, part C, Task 12), the app's `ExportFileWriter`'s
 * twin: the record rendered and saved **on this phone and nowhere else** (CLAUDE.md item 26).
 *
 * - **CSV** is [CommunicationRecordCsv]'s text as UTF-8, byte for byte what Android writes.
 * - **PDF** is [RecordLayout]'s pages, in the same order, each with [RecordVerificationLayout]'s footer
 *   (the page number and the record id, or "not registered"), drawn with `UIGraphicsPDFRenderer` on
 *   A4 (595 × 842 pt, [PageGeometry]'s default). The sizes and weights are [LineStyle]'s, in the
 *   system font, regular or bold, in black, as Android draws them in its default typeface; the layout
 *   measures with the same fonts it draws with. A line is placed by its baseline, as Android's
 *   `drawText` places it.
 *
 * [render] and [save] are separate so the export flow hashes precisely the bytes it saves (MON-16):
 * [save] writes the bytes it is handed and nothing else.
 *
 * Saved files live in `exports/` under the app's cache directory (`AppDirectories.cache`), which
 * sign-out, an account switch and deletion wipe (`IosAccountTraces.CACHE_DIRECTORIES`), and every
 * save clears the previous export first: a court record left in a cache is a copy nobody meant to
 * keep. [ExportedFile.uri] is the file's absolute path, which the share sheet takes.
 *
 * @param caches The app's cache directory (`AppDirectories.cache`; a test names its own).
 */
class IosRecordFileWriter(private val caches: String) : RecordFileWriter {

    override val available: Boolean = true

    override suspend fun render(record: CommunicationRecord, labels: RecordLabels, format: ExportFormat): ByteArray =
        withContext(Dispatchers.IO) {
            when (format) {
                ExportFormat.CSV -> CommunicationRecordCsv.render(record, labels).encodeToByteArray()
                ExportFormat.PDF -> drawPdf(record, labels)
            }
        }

    override suspend fun save(bytes: ByteArray, record: CommunicationRecord, format: ExportFormat): ExportedFile =
        withContext(Dispatchers.IO) {
            val path = freshFile(record, format.extension)
            SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
            ExportedFile(path, format.mimeType)
        }

    /**
     * The record's pages as the PDF draws them, each with its footer: the body lines [RecordLayout]
     * placed, then the footer's. Internal for the tests, which count them.
     */
    internal fun pages(record: CommunicationRecord, labels: RecordLabels): List<List<PlacedLine>> {
        val geometry = RecordVerificationLayout.withFooterRoom(record, labels, PageGeometry(), ::measure)
        val pages = RecordLayout.paginate(RecordLayout.blocks(record, labels), geometry, ::measure)
        return pages.mapIndexed { index, lines ->
            lines + RecordVerificationLayout.footer(record, labels, index + 1, pages.size, geometry, ::measure)
        }
    }

    /** The record as an A4 PDF, every page carrying the footer that names its record id. */
    private fun drawPdf(record: CommunicationRecord, labels: RecordLabels): ByteArray {
        val geometry = PageGeometry()
        val pages = pages(record, labels)
        val bounds = CGRectMake(0.0, 0.0, geometry.width.toDouble(), geometry.height.toDouble())
        val data = UIGraphicsPDFRenderer(bounds = bounds).PDFDataWithActions { context ->
            pages.forEach { lines ->
                context?.beginPage()
                lines.forEach { line ->
                    val font = fontOf(line.style)
                    // UIKit draws a string from the top of its line; Android's `drawText` from the
                    // baseline, which is where the layout put it.
                    val top = line.baseline.toDouble() - font.ascender
                    NSString.create(string = line.text)
                        .drawAtPoint(CGPointMake(line.x.toDouble(), top), withAttributes = attributes(line.style))
                }
            }
        }
        return data.toByteArray()
    }

    private fun measure(text: String, style: LineStyle): Float =
        NSString.create(string = text).sizeWithAttributes(attributes(style)).useContents { width.toFloat() }

    private fun freshFile(record: CommunicationRecord, extension: String): String {
        val directory = "$caches/$EXPORT_DIRECTORY"
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(directory, error = null)
        val created = manager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        if (!created) throw IOException("Could not make the exports folder")
        return "$directory/coplanly-record-${record.from}-${record.to}.$extension"
    }

    /** Public for `platformDataModule` and the tests. */
    companion object {
        /** The folder under the cache directory that holds the last export (`IosAccountTraces` wipes it). */
        const val EXPORT_DIRECTORY = "exports"

        private fun fontOf(style: LineStyle): UIFont {
            val size = style.size.toDouble()
            return if (style.bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
        }

        private fun attributes(style: LineStyle): Map<Any?, Any?> = mapOf(
            NSFontAttributeName to fontOf(style),
            NSForegroundColorAttributeName to UIColor.blackColor
        )

        private fun NSData.toByteArray(): ByteArray {
            val size = length.toInt()
            val bytes = ByteArray(size)
            if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
            return bytes
        }
    }
}
