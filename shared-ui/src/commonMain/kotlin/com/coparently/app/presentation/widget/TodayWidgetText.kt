package com.coparently.app.presentation.widget

import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.pushwording.WordingStrings

/**
 * Words a [TodayWidgetModel] with the app's own strings.
 *
 * Every line the widget shares with the app is worded with the app's string for it — "Today with
 * Alex" is `home_handover_current`, the handover is the hero's, a contact window and an empty day
 * are the today card's — so the two cannot drift into saying one thing two ways. Parents are
 * named through [ParentNames], never by slot (CLAUDE.md, "The app never shows the words Mom or
 * Dad"), and coloured by the family's chosen palette.
 *
 * The strings are read by key through `:push-wording`'s [WordingStrings], as a notification's are,
 * so the iPhone can word the widget from the same keys (iPhone phase 4 part D): every key named here
 * is listed in `push-wording/push-keys.txt`, which `tools/test/push-keys.test.js` holds to this
 * file. The language is the one [WordingStrings] and [WidgetClock] were made in. Android hands them a
 * context in the language the app speaks (`inAppLanguage()`: the per-app choice, else the phone's
 * first language when the app ships it, else English), as pushes are worded; a test hands them the
 * language it checks.
 */
object TodayWidgetText {

    /**
     * Both sizes of the widget.
     *
     * @param parents The names and colours to use, or null when none are known for this account;
     *   every parent is then named by the fallback labels.
     */
    fun contentState(
        strings: WordingStrings,
        clock: WidgetClock,
        model: TodayWidgetModel,
        parents: Parents?
    ): TodayWidgetContentState =
        TodayWidgetContentState(
            compact = lines(strings, clock, model, parents, maxEvents = 0),
            tall = lines(strings, clock, model, parents, maxEvents = TodayWidgetContentState.TALL_EVENT_ROWS)
        )

    /**
     * The widget at one size.
     *
     * @param maxEvents Event rows that fit. Zero lists none and counts them in the footer instead.
     */
    fun lines(
        strings: WordingStrings,
        clock: WidgetClock,
        model: TodayWidgetModel,
        parents: Parents?,
        maxEvents: Int
    ): TodayWidgetLines {
        val title = strings.string("widget_today_title", clock.titleDate(model.today))
        if (!model.signedIn) {
            return TodayWidgetLines(title = title, footer = strings.string("widget_today_signed_out"))
        }
        val known = parents ?: Parents(loaded = true)
        val names = ParentNames(
            parents = known,
            youFallback = strings.string("parent_label_you"),
            coParentFallback = strings.string("parent_label_coparent"),
            unknownFallback = strings.string("parent_label_unknown")
        )
        val palette = known.palette
        val shown = model.events.take(maxEvents)
        return TodayWidgetLines(
            title = title,
            custody = model.dayParent?.let { slot -> WidgetLine(custodyText(strings, slot, names), palette.of(slot)) },
            windows = model.contactWindows.map { window ->
                WidgetLine(
                    strings.string(
                        "calendar_agenda_contact_window",
                        clock.time(window.start),
                        clock.time(window.end),
                        names.labelFor(window.parent)
                    ),
                    palette.of(window.parent)
                )
            },
            handover = model.handover?.let { handover ->
                WidgetLine(
                    handoverText(strings, handover, names),
                    palette.of(handover.toParent)
                )
            },
            events = shown.map { event ->
                WidgetEventLine(
                    time = timeOf(strings, clock, event),
                    title = event.title,
                    parent = palette.of(event.parentOwner)
                )
            },
            footer = footer(strings, total = model.events.size, shown = shown.size, listsEvents = maxEvents > 0)
        )
    }

    /** Whose day it is, as Home's handover card says it: "Today with you" for the reader. */
    private fun custodyText(strings: WordingStrings, slot: String, names: ParentNames): String =
        if (names.isReader(slot)) {
            strings.string("home_handover_current_you")
        } else {
            strings.string("home_handover_current", names.labelFor(slot))
        }

    /** The hero's wording: today, tomorrow, or in N days — to "you" for the reader, as Home says it. */
    private fun handoverText(strings: WordingStrings, handover: HandoverInfo, names: ParentNames): String {
        val days = handover.daysUntil.toInt()
        val to = names.labelFor(handover.toParent)
        val toReader = names.isReader(handover.toParent)
        return when {
            toReader && handover.daysUntil == 0L -> strings.string("home_handover_hero_today_you")
            toReader && handover.daysUntil == 1L -> strings.string("home_handover_hero_tomorrow_you")
            toReader -> strings.plural("home_handover_hero_in_days_you", days, days)
            handover.daysUntil == 0L -> strings.string("home_handover_hero_today", to)
            handover.daysUntil == 1L -> strings.string("home_handover_hero_tomorrow", to)
            else -> strings.plural("home_handover_hero_in_days", days, to, days)
        }
    }

    /**
     * "14:00–15:30", just the start when the event has no end, or "All day" ([AllDayEvent]) —
     * the today card's format.
     */
    private fun timeOf(strings: WordingStrings, clock: WidgetClock, event: Event): String {
        if (AllDayEvent.isAllDay(event)) return strings.string("event_all_day")
        val start = clock.time(event.startDateTime.time)
        val end = event.endDateTime?.let { clock.time(it.time) } ?: return start
        return strings.string("calendar_agenda_time_range", start, end)
    }

    /**
     * What the rows leave out. An empty day says so, as the today card does; a size that lists no
     * events counts them, and one that lists some says how many it could not.
     */
    private fun footer(strings: WordingStrings, total: Int, shown: Int, listsEvents: Boolean): String? = when {
        total == 0 -> strings.string("calendar_agenda_empty")
        !listsEvents -> strings.plural("widget_today_events_count", total, total)
        total > shown -> (total - shown).let { hidden -> strings.plural("widget_today_more", hidden, hidden) }
        else -> null
    }
}
