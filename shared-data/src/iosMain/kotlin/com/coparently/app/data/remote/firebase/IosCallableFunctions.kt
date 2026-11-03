@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.remote.firebase

import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.details
import dev.gitlive.firebase.functions.ios
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNull

/**
 * [CallableFunctions] on the iPhone, through GitLive: the payload through its encoder, the result
 * as the iOS SDK's own value turned into plain values, a refusal as a [CallableException].
 *
 * @param functions GitLive's instance in `FUNCTIONS_REGION`.
 */
class IosCallableFunctions(private val functions: FirebaseFunctions) : CallableFunctions {

    override suspend fun call(name: String, payload: Map<String, Any>): Any? = try {
        plainValue(functions.httpsCallable(name).invoke(payload).ios.data())
    } catch (e: FirebaseFunctionsException) {
        throw CallableException(CallableCode.named(e.code.name), plainValue(e.details), e.message, e)
    }

    private fun plainValue(value: Any?): Any? = when (value) {
        null, is NSNull -> null
        is Map<*, *> -> value.entries.associateTo(LinkedHashMap<String, Any?>()) { (key, entry) ->
            key.toString() to plainValue(entry)
        }
        is List<*> -> value.map(::plainValue)
        else -> value
    }
}
