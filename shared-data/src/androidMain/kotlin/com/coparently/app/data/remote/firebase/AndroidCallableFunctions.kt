package com.coparently.app.data.remote.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * [CallableFunctions] on the Android SDK, the calls the app has always made: `getHttpsCallable`,
 * `call`, `getData`. A refusal is the SDK's [FirebaseFunctionsException], handed on as a
 * [CallableException] with its code and details, the SDK's exception as its cause.
 *
 * @param functions The app's instance, in `FUNCTIONS_REGION` (an e2e parent's names its app and
 *   emulator).
 */
class AndroidCallableFunctions(private val functions: FirebaseFunctions) : CallableFunctions {

    override suspend fun call(name: String, payload: Map<String, Any>): Any? = try {
        functions.getHttpsCallable(name).call(payload).await().getData()
    } catch (e: FirebaseFunctionsException) {
        throw CallableException(CallableCode.named(e.code.name), e.details, e.message, e)
    }
}
