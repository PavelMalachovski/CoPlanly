package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.coparently.app.domain.contacts.ContactGroup
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.User
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.changerequests.ChangeRequestsScreen
import com.coparently.app.presentation.changerequests.RequestsState
import com.coparently.app.presentation.childinfo.ChildDetailScreen
import com.coparently.app.presentation.childinfo.ChildInfoScreen
import com.coparently.app.presentation.childinfo.ChildInfoUiState
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.childinfo.GuestInviteState
import com.coparently.app.presentation.childinfo.MedicalPhotoError
import com.coparently.app.presentation.contacts.ContactsScreen
import com.coparently.app.presentation.contacts.ContactsViewModel
import com.coparently.app.presentation.custody.ChildScope
import com.coparently.app.presentation.custody.CustodySetupScreen
import com.coparently.app.presentation.custody.CustodySetupUiState
import com.coparently.app.presentation.custody.CustodySetupViewModel
import com.coparently.app.presentation.custody.SeasonalLayersUiState
import com.coparently.app.presentation.custody.SeasonalScheduleViewModel
import com.coparently.app.presentation.documents.DocumentsUiState
import com.coparently.app.presentation.documents.FamilyDocumentsScreen
import com.coparently.app.presentation.documents.FamilyDocumentsViewModel
import com.coparently.app.presentation.export.ExportScreen
import com.coparently.app.presentation.export.ExportUiState
import com.coparently.app.presentation.export.ExportViewModel
import com.coparently.app.presentation.family.EarlierFamiliesScreen
import com.coparently.app.presentation.family.EarlierFamiliesViewModel
import com.coparently.app.presentation.family.EarlierFamily
import com.coparently.app.presentation.family.EarlierFamilyScreen
import com.coparently.app.presentation.family.UnassignedNotice
import com.coparently.app.presentation.family.UnassignedRecordsScreen
import com.coparently.app.presentation.family.UnassignedRecordsUiState
import com.coparently.app.presentation.family.UnassignedRecordsViewModel
import com.coparently.app.presentation.friends.FriendCalendarScreen
import com.coparently.app.presentation.friends.FriendCalendarUiState
import com.coparently.app.presentation.friends.FriendCalendarViewModel
import com.coparently.app.presentation.friends.FriendDetailScreen
import com.coparently.app.presentation.friends.FriendInviteState
import com.coparently.app.presentation.friends.FriendProfileScreen
import com.coparently.app.presentation.friends.FriendRedeemState
import com.coparently.app.presentation.friends.FriendViewModel
import com.coparently.app.presentation.friends.FriendsScreen
import com.coparently.app.presentation.guests.GuestAcceptScreen
import com.coparently.app.presentation.guests.GuestAcceptUiState
import com.coparently.app.presentation.guests.GuestAcceptViewModel
import com.coparently.app.presentation.journal.JournalListScreen
import com.coparently.app.presentation.journal.JournalListState
import com.coparently.app.presentation.journal.JournalListViewModel
import com.coparently.app.presentation.pairing.CustodyConflict
import com.coparently.app.presentation.pairing.CustodyConflictPrompt
import com.coparently.app.presentation.pairing.CustodyConflictScreen
import com.coparently.app.presentation.pairing.CustodyConflictViewModel
import com.coparently.app.presentation.pairing.PairingFormState
import com.coparently.app.presentation.pairing.PairingScreen
import com.coparently.app.presentation.pairing.PairingViewModel
import com.coparently.app.presentation.parentingplan.ParentingPlanScreen
import com.coparently.app.presentation.parentingplan.ParentingPlanUiState
import com.coparently.app.presentation.parentingplan.ParentingPlanViewModel
import com.coparently.app.presentation.pets.PetsScreen
import com.coparently.app.presentation.pets.PetsUiState
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.professionals.ProfessionalCalendarScreen
import com.coparently.app.presentation.professionals.ProfessionalCalendarUiState
import com.coparently.app.presentation.professionals.ProfessionalCalendarViewModel
import com.coparently.app.presentation.professionals.ProfessionalInviteState
import com.coparently.app.presentation.professionals.ProfessionalPlanScreen
import com.coparently.app.presentation.professionals.ProfessionalPlanUiState
import com.coparently.app.presentation.professionals.ProfessionalPlanViewModel
import com.coparently.app.presentation.professionals.ProfessionalRedeemState
import com.coparently.app.presentation.professionals.ProfessionalsScreen
import com.coparently.app.presentation.professionals.ProfessionalsViewModel
import com.coparently.app.presentation.profile.ProfileScreen
import com.coparently.app.presentation.profile.ProfileUiState
import com.coparently.app.presentation.profile.ProfileViewModel
import com.coparently.app.presentation.review.MonthReviewScreen
import com.coparently.app.presentation.review.MonthReviewUiState
import com.coparently.app.presentation.review.MonthReviewViewModel
import com.coparently.app.presentation.school.ConnectSchoolScreen
import com.coparently.app.presentation.school.ConnectSchoolUiState
import com.coparently.app.presentation.school.ConnectSchoolViewModel
import com.coparently.app.presentation.school.ConnectStep
import com.coparently.app.presentation.school.SchoolImportScreen
import com.coparently.app.presentation.school.SchoolImportUiState
import com.coparently.app.presentation.school.SchoolImportViewModel
import com.coparently.app.presentation.settings.CalendarFeedScreen
import com.coparently.app.presentation.settings.CalendarFeedUiState
import com.coparently.app.presentation.settings.CalendarFeedViewModel
import com.coparently.app.presentation.settings.DataSourcesScreen
import com.coparently.app.presentation.stickers.MyStickersScreen
import com.coparently.app.presentation.stickers.StickersViewModel
import com.coparently.app.presentation.support.SupportScreen
import com.coparently.app.presentation.support.SupportUiState
import com.coparently.app.presentation.support.SupportViewModel
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.testing.RobolectricTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every screen pushed onto a stack describes its bar (MON-24 phase 3, part B, Tasks 5 and 6):
 * where a shell draws the bars (the iPhone, a recording [BarHost] here) the screen hands it its
 * title, the system back button and exactly its actions, each with its SF Symbol, and a list's
 * floating "+" becomes the bar's `plus`; with no host (Android) the screen draws the Material bar
 * with the same title and arrow, and its floating button, as before. A screen with a button pinned
 * under it (the profile's Save, custody's, each Connect school step's) hands it to the host as the
 * bar's primary action, the arrow kept, and draws it itself only on Android; the custody conflict,
 * which offers no way back, hands the host no back button at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class PushedBarsTest {

    @get:Rule
    val rule = createComposeRule()

    private val shown = mutableListOf<ScreenBar>()
    private val host = BarHost { shown += it }
    private val clicks = mutableListOf<String>()
    private var graphStarted = false

    @After
    fun stopGraph() {
        if (graphStarted) stopKoin()
    }

    // region Lists whose "+" moves into the bar

    @Test
    fun `the children list's bar goes back and adds a child`() {
        compose(withHost = true) { childInfoScreen() }
        assertBar(title = "Child information", back = "Back", actions = listOf("plus" to "Add child"))
        assertEquals(0, extendedFabCount("Add child"))

        clickAction("Add child")
        assertEquals(listOf("add child"), clicks)
    }

    @Test
    fun `without a host the children list keeps its arrow and its floating button`() {
        compose(withHost = false) { childInfoScreen() }
        assertAndroidBar(title = "Child information", back = "Back")
        assertEquals(1, extendedFabCount("Add child"))
    }

    @Test
    fun `the pets list's bar goes back and adds a pet`() {
        compose(withHost = true) { petsScreen() }
        assertBar(title = "Pets", back = "Back", actions = listOf("plus" to "Add pet"))
        assertEquals(0, extendedFabCount("Add pet"))

        clickAction("Add pet")
        assertEquals(listOf("edit pet new"), clicks)
    }

    @Test
    fun `without a host the pets list keeps its arrow and its floating button`() {
        compose(withHost = false) { petsScreen() }
        assertAndroidBar(title = "Pets", back = "Back")
        assertEquals(1, extendedFabCount("Add pet"))
    }

    @Test
    fun `the journal's bar goes back and starts an entry`() {
        compose(withHost = true) { journalScreen() }
        assertBar(title = "Private journal", back = "Back", actions = listOf("plus" to "New entry"))
        rule.onAllNodesWithContentDescription("New entry").assertCountEquals(0)

        clickAction("New entry")
        assertEquals(listOf("new entry"), clicks)
    }

    @Test
    fun `without a host the journal keeps its arrow and its floating button`() {
        compose(withHost = false) { journalScreen() }
        assertAndroidBar(title = "Private journal", back = "Back")
        assertTrue(rule.onAllNodesWithContentDescription("New entry").fetchSemanticsNodes().isNotEmpty())
    }

    // endregion

    // region A child

    @Test
    fun `a child's bar names the child and edits the record`() {
        startGraph()
        compose(withHost = true) { childDetailScreen() }
        assertBar(title = "Emma", back = "Back", actions = listOf("pencil" to "Edit"))

        clickAction("Edit")
        assertEquals(listOf("edit ${CHILD.id}"), clicks)
    }

    @Test
    fun `without a host a child's bar names the child and has no pencil`() {
        startGraph()
        compose(withHost = false) { childDetailScreen() }
        assertAndroidBar(title = "Emma", back = "Back")
        rule.onAllNodesWithContentDescription("Edit").assertCountEquals(0)
    }

    // endregion

    // region Screens with no actions

    @Test
    fun `the contacts' bar goes back`() = checkPlainBar("Contacts") { contactsScreen() }

    @Test
    fun `without a host the contacts draw their own bar`() = checkAndroidBar("Contacts") { contactsScreen() }

    @Test
    fun `the documents' bar goes back`() = checkPlainBar("Documents") { documentsScreen() }

    @Test
    fun `without a host the documents draw their own bar`() = checkAndroidBar("Documents") { documentsScreen() }

    @Test
    fun `the stickers' bar goes back`() = checkPlainBar("My stickers") { stickersScreen() }

    @Test
    fun `without a host the stickers draw their own bar`() = checkAndroidBar("My stickers") { stickersScreen() }

    @Test
    fun `the earlier families' bar goes back`() = checkPlainBar("Earlier families") { earlierFamiliesScreen() }

    @Test
    fun `without a host the earlier families draw their own bar`() =
        checkAndroidBar("Earlier families") { earlierFamiliesScreen() }

    @Test
    fun `an earlier family's bar names it and goes back`() = checkPlainBar("With Zuzana") { earlierFamilyScreen() }

    @Test
    fun `without a host an earlier family draws its own bar`() =
        checkAndroidBar("With Zuzana") { earlierFamilyScreen() }

    @Test
    fun `the unassigned records' bar goes back`() =
        checkPlainBar("Records without a family") { unassignedScreen() }

    @Test
    fun `without a host the unassigned records draw their own bar`() =
        checkAndroidBar("Records without a family") { unassignedScreen() }

    @Test
    fun `the month in review's bar goes back`() = checkPlainBar("Month in review") { monthReviewScreen() }

    @Test
    fun `without a host the month in review draws its own bar`() =
        checkAndroidBar("Month in review") { monthReviewScreen() }

    @Test
    fun `the export's bar goes back`() = checkPlainBar("Export the record") { exportScreen() }

    @Test
    fun `without a host the export draws its own bar`() = checkAndroidBar("Export the record") { exportScreen() }

    @Test
    fun `child support's bar goes back`() = checkPlainBar("Child support") { supportScreen() }

    @Test
    fun `without a host child support draws its own bar`() = checkAndroidBar("Child support") { supportScreen() }

    // endregion

    // region Agreements (Task 6)

    @Test
    fun `the requests' bar goes back`() = checkPlainBar("Requests") { changeRequestsScreen() }

    @Test
    fun `without a host the requests draw their own bar`() = checkAndroidBar("Requests") { changeRequestsScreen() }

    @Test
    fun `the parenting plan's bar goes back`() = checkPlainBar("Parenting plan") { parentingPlanScreen() }

    @Test
    fun `without a host the parenting plan draws its own bar`() =
        checkAndroidBar("Parenting plan") { parentingPlanScreen() }

    @Test
    fun `the custody screen's bar follows a child's schedule and carries its save`() {
        val states = MutableStateFlow(CustodySetupUiState())
        val viewModel = custodyViewModel(states)
        startGraph(module { factory { seasonalViewModel() } })
        compose(withHost = true) { CustodySetupScreen(onNavigateBack = { clicks += "up" }, viewModel = viewModel) }
        // The overview: the screen's title, and nothing to send.
        assertBar(title = "Custody schedule", back = "Back", actions = emptyList())

        // A child's own schedule: its title, and the pattern's save as the bar's primary action.
        rule.runOnIdle { states.value = CustodySetupUiState(childScope = ChildScope("child-1", "Emma")) }
        assertBar(
            title = "Schedule for Emma",
            back = "Back",
            actions = listOf("checkmark" to "Save custody schedule"),
            primary = "Save custody schedule"
        )
        rule.onAllNodesWithText("Save custody schedule").assertCountEquals(0)

        // Back leaves the child's schedule first, as the arrow does today.
        rule.runOnIdle { (shown.last().leading as BarLeading.Back).onClick() }
        verify(exactly = 1) { viewModel.editSchedule(null) }
        assertTrue("Back left the screen from a child's schedule", clicks.isEmpty())
    }

    @Test
    fun `without a host the custody screen draws its own bar`() {
        startGraph(module { factory { seasonalViewModel() } })
        compose(withHost = false) {
            CustodySetupScreen(
                onNavigateBack = {},
                viewModel = custodyViewModel(MutableStateFlow(CustodySetupUiState()))
            )
        }
        assertAndroidBar(title = "Custody schedule", back = "Back")
    }

    @Test
    fun `the custody conflict's bar offers no way back`() {
        compose(withHost = true) { custodyConflictScreen() }
        assertBar(title = "Two schedules", back = null, actions = emptyList())
    }

    @Test
    fun `without a host the custody conflict draws its bar with no arrow`() {
        compose(withHost = false) { custodyConflictScreen() }
        assertTrue(rule.onAllNodesWithText("Two schedules").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        assertTrue("A bar reached a host on Android", shown.isEmpty())
    }

    // endregion

    // region People (Task 6)

    @Test
    fun `the pairing screen's bar goes back`() = checkPlainBar("Pair with your co-parent") { pairingScreen() }

    @Test
    fun `without a host the pairing screen draws its own bar`() =
        checkAndroidBar("Pair with your co-parent") { pairingScreen() }

    @Test
    fun `the professionals' bar goes back`() = checkPlainBar("Professionals") { professionalsScreen() }

    @Test
    fun `without a host the professionals draw their own bar`() =
        checkAndroidBar("Professionals") { professionalsScreen() }

    @Test
    fun `a professional's calendar's bar goes back`() =
        checkPlainBar("Family calendar") { professionalCalendarScreen() }

    @Test
    fun `without a host a professional's calendar draws its own bar`() =
        checkAndroidBar("Family calendar") { professionalCalendarScreen() }

    @Test
    fun `a professional's plan's bar goes back`() = checkPlainBar("Parenting plan") { professionalPlanScreen() }

    @Test
    fun `without a host a professional's plan draws its own bar`() =
        checkAndroidBar("Parenting plan") { professionalPlanScreen() }

    @Test
    fun `the family friends' bar goes back`() = checkPlainBar("Family friends") { friendsScreen() }

    @Test
    fun `without a host the family friends draw their own bar`() =
        checkAndroidBar("Family friends") { friendsScreen() }

    @Test
    fun `a friend's bar names them and goes back`() = checkPlainBar("Grandma") { friendDetailScreen() }

    @Test
    fun `without a host a friend's bar names them`() = checkAndroidBar("Grandma") { friendDetailScreen() }

    @Test
    fun `a friend's own profile's bar goes back`() = checkPlainBar("My profile") { friendProfileScreen() }

    @Test
    fun `without a host a friend's own profile draws its own bar`() =
        checkAndroidBar("My profile") { friendProfileScreen() }

    @Test
    fun `a friend's family calendar's bar goes back`() =
        checkPlainBar("Family calendar") { friendCalendarScreen() }

    @Test
    fun `without a host a friend's family calendar draws its own bar`() =
        checkAndroidBar("Family calendar") { friendCalendarScreen() }

    @Test
    fun `the guest access bar goes back`() = checkPlainBar("Guest access") { guestAcceptScreen() }

    @Test
    fun `without a host the guest access draws its own bar`() =
        checkAndroidBar("Guest access") { guestAcceptScreen() }

    @Test
    fun `my details' bar goes back and carries the save`() {
        val states = MutableStateFlow(ProfileUiState(me = ME))
        val viewModel = profileViewModel(states)
        compose(withHost = true) {
            ProfileScreen(editable = true, onNavigateUp = { clicks += "up" }, viewModel = viewModel)
        }
        assertBar(title = "My details", back = "Back", actions = listOf("checkmark" to "Save"), primary = "Save")
        assertTrue(shown.last().actions.single().enabled)
        rule.onAllNodesWithText("Save").assertCountEquals(0)

        clickAction("Save")
        verify(exactly = 1) { viewModel.save() }

        // While it saves, the bar's Save waits as the button would.
        rule.runOnIdle { states.value = ProfileUiState(me = ME, isSaving = true) }
        rule.waitForIdle()
        assertFalse(shown.last().actions.single().enabled)
    }

    @Test
    fun `without a host my details keep their arrow and their save button`() {
        compose(withHost = false) {
            ProfileScreen(
                editable = true,
                onNavigateUp = {},
                viewModel = profileViewModel(MutableStateFlow(ProfileUiState(me = ME)))
            )
        }
        assertAndroidBar(title = "My details", back = "Back")
        assertTrue(rule.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the co-parent's profile's bar goes back`() = checkPlainBar("Co-parent") {
        ProfileScreen(
            editable = false,
            onNavigateUp = { clicks += "up" },
            viewModel = profileViewModel(MutableStateFlow(ProfileUiState(me = ME)))
        )
    }

    @Test
    fun `without a host the co-parent's profile draws its own bar`() = checkAndroidBar("Co-parent") {
        ProfileScreen(
            editable = false,
            onNavigateUp = {},
            viewModel = profileViewModel(MutableStateFlow(ProfileUiState(me = ME)))
        )
    }

    // endregion

    // region Sign-in links and Settings' sub-screens (Task 6)

    @Test
    fun `the school import's bar goes back`() = checkPlainBar("School import") { schoolImportScreen() }

    @Test
    fun `without a host the school import draws its own bar`() =
        checkAndroidBar("School import") { schoolImportScreen() }

    @Test
    fun `connecting a school's bar follows the flow and carries each step's button`() {
        val states = MutableStateFlow(ConnectSchoolUiState(step = ConnectStep.TOWN))
        val viewModel = connectSchoolViewModel(states)
        compose(withHost = true) {
            ConnectSchoolScreen(onNavigateUp = { clicks += "up" }, onDone = {}, viewModel = viewModel)
        }
        // Choosing a town: nothing pinned, so nothing in the bar.
        assertBar(title = "Connect a school", back = "Back", actions = emptyList())

        // An address typed by hand: the step's button, as the bar's primary action.
        rule.runOnIdle {
            states.value = ConnectSchoolUiState(step = ConnectStep.MANUAL, manualUrl = "https://school.cz")
        }
        assertBar(
            title = "Connect a school",
            back = "Back",
            actions = listOf("checkmark" to "Check address"),
            primary = "Check address"
        )
        clickAction("Check address")
        verify(exactly = 1) { viewModel.checkManualUrl() }

        // Signing in again: the title the flow had for it.
        rule.runOnIdle { states.value = ConnectSchoolUiState(step = ConnectStep.CREDENTIALS, reconnecting = true) }
        assertBar(
            title = "Sign in again",
            back = "Back",
            actions = listOf("checkmark" to "Sign in"),
            primary = "Sign in"
        )
        assertFalse("Sign in waits for a username and a password", shown.last().actions.single().enabled)

        // The arrow runs the step's back; the screen is left only once there is no step to undo.
        rule.runOnIdle { (shown.last().leading as BarLeading.Back).onClick() }
        verify(exactly = 1) { viewModel.back() }
        assertTrue("Back left the flow from an inner step", clicks.isEmpty())
    }

    @Test
    fun `without a host connecting a school keeps its arrow and its button`() {
        val states = MutableStateFlow(ConnectSchoolUiState(step = ConnectStep.MANUAL, manualUrl = "https://school.cz"))
        compose(withHost = false) {
            ConnectSchoolScreen(onNavigateUp = {}, onDone = {}, viewModel = connectSchoolViewModel(states))
        }
        assertAndroidBar(title = "Connect a school", back = "Back")
        assertTrue(rule.onAllNodesWithText("Check address").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the calendar feed's bar goes back`() = checkPlainBar("Calendar for other apps") { calendarFeedScreen() }

    @Test
    fun `without a host the calendar feed draws its own bar`() =
        checkAndroidBar("Calendar for other apps") { calendarFeedScreen() }

    @Test
    fun `the data sources' bar goes back`() =
        checkPlainBar("Data sources and licences") { DataSourcesScreen(onNavigateUp = { clicks += "up" }) }

    @Test
    fun `without a host the data sources draw their own bar`() =
        checkAndroidBar("Data sources and licences") { DataSourcesScreen(onNavigateUp = {}) }

    // endregion

    // region Helpers

    private fun checkPlainBar(title: String, content: @Composable () -> Unit) {
        compose(withHost = true, content = content)
        assertBar(title = title, back = "Back", actions = emptyList())
        rule.runOnIdle { (shown.last().leading as BarLeading.Back).onClick() }
        assertEquals(listOf("up"), clicks)
    }

    private fun checkAndroidBar(title: String, content: @Composable () -> Unit) {
        compose(withHost = false, content = content)
        assertAndroidBar(title = title, back = "Back")
    }

    private fun startGraph(definitions: Module? = null) {
        val parentsSource = mockk<ParentsSource> { every { observe() } returns MutableStateFlow(PARENTS) }
        startKoin { modules(listOfNotNull(module { single { parentsSource } }, definitions)) }
        graphStarted = true
    }

    /** The screen as the app draws it: the platform, the theme and, for the iPhone, a bar host. */
    private fun compose(withHost: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            ProvideAndroidPlatform {
                CoPlanlyTheme(darkTheme = false, contrastLevel = ContrastLevel.STANDARD) {
                    if (withHost) {
                        CompositionLocalProvider(LocalBarHost provides host) { content() }
                    } else {
                        content()
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /**
     * The bar last shown: [title], the back button labelled [back] (none at all when null), exactly
     * [actions] (symbol to label), and [primary], the label of the one primary action, if any.
     */
    private fun assertBar(
        title: String,
        back: String?,
        actions: List<Pair<String, String>>,
        primary: String? = null
    ) {
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals(title, bar.title)
        val leading = bar.leading
        if (back == null) {
            assertEquals(BarLeading.None, leading)
        } else {
            assertTrue("The leading is $leading", leading is BarLeading.Back)
            assertEquals(back, (leading as BarLeading.Back).label)
        }
        assertEquals(actions, bar.actions.map { it.sfSymbol to it.label })
        assertEquals(listOfNotNull(primary), bar.actions.filter { it.primary }.map { it.label })
    }

    /**
     * How many extended floating buttons labelled [label] are drawn. Material's extended button may
     * keep its text out of the semantics tree, so a clickable node with neither text nor a
     * description (the screen's only such node, its rows and arrow being labelled) counts as one too.
     */
    private fun extendedFabCount(label: String): Int {
        val labelled = rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
        val unlabelled = rule.onAllNodes(hasClickAction() and Unlabelled).fetchSemanticsNodes().size
        return maxOf(labelled, unlabelled)
    }

    /** Taps the bar's action labelled [label], as the iPhone's bar button does. */
    private fun clickAction(label: String) {
        rule.runOnIdle { shown.last().actions.single { it.label == label }.onClick() }
        rule.waitForIdle()
    }

    /** Without a host nothing reaches a host, and the Material bar shows [title] and its arrow. */
    private fun assertAndroidBar(title: String, back: String) {
        rule.waitForIdle()
        assertTrue("A bar reached a host on Android", shown.isEmpty())
        assertTrue("No title $title", rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            "No up arrow labelled $back",
            rule.onAllNodesWithContentDescription(back).fetchSemanticsNodes().isNotEmpty()
        )
    }

    // endregion

    // region The screens

    @Composable
    private fun childInfoScreen() {
        ChildInfoScreen(
            onNavigateBack = { clicks += "up" },
            onOpenChild = {},
            onAddChild = { clicks += "add child" },
            viewModel = childViewModel()
        )
    }

    @Composable
    private fun childDetailScreen() {
        ChildDetailScreen(
            childInfoId = CHILD.id,
            onNavigateBack = { clicks += "up" },
            onEditClick = { clicks += "edit $it" },
            viewModel = childViewModel()
        )
    }

    @Composable
    private fun petsScreen() {
        PetsScreen(
            onNavigateBack = { clicks += "up" },
            onEditPet = { clicks += "edit pet $it" },
            viewModel = mockk<PetsViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow<PetsUiState>(PetsUiState.Success(listOf(PET)))
            }
        )
    }

    @Composable
    private fun journalScreen() {
        JournalListScreen(
            onNavigateUp = { clicks += "up" },
            onOpenEntry = {},
            onNewEntry = { clicks += "new entry" },
            viewModel = mockk<JournalListViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow<JournalListState>(JournalListState.Loaded(listOf(ENTRY)))
            }
        )
    }

    @Composable
    private fun contactsScreen() {
        ContactsScreen(
            onNavigateUp = { clicks += "up" },
            onAddContact = {},
            viewModel = mockk<ContactsViewModel>(relaxed = true) {
                every { groups } returns MutableStateFlow<Loadable<List<ContactGroup>>>(Loadable.Loaded(emptyList()))
            }
        )
    }

    @Composable
    private fun documentsScreen() {
        FamilyDocumentsScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<FamilyDocumentsViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(DocumentsUiState())
                every { parents } returns MutableStateFlow(PARENTS)
                every { opened } returns emptyFlow()
            }
        )
    }

    @Composable
    private fun stickersScreen() {
        MyStickersScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<StickersViewModel>(relaxed = true) {
                every { parents } returns MutableStateFlow(PARENTS)
                every { received } returns MutableStateFlow<List<Sticker>>(emptyList())
                every { sent } returns MutableStateFlow<List<Sticker>>(emptyList())
                every { role } returns MutableStateFlow<ParentRoleAnswer>(ParentRoleAnswer.Loading)
                every { roleSaveFailed } returns MutableStateFlow(false)
            }
        )
    }

    @Composable
    private fun earlierFamiliesScreen() {
        EarlierFamiliesScreen(onNavigateUp = { clicks += "up" }, onOpen = {}, viewModel = earlierViewModel())
    }

    @Composable
    private fun earlierFamilyScreen() {
        EarlierFamilyScreen(
            familyId = EARLIER.familyId,
            onNavigateUp = { clicks += "up" },
            onOpenDocuments = {},
            onExport = {},
            viewModel = earlierViewModel()
        )
    }

    @Composable
    private fun unassignedScreen() {
        UnassignedRecordsScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<UnassignedRecordsViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(UnassignedRecordsUiState(loading = false))
                every { busy } returns MutableStateFlow(false)
                every { notice } returns MutableStateFlow<UnassignedNotice?>(null)
            }
        )
    }

    @Composable
    private fun monthReviewScreen() {
        MonthReviewScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<MonthReviewViewModel>(relaxed = true) {
                every { aiAvailable } returns false
                every { parents } returns MutableStateFlow(PARENTS)
                every { state } returns MutableStateFlow(MonthReviewUiState(YearMonth(2026, 5), canGoForward = false))
                every { summary } returns MutableStateFlow<String?>(null)
                every { summaryState } returns MutableStateFlow<AiAssistState>(AiAssistState.Idle)
            }
        )
    }

    @Composable
    private fun exportScreen() {
        ExportScreen(
            onNavigateBack = { clicks += "up" },
            viewModel = mockk<ExportViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(ExportUiState(from = DAY, to = DAY))
                every { files } returns emptyFlow()
                every { exportAvailable } returns true
            }
        )
    }

    @Composable
    private fun supportScreen() {
        SupportScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<SupportViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow<SupportUiState>(SupportUiState.Loading)
                every { busy } returns MutableStateFlow(false)
                every { error } returns MutableStateFlow<UiText?>(null)
                every { parents } returns MutableStateFlow(PARENTS)
                every { defaultCurrency } returns MutableStateFlow("EUR")
                every { myUid } returns "alice"
                every { remindersAvailable } returns true
            }
        )
    }

    @Composable
    private fun changeRequestsScreen() {
        ChangeRequestsScreen(
            onBack = { clicks += "up" },
            onOpenEvent = {},
            viewModel = mockk<ChangeRequestViewModel>(relaxed = true) {
                every { requests } returns MutableStateFlow<RequestsState>(RequestsState.Loading)
                every { miniMonths } returns MutableStateFlow(emptyMap())
                every { swapMonths } returns MutableStateFlow(emptyMap())
                every { pendingProposalDiff } returns MutableStateFlow(null)
                every { pendingProposalCitation } returns MutableStateFlow<CitationStatus>(CitationStatus.None)
                every { errorMessage } returns MutableStateFlow<UiText?>(null)
                every { parents } returns MutableStateFlow(PARENTS)
                every { lastChanceInFlight } returns MutableStateFlow(emptySet())
            }
        )
    }

    @Composable
    private fun parentingPlanScreen() {
        ParentingPlanScreen(
            onNavigateBack = { clicks += "up" },
            viewModel = mockk<ParentingPlanViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow<ParentingPlanUiState>(ParentingPlanUiState.Loading)
                every { parents } returns MutableStateFlow(PARENTS)
            }
        )
    }

    @Composable
    private fun custodyConflictScreen() {
        CustodyConflictScreen(
            onResolved = { clicks += "resolved" },
            viewModel = mockk<CustodyConflictViewModel>(relaxed = true) {
                every { prompt } returns MutableStateFlow<CustodyConflictPrompt?>(
                    CustodyConflictPrompt(conflict = CustodyConflict.Conflict(MINE, THEIRS), mySlot = "mom")
                )
                every { parents } returns MutableStateFlow(PARENTS)
                every { isSaving } returns MutableStateFlow(false)
                every { resolved } returns MutableStateFlow(false)
                every { saveFailed } returns MutableStateFlow(false)
            }
        )
    }

    @Composable
    private fun pairingScreen() {
        PairingScreen(
            onNavigateBack = { clicks += "up" },
            onCustodyConflict = {},
            viewModel = mockk<PairingViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow<PairingState>(PairingState.Loading)
                every { partnerOnScreen } returns MutableStateFlow<PartnerSummary?>(null)
                every { form } returns MutableStateFlow(PairingFormState())
                every { account } returns MutableStateFlow(null)
                every { events } returns emptyFlow()
                every { qrAvailable } returns false
            }
        )
    }

    @Composable
    private fun professionalsScreen() {
        ProfessionalsScreen(
            onNavigateUp = { clicks += "up" },
            onOpenCalendar = {},
            onOpenPlan = {},
            viewModel = mockk<ProfessionalsViewModel>(relaxed = true) {
                every { familyGrants } returns MutableStateFlow(emptyList())
                every { myGrants } returns MutableStateFlow(emptyList())
                every { invite } returns MutableStateFlow(ProfessionalInviteState())
                every { redeem } returns MutableStateFlow(ProfessionalRedeemState())
                every { openGrantId } returns MutableStateFlow<String?>(null)
                every { actionError } returns MutableStateFlow(null)
                every { viewerUid() } returns "alice"
            }
        )
    }

    @Composable
    private fun professionalCalendarScreen() {
        ProfessionalCalendarScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<ProfessionalCalendarViewModel>(relaxed = true) {
                every { uiState } returns
                    MutableStateFlow<ProfessionalCalendarUiState>(ProfessionalCalendarUiState.Loading)
            }
        )
    }

    @Composable
    private fun professionalPlanScreen() {
        ProfessionalPlanScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<ProfessionalPlanViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow<ProfessionalPlanUiState>(ProfessionalPlanUiState.Loading)
            }
        )
    }

    @Composable
    private fun friendsScreen() {
        FriendsScreen(
            onNavigateUp = { clicks += "up" },
            onOpenFriend = {},
            onOpenMyProfile = {},
            onOpenFriendCalendar = {},
            viewModel = friendViewModel()
        )
    }

    @Composable
    private fun friendDetailScreen() {
        FriendDetailScreen(
            friendUid = GRANDMA.friendUid,
            onNavigateUp = { clicks += "up" },
            onRevoked = {},
            viewModel = friendViewModel()
        )
    }

    @Composable
    private fun friendProfileScreen() {
        FriendProfileScreen(onNavigateUp = { clicks += "up" }, viewModel = friendViewModel())
    }

    @Composable
    private fun friendCalendarScreen() {
        FriendCalendarScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<FriendCalendarViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow<FriendCalendarUiState>(FriendCalendarUiState.Loading)
            }
        )
    }

    @Composable
    private fun guestAcceptScreen() {
        GuestAcceptScreen(
            onDone = { clicks += "up" },
            viewModel = mockk<GuestAcceptViewModel>(relaxed = true) {
                every { uiState } returns MutableStateFlow(GuestAcceptUiState())
            }
        )
    }

    @Composable
    private fun schoolImportScreen() {
        SchoolImportScreen(
            onNavigateUp = { clicks += "up" },
            onConnect = {},
            onReconnect = {},
            viewModel = mockk<SchoolImportViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(SchoolImportUiState())
            }
        )
    }

    @Composable
    private fun calendarFeedScreen() {
        CalendarFeedScreen(
            onNavigateUp = { clicks += "up" },
            viewModel = mockk<CalendarFeedViewModel>(relaxed = true) {
                every { state } returns MutableStateFlow(CalendarFeedUiState())
            }
        )
    }

    private fun custodyViewModel(states: MutableStateFlow<CustodySetupUiState>): CustodySetupViewModel =
        mockk(relaxed = true) {
            every { uiState } returns states
            every { parents } returns MutableStateFlow(PARENTS)
            every { currentModel } returns MutableStateFlow<CustodyModel?>(null)
            every { children } returns MutableStateFlow(emptyList())
            every { proposes } returns MutableStateFlow(false)
            every { answerByCap } returns MutableStateFlow<Long?>(null)
        }

    private fun seasonalViewModel(): SeasonalScheduleViewModel = mockk(relaxed = true) {
        every { layersState } returns MutableStateFlow(SeasonalLayersUiState())
    }

    private fun profileViewModel(states: MutableStateFlow<ProfileUiState>): ProfileViewModel = mockk(relaxed = true) {
        every { uiState } returns states
    }

    private fun connectSchoolViewModel(states: MutableStateFlow<ConnectSchoolUiState>): ConnectSchoolViewModel =
        mockk(relaxed = true) {
            every { state } returns states
            // An earlier step to go back to: the screen is not left.
            every { back() } returns true
        }

    private fun friendViewModel(): FriendViewModel = mockk(relaxed = true) {
        every { friends } returns MutableStateFlow(listOf(GRANDMA))
        every { myGrants } returns MutableStateFlow(emptyList())
        every { invite } returns MutableStateFlow(FriendInviteState())
        every { redeem } returns MutableStateFlow(FriendRedeemState())
        every { myName } returns MutableStateFlow<String?>("Alice")
        every { viewedProfile } returns MutableStateFlow<FriendProfile?>(null)
        every { myProfile } returns MutableStateFlow<FriendProfile?>(null)
        every { saveError } returns MutableStateFlow(null)
    }

    private fun childViewModel(): ChildInfoViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow<ChildInfoUiState>(ChildInfoUiState.Success(listOf(CHILD)))
        every { guestInvite } returns MutableStateFlow(GuestInviteState())
        every { guestRevokeFailed } returns MutableStateFlow(false)
        every { photoError } returns MutableStateFlow<MedicalPhotoError?>(null)
    }

    private fun earlierViewModel(): EarlierFamiliesViewModel = mockk(relaxed = true) {
        every { families } returns MutableStateFlow<List<EarlierFamily>?>(listOf(EARLIER))
    }

    // endregion

    private companion object {
        val Unlabelled = SemanticsMatcher("has neither text nor a content description") {
            SemanticsProperties.Text !in it.config && SemanticsProperties.ContentDescription !in it.config
        }

        val DAY = LocalDate(2026, 5, 13)
        val NOW = LocalDateTime(2026, 5, 1, 9, 0)

        val PARENTS = Parents(me = NamedParent(uid = "alice", slot = "mom", name = "Alice"), loaded = true)

        val CHILD = ChildInfo(id = "child-1", childName = "Emma", dateOfBirth = null, createdAt = NOW, updatedAt = NOW)

        val PET = Pet(id = "pet-1", name = "Max", createdAt = NOW, updatedAt = NOW)

        val ENTRY = JournalEntry(
            id = "entry-1",
            entryDate = DAY,
            text = "The handover went well.",
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
            familyId = null,
            createdByFirebaseUid = "alice"
        )

        val EARLIER = EarlierFamily(familyId = "alice_zuzana", coParentUid = "zuzana", coParentName = "Zuzana")

        val ME = User(id = "alice", email = "alice@example.com", name = "Alice", role = "mom", colorCode = "")

        val MINE = CustodyModel(
            id = "mine",
            modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
            patternDays = 14,
            momDayIndices = (0..6).toSet(),
            startDate = DAY
        )

        val THEIRS = MINE.copy(id = "theirs", momDayIndices = (7..13).toSet())

        val GRANDMA = CalendarFriendGrant(
            friendUid = "grandma",
            name = "Grandma",
            familyParents = listOf("alice", "bob"),
            grantedBy = "alice",
            grantedAtMillis = 1L,
            expiresAtMillis = Long.MAX_VALUE,
            familyId = "alice_bob"
        )
    }
}
