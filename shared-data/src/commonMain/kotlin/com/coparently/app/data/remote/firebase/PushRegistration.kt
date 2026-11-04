package com.coparently.app.data.remote.firebase

/**
 * This device's push registration as the parent controls it (MON-24 phase 2, Task 10): the Settings
 * switch, and the token sign-out takes off the profile. What `SettingsViewModel` and
 * `AuthStateViewModel` ask of Firebase Messaging, which stays in the app.
 *
 * The app's `FcmService` answers it on Android, beside [PushDevice]; the iPhone's push
 * registration will answer it there (phase 3).
 */
interface PushRegistration {

    /**
     * Whether this phone can register for pushes at all. Settings offers the push switch only when it
     * can (design item 8): Firebase Messaging on Android always can; the iPhone's stand-in cannot yet,
     * and turning it on there would always fail (MON-24 phase 2, slice 11f).
     */
    val available: Boolean get() = true

    /** Whether this person has left push notifications on. On unless they switched it off. */
    fun isPushEnabled(): Boolean

    /**
     * Records the Settings switch and makes it true on the server: off detaches this device's
     * token, on registers it again.
     *
     * @return failure when turning it on could not register a token; turning it off is
     *   best-effort and always succeeds locally.
     */
    suspend fun setPushEnabled(enabled: Boolean): Result<Unit>

    /**
     * Takes this device's token off the signed-in account's profile and deletes it, so the next
     * account on the phone is not pushed the previous one's messages. Best-effort: never throws.
     */
    suspend fun unregisterToken()
}
