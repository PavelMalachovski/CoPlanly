package com.coparently.app.data.platform

import platform.Foundation.NSRecursiveLock

/** An `NSRecursiveLock`; see the common declaration. */
internal actual fun newMonitor(): Monitor = object : Monitor {
    private val lock = NSRecursiveLock()

    override fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
