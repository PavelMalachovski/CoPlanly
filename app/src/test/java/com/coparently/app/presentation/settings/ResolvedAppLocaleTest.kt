package com.coparently.app.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * [ResolvedAppLocale.adjusted]: the default locale list Compose must see so its strings speak the
 * locale Android's resources resolved (MON-24, option (a)).
 */
class ResolvedAppLocaleTest {

    private fun tag(value: String): Locale = Locale.forLanguageTag(value)

    @Test
    fun `a first language the APK does not translate gives way to the resolved one`() {
        // Device [ga-IE, cs-CZ], say nothing in the APK speaks Irish: Android resolves Czech.
        val adjusted = ResolvedAppLocale.adjusted(listOf(tag("ga-IE"), tag("cs-CZ")), tag("cs-CZ"))

        assertEquals(listOf(tag("cs-CZ"), tag("ga-IE")), adjusted)
    }

    @Test
    fun `a resolved first language changes nothing`() {
        // Device [sk-SK, cs-CZ]: a library ships Slovak, so Android resolves Slovak and both
        // readers fall back to English.
        assertNull(ResolvedAppLocale.adjusted(listOf(tag("sk-SK"), tag("cs-CZ")), tag("sk-SK")))
        assertNull(ResolvedAppLocale.adjusted(listOf(tag("de-AT")), tag("de-AT")))
    }

    @Test
    fun `a per-app choice already first changes nothing`() {
        assertNull(ResolvedAppLocale.adjusted(listOf(tag("uk")), tag("uk")))
    }

    @Test
    fun `an empty default list takes the resolved locale alone`() {
        assertEquals(listOf(tag("en-US")), ResolvedAppLocale.adjusted(emptyList(), tag("en-US")))
    }

    @Test
    fun `the rest of the list keeps its order`() {
        val adjusted = ResolvedAppLocale.adjusted(
            listOf(tag("ga-IE"), tag("fr-FR"), tag("de-DE")),
            tag("de-DE")
        )

        assertEquals(listOf(tag("de-DE"), tag("ga-IE"), tag("fr-FR")), adjusted)
    }
}
