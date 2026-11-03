package com.coparently.app.resources

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.coparently.app.R
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/**
 * What [StringsParityTest] and [PluralsParityTest] share: the five app languages, Android's
 * resources in each, and the arguments a text is formatted with.
 *
 * One set of files is read twice since MON-24 phase 2 (Task 9): by aapt2 into `R.string` (the
 * app's generated copy of `:shared-ui`'s `composeResources`) and by Compose Multiplatform's
 * resource plugin into `Res`. Compose Multiplatform picks the language from [Locale.getDefault]
 * (its Android `getSystemEnvironment`), so a case sets that; Android's side reads a configuration
 * context in the same language.
 */
internal object ResourceParity {

    /** The app's languages; English is the base `values/` (there is no `values-en/`). */
    val LANGUAGES = listOf("en", "cs", "de", "ru", "uk")

    /** The value passed for a `%<n>$s` argument. */
    const val TEXT_ARGUMENT = "A"

    /** The value passed for a `%<n>$d` argument in a string (a plural passes its count). */
    const val NUMBER_ARGUMENT = 7

    private val POSITIONAL = Regex("""%(\d+)\$([sd])""")

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Android's resources in [language]. */
    fun androidResources(language: String): Resources {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration).resources
    }

    /**
     * Runs [block] with Compose Multiplatform's environment in [language], then restores it. Inline,
     * so the block may call Compose Multiplatform's suspending readers.
     */
    inline fun <T> inLanguage(language: String, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(language))
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    /**
     * The arguments for [text] by position: [number] where any `%<n>$d` names the position (a
     * number prints the same through `%s`), [TEXT_ARGUMENT] otherwise, and an empty list for a text
     * with no argument, which both sides then return as written.
     */
    fun argumentsFor(text: String, number: Int = NUMBER_ARGUMENT): Array<Any> {
        val kinds = mutableMapOf<Int, Char>()
        POSITIONAL.findAll(text).forEach { match ->
            val position = match.groupValues[1].toInt()
            val kind = match.groupValues[2].single()
            if (kinds[position] != 'd') kinds[position] = kind
        }
        val count = kinds.keys.maxOrNull() ?: 0
        return Array(count) { index -> if (kinds[index + 1] == 'd') number else TEXT_ARGUMENT }
    }

    /** The app's `R` id of [name] in [type] (`string`, `plurals`, `array`), or null. */
    fun androidId(type: Class<*>, name: String): Int? =
        runCatching { type.getField(name).getInt(null) }.getOrNull()

    /** `R.string`, `R.plurals` and `R.array`, for [androidId]. */
    val STRING: Class<*> = R.string::class.java
    val PLURALS: Class<*> = R.plurals::class.java
    val ARRAY: Class<*> = R.array::class.java

    /**
     * Compose Multiplatform's Android reader opens its files through the application context its
     * `AndroidContextProvider` records when the app starts. Robolectric creates the manifest's
     * content providers, so it is normally set; this sets it when it is not, so a Robolectric
     * change surfaces as a difference in text rather than as "Android context is not initialized".
     */
    fun ensureComposeResourcesContext() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        val field = provider.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
        if (field.get(null) == null) field.set(null, context)
    }
}
