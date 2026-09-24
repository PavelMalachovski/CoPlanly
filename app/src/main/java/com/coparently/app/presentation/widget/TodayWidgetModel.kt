package com.coparently.app.presentation.widget

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.HandoverCalculator
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.home.HomeWeek
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * What the Today widget shows, before any of it is worded.
 *
 * Built by [of] from the pure functions behind Home's today card and handover hero —
 * [HomeWeek.todayOf], [HandoverCalculator.nextHandoverFrom] and [CustodyResolver] — and from the
 * same Room rows, so the home screen can never tell a parent something different from the app
 * about the same day. Swaps count (the resolver puts them above the pattern), contact windows
 * naming the day's own parent are dropped (the grid's filter), and a private event is shown only
 * to the parent who wrote it.
 *
 * Nothing here is an hour the schema does not hold: a handover is a **day** and a person, as on
 * Home, because `CustodyModel` carries days, never hours.
 *
 * @property signedIn False when no account is signed in; the widget then says so and nothing else.
 * @property today The day described.
 * @property dayParent Whose day it is, as a slot, or null when no arrangement answers.
 * @property contactWindows Today's contact windows with the parent who does not have the day,
 *   earliest first.
 * @property handover The next handover after today, or null when there is no schedule or custody
 *   does not change within two cycles.
 * @property events Every event touching today, earliest first.
 */
data class TodayWidgetModel(
    val signedIn: Boolean,
    val today: LocalDate,
    val dayParent: String? = null,
    val contactWindows: List<ContactWindow> = emptyList(),
    val handover: HandoverInfo? = null,
    val events: List<Event> = emptyList()
) {
    companion object {

        /** The widget of a device nobody is signed in on. */
        fun signedOut(today: LocalDate): TodayWidgetModel = TodayWidgetModel(signedIn = false, today = today)

        /**
         * Today as Home describes it.
         *
         * @param today The day to describe.
         * @param userId The signed-in account, which decides whose private events are shown.
         * @param events Events overlapping today, recurring series already expanded — what
         *   `EventRepository.getEventsByDateRange` returns.
         * @param model The active custody pattern, or null when there is none.
         * @param overrides The accepted one-off swaps, keyed by ISO date.
         */
        fun of(
            today: LocalDate,
            userId: String,
            events: List<Event>,
            model: CustodyModel?,
            overrides: Map<String, DayOverride>
        ): TodayWidgetModel {
            // No legacy fallback, as on Home: one custody lookup per surface.
            val custodyFor = CustodyResolver.resolver(model, overrides, legacy = { null })
            val agenda = HomeWeek.todayOf(
                events = events,
                today = today,
                userId = userId,
                custodyFor = custodyFor,
                contactWindowsFor = CustodyResolver.contactWindowsResolver(model, custodyFor)
            )
            return TodayWidgetModel(
                signedIn = true,
                today = today,
                dayParent = agenda.dayParent,
                contactWindows = agenda.contactWindows,
                handover = model?.let { HandoverCalculator.nextHandoverFrom(it, today, overrides) },
                events = agenda.events
            )
        }
    }
}
