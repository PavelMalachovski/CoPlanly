package com.coparently.app.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.R
import com.coparently.app.presentation.MainActivity
import com.coparently.app.testing.SignedInSession
import com.coparently.app.testing.assertIconOnlyControlsAreAccessible
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Opens the real app as a signed-in parent and walks every top-level screen: the four tabs, the
 * calendar's three views, and Settings.
 *
 * It proves what no JVM test can: that the whole Hilt graph, the navigation graph and each main
 * screen's first composition hold together on a device without crashing — with Firebase mocked
 * (`FakeFirebaseModule`), so every screen is drawn from Room alone, the way a parent offline sees
 * it — and that the bottom bar obeys UX item 1: shown on the four tabs, hidden on Settings, which
 * is a detail screen.
 *
 * The second test runs a basic accessibility sweep on the same screens; see
 * [assertIconOnlyControlsAreAccessible] for what it checks and why not the full ATF suite.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainNavigationSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var session: SignedInSession

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bottomBar = hasTestTag(BOTTOM_BAR_TEST_TAG)
    private val settingsButton = hasContentDescription(string(R.string.nav_settings)) and hasClickAction()
    private val viewModeTitle = hasContentDescription(string(R.string.calendar_change_view_mode))

    @Before
    fun launchSignedIn() {
        hiltRule.inject()
        session.signIn()
        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.pumpUntil("Home with the bottom bar") { exists(settingsButton) && exists(bottomBar) }
        composeTestRule.settle()
    }

    @After
    fun tearDown() {
        scenario.close()
        session.signOut()
    }

    @Test
    fun everyTopLevelScreen_opens_andTheBottomBarShowsOnlyOnTabs() {
        tab(BottomNavDestination.HOME).assertIsSelected()
        composeTestRule.onNode(bottomBar).assertIsDisplayed()

        for (destination in listOf(
            BottomNavDestination.CALENDAR,
            BottomNavDestination.CHAT,
            BottomNavDestination.EXPENSES,
            BottomNavDestination.HOME
        )) {
            openTab(destination)
            tab(destination).assertIsSelected()
            composeTestRule.onNode(bottomBar).assertIsDisplayed()
        }

        openTab(BottomNavDestination.CALENDAR)
        val views = listOf(
            R.string.calendar_viewmode_week,
            R.string.calendar_viewmode_day,
            R.string.calendar_viewmode_month
        )
        for (mode in views) {
            chooseCalendarView(mode)
            composeTestRule.onNode(viewModeTitle).assertIsDisplayed()
            composeTestRule.onNode(bottomBar).assertIsDisplayed()
        }

        openSettingsFromHome()
        assertFalse("the bottom bar is hidden on Settings", composeTestRule.exists(bottomBar))

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.pumpUntil("Home again after Back") { exists(bottomBar) && exists(settingsButton) }
        tab(BottomNavDestination.HOME).assertIsSelected()
    }

    @Test
    fun mainScreens_iconOnlyControls_haveALabelAndA48dpTarget() {
        composeTestRule.assertIconOnlyControlsAreAccessible("Home")
        openTab(BottomNavDestination.CALENDAR)
        composeTestRule.assertIconOnlyControlsAreAccessible("Calendar (month)")
        openTab(BottomNavDestination.CHAT)
        composeTestRule.assertIconOnlyControlsAreAccessible("Chat")
        openTab(BottomNavDestination.EXPENSES)
        composeTestRule.assertIconOnlyControlsAreAccessible("Expenses")
        openSettingsFromHome()
        composeTestRule.assertIconOnlyControlsAreAccessible("Settings")
    }

    private fun tab(destination: BottomNavDestination) =
        composeTestRule.onNode(hasAnyAncestor(bottomBar) and hasText(string(destination.labelRes)) and hasClickAction())

    private fun openTab(destination: BottomNavDestination) {
        tab(destination).performClick()
        composeTestRule.settle()
        composeTestRule.pumpUntil("the ${destination.name} tab to be selected") {
            onNode(hasAnyAncestor(bottomBar) and hasText(string(destination.labelRes)))
                .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Selected) { null } == true
        }
    }

    private fun chooseCalendarView(@StringRes mode: Int) {
        composeTestRule.onNode(viewModeTitle).performClick()
        composeTestRule.settle()
        composeTestRule.onAllNodes(hasText(string(mode)) and hasClickAction() and isMenuItem()).onFirst().performClick()
        composeTestRule.settle()
    }

    private fun openSettingsFromHome() {
        openTab(BottomNavDestination.HOME)
        composeTestRule.onNode(settingsButton).performClick()
        composeTestRule.pumpUntil("Settings") { exists(hasText(string(R.string.settings_title))) }
        composeTestRule.settle()
    }

    /** A dropdown entry, as opposed to the same word elsewhere on screen: it sits outside the bar. */
    private fun isMenuItem(): SemanticsMatcher = !hasAnyAncestor(bottomBar)

    private fun string(@StringRes id: Int): String = context.getString(id)
}
