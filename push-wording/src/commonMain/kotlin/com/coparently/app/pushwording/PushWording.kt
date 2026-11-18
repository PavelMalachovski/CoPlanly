package com.coparently.app.pushwording

import com.coparently.app.domain.activity.ActivityAnnouncement

/**
 * Turns a push's data into the notification's two lines — or into nothing (iPhone phase 4 part C;
 * it was `PushNotifier.compose` in the Android app, which now calls this).
 *
 * **This device writes the sentence** (SEC-3). The payload carries a type and the few names that
 * type needs; the frame around them comes from this app's own strings ([strings]), in the reader's
 * language, and every date, time and amount from [clock]. A type this build cannot compose is
 * **dropped** (null), not rendered from whatever text happened to arrive: a fallback to relayed
 * text would leave the forgery open under any unrecognised type — an older notification still in
 * the queue, a newer client's type, or a forgery alike.
 *
 * A missing or blank actor name falls back to a translated "your co-parent" rather than to an
 * empty gap in the sentence. `displayName` is genuinely absent for an email/password account that
 * never set one, so this is the normal case for some pairs, not a defect.
 *
 * **A request's first push says when to answer by** (phase 3a). A type in [ANSWER_BY_TYPES] whose
 * payload carries a readable answer-by time gets "Answer by <time>." after its sentence; without
 * one — an older build's push, a request with no clock — the text is exactly what it was.
 *
 * Everything Android-specific — the channel, the tap, the tray id, the expanded style — stays with
 * the Android app's `PushNotifier`; the iPhone's notification extension calls this alone.
 *
 * @param strings The strings, already in the reader's language.
 * @param clock Dates, times and amounts as the reader writes them.
 */
class PushWording(private val strings: WordingStrings, private val clock: ReaderClock) {

    /** The notification [type] with [data] words to, or null when it words to nothing. */
    fun compose(type: String?, data: Map<String, String>): PushText? {
        // The chat preview is the one notification whose text is not a frame: the title is who sent
        // it and the body is what they wrote. Both were composed server-side by
        // `notifyOfChatMessage`, which is the only party that has seen the message, and
        // `firestore.rules` refuses `chat_message` from a client — so this relays rather than
        // composes, without reopening what the rest of this function closes.
        //
        // A chat card is the exception inside the exception (notification audit, 9 November 2026):
        // its preview is the card's base-English fallback, so this phone words the card from the
        // facts the function sent beside it, in its reader's language, as the thread does. A push
        // from before the function sent them, or a card this build cannot read, shows the preview.
        if (type == TYPE_CHAT_MESSAGE) {
            val sender = data[PushFields.ACTOR]?.takeIf { it.isNotBlank() } ?: strings.string(APP_NAME)
            val body = chatCardOf(data)?.let { CardWording.text(it, strings, clock) }
                ?: data[PushFields.PREVIEW].orEmpty()
            return PushText(sender, body)
        }

        return framedText(type, data)
    }

    /** Every push but the chat preview: a frame from this phone's strings, filled from [data]. */
    private fun framedText(type: String?, data: Map<String, String>): PushText? {
        // A swap offer that names its days says who offers which (day swap design, 28 October
        // 2026); one from an older sender falls through to the sentence it always had.
        swapPushWording(type, data)?.let { return swapOfferText(it, type.orEmpty(), data) }

        val spec = type?.let { PUSH_TEXT[it] } ?: return null
        return bodyOf(spec, data)?.let { body ->
            val title = if (spec.titleTakesActor) {
                strings.string(spec.title, actorOf(data))
            } else {
                strings.string(spec.title)
            }
            PushText(title, withDeadline(type, body, data))
        }
    }

    /**
     * The push of a swap offer worded by [wording], with its deadline after the body: the sender's
     * name (or "Your co-parent") and the days in the reader's language ([ReaderClock.swapDays]).
     */
    private fun swapOfferText(wording: SwapPushWording, type: String, data: Map<String, String>): PushText {
        val actor = actorOf(data)
        val (title, body) = when (wording) {
            is SwapPushWording.Gives -> strings.plural(
                "push_day_swap_gives_title",
                wording.toYou.size,
                actor,
                wording.toYou.size
            ) to strings.string("push_day_swap_gives_body", clock.swapDays(wording.toYou))
            is SwapPushWording.Asks -> strings.plural(
                "push_day_swap_asks_title",
                wording.toSender.size,
                actor,
                wording.toSender.size
            ) to strings.string("push_day_swap_asks_body", clock.swapDays(wording.toSender), actor)
            is SwapPushWording.Both -> strings.string("push_day_swap_both_title", actor) to
                strings.string(
                    "push_day_swap_both_body",
                    clock.swapDays(wording.toYou),
                    actor,
                    clock.swapDays(wording.toSender)
                )
        }
        return PushText(title, withDeadline(type, body, data))
    }

