package com.coparently.app.resources

import android.content.res.Resources
import com.coparently.app.resources.ResourceParity.ARRAY
import com.coparently.app.resources.ResourceParity.LANGUAGES
import com.coparently.app.resources.ResourceParity.STRING
import com.coparently.app.resources.ResourceParity.androidId
import com.coparently.app.resources.ResourceParity.androidResources
import com.coparently.app.resources.ResourceParity.argumentsFor
import com.coparently.app.resources.ResourceParity.inLanguage
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every string and string array reads the same through Android (`R.string`: aapt2 and
 * `String.format`) and through Compose Multiplatform (`Res`: its resource plugin and its
 * formatter), in each of the five languages (MON-24 phase 2, Task 9).
 *
 * Each string is read as written and, when it has arguments, formatted with `"A"` or `7` per
 * position ([ResourceParity.argumentsFor]). Where the two differ, the source text in
 * `shared-ui/src/commonMain/composeResources/` changes so both read it alike — never one consumer.
 * Check 7 of `tools/check-invariants.js` refuses the known differences offline; this is the proof
 * against the real parsers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class StringsParityTest {

    @Before
    fun setUp() = ResourceParity.ensureComposeResourcesContext()

    @Test
    fun `every string reads the same on Android and in Compose Multiplatform`() = runBlocking {
        val differences = mutableListOf<String>()
        for (language in LANGUAGES) {
            val android = androidResources(language)
            inLanguage(language) {
                for ((name, resource) in Res.allStringResources) {
                    differences += stringDifferences(language, android, name, resource)
                }
            }
        }
        val count = Res.allStringResources.size
        assertTrue("Res holds only $count strings", count > MINIMUM_STRINGS)
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    @Test
    fun `every string array reads the same on Android and in Compose Multiplatform`() = runBlocking {
        val differences = mutableListOf<String>()
        for (language in LANGUAGES) {
            val android = androidResources(language)
            inLanguage(language) {
                for ((name, resource) in Res.allStringArrayResources) {
                    differences += arrayDifferences(language, android, name, resource)
                }
            }
        }
        assertTrue("Res holds no string array", Res.allStringArrayResources.isNotEmpty())
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    @Test
    fun `a percent sign beside arguments is the one known difference, and it is still there`() = runBlocking {
        // Compose Multiplatform 1.9.3's formatter replaces `%<n>$s` and `%<n>$d` and nothing else, so
        // a literal `%%` stays doubled, while `String.format` prints `%`; no spelling reads as one
        // percent sign in both. When an upgrade formats `%%`, this fails: empty
        // PERCENT_WITH_ARGUMENTS here and in check 7 of tools/check-invariants.js.
        val name = "expenses_split_shares_named"
        val arguments = arrayOf<Any>("Alice", 70, "Bob", 30)
        val android = androidResources("en").getString(androidId(STRING, name)!!, *arguments)
        val compose = inLanguage("en") { getString(Res.allStringResources.getValue(name), *arguments) }
        assertEquals("Alice pays 70 %, Bob pays 30 %", android)
        assertEquals("Alice pays 70 %%, Bob pays 30 %%", compose)
    }

    private suspend fun stringDifferences(
        language: String,
        android: Resources,
        name: String,
        resource: StringResource,
    ): List<String> {
        val id = androidId(STRING, name) ?: return listOf("$name: in Res but not in R.string")
        val out = mutableListOf<String>()
        val written = android.getString(id)
        val composeWritten = getString(resource)
        if (composeWritten != written) out += difference(language, name, "as written", written, composeWritten)
        val arguments = argumentsFor(written)
        if (arguments.isNotEmpty()) {
            val formatted = android.getString(id, *arguments)
            val composeFormatted = getString(resource, *arguments)
            // The known difference, pinned by its own test above.
            val expected = if (name in PERCENT_WITH_ARGUMENTS) formatted.replace("%", "%%") else formatted
            if (composeFormatted != expected) {
                out += difference(language, name, "formatted", formatted, composeFormatted)
            }
        }
        return out
    }

    private suspend fun arrayDifferences(
        language: String,
        android: Resources,
        name: String,
        resource: StringArrayResource,
    ): List<String> {
        val id = androidId(ARRAY, name) ?: return listOf("$name: in Res but not in R.array")
        val expected = android.getStringArray(id).toList()
        val actual = getStringArray(resource)
        return if (actual == expected) emptyList() else listOf(difference(language, name, "", expected, actual))
    }

    private fun difference(language: String, name: String, how: String, android: Any, compose: Any) =
        "$language $name $how\n  Android: $android\n  Compose: $compose"

    private companion object {
        /** The app had about 2,300 strings when they moved; far fewer means `Res` is incomplete. */
        const val MINIMUM_STRINGS = 2000

        /**
         * The keys whose text holds `%%` beside arguments: check 7's `PERCENT_WITH_ARGUMENTS`
         * (tools/check-invariants.js). Read through `R.string` alone until `SplitShares.kt` moves to
         * `:shared-ui` (Task 11), which needs a wording both read alike first.
         */
        val PERCENT_WITH_ARGUMENTS = setOf(
            "expenses_split_shares_named",
            "expenses_split_shares_you_first",
            "expenses_split_shares_you_second",
        )
    }
}
