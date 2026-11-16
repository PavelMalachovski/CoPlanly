package com.coparently.app.presentation.common

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Every shared icon goes through [AppIcon] (MON-24 phase 3, part D), so the iPhone can draw its SF
 * Symbol: no file in `commonMain` imports Material's `Icon` or calls a bare `Icon(`. The second check
 * catches a call that reaches Material's `Icon` through the wildcard `androidx.compose.material3.*`
 * a few screens import. There is no allow-list: the one Material call is `AppIcon`'s Android
 * `actual`, in `androidMain`.
 */
class AppIconGuardTest {

    @Test
    fun noDirectMaterialIconInCommonMain() {
        val sources = commonSources()
        val importing = sources.filter { (_, text) -> MATERIAL_ICON_IMPORT.containsMatchIn(text) }
            .map { it.first }
        val calling = sources.filter { (_, text) -> BARE_ICON_CALL.containsMatchIn(text) }.map { it.first }

        assertTrue(
            importing.isEmpty(),
            "import Material's Icon; call AppIcon instead:\n${importing.joinToString("\n")}"
        )
        assertTrue(calling.isEmpty(), "call Icon( directly; call AppIcon instead:\n${calling.joinToString("\n")}")
    }

    @Test
    fun theChecksSeeWhatTheyLookFor() {
        assertTrue(MATERIAL_ICON_IMPORT.containsMatchIn("import a.B\nimport androidx.compose.material3.Icon\n"))
        assertTrue(!MATERIAL_ICON_IMPORT.containsMatchIn("import androidx.compose.material3.IconButton\n"))
        assertTrue(BARE_ICON_CALL.containsMatchIn("    Icon(Icons.Default.Add, null)"))
        assertTrue(!BARE_ICON_CALL.containsMatchIn("AppIcon(x, null) HeroIcon(y) material3.Icon"))
    }

    /** Each `.kt` file under `commonMain/kotlin`, by its path below it, with its text. */
    private fun commonSources(): List<Pair<String, String>> {
        val root = listOf(File(SOURCE_ROOT), File("shared-ui", SOURCE_ROOT)).firstOrNull { it.isDirectory }
            ?: error("$SOURCE_ROOT is not under the shared-ui module")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .sortedBy { it.first }
            .toList()
            .also { assertTrue(it.isNotEmpty(), "no sources found under $root") }
    }

    private companion object {
        /** `shared-ui`'s common sources, below the module. */
        const val SOURCE_ROOT = "src/commonMain/kotlin"

        /** The exact import of Material's `Icon` (not `IconButton` or any other `Icon…`). */
        val MATERIAL_ICON_IMPORT = Regex("""^import androidx\.compose\.material3\.Icon\s*$""", RegexOption.MULTILINE)

        /** A call of a function named exactly `Icon`, not `AppIcon(`, `HeroIcon(` or `x.Icon(`. */
        val BARE_ICON_CALL = Regex("""(?<![\w.])Icon\(""")
    }
}
