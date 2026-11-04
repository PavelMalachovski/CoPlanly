package com.coparently.app.presentation.common

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** The current locale's language code; which locale the iPhone app's own choice sets is phase 3's. */
internal actual fun readerLanguage(): String = NSLocale.currentLocale.languageCode
