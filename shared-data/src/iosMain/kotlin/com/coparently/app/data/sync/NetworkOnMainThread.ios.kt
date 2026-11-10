package com.coparently.app.data.sync

/** The iPhone has no such exception. */
internal actual fun Throwable.isNetworkOnMainThread(): Boolean = false
