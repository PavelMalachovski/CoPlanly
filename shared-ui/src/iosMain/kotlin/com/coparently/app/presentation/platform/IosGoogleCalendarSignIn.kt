package com.coparently.app.presentation.platform

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.google.GoogleCalendarAccess
import com.coparently.app.data.remote.google.GoogleSignInFailure
import com.coparently.app.data.remote.google.IosGoogleCalendarAccount
import com.coparently.app.ios.GoogleIdTokenSource

/**
 * Google's sign-in for Google Calendar on the iPhone (MON-24 phase 4, part A, Task 7), as
 * `AndroidGoogleCalendarSignIn` is Android's: Google's sheet through the app's
 * `GoogleSignInBridge.swift` ([GoogleIdTokenSource.requestCalendarAccess]) for the read-only scope
 * ([GoogleCalendarAccess.SCOPE], CLAUDE.md Legal item 10), whose authorization code [account]
 * redeems through the server and stores.
 *
 * On Android `MainActivity` hands the sheet's answer to its `SyncViewModel`; here the shell does, the
 * one Settings shares ([answered], `NativeShell`), so `SyncViewModel.handleSignInResult` says
 * "processing" before the code is redeemed, as on Android. Every way the sheet ends reaches it,
 * a dismissed sheet as [GoogleSignInFailure.Cancelled].
 *
 * It is [available] only where the sheet can run and the build names the web client the server's
 * code is issued for (`GIDServerClientID`, blank in CI): elsewhere Settings draws no Google Calendar
 * row, as the stand-in it replaced made it draw none.
 */
class IosGoogleCalendarSignIn(
    private val google: GoogleIdTokenSource,
    private val account: IosGoogleCalendarAccount,
    private val answered: (GoogleSignInAnswer) -> Unit,
) : GoogleCalendarSignIn {

    override val available: Boolean get() = google.available && google.calendarAvailable

    override fun open(): GoogleSignInStart {
        if (!available) return GoogleSignInStart.UNAVAILABLE
        google.requestCalendarAccess(GoogleCalendarAccess.SCOPE) { code, email, displayName, cancelled, error ->
            answered(answerOf(code, email, displayName, cancelled, error))
        }
        return GoogleSignInStart.OPENED
    }

    /** What the sheet's answer comes to once [account] has redeemed its code. */
    internal fun answerOf(
        serverAuthCode: String?,
        email: String?,
        displayName: String?,
        cancelled: Boolean,
        error: String?,
    ): GoogleSignInAnswer = GoogleSignInAnswer {
        when {
            cancelled -> GoogleSignInCompletion.Failed(GoogleSignInFailure.Cancelled)
            serverAuthCode == null -> {
                logger.w(TAG, "Google's sheet gave no authorization code: ${error ?: "no error"}")
                GoogleSignInCompletion.Failed(
                    if (error == null) GoogleSignInFailure.NoAuthCode else GoogleSignInFailure.Unexpected
                )
            }
            else -> account.connect(serverAuthCode, email.orEmpty())
                ?.let { GoogleSignInCompletion.Failed(it) }
                ?: GoogleSignInCompletion.Connected(displayName = displayName, email = email)
        }
    }

    private companion object {
        const val TAG = "IosGoogleCalendarSignIn"
    }
}
