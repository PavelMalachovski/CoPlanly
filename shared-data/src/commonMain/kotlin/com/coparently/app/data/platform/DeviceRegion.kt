package com.coparently.app.data.platform

/**
 * The device's region as an ISO 3166 country code ("CZ"), or "" when the device names none (MON-24
 * phase 2, Task 8g): the last guess at a new expense's currency (`PreferencesRepositoryImpl`).
 *
 * On Android it is `java.util.Locale.getDefault().country`, what the repository read before it
 * moved; on the iPhone the current locale's country code.
 */
internal expect fun deviceRegionCode(): String
