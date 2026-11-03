package com.coparently.app.data.sync

import android.content.Context

/** The production [SyncRequester]: hands the request to WorkManager through [SyncWorker.syncNow]. */
class WorkManagerSyncRequester(
    private val context: Context
) : SyncRequester {

    override fun requestSyncNow() = SyncWorker.syncNow(context)
}
