package com.coparently.app.utils

import com.coparently.app.domain.language.AppLanguageRule
import platform.Foundation.NSLocale
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.preferredLanguages

/**
 * What [readerLocale] reads as the phone's preferred languages instead of Foundation's, or null
 * for Foundation's. A test seam: a test sets it and resets it to null when done.
 */
internal var preferredLanguagesForTest: List<String>? = null

/** The locale built for [cachedFor]; read and written on the main thread only, as the UI is. */
private var cached: NSLocale? = null
private var cachedFor: String? = null

/**
 * The locale every date, number and language seam of the iPhone app reads: the owner's language
 * rule ([AppLanguageRule.effective]) over the phone's first preferred language, never
 * `NSLocale.currentLocale`, which keeps the phone's region and any language the app does not ship.
 * iOS's own per-app language puts the app's choice first in `preferredLanguages`, which is why the
 * rule's per-app argument is null here.
 *
 * Cached by the first preferred language, so it is rebuilt when that changes.
 */
internal fun readerLocale(): NSLocale {
    val first = preferredFirst()
    val known = cached
    if (known != null && cachedFor == first) return known
    val identifier = NSLocale.canonicalLocaleIdentifierFromString(AppLanguageRule.effective(null, first))
    return NSLocale(localeIdentifier = identifier).also {
        cached = it
        cachedFor = first
    }
}

private fun preferredFirst(): String =
    preferredLanguagesForTest?.firstOrNull() ?: (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
