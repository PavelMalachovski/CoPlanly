package com.coparently.app.data.session

/**
 * What runs on the iPhone while an account's session is ending, before it has ended (iPhone phase 4
 * part D): at sign-out, at account deletion and at an account switch, from [IosAccountTraces], which
 * all three call before the session ends (`AuthStateViewModel.signOut` before
 * `signOutCompletely`, `IosAccountDeletion` before its `signOutCompletely`, `IosAccountSwitch` as
 * it wipes the previous account's rows).
 *
 * The app's own module binds the Today widget's half (`IosApp`'s `iosAppModule`): the signed-out
 * snapshot written into the App Group and the remembered parents' names forgotten, so the home
 * screen never names the departing account's family once it has gone. A seam rather than the
 * widget's writer itself, because the writer and the names live in `:shared-ui`.
 *
 * Best effort, as every step of [IosAccountTraces] is: a failure is the caller's to log, and never
 * stops a sign-out.
 */
fun interface SessionEndingHook {

    /** Called while the session is still the departing account's. */
    suspend fun beforeSessionEnds()

    /** What [IosAccountTraces] calls where the app binds no hook: nothing. */
    companion object {
        /** A hook that does nothing. */
        val None: SessionEndingHook = SessionEndingHook {}
    }
}
