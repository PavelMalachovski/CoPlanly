package com.coparently.app.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The system's credential sheets for the app's own sign-in (MON-24 phase 2, slice 11f): Google's
 * sign-in, the passwords the phone has saved, and the offer to save one.
 *
 * On Android it is Credential Manager, which hosts its sheets on the Activity, so the shell holds
 * the Activity (`AndroidCredentialSignIn` in the app, provided by `MainActivity`) and the ViewModel
 * is handed this on every call and never keeps it. The iPhone has no sign-in yet, so it is not
 * [available] there and the sign-in screen offers neither Google nor a saved password.
 */
interface CredentialSignIn : PlatformCapability {

    /** Runs Google's sign-in and answers its id token, or the failure the sheet ended with. */
    suspend fun googleIdToken(): Result<String>

    /** Offers the passwords the phone has saved and answers the chosen one, or the failure. */
    suspend fun savedPassword(): Result<SavedCredential>

    /** Offers to save [email] and [password]; never throws, whatever the parent answers. */
    suspend fun offerToSave(email: String, password: String)
}

/**
 * A password the phone had saved for the app. [toString] leaves the password out, so a log line or
 * a crash report never carries it.
 */
data class SavedCredential(val email: String, val password: String) {
    override fun toString(): String = "SavedCredential(email=$email)"
}

/**
 * The credential sheets, provided by the shell alone (`MainActivity`, which hosts them). Read
 * outside it, it fails loudly, as the platform's other locals do.
 */
val LocalCredentialSignIn = staticCompositionLocalOf<CredentialSignIn> {
    error("LocalCredentialSignIn was read outside the shell: MainActivity provides the credential sheets")
}
