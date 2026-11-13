package com.coparently.app.data.session

import com.coparently.app.data.platform.FreshInstall
import com.coparently.app.data.platform.InstallMarker
import com.coparently.app.data.platform.KeychainTextStore
import com.coparently.app.data.platform.LeftoverCleanup
import com.coparently.app.data.platform.SealedTextStore
import com.coparently.app.data.platform.UserDefaultsInstallMarker
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.runBlocking

/**
 * Removes what an earlier install of the app left in the Keychain, on the first launch of this
 * install and never again (MON-24 phase 3, Task 5). On Android an uninstall takes the app's data and
 * Firebase Auth's session with it; on the iPhone the Keychain survives it, so without this a
 * reinstall would open on the previous account, still signed in, with its sealed preferences:
 * - the sealed preference store's item ([KeychainTextStore]), deleted;
 * - Firebase Auth's saved session, signed out.
 *
 * Both run before the install marker ([UserDefaultsInstallMarker], deleted with the app) is set, so
 * a launch never marks the install without having signed out ([FreshInstall]). A failure of either
 * is logged and never thrown, as a failed delete always was.
 *
 * Call it once at launch, after Firebase is configured (`IosFirebase`) and before the Koin graph is
 * started and anything is drawn: the account switch's last uid also lives in the user defaults, so
 * the next sign-in finds an empty last uid, an empty store and nobody signed in.
 */
fun clearPreviousInstall() {
    previousInstallCleanup(UserDefaultsInstallMarker, KeychainTextStore(), Firebase.auth).clearIfFirstLaunch()
}

/**
 * [clearPreviousInstall]'s cleanup over the given [marker], preference [store] and [auth], in that
 * order: the store's item, then the session.
 *
 * GitLive's `signOut` is a suspend function wrapping the iOS SDK's synchronous `signOut:`, which
 * never suspends, so blocking on it here costs the launch nothing and keeps the order certain.
 */
internal fun previousInstallCleanup(marker: InstallMarker, store: SealedTextStore, auth: FirebaseAuth): FreshInstall =
    FreshInstall(
        marker,
        listOf(
            LeftoverCleanup("preferences", store::delete),
            LeftoverCleanup("signed-in session") { runBlocking { auth.signOut() } },
        ),
    )
