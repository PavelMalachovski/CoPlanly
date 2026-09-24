package com.coparently.app.domain.export

private const val TITLE_SIZE = 18f
private const val HEADING_SIZE = 14f
private const val SUBHEADING_SIZE = 11.5f
private const val BODY_SIZE = 10f
private const val SMALL_SIZE = 8.5f
private const val LEADING = 1.4f
private const val A4_WIDTH = 595f
private const val A4_HEIGHT = 842f
private const val MARGIN = 48f
private const val FOOTER = 28f
private const val INDENT = 14f

/**
 * The kinds of line a printed record has.
 *
 * @property size The font size in points.
 * @property bold Whether the renderer sets it in the bold face.
 */
enum class LineStyle(val size: Float, val bold: Boolean) {
    TITLE(TITLE_SIZE, true),
    HEADING(HEADING_SIZE, true),
    SUBHEADING(SUBHEADING_SIZE, true),
    EMPHASIS(BODY_SIZE, true),
    BODY(BODY_SIZE, false),
    SMALL(SMALL_SIZE, false);

    /** The distance from one baseline to the next. */
    val lineHeight: Float get() = size * LEADING
}

/**
 * One paragraph of the printed record, before it is wrapped.
 *
 * @property indent Nesting depth; each level moves the text right by [PageGeometry.indentStep].
 * @property spaceBefore Extra points above the block, which is how sections separate.
 */
data class RecordBlock(
    val text: String,
    val style: LineStyle,
    val indent: Int = 0,
    val spaceBefore: Float = 0f
)

/** One line of text at its place on a page. */
data class PlacedLine(val text: String, val style: LineStyle, val x: Float, val baseline: Float)

/**
 * The printable area. A4 in points by default, which is what a court in the Czech Republic or
 * Germany prints on.
 */
data class PageGeometry(
    val width: Float = A4_WIDTH,
    val height: Float = A4_HEIGHT,
    val margin: Float = MARGIN,
    val footerHeight: Float = FOOTER,
    val indentStep: Float = INDENT
)

/**
 * Lays a [CommunicationRecord] out as pages of text (MON-3).
 *
 * Pure: the renderer hands in a `measure` function backed by its `Paint`, and gets back every line
 * at its position. That keeps the decisions a reader would notice — what is printed, in what
 * order, where a page breaks — testable on the JVM, and leaves `android.graphics.pdf` to draw.
 *
 * The statement is the first thing on the first page, exactly as in the CSV, and it is never
 * wrapped into anything smaller than body text.
 */
object RecordLayout {

    /** The gap above a section's heading; shared with [RecordPlanLayout]. */
    internal const val SECTION_GAP = 14f

    /** The gap above an item; shared with [RecordVerificationLayout] so the header reads as one. */
    internal const val ITEM_GAP = 8f

    /** The gap above a line inside an item; shared with [RecordPlanLayout]. */
    internal const val SMALL_GAP = 3f

    /**
     * The record as paragraphs, in reading order. The parenting plan, when there is one, comes
     * last: it is not bound to the period the three sections before it cover.
     */
    fun blocks(record: CommunicationRecord, labels: RecordLabels): List<RecordBlock> =
        header(record, labels) + events(record, labels) + messages(record, labels) + expenses(record, labels) +
            record.plan?.let { RecordPlanLayout.blocks(it, record.zone, labels.plan) }.orEmpty()

    /**
     * Wraps [blocks] to the page and breaks them across pages.
     *
     * @param measure The width of a string in points, in a style — the renderer's `Paint`.
     * @return One list of lines per page; never empty, so even an empty record has a first page.
     */
    fun paginate(
        blocks: List<RecordBlock>,
        geometry: PageGeometry,
        measure: (String, LineStyle) -> Float
    ): List<List<PlacedLine>> {
        val pages = mutableListOf<List<PlacedLine>>()
        var current = mutableListOf<PlacedLine>()
        val top = geometry.margin
        val bottom = geometry.height - geometry.margin - geometry.footerHeight
        var y = top
        for (block in blocks) {
            val x = geometry.margin + block.indent * geometry.indentStep
            val width = geometry.width - geometry.margin - x
            // A gap at the top of a page is space nobody asked for.
            if (current.isNotEmpty()) y += block.spaceBefore
            for (line in wrap(block.text, width) { measure(it, block.style) }) {
                if (y + block.style.lineHeight > bottom && current.isNotEmpty()) {
                    pages += current
                    current = mutableListOf()
                    y = top
                }
                y += block.style.lineHeight
                current += PlacedLine(line, block.style, x, y)
            }
        }
        pages += current
        return pages
    }

    /**
     * Breaks [text] into lines no wider than [width], at spaces where it can and inside a word
     * where it must — an id or a link longer than the page is still printed whole.
     */
    fun wrap(text: String, width: Float, measure: (String) -> Float): List<String> =
        text.split('\n').flatMap { paragraph -> wrapParagraph(paragraph, width, measure) }

