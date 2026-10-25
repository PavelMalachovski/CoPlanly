package com.coparently.app.data.payment

import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.time.YearMonth

/**
 * The wire form of `payments/{id}` (25 October 2026), in both directions — the one place it is
 * defined, like `EventDocument` for events.
 *
 * The amount is a plain decimal string with two places (`"450.00"`), as the settle-up card already
 * carries it, never a floating-point number: the rules bound its shape, and a phone in any locale
 * reads it back the same.
 */
object PaymentDocument {

    /** The fields the payer writes; `paidAt` is the server's clock, which the rule requires. */
    fun paid(payment: Payment, paidAt: Any): Map<String, Any> = mapOf(
        "familyId" to payment.familyId,
        "participants" to listOf(payment.payerUid, payment.payeeUid).sorted(),
        "kind" to payment.kind.wire,
        "month" to payment.month.toString(),
        "currency" to payment.currency,
        "amount" to amountText(payment.amount),
        "payerUid" to payment.payerUid,
        "payeeUid" to payment.payeeUid,
        "paidBy" to payment.payerUid,
        "paidAt" to paidAt
    )

    /** The fields the parent paid writes when the money arrived. */
    fun confirmed(byUid: String, confirmedAt: Any): Map<String, Any> =
        mapOf("confirmedBy" to byUid, "confirmedAt" to confirmedAt)

    /** The payment [data] describes, or null for one this build cannot read. */
    @Suppress("ReturnCount") // one early answer per field a payment cannot do without
    fun read(id: String, data: Map<String, Any?>): Payment? {
        val kind = PaymentKind.fromWire(data["kind"] as? String) ?: return null
        val month = (data["month"] as? String)?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return null
        val amount = (data["amount"] as? String)?.toBigDecimalOrNull() ?: return null
        return Payment(
            id = id,
            familyId = data["familyId"] as? String ?: return null,
            kind = kind,
            month = month,
            currency = data["currency"] as? String ?: return null,
            amount = amount,
            payerUid = data["payerUid"] as? String ?: return null,
            payeeUid = data["payeeUid"] as? String ?: return null,
            paidAtMillis = (data["paidAt"] as? Timestamp)?.toDate()?.time,
            confirmedAtMillis = (data["confirmedAt"] as? Timestamp)?.toDate()?.time
        )
    }

    /** [amount] as the wire carries it: two decimal places, no grouping, a dot — the card's form. */
    fun amountText(amount: BigDecimal): String = PaymentQr.amountText(amount)
}