    /**
     * [body] with the deadline said after it — "Answer by Tue, Oct 13, 10:00." — when [type] is a
     * request's first push and [data] names one; [body] as it is otherwise.
     */
    private fun withDeadline(type: String, body: String, data: Map<String, String>): String {
        if (type !in ANSWER_BY_TYPES) return body
        val deadline = answerByOf(data) ?: return body
        return sentenceAfter(body, strings.string("push_answer_by", deadline))
    }

    /** The deadline in [data] as this phone's reader says it, or null when it names none or an unreadable one. */
    private fun answerByOf(data: Map<String, String>): String? =
        data[PushFields.ANSWER_BY]?.toLongOrNull()?.let(clock::answerBy)

    /** The name a push carries for the person it is about, or the co-parent fallback when blank. */
    private fun actorOf(data: Map<String, String>): String =
        data[PushFields.ACTOR]?.takeIf { it.isNotBlank() } ?: strings.string(ACTOR_FALLBACK)

    /** The text of a push worded by [spec], filled from [data]; null when [data] cannot fill it. */
    private fun bodyOf(spec: PushTextSpec, data: Map<String, String>): String? {
        val actor = actorOf(data)
        return when (spec.args) {
            BodyArgs.ACTOR_AND_SUBJECT -> strings.string(spec.body, actor, data[PushFields.SUBJECT].orEmpty())
            BodyArgs.ACTOR -> strings.string(spec.body, actor)
            // The deadline is an instant; the day it falls on is this phone's (the banner in the
            // thread says the same day). The UTC day in the date field only when it is unreadable.
            BodyArgs.ACTOR_AND_DEADLINE -> {
                val day = data[PushFields.RETAINED_UNTIL]?.toLongOrNull()?.let(clock::dayOfMillis)
                    ?: clock.dayInSentence(data[PushFields.DATE])
                strings.string(spec.body, actor, day)
            }
            // A last chance names where it ends, and a sentence about waiting "until" nothing says
            // nothing: an unreadable instant composes nothing, like an unparseable count below.
            BodyArgs.ACTOR_AND_ANSWER_BY -> answerByOf(data)?.let { strings.string(spec.body, actor, it) }
            // The date travels as ISO text; the reader's language says it (D-18). An unreadable
            // one is shown as it came rather than dropped.
            BodyArgs.DATE -> strings.string(spec.body, clock.dayInSentence(data[PushFields.DATE]))
            // An unparseable count composes nothing rather than announcing "0 days" — the same
            // rule as an unrecognised type. Only this build's own writer produces it.
            BodyArgs.DAY_COUNT -> data[PushFields.DAY_COUNT]?.toIntOrNull()?.let { count ->
                strings.plural(spec.body, count, count)
            }
            BodyArgs.EXPENSE -> expenseBody(actor, data)
            BodyArgs.NONE -> strings.string(spec.body)
        }
    }

    /**
     * A new expense: who added what for how much, and what it costs the reader when it is split with
     * them, the figures in the reader's language. Null — no push — when the amount or the currency
     * cannot be read: a sentence with a blank figure says less than the chat card that comes with it.
     */
    private fun expenseBody(actor: String, data: Map<String, String>): String? {
        val currency = data[PushFields.CURRENCY] ?: return null
        val amount = data[PushFields.AMOUNT]?.let { clock.money(it, currency) } ?: return null
        val subject = data[PushFields.SUBJECT].orEmpty()
        val share = data[PushFields.SHARE]?.let { clock.money(it, currency) }
        return if (share == null) {
            strings.string("push_expense_added_body", actor, subject, amount)
        } else {
            strings.string("push_expense_added_body_share", actor, subject, amount, share)
        }
    }

    /**
     * The chat card a `chat_message` push carries (the `activity…` fields), read as the thread reads
     * a message's `activity`: null for a plain message, a push from before the function sent the
     * facts, and a kind or entity this build does not know.
     */
    private fun chatCardOf(data: Map<String, String>): ActivityAnnouncement? =
        ActivityAnnouncement.fromMap(
            mapOf(
                "kind" to data[PushFields.ACTIVITY_KIND],
                "entityType" to data[PushFields.ACTIVITY_ENTITY_TYPE],
                "entityId" to data[PushFields.ACTIVITY_ENTITY_ID],
                "title" to data[PushFields.ACTIVITY_TITLE],
                "whenIso" to data[PushFields.ACTIVITY_WHEN_ISO],
                "amount" to data[PushFields.ACTIVITY_AMOUNT],
                "currency" to data[PushFields.ACTIVITY_CURRENCY]
            )
        )

