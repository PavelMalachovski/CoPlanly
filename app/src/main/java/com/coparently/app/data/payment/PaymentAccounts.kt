package com.coparently.app.data.payment

import android.util.Log
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.payment.Iban
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bank account each parent receives a settle-up payment on: `users/{uid}.paymentIban`, written
 * by its owner alone and read by their co-parent, who pays to it (tester feedback, October 2026;
 * owner decision: each parent types their own IBAN).
 *
 * Firestore only, no Room column: the co-parent's profile lives in Firestore alone (only the
 * signed-in parent has a Room `users` row), and this parent's own value is read where it is
 * edited. A value that is not a valid IBAN reads as none, so a payment code is never built from
 * one.
 */
@Singleton
class PaymentAccounts @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuthService
) {

    /**
     * The IBAN [uid] receives payments on, live; null when none is set, it is not a valid IBAN,
     * or the profile cannot be read.
     */
    fun observeIban(uid: String): Flow<String?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "A payment account could not be read", error)
                trySend(null)
            } else {
                // `as? String`, never `getString`: the document is its owner's to write, and a
                // number there would throw inside the listener.
                trySend(Iban.normalize(snapshot?.get(FIELD) as? String))
            }
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    /** The signed-in parent's own IBAN, live. */
    fun observeMine(): Flow<String?> = auth.getCurrentUser()?.uid?.let(::observeIban) ?: flowOf(null)

    /**
     * Sets the signed-in parent's IBAN, or removes it for null. A merge of one key, so nothing
     * else on the profile is touched.
     *
     * @return Whether the write landed.
     */
    suspend fun setMine(iban: String?): Boolean {
        val uid = auth.getCurrentUser()?.uid ?: return false
        val value: Any = iban?.let(Iban::normalize) ?: FieldValue.delete()
        return try {
            firestore.collection(USERS).document(uid).set(mapOf(FIELD to value), SetOptions.merge()).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "The payment account could not be saved", e)
            false
        }
    }

    private companion object {
        const val TAG = "PaymentAccounts"
        const val USERS = "users"
        const val FIELD = "paymentIban"
    }
}
