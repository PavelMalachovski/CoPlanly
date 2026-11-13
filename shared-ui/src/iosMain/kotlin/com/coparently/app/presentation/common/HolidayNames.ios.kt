package com.coparently.app.presentation.common

import com.coparently.app.utils.readerLocale
import platform.Foundation.languageCode

/** The reader's language code ([readerLocale], the owner's language rule). */
internal actual fun readerLanguage(): String = readerLocale().languageCode
