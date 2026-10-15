package com.coparently.app.data.remote.google

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * What CoPlanly asks of a parent's Google Calendar, and what it does with it.
 *
 * The privacy policy, the home page (`web/index.html`) and the OAuth consent screen all say the
 * same thing: the app reads the events of the primary calendar, keeps them on the phone as private
 * events, and writes nothing to Google. Google verifies the consent screen against the narrowest
 * scope the app could work with. These tests hold the code to that, so a change that would need a
 * broader scope fails here, where the scope, those three texts and the verification are revisited
 * together.
 */
class GoogleCalendarAccessTest {

    @Test
    fun `the app asks Google only to see events on calendars the parent owns`() {
        // Narrower than `calendar.events.readonly` ("View events on all your calendars"): the import
        // reads the primary calendar alone, which is always the parent's own. The Calendar API's
        // discovery document lists this scope for `events.list`.
        assertEquals(
            "https://www.googleapis.com/auth/calendar.events.owned.readonly",
            GoogleCalendarAccess.SCOPE
        )
    }

    @Test
    fun `the Calendar wrapper only reads`() {
        val publicMethods = GoogleCalendarApi::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSortedSet()

        assertEquals(sortedSetOf("getCalendarService", "listEvents"), publicMethods)
    }
}
