package com.coparently.app.data.school.edupage

import kotlinx.io.IOException

/**
 * Why a call to EduPage failed, as far as the school import acts on it.
 *
 * Facts, not sentences: each message is fixed English for the log and never names the account,
 * the password or a cookie. The screens word each kind in the reader's language and never render
 * a message (CQ-14).
 */
sealed class EdupageException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** EduPage sent the sign-in back with `bad=1`: a wrong username or password. */
    class BadCredentials : EdupageException("Bad credentials")

    /**
     * EduPage asked for a CAPTCHA (`cap=1`, or the error code `lerr=b43b43`), which the app cannot
     * answer: the parent signs in on EduPage's own site once, and then tries again.
     */
    class Captcha : EdupageException("CAPTCHA required")

    /**
     * The account asks for a second factor (a redirect to `/login/twofactor`). The app does not
     * complete it.
     *
     * @property session The cookies the sign-in had collected when EduPage asked.
     * @property host The host that asked.
     */
    class SecondFactor(
        val session: Map<String, String>,
        val host: String
    ) : EdupageException("Second factor required")

    /** The stored session cookie was refused: EduPage answered with its sign-in page. */
    class SessionExpired : EdupageException("Session expired")

    /**
     * No network, a timeout, or a TLS failure: what the HTTP engine throws, `java.io.IOException` on
     * Android and an NSURLSession error on iOS, both `kotlinx.io.IOException`.
     */
    class Network(cause: IOException) : EdupageException("Network failure", cause)

    /**
     * EduPage answered with a status that is not a success, a redirect it may not follow, or, for a
     * child switch, with something other than `OK`.
     *
     * @property status The HTTP status of the last answer.
     */
    class Server(val status: Int) : EdupageException("HTTP $status")

    /**
     * The host does not answer as an EduPage server: no sign-in token on its sign-in page, an
     * address that is not a host, or a page whose user data cannot be read.
     */
    class NotEdupage(message: String = "Not an EduPage server") : EdupageException(message)
}
