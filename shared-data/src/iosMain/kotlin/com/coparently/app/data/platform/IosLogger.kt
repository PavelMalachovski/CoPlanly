package com.coparently.app.data.platform

/**
 * The process's standard output, in logcat's `<level>/<tag>: <message>` shape, which Xcode's
 * console shows while the app runs from it. Nothing is kept and nothing leaves the phone.
 */
internal object IosLogger : Logger {

    override fun e(tag: String, message: String, error: Throwable?) = print("E", tag, message, error)

    override fun w(tag: String, message: String, error: Throwable?) = print("W", tag, message, error)

    override fun i(tag: String, message: String, error: Throwable?) = print("I", tag, message, error)

    override fun d(tag: String, message: String, error: Throwable?) = print("D", tag, message, error)

    private fun print(level: String, tag: String, message: String, error: Throwable?) {
        println("$level/$tag: $message")
        error?.let { println(it.stackTraceToString()) }
    }
}

/** Standard output; see the common declaration. */
internal actual fun platformLogger(): Logger = IosLogger
