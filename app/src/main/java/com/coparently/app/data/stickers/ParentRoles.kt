package com.coparently.app.data.stickers

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * How each parent's stickers address them: `users/{uid}.parentRole`, "dad", "mom" or "parent",
 * written by its owner alone (`firestore.rules`' `parentRoleValid`) — October 2026, owner decision.
 *
 * Firestore only, like `PaymentAccounts`: the receiving phone words a sticker from its own
 * parent's answer, so nothing but the signed-in parent's own value is ever read, and no Room
 * column is needed. It is read **only** to word a sticker; every other label in the app stays the
 * person's name (CLAUDE.md, "Parent colours identify a person, not a role").
 */
class ParentRoles(
    private val firestore: FirestoreClient,
    private val auth: FirebaseAuthService
) {

    /**
     * The signed-in parent's answer, live. [ParentRoleAnswer.Loading] until the server has
     * spoken: a cached snapshot without the field may only mean the cache is cold, and a failed
     * read says nothing at all, so neither asks the question.
     */
    fun observeMine(): Flow<ParentRoleAnswer> {
        val uid = auth.getCurrentUser()?.uid ?: return flowOf(ParentRoleAnswer.Loading)
        return firestore.observe(DocumentPath(USERS, uid))
            .map { snapshot ->
                // `as? String`: a number there must not throw inside the listener.
                val role = ParentRole.fromWire(snapshot.data?.get(FIELD) as? String)
                when {
                    role != null -> ParentRoleAnswer.Answered(role)
                    snapshot.isFromCache -> ParentRoleAnswer.Loading
                    else -> ParentRoleAnswer.Unanswered
                }
            }
            .catch { error ->
                logger.w(TAG, "The parent role could not be read", error)
                emit(ParentRoleAnswer.Loading)
                // A failed listener says nothing more, and the question stays unasked until the
                // collector goes away — as the SDK listener it replaced did.
                awaitCancellation()
            }
            .distinctUntilChanged()
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
            firestore.set(DocumentPath(USERS, uid), mapOf(FIELD to role.wire), SetMerge.Merge)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "The parent role could not be saved", e)
            false
        }
    }

    private companion object {
        const val TAG = "ParentRoles"
        const val USERS = "users"
        const val FIELD = "parentRole"
    }
}
