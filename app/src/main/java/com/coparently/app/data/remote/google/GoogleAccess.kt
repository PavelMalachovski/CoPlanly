package com.coparently.app.data.remote.google

/**
 * Whether this device holds a Google Calendar connection, as far as it can tell without asking
 * Google.
 *
 * "Connected" is not "the access token is still good": an access token lives an hour, and the
 * refresh token stored beside it is what keeps the connection. The check used to be the
 * former, so every connection read as gone about 55 minutes after it was made — Settings said
 * "not signed in" on the next launch, and an import failed without trying to renew the token.
 */
enum class GoogleCalendarConnection {
    /** Nothing is stored: Calendar was never connected on this device, or was disconnected. */
    NOT_CONNECTED,

    /** A refresh token is stored, or an access token that has not run out yet. */
    CONNECTED,

    /**
     * Calendar was connected here, and Google has since refused the connection for good — access
     * revoked or expired, a token the server no longer recognises, or the Calendar permission
     * missing. The tokens are gone; the address is kept, which is what tells this state apart from
     * [NOT_CONNECTED]. Only connecting again helps.
     */
    NEEDS_RECONNECT
}

/** Why no Google access token could be had. */
enum class GoogleAccessFailure {
    /** Calendar is not connected on this device. */
    NOT_CONNECTED,

    /** Google refused the connection for good; see [GoogleCalendarConnection.NEEDS_RECONNECT]. */
    NEEDS_RECONNECT,

    /**
     * Google, or the function that renews the token, could not be reached or failed this time.
     * The connection is kept, and trying again later may well succeed.
     */
    UNAVAILABLE
}

/** What asking for a Google access token came to. */
sealed interface GoogleAccess {
    /** A token to call Google with. */
    data class Granted(val accessToken: String) : GoogleAccess

    /** No token, and why. */
    data class Denied(val failure: GoogleAccessFailure) : GoogleAccess
}

/**
 * Carries a [GoogleAccessFailure] out of code that works with exceptions: a token call's
 * `Result.failure` (`GoogleOAuthFunctions`) and a call made with a credential
 * ([CredentialProvider.withCredential]).
 *
 * @property failure Why there is no access, which decides what the user is told.
 */
class GoogleAccessException(
    val failure: GoogleAccessFailure,
    cause: Throwable? = null
) : Exception("No Google access: $failure", cause)