    /** Which of the payload's names a body string takes, in order. */
    enum class BodyArgs {
        /** The actor, then the subject. */
        ACTOR_AND_SUBJECT,

        /** The actor. */
        ACTOR,

        /** The actor, then the day a departed co-parent's thread is kept until. */
        ACTOR_AND_DEADLINE,

        /** The actor, then the answer-by time. */
        ACTOR_AND_ANSWER_BY,

        /** The day, from its ISO date. */
        DATE,

        /** A count of days: [PushTextSpec.body] is a plural. */
        DAY_COUNT,

        /** A new expense's sentence, which picks its own string. */
        EXPENSE,

        /** Nothing. */
        NONE
    }

    /**
     * A type's wording: the frame's resource keys, and what fills it.
     *
     * [body] is a plural's key, not a string's, when [args] is [BodyArgs.DAY_COUNT] — Czech, Russian
     * and Ukrainian each need three forms for "N days".
     *
     * @property title The title's string key.
     * @property body The body's string key (a plural's for [BodyArgs.DAY_COUNT]).
     * @property args What fills the body.
     * @property titleTakesActor True when the title, not the body, names the person (`%1$s` = the
     *   payload's actor).
     */
    data class PushTextSpec(
        val title: String,
        val body: String,
        val args: BodyArgs,
        val titleTakesActor: Boolean = false
    )

