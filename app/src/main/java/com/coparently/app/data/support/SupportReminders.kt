package com.coparently.app.data.support

import com.coparently.app.data.local.preferences.EncryptedPreferences
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's child support reminders (25 October 2026): per account and family, how many days
 * before the due day to remind, and the month it last did. On this phone only — a setting about
 * this phone's notifications — and gone at sign-out with the rest of the sealed store.
 */
@Singleton
class SupportReminders @Inject constructor(private val preferences: EncryptedPreferences) {

    /** How many days before the due day [uid] is reminded in [familyId], or null when not at all. */
    fun daysBefore(uid: String, familyId: String): Int? =
        preferences.getString(key(uid, familyId))?.toIntOrNull()

    /** Turns [uid]'s reminder in [familyId] on with [days], or off with null. */
    fun set(uid: String, familyId: String, days: Int?) {
        if (days == null) {
            preferences.remove(key(uid, familyId))
        } else {
            preferences.putString(key(uid, familyId), days.toString())
        }
    }

    /** The families [uid] has a reminder on in. */
    fun families(uid: String): List<String> =
        preferences.keysWithPrefix("$PREFIX$uid$SEPARATOR").map { it.removePrefix("$PREFIX$uid$SEPARATOR") }

    /** The month [uid] was last reminded about in [familyId], or null. */
    fun lastReminded(uid: String, familyId: String): YearMonth? =
        preferences.getString(lastKey(uid, familyId))?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    /** Records that [uid] was reminded about [month] in [familyId]. */
    fun reminded(uid: String, familyId: String, month: YearMonth) {
        preferences.putString(lastKey(uid, familyId), month.toString())
    }

    private fun key(uid: String, familyId: String) = "$PREFIX$uid$SEPARATOR$familyId"
    private fun lastKey(uid: String, familyId: String) = "$LAST_PREFIX$uid$SEPARATOR$familyId"

    private companion object {
        const val PREFIX = "support_reminder_"
        const val LAST_PREFIX = "support_reminded_"
        const val SEPARATOR = "|"
    }
}
