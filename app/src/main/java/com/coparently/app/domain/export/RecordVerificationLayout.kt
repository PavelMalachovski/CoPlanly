package com.coparently.app.domain.export

/**
 * How a printed record says whether it can be verified (MON-16): the block under the statement on
 * the first page, and the footer on every page.
 *
 * Pure, like [RecordLayout], which calls it: the renderer hands in a `measure` function and draws
 * what comes back. A registered file prints its record id — and the verification page's address,
 * when one is hosted — on every page, so a page separated from the rest still names its record; a
 * file that could not be registered says so in the same places, never leaving the space blank for a
 * reader to guess at.
 */
object RecordVerificationLayout {

    private const val WIDEST_PAGE = 9999

    /** The record id, where and how to check the file — or that it cannot be checked (MON-16). */
    fun blocks(record: CommunicationRecord, words: VerificationLabels): List<RecordBlock> =
        when (val verification = record.verification) {
            is RecordVerification.Registered -> listOfNotNull(
                RecordBlock(
                    "${words.recordId}: ${RecordId.display(verification.recordId)}",
                    LineStyle.EMPHASIS,
                    spaceBefore = RecordLayout.ITEM_GAP
                ),
                verification.verifyUrl.takeIf { it.isNotBlank() }?.let {
                    RecordBlock("${words.verifyAt}: $it", LineStyle.SMALL)
                },
                RecordBlock(
                    if (verification.verifyUrl.isBlank()) words.instructionNoUrl else words.instruction,
                    LineStyle.SMALL
                )
            )
            RecordVerification.Unregistered ->
                listOf(RecordBlock(words.notRegistered, LineStyle.EMPHASIS, spaceBefore = RecordLayout.ITEM_GAP))
        }

    /**
     * What every page's footer says: the title and page number, then the record id and the
     * verification address — or "not registered" — so a single page separated from the rest
     * still says which record it belongs to and whether it can be checked.
     */
    fun footerTexts(record: CommunicationRecord, labels: RecordLabels, page: Int, pageCount: Int): List<String> {
        val words = labels.verification
        val verification = when (val state = record.verification) {
            is RecordVerification.Registered -> listOfNotNull(
                "${words.recordId}: ${RecordId.display(state.recordId)}",
                state.verifyUrl.takeIf { it.isNotBlank() }?.let { "${words.verifyAt}: $it" }
            ).joinToString(" · ")
            RecordVerification.Unregistered -> words.notRegisteredShort
        }
        return listOf("${labels.title} · ${labels.page} $page / $pageCount", verification)
    }

    /**
     * The footer's lines at their place on a page, wrapped to the text width and stacked upwards
     * from the bottom margin.
     */
    // Each argument is one input of the footer; bundling them would only rename this list.
    @Suppress("LongParameterList")
    fun footer(
        record: CommunicationRecord,
        labels: RecordLabels,
        page: Int,
        pageCount: Int,
        geometry: PageGeometry,
        measure: (String, LineStyle) -> Float
    ): List<PlacedLine> {
        val style = LineStyle.SMALL
        val width = geometry.width - 2 * geometry.margin
        val lines = footerTexts(record, labels, page, pageCount).flatMap { text ->
            RecordLayout.wrap(text, width) { measure(it, style) }
        }
        val lastBaseline = geometry.height - geometry.margin / 2
        return lines.mapIndexed { index, text ->
            PlacedLine(text, style, geometry.margin, lastBaseline - (lines.size - 1 - index) * style.lineHeight)
        }
    }

    /**
     * [geometry] with room kept for the footer [record] needs, so body text never runs into it. Measured
     * with the widest page number the footer could print.
     */
    fun withFooterRoom(
        record: CommunicationRecord,
        labels: RecordLabels,
        geometry: PageGeometry,
        measure: (String, LineStyle) -> Float
    ): PageGeometry {
        val lines = footer(record, labels, WIDEST_PAGE, WIDEST_PAGE, geometry, measure).size
        // The footer sits in the bottom half-margin and grows upwards, so a full line height per
        // line above the body's limit keeps the two apart with room to spare.
        val needed = lines * LineStyle.SMALL.lineHeight
        return geometry.copy(footerHeight = maxOf(geometry.footerHeight, needed))
    }
}
