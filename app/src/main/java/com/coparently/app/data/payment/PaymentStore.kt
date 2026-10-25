package com.coparently.app.data.payment

import android.util.Log
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentId
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Payments between the two parents of a family (25 October 2026): `payments/{id}`, one per
 * family, kind, month and currency ([PaymentId]).
 *
 * Firestore only, no Room table, like the requests' clocks: a payment's state is the server's,
 * written once by each parent, and a phone offline simply does not show it yet. Each step posts a
 * card in the family's thread, pushed like any message — no push type of its own.
 */
@Singleton
class PaymentStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuthService,
    private val userRepository: UserRepository,
    private val activityAnnouncer: ActivityAnnouncer
) {

    private fun signedInUid(): String? = auth.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }

    /**
     * Every payment of [familyId] the signed-in parent takes part in, live; null while it cannot
     * be read. Keyed on `participants`, the field the rule reads.
     */
    fun observe(familyId: String): Flow<List<Payment>?> {
        val uid = signedInUid() ?: return flowOf(null)
        return callbackFlow {
            val registration = firestore.collection(COLLECTION)
                .whereArrayContains("participants", uid)
                .whereEqualTo("familyId", familyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Payments could not be read", error)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val read = snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.data?.let { PaymentDocument.read(doc.id, it) } }
                    trySend(read)
                }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    /**
     * The payment [id] as the server holds it now: null inside when there is none, a failure when
     * the server could not answer — a reminder that is not sure keeps quiet.
     */
    suspend fun readFromServer(id: String): Result<Payment?> = try {
        val snapshot = firestore.collection(COLLECTION).document(id).get(Source.SERVER).await()
        Result.success(snapshot.data?.let { PaymentDocument.read(snapshot.id, it) })
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Result.failure(e)
    }

    /**
     * Records that the signed-in parent paid [amount] of [currency] for [month] to the other parent
     * of [familyId], and posts the card.
     *
     * @return Whether the payment was stored.
     */
    suspend fun markPaid(
        familyId: String,
        kind: PaymentKind,
        month: YearMonth,
        currency: String,
        amount: BigDecimal
    ): Boolean {
        val me = signedInUid() ?: return false
        val payee = FamilyKey.membersOf(familyId)?.toList()?.singleOrNull { it != me } ?: return false
        val payment = Payment(
            id = PaymentId.of(familyId, kind, month, currency),
            familyId = familyId,
            kind = kind,
            month = month,
            currency = currency,
            amount = amount,
            payerUid = me,
            payeeUid = payee
        )
        val stored = write {
            firestore.collection(COLLECTION).document(payment.id)
                .set(PaymentDocument.paid(payment, FieldValue.serverTimestamp())).await()
        }
        if (stored) {
            val card = if (kind == PaymentKind.SUPPORT) ActivityKind.SUPPORT_PAID else ActivityKind.SETTLE_UP_PAID
            announce(payment, card)
        }
        return stored
    }

    /**
     * Records that the money of [payment] arrived — only its payee may — and posts the card.
     *
     * @return Whether the confirmation was stored.
     */
    suspend fun confirm(payment: Payment): Boolean {
        val me = signedInUid() ?: return false
        if (payment.payeeUid != me || payment.confirmed) return false
        val stored = write {
            firestore.collection(COLLECTION).document(payment.id)
                .update(PaymentDocument.confirmed(me, FieldValue.serverTimestamp())).await()
        }
        if (stored) {
            val kind = if (payment.kind == PaymentKind.SUPPORT) {
                ActivityKind.SUPPORT_CONFIRMED
            } else {
                ActivityKind.SETTLE_UP_CONFIRMED
            }
            announce(payment, kind)
        }
        return stored
    }

    /** The card for [payment], in the thread of its own family with the other parent. */
    private suspend fun announce(payment: Payment, kind: ActivityKind) {
        val me = signedInUid() ?: return
        val other = if (payment.payerUid == me) payment.payeeUid else payment.payerUid
        activityAnnouncer.announce(
            cardFor(payment, kind),
            senderName = userRepository.getCurrentUser()?.name.orEmpty(),
            partnerUid = other
        )
    }

    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "A payment could not be stored", e)
        false
    }

    /** What a payment's chat card carries. */
    companion object {
        /** The collection's name, which `firestore.rules` matches. */
        const val COLLECTION = "payments"
        private const val TAG = "PaymentStore"

        /** [payment]'s card of [kind]: `PAYMENT`, its id, the month and the amount. */
        internal fun cardFor(payment: Payment, kind: ActivityKind) = ActivityAnnouncement(
            kind = kind,
            entityType = ActivityEntityType.PAYMENT,
            entityId = payment.id,
            title = payment.month.toString(),
            whenIso = payment.month.atDay(1).toString(),
            amount = PaymentDocument.amountText(payment.amount),
            currency = payment.currency
        )
    }
}
