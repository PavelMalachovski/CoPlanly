package com.coparently.app.domain.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The owner's language rule (4 November 2026): the per-app choice; else the phone's first language
 * when the app ships it; else English with the phone's region. Runs on the JVM and on iOS.
 */
class AppLanguageRuleTest {

    @Test
    fun aShippedFirstLanguageIsSpokenAsThePhoneListsIt() {
        assertEquals("cs-CZ", AppLanguageRule.effective(null, "cs-CZ"))
        assertEquals("de-AT", AppLanguageRule.effective(null, "de-AT"))
        assertEquals("uk-UA", AppLanguageRule.effective(null, "uk-UA"))
        assertEquals("ru", AppLanguageRule.effective(null, "ru"))
        assertEquals("en-US", AppLanguageRule.effective(null, "en-US"))
        assertEquals("en-GB", AppLanguageRule.effective(null, "en-GB"))
    }

    @Test
    fun anUnshippedFirstLanguageIsEnglishWithThePhonesRegion() {
        // Slovak first: English, still in Slovakia for the currency hint and number formats.
        assertEquals("en-SK", AppLanguageRule.effective(null, "sk-SK"))
        // Irish then Czech: only the first language counts, so English, never Czech.
        assertEquals("en-IE", AppLanguageRule.effective(null, "ga-IE"))
        assertEquals("en-PL", AppLanguageRule.effective(null, "pl-PL"))
        assertEquals("en-RS", AppLanguageRule.effective(null, "sr-Latn-RS"))
        assertEquals("en-419", AppLanguageRule.effective(null, "es-419"))
        assertEquals("en-HK", AppLanguageRule.effective(null, "zh-yue-HK"))
    }

    @Test
    fun anUnshippedFirstLanguageWithNoRegionIsPlainEnglish() {
        assertEquals("en", AppLanguageRule.effective(null, "sk"))
        assertEquals("en", AppLanguageRule.effective(null, "sk-u-fw-mon"))
        assertEquals("en", AppLanguageRule.effective(null, "und"))
        assertEquals("en", AppLanguageRule.effective(null, ""))
    }

    @Test
    fun aPerAppChoiceWinsOverThePhone() {
        assertEquals("de", AppLanguageRule.effective("de", "cs-CZ"))
        assertEquals("cs", AppLanguageRule.effective("cs", "sk-SK"))
        assertEquals("en", AppLanguageRule.effective("en", "ru-RU"))
    }

    @Test
    fun aBlankOrUndeterminedChoiceIsNoChoice() {
        assertEquals("cs-CZ", AppLanguageRule.effective("", "cs-CZ"))
        assertEquals("cs-CZ", AppLanguageRule.effective("  ", "cs-CZ"))
        assertEquals("en-SK", AppLanguageRule.effective("und", "sk-SK"))
    }

    @Test
    fun theShippedLanguagesAreTheFiveTheAppTranslates() {
        assertEquals(setOf("en", "cs", "de", "ru", "uk"), AppLanguageRule.SHIPPED)
        assertEquals("en", AppLanguageRule.FALLBACK)
    }

    @Test
    fun aTagIsReadAsBcp47OrAsAnAndroidResourceName() {
        assertEquals("cs", AppLanguageRule.languageOf("CS_cz"))
        assertEquals("CZ", AppLanguageRule.regionOf("cs_cz"))
        assertEquals("sr", AppLanguageRule.languageOf("sr-Latn-RS"))
        assertNull(AppLanguageRule.regionOf("sr-Latn"))
        assertNull(AppLanguageRule.regionOf("de-1996"))
        assertNull(AppLanguageRule.regionOf("en-x-private"))
    }
}
