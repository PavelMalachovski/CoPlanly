package com.coparently.app.presentation.widget

import kotlinx.serialization.Serializable

/**
 * Seven days of the Today widget, worded and coloured, as the iPhone's widget extension draws them
 * (iPhone phase 4 part D).
 *
 * The app writes it to the App Group (`<group container>/widget/today.json`) through `StoredJson`,
 * and the extension, a process of its own with no database and no wording, only picks the day it is
 * and draws its lines. So the snapshot holds lines and colours only: never a record, a uid, a token
 * or an address, and never a private event of the co-parent, which [TodayWidgetModel] already drops.
 *
 * @property version The shape of this document; the extension draws nothing it cannot read.
 * @property writtenAtMillis When the app wrote it, epoch milliseconds.
 * @property language The BCP 47 tag the lines were worded in.
 * @property signedIn False when nobody is signed in; [days] then holds one day with the signed-out
 *   line and nothing else.
 * @property days From the day it was written, one per day, [WidgetSnapshotBuilder.DAYS] of them
 *   when signed in.
 * @property staleLine What the widget says once every day in [days] has passed: the app has not run
 *   for a week, and the lines would be about days gone.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = 1,
    val writtenAtMillis: Long,
    val language: String,
    val signedIn: Boolean,
    val days: List<WidgetDay>,
    val staleLine: String
)

/**
 * One day of the widget at each size it is drawn at.
 *
 * @property date The day, as ISO text ("2026-11-18").
 * @property compact The small widget: no event rows, a count instead.
 * @property compactLarge The small widget at the accessibility text sizes: [compact] after
 *   [compactAtLargeText], decided here so the drop rule is not written again in Swift.
 * @property tall The medium widget, listing up to [TodayWidgetContentState.TALL_EVENT_ROWS] events.
 */
@Serializable
data class WidgetDay(
    val date: String,
    val compact: WidgetDayLines,
    val compactLarge: WidgetDayLines,
    val tall: WidgetDayLines
)

/**
 * [TodayWidgetLines] with every colour resolved, so the extension never computes one.
 *
 * @property title "Today · Thu, 19 Nov".
 * @property custody Whose day it is.
 * @property windows The day's contact windows with the other parent.
 * @property handover The next handover: a day and a person, never an hour.
 * @property events The events that fit at this size.
 * @property footer What is not drawn, said in words, or null.
 */
@Serializable
data class WidgetDayLines(
    val title: String,
    val custody: WidgetSnapshotLine?,
    val windows: List<WidgetSnapshotLine>,
    val handover: WidgetSnapshotLine?,
    val events: List<WidgetSnapshotEvent>,
    val footer: String?
)

/**
 * One line of text and the colour it is drawn in.
 *
 * @property text What the line says.
 * @property colour The parent's text-grade tones, or null for the theme's neutral text colour.
 */
@Serializable
data class WidgetSnapshotLine(val text: String, val colour: WidgetColour?)

/**
 * One event row.
 *
 * @property time "14:00–15:30", the start alone, or "All day".
 * @property title The event's title.
 * @property mark The owner's full-strength tones for the mark beside it, or null.
 */
@Serializable
data class WidgetSnapshotEvent(val time: String, val title: String, val mark: WidgetColour?)

/**
 * A colour in each of the widget's two appearances, as "#RRGGBB".
 *
 * @property light Drawn on the light background.
 * @property dark Drawn on the dark background.
 */
@Serializable
data class WidgetColour(val light: String, val dark: String)
