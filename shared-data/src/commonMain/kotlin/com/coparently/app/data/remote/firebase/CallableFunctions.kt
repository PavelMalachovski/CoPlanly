package com.coparently.app.data.remote.firebase

/**
 * The callable Cloud Functions, as the data layer calls them (MON-24 phase 2, Task 8a).
 *
 * [PairingFunctions] and [GoogleOAuthFunctions] used to call the Android SDK; they call this. Its
 * Android implementation is the SDK the app has always called, on the instance Koin builds in
 * `FUNCTIONS_REGION` (GitLive 2.7.0 has no public way to wrap an existing Android
 * `FirebaseFunctions`, and its `FirebaseFunctions.android` getter answers the default region's
 * instance); the iPhone's goes through GitLive.
 */
interface CallableFunctions {

    /**
     * Calls the function [name] with [payload] and answers its result as plain values
     * ([FirestoreValues], without timestamps), or null for none.
     *
     * @throws CallableException when the function, or the SDK on its behalf, refuses the call.
     */
    suspend fun call(name: String, payload: Map<String, Any>): Any?
}

/**
 * A callable's refusal: the SDK's status code, by name, and the `details` the function attached
 * (a map with `reason` for the ones this app's functions send).
 *
 * @property code The status.
 * @property details What the function sent beside it, as plain values, or null.
 */
class CallableException(
    val code: CallableCode,
    val details: Any?,
    message: String?,
    cause: Throwable?
) : Exception(message, cause)

/** A callable's status code, by the names both SDKs use. */
enum class CallableCode {
    OK,
    CANCELLED,
    UNKNOWN,
    INVALID_ARGUMENT,
    DEADLINE_EXCEEDED,
    NOT_FOUND,
    ALREADY_EXISTS,
    PERMISSION_DENIED,
    RESOURCE_EXHAUSTED,
    FAILED_PRECONDITION,
    ABORTED,
    OUT_OF_RANGE,
    UNIMPLEMENTED,
    INTERNAL,
    UNAVAILABLE,
    DATA_LOSS,
    UNAUTHENTICATED;

    companion object {
        /** The code named [name]; [UNKNOWN] for a name this build does not know. */
        fun named(name: String): CallableCode = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
