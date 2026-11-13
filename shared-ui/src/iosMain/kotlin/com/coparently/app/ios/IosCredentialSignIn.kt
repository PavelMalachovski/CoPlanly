package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.GoogleAccessTokens
import com.coparently.app.presentation.auth.GoogleSignInCancelled
import com.coparently.app.presentation.auth.NoSavedPassword
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.SavedCredential
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google's sign-in sheet, as the iPhone app's Swift side runs it (MON-24 phase 3, Task 9): the app's
 * `GoogleSignInBridge.swift` implements it over Google Sign-In for iOS
 * (`GIDSignIn.sharedInstance.signIn(withPresenting:)`).
 */
interface GoogleIdTokenSource {

    /**
     * Whether the sheet can run: the app carries a `GoogleService-Info.plist` with a `CLIENT_ID` and
     * its reversed client id is one of the app's URL schemes. False in CI and on the emulators,
     * where no such file is bundled.
     */
    val available: Boolean

    /**
     * Opens the sheet and calls [completion] once, on the main queue, with what it ended in: Google's
     * ID token and access token on success; `cancelled` when the parent dismissed it; otherwise an
     * `error` for the log, which no screen shows.
     */
    fun requestIdToken(
        completion: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit
    )
}

/**
 * The iPhone's [CredentialSignIn] (MON-24 phase 3, Task 9), as `AndroidCredentialSignIn` is
 * Android's: Google's sheet through [google], whose access token it holds in [GoogleAccessTokens] for
 * the Firebase sign-in `AuthViewModel` makes next with the ID token (`IosFirebaseAuth`), since the
 * shared seams carry the ID token alone.
 *
 * Saved passwords are iOS's autofill, so there is no button for them ([offersSavedPasswords] is
 * false), [savedPassword] fails as [NoSavedPassword] and [offerToSave] leaves the offer to the
 * system. What makes iOS offer the Keychain's passwords is the fields' keyboard types
 * (`KeyboardType.Email` and `KeyboardType.Password`), which Compose Multiplatform 1.9.3 turns into
 * the e-mail and password text content types. It ignores the fields' `ContentType` semantics on iOS
 * (CMP-7154), so `NewPassword` on sign-up is not honoured there yet; Android's autofill reads them.
 */
class IosCredentialSignIn(private val google: GoogleIdTokenSource) : CredentialSignIn {

    override val available: Boolean get() = google.available

    override val offersSavedPasswords: Boolean get() = false

    override suspend fun googleIdToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        google.requestIdToken { idToken, accessToken, cancelled, error ->
            val result = when {
                cancelled -> Result.failure(GoogleSignInCancelled())
                idToken != null -> {
                    if (accessToken != null) GoogleAccessTokens.hold(idToken, accessToken)
                    Result.success(idToken)
                }
                else -> Result.failure(IllegalStateException(error ?: NO_ID_TOKEN))
            }
            if (continuation.isActive) continuation.resume(result)
        }
    }

    override suspend fun savedPassword(): Result<SavedCredential> = Result.failure(NoSavedPassword())

    override suspend fun offerToSave(email: String, password: String) = Unit

    private companion object {
        const val NO_ID_TOKEN = "Google's sign-in answered no ID token"
    }
}
