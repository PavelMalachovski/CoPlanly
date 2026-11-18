package com.coparently.app.presentation.widget

import android.content.Context
import android.text.format.DateFormat
import com.coparently.app.R
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.utils.format
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

/**
 * Words a [TodayWidgetModel] with the app's own strings.
 *
 * Every line the widget shares with the app is worded with the app's string for it — "Today with
 * Alex" is `home_handover_current`, the handover is the hero's, a contact window and an empty day
 * are the today card's — so the two cannot drift into saying one thing two ways. Parents are
 * named through [ParentNames], never by slot (CLAUDE.md, "The app never shows the words Mom or
 * Dad"), and coloured by the family's chosen palette.
 *
 * The language is the [context]'s configuration. The widget hands it a context in the language the
 * app speaks (`inAppLanguage()`: the per-app choice, else the phone's first language when the app
 * ships it, else English), as pushes are worded; a test hands it the language it checks.
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
            model.today.toJavaLocalDate().format(localizedDate("MMMEEEd", locale))
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
                    if (names.isReader(slot)) {
                        context.getString(R.string.home_handover_current_you)
                    } else {
                        context.getString(R.string.home_handover_current, names.labelFor(slot))
                    },
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
                    handoverText(context, handover, names),
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

    /** The hero's wording: today, tomorrow, or in N days — to "you" for the reader, as Home says it. */
    private fun handoverText(context: Context, handover: HandoverInfo, names: ParentNames): String {
        val days = handover.daysUntil.toInt()
        val to = names.labelFor(handover.toParent)
        val toReader = names.isReader(handover.toParent)
        return when {
            toReader && handover.daysUntil == 0L -> context.getString(R.string.home_handover_hero_today_you)
            toReader && handover.daysUntil == 1L -> context.getString(R.string.home_handover_hero_tomorrow_you)
            toReader -> context.resources.getQuantityString(R.plurals.home_handover_hero_in_days_you, days, days)
            handover.daysUntil == 0L -> context.getString(R.string.home_handover_hero_today, to)
            handover.daysUntil == 1L -> context.getString(R.string.home_handover_hero_tomorrow, to)
            else -> context.resources.getQuantityString(R.plurals.home_handover_hero_in_days, days, to, days)
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
