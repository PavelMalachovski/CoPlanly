package com.coparently.app.data.requests

import android.util.Log
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.RequestDeadline
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the proposer's phone tells the answering parent about a request's clock (phase 3a, spec
 * §4.4, §4.6, §5.4): the last-chance push and its card, and the "No answer" card.
 *
 * **Always the clock's family** (spec §5.6). The push is addressed to the clock's responder and
 * carries the clock's `familyId`, and the card goes to the thread with that same parent — never the
 * thread of the family on screen, which by the time a background step runs may be another one.
 *
 * Best-effort, like every announcement: a push that cannot be queued or a card that cannot be
 * posted is logged and dropped, and never undoes the write that caused it.
 */
@Singleton
class RequestTelling @Inject constructor(
    private val activityAnnouncer: ActivityAnnouncer,
    private val fcmService: FcmService,
    private val authService: FirebaseAuthService
) {

    /**
     * Pushes "Last chance to answer" to the answering parent, until [untilMillis], then posts the
     * "Last chance" card about [card] to their thread.
     */
    suspend fun lastChance(clock: RequestDeadline, card: CardSubject, untilMillis: Long) {
        val actor = senderName()
        fcmService.queueNotificationForUser(
            clock.responderUid,
            mapOf(
                PushPayload.TYPE to PushPayload.REQUEST_LAST_CHANCE,
                PushPayload.ACTOR to actor,
                PushPayload.ANSWER_BY to untilMillis.toString(),
                PushPayload.FAMILY_ID to clock.familyId
            )
        ).onFailure { Log.w(TAG, "The last-chance push was not queued", it) }
        announce(clock, card, ActivityKind.REQUEST_LAST_CHANCE, actor)
    }

    /**
     * Posts the "No answer" card about [card] to the answering parent's thread. No push: nothing
     * more is sent to a parent whose time has run out (`notifyOfChatMessage` skips the card too).
     */
    suspend fun unanswered(clock: RequestDeadline, card: CardSubject) {
        announce(clock, card, ActivityKind.REQUEST_UNANSWERED, senderName())
    }

    private suspend fun announce(clock: RequestDeadline, card: CardSubject, kind: ActivityKind, actor: String) {
        activityAnnouncer.announce(
            ActivityAnnouncement(kind, card.entityType, card.entityId, card.title),
            actor,
            false,
            clock.responderUid
        )
    }

    /** The signed-in parent's display name, for the push and the bubble; blank is tolerated. */
    private fun senderName(): String = authService.getCurrentUser()?.displayName.orEmpty()

    private companion object {
        const val TAG = "RequestTelling"
    }
}
