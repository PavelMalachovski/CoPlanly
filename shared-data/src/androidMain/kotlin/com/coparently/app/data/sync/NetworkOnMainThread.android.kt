package com.coparently.app.data.sync

import android.os.NetworkOnMainThreadException

/** Android's own exception for a network call made on the main thread. */
internal actual fun Throwable.isNetworkOnMainThread(): Boolean = this is NetworkOnMainThreadException
