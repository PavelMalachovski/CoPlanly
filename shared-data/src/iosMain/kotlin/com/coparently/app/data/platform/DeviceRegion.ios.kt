package com.coparently.app.data.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

/** The current locale's country code, or "" when it names none. */
internal actual fun deviceRegionCode(): String =
    NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String ?: ""
