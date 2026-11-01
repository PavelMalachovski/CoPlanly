package com.coparently.app.data.school.bakalari

import java.io.IOException

/**
 * Why a call to a Bakaláři server failed (MON-8), as far as the app acts on it.
 *
 * Facts, not sentences: the messages are for the log only. The screens word each kind in the
 * reader's language and never render a message (CQ-14) — the server's own `error_description`
 * is Czech free text, and matching on it would break the day a school updates its server.
 */
sealed class BakalariException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * `400` with `error: invalid_grant`: a wrong username or password on sign-in, or a refresh
     * token the server will not honour any more. Either way only the password can recover it.
     */
    class InvalidGrant(message: String = "invalid_grant") : BakalariException(message)

    /** `401` on a resource call: the access token was refused. Refreshed once and retried. */
    class Unauthorized(message: String = "401") : BakalariException(message)

    /** `403`: the account has no right to this module, which happens during the summer holidays. */
    class Forbidden(message: String = "403") : BakalariException(message)

    /** Another HTTP status. */
    class Http(val status: Int) : BakalariException("HTTP $status")

    /** No network, a timeout, or a TLS failure. */
    class Network(cause: IOException) : BakalariException("Network failure", cause)

    /** The server answered with something that is not the JSON it documents. */
    class Malformed(message: String, cause: Throwable? = null) : BakalariException(message, cause)

    /** The address does not answer as a Bakaláři server (`GET {base}/api`). */
    class NotBakalari(message: String = "Not a Bakaláři server") : BakalariException(message)

    /** The address is not an `https://` URL; nothing is sent to it. */
    class InsecureUrl(message: String = "Only https:// addresses are accepted") : BakalariException(message)

    /**
     * The stored sign-in has already been refused: the connection waits for the password, and no
     * request is made with a token the server has rejected.
     */
    class NeedsPassword(message: String = "The connection needs the password again") : BakalariException(message)

    /** The connection is not stored for the signed-in account any more. */
    class ConnectionGone(message: String = "No such connection") : BakalariException(message)
}