    /** The table of every framed type, the types it words, and the deadline sentence. */
    companion object {
        private const val TYPE_CHAT_MESSAGE = PushFields.CHAT_MESSAGE
        private const val APP_NAME = "app_name"
        private const val ACTOR_FALLBACK = "push_actor_fallback"

        /**
         * Every framed notification this build knows how to word, by type.
         *
         * A table rather than a `when`, so the set of types the app will render is one list a reader
         * can check against `PushPayload` and against `firestore.rules` — the three have to agree.
         * A type absent from here is dropped on arrival (see [compose]), which is also why adding a
         * type to `PushPayload` without adding it here is a push that silently never appears.
         * `tools/check-invariants.js` reads this table: keep each entry `"<type>" to PushTextSpec(`.
         *
         * `chat_message` is deliberately not in it: its text is not a frame, and [compose] handles
         * it before consulting this.
         */
        val PUSH_TEXT: Map<String, PushTextSpec> = mapOf(
            "expense_added" to PushTextSpec(
                "push_expense_added_title",
                "push_expense_added_body",
                BodyArgs.EXPENSE
            ),
            "event_created" to PushTextSpec(
                "push_event_created_title",
                "push_event_created_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "event_important" to PushTextSpec(
                "push_event_important_title",
                "push_event_important_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "event_updated" to PushTextSpec(
                "push_event_updated_title",
                "push_event_updated_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "event_deleted" to PushTextSpec(
                "push_event_deleted_title",
                "push_event_deleted_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "child_info_updated" to PushTextSpec(
                "push_child_info_updated_title",
                "push_child_info_updated_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "change_request_created" to PushTextSpec(
                "push_change_request_created_title",
                "push_change_request_created_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "change_request_accepted" to PushTextSpec(
                "push_change_request_accepted_title",
                "push_change_request_accepted_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "change_request_declined" to PushTextSpec(
                "push_change_request_declined_title",
                "push_change_request_declined_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "change_request_cancelled" to PushTextSpec(
                "push_change_request_cancelled_title",
                "push_change_request_cancelled_body",
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            "custody_proposal_proposed" to PushTextSpec(
                "push_custody_proposal_proposed_title",
                "push_custody_proposal_proposed_body",
                BodyArgs.NONE
            ),
            "custody_proposal_accepted" to PushTextSpec(
                "push_custody_proposal_accepted_title",
                "push_custody_proposal_accepted_body",
                BodyArgs.NONE
            ),
            "custody_proposal_declined" to PushTextSpec(
                "push_custody_proposal_declined_title",
                "push_custody_proposal_declined_body",
                BodyArgs.NONE
            ),
            "day_swap_offered" to PushTextSpec(
                "push_day_swap_offered_title",
                "push_day_swap_offered_body",
                BodyArgs.DATE
            ),
            "day_swap_accepted" to PushTextSpec(
                "push_day_swap_accepted_title",
                "push_day_swap_accepted_body",
                BodyArgs.DATE
            ),
            "day_swap_declined" to PushTextSpec(
                "push_day_swap_declined_title",
                "push_day_swap_declined_body",
                BodyArgs.DATE
            ),
            "day_swap_group_offered" to PushTextSpec(
                "push_day_swap_offered_title",
                "push_day_swap_group_offered_body",
                BodyArgs.DAY_COUNT
            ),
            "day_swap_group_accepted" to PushTextSpec(
                "push_day_swap_accepted_title",
                "push_day_swap_group_accepted_body",
                BodyArgs.DAY_COUNT
            ),
            "day_swap_group_declined" to PushTextSpec(
                "push_day_swap_declined_title",
                "push_day_swap_group_declined_body",
                BodyArgs.DAY_COUNT
            ),
            "split_ratio_proposed" to PushTextSpec(
                "push_split_ratio_proposed_title",
                "push_split_ratio_proposed_body",
                BodyArgs.NONE
            ),
            "split_ratio_accepted" to PushTextSpec(
                "push_split_ratio_accepted_title",
                "push_split_ratio_accepted_body",
                BodyArgs.NONE
            ),
            "split_ratio_declined" to PushTextSpec(
                "push_split_ratio_declined_title",
                "push_split_ratio_declined_body",
                BodyArgs.NONE
            ),
            "split_ratio_agreed" to PushTextSpec(
                "push_split_ratio_agreed_title",
                "push_split_ratio_agreed_body",
                BodyArgs.NONE
            ),
            "records_shared" to PushTextSpec(
                "push_records_shared_title",
                "push_records_shared_body",
                BodyArgs.ACTOR
            ),
            "sticker_received" to PushTextSpec(
                "push_sticker_received_title",
                "push_sticker_received_body",
                BodyArgs.ACTOR
            ),
            "request_last_chance" to PushTextSpec(
                "push_request_last_chance_title",
                "push_request_last_chance_body",
                BodyArgs.ACTOR_AND_ANSWER_BY
            ),
            // Server-only (MON-25): the scheduled clock function's two pushes to the proposer. The
            // title names who did not answer; the body is a fixed sentence.
            "request_deadline_passed" to PushTextSpec(
                "push_request_deadline_passed_title",
                "push_request_deadline_passed_body",
                BodyArgs.NONE,
                titleTakesActor = true
            ),
            "request_no_answer" to PushTextSpec(
                "push_request_no_answer_title",
                "push_request_no_answer_body",
                BodyArgs.NONE,
                titleTakesActor = true
            ),
            // Server-only (phase 4 part C): the answering parent's reminder, sent by the scheduled
            // clock function at the times their phone planned. Worded exactly as the phone's own
            // reminder (`ReminderWording.request`): the same two strings, the proposer's name or
            // the fallback, and the deadline; an unreadable deadline words nothing.
            "request_reminder" to PushTextSpec(
                "request_notice_reminder_title",
                "request_notice_reminder_body",
                BodyArgs.ACTOR_AND_ANSWER_BY
            ),
            "pairing_accepted" to PushTextSpec(
                "push_pairing_accepted_title",
                "push_pairing_accepted_body",
                BodyArgs.ACTOR
            ),
            "pairing_removed" to PushTextSpec(
                "push_pairing_removed_title",
                "push_pairing_removed_body",
                BodyArgs.ACTOR
            ),
            "professional_access_requested" to PushTextSpec(
                "push_professional_access_requested_title",
                "push_professional_access_requested_body",
                BodyArgs.ACTOR
            ),
            "calendar_friend_accepted" to PushTextSpec(
                "push_calendar_friend_accepted_title",
                "push_calendar_friend_accepted_body",
                BodyArgs.ACTOR
            ),
            "coparent_account_deleted" to PushTextSpec(
                "push_coparent_account_deleted_title",
                "push_coparent_account_deleted_body",
                BodyArgs.ACTOR_AND_DEADLINE
            )
        )

        /** Every type [compose] words: the framed ones in [PUSH_TEXT], and the chat message. */
        val TYPES: Set<String> = PUSH_TEXT.keys + TYPE_CHAT_MESSAGE

        /**
         * The pushes that open a request, and so may carry its deadline for [compose] to say after
         * their sentence: a day swap offered (one day or a run), a change request, a custody
         * proposal, an expense split proposal. An event waiting to be accepted has no push of its
         * own, and a last chance says its time in its own body ([BodyArgs.ACTOR_AND_ANSWER_BY]).
         */
        val ANSWER_BY_TYPES: Set<String> = setOf(
            "day_swap_offered",
            "day_swap_group_offered",
            "change_request_created",
            "custody_proposal_proposed",
            "split_ratio_proposed"
        )

        /** What ends a sentence in the five languages the app is worded in. */
        private val SENTENCE_END = setOf('.', '!', '?', '…')

        /**
         * [sentence] after [body], as a sentence of its own.
         *
         * Most bodies end on a name or a date and no full stop ("Your co-parent proposes swapping
         * May 14"), so a bare space would run the two together; one is added first unless the body
         * already ends a sentence.
         */
        fun sentenceAfter(body: String, sentence: String): String =
            body + (if (body.lastOrNull() in SENTENCE_END) " " else ". ") + sentence
    }
}
