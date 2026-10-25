package com.coparently.app.domain.payments

import java.math.BigDecimal
import java.time.YearMonth

/**
 * What a payment between the two parents is for (25 October 2026).
 *
 * @property wire The value stored in `payments/{id}.kind`, which `firestore.rules` lists.
 */
enum class PaymentKind(val wire: String) {
    /** A month's settle-up of the shared expenses. */
    EXPENSES("expenses"),

    /** A month of the agreed child support. */
    SUPPORT("support");

    companion object {
        /** The kind [value] names, or null for one this build does not know. */
        fun fromWire(value: String?): PaymentKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One payment from one parent of a family to the other, for one month, in one currency: the payer
 * says they paid, and the parent paid says the money arrived (owner decision, 25 October 2026:
 * "when I settle up and send the money, it should say it is settled").
 *
 * Two steps, each written by the parent it is about: nobody can say the other paid, and nobody
 * can say money arrived to the other.
 *
 * @property paidAtMillis When the server stored the payer's word, or null until it has.
 * @property confirmedAtMillis When the parent paid confirmed it, or null while they have not.
 */
data class Payment(
    val id: String,
    val familyId: String,
    val kind: PaymentKind,
    val month: YearMonth,
    val currency: String,
    val amount: BigDecimal,
    val payerUid: String,
    val payeeUid: String,
    val paidAtMillis: Long? = null,
    val confirmedAtMillis: Long? = null
) {
    /** Whether the parent paid has said the money arrived. */
    val confirmed: Boolean get() = confirmedAtMillis != null
}

/** Where a month's payment stands for the parent reading it. */
enum class PaymentState {
    /** Nothing recorded yet. */
    NONE,

    /** The reader paid and waits for the co-parent to confirm. */
    PAID_BY_ME,

    /** The co-parent says they paid; the reader is asked whether it arrived. */
    PAID_TO_ME,

    /** The parent paid confirmed it: the month is settled. */
    CONFIRMED;

    companion object {
        /** [payment]'s state for the parent [readerUid]. */
        fun of(payment: Payment?, readerUid: String?): PaymentState = when {
            payment == null -> NONE
            payment.confirmed -> CONFIRMED
            payment.payerUid == readerUid -> PAID_BY_ME
            else -> PAID_TO_ME
        }
    }
}

/** The one place a payment's document id is spelt, so a phone and the rules agree on it. */
object PaymentId {

    /**
     * `{familyId}__{kind}__{yyyy-MM}__{currency}`: one payment per family, kind, month and
     * currency. The rules rebuild it from the document's own fields, so a payment cannot be filed
     * under another family or month.
     */
    fun of(familyId: String, kind: PaymentKind, month: YearMonth, currency: String): String =
        "${familyId}__${kind.wire}__${month}__$currency"
}
