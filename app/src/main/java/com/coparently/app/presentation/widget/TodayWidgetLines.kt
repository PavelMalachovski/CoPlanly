package com.coparently.app.presentation.widget

import android.content.Context
import android.text.format.DateFormat
import com.coparently.app.R
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime
import java.time.format.DateTimeFormatter

/**
 * One line of the widget, and the parent whose colour it is drawn in.
 *
 * @property text What the line says.
 * @property parent The parent the line is about, whose text-grade tone colours it; null draws it
 *   in the theme's neutral text colour.
 */
data class WidgetLine(val text: String, val parent: ParentColorChoice? = null)

/**
 * One of today's events as the widget lists it.
 *
 * @property time "14:00–15:30", or the start alone when the event has no end — the today card's
 *   own format.
 * @property title The event's title.
 * @property parent The event owner's colour, for the mark beside it.
 */
data class WidgetEventLine(val time: String, val title: String, val parent: ParentColorChoice?)

/**
 * Everything the widget draws at one size, worded.
 *
 * Decided here rather than in the composable so that [TodayWidgetContent] reads no `Context` and
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

/**
 * Words a [TodayWidgetModel] with the app's own strings.
 *
 * Every line the widget shares with the app is worded with the app's string for it — "Today with
 * Alex" is `home_handover_current`, the handover is the hero's, a contact window and an empty day
 * are the today card's — so the two cannot drift into saying one thing two ways. Parents are
 * named through [ParentNames], never by slot (CLAUDE.md, "The app never shows the words Mom or
 * Dad"), and coloured by the family's chosen palette.
 *
 * The language is the [context]'s configuration: on API 33 and above that is the per-app language
 * the parent chose; on API 32 and below AppCompat applies that choice to activities only, so the
 * widget follows the device language there — the same limit pushes have.
 */
object TodayWidgetText {

    /**
     * Both sizes of the widget.
     *
     * @param parents The names and colours to use, or null when none are known for this account;
     *   every parent is then named by the fallback labels.
     */
    fun contentState(context: Context, model: TodayWidgetModel, parents: Parents?): TodayWidgetContentState =
        TodayWidgetContentState(
            compact = lines(context, model, parents, maxEvents = 0),
            tall = lines(context, model, parents, maxEvents = TodayWidgetContentState.TALL_EVENT_ROWS)
        )

    /**
     * The widget at one size.
     *
     * @param maxEvents Event rows that fit. Zero lists none and counts them in the footer instead.
     */
    fun lines(context: Context, model: TodayWidgetModel, parents: Parents?, maxEvents: Int): TodayWidgetLines {
        val locale = context.resources.configuration.locales[0]
        val timeFormatter = timeFormatter(context)
        val title = context.getString(
            R.string.widget_today_title,
            model.today.format(localizedDate("MMMEEEd", locale))
        )
        if (!model.signedIn) {
            return TodayWidgetLines(title = title, footer = context.getString(R.string.widget_today_signed_out))
        }
        val known = parents ?: Parents(loaded = true)
        val names = ParentNames(
            parents = known,
            youFallback = context.getString(R.string.parent_label_you),
            coParentFallback = context.getString(R.string.parent_label_coparent),
            unknownFallback = context.getString(R.string.parent_label_unknown)
        )
        val palette = known.palette
        val shown = model.events.take(maxEvents)
        return TodayWidgetLines(
            title = title,
            custody = model.dayParent?.let { slot ->
                WidgetLine(
                    context.getString(R.string.home_handover_current, names.labelFor(slot)),
                    palette.of(slot)
                )
            },
            windows = model.contactWindows.map { window ->
                WidgetLine(
                    context.getString(
                        R.string.calendar_agenda_contact_window,
                        window.start.format(timeFormatter),
                        window.end.format(timeFormatter),
                        names.labelFor(window.parent)
                    ),
                    palette.of(window.parent)
                )
            },
            handover = model.handover?.let { handover ->
                WidgetLine(
                    handoverText(context, handover, names.labelFor(handover.toParent)),
                    palette.of(handover.toParent)
                )
            },
            events = shown.map { event ->
                WidgetEventLine(
                    time = timeOf(context, event, timeFormatter),
                    title = event.title,
                    parent = palette.of(event.parentOwner)
                )
            },
            footer = footer(context, total = model.events.size, shown = shown.size, listsEvents = maxEvents > 0)
        )
    }

    /** The hero's wording: today, tomorrow, or in N days. */
    private fun handoverText(context: Context, handover: HandoverInfo, to: String): String = when (handover.daysUntil) {
        0L -> context.getString(R.string.home_handover_hero_today, to)
        1L -> context.getString(R.string.home_handover_hero_tomorrow, to)
        else -> {
            val days = handover.daysUntil.toInt()
            context.resources.getQuantityString(R.plurals.home_handover_hero_in_days, days, to, days)
        }
    }

    /**
     * The reader's clock, read from the device here: a widget is drawn without `MainActivity`
     * having run, so [com.coparently.app.utils.ClockFormat] may not have followed the setting.
     */
    private fun timeFormatter(context: Context): DateTimeFormatter =
        shortTime(context.resources.configuration.locales[0], DateFormat.is24HourFormat(context))

    /**
     * "14:00–15:30", just the start when the event has no end, or "All day" ([AllDayEvent]) —
     * the today card's format.
     */
    private fun timeOf(context: Context, event: Event, timeFormatter: DateTimeFormatter): String {
        if (AllDayEvent.isAllDay(event)) return context.getString(R.string.event_all_day)
        val start = event.startDateTime.format(timeFormatter)
        val end = event.endDateTime?.format(timeFormatter) ?: return start
        return context.getString(R.string.calendar_agenda_time_range, start, end)
    }

    /**
     * What the rows leave out. An empty day says so, as the today card does; a size that lists no
     * events counts them, and one that lists some says how many it could not.
     */
    private fun footer(context: Context, total: Int, shown: Int, listsEvents: Boolean): String? = when {
        total == 0 -> context.getString(R.string.calendar_agenda_empty)
        !listsEvents -> context.resources.getQuantityString(R.plurals.widget_today_events_count, total, total)
        total > shown -> (total - shown).let { hidden ->
            context.resources.getQuantityString(R.plurals.widget_today_more, hidden, hidden)
        }
        else -> null
    }
}
