package com.coparently.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Android app is a thin shell over `:shared-ui` (MON-24 phase 2, Task 12): its UI and its
 * ViewModels live in the shared module, which the iPhone app runs too, so a screen or a ViewModel
 * written in `app/src/main/java/` is one the iPhone will never have.
 *
 * This fails when a file there declares a `@Composable` function or property, other than those
 * [ALLOWED] names with its reason, or declares a `ViewModel` subclass at all. `MainActivity`'s
 * `setContent { … }` and `QRScannerActivity`'s are lambdas, not declarations, and need no entry.
 * Comments and string literals are not read, so a KDoc that mentions a composable is not one.
 * Every `.kt` file under `src/main/` is read (`java/` and `kotlin/`, any package), a use-site target
 * (`@get:Composable`), a fully qualified annotation and a `context(…)` clause are matched, and an
 * import that renames `Composable`, `ViewModel` or `AndroidViewModel` fails on its own, because a
 * renamed one would hide from both patterns (Task 12 review, I1).
 */
class ShellBoundaryTest {

    @Test
    fun theShellDeclaresNoComposableOutsideItsAllowList() {
        val offenders = shellSources()
            .filter { (path, text) -> path !in ALLOWED && declaresComposable(text) }
            .map { it.first }
        assertEquals(
            "a @Composable in the app's main sources: move it to :shared-ui, or add the file to " +
                "ALLOWED with the reason the shell must keep it",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun theShellDeclaresNoViewModel() {
        val offenders = shellSources().filter { (_, text) -> declaresViewModel(text) || renamesWatchedImport(text) }
            .map { it.first }
        assertEquals("a ViewModel in the app's main sources: it belongs in :shared-ui", emptyList<String>(), offenders)
    }

    @Test
    fun everyAllowedFileStillExistsAndStillDeclaresAComposable() {
        val sources = shellSources().toMap()
        ALLOWED.keys.forEach { path ->
            val text = sources[path]
            assertTrue("ALLOWED names $path, which is gone: remove the entry", text != null)
            assertTrue("$path declares no @Composable any more: remove its entry", declaresComposable(text.orEmpty()))
        }
    }

    @Test
    fun theDetectorReadsDeclarationsAndNothingElse() {
        assertTrue(declaresComposable("@Composable\nfun A() {}"))
        assertTrue(declaresComposable("@Preview(name = \"x (y)\")\n@Composable\nprivate fun A() {}"))
        assertTrue(declaresComposable("@Composable internal inline fun <T> A(): T = TODO()"))
        assertTrue(declaresComposable("val a: Int\n    @Composable get() = 1"))
        assertTrue(declaresComposable("@Composable\noverride fun rememberPicker(): Launcher = TODO()"))
        assertTrue(!declaresComposable("fun A(content: @Composable () -> Unit) {}"))
        assertTrue(!declaresComposable("fun A(content: @Composable RowScope.() -> Unit) {}"))
        assertTrue(!declaresComposable("val content = @Composable { Text(\"a\") }"))
        assertTrue(!declaresComposable("// @Composable fun A() {}\n/** @Composable fun B() */\nfun C() {}"))
        assertTrue(!declaresComposable("val s = \"@Composable fun A()\"\nval g = \"**/*.kt\"\nfun C() {}"))
        assertTrue(declaresViewModel("class A : ViewModel() {}"))
        assertTrue(declaresViewModel("class A(app: Application) : AndroidViewModel(app)"))
        assertTrue(declaresViewModel("class A(x: Int) : Base(), androidx.lifecycle.ViewModel()"))
        assertTrue(!declaresViewModel("val vm: SyncViewModel by viewModel()\nval f = ViewModelProvider(store)"))
        assertTrue(!declaresViewModel("// class A : ViewModel()"))
        assertTrue(declaresComposable("val a: Int\n    @get:Composable get() = 1"))
        assertTrue(declaresComposable("@androidx.compose.runtime.Composable\nfun A() {}"))
        assertTrue(declaresComposable("@Composable\ncontext(scope: RowScope)\nfun A() {}"))
        assertTrue(declaresComposable("context(scope: RowScope)\n@Composable\nfun A() {}"))
        assertTrue(renamesWatchedImport("import androidx.compose.runtime.Composable as C"))
        assertTrue(renamesWatchedImport("import androidx.lifecycle.ViewModel as VM"))
        assertTrue(renamesWatchedImport("import androidx.lifecycle.AndroidViewModel as Avm"))
        assertTrue(!renamesWatchedImport("import androidx.compose.runtime.Composable\nimport a.b.C as D"))
    }

    /** Each `.kt` file under the app's main sources, by its path below [SOURCE_ROOT], with its text. */
    private fun shellSources(): List<Pair<String, String>> {
        val root = listOf(File(SOURCE_ROOT), File("app", SOURCE_ROOT)).firstOrNull { it.isDirectory }
            ?: error("$SOURCE_ROOT is not under the app module")
        assertTrue("no java sources under $root", File(root, APP_PACKAGE).isDirectory)
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .sortedBy { it.first }
            .toList()
            .also { assertTrue("no sources found under $root", it.isNotEmpty()) }
    }

    private companion object {

        /** The app's main source sets, below the app module: `java/` and `kotlin/`, every package. */
        const val SOURCE_ROOT = "src/main"

        /** The app's own package below [SOURCE_ROOT], which [ALLOWED]'s paths start with. */
        const val APP_PACKAGE = "java/com/coparently/app"

        private const val TRIPLE_QUOTE = "\"\"\""

        private const val PREVIEWS = "an Android Studio preview of shared UI: `@Preview` is Android's tooling"

        /**
         * The files under [SOURCE_ROOT] that may declare a `@Composable`, each with the reason the
         * shell keeps it. Paths are below [SOURCE_ROOT].
         */
        val ALLOWED: Map<String, String> = mapOf(
            "$APP_PACKAGE/presentation/platform/AndroidPlatform.kt" to
                "the Android halves of `presentation/platform/`: the pickers, the camera, the " +
                "permissions and the images are composable members, and `ProvideAndroidPlatform`",
            "$APP_PACKAGE/presentation/chat/DictationLanguage.kt" to
                "the dictation language the Android `Dictation` reads in composition",
            "$APP_PACKAGE/presentation/pairing/QrScannerScreen.kt" to
                "`QRScannerActivity`'s own camera screen (CameraX), an activity of its own",
            "$APP_PACKAGE/presentation/widget/TodayWidget.kt" to
                "the home-screen widget's Glance content, not the app's UI",
            "$APP_PACKAGE/presentation/widget/TodayWidgetContent.kt" to "the home-screen widget's Glance content",
            "$APP_PACKAGE/presentation/calendar/CalendarPreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/presentation/chat/ChatPreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/presentation/common/DesignSystemPreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/presentation/components/TimePickerPreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/presentation/expenses/ExpensePreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/presentation/home/HomePreviews.kt" to PREVIEWS,
            "$APP_PACKAGE/utils/PreviewHelpers.kt" to "the previews' wrapper and sample data",
        )

        /**
         * `@Composable` on a declaration: after it, any other annotations (with arguments) and
         * modifiers, then `fun`, `val`, `var` or a getter. On a function type or a lambda it is
         * followed by `(`, a receiver type or `{`, which this does not match.
         */
        private val COMPOSABLE_DECLARATION = Regex(
            "@(?:get:|set:)?(?:androidx\\.compose\\.runtime\\.)?Composable\\b" +
                "(?:\\s|@[\\w.:]+(?:\\((?:[^()]|\\([^()]*\\))*\\))?|context\\((?:[^()]|\\([^()]*\\))*\\))*" +
                "(?:(?:private|internal|public|protected|override|inline|actual|expect|operator|open|" +
                "abstract|final|suspend)\\s+)*(?:fun|val|var|get)\\b"
        )

        /** An import that renames `Composable`, `ViewModel` or `AndroidViewModel`. */
        private val RENAMED_IMPORT = Regex(
            "import\\s+[\\w.]*\\.(?:Composable|ViewModel|AndroidViewModel)\\s+as\\s+\\w+"
        )

        /** A supertype entry naming `ViewModel` or `AndroidViewModel`, with its constructor call. */
        private val VIEW_MODEL_SUPERTYPE = Regex("[:,]\\s*(?:androidx\\.lifecycle\\.)?(?:Android)?ViewModel\\s*\\(")

        fun declaresComposable(text: String): Boolean = COMPOSABLE_DECLARATION.containsMatchIn(code(text))

        fun declaresViewModel(text: String): Boolean = VIEW_MODEL_SUPERTYPE.containsMatchIn(code(text))

        fun renamesWatchedImport(text: String): Boolean = RENAMED_IMPORT.containsMatchIn(code(text))

        /**
         * [text] with its comments and the contents of its string and character literals blanked,
         * so only code is matched: a glob in a string does not open a comment, and a KDoc that names
         * a composable is not a declaration.
         */
        fun code(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) i = copyCodeAt(text, i, out)
            return out.toString()
        }

        /**
         * Copies the character at [i] to [out] when it is code, or skips the comment or literal that
         * opens there (a literal leaves its two quotes), and answers the index after what it read.
         */
        private fun copyCodeAt(text: String, i: Int, out: StringBuilder): Int {
            val char = text[i]
            return when {
                text.startsWith("//", i) -> indexOrEnd(text, "\n", i)
                text.startsWith("/*", i) -> skipPast(text, "*/", i + 2).also { out.append(' ') }
                text.startsWith(TRIPLE_QUOTE, i) -> skipPast(text, TRIPLE_QUOTE, i + 3).also { out.append("\"\"") }
                char == '"' || char == '\'' -> skipLiteral(text, i).also { out.append(char).append(char) }
                else -> (i + 1).also { out.append(char) }
            }
        }

        /** The index of the first [end] at or after [from], or the text's length. */
        private fun indexOrEnd(text: String, end: String, from: Int): Int =
            text.indexOf(end, from).takeIf { it >= 0 } ?: text.length

        /** The index just past the first [end] at or after [from], or the text's length. */
        private fun skipPast(text: String, end: String, from: Int): Int =
            minOf(indexOrEnd(text, end, from) + end.length, text.length)

        /** The index just past the one-line string or character literal opening at [start]. */
        private fun skipLiteral(text: String, start: Int): Int {
            val quote = text[start]
            var i = start + 1
            while (i < text.length && text[i] != quote && text[i] != '\n') {
                i += if (text[i] == '\\') 2 else 1
            }
            return i + 1
        }
    }
}
