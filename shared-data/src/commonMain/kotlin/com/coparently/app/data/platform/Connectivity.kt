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
}
