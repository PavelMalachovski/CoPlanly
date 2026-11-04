package com.coparently.app.domain.language

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The owner's language rule (4 November 2026): the per-app choice; else the phone's first language
 * when the app ships it; else European English (`en-150`). Runs on the JVM and on iOS.
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
    fun anUnshippedFirstLanguageIsEuropeanEnglish() {
        // Slovak first: English, in European English's formats; the region is read from the phone.
        assertEquals("en-150", AppLanguageRule.effective(null, "sk-SK"))
        // Irish then Czech: only the first language counts, so English, never Czech.
        assertEquals("en-150", AppLanguageRule.effective(null, "ga-IE"))
        assertEquals("en-150", AppLanguageRule.effective(null, "pl-PL"))
        assertEquals("en-150", AppLanguageRule.effective(null, "sr-Latn-RS"))
        assertEquals("en-150", AppLanguageRule.effective(null, "es-419"))
        assertEquals("en-150", AppLanguageRule.effective(null, "zh-yue-HK"))
    }

    @Test
    fun anUnshippedFirstLanguageWithNoRegionIsEuropeanEnglishToo() {
        assertEquals("en-150", AppLanguageRule.effective(null, "sk"))
        assertEquals("en-150", AppLanguageRule.effective(null, "sk-u-fw-mon"))
        assertEquals("en-150", AppLanguageRule.effective(null, "und"))
        assertEquals("en-150", AppLanguageRule.effective(null, ""))
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
        assertEquals("en-150", AppLanguageRule.effective("und", "sk-SK"))
    }

    @Test
    fun theShippedLanguagesAreTheFiveTheAppTranslates() {
        assertEquals(setOf("en", "cs", "de", "ru", "uk"), AppLanguageRule.SHIPPED)
        assertEquals("en", AppLanguageRule.FALLBACK)
        assertEquals("en-150", AppLanguageRule.FALLBACK_LOCALE)
    }

    @Test
    fun aTagIsReadAsBcp47OrAsAnAndroidResourceName() {
        assertEquals("cs", AppLanguageRule.languageOf("CS_cz"))
        assertEquals("sr", AppLanguageRule.languageOf("sr-Latn-RS"))
        assertEquals("", AppLanguageRule.languageOf(""))
    }
}
