package com.coparently.app.presentation.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.R
import com.coparently.app.presentation.MainActivity
import com.coparently.app.testing.SignedInSession
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Switches the app's language the way Settings → Language does —
 * `AppCompatDelegate.setApplicationLocales` — and checks that Settings then renders in it, for
 * each of the four languages the app ships besides English.
 *
 * What this guards is the infrastructure, not the translations: `MainActivity` staying an
 * `AppCompatActivity`, `Theme.CoPlanly` staying an AppCompat theme, and composition reading the
 * Activity's configuration rather than the application's. Break any of those and the picker
 * silently stops working on the next launch (CLAUDE.md, Localization). The expected strings are
 * read from the app's own resources in that locale, so a reworded translation does not fail it —
 * only a screen that ignores the choice does.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PerAppLocaleTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var session: SignedInSession

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun signIn() {
        hiltRule.inject()
        session.signIn()
        composeTestRule.mainClock.autoAdvance = false
    }

    @After
    fun restore() {
        setAppLanguage(LocaleListCompat.getEmptyLocaleList())
        session.signOut()
    }

    @Test
    fun settings_rendersInEachShippedLanguage() {
        for (tag in listOf("cs", "de", "ru", "uk")) {
            val title = localized(tag, R.string.settings_title)
            val familyGroup = localized(tag, R.string.settings_group_family)
            val gear = localized(tag, R.string.nav_settings)
            assertNotEquals("$tag has its own Settings title", localized("en", R.string.settings_title), title)

            setAppLanguage(LocaleListCompat.forLanguageTags(tag))
            ActivityScenario.launch(MainActivity::class.java).use {
                composeTestRule.pumpUntil("Home in $tag") { exists(hasContentDescription(gear) and hasClickAction()) }
                composeTestRule.settle()
                composeTestRule.onNode(hasContentDescription(gear) and hasClickAction()).performClick()
                composeTestRule.pumpUntil("Settings titled \"$title\"") {
                    exists(hasText(title)) && exists(hasText(familyGroup, ignoreCase = true))
                }
            }
        }
    }

    private fun setAppLanguage(locales: LocaleListCompat) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** [id] as the app's resources hold it for [tag], whatever language the device is in. */
    private fun localized(tag: String, @StringRes id: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return context.createConfigurationContext(configuration).getString(id)
    }
}
