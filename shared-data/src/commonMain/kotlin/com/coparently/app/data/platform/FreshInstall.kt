package com.coparently.app.data.platform

/** Remembers, in storage that is deleted with the app, that this install has started once. */
interface InstallMarker {

    /** Whether [set] has been called since the app was installed. */
    fun isSet(): Boolean

    /** Records that this install has started. */
    fun set()
}

/**
 * One thing an earlier install of the app left behind that the first launch of this install
 * removes, such as the sealed store's Keychain item or Firebase Auth's saved session.
 *
 * @property what what is removed, for the log line when [remove] fails (never a value of it)
 * @property remove removes it; may throw, which [FreshInstall] logs
 */
class LeftoverCleanup(val what: String, val remove: () -> Unit)

/**
 * Clears what a previous install left behind on a fresh install (iPhone design: the Keychain
 * survives an uninstall, the app's user defaults do not). The first launch of an install finds the
 * [marker] unset, runs every one of [cleanups] in order and only then sets the marker; later
 * launches touch nothing. So a launch that dies part-way leaves the marker unset and the next one
 * runs them all again, and the marker is never set before every cleanup has run.
 *
 * A failed cleanup is logged and never thrown, and the next one still runs: the app then carries on,
 * and the marker is set all the same, so the next launch does not delete what this one writes.
 */
class FreshInstall(private val marker: InstallMarker, private val cleanups: List<LeftoverCleanup>) {

    /** Runs the first-launch cleanups, once per install. */
    @Suppress("TooGenericExceptionCaught") // any failure (the Keychain's and Firebase's types are iOS-only) is logged
    fun clearIfFirstLaunch() {
        if (marker.isSet()) return
        cleanups.forEach { cleanup ->
            try {
                cleanup.remove()
            } catch (failure: Exception) {
                logger.w(TAG, "Could not clear the previous install's ${cleanup.what}: ${failure.message}")
            }
        }
        marker.set()
    }

    private companion object {
        const val TAG = "FreshInstall"
    }
}
