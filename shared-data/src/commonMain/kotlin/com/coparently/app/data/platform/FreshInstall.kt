package com.coparently.app.data.platform

/** Remembers, in storage that is deleted with the app, that this install has started once. */
interface InstallMarker {

    /** Whether [set] has been called since the app was installed. */
    fun isSet(): Boolean

    /** Records that this install has started. */
    fun set()
}

/**
 * Clears what a previous install left in the sealed store on a fresh install (iPhone design: the
 * Keychain survives an uninstall, the app's user defaults do not). The first launch of an install
 * finds the [marker] unset, deletes [store]'s item and sets the marker; later launches touch
 * nothing. A failed delete is logged and never thrown: the store then carries on, and the marker is
 * set all the same, so the next launch does not delete what this one writes.
 */
class FreshInstall(private val marker: InstallMarker, private val store: SealedTextStore) {

    /** Runs the first-launch cleanup, once per install. */
    fun clearIfFirstLaunch() {
        if (marker.isSet()) return
        try {
            store.delete()
        } catch (failure: Exception) {
            logger.w(TAG, "Could not clear the previous install's preferences: ${failure.message}")
        }
        marker.set()
    }

    private companion object {
        const val TAG = "FreshInstall"
    }
}
