package com.coparently.app.presentation.chat

import android.content.Context
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.utils.DAY_WITH_WEEKDAY
import com.coparently.app.utils.isoDateText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A chat card's sentence for its push, worded on the receiving phone in [context]'s language
 * (notification audit, 9 November 2026).
 *
 * Every card used to be pushed with its base-English fallback (`ActivityFallbackText`), so a Czech
 * or Russian parent read "New event: Zubař" on the lock screen and the card in their own language
 * in the thread. This is the thread's sentence — `MessageItem` with `settleUpCardText` and
 * [moneyCardText] — composed without Compose, because a push is worded in a service; both read
 * [ActivityCardText]'s decisions, so a new [ActivityKind] reaches the two at once. A push is always
 * the co-parent's card, so its reader is never the sender.
 */
internal fun cardPushText(context: Context, card: ActivityAnnouncement): String {
    if (card.kind.isSettleUp) {
        val month = card.whenIso?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
            ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
            ?: card.title
        return context.getString(
            ActivityCardText.settleUpResource(card.kind, readerIsSender = false),
            month,
            amountText(card)
        )
    }
    val resource = ActivityCardText.resourceFor(card.kind)
    if (ActivityCardText.takesMoney(card.kind)) {
        return when (card.kind) {
            // `title` is the day of the month the agreement names.
            ActivityKind.SUPPORT_PROPOSED, ActivityKind.SUPPORT_ACCEPTED ->
                context.getString(resource, amountText(card), card.title)
            // `title` is the month, `2026-10`.
            else -> context.getString(resource, monthText(card.title), amountText(card))
        }
    }
    val subject = when (val about = ActivityCardText.subjectOf(card)) {
        is CardSubjectText.IsoDate -> isoDateText(about.iso, DAY_WITH_WEEKDAY)
        is CardSubjectText.Res -> context.getString(about.id)
        is CardSubjectText.Raw -> about.text
    }
    return context.getString(resource, subject)
}

/** The card's amount in the reader's conventions and its own currency, or as it came. */
private fun amountText(card: ActivityAnnouncement): String {
    val amount = card.amount?.toBigDecimalOrNull() ?: return card.amount.orEmpty()
    val currency = card.currency ?: return amount.toPlainString()
    return currencyFormat(currency).format(amount)
}
