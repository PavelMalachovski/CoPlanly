package com.coparently.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.LocalParentPalette
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * The Android SDK the screenshot tests run on.
 *
 * Not the app's `compileSdk`/`targetSdk` 36: Robolectric 4.16 supports SDK 36 but runs it only on
 * JDK 21, because the SDK 36 `android-all` jar is Java 21 bytecode, and every CI job builds on
 * JDK 17. SDK 34 renders Compose identically for these purposes — nothing on screen here reads an
 * API level — and moving to 36 is this constant plus `java-version: '21'` in the `screenshots`
 * job, nothing else.
 */
const val SCREENSHOT_SDK = 34

/** Test tag on the frame around the component; the capture is cropped to it. */
private const val SCREENSHOT_TAG = "screenshot-frame"

/** Space around a component so its own edges and shadows are visible in the image. */
private val FRAME_PADDING = 16.dp

/**
 * Base class for the JVM screenshot tests (Roborazzi on Robolectric native graphics).
 *
 * A subclass is a parameterised Robolectric test over a [ScreenshotVariants] set; each `@Test`
 * calls [snap] once. The image lands at `<component>/<variant>.png` under Roborazzi's output
 * directory — `app/build/outputs/roborazzi` — which is the layout `tools/screenshot-gallery.js`
 * builds its index from.
 *
 * These tests run only under a Roborazzi task (`./gradlew recordRoborazziDebug`); an ordinary
 * `testDebugUnitTest` excludes the package, see `roborazziRequested` in `app/build.gradle.kts`.
 *
 * Subclasses carry their own `@RunWith(ParameterizedRobolectricTestRunner::class)`,
 * `@GraphicsMode(NATIVE)` and `@Config(sdk = [SCREENSHOT_SDK], application = Application::class)`.
 * The plain `Application` matters: the manifest names `CoPlanlyApplication`, whose Hilt graph
 * reaches Firebase, and nothing rendered here needs it.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
abstract class ScreenshotMatrix(private val variant: ScreenshotVariant) {

    private val composeRule = createComposeRule()

    /**
     * The environment rule wraps the compose rule, so the qualifiers and the default locale are in
     * place before the rule launches its activity — setting them from `@Before` would be too late,
     * because the activity is already created by then and keeps the configuration it started with.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(VariantEnvironmentRule(variant)).around(composeRule)

    /**
     * Renders [content] inside the app theme for this variant and records it.
     *
     * @param component Directory name for the component, e.g. `home_stat_tiles`
     * @param fullScreen Give the content the whole window rather than wrapping its height —
     *   for screens and anything that fills its parent
     * @param content The composable under test
     */
    protected fun snap(component: String, fullScreen: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            ScreenshotFrame(variant = variant, fullScreen = fullScreen, content = content)
        }
        composeRule.onNodeWithTag(SCREENSHOT_TAG).captureRoboImage("$component/${variant.fileName}.png")
    }
}

/**
 * The app theme, the family's palette and the font scale around one component, on the theme's
 * background so a dark image is dark to its edges.
 *
 * The font scale goes through [LocalDensity] rather than the resource configuration: Compose
 * converts every `sp` with `LocalDensity.current.fontScale`, so this is exactly what a system
 * setting of 1.5× does to the text, and it cannot be lost to a configuration the activity did
 * not pick up.
 */
@Composable
private fun ScreenshotFrame(
    variant: ScreenshotVariant,
    fullScreen: Boolean,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, variant.fontScale),
        LocalParentPalette provides variant.palette.palette
    ) {
        CoPlanlyTheme(darkTheme = variant.dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                val size = if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                Box(
                    modifier = size
                        .testTag(SCREENSHOT_TAG)
                        .padding(if (fullScreen) 0.dp else FRAME_PADDING)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Puts the variant's language and night mode into the Robolectric configuration and the JVM
 * defaults, and restores the JVM defaults afterwards.
 *
 * `Locale.getDefault()` is set as well as the resource qualifier because several composables
 * format dates with it directly (`DayAgendaCard`, the month grid's weekday header) — the
 * qualifier alone would give Czech strings around English day names. The time zone is pinned so
 * chat timestamps do not depend on the machine that recorded them.
 */
private class VariantEnvironmentRule(private val variant: ScreenshotVariant) : ExternalResource() {

    private var savedLocale: Locale? = null
    private var savedZone: TimeZone? = null

    override fun before() {
        savedLocale = Locale.getDefault()
        savedZone = TimeZone.getDefault()
        Locale.setDefault(Locale.forLanguageTag(variant.locale.tag))
        TimeZone.setDefault(TimeZone.getTimeZone(FIXED_ZONE))
        RuntimeEnvironment.setQualifiers(variant.qualifiers)
    }

    override fun after() {
        savedLocale?.let(Locale::setDefault)
        savedZone?.let(TimeZone::setDefault)
    }

    private companion object {
        const val FIXED_ZONE = "Europe/Prague"
    }
}
