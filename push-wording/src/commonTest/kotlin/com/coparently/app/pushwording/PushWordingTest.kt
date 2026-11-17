package com.coparently.app.pushwording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What [PushWording] and [ReminderWording] decide, over strings and a clock that print what they
 * were asked for: which key, which arguments, which plural count. The text itself, byte for byte,
 * is the app's `PushWordingGoldenTest`'s and, on the iPhone, `PushWordingIosTest`'s.
 */
class PushWordingTest {

    private object Strings : WordingStrings {
        override fun string(key: String, vararg args: Any): String =
            if (args.isEmpty()) key else "$key(${args.joinToString("|")})"

        override fun plural(key: String, count: Int, vararg args: Any): String =
            "$key[$count](${args.joinToString("|")})"
    }

    private object Clock : ReaderClock {
        override fun answerBy(millis: Long) = "by:$millis"
        override fun dayInSentence(isoDate: String?) = "day:${isoDate.orEmpty()}"
        override fun dayOfMillis(millis: Long) = "dayOf:$millis"
        override fun swapDays(isoDates: List<String>) = "days:${isoDates.joinToString("+")}"
        override fun eventStart(millis: Long) = "at:$millis"
        override fun money(amount: String, currency: String) =
            if (amount.toDoubleOrNull() == null || currency.length != 3) null else "$amount $currency"
        override fun amount(amount: String?, currency: String?) = "${amount.orEmpty()}~${currency.orEmpty()}"
        override fun dayWithWeekday(isoDate: String) = "weekday:$isoDate"
        override fun monthOfDay(isoDate: String) = if (isoDate.length == 10) "month:${isoDate.take(7)}" else null
        override fun monthOfMonth(isoMonth: String) = if (isoMonth.length == 7) "month:$isoMonth" else null
        override fun mediumDate(millis: Long) = "medium:$millis"
    }

    private val wording = PushWording(Strings, Clock)

    @Test
    fun aTypeItCannotWordIsDropped() {
        assertNull(wording.compose("a_type_from_a_newer_build", mapOf("actorName" to "Lana")))
        assertNull(wording.compose(null, mapOf("actorName" to "Lana")))
    }

    @Test
    fun aFramedTypeTakesItsActorOrTheFallback() {
        assertEquals(
            PushText("push_event_created_title", "push_event_created_body(Lana|Dentist)"),
            wording.compose("event_created", mapOf("actorName" to "Lana", "subject" to "Dentist"))
        )
        assertEquals(
            PushText("push_event_created_title", "push_event_created_body(push_actor_fallback|)"),
            wording.compose("event_created", mapOf("actorName" to " "))
        )
    }

    @Test
    fun aRequestsFirstPushSaysWhenToAnswerBy() {
        val text = wording.compose("change_request_created", mapOf("actorName" to "Lana", "answerBy" to "42"))
        assertEquals("push_change_request_created_body(Lana|). push_answer_by(by:42)", text?.body)
        val withoutClock = wording.compose("change_request_created", mapOf("actorName" to "Lana", "answerBy" to "x"))
        assertEquals("push_change_request_created_body(Lana|)", withoutClock?.body)
    }

    @Test
    fun aCountItCannotReadComposesNothing() {
        assertEquals(
            "push_day_swap_group_accepted_body[3](3)",
            wording.compose("day_swap_group_accepted", mapOf("dayCount" to "3"))?.body
        )
        assertNull(wording.compose("day_swap_group_accepted", mapOf("dayCount" to "three")))
    }

    @Test
    fun aSwapOfferNamesItsDaysSoonestFirst() {
        val text = wording.compose(
            "day_swap_offered",
            mapOf("actorName" to "Lana", "datesToYou" to "2026-11-07,2026-11-06")
        )
        assertEquals(
            PushText("push_day_swap_gives_title[2](Lana|2)", "push_day_swap_gives_body(days:2026-11-06+2026-11-07)"),
            text
        )
    }

    @Test
    fun anExpenseWithoutAReadableAmountOrCurrencyComposesNothing() {
        val expense = mapOf("actorName" to "Lana", "subject" to "Shoes", "amount" to "12.5", "currency" to "EUR")
        assertEquals("push_expense_added_body(Lana|Shoes|12.5 EUR)", wording.compose("expense_added", expense)?.body)
        assertEquals(
            "push_expense_added_body_share(Lana|Shoes|12.5 EUR|6.25 EUR)",
            wording.compose("expense_added", expense + ("share" to "6.25"))?.body
        )
        assertNull(wording.compose("expense_added", expense - "currency"))
        assertNull(wording.compose("expense_added", expense + ("amount" to "lots")))
    }

    @Test
    fun aChatMessageRelaysItsPreviewAndWordsItsCard() {
        val message = mapOf("actorName" to "Lana", "preview" to "Hi")
        assertEquals(PushText("Lana", "Hi"), wording.compose("chat_message", message))
        assertEquals(PushText("app_name", ""), wording.compose("chat_message", emptyMap()))
        val card = message + mapOf(
            "activityKind" to "EVENT_CREATED",
            "activityEntityType" to "EVENT",
            "activityEntityId" to "e1",
            "activityTitle" to "Dentist"
        )
        assertEquals(PushText("Lana", "activity_event_created(Dentist)"), wording.compose("chat_message", card))
        val unknown = card + ("activityKind" to "A_KIND_FROM_A_NEWER_BUILD")
        assertEquals(PushText("Lana", "Hi"), wording.compose("chat_message", unknown))
    }

    @Test
    fun everyFramedTypeAndTheChatMessageAreWorded() {
        assertTrue("chat_message" in PushWording.TYPES)
        assertTrue(PushWording.TYPES.containsAll(PushWording.PUSH_TEXT.keys))
        assertTrue(PushWording.PUSH_TEXT.keys.containsAll(PushWording.ANSWER_BY_TYPES))
    }

    @Test
    fun aReminderNamesTheOtherParentOrTheFallback() {
        val reminders = ReminderWording(Strings, Clock)
        assertEquals(
            PushText("request_notice_reminder_title", "request_notice_reminder_body(Lana|by:7)"),
            reminders.request("Lana", 7)
        )
        assertEquals(
            "request_notice_reminder_body(push_actor_fallback|by:7)",
            reminders.request(" ", 7).body
        )
        assertEquals(
            PushText("support_reminder_title(medium:5)", "support_reminder_body(350.00~CZK|push_actor_fallback)"),
            reminders.support(5, "350.00", "CZK", null)
        )
        assertEquals(PushText("Dentist", "reminder_notification_text(at:9)"), reminders.event("Dentist", 9))
    }
}
