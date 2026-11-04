package com.coparently.app.domain.platform

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

/** NFD through Foundation's `decomposedStringWithCanonicalMapping`. */
@Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin String is bridged to NSString on Apple platforms.
actual fun String.canonicalDecomposition(): String = (this as NSString).decomposedStringWithCanonicalMapping
