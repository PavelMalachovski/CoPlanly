package com.coparently.app.data.stickers

import android.util.Log
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
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
 * How each parent's stickers address them: `users/{uid}.parentRole`, "dad", "mom" or "parent",
 * written by its owner alone (`firestore.rules`' `parentRoleValid`) — October 2026, owner decision.
 *
 * Firestore only, like `PaymentAccounts`: the receiving phone words a sticker from its own
 * parent's answer, so nothing but the signed-in parent's own value is ever read, and no Room
 * column is needed. It is read **only** to word a sticker; every other label in the app stays the
 * person's name (CLAUDE.md, "Parent colours identify a person, not a role").
 */
@Singleton
class ParentRoles @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuthService
) {

    /**
     * The signed-in parent's answer, live. [ParentRoleAnswer.Loading] until the server has
     * spoken: a cached snapshot without the field may only mean the cache is cold, and a failed
     * read says nothing at all, so neither asks the question.
     */
    fun observeMine(): Flow<ParentRoleAnswer> {
        val uid = auth.getCurrentUser()?.uid ?: return flowOf(ParentRoleAnswer.Loading)
        return callbackFlow {
            val registration = firestore.collection(USERS).document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "The parent role could not be read", error)
                    trySend(ParentRoleAnswer.Loading)
                    return@addSnapshotListener
                }
                // `as? String`, never `getString`: a number there would throw inside the listener.
                val role = ParentRole.fromWire(snapshot?.get(FIELD) as? String)
                trySend(
                    when {
                        role != null -> ParentRoleAnswer.Answered(role)
                        snapshot == null || snapshot.metadata.isFromCache -> ParentRoleAnswer.Loading
                        else -> ParentRoleAnswer.Unanswered
                    }
                )
            }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    /**
     * Sets the signed-in parent's answer. A merge of one key, so nothing else on the profile is
     * touched.
     *
     * @return Whether the write landed.
     */
    suspend fun setMine(role: ParentRole): Boolean {
        val uid = auth.getCurrentUser()?.uid ?: return false
        return try {
            firestore.collection(USERS).document(uid).set(mapOf(FIELD to role.wire), SetOptions.merge()).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "The parent role could not be saved", e)
            false
        }
    }

    private companion object {
        const val TAG = "ParentRoles"
        const val USERS = "users"
        const val FIELD = "parentRole"
    }
}
