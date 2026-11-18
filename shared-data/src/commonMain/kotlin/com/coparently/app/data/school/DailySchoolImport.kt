package com.coparently.app.data.school

import com.coparently.app.data.platform.Connectivity
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The iPhone's daily school import (MON-8, iPhone phase 4 part D): where Android keeps unique
 * periodic work (`school_import_daily`, `KEEP`, on a connected network), the iPhone runs the import
 * after a sync — the launch, the foreground and the background refresh alike — at most once a day
 * per account.
 *
 * The day is remembered in the sealed store under `school_import_daily_<uid>` as the ISO date of the
 * local day the import ran on, and only once it did the day's work:
 * - **Offline it neither runs nor records**, the twin of Android's network constraint: the next
 *   sync with a network runs it.
 * - **A connection that came back [SchoolSyncResult.Failed]** (no network, the school's server
 *   down or unreadable) leaves the day unrecorded, so a later sync that day tries again. A
 *   connection waiting for its password, or naming no family, does not: no retry today would
 *   change it, and the parent is asked on the screen.
 * - **A throw, cancellation included, records nothing.**
 */
class DailySchoolImport internal constructor(
    private val syncEach: suspend () -> List<SchoolSyncResult>,
    private val online: () -> Boolean,
    private val preferences: SecurePreferences,
    private val signedInUser: SignedInUser,
    private val clock: Clock,
    private val zone: () -> TimeZone
) {

    /** The production import, over [importer], on [clock] in the phone's current [zone]. */
    constructor(
        importer: SchoolImporter,
        connectivity: Connectivity,
        preferences: SecurePreferences,
        signedInUser: SignedInUser,
        clock: Clock = Clock.System,
        zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
    ) : this(importer::syncEach, connectivity::isOnline, preferences, signedInUser, clock, zone)

    /**
     * Runs today's import for the signed-in account unless it already ran today or the phone is
     * offline.
     *
     * @return true when it ran the import now and recorded the day.
     */
    suspend fun runIfDue(): Boolean {
        val uid = signedInUser.getCurrentUid() ?: return false
        val key = KEY_PREFIX + uid
        val today = IsoText.date(clock.todayIn(zone()))
        if (preferences.getString(key) == today || !online()) return false
        val done = syncEach().none { it is SchoolSyncResult.Failed }
        if (done) preferences.putString(key, today)
        return done
    }

    private companion object {
        const val KEY_PREFIX = "school_import_daily_"
    }
}
