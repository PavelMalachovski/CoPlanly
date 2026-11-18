package com.coparently.app.presentation.widget

import com.coparently.app.presentation.theme.ParentColor

/**
 * One line of the widget, and the parent whose colour it is drawn in.
 *
 * @property text What the line says.
 * @property parent The parent the line is about, whose text-grade tone colours it; null draws it
 *   in the theme's neutral text colour.
 */
data class WidgetLine(val text: String, val parent: ParentColor? = null)

/**
 * One of today's events as the widget lists it.
 *
 * @property time "14:00–15:30", or the start alone when the event has no end — the today card's
 *   own format.
 * @property title The event's title.
 * @property parent The event owner's colour, for the mark beside it.
 */
data class WidgetEventLine(val time: String, val title: String, val parent: ParentColor?)

/**
 * Everything the widget draws at one size, worded.
 *
 * Decided here rather than in the composable so that `TodayWidgetContent` reads no `Context` and
 * can be tested without one, and so that what the widget *says* is one function a unit test can
 * reach: which lines appear, how a parent is named, how many events fit and what the footer
 * admits about the rest.
 *
 * @property title "Today · Thu, 24 Sep".
 * @property custody Whose day it is.
 * @property windows Today's contact windows with the other parent.
 * @property handover The next handover: a day and a person, never an hour.
 * @property events The events that fit at this size.
 * @property footer What is not drawn, said in words: "+2 more", "3 events today",
 *   "Nothing scheduled.", or the signed-out sentence. Null when there is nothing to add.
 */
data class TodayWidgetLines(
    val title: String,
    val custody: WidgetLine? = null,
    val windows: List<WidgetLine> = emptyList(),
    val handover: WidgetLine? = null,
    val events: List<WidgetEventLine> = emptyList(),
    val footer: String? = null
)

/**
 * The widget worded for both of its sizes: [compact] lists no events and counts them instead,
 * [tall] lists up to [TALL_EVENT_ROWS].
 */
data class TodayWidgetContentState(val compact: TodayWidgetLines, val tall: TodayWidgetLines) {
    companion object {
        /** Event rows the tall widget draws before it says "+N more". */
        const val TALL_EVENT_ROWS = 4
    }
}

/**
 * The compact widget's lines at a large font scale: the date, whose day it is, and **one** more
 * line — the handover, else the first contact window, else the footer.
 *
 * A widget cannot measure its text, and at 150 % the compact size holds three lines: the fourth
 * was clipped through the middle ("1 Termin heute" cut in half under the handover). Dropping the
 * lowest-priority lines says less, but everything it says can be read. A tap opens Home, which
 * says the rest.
 */
fun TodayWidgetLines.compactAtLargeText(): TodayWidgetLines {
    val third = handover ?: windows.firstOrNull()
    return copy(
        windows = if (handover == null) windows.take(1) else emptyList(),
        events = emptyList(),
        footer = footer.takeIf { third == null }
    )
}
