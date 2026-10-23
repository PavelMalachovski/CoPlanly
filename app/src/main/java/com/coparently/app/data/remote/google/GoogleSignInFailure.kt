package com.coparently.app.data.remote.google

/**
 * Why connecting Google Calendar did not finish, named by the step that failed.
 *
 * One sentence used to cover every step ("Unable to sign in with Google"), so a tester's
 * screenshot could not say whether Google refused this build, the parent backed out, or the
 * function that redeems the code failed (October 2026, tester report). Those are three problems
 * with three different fixes: the OAuth client's registration, nothing, and the functions'
 * configuration. Each failure now carries what tells them apart, and nothing more: a status code
 * or the server's short reason, never a message, which can carry anything.
 */
sealed interface GoogleSignInFailure {

    /** The parent closed Google's sign-in without choosing an account (status 12501). */
    data object Cancelled : GoogleSignInFailure

    /**
     * Google's sign-in refused, with its status code. 10 (`DEVELOPER_ERROR`) means this build's
     * package name or signing certificate is not registered with the project's OAuth clients.
     */
    data class Google(val statusCode: Int) : GoogleSignInFailure

    /** Google signed the parent in and handed back no authorization code to redeem. */
    data object NoAuthCode : GoogleSignInFailure

    /**
     * Google signed the parent in, and `exchangeGoogleAuthCode` failed to redeem the code.
     *
     * @property diagnosis The server's reason, from `GoogleOAuthFunctions.diagnosisOf`:
     *   `oauth-not-configured`, `google-refused: unauthorized_client`, `NOT_FOUND`, …
     */
    data class Server(val diagnosis: String) : GoogleSignInFailure

    /** Something on this phone failed while the tokens were stored; the log says what. */
    data object Unexpected : GoogleSignInFailure
}
