package com.coparently.app.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.coparently.app.data.remote.google.GoogleSignInFailure

/**
 * Google's own sign-in screen, for Google Calendar's read-only scope (CLAUDE.md Legal item 10), which
 * the shell runs (MON-24 phase 2, Task 11).
 *
 * On Android it is an activity result `MainActivity` registers, so the answer survives the activity
 * being recreated behind Google's screen; the activity hands it to its own `SyncViewModel`
 * (`SyncViewModel.handleSignInResult`) as a [GoogleSignInAnswer]. The iPhone has no Google sign-in
 * yet, so it is not [available] there and Settings draws no Google Calendar row.
 */
interface GoogleCalendarSignIn : PlatformCapability {

    /** Opens Google's sign-in screen, or says why it could not be opened. */
    fun open(): GoogleSignInStart
}

/** How opening Google's sign-in went. */
enum class GoogleSignInStart {

    /** The screen is open; its answer arrives as a [GoogleSignInAnswer]. */
    OPENED,

    /** This build has no Google sign-in client: OAuth is not configured for it. */
    UNAVAILABLE,

    /** Building the sign-in failed on this phone; the log says why. */
    FAILED,
}

/**
 * What Google's sign-in screen answered, before this app's server has redeemed it.
 *
 * [complete] redeems the authorization code and stores the connection (on Android
 * `CredentialManagerService.handleSignInResult`, a network call), which is why it is `suspend` and
 * why the ViewModel says "processing" before it calls it.
 */
fun interface GoogleSignInAnswer {

    /** Redeems the answer and says how connecting ended. */
    suspend fun complete(): GoogleSignInCompletion
}

/** How connecting Google Calendar ended once Google's answer was redeemed. */
sealed interface GoogleSignInCompletion {

    /** The parent is signed in and the tokens are stored; named as Google names them. */
    data class Connected(val displayName: String?, val email: String?) : GoogleSignInCompletion

    /** Connecting stopped at [failure] (null when nothing more is known). */
    data class Failed(val failure: GoogleSignInFailure?) : GoogleSignInCompletion
}

/**
 * Google's sign-in for Google Calendar, provided by the shell alone (`MainActivity`, which owns the
 * activity result). Read outside it, it fails loudly, as the platform's other locals do.
 */
val LocalGoogleCalendarSignIn = staticCompositionLocalOf<GoogleCalendarSignIn> {
    error("LocalGoogleCalendarSignIn was read outside the shell: MainActivity provides Google's sign-in")
}