    private fun wrapParagraph(paragraph: String, width: Float, measure: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in paragraph.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate) <= width) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
                while (current.length > 1 && measure(current) > width) {
                    val fits = longestFittingPrefix(current, width, measure)
                    lines += current.take(fits)
                    current = current.drop(fits)
                }
            }
        }
        lines += current
        return lines
    }

    private fun longestFittingPrefix(word: String, width: Float, measure: (String) -> Float): Int {
        var fits = 1
        while (fits < word.length && measure(word.take(fits + 1)) <= width) fits++
        return fits
    }

    private fun header(record: CommunicationRecord, labels: RecordLabels): List<RecordBlock> {
        val statement = labels.statement.map { RecordBlock(it, LineStyle.BODY, spaceBefore = SMALL_GAP) }
        val meta = listOf(
            "${labels.period}: ${RecordFormat.date(record.from)} – ${RecordFormat.date(record.to)}",
            "${labels.generated}: ${RecordFormat.instant(record.generatedAtMillis, record.zone)}",
            "${labels.timeZone}: ${record.zone.id}",
            "${labels.parents}: ${record.parents.joinToString(", ")}"
        ).mapIndexed { index, line ->
            RecordBlock(line, LineStyle.SMALL, spaceBefore = if (index == 0) ITEM_GAP else 0f)
        }
        val warning = if (record.complete) {
            emptyList()
        } else {
            listOf(RecordBlock(labels.incomplete, LineStyle.EMPHASIS, spaceBefore = ITEM_GAP))
        }
        return listOf(RecordBlock(labels.title, LineStyle.TITLE)) + statement + meta +
            RecordVerificationLayout.blocks(record, labels.verification) + warning
    }

    private fun events(record: CommunicationRecord, labels: RecordLabels): List<RecordBlock> {
        val heading = RecordBlock(labels.sectionEvents, LineStyle.HEADING, spaceBefore = SECTION_GAP)
        if (record.events.isEmpty()) return listOf(heading, RecordBlock(labels.nothingInPeriod, LineStyle.BODY))
        val columns = labels.columns
        return listOf(heading) + record.events.flatMap { event ->
            val latest = event.revisions.last()
            listOf(
                RecordBlock(latest.facts.title, LineStyle.SUBHEADING, spaceBefore = ITEM_GAP),
                RecordBlock("${columns.item}: ${event.eventId}", LineStyle.SMALL)
            ) + event.revisions.flatMap { revision ->
                val title = listOf(
                    "${labels.revision} ${revision.number}",
                    RecordFormat.action(revision.kind, labels.actions),
                    revision.byName
                ).filter { it.isNotBlank() }.joinToString(" — ")
                listOf(RecordBlock(title, LineStyle.EMPHASIS, indent = 1, spaceBefore = SMALL_GAP)) +
                    fields(
                        columns.deviceTime to revision.deviceTimeMillis?.let {
                            RecordFormat.instant(it, record.zone)
                        }.orEmpty(),
                        columns.serverTime to RecordFormat.serverTime(revision, record.zone, labels),
                        columns.text to revision.facts.title,
                        columns.starts to RecordFormat.wallClock(revision.facts.start),
                        columns.ends to RecordFormat.wallClock(revision.facts.end),
                        columns.parent to revision.parentName,
                        columns.notes to RecordFormat.eventNotes(revision.facts)
                    )
            }
        }
    }

    private fun messages(record: CommunicationRecord, labels: RecordLabels): List<RecordBlock> {
        val heading = RecordBlock(labels.sectionMessages, LineStyle.HEADING, spaceBefore = SECTION_GAP)
        if (record.messages.isEmpty()) return listOf(heading, RecordBlock(labels.nothingInPeriod, LineStyle.BODY))
        return listOf(heading) + record.messages.flatMap { message ->
            val status = if (message.delivered) "" else " — ${labels.actions.notSent}"
            listOf(
                RecordBlock(
                    "${RecordFormat.instant(message.sentAtMillis, record.zone)} — ${message.senderName}$status",
                    LineStyle.SMALL,
                    spaceBefore = ITEM_GAP
                ),
                RecordBlock(message.text, LineStyle.BODY, indent = 1)
            ) + RecordFormat.planCitation(message, labels.plan).takeIf { it.isNotEmpty() }?.let {
                listOf(RecordBlock(it, LineStyle.SMALL, indent = 1))
            }.orEmpty()
        }
    }

    private fun expenses(record: CommunicationRecord, labels: RecordLabels): List<RecordBlock> {
        val heading = RecordBlock(labels.sectionExpenses, LineStyle.HEADING, spaceBefore = SECTION_GAP)
        if (record.expenses.isEmpty()) return listOf(heading, RecordBlock(labels.nothingInPeriod, LineStyle.BODY))
        val columns = labels.columns
        return listOf(heading) + record.expenses.flatMap { expense ->
            val line = "${RecordFormat.date(expense.date)} — ${expense.title} — " +
                "${RecordFormat.amount(expense.amount)} ${expense.currency}"
            listOf(RecordBlock(line, LineStyle.EMPHASIS, spaceBefore = ITEM_GAP)) + fields(
                columns.parent to expense.paidByName,
                columns.by to expense.recordedByName,
                columns.notes to expense.notes,
                columns.item to expense.expenseId
            )
        }
    }

    /** "Label: value" lines for the values that are not blank, one level in. */
    private fun fields(vararg pairs: Pair<String, String>): List<RecordBlock> = pairs
        .filter { (_, value) -> value.isNotBlank() }
        .map { (label, value) -> RecordBlock("$label: $value", LineStyle.SMALL, indent = 1) }
}
