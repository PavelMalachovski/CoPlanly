package com.coparently.app.data.platform

import android.util.Log

/** `android.util.Log`, with each call's tag and message as they were before MON-24. */
internal object AndroidLogger : Logger {

    // A null error goes to the two-argument overload: the three-argument one appends a newline
    // and an empty stack, which would change the line logcat shows.
    override fun e(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
    }

    override fun w(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
    }

    override fun i(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.i(tag, message) else Log.i(tag, message, error)
    }

    override fun d(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.d(tag, message) else Log.d(tag, message, error)
    }
}

/** logcat; see the common declaration. */
internal actual fun platformLogger(): Logger = AndroidLogger
