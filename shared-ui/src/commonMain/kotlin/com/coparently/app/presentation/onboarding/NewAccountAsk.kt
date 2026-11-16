package com.coparently.app.presentation.onboarding

import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.session.AccountConsistency

/**
 * The one question onboarding asks an account a Google or Apple sign-in has just created: "Already
 * use CoPlanly?" (MON-24 phase 4, part A; spec §3.1 goal 3 and §3.8 call 4).
 *
 * A parent who signs in with a method no account holds gets a new, empty account, and nothing can
 * tell it is the same person (Apple's private relay address). So the sign-in records here that the
 * account is new ([pending]), and onboarding asks once before its first step: sign out and try the
 * way used before, or keep the new account. The answer is kept on this phone, per uid, in
 * [SecurePreferences], so the question is asked once; an account created on purpose through
 * "Create account" is never marked.
 */
class NewAccountAsk(
    private val preferences: SecurePreferences,
    private val accountConsistency: AccountConsistency
) {

    /**
     * Marks [uid] as just created, so onboarding asks the question.
     *
     * The device's account-switch wipe runs first: it clears [SecurePreferences] when a different
     * account signs in on this phone, and the session's own run of it may land after this call
     * returns, which would erase the mark. Run here first, under its own lock, it has already
     * wiped by the time the mark is written, and the session's later run finds nothing to do.
     */
    suspend fun pending(uid: String) {
        accountConsistency.ensureAccountConsistency()
        preferences.putBoolean(keyFor(uid), true)
    }

    /** Whether [uid] still owes the question. */
    fun isPending(uid: String): Boolean = preferences.getBoolean(keyFor(uid), false)

    /** Records that [uid] answered, so the question is not asked again. */
    fun answer(uid: String) {
        preferences.remove(keyFor(uid))
    }

    companion object {
        /** The stored key's prefix; the uid follows it. */
        const val KEY_PREFIX = "new_account_ask_"

        /** The stored key for [uid]: one per account, so another account never reads it. */
        fun keyFor(uid: String): String = KEY_PREFIX + uid
    }
}
