package com.coparently.app.e2e

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.testing.settle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * One way of looking at the app in the UI tour, named `<theme>-<language>-<font scale %>`:
 * `light-en-100`, `dark-en-100`, `light-ru-130`.
 *
 * The theme and the language are applied by the test ([applyTo]) — the app's own theme preference,
 * and AppCompat's per-app locale, exactly what Settings → Theme and Settings → Language write. The
 * font scale is a device setting, which `tools/ui-tour/run-ui-tour.sh` sets with
 * `settings put system font_scale` before the run; the name only records it.
 *
 * @property name The variant's name, also the directory its screenshots are written to.
 * @property dark Whether the app is switched to its dark theme.
 * @property language The BCP 47 tag of the app language.
 */
data class UiTourVariant(val name: String, val dark: Boolean, val language: String) {

    /** A context whose resources are in [language], for the tour's matchers to read the app's text. */
    fun localized(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    /** Switches the app to this variant's theme and language; call before `MainActivity` starts. */
    suspend fun applyTo(preferences: PreferencesRepository) {
        preferences.setDarkTheme(dark)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
    }

    companion object {
        /** The variant a run without [EmulatorEnvironment.UI_TOUR_VARIANT_ARGUMENT] takes. */
        const val DEFAULT = "light-en-100"

        private val NAME = Regex("[a-z0-9]+(-[a-z0-9]+)*")

        /** The variant this run was asked for. */
        fun current(): UiTourVariant {
            val name = InstrumentationRegistry.getArguments()
                .getString(EmulatorEnvironment.UI_TOUR_VARIANT_ARGUMENT)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT
            require(NAME.matches(name)) { "Not a UI tour variant name: $name" }
            val parts = name.split('-')
            return UiTourVariant(name = name, dark = parts.first() == "dark", language = parts.getOrElse(1) { "en" })
        }

        /** Puts the app back on the device's language, so nothing after the tour inherits the tour's. */
        fun resetLanguage() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }
}

/**
 * Takes the UI tour's screenshots and keeps its `manifest.json`.
 *
 * Each [shot] is a **full device screenshot** (`UiAutomation.takeScreenshot`): status and navigation
 * bars, dialogs, bottom sheets and the keyboard included, which a Compose node capture cannot see.
 * Files go to `<files dir>/ui-tour/<variant>/NN_<screen>.png`, the app's internal storage, which
 * `tools/ui-tour/run-ui-tour.sh` streams out with `run-as` (the debug build is debuggable). The
 * external app-specific directory was tried first: on API 30 neither `adb pull` nor `run-as` read it.
 *
 * **A screen that cannot be reached is skipped, never fatal.** [shot] runs its preparation, and any
 * failure — a node that never appeared, a tap that threw — is written to the manifest's `skipped`
 * list with its reason, after which [recover] returns the app to a known place and the tour goes on.
 * The manifest is rewritten after every screen, so a run that dies half-way still says how far it got.
 *
 * Numbers are handed out in call order and consumed by a skip too, so a given screen keeps its
 * number across the variants of one build; the gallery (`tools/ui-tour/gallery.js`) groups by the
 * name without the number anyway.
 *
 * @param rule The compose rule whose clock the tour drives (paused, as in every on-screen e2e test).
 * @param section The manifest section this camera writes — one per test class.
 * @param firstNumber The number the first screenshot takes; sections use separate ranges.
 * @param recover Brings the app back to a known screen after a failed [shot].
 */
class UiTourCamera(
    private val rule: ComposeTestRule,
    private val section: String,
    firstNumber: Int,
    private val recover: () -> Unit
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val variant = UiTourVariant.current()
    // The app's internal files dir, streamed out by `run-as` (tools/ui-tour/run-ui-tour.sh): on API 30
    // neither `adb pull` nor `run-as` could read the external app-specific directory.
    private val directory = File(context.filesDir, "ui-tour/${variant.name}").apply { mkdirs() }
    private var number = firstNumber - 1
    private val captured = JSONArray()
    private val skipped = JSONArray()

    /** Runs [prepare], lets the screen settle and saves it as `NN_[screen].png`; skips it on failure. */
    @Suppress("TooGenericExceptionCaught") // a tour goes on past any one screen, whatever broke it
    fun shot(screen: String, prepare: () -> Unit = {}) {
        number += 1
        val file = String.format(Locale.ROOT, "%02d_%s.png", number, screen)
        step("tour: $file")
        try {
            prepare()
            save(file)
            captured.put(JSONObject().put("file", file).put("screen", screen))
        } catch (e: Exception) {
            recordSkip(screen, e)
        } catch (e: AssertionError) {
            recordSkip(screen, e)
        }
        writeManifest()
    }

    /** Records [screen] as not captured, for a [reason] known without trying (e.g. no such feature). */
    fun skip(screen: String, reason: String) {
        number += 1
        skipped.put(JSONObject().put("screen", screen).put("reason", reason))
        writeManifest()
    }

    @Suppress("TooGenericExceptionCaught") // recovering is best effort; the next shot says if it failed
    private fun recordSkip(screen: String, cause: Throwable) {
        Log.w(TAG, "skipped $screen", cause)
        skipped.put(JSONObject().put("screen", screen).put("reason", cause.toString().take(MAX_REASON)))
        try {
            recover()
        } catch (e: Exception) {
            Log.w(TAG, "could not recover after $screen", e)
        } catch (e: AssertionError) {
            Log.w(TAG, "could not recover after $screen", e)
        }
    }

    private fun save(file: String) {
        rule.settle()
        rule.waitForIdle()
        // Compose has laid the frame out; the render thread and the window manager draw it in
        // real time, which the paused Compose clock does not move.
        SystemClock.sleep(RENDER_PAUSE_MS)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot()
            ?: SystemClock.sleep(RENDER_PAUSE_MS).let { automation.takeScreenshot() }
            ?: error("UiAutomation returned no screenshot")
        FileOutputStream(File(directory, file)).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
    }

    /** Merges this section into the variant's manifest; another test class of the run owns another section. */
    private fun writeManifest() {
        val file = File(directory, MANIFEST)
        val manifest = file.takeIf { it.exists() }?.let { JSONObject(it.readText()) } ?: JSONObject()
        val metrics = context.resources.displayMetrics
        manifest.put("variant", variant.name)
            .put("dark", variant.dark)
            .put("language", variant.language)
            .put("fontScale", context.resources.configuration.fontScale.toDouble())
            .put(
                "device",
                JSONObject()
                    .put("model", Build.MODEL)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("widthPx", metrics.widthPixels)
                    .put("heightPx", metrics.heightPixels)
                    .put("densityDpi", metrics.densityDpi)
            )
        val sections = manifest.optJSONObject("sections") ?: JSONObject()
        sections.put(section, JSONObject().put("captured", captured).put("skipped", skipped))
        manifest.put("sections", sections)
        file.writeText(manifest.toString(2))
    }

    private companion object {
        const val TAG = "UiTour"
        const val MANIFEST = "manifest.json"
        const val RENDER_PAUSE_MS = 600L
        const val PNG_QUALITY = 100
        const val MAX_REASON = 400
    }
}
