package com.coparently.app.data.platform

import java.util.Locale

/** The default locale's region, as `PreferencesRepositoryImpl` read it in the app. */
internal actual fun deviceRegionCode(): String = Locale.getDefault().country
