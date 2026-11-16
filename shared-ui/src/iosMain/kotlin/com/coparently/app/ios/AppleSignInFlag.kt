package com.coparently.app.ios

import platform.Foundation.NSBundle
import platform.Foundation.NSNumber

/** The Info.plist key that turns Sign in with Apple on: YES in Debug, NO in Release (spec §3.8 call 3). */
internal const val APPLE_SIGN_IN_KEY = "CoplanlyAppleSignIn"

/**
 * Whether the app's bundle turns Sign in with Apple on: the iPhone's `APPLE_SIGN_IN` flag, which
 * `AppBuildValues.appleSignIn` carries. Off when the key is missing.
 */
internal fun bundleAppleSignIn(): Boolean =
    appleSignInFrom(NSBundle.mainBundle.objectForInfoDictionaryKey(APPLE_SIGN_IN_KEY))

/**
 * Reads the key's [value] as a boolean: a plist `<true/>` arrives as an `NSNumber` (or a Kotlin
 * `Boolean`, as Kotlin/Native may bridge it), and a build setting substituted into the plist as text
 * reads `YES` or `true`. Anything else, a missing key included, is off.
 */
internal fun appleSignInFrom(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is NSNumber -> value.boolValue
    is String -> value.trim().equals("YES", ignoreCase = true) || value.trim().equals("true", ignoreCase = true)
    else -> false
}
