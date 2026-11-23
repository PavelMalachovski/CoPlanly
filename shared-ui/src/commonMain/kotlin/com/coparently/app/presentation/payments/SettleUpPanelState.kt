package com.coparently.app.presentation.payments

import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentState
import kotlinx.datetime.YearMonth
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where one month's settle-up stands for the parent reading it, in one currency — the five states
 * the Money card and the Expenses header draw (redesign, November 2026: settle up leaves the chat
 * for Money). Every state knows its [month], [currency] and the amount it is about, in minor units.
 */
sealed interface SettleUpPanelState {
    /** The month being settled. */
    val month: YearMonth

    /** The currency of [amountMinor]; the app never converts between currencies. */
    val currency: String

    /** The amount, in hundredths: 62000 is 620.00. */
    val amountMinor: Long

    /** The co-parent owes this parent and nothing is recorded: "Ask Bob to settle up". */
    data class AskToSettle(
        override val month: YearMonth,
        override val currency: String,
        override val amountMinor: Long,
        val netForMe: Double
    ) : SettleUpPanelState

    /** This parent owes the co-parent and has not said they paid: the payment code and "I paid". */
    data class Pay(
        override val month: YearMonth,
        override val currency: String,
        override val amountMinor: Long
    ) : SettleUpPanelState

    /** This parent said they paid; the co-parent has not confirmed it yet. */
    data class WaitingForConfirm(
        override val month: YearMonth,
        override val currency: String,
        override val amountMinor: Long,
        val paidAtMillis: Long?
    ) : SettleUpPanelState

    /** The co-parent says they paid this parent; "Money arrived" once the bank shows it. */
    data class Confirm(
        override val month: YearMonth,
        override val currency: String,
        override val amountMinor: Long,
        val paidAtMillis: Long?,
        val payment: Payment
    ) : SettleUpPanelState

    /** The parent paid confirmed the money arrived: the month is settled. */
    data class Settled(
        override val month: YearMonth,
        override val currency: String,
        override val amountMinor: Long
    ) : SettleUpPanelState
}

/** Below this a balance is settled — the Expenses screen's and Home's figure, so they agree. */
private const val SETTLED_EPSILON = 0.01

/** Hundredths in a unit, for the fallback rounding of [minorUnitsOf]. */
private const val MINOR_PER_UNIT = 100.0

/** Half a hundredth, for rounding half up. */
private const val HALF = 0.5

/**
 * Which [SettleUpPanelState] a month's balance and its recorded payment come to for [myUid].
 *
 * A recorded payment decides first, whatever the balance still reads, because the expenses do not
 * change when one parent pays the other (25 October 2026). With none: owed to this parent is
 * [SettleUpPanelState.AskToSettle], owed by them [SettleUpPanelState.Pay], and nothing owed — or a
 * split that cannot be worked out, while both parents read one slot — is null: no panel at all.
 *
 * @param netForMe The month's balance for this parent: positive when the co-parent owes them
 * @param splitKnown Whether the two parents can be told apart, as `ExpenseBalance.splitKnown`
 * @param payment The month's recorded payment in [currency], or null when there is none
 * @param myUid The signed-in parent
 */
@Suppress("LongParameterList") // the balance, the payment and the three facts that place them
fun settleUpPanelState(
    netForMe: Double,
    splitKnown: Boolean,
    currency: String,
    month: YearMonth,
    payment: Payment?,
    myUid: String?
): SettleUpPanelState? {
    if (payment != null) {
        return when (PaymentState.of(payment, myUid)) {
            PaymentState.CONFIRMED -> SettleUpPanelState.Settled(month, currency, payment.amountMinor)
            PaymentState.PAID_BY_ME ->
                SettleUpPanelState.WaitingForConfirm(month, currency, payment.amountMinor, payment.paidAtMillis)
            PaymentState.PAID_TO_ME ->
                SettleUpPanelState.Confirm(month, currency, payment.amountMinor, payment.paidAtMillis, payment)
            PaymentState.NONE -> null
        }
    }
    if (!splitKnown || abs(netForMe) < SETTLED_EPSILON) return null
    val owed = minorUnitsOf(abs(netForMe))
    return if (netForMe > 0) {
        SettleUpPanelState.AskToSettle(month, currency, owed, netForMe)
    } else {
        SettleUpPanelState.Pay(month, currency, owed)
    }
}

/**
 * [value] in hundredths, rounded half up as `MinorUnits.fromDouble` reads a double (its decimal
 * text through [DecimalText.parseHalfUp]); arithmetic only where that text has an exponent.
 */
internal fun minorUnitsOf(value: Double): Long =
    DecimalText.parseHalfUp(value.toString(), PaymentQr.MONEY_SCALE)
        ?: floor(value * MINOR_PER_UNIT + HALF).toLong()
