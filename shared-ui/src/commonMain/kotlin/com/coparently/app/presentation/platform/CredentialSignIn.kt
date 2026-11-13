package com.coparently.app.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The system's credential sheets for the app's own sign-in (MON-24 phase 2, slice 11f): Google's
 * sign-in, the passwords the phone has saved, and the offer to save one.
 *
 * On Android it is Credential Manager, which hosts its sheets on the Activity, so the shell holds
 * the Activity (`AndroidCredentialSignIn` in the app, provided by `MainActivity`) and the ViewModel
 * is handed this on every call and never keeps it. On the iPhone it is Google's sign-in alone
 * (`IosCredentialSignIn`, MON-24 phase 3, Task 9), [available] only where the app carries Google's
 * configuration; saved passwords are iOS's own autofill there, filling the fields, so the iPhone
 * does not [offersSavedPasswords].
 */
interface CredentialSignIn : PlatformCapability {

    /**
     * Whether the sign-in screen offers the phone's saved passwords as a button of their own: on
     * Android whenever the sheets are [available], which is what the screen read before this
     * existed; on the iPhone never, because the system's autofill offers them in the fields.
     */
    val offersSavedPasswords: Boolean get() = available

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
