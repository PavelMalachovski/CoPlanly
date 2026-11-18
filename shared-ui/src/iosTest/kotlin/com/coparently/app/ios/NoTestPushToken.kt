package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.IosPushToken

/** The Swift side's push token as a test process has it: none, as on the Firebase emulators. */
internal object NoTestPushToken : IosPushToken {
    override fun current(completion: (token: String?, error: String?) -> Unit) = completion(null, "no token in a test")

    override fun delete(completion: (error: String?) -> Unit) = completion(null)
}
