package com.coparently.app.data.sync

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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

    init {
        // `consumeEach` cancels the channel when the scope is cancelled, so a later request is
        // refused by `trySend` rather than buffered for a runner that is gone.
        scope.launch { requests.consumeEach { runOnce() } }
    }

    /** Asks for one sync; safe to call repeatedly and from any thread. */
    override fun requestSyncNow() {
        requests.trySend(Unit)
    }

    private suspend fun runOnce() {
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
    }

    private companion object {
        const val TAG = "ForegroundSyncRunner"
    }
}
