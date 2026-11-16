package com.coparently.app.ios

/**
 * Apple's sign-in sheet, as the iPhone app's Swift side runs it (MON-24 phase 4, part A): the app's
 * `AppleSignInBridge.swift` implements it over AuthenticationServices
 * (`ASAuthorizationAppleIDProvider`, asking for the full name and the e-mail address), as
 * `GoogleSignInBridge.swift` implements [GoogleIdTokenSource]. [IosCredentialSignIn] turns its answer
 * into a Firebase sign-in or link.
 */
interface AppleIdSource {

    /**
     * Whether the sheet can run. The system offers it on every iPhone the app supports, so the
     * bridge says true; whether the app offers Apple at all is the build's flag
     * (`CoplanlyAppleSignIn`), which [IosCredentialSignIn.appleAvailable] reads beside this.
     */
    val available: Boolean

    /**
     * Opens Apple's sheet with [hashedNonce] as the request's nonce, and calls [completion] once, on
     * the main queue, with what it ended in.
     *
     * - On success: Apple's identity token, the one-time authorization code (null when Apple sent
     *   none), and the given and family names, which Apple shares only at the first authorisation
     *   and only when the parent agrees (each null otherwise).
     * - `cancelled` when the parent closed the sheet (`ASAuthorizationError.canceled`), or when a
     *   sheet was already showing.
     * - Otherwise an `error` for the log, which no screen shows.
     *
     * The raw nonce never crosses: the shared code made it ([AppleNonce]) and hands Firebase the raw
     * text with the token.
     */
    fun requestAppleId(
        hashedNonce: String,
        completion: (
            idToken: String?,
            authorizationCode: String?,
            givenName: String?,
            familyName: String?,
            cancelled: Boolean,
            error: String?,
        ) -> Unit
    )
}

/** Apple's sheet where the app has none to offer: never available, and every request fails. */
object NoAppleId : AppleIdSource {
    override val available: Boolean = false

    override fun requestAppleId(
        hashedNonce: String,
        completion: (
            idToken: String?,
            authorizationCode: String?,
            givenName: String?,
            familyName: String?,
            cancelled: Boolean,
            error: String?,
        ) -> Unit
    ) = completion(null, null, null, null, false, "Sign in with Apple is not offered here")
}
