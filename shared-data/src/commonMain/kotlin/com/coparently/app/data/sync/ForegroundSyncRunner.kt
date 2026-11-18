package com.coparently.app.data.sync

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The iPhone's [SyncRequester] (MON-24 phase 3, Task 6): runs the sync in the app's own scope while
 * the app is open, where Android hands it to WorkManager (`SyncWorker.syncNow`). The iPhone has no
 * background job yet (phase 4), so a sync runs at launch, on every return to the foreground and
 * whenever the data layer asks for one (a pairing transition, say).
 *
 * The rule is `SyncWorker`'s (CLAUDE.md item 22):
 * - **One run at a time.** A request while idle starts a run; requests made together coalesce.
 * - **A request during a run is not lost.** It is evidence that something changed after the run
 *   read it, so exactly one more run follows, however many requests arrived.
 * - **[afterSync] — the Google Calendar import — runs after each sync and apart from it** (Legal
 *   item 10): its failure is logged and never fails the run or stops the next one.
 * - **A failed or throwing sync is logged**, and the next request runs as usual.
 * - **Every finished run is announced** on [completedRuns], success or failure, once [afterSync] has
 *   run too: the iPhone's Today widget rewrites its snapshot from what the run brought (phase 4
 *   part D).
 * - **[syncAndAwait] waits for a run that started after it asked** (the background refresh, phase 4
 *   part D): a run already going when it asks may have read the data before the change, so it
 *   waits for the follow-up run that request leaves behind.
 * - **A cancelled [scope] stops the runner cleanly**: the run in flight is cancelled with it and a
 *   later request does nothing.
 *
 * @property sync One full sync (`SyncService.performFullSync`).
 * @property afterSync What follows each sync (`GoogleCalendarAutoImport.importIfDue`).
 * @property scope The app-lifetime scope the runs are made in.
 */
class ForegroundSyncRunner(
    private val sync: suspend () -> Result<Unit>,
    private val afterSync: suspend () -> Unit,
    private val scope: CoroutineScope
) : SyncRequester {

    /**
     * Pending requests, conflated: a request handed to the waiting runner starts a run, and those
     * that arrive while it runs leave one element behind, which is the one follow-up run.
     */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    private val completed =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One element after each run, whether the sync succeeded, failed or threw, emitted after
     * [afterSync]. Nothing is replayed: a collector hears of the runs that end after it subscribed,
     * and a slow one of at least the latest.
     */
    val completedRuns: SharedFlow<Unit> = completed.asSharedFlow()

    /** How many requests were ever made; each request's number is the value it raised this to. */
    private val requested = MutableStateFlow(0L)

    /**
     * The highest request number a finished run had seen when it started: every request up to it
     * has been served by a run that began after it was made.
     */
    private val served = MutableStateFlow(0L)

    init {
        // `consumeEach` cancels the channel when the scope is cancelled, so a later request is
        // refused by `trySend` rather than buffered for a runner that is gone.
        scope.launch { requests.consumeEach { runOnce() } }
    }

    /** Asks for one sync; safe to call repeatedly and from any thread. */
    override fun requestSyncNow() {
        request()
    }

    /**
     * Asks for one sync and returns once a run that started after this request has ended, its
     * [afterSync] included, however the sync itself went. Returns at once when the runner is gone
     * (its scope cancelled); cancelling the caller only stops the wait, never the run.
     */
    suspend fun syncAndAwait() {
        val mine = request() ?: return
        served.first { it >= mine }
    }

    /** Numbers and sends one request; null when the runner is gone and nothing will run. */
    private fun request(): Long? {
        val number = requested.updateAndGet { it + 1 }
        return if (requests.trySend(Unit).isSuccess) number else null
    }

    private suspend fun runOnce() {
        // Read before the sync reads anything: every request numbered up to here is served by it.
        val seen = requested.value
        try {
            sync().exceptionOrNull()?.let { logger.w(TAG, "Sync reported a failure.", it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.e(TAG, "Sync threw.", e)
        }
        try {
            afterSync()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "Automatic Google Calendar import threw.", e)
        }
        served.value = maxOf(served.value, seen)
        completed.tryEmit(Unit)
    }

    private companion object {
        const val TAG = "ForegroundSyncRunner"
    }
}
