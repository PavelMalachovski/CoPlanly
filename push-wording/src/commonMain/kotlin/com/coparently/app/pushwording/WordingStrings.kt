package com.coparently.app.pushwording

/**
 * The strings a notification is worded from, by resource key, already in the reader's language.
 *
 * On Android `AndroidWordingStrings` reads the app's own resources; on the iPhone
 * `BundleWordingStrings` reads the `.strings` and `.stringsdict` files
 * `tools/ios/generate-push-strings.js` writes from the same XML. Both answer the text Android's
 * `getString` and `getQuantityString` answer for the same key, language and arguments.
 */
interface WordingStrings {
    /** The string [key] formatted with [args]; throws `IllegalArgumentException` for an unknown key. */
    fun string(key: String, vararg args: Any): String

    /**
     * The plural [key]'s text for [count], formatted with [args] (which carry the count again when
     * the text prints it, as Android's `getQuantityString` takes it); throws
     * `IllegalArgumentException` for an unknown key.
     */
    fun plural(key: String, count: Int, vararg args: Any): String
}
