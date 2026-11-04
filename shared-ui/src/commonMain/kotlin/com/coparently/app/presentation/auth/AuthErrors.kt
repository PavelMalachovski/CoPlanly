package com.coparently.app.presentation.auth

/**
 * Which [AuthError] a failed sign-in is (MON-24 phase 2, slice 11f): the SDKs' exceptions are the
 * platform's, so the mapping is too. On Android the app's `AndroidAuthErrors`, the mappings
 * [AuthError]'s companion held before it moved; on the iPhone, where nobody signs in yet, a stand-in
 * that calls every failure [AuthError.Unknown].
 */
interface AuthErrors {

    /**
     * Maps a failure of the email/password path. Never null: typing an email and a password has no
     * "user changed their mind" outcome, so every failure here is worth reporting.
     */
    fun fromEmailPassword(cause: Throwable): AuthError

    /**
     * Maps a failure of the Google path.
     *
     * @return null when the user dismissed Google's sheet: a deliberate cancel is not an error
     */
    fun fromGoogle(cause: Throwable): AuthError?

    /**
     * Maps a failure of the saved-password path ("nothing saved yet" is its own reason here, where
     * the same exception means "no Google account" on the Google path).
     *
     * @return null when the user dismissed the sheet, as [fromGoogle]
     */
    fun fromSavedPassword(cause: Throwable): AuthError?
}
