package com.coparently.app.presentation.settings

import com.coparently.app.domain.language.AppLanguageRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The languages the rule calls shipped ([AppLanguageRule.SHIPPED]) are the ones the app translates,
 * offers in Settings → Language and declares to Android 13's per-app language setting. A language
 * added to one place and not to the rule would be translated and never spoken on a phone set to it.
 */
class ShippedLanguagesTest {

    @Test
    fun `the picker offers exactly the shipped languages`() {
        val offered = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { it.tag }.toSet()
        assertEquals(AppLanguageRule.SHIPPED, offered)
    }

    @Test
    fun `the locale config declares exactly the shipped languages`() {
        val config = moduleFile("src/main/res/xml/locales_config.xml").readText()
        val declared = Regex("""<locale\s+android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(AppLanguageRule.SHIPPED, declared)
    }

    @Test
    fun `the string folders are exactly the shipped languages`() {
        val resources = moduleFile("../shared-ui/src/commonMain/composeResources")
        val folders = resources.listFiles().orEmpty()
            .map { it.name }
            .filter { it == "values" || it.startsWith("values-") }
            .map { if (it == "values") AppLanguageRule.FALLBACK else it.removePrefix("values-") }
            .toSet()
        assertEquals(AppLanguageRule.SHIPPED, folders)
    }

    /** [path] under the app module, whether the tests run from the module or from the root. */
    private fun moduleFile(path: String): File =
        listOf(File(path), File("app", path)).firstOrNull { it.exists() }
            ?: error("$path is not under the app module")
}
