package com.coparently.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for one sync run as soon as there is a network, without waiting for the periodic tick.
 *
 * An interface over [SyncWorker.syncNow] so that code without a `Context` — a repository, a
 * ViewModel — can ask for the run and a unit test can see that it asked. The request is
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

/** The production [SyncRequester]: hands the request to WorkManager through [SyncWorker.syncNow]. */
@Singleton
class WorkManagerSyncRequester @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncRequester {

    override fun requestSyncNow() = SyncWorker.syncNow(context)
}
