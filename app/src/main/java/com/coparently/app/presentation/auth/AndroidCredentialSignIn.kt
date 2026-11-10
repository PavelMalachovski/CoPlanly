package com.coparently.app.presentation.auth

import android.app.Activity
import com.coparently.app.data.remote.google.CredentialAuthenticator
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.SavedCredential

/**
 * The Android [CredentialSignIn] (MON-24 phase 2, slice 11f): [CredentialAuthenticator]'s three
 * Credential Manager calls on [activity], which hosts their sheets, as `AuthViewModel` called them
 * with the Activity it was handed before it moved to `:shared-ui`.
 *
 * `MainActivity` builds it in its own composition, so the Activity is held no longer than the screen
 * it hosts; the ViewModel is handed this on every call and keeps nothing.
 */
class AndroidCredentialSignIn(
    private val activity: Activity,
    private val credentialAuthenticator: CredentialAuthenticator
) : CredentialSignIn {

    override val available: Boolean = true

    override suspend fun googleIdToken(): Result<String> = credentialAuthenticator.signInWithGoogle(activity)

    override suspend fun savedPassword(): Result<SavedCredential> =
        credentialAuthenticator.getSavedPassword(activity).map { saved ->
            SavedCredential(email = saved.email, password = saved.password)
        }

    override suspend fun offerToSave(email: String, password: String) =
        credentialAuthenticator.savePassword(activity, email, password)
}
