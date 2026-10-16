package com.coparently.app.data.remote.google

/**
 * What CoPlanly asks of a parent's Google Calendar (October 2026, ahead of Google's verification
 * of the OAuth consent screen).
 *
 * The connection reads the events of the parent's primary calendar ([GoogleCalendarApi.listEvents])
 * and nothing else, and `CalendarSyncRepository` stores them as **private** events, which never
 * leave the phone. Nothing in the app writes to Google: `GoogleCalendarApi` had create, update and
 * delete methods that no caller ever reached, and they were removed with the full `calendar`
 * scope they needed. Google verifies a consent screen against the narrowest scope the app could
 * work with, so the scope says exactly what the code does: read the events of calendars the parent
 * owns, which the primary calendar always is. `calendar.events.readonly` would also reach every
 * calendar shared with them.
 *
 * The scope is a literal because the Calendar client library in the build (v3-rev20220715)
 * predates it; the Calendar API's discovery document lists it among the scopes `events.list`
 * accepts.
 *
 * The privacy policy ("Google Calendar, if you connect it"), the home page (`web/index.html`) and
 * the consent screen's Data access name this scope too. A feature that writes to Google, or reads a
 * calendar the parent does not own, needs a broader scope, a new consent from every connected
 * parent, all three texts changed and the verification done again — which is why
 * `GoogleCalendarAccessTest` fails first.
 *
 * A token granted for the old full scope keeps working: it still covers reading events, and the
 * next connection asks for this one.
 */
object GoogleCalendarAccess {

    /** The OAuth scope the Calendar connection requests: see the events on calendars the parent owns. */
    const val SCOPE: String = "https://www.googleapis.com/auth/calendar.events.owned.readonly"
}
