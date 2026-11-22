package com.coparently.app.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.time.IsoText
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the family's Alimony screen from an alimony card ([ChatCardTarget.Support]), or null where
 * the thread cannot. A CompositionLocal for [LocalChatEventCard]'s reason: the detekt baseline keys
 * entries on `MessageItem`'s signature.
 */
val LocalOpenSupport = compositionLocalOf<(() -> Unit)?> { null }

/**
 * A payment or alimony card's sentence in the reader's language (25 October 2026): the month in
 * the reader's words and the amount in the reader's conventions, in the currency it was paid in.
 * Only for the kinds [ActivityCardText.takesMoney] names.
 */
@Composable
internal fun moneyCardText(card: ActivityAnnouncement): String {
    val amount = card.amount?.let { ReaderMoneyText.amount(it, card.currency) } ?: card.amount.orEmpty()
    val resource = ActivityCardText.resourceFor(card.kind)
    return when (card.kind) {
        // `title` is the day of the month the agreement names.
        ActivityKind.SUPPORT_PROPOSED, ActivityKind.SUPPORT_ACCEPTED -> stringResource(resource, amount, card.title)
        // `title` is the month, `2026-10`.
        else -> stringResource(resource, monthText(card.title), amount)
    }
}

/** `2026-10` as the reader's language names the month ("October 2026"), or as it is. */
internal fun monthText(isoMonth: String): String =
    runCatching { IsoText.parseYearMonth(isoMonth) }.getOrNull()
        ?.let { ReaderDateText.pattern(it, "LLLL yyyy") }
        ?: isoMonth

/**
 * A settle-up card's sentence on this phone — "you owe" or "you are owed", by who reads it — with
 * the month and the amount in the reader's language. What to pay, and "I paid", are on Home's Money
 * card and the Expenses header since November 2026, no longer under the card.
 */
@Composable
internal fun settleUpCardText(card: ActivityAnnouncement, isCurrentUser: Boolean): String {
    val month = remember(card.whenIso) {
        card.whenIso?.let { iso -> runCatching { IsoText.parseDate(iso) }.getOrNull() }
            ?.let { ReaderDateText.pattern(it, "LLLL yyyy") }
            ?: card.title
    }
    val amount = card.amount?.let { ReaderMoneyText.amount(it, card.currency) } ?: card.amount.orEmpty()
    return stringResource(ActivityCardText.settleUpResource(card.kind, isCurrentUser), month, amount)
}
