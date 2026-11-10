package com.coparently.app.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.coparently.app.R
import com.coparently.app.data.platform.AndroidDeviceRegion
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.time.LocalTime
import java.util.Locale

/**
 * Android's half of the owner's language rule, with the app's real string resources: the context
 * [inAppLanguage] hands a push, a notice, a worker or the widget reads the phone's first language
 * when the app ships it, English otherwise, and a per-app choice over both. The rule's own table
 * is `AppLanguageRuleTest` (the shared domain); this is the platform around it.
 *
 * The phone's language is set as Robolectric sets a device's (`qualifiers`), which reaches
 * `Resources.getSystem()` and, on Android 13 and later, the system locales `LocaleManager` answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class AppLocaleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var savedDefaults: LocaleList

    @Before
    fun saveDefaults() {
        savedDefaults = LocaleList.getDefault()
    }

    @After
    fun restoreDefaults() {
        LocaleList.setDefault(savedDefaults)
    }

    @Test
    @Config(qualifiers = "cs-rCZ")
    fun `a phone in Czech reads Czech`() {
        assertEquals(Locale.forLanguageTag("cs-CZ"), AppLocale.effective(context))
        assertEquals(text("cs", R.string.settings_title), context.inAppLanguage().getString(R.string.settings_title))
    }

    @Test
    @Config(qualifiers = "ga-rIE")
    fun `a phone in a language the app does not ship reads English`() {
        assertEquals(Locale.forLanguageTag("en-150"), AppLocale.effective(context))
        val words = context.inAppLanguage()
        assertEquals(text("en", R.string.settings_title), words.getString(R.string.settings_title))
        assertNotEquals(text("cs", R.string.settings_title), words.getString(R.string.settings_title))
    }

    @Test
    @Config(qualifiers = "sk-rSK")
    fun `the English fallback is European English, on a 24-hour clock`() {
        assertEquals(Locale.forLanguageTag("en-150"), AppLocale.effective(context))
        val words = context.inAppLanguage()
        assertEquals(Locale.forLanguageTag("en-150"), words.resources.configuration.locales[0])
        // The phone's clock left on its default ("use locale default"): European English's is
        // 24-hour, so pushes, notices, the widget and the activity all read 24 hours.
        assertTrue(DateFormat.is24HourFormat(words))
        AppLocale.follow(context)
        assertTrue(ClockFormat.localeUses24Hour(Locale.getDefault()))
        val time = shortTime(Locale.getDefault(), DateFormat.is24HourFormat(words))
        assertEquals("18:05", LocalTime.of(18, 5).format(time))

        // The phone's own setting still wins: a parent who chose a 12-hour clock keeps it.
        ShadowSettings.set24HourTimeFormat(false)
        assertFalse(DateFormat.is24HourFormat(context.inAppLanguage()))
    }

    @Test
    fun `only the phone's first language counts`() {
        // Irish then Czech: Android's own resolution would read Czech; the rule reads English.
        val irishThenCzech = LocaleList.forLanguageTags("ga-IE,cs-CZ")
        val english = AppLocale.contextIn(context, AppLocale.effective(null, irishThenCzech[0]))
        assertEquals(text("en", R.string.settings_title), english.getString(R.string.settings_title))

        val czechThenGerman = LocaleList.forLanguageTags("cs-CZ,de-DE")
        val czech = AppLocale.contextIn(context, AppLocale.effective(null, czechThenGerman[0]))
        assertEquals(text("cs", R.string.settings_title), czech.getString(R.string.settings_title))
    }

    @Test
    @Config(qualifiers = "cs-rCZ")
    fun `a per-app choice wins over the phone`() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de")

        assertEquals(Locale.GERMAN, AppLocale.effective(context))
        assertEquals(text("de", R.string.settings_title), context.inAppLanguage().getString(R.string.settings_title))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `an English phone reads English as it lists it`() {
        assertEquals(Locale.US, AppLocale.effective(context))
    }

    @Test
    @Config(qualifiers = "sk-rSK")
    fun `follow makes the process's default the language the app speaks`() {
        AppLocale.follow(context)

        assertEquals(Locale.forLanguageTag("en-150"), Locale.getDefault())
        assertEquals(LocaleList.forLanguageTags("en-150,sk-SK"), LocaleList.getDefault())
    }

    @Test
    @Config(qualifiers = "sk-rSK")
    fun `the phone's region is read from the phone, never from the language the app speaks`() {
        AppLocale.follow(context)

        // The currency hint and the onboarding's country guess: still Slovakia.
        assertEquals("SK", AndroidDeviceRegion(context).code())
    }

    @Test
    fun `the default list keeps the phone's other languages after the one the app speaks`() {
        // English for a phone in Japanese then Chinese: the co-parent's Han text keeps its glyphs.
        assertEquals(
            LocaleList.forLanguageTags("en-150,ja-JP,zh-TW"),
            defaultLocalesFor(Locale.forLanguageTag("en-150"), LocaleList.forLanguageTags("ja-JP,zh-TW"))
        )
        // The phone's own first language is not repeated.
        assertEquals(
            LocaleList.forLanguageTags("cs-CZ,de-DE"),
            defaultLocalesFor(Locale.forLanguageTag("cs-CZ"), LocaleList.forLanguageTags("cs-CZ,de-DE"))
        )
        // A per-app choice goes first, whatever the phone lists.
        assertEquals(
            LocaleList.forLanguageTags("de,sk-SK,cs-CZ"),
            defaultLocalesFor(Locale.GERMAN, LocaleList.forLanguageTags("sk-SK,cs-CZ"))
        )
    }

    /**
     * Below Android 13's path, on SDK 34 (the SDK every Robolectric test here runs, so CI fetches no
     * other): AppCompat answers nothing outside an activity, and the mirror answers instead.
     */
    @Test
    fun `below Android 13 a mirrored choice reaches a process no activity has started`() {
        val mirror = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        mirror.edit().putString("per_app_choice", "de").commit()
        val chosen = AppLocale.appCompatOrRememberedChoice(context)
        assertEquals(Locale.GERMAN, chosen)
        val words = AppLocale.contextIn(context, AppLocale.effective(chosen, Locale.forLanguageTag("cs-CZ")))
        assertEquals(text("de", R.string.settings_title), words.getString(R.string.settings_title))

        // An activity that finds no choice in AppCompat clears the mirror: the phone's language again.
        AppLocale.storeChoice(context)
        assertNull(AppLocale.appCompatOrRememberedChoice(context))
    }

    @Test
    @Config(qualifiers = "ga-rIE")
    fun `the configuration context changes the language and nothing else`() {
        val base = context.resources.configuration
        val words = context.inAppLanguage().resources.configuration
        assertEquals(base.uiMode, words.uiMode)
        assertEquals(base.fontScale, words.fontScale, 0f)
        assertEquals(base.densityDpi, words.densityDpi)
        assertEquals(Locale.forLanguageTag("en-150"), words.locales[0])
    }

    /** [id] as the app's resources hold it for [tag], whatever language the phone is in. */
    private fun text(tag: String, @StringRes id: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return context.createConfigurationContext(configuration).getString(id)
    }
}
