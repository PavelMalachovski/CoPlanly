package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.coparently.app.domain.contacts.ContactGroup
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.childinfo.ChildDetailScreen
import com.coparently.app.presentation.childinfo.ChildInfoScreen
import com.coparently.app.presentation.childinfo.ChildInfoUiState
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.childinfo.GuestInviteState
import com.coparently.app.presentation.childinfo.MedicalPhotoError
import com.coparently.app.presentation.contacts.ContactsScreen
import com.coparently.app.presentation.contacts.ContactsViewModel
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
import com.coparently.app.presentation.journal.JournalListScreen
import com.coparently.app.presentation.journal.JournalListState
import com.coparently.app.presentation.journal.JournalListViewModel
import com.coparently.app.presentation.pets.PetsScreen
import com.coparently.app.presentation.pets.PetsUiState
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.review.MonthReviewScreen
import com.coparently.app.presentation.review.MonthReviewUiState
import com.coparently.app.presentation.review.MonthReviewViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every screen pushed onto a stack describes its bar (MON-24 phase 3, part B, Tasks 5 and 6):
 * where a shell draws the bars (the iPhone, a recording [BarHost] here) the screen hands it its
 * title, the system back button and exactly its actions, each with its SF Symbol, and a list's
 * floating "+" becomes the bar's `plus`; with no host (Android) the screen draws the Material bar
 * with the same title and arrow, and its floating button, as before.
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
        rule.onAllNodes(hasText("Add child") and hasClickAction()).assertCountEquals(0)

        clickAction("Add child")
        assertEquals(listOf("add child"), clicks)
    }

    @Test
    fun `without a host the children list keeps its arrow and its floating button`() {
        compose(withHost = false) { childInfoScreen() }
        assertAndroidBar(title = "Child information", back = "Back")
        assertTrue(rule.onAllNodesWithText("Add child").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the pets list's bar goes back and adds a pet`() {
        compose(withHost = true) { petsScreen() }
        assertBar(title = "Pets", back = "Back", actions = listOf("plus" to "Add pet"))
        rule.onAllNodes(hasText("Add pet") and hasClickAction()).assertCountEquals(0)

        clickAction("Add pet")
        assertEquals(listOf("edit pet new"), clicks)
    }

    @Test
    fun `without a host the pets list keeps its arrow and its floating button`() {
        compose(withHost = false) { petsScreen() }
        assertAndroidBar(title = "Pets", back = "Back")
        assertTrue(rule.onAllNodesWithText("Add pet").fetchSemanticsNodes().isNotEmpty())
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

    private fun startGraph() {
        val parentsSource = mockk<ParentsSource> { every { observe() } returns MutableStateFlow(PARENTS) }
        startKoin { modules(module { single { parentsSource } }) }
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

    /** The bar last shown: [title], the back button labelled [back], and exactly [actions] (symbol to label). */
    private fun assertBar(title: String, back: String, actions: List<Pair<String, String>>) {
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals(title, bar.title)
        val leading = bar.leading
        assertTrue("The leading is $leading", leading is BarLeading.Back)
        assertEquals(back, (leading as BarLeading.Back).label)
        assertEquals(actions, bar.actions.map { it.sfSymbol to it.label })
        assertTrue("A pushed screen's action is not a form's Save", bar.actions.none { it.primary })
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
    }
}
