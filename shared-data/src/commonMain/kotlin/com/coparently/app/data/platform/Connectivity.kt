package com.coparently.app.data.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device can reach the internet, as it changes (MON-24 phase 2, Task 6).
 *
 * Android's `NetworkMonitor` answers it from validated networks; the iPhone's `IosConnectivity`
 * from `NWPathMonitor`.
 */
interface Connectivity {

    /** True while a network can reach the internet. */
    val online: StateFlow<Boolean>

    /**
     * Whether a network can reach the internet now (Task 8f: `ErrorHandler` asks it of a failure).
     * [online]'s value unless the platform can ask the system directly, as Android's
     * `NetworkMonitor` does: [online] is shared only while it has a collector, so its value can be
     * the one from when it last had one.
     */
    fun isOnline(): Boolean = online.value
}
