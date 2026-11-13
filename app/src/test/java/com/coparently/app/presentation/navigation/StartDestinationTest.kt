package com.coparently.app.presentation.navigation

import com.coparently.app.domain.telemetry.TelemetryConsent
import org.junit.Test
import kotlin.test.assertEquals

/** The route the graph starts on (redesign, part 2a: a signed-in parent opens on the Calendar). */
class StartDestinationTest {

    @Test
    fun aSignedInParentThroughOnboardingOpensOnTheCalendar() {
        assertEquals(
            Screen.Calendar.route,
            startDestinationFor(TelemetryConsent.GRANTED, false, true, false)
        )
    }

    @Test
    fun theEarlierQuestionsStillComeFirst() {
        assertEquals(
            Screen.PrivacyConsent.route,
            startDestinationFor(TelemetryConsent.UNANSWERED, false, true, false)
        )
        assertEquals(Screen.Loading.route, startDestinationFor(TelemetryConsent.GRANTED, true, true, false))
        assertEquals(Screen.Auth.route, startDestinationFor(TelemetryConsent.GRANTED, false, false, false))
        assertEquals(Screen.Loading.route, startDestinationFor(TelemetryConsent.GRANTED, false, true, null))
        assertEquals(Screen.Onboarding.route, startDestinationFor(TelemetryConsent.GRANTED, false, true, true))
    }
}
