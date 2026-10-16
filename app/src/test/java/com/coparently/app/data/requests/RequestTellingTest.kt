package com.coparently.app.data.requests

import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [RequestTelling]: the last-chance push and the two cards go to the clock's family, never the
 * family on screen (phase 3a, spec §5.6, Review Focus 1).
 *
 * Alice co-parents with Bob and with Carol, and shows Carol's family while Bob's request ends. The
 * announcer is handed Bob explicitly — its fourth argument — because without one it posts to the
 * family on screen.
 */
class RequestTellingTest {

    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)
    private val fcmService = mockk<FcmService>()
    private val authService = mockk<FirebaseAuthService>()
    private val telling = RequestTelling(announcer, fcmService, authService)

    private val clock = RequestDeadline(
        id = RequestDeadlineId.of(RequestKind.CHANGE, ALICE, "cr-1", FAMILY),
        kind = RequestKind.CHANGE,
        ref = "cr-1",
        familyId = FAMILY,
        proposerUid = ALICE,
        responderUid = BOB,
        answerByMillis = 1_778_000_000_000L
    )
    private val card = CardSubject(ActivityEntityType.CHANGE_REQUEST, "ev-9", "Dentist")

    init {
        val user = mockk<FirebaseUser> { every { displayName } returns "Alice" }
        every { authService.getCurrentUser() } returns user
        coEvery { fcmService.queueNotificationForUser(any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `a last chance pushes the responder in the clock's family, then posts its card to them`() = runTest {
        telling.lastChance(clock, card, untilMillis = UNTIL)

        coVerifyOrder {
            fcmService.queueNotificationForUser(
                BOB,
                mapOf(
                    PushPayload.TYPE to PushPayload.REQUEST_LAST_CHANCE,
                    PushPayload.ACTOR to "Alice",
                    PushPayload.ANSWER_BY to UNTIL.toString(),
                    PushPayload.FAMILY_ID to FAMILY
                )
            )
            announcer.announce(
                announcement(ActivityKind.REQUEST_LAST_CHANCE),
                "Alice",
                false,
                BOB
            )
        }
    }

    @Test
    fun `no answer posts its card to the clock's family and pushes nobody`() = runTest {
        telling.unanswered(clock, card)

        coVerify(exactly = 1) {
            announcer.announce(
                announcement(ActivityKind.REQUEST_UNANSWERED),
                "Alice",
                false,
                BOB
            )
        }
        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }
    }

    private fun announcement(kind: ActivityKind) =
        ActivityAnnouncement(kind, ActivityEntityType.CHANGE_REQUEST, "ev-9", "Dentist")

    private companion object {
        const val ALICE = "uidA"
        const val BOB = "uidB"
        const val FAMILY = "uidA__uidB"
        const val UNTIL = 1_778_086_400_000L
    }
}
