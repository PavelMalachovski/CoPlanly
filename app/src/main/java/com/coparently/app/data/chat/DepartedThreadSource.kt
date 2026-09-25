package com.coparently.app.data.chat

import android.util.Log
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.domain.chat.DepartedThread
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The conversations kept for this parent after a co-parent deleted their account ([DepartedThread]).
 *
 * After the deletion the pairing is gone, so [ChatPartnerSource] no longer names that co-parent and
 * nothing can derive the thread's id from a pairing; the server's marks on the conversation document
 * are the only record that the thread exists and until when. This reads them — a Firestore listener
 * for the chat screens, a one-shot read for the export — and keeps them in memory only. No Room
 * column: a thread that is going away in 30 days needs nothing stored about it.
 *
 * The listener's query returns only marked threads, so for every parent whose co-parent has not
 * deleted an account it answers an empty list and costs one listener while a chat screen is open.
 */
@Singleton
class DepartedThreadSource @Inject constructor(
    private val userRepository: UserRepository,
    private val messages: FirestoreMessageDataSource
) {

    /**
     * The signed-in parent's kept threads, re-read on every change to them and empty while signed
     * out. A failed listener answers an empty list rather than ending the chat screens' flows —
     * the same trade the rest of the chat makes: a degraded screen, never a crashed one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<DepartedThread>> =
        userRepository.observeCurrentUserId().flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList<DepartedThread>())
            } else {
                messages.observeDepartedThreads(uid)
                    .map { documents -> parse(documents, uid, System.currentTimeMillis()) }
                    .catch { e ->
                        Log.w(TAG, "Kept-thread listener failed; showing none", e)
                        emit(emptyList())
                    }
            }
        }.distinctUntilChanged()

    /**
     * The kept threads of [myUid] now, read once — empty when the read fails, which the export
     * treats like any thread it cannot reach.
     */
    suspend fun current(myUid: String): List<DepartedThread> = try {
        parse(messages.fetchDepartedThreads(myUid), myUid, System.currentTimeMillis())
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "Kept threads could not be read", e)
        emptyList()
    }

    /** Pure, for the rule's sake: see [DepartedThread.fromDocument]. */
    internal companion object {
        private const val TAG = "DepartedThreadSource"

        /** The kept threads among [documents], soonest deadline first. */
        fun parse(documents: List<Map<String, Any?>>, myUid: String, nowMillis: Long): List<DepartedThread> =
            documents.mapNotNull { data ->
                (data["id"] as? String)?.let { id -> DepartedThread.fromDocument(id, data, myUid, nowMillis) }
            }.sortedBy { it.retainedUntilMillis }
    }
}
