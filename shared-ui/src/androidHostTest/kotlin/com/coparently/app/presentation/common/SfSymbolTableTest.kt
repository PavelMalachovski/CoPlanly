package com.coparently.app.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import com.google.gson.JsonParser
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iPhone's symbol table (MON-24 phase 3, part D, Task 5): [SF_SYMBOLS] covers every icon a
 * shared screen uses, its keys are the icons' own `ImageVector.name`s, and its values are the
 * owner-approved table's, row for row.
 */
class SfSymbolTableTest {

    @Test
    fun everyIconInUseHasARowOrIsKeptMaterial() {
        val used = commonSources()
            .flatMap { text -> ICON_REFERENCE.findAll(text).map { keyOf(it.value) } }
            .toSet()
        assertTrue(used.isNotEmpty(), "no icon references found in commonMain")

        val missing = used.filterNot { it in SF_SYMBOLS || it in KEPT_MATERIAL }.sorted()
        assertTrue(
            missing.isEmpty(),
            "icons with no SF Symbol row and not kept Material:\n${missing.joinToString("\n")}"
        )
    }

    @Test
    fun commentsAreNotScanned() {
        val code = "/** Icons.Default.X */\nval a = Icons.Default.Add // Icons.Filled.Y\n/* Icons.Outlined.Z */"
        val found = ICON_REFERENCE.findAll(withoutComments(code)).map { it.value }.toList()
        assertEquals(listOf("Icons.Default.Add"), found)
    }

    @Test
    fun keysAreRealVectorNames() {
        assertEquals("Filled.Add", Icons.Default.Add.name)
        assertEquals("Filled.Home", Icons.Filled.Home.name)
        assertEquals("Outlined.Home", Icons.Outlined.Home.name)
        assertEquals("AutoMirrored.Filled.ArrowBack", Icons.AutoMirrored.Filled.ArrowBack.name)
        assertEquals("AutoMirrored.Outlined.Chat", Icons.AutoMirrored.Outlined.Chat.name)
        assertEquals("Filled.Gavel", Icons.Default.Gavel.name)

        listOf(Icons.Default.Add, Icons.Filled.Home, Icons.Outlined.Home, Icons.AutoMirrored.Filled.ArrowBack)
            .plus(Icons.AutoMirrored.Outlined.Chat)
            .forEach { assertTrue(it.name in SF_SYMBOLS, "${it.name} has no row") }
        assertTrue(Icons.Default.Gavel.name in KEPT_MATERIAL && Icons.Default.Gavel.name !in SF_SYMBOLS)

        // The scan's translation agrees with the names.
        assertEquals("Filled.Add", keyOf("Icons.Default.Add"))
        assertEquals("Filled.Home", keyOf("Icons.Filled.Home"))
        assertEquals("Outlined.Home", keyOf("Icons.Outlined.Home"))
        assertEquals("AutoMirrored.Filled.ArrowBack", keyOf("Icons.AutoMirrored.Filled.ArrowBack"))
        assertEquals("AutoMirrored.Outlined.Chat", keyOf("Icons.AutoMirrored.Outlined.Chat"))
    }

    @Test
    fun tableMatchesApprovedJson() {
        val rows = JsonParser.parseString(approvedTable().readText()).asJsonArray.map { it.asJsonObject }
        assertEquals(APPROVED_ROWS, rows.size)
        val kept = rows.filter { it.get("keepMaterial").asBoolean }
        assertEquals(KEPT_MATERIAL.size, kept.size)

        kept.forEach { row ->
            val key = keyOf("Icons." + row.get("material").asString)
            assertTrue(key in KEPT_MATERIAL, "$key is kept in the approved table but not in the test's set")
            assertTrue(key !in SF_SYMBOLS, "$key is kept Material but has a symbol")
        }
        val approved = rows.filterNot { it.get("keepMaterial").asBoolean }
            .associate { keyOf("Icons." + it.get("material").asString) to it.get("sfSymbol").asString }
        approved.forEach { (key, symbol) -> assertEquals(symbol, SF_SYMBOLS[key], "the symbol of $key") }
        assertEquals(approved.keys, SF_SYMBOLS.keys, "the table has rows the approved table does not")
    }

    /** `Icons.Default.X` → `Filled.X`; every other form drops `Icons.`. */
    private fun keyOf(reference: String): String =
        reference.removePrefix("Icons.").let {
            if (it.startsWith("Default.")) "Filled." + it.removePrefix("Default.") else it
        }

    private fun moduleFile(path: String): File =
        listOf(File(path), File("shared-ui", path)).firstOrNull { it.exists() }
            ?: error("$path is not under the shared-ui module")

    private fun commonSources(): List<String> =
        moduleFile(SOURCE_ROOT).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { withoutComments(it.readText()) }
            .toList()

    /**
     * [source] without its comments (`//` lines, `/* */` blocks and KDoc), so an icon named in a
     * comment, such as the table's own KDoc, is not counted as in use. String literals are not
     * parsed: a `//` inside one cuts the rest of that line, which can only drop references, and no
     * icon reference sits after a URL on one line.
     */
    private fun withoutComments(source: String): String =
        source.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, "")

    private fun approvedTable(): File =
        listOf(File("../$APPROVED_TABLE"), File(APPROVED_TABLE)).firstOrNull { it.isFile }
            ?: error("$APPROVED_TABLE not found from ${File(".").absolutePath}")

    private companion object {
        const val SOURCE_ROOT = "src/commonMain/kotlin"
        const val APPROVED_TABLE = "docs/superpowers/plans/2026-11-16-iphone-phase-3d-sf-symbols.json"
        const val APPROVED_ROWS = 133

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        val ICON_REFERENCE = Regex("""Icons\.(Default|Filled|Outlined|AutoMirrored\.(Filled|Outlined))\.(\w+)""")

        /** The twelve icons the approved table keeps Material, by `ImageVector.name`. */
        val KEPT_MATERIAL = setOf(
            "Filled.AccountBalanceWallet",
            "Filled.Balance",
            "Filled.Diversity3",
            "Filled.DoneAll",
            "Filled.EventRepeat",
            "Filled.Gavel",
            "Filled.LinkOff",
            "Filled.NoFood",
            "Filled.PictureAsPdf",
            "Filled.SearchOff",
            "Filled.Source",
            "Filled.VolunteerActivism",
        )
    }
}
