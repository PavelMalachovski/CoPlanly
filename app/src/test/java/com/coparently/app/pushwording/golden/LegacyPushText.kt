package com.coparently.app.pushwording.golden

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.coparently.app.R
import com.coparently.app.data.money.MinorUnits
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.presentation.stickers.StickerText
import com.coparently.app.pushwording.PushText
import com.coparently.app.resources.Res
import com.coparently.app.resources.activity_change_accepted
import com.coparently.app.resources.activity_change_declined
import com.coparently.app.resources.activity_change_requested
import com.coparently.app.resources.activity_custody_accepted
import com.coparently.app.resources.activity_custody_declined
import com.coparently.app.resources.activity_custody_proposed
import com.coparently.app.resources.activity_custody_withdrawn
import com.coparently.app.resources.activity_day_swap_accepted
import com.coparently.app.resources.activity_day_swap_declined
import com.coparently.app.resources.activity_day_swap_offered
import com.coparently.app.resources.activity_day_swap_withdrawn
import com.coparently.app.resources.activity_event_accepted
import com.coparently.app.resources.activity_event_created
import com.coparently.app.resources.activity_event_declined
import com.coparently.app.resources.activity_event_deleted
import com.coparently.app.resources.activity_event_updated
import com.coparently.app.resources.activity_expense_added
import com.coparently.app.resources.activity_expense_deleted
import com.coparently.app.resources.activity_expense_questioned
import com.coparently.app.resources.activity_expense_updated
import com.coparently.app.resources.activity_request_last_chance
import com.coparently.app.resources.activity_request_unanswered
import com.coparently.app.resources.activity_settle_up_confirmed
import com.coparently.app.resources.activity_settle_up_owed_to_you
import com.coparently.app.resources.activity_settle_up_paid
import com.coparently.app.resources.activity_settle_up_you_owe
import com.coparently.app.resources.activity_split_accepted
import com.coparently.app.resources.activity_split_declined
import com.coparently.app.resources.activity_split_proposed
import com.coparently.app.resources.activity_split_withdrawn
import com.coparently.app.resources.activity_sticker_sent
import com.coparently.app.resources.activity_subject_custody_schedule
import com.coparently.app.resources.activity_subject_expense_split
import com.coparently.app.resources.activity_subject_sticker
import com.coparently.app.resources.activity_support_accepted
import com.coparently.app.resources.activity_support_confirmed
import com.coparently.app.resources.activity_support_declined
import com.coparently.app.resources.activity_support_paid
import com.coparently.app.resources.activity_support_proposed
import com.coparently.app.resources.activity_support_withdrawn
import com.coparently.app.utils.DAY_IN_SENTENCE
import com.coparently.app.utils.DAY_WITH_WEEKDAY
import com.coparently.app.utils.answerByText
import com.coparently.app.utils.isoDateText
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.swapDateList
import org.jetbrains.compose.resources.StringResource
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * The golden oracle of every notification's wording (iPhone phase 4 part C, Task 2): a copy of the
 * code that worded them before it moved to `:push-wording`, taken at commit 7f9d97e6 and changed
 * only in its package, in being gathered into one class, and in taking its context as a parameter
 * (the three reminders' wording, which was inline in `DueReminders`, `SupportReminderWorker` and
 * `RequestNotices`, is the lines those classes ran). `PushWordingGoldenTest` holds the module's
 * text to it, byte for byte, in the five languages.
 *
 * It stays in the app's tests after the move, as Gson stayed as `StoredJson`'s oracle: never edit
 * it to agree with a change in the module. A deliberate change of wording changes the strings
 * (which both read) or is argued for in its own pull request with this oracle updated beside it.
 *
 * - [PushNotifier] is `PushNotifier.compose` and what it called (`framedText`, `bodyOf`, the swap
 *   offer's wording from `SwapPushWording.kt`, the deadline sentence, the expense's money).
 * - [CardPushText] is `presentation/chat/CardPushText.kt`, with the decisions it read of
 *   `ActivityCardText` ([CardDecisions]) as they were.
 */
@Suppress("LargeClass", "MagicNumber", "TooManyFunctions")
internal object LegacyPushText {

