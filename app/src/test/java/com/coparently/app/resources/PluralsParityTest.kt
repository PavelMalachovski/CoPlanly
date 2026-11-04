package com.coparently.app.resources

import android.content.res.Resources
import com.coparently.app.resources.ResourceParity.LANGUAGES
import com.coparently.app.resources.ResourceParity.PLURALS
import com.coparently.app.resources.ResourceParity.androidId
import com.coparently.app.resources.ResourceParity.androidResources
import com.coparently.app.resources.ResourceParity.argumentsFor
import com.coparently.app.resources.ResourceParity.inLanguage
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.getPluralString
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every plural selects the same text, formatted the same, on Android (`getQuantityString`, ICU's
 * plural rules) and in Compose Multiplatform (`getPluralString`, its own CLDR tables), for every
 * count from 0 to 200 in each of the five languages (MON-24 phase 2, Task 9).
 *
 * The count is passed as every `%<n>$d` argument and `"A"` as every `%<n>$s`, as the app's callers
 * pass the count. A difference is fixed in the source text (or, for a rule, reported), never in
 * one consumer; see [StringsParityTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class PluralsParityTest {

    @Before
    fun setUp() = ResourceParity.ensureComposeResourcesContext()

    @Test
    fun `every plural selects and formats the same text for the counts 0 to 200`() = runBlocking {
        val differences = mutableListOf<String>()
        for (language in LANGUAGES) {
            val android = androidResources(language)
            inLanguage(language) {
                for ((name, resource) in Res.allPluralStringResources) {
                    differences += pluralDifferences(language, android, name, resource)
                }
            }
        }
        assertTrue("Res holds no plural", Res.allPluralStringResources.isNotEmpty())
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    /** The first count at which [name] differs in [language], if any (one line per key, not 201). */
    private suspend fun pluralDifferences(
        language: String,
        android: Resources,
        name: String,
        resource: PluralStringResource,
    ): List<String> {
        val id = androidId(PLURALS, name) ?: return listOf("$name: in Res but not in R.plurals")
        for (count in COUNTS) {
            val arguments = argumentsFor(android.getQuantityText(id, count).toString(), number = count)
            val expected = android.getQuantityString(id, count, *arguments)
            val actual = getPluralString(resource, count, *arguments)
            if (actual != expected) {
                return listOf("$language $name at $count\n  Android: $expected\n  Compose: $actual")
            }
        }
        return emptyList()
    }

    private companion object {
        val COUNTS = 0..200
    }
}
