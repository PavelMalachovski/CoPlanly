package com.coparently.app.data.remote.firebase

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sentence "Answer by <time>." that a request's first push carries after its own (phase 3a).
 *
 * Most bodies end on a name or a date and no full stop, so a bare space would run the two
 * sentences together; the join supplies the stop unless the body has one. Plain JVM: it is string
 * handling, and the notification as a person sees it is `PushNotificationTest`'s.
 */
class PushDeadlineSentenceTest {

    private val answerBy = "Answer by Tue, Oct 13, 10:00."

    @Test
    fun `a body that ends on a date gets a full stop before the sentence`() {
        assertEquals(
            "Your co-parent proposes swapping May 14. $answerBy",
            PushNotifier.sentenceAfter("Your co-parent proposes swapping May 14", answerBy)
        )
    }

    @Test
    fun `a body that ends on a closing quote gets one too, outside the quote`() {
        assertEquals(
            "Jana asked to change “Dentist”. $answerBy",
            PushNotifier.sentenceAfter("Jana asked to change “Dentist”", answerBy)
        )
    }

    @Test
    fun `a body that already ends a sentence is not given a second stop`() {
        for (end in listOf('.', '!', '?', '…')) {
            val body = "Your co-parent proposes a new schedule$end"
            assertEquals("$body $answerBy", PushNotifier.sentenceAfter(body, answerBy))
        }
    }

    @Test
    fun `only a request's opening push can carry a deadline, and each is worded`() {
        assertEquals(
            setOf(
                PushPayload.DAY_SWAP_OFFERED,
                PushPayload.DAY_SWAP_GROUP_OFFERED,
                PushPayload.CHANGE_REQUEST_CREATED,
                PushPayload.CUSTODY_PROPOSAL_PROPOSED,
                PushPayload.SPLIT_RATIO_PROPOSED
            ),
            PushNotifier.ANSWER_BY_TYPES
        )
        assertTrue(PushNotifier.ANSWER_BY_TYPES.all { it in PushNotifier.PUSH_TEXT })
    }
}
