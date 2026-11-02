@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * `NWPathMonitor`, started when this is created and kept for the life of the process (one per app,
 * from the graph).
 *
 * It starts at offline, never online: the monitor answers within moments of starting, and a screen
 * that briefly says "offline" is wrong for less long than one that claims a connection nobody saw.
 */
class IosConnectivity : Connectivity {

    private val state = MutableStateFlow(false)

    override val online: StateFlow<Boolean> = state.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            state.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}
