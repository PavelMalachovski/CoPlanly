package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreRequestDeadlineDataSource
import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed [RequestDeadlineRepository] (phase 3a, spec §5.1).
 *
 * It owns three rules and nothing else; the wire form is `RequestDeadlineDocument`'s and the calls
 * are `FirestoreRequestDeadlineDataSource`'s.
 *
 * - **A write waits ten seconds for the server, and then counts as written** (ruling 14). Offline,
 *   a Firestore write never completes: the SDK keeps it queued and sends it when it can, and a
 *   caller that waited for it would hang until the system stopped the job. A write the server
 *   refuses — the rules, or anything else that throws — counts as not written.
 * - **A read that fails reads as nothing.** The listener is retried a bounded number of times with
 *   a growing pause, as the switcher's dot is (`OtherFamiliesSignals`), then reads as no clocks;
 *   the one-shot reads answer null and an empty list. A request whose clock cannot be read
 *   behaves as one without a clock, which is what it was before this feature.
 * - **A start is a write, then two jobs.** The deadline and the end are scheduled only once the
 *   clock was written, so a refused clock leaves nothing behind.
 *
 * No log line names a clock: its id spells the proposer's uid, and a proposal's the family's two more.
 */
@Singleton
class RequestDeadlineRepositoryImpl @Inject constructor(
    private val dataSource: FirestoreRequestDeadlineDataSource,
    private val authService: FirebaseAuthService,
    private val scheduler: RequestClockScheduler
) : RequestDeadlineRepository {

    override fun signedInUid(): String? = authService.getCurrentUser()?.uid

    override fun observeFamily(myUid: String, familyId: String): Flow<List<RequestDeadline>> =
        dataSource.observeFamily(myUid, familyId)
            .retryWhen { cause, attempt ->
                if (cause is CancellationException || attempt >= MAX_RECONNECT_ATTEMPTS) {
                    return@retryWhen false
                }
                delay((RECONNECT_BASE_DELAY_MS shl attempt.toInt()).coerceAtMost(RECONNECT_MAX_DELAY_MS))
                true
            }
            .catch { e ->
                Log.w(TAG, "The request clocks listener gave up; reading as no clocks", e)
                emit(emptyList())
            }

    override suspend fun openFor(myUid: String, sinceMillis: Long): List<RequestDeadline> =
        read("the open request clocks", emptyList()) { dataSource.openFor(myUid, sinceMillis) }

    override suspend fun get(id: String): RequestDeadline? =
        read("a request clock", null) { dataSource.get(id) }

    override suspend fun start(start: RequestStart): RequestDeadline? {
        val uid = signedInUid() ?: return null
        val id = RequestDeadlineId.of(start.kind, uid, start.ref, start.familyId)
        val fields = RequestDeadlineDocument.createFields(start, uid)
        if (!write("The request clock") { dataSource.create(id, fields) }) return null
        val clock = RequestDeadline(
            id = id,
            kind = start.kind,
            ref = start.ref,
            familyId = start.familyId,
            proposerUid = uid,
            responderUid = start.responderUid,
            answerByMillis = start.answerByMillis,
            capMillis = start.capMillis,
            firstDate = start.firstDate,
            dayCount = start.dayCount
        )
        scheduler.scheduleDeadline(id, clock.answerByMillis)
        scheduler.scheduleEnd(id, RequestClock.endMillis(clock))
        return clock
    }

    override suspend fun markDelivered(
        clock: RequestDeadline,
        deliveredAtMillis: Long,
        reminderTimesMillis: List<Long>
    ): Boolean = write("The delivery mark") {
        dataSource.update(clock.id, RequestDeadlineDocument.deliveredFields(deliveredAtMillis, reminderTimesMillis))
    }

    override suspend fun markSeen(clock: RequestDeadline, seenAtMillis: Long): Boolean =
        write("The opened mark") {
            dataSource.update(clock.id, mapOf(RequestDeadlineDocument.SEEN_AT_MILLIS to seenAtMillis))
        }

    override suspend fun reportReminderShown(clock: RequestDeadline): Boolean =
        write("The reminder count") { dataSource.reportShown(clock.id) }

    override suspend fun giveLastChance(clock: RequestDeadline, untilMillis: Long): Boolean =
        write("The last chance") { dataSource.giveLastChance(clock.id, untilMillis) }

    override suspend fun close(clock: RequestDeadline): Boolean =
        write("The closure") { dataSource.close(clock.id) }

    /**
     * Runs a Firestore write: true when it was accepted, or when the server had not answered after
     * [WRITE_TIMEOUT_MS] (it is queued, and goes out later); false when it threw. Cancellation is
     * not a refusal and is rethrown.
     */
    private suspend fun write(what: String, block: suspend () -> Unit): Boolean =
        try {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { block() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Any refusal reads the same to the caller; the log keeps which.
            Log.w(TAG, "$what was refused", e)
            false
        }

    /** Runs a Firestore read: its answer, or [fallback] when it threw. Cancellation is rethrown. */
    private suspend fun <T> read(what: String, fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Any failure reads the same to the caller; the log keeps which.
            Log.w(TAG, "Could not read $what", e)
            fallback
        }

    private companion object {
        const val TAG = "RequestDeadlineRepo"

        /** How long a background write waits for the server before it counts as queued (ruling 14). */
        const val WRITE_TIMEOUT_MS = 10_000L

        /** Same bound as `OtherFamiliesSignals` and `MessageRepositoryImpl.reconnecting()`. */
        const val MAX_RECONNECT_ATTEMPTS = 8L
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val RECONNECT_MAX_DELAY_MS = 60_000L
    }
}
