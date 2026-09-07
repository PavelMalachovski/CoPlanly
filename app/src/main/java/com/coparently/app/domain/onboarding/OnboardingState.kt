package com.coparently.app.domain.onboarding

import com.coparently.app.domain.model.User

/**
 * Decides whether a parent should be walked through the first-run questionnaire.
 *
 * Kept in the domain layer and free of Android because it gates the app's start destination:
 * getting it wrong shows a questionnaire to a long-standing user, or hides it from a new one,
 * and neither should depend on a device to test.
 */
object OnboardingState {

    /**
     * Whether the wizard should run for this account.
     *
     * @param user The signed-in user, or null before the profile has loaded
     * @param hasChildInfo Whether this account has at least one child record **of its own** —
     *   see [isOwnRecord]
     * @param hasPets Whether this account has at least one pet record of its own
     * @return true when the wizard should run
     */
    fun isNeeded(user: User?, hasChildInfo: Boolean, hasPets: Boolean = false): Boolean {
        if (user == null) return false
        if (!user.onboardingCompletedAt.isNullOrBlank()) return false

        // Complete by evidence. Every installation that predates this column upgrades with a
        // null marker; an account that already carries a name and a record it keeps has plainly
        // been through this once, whatever the marker says.
        //
        // A **pet** counts, not only a child. Counting children alone handed the questionnaire
        // to a pets-only family on every launch — and permanently, if the marker write ever
        // failed, because such an account can never satisfy "named and has a child".
        val named = user.name.isNotBlank()
        return !(named && (hasChildInfo || hasPets))
    }

    /**
     * Whether a record counts as evidence that **this** account has been through the wizard.
     *
     * Only a record this account created does. Since the wizard links the co-parent *first*, a
     * second parent's phone holds the first parent's children within seconds of pairing — and a
     * Google sign-in arrives with a name — so "named, and there is a child" would be true of an
     * account that has answered nothing at all. Counting the co-parent's records used to hide
     * the questionnaire from exactly the parent it now exists to help.
     *
     * A record with no creator recorded is treated as this account's: it predates the stamp and
     * was written by the only device that could have written it.
     *
     * @param uid The signed-in account.
     * @param createdByUid The record's `createdByFirebaseUid`, or null when it carries none.
     */
    fun isOwnRecord(uid: String, createdByUid: String?): Boolean =
        createdByUid.isNullOrBlank() || createdByUid == uid
}
