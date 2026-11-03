package com.coparently.app.data.sync

/**
 * Asks for one sync run as soon as there is a network, without waiting for the periodic tick.
 *
 * An interface over the app's `SyncWorker.syncNow` so that code without a `Context` — a repository,
 * a ViewModel — can ask for the run and a unit test can see that it asked. Here since MON-24 phase 2
 * (Task 8b); the WorkManager implementation, `WorkManagerSyncRequester`, stays in the app. The request is
 * coalescing: several callers landing together (a push arriving as pairing is observed, say)
 * produce one run, because the worker behind it is enqueued as unique work with
 * `ExistingWorkPolicy.KEEP`.
 *
 * The first caller was the pairing transition. Both phones learn about a new co-parent from a
 * snapshot listener, and until that observation also asked for a sync the records the two of
 * them were now entitled to see stayed where they were for up to fifteen minutes: the inviter's
 * children were uploaded with an audience of one uid and nothing widened it, and the accepter's
 * phone downloaded nothing it could not yet read. The onboarding wizard, which now pairs *first*
 * so that a second parent does not retype what the first already entered, is what made that
 * quarter of an hour visible.
 */
interface SyncRequester {

    /** Requests one full sync as soon as there is a network. Safe to call repeatedly. */
    fun requestSyncNow()
}
