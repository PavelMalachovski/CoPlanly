package com.coparently.app.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.coparently.app.data.remote.firebase.SignInOutcome

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
 *
 * Sign in with Apple (MON-24 phase 4, part A) is the shell's too: [signInWithApple] signs in to
 * Firebase with an Apple ID and [linkApple] adds one to the signed-in account, on Android through
 * Firebase Auth's web flow on the Activity, on the iPhone through the system's sheet. Both are
 * offered only while [appleAvailable], which follows the build's `APPLE_SIGN_IN` flag.
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

    /**
     * Whether the sign-in screen and the sign-in methods offer Apple: the build's `APPLE_SIGN_IN`
     * flag (`AppBuildValues.appleSignIn`) on a platform that implements [signInWithApple]. False
     * unless a platform says otherwise.
     */
    val appleAvailable: Boolean get() = false

    /**
     * Signs in to Firebase with an Apple ID and answers whether the account is new and the name
     * Apple shared, or the failure. A sheet the parent closed fails with
     * [AppleSignInCancelledException], which the screen passes over in silence; an address that
     * already signs in another way with [SignsInAnotherWayException], which the screen words
     * generically (spec §3.8 call 2).
     */
    suspend fun signInWithApple(): Result<AppleSignInResult> =
        Result.failure(UnsupportedOperationException("Sign in with Apple is not offered here"))

    /**
     * Links an Apple ID to the signed-in account. One that belongs to another account fails with a
     * `LinkException` of `LinkFailure.InUseByAnotherAccount`: nothing is linked and the signed-in
     * account stays as it was. A sheet the parent closed fails with [AppleSignInCancelledException].
     */
    suspend fun linkApple(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Sign in with Apple is not offered here"))
}

/**
 * What a sign-in with Apple answered.
 *
 * Apple shares the name only at the first authorisation, and a parent may decline to share it: then
 * both parts are null, the profile's name stays empty and onboarding's Profile step asks for it.
 *
 * @property outcome Whether Firebase created the account at this sign-in.
 * @property givenName The given name Apple shared, or null.
 * @property familyName The family name Apple shared, or null.
 */
data class AppleSignInResult(
    val outcome: SignInOutcome,
    val givenName: String?,
    val familyName: String?,
) {
    /**
     * The display name to set on the Firebase account from what Apple shared ("Jana Nováková"), or
     * null when Apple shared none, in which case nothing is written: `ProfileIdentity.resolveName`
     * then reads the account's display name as it reads a Google one.
     */
    val displayNameUpdate: String?
        get() = listOfNotNull(givenName?.trim(), familyName?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
}

/**
 * A sign-in whose address already signs in to an account another way
 * (`account-exists-with-different-credential`). Nothing was signed in, and nothing about that
 * account is named: the screen says only to sign in the way the parent did before and add this
 * method in Settings (spec §3.8 call 2). No lookup is made.
 */
class SignsInAnotherWayException(cause: Throwable? = null) :
    Exception("This address already signs in another way", cause)

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

/**
 * A sign-in with Apple, or a link of one, that the parent closed before it finished (or that could
 * not open because another was already showing). Nothing changed, and it is not an error to show.
 */
class AppleSignInCancelledException(cause: Throwable? = null) :
    Exception("Sign in with Apple was closed", cause)
