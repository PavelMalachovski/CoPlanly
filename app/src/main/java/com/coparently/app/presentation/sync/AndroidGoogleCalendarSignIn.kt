package com.coparently.app.presentation.sync

import android.content.Intent
import android.util.Log
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.presentation.platform.GoogleCalendarSignIn
import com.coparently.app.presentation.platform.GoogleSignInAnswer
import com.coparently.app.presentation.platform.GoogleSignInCompletion
import com.coparently.app.presentation.platform.GoogleSignInStart
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task

/**
 * Google's sign-in for Google Calendar on Android (MON-24 phase 2, Task 11): the sign-in client's
 * intent from [CredentialManagerService], launched through `MainActivity`'s activity result
 * ([launch]), whose answer the activity hands to `SyncViewModel` through [signInAnswer].
 *
 * What `SyncViewModel.createGoogleSignInIntent` and the Settings screen's `onStartGoogleSignIn` did
 * before the ViewModel moved to `:shared-ui`, in the same order: the client is built, its intent
 * launched; a missing client is "unavailable", a client that cannot be built "failed".
 */
class AndroidGoogleCalendarSignIn(
    private val credentialManagerService: CredentialManagerService,
    private val launch: (Intent) -> Unit
) : GoogleCalendarSignIn {

    override val available: Boolean = true

    override fun open(): GoogleSignInStart {
        val intent = try {
            credentialManagerService.getGoogleSignInClient()?.signInIntent
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // As the ViewModel caught it: building the client reads Play services and resources.
            Log.e(TAG, "Unable to create Google Sign-In intent", e)
            return GoogleSignInStart.FAILED
        } ?: return GoogleSignInStart.UNAVAILABLE
        launch(intent)
        return GoogleSignInStart.OPENED
    }

    private companion object {
        const val TAG = "SyncViewModel"
    }
}

/**
 * Google's answer in [task], redeemed by [CredentialManagerService.handleSignInResult] when
 * `SyncViewModel` asks for it, after it says "processing".
 */
fun CredentialManagerService.signInAnswer(task: Task<GoogleSignInAccount>): GoogleSignInAnswer =
    GoogleSignInAnswer {
        val (account, failure) = handleSignInResult(task)
        if (account != null) {
            GoogleSignInCompletion.Connected(displayName = account.displayName, email = account.email)
        } else {
            GoogleSignInCompletion.Failed(failure)
        }
    }
