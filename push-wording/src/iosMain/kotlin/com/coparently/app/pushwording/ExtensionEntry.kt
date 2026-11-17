package com.coparently.app.pushwording

import platform.Foundation.NSBundle

/**
 * The Notification Service Extension's one call into this framework (iPhone phase 4 part C, design
 * §7.2): the two lines the push [data] words to for the account signed in on this iPhone, or null.
 *
 * [sharedUid] and [language] are what the app shares through the App Group `group.app.coplanly`
 * (`AppGroupShare`: `coplanly.push.uid`, `coplanly.push.language`). The answer is null — and the
 * extension then delivers the content as the server sent it, the neutral fallback (design §2
 * decision 4) — when:
 * - nobody is signed in ([sharedUid] null or blank: the app removed it at sign-out or deletion);
 * - the push is addressed to another account, or to none (`targetUserId` is not [sharedUid]): a
 *   push for the account that signed out is never worded with the next one's data;
 * - [PushWording] cannot word its type (item 15: an unknown type is dropped, never relayed).
 *
 * The strings are the extension's own bundle's (`<lang>.lproj/PushStrings.*`, which
 * `tools/ios/generate-push-strings.js` writes into it at build time), read in [language], or in
 * the owner's rule's English fallback, `en-150`, when the app shared none. Dates and times are on
 * the phone's zone and the language's clock ([FoundationReaderClock]).
 *
 * Swift calls it as `ExtensionEntryKt.wordPush(data:sharedUid:language:)`.
 */
fun wordPush(data: Map<String, String>, sharedUid: String?, language: String?): PushText? =
    wordPushFrom(NSBundle.mainBundle, data, sharedUid, language)

/** [wordPush] over the strings of [bundle]: the tests' bundle of generated strings. */
internal fun wordPushFrom(
    bundle: NSBundle,
    data: Map<String, String>,
    sharedUid: String?,
    language: String?,
): PushText? {
    val reader = sharedUid?.takeIf { it.isNotBlank() } ?: return null
    if (data[PushFields.TARGET_USER_ID] != reader) return null
    val tag = language?.takeIf { it.isNotBlank() } ?: FALLBACK_LANGUAGE
    val wording = PushWording(BundleWordingStrings(tag, bundle), FoundationReaderClock(tag))
    return wording.compose(data[PushFields.TYPE], data)
}

/** The owner's rule's English fallback (`AppLanguageRule.FALLBACK_LOCALE`), for a push with no shared language. */
private const val FALLBACK_LANGUAGE = "en-150"
