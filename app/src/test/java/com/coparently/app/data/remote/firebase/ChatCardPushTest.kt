package com.coparently.app.data.remote.firebase

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A chat card's push is worded on the receiving phone, in its reader's language (notification
 * audit, 9 November 2026): every card used to arrive with its base-English fallback, so a German
 * parent read "New event: Zahnarzt" on the lock screen. And each chat push has a tray id of its own
 * per conversation and per card, where every one used to share one and replace the last.
 *
 * Robolectric for the resources; what the phone then shows is `PushNotificationTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatCardPushTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun inLanguage(language: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    private fun chatPush(vararg card: Pair<String, String>): Map<String, String> = mapOf(
        PushPayload.TYPE to PushPayload.CHAT_MESSAGE,
        PushPayload.ACTOR to "Lana",
        PushPayload.PREVIEW to "New event: Zahnarzt",
        PushPayload.CONVERSATION_ID to "a__b"
    ) + card

    @Test
    fun `a card is worded in the reader's language, not relayed in English`() {
        val german = inLanguage("de")
        val text = PushNotifier(german).compose(
            PushPayload.CHAT_MESSAGE,
            chatPush(
                PushPayload.ACTIVITY_KIND to "EVENT_CREATED",
                PushPayload.ACTIVITY_ENTITY_TYPE to "EVENT",
                PushPayload.ACTIVITY_ENTITY_ID to "e1",
                PushPayload.ACTIVITY_TITLE to "Zahnarzt"
            )
        )

        assertEquals("Lana", text?.title)
        assertEquals(german.getString(R.string.activity_event_created, "Zahnarzt"), text?.body)
    }

    @Test
    fun `a message, an older push and a card this build cannot read show the preview`() {
        val notifier = PushNotifier(inLanguage("de"))

        assertEquals("New event: Zahnarzt", notifier.compose(PushPayload.CHAT_MESSAGE, chatPush())?.body)
        assertEquals(
            "New event: Zahnarzt",
            notifier.compose(
                PushPayload.CHAT_MESSAGE,
                chatPush(
                    PushPayload.ACTIVITY_KIND to "A_KIND_FROM_A_NEWER_BUILD",
                    PushPayload.ACTIVITY_ENTITY_TYPE to "EVENT",
                    PushPayload.ACTIVITY_ENTITY_ID to "e1"
                )
            )?.body
        )
    }

    @Test
    fun `each conversation, and each card, has a tray id of its own`() {
        val card = ActivityAnnouncement(ActivityKind.EXPENSE_UPDATED, ActivityEntityType.EXPENSE, "x1", "Shoes")
        val other = card.copy(entityId = "x2")

        val ids = listOf(
            PushNotifier.chatNotificationId("a__b", null),
            PushNotifier.chatNotificationId("a__c", null),
            PushNotifier.chatNotificationId("a__b", card),
            PushNotifier.chatNotificationId("a__b", other),
            PushNotifier.PAIRING_NOTIFICATION_ID
        )

        assertEquals(ids.size, ids.toSet().size)
        // A later message in the same thread replaces the one before it.
        assertEquals(PushNotifier.chatNotificationId("a__b", null), ids.first())
        assertNotEquals(ids[0], ids[2])
    }
}
