@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.pushwording

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.dataWithContentsOfFile

/**
 * [WordingStrings] over `PushStrings.strings` and `PushStrings.stringsdict` in the `<lang>.lproj`
 * of [bundle] (the notification extension's own, by default), which
 * `tools/ios/generate-push-strings.js` writes from `:shared-ui`'s XML. [languageTag] is the app's
 * resolved language (`AppLanguageRule`, shared through the App Group); a language with no folder
 * reads `en`, as Android falls back to `values/`.
 *
 * Both files are read as property lists and formatted here, not by `NSString`:
 * - a string's `%<n>$@` is read back as `%<n>$s` and the text formatted with [formatArgs], exactly
 *   as Compose Multiplatform and Android format it;
 * - a plural's quantity is picked by [pluralQuantity] (Android's rule) rather than by
 *   `NSString.localizedStringWithFormat`, because Kotlin/Native turns the localized `NSString`
 *   `NSBundle` answers into a plain Kotlin `String` and so loses the `.stringsdict` configuration
 *   that call selects by. The quantity's text is taken from the entry's `value` rule, its arguments
 *   shifted back by one (the file passes the count first), and formatted with [formatArgs]. A
 *   quantity the language's entry lacks reads `other`, as Android's does.
 */
class BundleWordingStrings(
    languageTag: String,
    bundle: NSBundle = NSBundle.mainBundle,
) : WordingStrings {
    private val language: String = localizationOf(bundle, languageTag)

    /** The reader's language, whose plural rules apply whichever folder the text came from, as on Android. */
    private val readerLanguage: String = primaryLanguage(languageTag)

    private val strings: Map<*, *> by lazy { table(bundle, "strings") }

    private val plurals: Map<*, *> by lazy { table(bundle, "stringsdict") }

    override fun string(key: String, vararg args: Any): String {
        val text = requireNotNull(strings[key] as? String) { "No string named $key" }
        return formatArgs(fromStringsFile(text), args)
    }

    override fun plural(key: String, count: Int, vararg args: Any): String {
        val entry = requireNotNull(plurals[key] as? Map<*, *>) { "No plural named $key" }
        val rule = requireNotNull(entry[PLURAL_VARIABLE] as? Map<*, *>) { "Plural $key has no rule" }
        val text = (rule[pluralQuantity(readerLanguage, count)] ?: rule[OTHER]) as? String
        return formatArgs(fromStringsdict(requireNotNull(text) { "Plural $key has no other" }), args)
    }

    private fun table(bundle: NSBundle, type: String): Map<*, *> {
        val path = bundle.pathForResource(TABLE, type, null, language) ?: return emptyMap<String, Any>()
        // Read as a property list: `NSDictionary`'s file initializers are deprecated, and Kotlin/Native
        // exposes the class factory only through its category.
        val data = NSData.dataWithContentsOfFile(path) ?: return emptyMap<String, Any>()
        val list = NSPropertyListSerialization.propertyListWithData(data, IMMUTABLE, null, null)
        return list as? Map<*, *> ?: emptyMap<String, Any>()
    }

    private companion object {
        const val TABLE = "PushStrings"
        const val PLURAL_VARIABLE = "value"
        const val OTHER = "other"
        const val BASE = "en"

        /** `NSPropertyListImmutable`: the table is only read. */
        const val IMMUTABLE: ULong = 0u

        val APPLE_STRING_ARGUMENT = Regex("""%(\d+)\$@""")
        val STRINGSDICT_ARGUMENT = Regex("""%(?:(\d+)\$([@d])|%)""")

        /** [tag], else its language (`cs-CZ` or `cs_CZ` → `cs`), else `en`: the first [bundle] has a folder for. */
        fun localizationOf(bundle: NSBundle, tag: String): String {
            val folders = bundle.localizations.mapNotNull { it as? String }.toSet()
            return listOf(tag, primaryLanguage(tag)).firstOrNull { it in folders } ?: BASE
        }

        /** A tag's language subtag, whether the tag is written with `-` (BCP 47) or `_` (Apple's locale ids). */
        fun primaryLanguage(tag: String): String = tag.substringBefore('-').substringBefore('_')

        /** A `.strings` text as the XML wrote it: `%<n>$@` back to `%<n>$s`. */
        fun fromStringsFile(text: String): String =
            APPLE_STRING_ARGUMENT.replace(text) { "%${it.groupValues[1]}\$s" }

        /** A `.stringsdict` quantity's text as the XML wrote it: arguments shifted back, `%%` back to `%`. */
        fun fromStringsdict(text: String): String = STRINGSDICT_ARGUMENT.replace(text) { match ->
            val position = match.groupValues[1]
            if (position.isEmpty()) {
                "%"
            } else {
                val conversion = if (match.groupValues[2] == "@") "s" else "d"
                "%${position.toInt() - 1}\$$conversion"
            }
        }
    }
}
