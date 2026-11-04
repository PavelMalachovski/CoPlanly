package com.coparently.app.data.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

/** [DeviceRegion] on the iPhone: the current locale's country code, or "" when it names none. */
object IosDeviceRegion : DeviceRegion {
    override fun code(): String = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String ?: ""
}
