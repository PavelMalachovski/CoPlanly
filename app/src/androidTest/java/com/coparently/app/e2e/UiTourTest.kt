package com.coparently.app.e2e

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * **The UI tour**: full-screen screenshots of Alice's real app, with a realistic family in it, for a
 * design review that has no phone — produced by `.github/workflows/ui-tour.yml` and committed to a
 * `ui-tour/<branch>` branch that a reviewer can `git fetch`.
 *
 * It is an on-screen e2e test ([AliceOnScreenTest]: Alice signed up on the Auth emulator and paired
 * with Bob through the real callable, `MainActivity` on the paused Compose clock) that asserts
 * nothing. [UiTourSeed] fills both phones with a few weeks' worth of family — custody with contact
 * afternoons and an autumn break, two children and a dog, a dozen events, a month of expenses in two
 * currencies under an agreed split, budgets, a half-agreed parenting plan, a chat with an attachment,
 * a vault document, and Bob's pending day swap and change request — and the tour then walks every
 * main screen through the app's own navigation, reading every label from the app's string resources
 * in the variant's language, and hands each one to [UiTourCamera]. A screen it cannot reach is
 * written to the manifest as skipped, with the reason; nothing here fails the test on its own.
 *
 * **It runs only when asked for** — the emulator host *and* `-e coplanlyUiTour true`
 * ([EmulatorEnvironment.assumeUiTour]) — and `tools/e2e/run-two-parent-tests.sh` excludes it by name
 * from the `e2e` job. The variant (`-e coplanlyUiTourVariant light-ru-130`) picks the theme and the
 * language here, and names the output directory; the font scale is set on the device by
 * `tools/ui-tour/run-ui-tour.sh`. First-run screens are [UiTourOnboardingTest]'s.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UiTourTest : AliceOnScreenTest() {

    @Inject
    lateinit var seed: UiTourSeed

    @Inject
    lateinit var selectedFamilySource: SelectedFamilySource

    private val variant by lazy { UiTourVariant.current() }
    private val localized by lazy { variant.localized(context) }
    private val driver by lazy { UiTourDriver(composeTestRule, bottomBar) }
    private val camera by lazy { UiTourCamera(composeTestRule, SECTION, FIRST_NUMBER) { driver.backToTabs() } }

    override val strings: Context
        get() = localized

    override fun assumeRunnable() = EmulatorEnvironment.assumeUiTour()

    override fun beforeLaunch() = runBlocking { variant.applyTo(preferencesRepository) }

    @Test
    fun tour() {
        step("tour: seed")
        runBlocking {
            seed.seedSettled(aliceUid, bob)
            runCatching { syncService.performFullSync() }
            seed.seedPending(aliceUid, bob)
        }
        homeSection()
        widgetSection()
        calendarSection()
        eventFormSection()
        chatSection()
        expensesSection()
        settingsSection()
        familyScreens()
        recordScreens()
        switcherSection()
        signOutSection()
    }

    // ---- Home -----------------------------------------------------------------------------------

    /** Home as the app opens on it: Bob's swap and request pop up first, then the dashboard. */
    private fun homeSection() {
        camera.shot("home_dialog_swap") {
            driver.await("Bob's swap on Home", inDialog(R.string.home_dialog_swap_title), CROSS_DEVICE_TIMEOUT_MS)
        }
        driver.pressIfPresent(dialogButton(R.string.home_dialog_later))
        camera.shot("home_dialog_requests") {
            driver.await("the requests pop-up", inDialog(R.string.home_dialog_requests_title), CROSS_DEVICE_TIMEOUT_MS)
        }
        camera.shot("change_requests_inbox") {
            driver.press("Review", dialogButton(R.string.home_dialog_review))
            driver.appeared(hasText(UiTourSeed.DENTIST, substring = true))
            driver.linger()
        }
        driver.backToTabs()
        repeat(MAX_DIALOGS) { driver.pressIfPresent(dialogButton(R.string.home_dialog_later)) }
        camera.shot("home_top") { driver.linger() }
        camera.shot("home_scrolled") { check(driver.scrollDown()) { "Home does not scroll" } }
        camera.shot("home_bottom") { driver.scrollToEnd() }
        camera.shot("event_preview_sheet") {
            driver.scrollToTop()
            driver.press("today's pickup", hasText(UiTourSeed.SCHOOL_PICKUP, substring = true) and hasClickAction())
            driver.await("the preview's Edit", textButton(R.string.event_preview_edit))
        }
        camera.shot("event_edit_form") {
            driver.press("Edit", textButton(R.string.event_preview_edit))
            driver.await("the event form", hasSetTextAction())
            driver.linger()
        }
        driver.backToTabs()
    }

    // ---- The Today widget -----------------------------------------------------------------------

    /**
     * The home-screen widget, drawn from what Home has just shown: the same Room rows, and the names
     * the app remembered for it while Home was on screen.
     */
    private fun widgetSection() {
        camera.picture("widget_today_compact") { UiTourWidget.render(variant, UiTourWidget.COMPACT) }
        camera.picture("widget_today_tall") { UiTourWidget.render(variant, UiTourWidget.TALL) }
    }

    // ---- Calendar -------------------------------------------------------------------------------

    private fun calendarSection() {
        camera.shot("calendar_month") {
            driver.backToTabs()
            openTab(BottomNavDestination.CALENDAR)
            driver.linger()
        }
        camera.shot("calendar_week") { viewMode(R.string.calendar_viewmode_week) }
        camera.shot("calendar_day") { viewMode(R.string.calendar_viewmode_day) }
        camera.shot("calendar_filters") {
            viewMode(R.string.calendar_viewmode_month)
            driver.press("Filters", textButton(R.string.calendar_filters_button))
            driver.linger()
        }
        driver.back()
        camera.shot("calendar_next_month") {
            driver.backToTabs()
            openTab(BottomNavDestination.CALENDAR)
            check(driver.pageForward()) { "the month grid does not page" }
            driver.linger()
        }
    }

    /** Switches the calendar through its title menu, which is also the Month/Week/Day picker. */
    private fun viewMode(label: Int) {
        val menu = hasContentDescription(string(R.string.calendar_change_view_mode)) and hasClickAction()
        driver.press("the view-mode menu", menu)
        driver.press("the mode", textButton(label))
        driver.linger()
    }

    private fun eventFormSection() {
        camera.shot("event_add_empty") {
            driver.backToTabs()
            openTab(BottomNavDestination.CALENDAR)
            driver.press("Add event", hasContentDescription(string(R.string.calendar_add_event)) and hasClickAction())
            driver.await("the event form", hasSetTextAction())
        }
        camera.shot("event_add_keyboard") {
            driver.type("the title field", hasSetTextAction(), "Parent-teacher meeting")
            driver.linger(KEYBOARD_MS)
        }
        camera.shot("event_add_scrolled") {
            driver.closeKeyboard()
            check(driver.scrollDown()) { "the event form does not scroll" }
        }
        driver.backToTabs()
    }

    // ---- Chat -----------------------------------------------------------------------------------

    private fun chatSection() {
        camera.shot("chat_thread") {
            driver.backToTabs()
            openTab(BottomNavDestination.CHAT)
            driver.await("the thread", hasText("see you there", substring = true), CROSS_DEVICE_TIMEOUT_MS)
            driver.linger()
        }
        camera.shot("chat_composer_keyboard") {
            driver.type("the composer", hasSetTextAction(), null)
            driver.linger(KEYBOARD_MS)
        }
        driver.closeKeyboard()
        camera.shot("chat_search") {
            driver.press("Search", hasContentDescription(string(R.string.chat_search)) and hasClickAction())
            val field = hasSetTextAction() and hasText(string(R.string.chat_search_placeholder), substring = true)
            driver.type("the search field", if (driver.present(field)) field else hasSetTextAction(), "pickup")
            driver.closeKeyboard()
            driver.linger()
        }
        driver.backToTabs()
    }

    // ---- Expenses -------------------------------------------------------------------------------

    private fun expensesSection() {
        camera.shot("expenses_list") {
            driver.backToTabs()
            openTab(BottomNavDestination.EXPENSES)
            driver.appeared(hasText("Winter jacket", substring = true))
            driver.linger()
        }
        camera.shot("expenses_scrolled") { check(driver.scrollDown()) { "Expenses does not scroll" } }
        camera.shot("expenses_analytics") {
            driver.press("Analytics", textButton(R.string.expense_analytics_tab_analytics))
            driver.linger()
        }
        driver.pressIfPresent(textButton(R.string.expense_analytics_tab_list))
        camera.shot("budgets") {
            driver.press("+ Budget", textButton(R.string.expenses_budget_add))
            driver.linger()
        }
        camera.shot("expense_add") {
            driver.backToTabs()
            driver.press("Add expense", hasContentDescription(string(R.string.expenses_add)) and hasClickAction())
            driver.await("the expense form", hasSetTextAction())
            driver.linger()
        }
        driver.backToTabs()
    }

    // ---- Settings and what opens from it ---------------------------------------------------------

    private fun settingsSection() {
        camera.shot("settings_top") { openSettings() }
        repeat(SETTINGS_SCREENFULS) { index ->
            val name = "settings_scrolled_${index + 1}"
            if (driver.scrollDown()) camera.shot(name) else camera.skip(name, "Settings ends before this")
        }
        camera.shot("settings_theme_picker") {
            openSettings()
            driver.press("Theme", row(R.string.settings_theme))
        }
        driver.dismissDialog()
        camera.shot("settings_language_picker") {
            openSettings()
            driver.press("Language", row(R.string.settings_language_title))
        }
        driver.dismissDialog()
    }

    private fun familyScreens() {
        fromSettings("child_info_list", R.string.settings_child_info_title, UiTourSeed.EMMA)
        camera.shot("child_detail") {
            driver.press("Emma", hasText(UiTourSeed.EMMA, substring = true) and hasClickAction())
            driver.linger()
        }
        scrolls("child_detail", 2)
        fromSettings("pets_list", R.string.settings_pets_title, UiTourSeed.MAX)
        camera.shot("pet_edit") {
            driver.press("Max", hasText(UiTourSeed.MAX, substring = true) and hasClickAction())
            driver.linger()
        }
        fromSettings("custody_setup", R.string.settings_custody_title)
        scrolls("custody_setup", CUSTODY_SCREENFULS)
        fromSettings("parenting_plan", R.string.parenting_plan_title)
        scrolls("parenting_plan", 2)
    }

    private fun recordScreens() {
        fromSettings("pairing", R.string.settings_pairing_title)
        fromSettings("friends", R.string.friend_section_title)
        fromSettings("professionals", R.string.professional_section_title)
        fromSettings("export", R.string.export_settings_title)
        scrolls("export", 1)
        fromSettings("documents_vault", R.string.documents_settings_title, "Custody agreement")
        fromSettings("journal", R.string.journal_title)
        fromSettings("calendar_feed", R.string.calendar_feed_settings_title)
        fromSettings("my_profile", R.string.settings_my_profile_title)
        fromSettings("coparent_profile", R.string.settings_coparent_profile_title)
        fromSettings("data_sources", R.string.settings_data_sources_title)
        driver.backToTabs()
    }

    /** A screen opened from its Settings row; [content] is a text worth waiting a moment for. */
    private fun fromSettings(name: String, rowTitle: Int, content: String? = null) {
        camera.shot(name) {
            openSettings()
            driver.press(name, row(rowTitle))
            content?.let { driver.appeared(hasText(it, substring = true)) }
            driver.linger()
        }
    }

    /** Up to [count] further screenfuls of the screen on display, each its own screenshot. */
    private fun scrolls(name: String, count: Int) {
        repeat(count) { index ->
            val shot = "${name}_scrolled_${index + 1}"
            if (driver.scrollDown()) camera.shot(shot) else camera.skip(shot, "the screen ends before this")
        }
    }

    /** Settings, from wherever the tour is: Back to it if it is under this screen, else its gear. */
    private fun openSettings() {
        val settings = hasText(string(R.string.settings_title))
        if (driver.backUntil(settings or bottomBar) && driver.present(settings)) return
        val gear = (
            hasContentDescription(string(R.string.nav_settings)) or
                hasContentDescription(string(R.string.calendar_settings))
            ) and hasClickAction()
        driver.press("the gear", gear)
        driver.await("Settings", settings)
    }

    // ---- A second family, and signing out --------------------------------------------------------

    /** Carol pairs with Alice too, which puts the family switcher on Home — `OnScreenFamiliesTest`'s setup. */
    private fun switcherSection() {
        val paired = runCatching {
            runBlocking {
                val carol = newParent("Carol")
                val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
                carol.pairingRepository.redeem(invite.code).getOrThrow()
                carol.awaitPairedWith(aliceUid)
                syncService.performFullSync().getOrThrow()
                selectedFamilySource.select(FamilyKey.of(aliceUid, bob.uid))
            }
        }
        val switcher = hasContentDescription(string(R.string.settings_family_switch), substring = true) and
            hasClickAction()
        camera.shot("home_with_family_switcher") {
            paired.getOrThrow()
            driver.backToTabs()
            openTab(BottomNavDestination.HOME)
            repeat(MAX_DIALOGS) { driver.pressIfPresent(dialogButton(R.string.home_dialog_later)) }
            driver.await("the switcher chip", switcher, CROSS_DEVICE_TIMEOUT_MS)
        }
        camera.shot("family_switcher_dialog") {
            driver.press("the switcher", switcher)
            driver.await("the switcher dialog", isDialog())
        }
        driver.dismissDialog()
    }

    private fun signOutSection() {
        camera.shot("sign_out_confirm") {
            openSettings()
            driver.press("Sign out", row(R.string.settings_account_sign_out))
            driver.await("the confirmation", isDialog())
        }
        camera.shot("auth_sign_in") {
            driver.press("Sign out", dialogButton(R.string.settings_account_sign_out))
            driver.await("the sign-in screen", hasText(string(R.string.auth_tagline)), CROSS_DEVICE_TIMEOUT_MS)
            driver.linger()
        }
        camera.shot("auth_sign_up") {
            driver.press("Sign up", textButton(R.string.auth_action_sign_up))
            driver.linger()
        }
    }

    // ---- matchers -------------------------------------------------------------------------------

    private fun inDialog(text: Int): SemanticsMatcher = hasAnyAncestor(isDialog()) and hasText(string(text))

    private fun textButton(label: Int): SemanticsMatcher = hasText(string(label)) and hasClickAction()

    private fun row(title: Int): SemanticsMatcher = hasText(string(title)) and hasClickAction()

    companion object {
        private const val SECTION = "main"
        private const val FIRST_NUMBER = 1
        private const val MAX_DIALOGS = 4
        private const val SETTINGS_SCREENFULS = 4
        private const val CUSTODY_SCREENFULS = 3
        private const val KEYBOARD_MS = 1_500L

        /** Puts the app back on the device's language once the tour is over. */
        @JvmStatic
        @AfterClass
        fun resetLanguage() {
            if (EmulatorEnvironment.uiTour && EmulatorEnvironment.host != null) UiTourVariant.resetLanguage()
        }
    }
}
