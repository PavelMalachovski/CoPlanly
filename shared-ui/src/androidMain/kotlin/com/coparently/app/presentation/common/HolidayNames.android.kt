package com.coparently.app.presentation.common

import java.util.Locale

/** `Locale.getDefault().language`, which [holidayDisplayName] compared before it moved. */
internal actual fun readerLanguage(): String = Locale.getDefault().language
