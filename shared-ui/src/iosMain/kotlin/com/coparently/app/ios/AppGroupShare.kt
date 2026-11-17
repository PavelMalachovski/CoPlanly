package com.coparently.app.ios

import kotlinx.coroutines.flow.Flow
import platform.Foundation.NSUserDefaults

/**
 * What the app shares with its Notification Service Extension through the App Group (iPhone phase
 * 4 part C, spec §7.2): the signed-in account's uid and the language the app speaks, and nothing
 * else. The extension drops a push addressed to another uid, and words the rest in that language
 * (`PushWording`), so a push meant for the account that signed out is never worded with the next
 * one's data.
 *
 * The App Group is [SUITE], `group.app.coplanly`, in both targets' entitlements. The extension
 * (Swift, Task 6) reads the two values with:
 * ```swift
 * let shared = UserDefaults(suiteName: "group.app.coplanly")
 * let uid = shared?.string(forKey: "coplanly.push.uid")          // UID_KEY
 * let language = shared?.string(forKey: "coplanly.push.language") // LANGUAGE_KEY, e.g. "cs", "en-150"
 * ```
 * Both are absent while nobody is signed in: [write] with no uid removes both (sign-out, account
 * deletion), so the extension then words nothing. The language is the owner's rule's answer
 * (`AppLanguageRule.effective`): a shipped language's tag, or `en-150`.
 *
 * `IosApp` writes them on start and on every change of the signed-in uid (sign-in, sign-out,
 * deletion), and again on every return to the foreground, where a language changed in the phone's
 * Settings shows (iOS restarts the app for a change of its own language).
 */
object AppGroupShare {

    /** The App Group, exactly as the entitlements name it. */
    const val SUITE: String = "group.app.coplanly"

    /** The signed-in account's uid, a string; absent while nobody is signed in. */
    const val UID_KEY: String = "coplanly.push.uid"

    /** The app's resolved language tag (`AppLanguageRule`), a string; absent while nobody is signed in. */
    const val LANGUAGE_KEY: String = "coplanly.push.language"

    /** The suite written to: [SUITE], or a test's own, so a test never touches the app's values. */
    internal var suiteName: String = SUITE

    private fun defaults(): NSUserDefaults = NSUserDefaults(suiteName = suiteName)

    /**
     * Shares [uid] and [language]; a null or blank [uid] removes both, as at sign-out and deletion,
     * and a null or blank [language] beside a uid removes the language alone.
     */
    fun write(uid: String?, language: String?) {
        val defaults = defaults()
        val account = uid?.takeIf { it.isNotBlank() }
        if (account == null) {
            defaults.removeObjectForKey(UID_KEY)
            defaults.removeObjectForKey(LANGUAGE_KEY)
            return
        }
        defaults.setObject(account, forKey = UID_KEY)
        val tag = language?.takeIf { it.isNotBlank() }
        if (tag == null) defaults.removeObjectForKey(LANGUAGE_KEY) else defaults.setObject(tag, forKey = LANGUAGE_KEY)
    }

    /** The uid shared now, or null. */
    val uid: String? get() = defaults().stringForKey(UID_KEY)

    /** The language shared now, or null. */
    val language: String? get() = defaults().stringForKey(LANGUAGE_KEY)
}

/**
 * Shares each signed-in uid [uids] emits, with the [language] the app speaks then, until the flow
 * ends: what `IosApp` runs for the session, so a sign-out (null) clears both values at once.
 */
internal suspend fun shareSignedInAccount(uids: Flow<String?>, language: () -> String) {
    uids.collect { uid -> AppGroupShare.write(uid, uid?.let { language() }) }
}
