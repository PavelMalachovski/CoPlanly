package com.coparently.app.pushwording

import android.content.Context

/**
 * [WordingStrings] over [context]'s resources: the app's own, generated from `:shared-ui`'s XML by
 * the app's `stringsToAndroidRes`, in the language [context] is configured for. A caller hands it
 * the context the text is worded in (`inAppLanguage()`), so a key reads exactly what
 * `getString`/`getQuantityString` of the app's `R.string`/`R.plurals` read.
 *
 * The keys are looked up by name, because this module cannot name the app's `R` class. A key the
 * release build's resource shrinker removed reads as unknown, so the keys this module words are
 * kept (`res/raw/` keep rules in the app) when it starts to read them.
 */
class AndroidWordingStrings(private val context: Context) : WordingStrings {
    override fun string(key: String, vararg args: Any): String =
        context.resources.getString(idOf(key, "string"), *args)

    override fun plural(key: String, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(idOf(key, "plurals"), count, *args)

    // A name is the one bridge to the app's resources from a library module.
    @Suppress("DiscouragedApi")
    private fun idOf(key: String, type: String): Int {
        val id = context.resources.getIdentifier(key, type, context.packageName)
        require(id != 0) { "No $type resource named $key" }
        return id
    }
}
