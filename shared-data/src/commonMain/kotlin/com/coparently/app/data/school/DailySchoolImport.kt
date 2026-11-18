package com.coparently.app.data.school

import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The iPhone's daily school import (MON-8, iPhone phase 4 part D): where Android keeps unique
 * periodic work (`school_import_daily`, `KEEP`), the iPhone runs the import after a sync — the
 * launch, the foreground and the background refresh alike — at most once a day per account.
 *
 * The day is remembered in the sealed store under `school_import_daily_<uid>` as the ISO date of the
 * local day the import ran on. It is written only once [SchoolImporter.syncAll] has returned, so an
 * import that threw is tried again at the next sync; a cancelled one is not recorded either.
 */
class DailySchoolImport internal constructor(
    private val syncAll: suspend () -> Int,
    private val preferences: SecurePreferences,
    private val signedInUser: SignedInUser,
    private val clock: Clock,
    private val zone: () -> TimeZone
) {

    /** The production import, over [importer]'s `syncAll`, on [clock] in the phone's current [zone]. */
    constructor(
        importer: SchoolImporter,
        preferences: SecurePreferences,
        signedInUser: SignedInUser,
        clock: Clock = Clock.System,
        zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
    ) : this(importer::syncAll, preferences, signedInUser, clock, zone)

    /**
     * Runs today's import for the signed-in account unless it already ran today.
     *
     * @return true when it ran the import now; false when nobody is signed in or today's has run.
     */
    suspend fun runIfDue(): Boolean {
        val uid = signedInUser.getCurrentUid() ?: return false
        val key = KEY_PREFIX + uid
        val today = IsoText.date(clock.todayIn(zone()))
        if (preferences.getString(key) == today) return false
        // A throw, cancellation included, leaves the day unrecorded: the next sync tries again.
        syncAll()
        preferences.putString(key, today)
        return true
    }

    private companion object {
        const val KEY_PREFIX = "school_import_daily_"
    }
}
