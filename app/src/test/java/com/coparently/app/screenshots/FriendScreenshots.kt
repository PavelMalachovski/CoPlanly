package com.coparently.app.screenshots

import android.app.Application
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.presentation.friends.FriendAgendaDay
import com.coparently.app.presentation.friends.FriendEventDetails
import com.coparently.app.presentation.professionals.ProfessionalDay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a family friend reads (phase 2, spec §4.4): a day of the family's calendar, naming who
 * added each event and marking the one she takes part in, and an event's details. Text-heavy —
 * a long date, names and German at 2.0× — so the full [ScreenshotVariants.TEXT_HEAVY] set.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class FriendScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    /** Today with two events: the parents' handover, and a school play the friend takes part in. */
    @Test
    fun agendaDay() = snap("friends_agenda_day") {
        FriendAgendaDay(
            day = ProfessionalDay(date = ScreenshotFixtures.TODAY, custodySlot = null, events = dayEvents),
            grant = grant,
            onOpenEvent = {}
        )
    }

    /** The school play's details: when, who added it, what it says, and that she takes part. */
    @Test
    fun eventDetails() = snap("friends_event_details") {
        FriendEventDetails(event = schoolPlay, addedBy = "Pavel", takesPart = true)
    }

    companion object {
        private const val FRIEND_UID = "f1"

        private val grant = CalendarFriendGrant(
            friendUid = FRIEND_UID,
            name = "Babička",
            familyParents = listOf(ScreenshotFixtures.MY_UID, ScreenshotFixtures.CO_PARENT_UID),
            grantedBy = ScreenshotFixtures.MY_UID,
            grantedAtMillis = 1L,
            // Fixed rather than computed from the clock, so the image never changes with the date.
            expiresAtMillis = Long.MAX_VALUE,
            familyId = "${ScreenshotFixtures.MY_UID}__${ScreenshotFixtures.CO_PARENT_UID}",
            parentNames = mapOf(ScreenshotFixtures.MY_UID to "Olya", ScreenshotFixtures.CO_PARENT_UID to "Pavel")
        )

        private val handover = ScreenshotFixtures.event(
            id = "f-handover",
            title = "Handover at school",
            start = ScreenshotFixtures.TODAY.atTime(15, 0),
            owner = "mom",
            durationMinutes = 30
        ).copy(createdByFirebaseUid = ScreenshotFixtures.MY_UID)

        private val schoolPlay = ScreenshotFixtures.event(
            id = "f-play",
            title = "Mia's school play",
            start = ScreenshotFixtures.TODAY.atTime(17, 30),
            owner = "dad",
            durationMinutes = 90
        ).copy(
            createdByFirebaseUid = ScreenshotFixtures.CO_PARENT_UID,
            description = "In the school hall. Mia would love it if you came — doors open at five.",
            friendParticipates = FRIEND_UID
        )

        private val dayEvents = listOf(handover, schoolPlay)

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
