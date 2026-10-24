package com.coparently.app.e2e

import android.content.Context
import android.util.Log
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
import com.coparently.app.domain.repository.FriendRepository
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
 * currencies under an agreed split, a half-agreed parenting plan, a chat with an attachment,
 * a vault document, and Bob's pending day swap and change request — and the tour then walks every
 * main screen through the app's own navigation, reading every label from the app's string resources
 * in the variant's language, and hands each one to [UiTourCamera]. What the family wrote — titles,
 * notes, the chat — is in that language too ([UiTourContent]), and the tour looks for it there. A
 * screen it cannot reach is written to the manifest as skipped, with the reason; nothing here fails
 * the test on its own.
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

    @Inject
    lateinit var friendRepository: FriendRepository

    private val variant by lazy { UiTourVariant.current() }
    private val localized by lazy { variant.localized(context) }
    private val content by lazy { UiTourContent.forLanguage(variant.language) }
    private val driver by lazy { UiTourDriver(composeTestRule, tabBar) }
    private val camera by lazy { UiTourCamera(composeTestRule, SECTION, FIRST_NUMBER) { driver.backToTabs() } }

    override val strings: Context
        get() = localized

    override fun assumeRunnable() = EmulatorEnvironment.assumeUiTour()

    override fun beforeLaunch() = runBlocking { variant.applyTo(preferencesRepository) }

    @Test
    fun tour() {
        step("tour: seed")
        runBlocking {
            seed.seedSettled(aliceUid, bob, content)
            runCatching { syncService.performFullSync() }
            seed.seedPending(aliceUid, bob, content)
        }
        section("home") { homeSection() }
        section("widget") { widgetSection() }
        section("calendar") { calendarSection() }
        section("event_form") { eventFormSection() }
        section("chat") { chatSection() }
        section("expenses") { expensesSection() }
        section("settings") { settingsSection() }
        section("family") { familyScreens() }
        section("records") { recordScreens() }
        section("switcher") { switcherSection() }
        section("friend_view") { friendViewSection() }
        section("sign_out") { signOutSection() }
    }

    /**
     * Walks one part of the tour. A step *between* two screens that throws — a Back that found no
     * focused window, a pop-up that did not close — used to end the whole tour, and the screens after
     * it were simply missing from the manifest, which read like a short tour rather than a broken
     * one. Now it is the section's skip, with the reason, the app goes back to the tabs, and the tour
     * goes on with the next section.
     */
    @Suppress("TooGenericExceptionCaught") // the tour goes on past any one section, whatever broke it
    private fun section(name: String, walk: () -> Unit) {
        try {
            walk()
        } catch (e: Exception) {
            abandon(name, e)
        } catch (e: AssertionError) {
            abandon(name, e)
        }
    }

    private fun abandon(name: String, cause: Throwable) {
        Log.w(TAG, "section $name ended early", cause)
        camera.skip("section_$name", "ended early: ${cause.toString().take(REASON_CHARS)}")
        runCatching { driver.backToTabs() }
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
            driver.appeared(hasText(content.events.dentist, substring = true))
            driver.linger()
        }
        // Alice's own request, waiting on Bob, with its deadline and the "opened" mark (phase 3a).
        camera.shot("requests_waiting_on_them") {
            val card = hasText(content.events.parentTeacherMeeting, substring = true)
            repeat(MAX_INBOX_SCROLLS) {
                if (!driver.appeared(card, CARD_WAIT_MS)) driver.scrollDown()
            }
            driver.await("Alice's request waiting on Bob", card)
            driver.linger()
            // The history shot that follows looks for the swap's Decline at the top of the inbox.
            driver.scrollToTop()
        }
        // Answered on screen rather than seeded, so the tour walks the answer path too. The swap's
        // card comes before the request's (InboxItems), so the first Decline is the swap's; once
        // its card has gone to History the only Accept left is the request's — in English the two
        // buttons share their labels.
        camera.shot("change_requests_history") {
            val swapWaiting = hasText(string(R.string.day_swap_inbox_waiting))
            driver.press("decline Bob's swap", textButton(R.string.day_swap_decline))
            driver.awaitGone("the swap's card", swapWaiting, CROSS_DEVICE_TIMEOUT_MS)
            driver.press("accept Bob's request", textButton(R.string.change_request_accept))
            val requestDecline = textButton(R.string.change_request_decline)
            driver.awaitGone("the request's buttons", requestDecline, CROSS_DEVICE_TIMEOUT_MS)
            driver.scrollToEnd()
            driver.await("History", hasContentDescription(string(R.string.request_section_history)))
            driver.linger()
        }
        driver.backToTabs()
        repeat(MAX_DIALOGS) { driver.pressIfPresent(dialogButton(R.string.home_dialog_later)) }
        camera.shot("home_top") { driver.linger() }
        camera.shot("home_scrolled") { check(driver.scrollDown()) { "Home does not scroll" } }
        camera.shot("home_bottom") { driver.scrollToEnd() }
        camera.shot("event_preview_sheet") {
            driver.scrollToTop()
            driver.press("today's pickup", hasText(content.events.schoolPickup, substring = true) and hasClickAction())
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
            driver.type("the title field", hasSetTextAction(), content.events.parentTeacherMeeting)
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
            driver.await("the thread", hasText(content.chat.lastLine, substring = true), CROSS_DEVICE_TIMEOUT_MS)
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
            val searchField = if (driver.present(field)) field else hasSetTextAction()
            driver.type("the search field", searchField, content.chat.search)
            driver.closeKeyboard()
            driver.linger()
        }
        driver.backToTabs()
    }

    // ---- Expenses -------------------------------------------------------------------------------

    private fun expensesSection() {
        // The Family tab replaced the Expenses tab (new-user review, October 2026): the tab first,
        // then Expenses from its money card. The shot names are kept for the Play listing.
        camera.shot("family") {
            driver.backToTabs()
            openTab(BottomNavDestination.FAMILY)
            driver.linger()
        }
        scrolls("family", FAMILY_SCREENFULS)
        camera.shot("expenses_list") {
            openFamilyRow(R.string.family_money_this_month, substring = true)
            driver.appeared(hasText(content.expenses.jacket, substring = true))
            driver.linger()
        }
        camera.shot("expenses_scrolled") { check(driver.scrollDown()) { "Expenses does not scroll" } }
        camera.shot("expenses_analytics") {
            driver.press("Analytics", textButton(R.string.expense_analytics_tab_analytics))
            driver.linger()
        }
        driver.pressIfPresent(textButton(R.string.expense_analytics_tab_list))
        camera.shot("expense_add") {
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
        fromFamily("child_info_list", R.string.settings_child_info_title, UiTourSeed.EMMA)
        camera.shot("child_detail") {
            driver.press("Emma", hasText(UiTourSeed.EMMA, substring = true) and hasClickAction())
            driver.linger()
        }
        scrolls("child_detail", 2)
        fromFamily("pets_list", R.string.settings_pets_title, UiTourSeed.MAX)
        camera.shot("pet_edit") {
            driver.press("Max", hasText(UiTourSeed.MAX, substring = true) and hasClickAction())
            driver.linger()
        }
        fromFamily("custody_setup", R.string.settings_custody_title)
        scrolls("custody_setup", CUSTODY_SCREENFULS)
        fromFamily("parenting_plan", R.string.parenting_plan_title)
        scrolls("parenting_plan", 2)
    }

    private fun recordScreens() {
        fromFamily("pairing", R.string.settings_pairing_title)
        fromFamily("friends", R.string.friend_section_title)
        // The sheet's button repeats the invite row's label, and the row stays behind the sheet.
        camera.shot("friends_invite_sheet") {
            driver.press("Invite a family friend", row(R.string.friend_invite_action))
            driver.await("the invite sheet", hasText(string(R.string.friend_invite_will_see_lead), substring = true))
            driver.pressLast("the sheet's Invite", textButton(R.string.friend_invite_action))
            driver.await("the friend's code", textButton(R.string.friend_invite_share), CROSS_DEVICE_TIMEOUT_MS)
        }
        fromFamily("professionals", R.string.professional_section_title)
        fromFamily("export", R.string.export_settings_title)
        scrolls("export", 1)
        fromFamily("documents_vault", R.string.documents_settings_title, content.document.title)
        fromFamily("journal", R.string.journal_title)
        fromFamily("my_stickers", R.string.stickers_title)
        fromSettings("calendar_feed", R.string.calendar_feed_settings_title)
        // The school import (MON-8): the list with nobody connected, then the connect flow's
        // first step. The tour signs in to no school.
        fromSettings("school_import", R.string.settings_school_import_title)
        camera.shot("school_connect") {
            driver.press("school_connect", hasText(string(R.string.school_import_connect)) and hasClickAction())
            driver.linger()
        }
        fromFamily("my_profile", R.string.settings_my_profile_title)
        fromFamily("coparent_profile", R.string.settings_coparent_profile_title)
        fromSettings("data_sources", R.string.settings_data_sources_title)
        driver.backToTabs()
    }

    /** A screen opened from its Family tab row; [content] is a text worth waiting a moment for. */
    private fun fromFamily(name: String, rowTitle: Int, content: String? = null) {
        camera.shot(name) {
            openFamilyRow(rowTitle)
            content?.let { driver.appeared(hasText(it, substring = true)) }
            driver.linger()
        }
    }

    /** Opens the Family tab and presses the row titled [rowTitle] (a part of its text with [substring]). */
    private fun openFamilyRow(rowTitle: Int, substring: Boolean = false) {
        driver.backToTabs()
        // Through the item's click action, as every row is pressed: right after a Back the item is
        // in the tree but a touch injected at its bounds is refused (the tour lost seven screens).
        driver.press(
            "the Family tab",
            hasAnyAncestor(tabBar) and hasText(string(BottomNavDestination.FAMILY.labelRes)) and hasClickAction()
        )
        driver.press(string(rowTitle), hasText(string(rowTitle), substring = substring) and hasClickAction())
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
        if (driver.backUntil(settings or tabBar) && driver.present(settings)) return
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

    /**
     * Alice as a family friend of another family (phase 2): Dave and Erin pair, Dave invites her and
     * adds an event she takes part in, and she redeems the code — so her Friends screen lists that
     * family's calendar, which opens on Dave's event. Last before signing out: a grant switches
     * Alice's Friends screen to the friend's branch for the rest of the run.
     */
    private fun friendViewSection() {
        val joined = runCatching {
            runBlocking {
                val dave = newParent("Dave")
                val erin = newParent("Erin")
                val pairing = dave.pairingRepository.createOrReuseInviteCode().getOrThrow()
                erin.pairingRepository.redeem(pairing.code).getOrThrow()
                erin.awaitPairedWith(dave.uid)
                dave.awaitPairedWith(erin.uid)
                val invite = dave.friendRepository.inviteFriend(System.currentTimeMillis() + FRIEND_ACCESS_MS)
                    .getOrThrow()
                friendRepository.acceptFriendInvite(invite.code).getOrThrow()
                seed.friendFamilyEvent(dave, aliceUid, content.events.birthdayParty)
            }
        }
        val familyRow = row(R.string.friend_calendar_title)
        camera.shot("friend_calendar_list") {
            joined.getOrThrow()
            openFamilyRow(R.string.friend_section_title)
            driver.await("the friend's family row", familyRow, CROSS_DEVICE_TIMEOUT_MS)
        }
        camera.shot("friend_calendar") {
            driver.press("the family calendar", familyRow)
            driver.appeared(hasText(content.events.birthdayParty, substring = true))
            driver.linger()
        }
        driver.backToTabs()
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
        private const val TAG = "UiTour"
        private const val REASON_CHARS = 400
        private const val FIRST_NUMBER = 1
        private const val MAX_DIALOGS = 4
        private const val MAX_INBOX_SCROLLS = 4
        private const val CARD_WAIT_MS = 1_000L
        private const val SETTINGS_SCREENFULS = 2

        /** The Family tab's screenfuls below its first. */
        private const val FAMILY_SCREENFULS = 2
        private const val CUSTODY_SCREENFULS = 3
        private const val KEYBOARD_MS = 1_500L

        /** How long Dave admits Alice as a family friend: the invite's longest choice. */
        private const val FRIEND_ACCESS_MS = 90L * 24 * 60 * 60 * 1000

        /** Puts the app back on the device's language once the tour is over. */
        @JvmStatic
        @AfterClass
        fun resetLanguage() {
            if (EmulatorEnvironment.uiTour && EmulatorEnvironment.host != null) UiTourVariant.resetLanguage()
        }
    }
}
