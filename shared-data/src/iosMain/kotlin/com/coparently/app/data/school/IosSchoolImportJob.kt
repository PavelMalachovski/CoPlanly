package com.coparently.app.data.school

import com.coparently.app.data.platform.logger

/**
 * The iPhone's [SchoolImportScheduler] (phase 4 part D): there is no job to schedule. The daily
 * import rides every sync — the launch, the foreground and the background refresh — through
 * [DailySchoolImport], at most once a day per account, so starting or stopping it here only notes
 * that a connection was added or the last one removed.
 */
object IosSchoolImportJob : SchoolImportScheduler {

    private const val TAG = "IosSchoolImportJob"

    override fun schedule() {
        logger.i(TAG, "A school connection exists; the daily import runs after the next sync.")
    }

    override fun cancel() {
        logger.i(TAG, "No school connection left; the daily import finds nothing to update.")
    }
}
