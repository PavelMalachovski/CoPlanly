package com.coparently.app.data.payment

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.repository.UserRepository
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.abs

/**
 * Posts a month's settle-up to the thread of the family on screen as a card, never as text.
 *
 * Settle up used to put a sentence into the composer, so the amount could be edited before it was
 * sent (tester report, October 2026; owner decision: it must not be). A card carries the amount as
 * a fact the reader's phone words, in the reader's language, and a message cannot be edited once
 * it is in the thread. The payer's phone shows a payment code under it.
 */
class SettleUpMessages @Inject constructor(
    private val activityAnnouncer: ActivityAnnouncer,
    private val userRepository: UserRepository
) {

    /**
     * Posts the card.
     *
     * @param netForMe The month's balance from this parent's side: positive when the co-parent
     *   owes them, negative when they owe the co-parent.
     * @param currency The balance's currency; the app never converts between currencies.
     * @param month The month the balance is for.
     */
    suspend fun post(netForMe: Double, currency: String, month: YearMonth) {
        activityAnnouncer.announce(
            announcement = cardFor(netForMe, currency, month),
            senderName = userRepository.getCurrentUser()?.name.orEmpty()
        )
    }

    internal companion object {
        /** The card [post] sends. */
        fun cardFor(netForMe: Double, currency: String, month: YearMonth) = ActivityAnnouncement(
            kind = if (netForMe > 0) ActivityKind.SETTLE_UP_OWED else ActivityKind.SETTLE_UP_OWING,
            entityType = ActivityEntityType.EXPENSE,
            entityId = "settle_${month}_$currency",
            title = month.toString(),
            whenIso = month.atDay(1).toString(),
            amount = PaymentQr.amountText(BigDecimal.valueOf(abs(netForMe))),
            currency = currency
        )
    }
}
