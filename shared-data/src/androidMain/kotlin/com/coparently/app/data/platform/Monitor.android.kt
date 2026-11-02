package com.coparently.app.data.platform

/** `synchronized` on a lock of its own; see the common declaration. */
internal actual fun newMonitor(): Monitor = object : Monitor {
    private val lock = Any()

    override fun <T> locked(block: () -> T): T = synchronized(lock, block)
}