    /** `PushNotifier`'s wording as it stood, over [context]'s resources. */
    class PushNotifier(private val context: Context) {

        fun compose(type: String?, data: Map<String, String>): PushText? {
            if (type == TYPE_CHAT_MESSAGE) {
                val sender = data[PushPayload.ACTOR]?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.app_name)
                val body = chatCardOf(data)?.let { CardPushText.wordCard(context, it) }
                    ?: data[PushPayload.PREVIEW].orEmpty()
                return PushText(sender, body)
            }

            return framedText(type, data)
        }

        private fun framedText(type: String?, data: Map<String, String>): PushText? {
            swapPushWording(type, data)?.let { return swapOfferText(it, type.orEmpty(), data) }

            val spec = type?.let { PUSH_TEXT[it] } ?: return null
            return bodyOf(spec, data)?.let { body ->
                val title = if (spec.titleTakesActor) {
                    context.getString(spec.title, actorOf(data))
                } else {
                    context.getString(spec.title)
                }
                PushText(title, withDeadline(type, body, data))
            }
        }

        private fun swapOfferText(wording: SwapPushWording, type: String, data: Map<String, String>): PushText {
            val actor = data[PushPayload.ACTOR]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.push_actor_fallback)
            val locale = context.resources.configuration.locales[0]
            val resources = context.resources
            val (title, body) = when (wording) {
                is SwapPushWording.Gives -> resources.getQuantityString(
                    R.plurals.push_day_swap_gives_title,
                    wording.toYou.size,
                    actor,
                    wording.toYou.size
                ) to context.getString(R.string.push_day_swap_gives_body, swapDateList(wording.toYou, locale))
                is SwapPushWording.Asks -> resources.getQuantityString(
                    R.plurals.push_day_swap_asks_title,
                    wording.toSender.size,
                    actor,
                    wording.toSender.size
                ) to context.getString(
                    R.string.push_day_swap_asks_body,
                    swapDateList(wording.toSender, locale),
                    actor
                )
                is SwapPushWording.Both -> context.getString(R.string.push_day_swap_both_title, actor) to
                    context.getString(
                        R.string.push_day_swap_both_body,
                        swapDateList(wording.toYou, locale),
                        actor,
                        swapDateList(wording.toSender, locale)
                    )
            }
            return PushText(title, withDeadline(type, body, data))
        }

        private fun withDeadline(type: String, body: String, data: Map<String, String>): String {
            if (type !in ANSWER_BY_TYPES) return body
            val deadline = answerByOf(data) ?: return body
            return sentenceAfter(body, context.getString(R.string.push_answer_by, deadline))
        }

        private fun answerByOf(data: Map<String, String>): String? =
            data[PushPayload.ANSWER_BY]?.toLongOrNull()?.let { millis ->
                answerByText(
                    millis,
                    locale = context.resources.configuration.locales[0],
                    is24Hour = DateFormat.is24HourFormat(context)
                )
            }

        private fun actorOf(data: Map<String, String>): String =
            data[PushPayload.ACTOR]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.push_actor_fallback)

        private fun bodyOf(spec: PushTextSpec, data: Map<String, String>): String? {
            val actor = actorOf(data)
            return when (spec.args) {
                BodyArgs.ACTOR_AND_SUBJECT ->
                    context.getString(spec.body, actor, data[PushPayload.SUBJECT].orEmpty())
                BodyArgs.ACTOR -> context.getString(spec.body, actor)
                BodyArgs.ACTOR_AND_DEADLINE -> {
                    val locale = context.resources.configuration.locales[0]
                    val day = data[PushPayload.RETAINED_UNTIL]?.toLongOrNull()
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                        ?.format(localizedDate(DAY_IN_SENTENCE, locale))
                        ?: isoDateText(data[PushPayload.DATE], DAY_IN_SENTENCE, locale)
                    context.getString(spec.body, actor, day)
                }
                BodyArgs.ACTOR_AND_ANSWER_BY -> answerByOf(data)?.let { context.getString(spec.body, actor, it) }
                BodyArgs.DATE -> context.getString(
                    spec.body,
                    isoDateText(data[PushPayload.DATE], DAY_IN_SENTENCE, context.resources.configuration.locales[0])
                )
                BodyArgs.DAY_COUNT -> data[PushPayload.DAY_COUNT]?.toIntOrNull()?.let { count ->
                    context.resources.getQuantityString(spec.body, count, count)
                }
                BodyArgs.EXPENSE -> expenseBody(actor, data)
                BodyArgs.NONE -> context.getString(spec.body)
            }
        }

        private fun expenseBody(actor: String, data: Map<String, String>): String? {
            val locale = context.resources.configuration.locales[0]
            val currency = data[PushPayload.CURRENCY]?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
                ?: return null
            val amount = data[PushPayload.AMOUNT]?.toBigDecimalOrNull()?.let { moneyText(it, currency, locale) }
                ?: return null
            val subject = data[PushPayload.SUBJECT].orEmpty()
            val share = data[PushPayload.SHARE]?.toBigDecimalOrNull()?.let { moneyText(it, currency, locale) }
            return if (share == null) {
                context.getString(R.string.push_expense_added_body, actor, subject, amount)
            } else {
                context.getString(R.string.push_expense_added_body_share, actor, subject, amount, share)
            }
        }

        private fun moneyText(value: BigDecimal, currency: Currency, locale: Locale): String =
            NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }.format(value)

        private fun chatCardOf(data: Map<String, String>): ActivityAnnouncement? =
            ActivityAnnouncement.fromMap(
                mapOf(
                    "kind" to data[PushPayload.ACTIVITY_KIND],
                    "entityType" to data[PushPayload.ACTIVITY_ENTITY_TYPE],
                    "entityId" to data[PushPayload.ACTIVITY_ENTITY_ID],
                    "title" to data[PushPayload.ACTIVITY_TITLE],
                    "whenIso" to data[PushPayload.ACTIVITY_WHEN_ISO],
                    "amount" to data[PushPayload.ACTIVITY_AMOUNT],
                    "currency" to data[PushPayload.ACTIVITY_CURRENCY]
                )
            )

        enum class BodyArgs {
            ACTOR_AND_SUBJECT,
            ACTOR,
            ACTOR_AND_DEADLINE,
            ACTOR_AND_ANSWER_BY,
            DATE,
            DAY_COUNT,
            EXPENSE,
            NONE
        }

        data class PushTextSpec(
            @StringRes val title: Int,
            val body: Int,
            val args: BodyArgs,
            val titleTakesActor: Boolean = false
        )

        companion object {
            private const val TYPE_CHAT_MESSAGE = PushPayload.CHAT_MESSAGE

            val PUSH_TEXT: Map<String, PushTextSpec> = mapOf(
                PushPayload.EXPENSE_ADDED to PushTextSpec(
                    R.string.push_expense_added_title,
                    R.string.push_expense_added_body,
                    BodyArgs.EXPENSE
                ),
                PushPayload.EVENT_CREATED to PushTextSpec(
                    R.string.push_event_created_title,
                    R.string.push_event_created_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.EVENT_IMPORTANT to PushTextSpec(
                    R.string.push_event_important_title,
                    R.string.push_event_important_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.EVENT_UPDATED to PushTextSpec(
                    R.string.push_event_updated_title,
                    R.string.push_event_updated_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.EVENT_DELETED to PushTextSpec(
                    R.string.push_event_deleted_title,
                    R.string.push_event_deleted_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CHILD_INFO_UPDATED to PushTextSpec(
                    R.string.push_child_info_updated_title,
                    R.string.push_child_info_updated_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CHANGE_REQUEST_CREATED to PushTextSpec(
                    R.string.push_change_request_created_title,
                    R.string.push_change_request_created_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CHANGE_REQUEST_ACCEPTED to PushTextSpec(
                    R.string.push_change_request_accepted_title,
                    R.string.push_change_request_accepted_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CHANGE_REQUEST_DECLINED to PushTextSpec(
                    R.string.push_change_request_declined_title,
                    R.string.push_change_request_declined_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CHANGE_REQUEST_CANCELLED to PushTextSpec(
                    R.string.push_change_request_cancelled_title,
                    R.string.push_change_request_cancelled_body,
                    BodyArgs.ACTOR_AND_SUBJECT
                ),
                PushPayload.CUSTODY_PROPOSAL_PROPOSED to PushTextSpec(
                    R.string.push_custody_proposal_proposed_title,
                    R.string.push_custody_proposal_proposed_body,
                    BodyArgs.NONE
                ),
                PushPayload.CUSTODY_PROPOSAL_ACCEPTED to PushTextSpec(
                    R.string.push_custody_proposal_accepted_title,
                    R.string.push_custody_proposal_accepted_body,
                    BodyArgs.NONE
                ),
                PushPayload.CUSTODY_PROPOSAL_DECLINED to PushTextSpec(
                    R.string.push_custody_proposal_declined_title,
                    R.string.push_custody_proposal_declined_body,
                    BodyArgs.NONE
                ),
                PushPayload.DAY_SWAP_OFFERED to PushTextSpec(
                    R.string.push_day_swap_offered_title,
                    R.string.push_day_swap_offered_body,
                    BodyArgs.DATE
                ),
                PushPayload.DAY_SWAP_ACCEPTED to PushTextSpec(
                    R.string.push_day_swap_accepted_title,
                    R.string.push_day_swap_accepted_body,
                    BodyArgs.DATE
                ),
                PushPayload.DAY_SWAP_DECLINED to PushTextSpec(
                    R.string.push_day_swap_declined_title,
                    R.string.push_day_swap_declined_body,
                    BodyArgs.DATE
                ),
                PushPayload.DAY_SWAP_GROUP_OFFERED to PushTextSpec(
                    R.string.push_day_swap_offered_title,
                    R.plurals.push_day_swap_group_offered_body,
                    BodyArgs.DAY_COUNT
                ),
                PushPayload.DAY_SWAP_GROUP_ACCEPTED to PushTextSpec(
                    R.string.push_day_swap_accepted_title,
                    R.plurals.push_day_swap_group_accepted_body,
                    BodyArgs.DAY_COUNT
                ),
                PushPayload.DAY_SWAP_GROUP_DECLINED to PushTextSpec(
                    R.string.push_day_swap_declined_title,
                    R.plurals.push_day_swap_group_declined_body,
                    BodyArgs.DAY_COUNT
                ),
                PushPayload.SPLIT_RATIO_PROPOSED to PushTextSpec(
                    R.string.push_split_ratio_proposed_title,
                    R.string.push_split_ratio_proposed_body,
                    BodyArgs.NONE
                ),
                PushPayload.SPLIT_RATIO_ACCEPTED to PushTextSpec(
                    R.string.push_split_ratio_accepted_title,
                    R.string.push_split_ratio_accepted_body,
                    BodyArgs.NONE
                ),
                PushPayload.SPLIT_RATIO_DECLINED to PushTextSpec(
                    R.string.push_split_ratio_declined_title,
                    R.string.push_split_ratio_declined_body,
                    BodyArgs.NONE
                ),
                PushPayload.SPLIT_RATIO_AGREED to PushTextSpec(
                    R.string.push_split_ratio_agreed_title,
                    R.string.push_split_ratio_agreed_body,
                    BodyArgs.NONE
                ),
                PushPayload.RECORDS_SHARED to PushTextSpec(
                    R.string.push_records_shared_title,
                    R.string.push_records_shared_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.STICKER_RECEIVED to PushTextSpec(
                    R.string.push_sticker_received_title,
                    R.string.push_sticker_received_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.REQUEST_LAST_CHANCE to PushTextSpec(
                    R.string.push_request_last_chance_title,
                    R.string.push_request_last_chance_body,
                    BodyArgs.ACTOR_AND_ANSWER_BY
                ),
                PushPayload.REQUEST_DEADLINE_PASSED to PushTextSpec(
                    R.string.push_request_deadline_passed_title,
                    R.string.push_request_deadline_passed_body,
                    BodyArgs.NONE,
                    titleTakesActor = true
                ),
                PushPayload.REQUEST_NO_ANSWER to PushTextSpec(
                    R.string.push_request_no_answer_title,
                    R.string.push_request_no_answer_body,
                    BodyArgs.NONE,
                    titleTakesActor = true
                ),
                PushPayload.PAIRING_ACCEPTED to PushTextSpec(
                    R.string.push_pairing_accepted_title,
                    R.string.push_pairing_accepted_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.PAIRING_REMOVED to PushTextSpec(
                    R.string.push_pairing_removed_title,
                    R.string.push_pairing_removed_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.PROFESSIONAL_ACCESS_REQUESTED to PushTextSpec(
                    R.string.push_professional_access_requested_title,
                    R.string.push_professional_access_requested_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.CALENDAR_FRIEND_ACCEPTED to PushTextSpec(
                    R.string.push_calendar_friend_accepted_title,
                    R.string.push_calendar_friend_accepted_body,
                    BodyArgs.ACTOR
                ),
                PushPayload.COPARENT_ACCOUNT_DELETED to PushTextSpec(
                    R.string.push_coparent_account_deleted_title,
                    R.string.push_coparent_account_deleted_body,
                    BodyArgs.ACTOR_AND_DEADLINE
                )
            )

            val ANSWER_BY_TYPES: Set<String> = setOf(
                PushPayload.DAY_SWAP_OFFERED,
                PushPayload.DAY_SWAP_GROUP_OFFERED,
                PushPayload.CHANGE_REQUEST_CREATED,
                PushPayload.CUSTODY_PROPOSAL_PROPOSED,
                PushPayload.SPLIT_RATIO_PROPOSED
            )

            private val SENTENCE_END = setOf('.', '!', '?', '…')

            fun sentenceAfter(body: String, sentence: String): String =
                body + (if (body.lastOrNull() in SENTENCE_END) " " else ". ") + sentence
        }
    }

    // ---- SwapPushWording.kt ------------------------------------------------------------------

    sealed interface SwapPushWording {
        data class Gives(val toYou: List<LocalDate>) : SwapPushWording

        data class Asks(val toSender: List<LocalDate>) : SwapPushWording

        data class Both(val toYou: List<LocalDate>, val toSender: List<LocalDate>) : SwapPushWording
    }

    fun swapPushWording(type: String?, data: Map<String, String>): SwapPushWording? {
        if (type != PushPayload.DAY_SWAP_OFFERED && type != PushPayload.DAY_SWAP_GROUP_OFFERED) return null
        val toYou = datesIn(data[PushPayload.DATES_TO_YOU])
        val toSender = datesIn(data[PushPayload.DATES_TO_SENDER])
        return when {
            toYou.isNotEmpty() && toSender.isNotEmpty() -> SwapPushWording.Both(toYou, toSender)
            toYou.isNotEmpty() -> SwapPushWording.Gives(toYou)
            toSender.isNotEmpty() -> SwapPushWording.Asks(toSender)
            else -> null
        }
    }

    private fun datesIn(field: String?): List<LocalDate> =
        field.orEmpty().split(',').mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.sorted()

    // ---- presentation/chat/CardPushText.kt ----------------------------------------------------

    object CardPushText {

        // `cardPushText` in the app; renamed only, so no member is named after this object.
        fun wordCard(context: Context, card: ActivityAnnouncement): String? {
            val locale = context.resources.configuration.locales[0]
            if (card.kind.isSettleUp) {
                val month = card.whenIso?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
                    ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                    ?: card.title
                val resource = CardDecisions.settleUpResource(card.kind, readerIsSender = false)
                return context.wording(resource, month, amountText(card, locale))
            }
            val resource = CardDecisions.resourceFor(card.kind)
            if (CardDecisions.takesMoney(card.kind)) {
                return when (card.kind) {
                    ActivityKind.SUPPORT_PROPOSED, ActivityKind.SUPPORT_ACCEPTED ->
                        context.wording(resource, amountText(card, locale), card.title)
                    else -> context.wording(resource, monthText(card.title, locale), amountText(card, locale))
                }
            }
            return subjectText(context, card, locale)?.let { context.wording(resource, it) }
        }

        private fun subjectText(context: Context, card: ActivityAnnouncement, locale: Locale): String? =
            when (val about = CardDecisions.subjectOf(card)) {
                is CardSubjectText.IsoDate -> isoDateText(about.iso, DAY_WITH_WEEKDAY, locale)
                is CardSubjectText.Res -> context.wording(about.id)
                is CardSubjectText.Raw -> about.text
            }

        @Suppress("DiscouragedApi")
        fun Context.androidStringId(resource: StringResource): Int =
            resources.getIdentifier(resource.key, "string", packageName)

        private fun Context.wording(resource: StringResource, vararg args: Any): String? =
            androidStringId(resource).takeIf { it != 0 }?.let { getString(it, *args) }

        private fun monthText(isoMonth: String, locale: Locale): String =
            runCatching { YearMonth.parse(isoMonth) }.getOrNull()
                ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                ?: isoMonth

        private fun amountText(card: ActivityAnnouncement, locale: Locale): String {
            val amount = card.amount?.toBigDecimalOrNull() ?: return card.amount.orEmpty()
            val currency = card.currency ?: return amount.toPlainString()
            return currencyFormat(currency, locale).format(amount)
        }
    }

    // ---- presentation/chat/ActivityCardText.kt, the decisions CardPushText read ---------------

    sealed interface CardSubjectText {
        data class IsoDate(val iso: String) : CardSubjectText

        data class Res(val id: StringResource) : CardSubjectText

        data class Raw(val text: String) : CardSubjectText
    }

    object CardDecisions {

        @Suppress("CyclomaticComplexMethod")
        fun resourceFor(kind: ActivityKind): StringResource = when (kind) {
            ActivityKind.EVENT_CREATED -> Res.string.activity_event_created
            ActivityKind.EVENT_UPDATED -> Res.string.activity_event_updated
            ActivityKind.EVENT_DELETED -> Res.string.activity_event_deleted
            ActivityKind.EVENT_ACCEPTED -> Res.string.activity_event_accepted
            ActivityKind.EVENT_DECLINED -> Res.string.activity_event_declined
            ActivityKind.CHANGE_REQUESTED -> Res.string.activity_change_requested
            ActivityKind.CHANGE_ACCEPTED -> Res.string.activity_change_accepted
            ActivityKind.CHANGE_DECLINED -> Res.string.activity_change_declined
            ActivityKind.EXPENSE_ADDED -> Res.string.activity_expense_added
            ActivityKind.EXPENSE_UPDATED -> Res.string.activity_expense_updated
            ActivityKind.EXPENSE_DELETED -> Res.string.activity_expense_deleted
            ActivityKind.EXPENSE_QUESTIONED -> Res.string.activity_expense_questioned
            ActivityKind.DAY_SWAP_OFFERED -> Res.string.activity_day_swap_offered
            ActivityKind.DAY_SWAP_ACCEPTED -> Res.string.activity_day_swap_accepted
            ActivityKind.DAY_SWAP_DECLINED -> Res.string.activity_day_swap_declined
            ActivityKind.CUSTODY_PROPOSED -> Res.string.activity_custody_proposed
            ActivityKind.CUSTODY_ACCEPTED -> Res.string.activity_custody_accepted
            ActivityKind.CUSTODY_DECLINED -> Res.string.activity_custody_declined
            ActivityKind.REQUEST_LAST_CHANCE -> Res.string.activity_request_last_chance
            ActivityKind.REQUEST_UNANSWERED -> Res.string.activity_request_unanswered
            ActivityKind.SETTLE_UP_OWED -> Res.string.activity_settle_up_owed_to_you
            ActivityKind.SETTLE_UP_OWING -> Res.string.activity_settle_up_you_owe
            ActivityKind.SPLIT_PROPOSED -> Res.string.activity_split_proposed
            ActivityKind.SPLIT_ACCEPTED -> Res.string.activity_split_accepted
            ActivityKind.SPLIT_DECLINED -> Res.string.activity_split_declined
            ActivityKind.SPLIT_WITHDRAWN -> Res.string.activity_split_withdrawn
            ActivityKind.DAY_SWAP_WITHDRAWN -> Res.string.activity_day_swap_withdrawn
            ActivityKind.CUSTODY_WITHDRAWN -> Res.string.activity_custody_withdrawn
            ActivityKind.STICKER_SENT -> Res.string.activity_sticker_sent
            ActivityKind.SETTLE_UP_PAID -> Res.string.activity_settle_up_paid
            ActivityKind.SETTLE_UP_CONFIRMED -> Res.string.activity_settle_up_confirmed
            ActivityKind.SUPPORT_PROPOSED -> Res.string.activity_support_proposed
            ActivityKind.SUPPORT_ACCEPTED -> Res.string.activity_support_accepted
            ActivityKind.SUPPORT_DECLINED -> Res.string.activity_support_declined
            ActivityKind.SUPPORT_WITHDRAWN -> Res.string.activity_support_withdrawn
            ActivityKind.SUPPORT_PAID -> Res.string.activity_support_paid
            ActivityKind.SUPPORT_CONFIRMED -> Res.string.activity_support_confirmed
        }

        fun takesMoney(kind: ActivityKind): Boolean =
            kind.isPayment || kind == ActivityKind.SUPPORT_PROPOSED || kind == ActivityKind.SUPPORT_ACCEPTED

        fun settleUpResource(kind: ActivityKind, readerIsSender: Boolean): StringResource =
            if (kind.readerPays(readerIsSender)) {
                Res.string.activity_settle_up_you_owe
            } else {
                Res.string.activity_settle_up_owed_to_you
            }

        fun subjectOf(card: ActivityAnnouncement): CardSubjectText = when (card.entityType) {
            ActivityEntityType.DAY_SWAP -> CardSubjectText.IsoDate(card.title)
            ActivityEntityType.CUSTODY_PROPOSAL -> CardSubjectText.Res(Res.string.activity_subject_custody_schedule)
            ActivityEntityType.SPLIT_PROPOSAL -> CardSubjectText.Res(Res.string.activity_subject_expense_split)
            ActivityEntityType.STICKER -> CardSubjectText.Res(
                StickerKind.fromWire(card.title)?.let { StickerText.phrase(it) }
                    ?: Res.string.activity_subject_sticker
            )
            ActivityEntityType.PAYMENT,
            ActivityEntityType.SUPPORT_AGREEMENT,
            ActivityEntityType.EVENT,
            ActivityEntityType.CHANGE_REQUEST,
            ActivityEntityType.EXPENSE -> CardSubjectText.Raw(card.title)
        }
    }

    // ---- the three reminders, as DueReminders, SupportReminderWorker and RequestNotices word them

    /** `DueReminders.post`: the event's title over "Starts at <startTime>". */
    fun eventReminder(words: Context, title: String, startTime: String): PushText =
        PushText(title, words.getString(R.string.reminder_notification_text, startTime))

    /**
     * `SupportReminderWorker.post` with `nameOf`'s fallback: the due day in the medium style and the
     * monthly amount in [currency], to [payeeName] or "Your co-parent".
     */
    fun supportReminder(
        words: Context,
        dueDate: LocalDate,
        currency: String,
        amountMinor: Long,
        payeeName: String?
    ): PushText {
        val name = payeeName?.takeIf { it.isNotBlank() } ?: words.getString(R.string.push_actor_fallback)
        val locale = words.resources.configuration.locales[0]
        val due = dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val amount = currencyFormat(currency, locale).format(MinorUnits.toDecimal(amountMinor))
        val title = words.getString(R.string.support_reminder_title, due)
        val body = words.getString(R.string.support_reminder_body, amount, name)
        return PushText(title, body)
    }

    /** `RequestNotices.remind` with `nameOf`'s fallback. */
    fun requestReminder(words: Context, proposerName: String?, answerByMillis: Long): PushText {
        val name = proposerName?.takeIf { it.isNotBlank() } ?: words.getString(R.string.push_actor_fallback)
        val until = answerByText(
            answerByMillis,
            locale = words.resources.configuration.locales[0],
            is24Hour = DateFormat.is24HourFormat(words)
        )
        return PushText(
            words.getString(R.string.request_notice_reminder_title),
            words.getString(R.string.request_notice_reminder_body, name, until)
        )
    }
}
