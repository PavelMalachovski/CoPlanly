package com.coparently.app.data.session

/**
 * A sign-in provider's own session on the iPhone, kept beside Firebase Auth's (MON-24 phase 3, part
 * B, Task 12): Google Sign-In for iOS remembers the account it last signed in, in the Keychain, and
 * offers it first the next time its sheet opens. On a shared phone that would preselect the previous
 * parent's Google account, so the session is ended with ours: on sign-out
 * ([IosAccountTraces.clearAfterSignOut]) and on the first launch after an install
 * (`clearPreviousInstall`), since the Keychain survives an uninstall.
 *
 * The iPhone app hands the shared code its Google bridge (`GoogleSignInBridge.swift`, through
 * `IosApp.start`), whose `signOut()` this calls. Ending it never signs anybody out of Firebase and
 * never fails: a provider with no session has nothing to end.
 */
fun interface SignInProviderSession {

    /** Ends the provider's own session on this phone; does nothing when there is none. */
    fun signOut()

    /** No provider session to end, for a test or a phone without the provider. */
    companion object {
        /** Ends nothing. */
        val None: SignInProviderSession = SignInProviderSession { }
    }
}
