package com.coparently.app.data.payment

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.payment.Iban
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The bank account each parent receives a settle-up payment on: `users/{uid}.paymentIban`, written
 * by its owner alone and read by their co-parent, who pays to it (tester feedback, October 2026;
 * owner decision: each parent types their own IBAN).
 *
 * Firestore only, no Room column: the co-parent's profile lives in Firestore alone (only the
 * signed-in parent has a Room `users` row), and this parent's own value is read where it is
 * edited. A value that is not a valid IBAN reads as none, so a payment code is never built from
 * one.
 *
 * On [FirestoreClient] and [SignedInUser] since MON-24 phase 2 (Task 8e): the same listener and
 * merge as the Android SDK calls it made.
 */
class PaymentAccounts(
    private val firestore: FirestoreClient,
    private val auth: SignedInUser
) {

    /**
     * The IBAN [uid] receives payments on, live; null when none is set, it is not a valid IBAN,
     * or the profile cannot be read.
     */
    fun observeIban(uid: String): Flow<String?> =
        firestore.observe(DocumentPath(USERS, uid))
            // `as? String`, never `string()`: the document is its owner's to write, and a number
            // there would throw inside the listener.
            .map<StoredDocument, String?> { snapshot -> Iban.normalize(snapshot.data?.get(FIELD) as? String) }
            .catch { error ->
                logger.w(TAG, "A payment account could not be read", error)
                emit(null)
                // Not closed, as the SDK listener it replaced was not: nothing more arrives.
                awaitCancellation()
            }
            .distinctUntilChanged()

    /** The signed-in parent's own IBAN, live. */
    fun observeMine(): Flow<String?> = auth.getCurrentUid()?.let(::observeIban) ?: flowOf(null)

    /**
     * Sets the signed-in parent's IBAN, or removes it for null. A merge of one key, so nothing
     * else on the profile is touched.
     *
     * @return Whether the write landed.
     */
    suspend fun setMine(iban: String?): Boolean {
        val uid = auth.getCurrentUid() ?: return false
        val value: Any = iban?.let(Iban::normalize) ?: ServerValue.Delete
        return try {
            firestore.set(DocumentPath(USERS, uid), mapOf(FIELD to value), SetMerge.Merge)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "The payment account could not be saved", e)
            false
        }
    }

    private companion object {
        const val TAG = "PaymentAccounts"
        const val USERS = "users"
        const val FIELD = "paymentIban"
    }
}
