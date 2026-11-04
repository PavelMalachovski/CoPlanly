package com.coparently.app.presentation.platform

/**
 * Something a screen asks of the phone it runs on that common code cannot do itself (MON-24 phase
 * 2, Task 11): share, pick a file, take a photo, scan a code, dictate, ask for a permission, open
 * a link.
 *
 * Each capability is one interface in this package, provided to the screens through its
 * `staticCompositionLocalOf` by the shell ([ProvidePlatform]): on Android `MainActivity`, from the
 * Android code the screens called before they moved; on the iPhone the stand-ins of
 * `iosPlatformServices`, until phase 3 and 4 give it each capability.
 */
interface PlatformCapability {

    /**
     * Whether this phone can do it at all. A screen draws no affordance for a capability that is not
     * [available], because a control that cannot keep its promise is design item 8's defect.
     */
    val available: Boolean
}

/** Starts what a capability prepared in composition: a picker, the camera, a scanner, a request. */
fun interface PlatformLauncher {

    /** Opens it; its answer arrives through the callback the launcher was remembered with. */
    fun launch()
}
