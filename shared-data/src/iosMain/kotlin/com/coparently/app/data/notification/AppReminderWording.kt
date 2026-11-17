package com.coparently.app.data.notification

import com.coparently.app.domain.language.AppLanguageRule
import com.coparently.app.pushwording.BundleWordingStrings
import com.coparently.app.pushwording.FoundationReaderClock
import com.coparently.app.pushwording.ReminderWording
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The local reminders' wording in the language the app speaks now (iPhone phase 4 part C): the
 * owner's rule ([AppLanguageRule.effective]) over the phone's first preferred language, as
 * `:shared-ui`'s `readerLanguageTag` answers it and `AppGroupShare` shares it with the notification
 * extension, so a reminder and a push read alike. The strings are the generated `PushStrings` the
 * app's own bundle carries (`<lang>.lproj`), the dates, times and amounts Foundation's on the phone's
 * zone and the language's clock ([FoundationReaderClock]) — what the extension words a push with.
 *
 * Built afresh for each re-arm, so a change of language in the phone's Settings (which restarts the
 * app) is followed.
 */
fun appReminderWording(): ReminderWording {
    val first = NSLocale.preferredLanguages.firstOrNull() as? String ?: FIRST_LANGUAGE_FALLBACK
    val tag = AppLanguageRule.effective(null, first)
    return ReminderWording(BundleWordingStrings(tag), FoundationReaderClock(tag))
}

/** What `readerLanguageTag` reads when the phone lists no preferred language. */
private const val FIRST_LANGUAGE_FALLBACK = "en"
