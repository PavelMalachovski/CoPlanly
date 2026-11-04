package com.coparently.app.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * [ResolvedAppLocale.target]: the default locale Compose Multiplatform must see so its strings
 * speak the language Android's resources resolved (MON-24, option (a)).
 */
class ResolvedAppLocaleTest {

    private fun tag(value: String): Locale = Locale.forLanguageTag(value)

    @Test
    fun `a phone in Slovak then Czech reads Czech, in Slovakia`() {
        val target = ResolvedAppLocale.target(listOf(tag("sk-SK"), tag("cs-CZ")), tag("sk-SK"))

        assertEquals("cs", target?.language)
        assertEquals("SK", target?.country)
    }

    @Test
    fun `a shipped first language changes nothing`() {
        assertNull(ResolvedAppLocale.target(listOf(tag("de-AT"), tag("cs-CZ")), tag("de-AT")))
        assertNull(ResolvedAppLocale.target(listOf(tag("en-US")), tag("en-US")))
    }

    @Test
    fun `no shipped language anywhere changes nothing, both readers show English`() {
        assertNull(ResolvedAppLocale.target(listOf(tag("sk-SK"), tag("pl-PL")), tag("sk-SK")))
    }

    @Test
    fun `a per-app choice already in the default changes nothing`() {
        assertNull(ResolvedAppLocale.target(listOf(tag("uk")), tag("uk")))
    }

    @Test
    fun `a default left behind by an earlier choice is replaced`() {
        val target = ResolvedAppLocale.target(listOf(tag("ru-RU")), tag("cs-SK"))

        assertEquals("ru", target?.language)
        assertEquals("SK", target?.country)
    }

    @Test
    fun `a default with no region keeps none`() {
        val target = ResolvedAppLocale.target(listOf(tag("be"), tag("ru")), tag("be"))

        assertEquals(tag("ru"), target)
    }

    @Test
    fun `the shipped languages are the picker's`() {
        assertEquals(setOf("en", "cs", "de", "ru", "uk"), ResolvedAppLocale.SHIPPED)
    }
}
