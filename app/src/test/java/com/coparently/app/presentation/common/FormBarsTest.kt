package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Pet
import com.coparently.app.presentation.changerequests.RequestChangeScreen
import com.coparently.app.presentation.changerequests.RequestChangeUiState
import com.coparently.app.presentation.changerequests.RequestChangeViewModel
import com.coparently.app.presentation.childinfo.AddEditChildInfoScreen
import com.coparently.app.presentation.childinfo.ChildFields
import com.coparently.app.presentation.childinfo.ChildInfoViewModel
import com.coparently.app.presentation.childinfo.ChildSaveOutcome
import com.coparently.app.presentation.childinfo.MedicalPhotoError
import com.coparently.app.presentation.consent.HealthConsentViewModel
import com.coparently.app.presentation.event.AddEditEventScreen
import com.coparently.app.presentation.event.EventOwnerViewModel
import com.coparently.app.presentation.event.EventViewModel
import com.coparently.app.presentation.friends.FriendViewModel
import com.coparently.app.presentation.journal.JournalEditorScreen
import com.coparently.app.presentation.journal.JournalEditorState
import com.coparently.app.presentation.journal.JournalEditorViewModel
import com.coparently.app.presentation.pets.AddEditPetScreen
import com.coparently.app.presentation.pets.PetFields
import com.coparently.app.presentation.pets.PetPhotoError
import com.coparently.app.presentation.pets.PetSaveOutcome
import com.coparently.app.presentation.pets.PetsViewModel
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.testing.RobolectricTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Every form follows the form rule (MON-24 phase 3, part B, Task 4): where a shell draws the bars
 * (the iPhone, a recording [BarHost] here) the form's bar has a cross at its start and the form's
 * Save as its one primary action, labelled as the form's own button, disabled exactly while that
 * button is and running the same save; the form draws no Save of its own. With no host (Android)
 * the form draws its bar with the up arrow and keeps its own Save, as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class FormBarsTest {

    @get:Rule
    val rule = createComposeRule()

    private val shown = mutableListOf<ScreenBar>()
    private val host = BarHost { shown += it }
    private var graphStarted = false

    @After
    fun stopGraph() {
        if (graphStarted) stopKoin()
    }

    // region The event form

    @Test
    fun `the event form's bar closes and saves once the event has a title`() {
        val viewModel = eventViewModel()
        startGraph(module { factory { ownerViewModel() } })
        compose(withHost = true) {
            AddEditEventScreen(
                eventId = null,
                onSave = {},
                onCancel = {},
                viewModel = viewModel,
                friendViewModel = friendViewModel()
            )
        }
        assertFormBar(save = "Save event", enabled = false)
        assertNoOwnSave("Save event")

        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Dentist")
        assertFormBar(save = "Save event", enabled = true)

        clickPrimary()
        verify(exactly = 1) { viewModel.createEvent(any(), any()) }
    }

    @Test
    fun `without a host the event form keeps its arrow and its sticky Save`() {
        startGraph(module { factory { ownerViewModel() } })
        compose(withHost = false) {
            AddEditEventScreen(
                eventId = null,
                onSave = {},
                onCancel = {},
                viewModel = eventViewModel(),
                friendViewModel = friendViewModel()
            )
        }
        assertAndroidBar(save = "Save event", arrow = "Cancel")
    }

    // endregion

    // region The change request

    @Test
    fun `the change request's bar sends once the event is loaded`() {
        val state = MutableStateFlow<RequestChangeUiState>(RequestChangeUiState.Loading)
        val viewModel = requestChangeViewModel(state)
        compose(withHost = true) { RequestChangeScreen(eventId = EVENT.id, onBack = {}, viewModel = viewModel) }
        assertFormBar(save = "Send request", enabled = false)

        rule.runOnIdle { state.value = RequestChangeUiState.Ready(EVENT) }
        assertFormBar(save = "Send request", enabled = true)
        assertNoOwnSave("Send request")

        clickPrimary()
        verify(exactly = 1) { viewModel.submit(EVENT, any(), any(), any(), any()) }
    }

    @Test
    fun `without a host the change request keeps its arrow and its Send button`() {
        val state = MutableStateFlow<RequestChangeUiState>(RequestChangeUiState.Ready(EVENT))
        compose(withHost = false) {
            RequestChangeScreen(eventId = EVENT.id, onBack = {}, viewModel = requestChangeViewModel(state))
        }
        assertAndroidBar(save = "Send request", arrow = "Back")
    }

    // endregion

    // region The journal

    @Test
    fun `the journal editor's bar saves once there is text`() {
        val state = MutableStateFlow(JournalEditorState(loading = false, isNew = true, entryDate = DAY))
        val viewModel = journalViewModel(state)
        compose(withHost = true) { JournalEditorScreen(onNavigateUp = {}, viewModel = viewModel) }
        assertFormBar(save = "Save", enabled = false)
        assertNoOwnSave("Save")

        rule.runOnIdle { state.value = state.value.copy(text = "The handover went well.") }
        assertFormBar(save = "Save", enabled = true)

        clickPrimary()
        verify(exactly = 1) { viewModel.save() }
    }

    @Test
    fun `without a host the journal editor keeps its arrow and its sticky Save`() {
        val state = MutableStateFlow(
            JournalEditorState(loading = false, isNew = true, entryDate = DAY, text = "A note")
        )
        compose(withHost = false) { JournalEditorScreen(onNavigateUp = {}, viewModel = journalViewModel(state)) }
        assertAndroidBar(save = "Save", arrow = "Back")
    }

    // endregion

    // region A child

    @Test
    fun `the child form's bar adds the child once it has a name`() {
        val form = FormDraft(ChildFields.EMPTY)
        val viewModel = childViewModel(form)
        startGraph(module { factory { healthConsentViewModel() } })
        compose(withHost = true) {
            AddEditChildInfoScreen(childInfoId = "new", onNavigateBack = {}, viewModel = viewModel)
        }
        assertFormBar(save = "Add child", enabled = false)
        assertNoOwnSave("Add child")

        rule.runOnIdle { form.edit { it.copy(childName = "Emma") } }
        assertFormBar(save = "Add child", enabled = true)

        clickPrimary()
        verify(exactly = 1) {
            viewModel.upsertChildInfoWithPhotos(
                childInfo = match { it.childName == "Emma" },
                isNewChild = true,
                newPhotoUris = any(),
                removedPhotoUrls = any()
            )
        }
        // Saving: the bar's Save cannot be pressed twice.
        assertFormBar(save = "Add child", enabled = false)
    }

    @Test
    fun `without a host the child form keeps its arrow and its Save button`() {
        startGraph(module { factory { healthConsentViewModel() } })
        compose(withHost = false) {
            AddEditChildInfoScreen(
                childInfoId = "new",
                onNavigateBack = {},
                viewModel = childViewModel(FormDraft(ChildFields.EMPTY))
            )
        }
        assertAndroidBar(save = "Add child", arrow = "Back")
    }

    // endregion

    // region A pet

    @Test
    fun `the pet form's bar adds the pet once it has a name`() {
        val form = FormDraft(PetFields.EMPTY)
        val viewModel = petViewModel(form)
        compose(withHost = true) { AddEditPetScreen(petId = "new", onNavigateBack = {}, viewModel = viewModel) }
        assertFormBar(save = "Add pet", enabled = false)
        assertNoOwnSave("Add pet")

        rule.runOnIdle { form.edit { it.copy(name = "Max") } }
        assertFormBar(save = "Add pet", enabled = true)

        clickPrimary()
        verify(exactly = 1) {
            viewModel.upsertPetWithPhotos(
                pet = match { it.name == "Max" },
                isNewPet = true,
                newPhotoUris = any(),
                removedPhotoUrls = any()
            )
        }
    }

    @Test
    fun `without a host the pet form keeps its arrow and its Save button`() {
        compose(withHost = false) {
            AddEditPetScreen(petId = "new", onNavigateBack = {}, viewModel = petViewModel(FormDraft(PetFields.EMPTY)))
        }
        assertAndroidBar(save = "Add pet", arrow = "Back")
    }

    // endregion

    // region Helpers

    private fun startGraph(definitions: Module) {
        startKoin { modules(definitions) }
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

    /** The bar last shown has a cross and one primary action, [save], [enabled] or not. */
    private fun assertFormBar(save: String, enabled: Boolean) {
        rule.waitForIdle()
        val bar = shown.last()
        assertTrue("The leading is ${bar.leading}", bar.leading is BarLeading.Close)
        val primary = bar.actions.filter { it.primary }
        assertEquals(1, primary.size)
        assertEquals(save, primary.single().label)
        assertEquals(enabled, primary.single().enabled)
    }

    /** Under a host the form draws no Save of its own: the bar's is the only one. */
    private fun assertNoOwnSave(save: String) {
        rule.onAllNodes(hasText(save) and hasClickAction()).assertCountEquals(0)
    }

    /** Taps the bar's primary action, as the iPhone's bar button does. */
    private fun clickPrimary() {
        rule.runOnIdle { shown.last().actions.single { it.primary }.onClick() }
        rule.waitForIdle()
    }

    /** Without a host nothing reaches a host, the up arrow is drawn and the form's own Save is too. */
    private fun assertAndroidBar(save: String, arrow: String) {
        rule.waitForIdle()
        assertTrue("A bar reached a host on Android", shown.isEmpty())
        assertTrue(
            "No up arrow labelled $arrow",
            rule.onAllNodesWithContentDescription(arrow).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "No Save button labelled $save",
            rule.onAllNodes(hasText(save) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun eventViewModel(): EventViewModel = mockk(relaxed = true) {
        every { parents } returns MutableStateFlow(PARENTS)
        every { familyMembers } returns MutableStateFlow<List<FamilyMember>>(emptyList())
        every { customEventTypes() } returns emptyList()
        coEvery { loadEventDraft() } returns null
    }

    private fun friendViewModel(): FriendViewModel = mockk(relaxed = true) {
        every { friends } returns MutableStateFlow<List<CalendarFriendGrant>>(emptyList())
    }

    private fun ownerViewModel(): EventOwnerViewModel = mockk(relaxed = true) {
        coEvery { custodyParentOn(any()) } returns null
    }

    private fun requestChangeViewModel(
        uiStates: MutableStateFlow<RequestChangeUiState>
    ): RequestChangeViewModel = mockk(relaxed = true) {
        every { uiState } returns uiStates
        every { parents } returns MutableStateFlow(PARENTS)
    }

    private fun journalViewModel(states: MutableStateFlow<JournalEditorState>): JournalEditorViewModel =
        mockk(relaxed = true) {
            every { state } returns states
        }

    private fun childViewModel(form: FormDraft<ChildFields>): ChildInfoViewModel = mockk(relaxed = true) {
        every { childForm } returns form
        every { currentChildInfo } returns MutableStateFlow<ChildInfo?>(null)
        every { saveOutcome } returns MutableSharedFlow<ChildSaveOutcome>()
        every { photoError } returns MutableStateFlow<MedicalPhotoError?>(null)
    }

    private fun healthConsentViewModel(): HealthConsentViewModel = mockk(relaxed = true) {
        every { unlocked } returns MutableStateFlow(false)
        every { asking } returns MutableStateFlow(false)
    }

    private fun petViewModel(form: FormDraft<PetFields>): PetsViewModel = mockk(relaxed = true) {
        every { petForm } returns form
        every { currentPet } returns MutableStateFlow<Pet?>(null)
        every { saveOutcome } returns MutableSharedFlow<PetSaveOutcome>()
        every { photoError } returns MutableStateFlow<PetPhotoError?>(null)
    }

    // endregion

    private companion object {
        val DAY = LocalDate(2026, 5, 13)

        val PARENTS = Parents(me = NamedParent(uid = "alice", slot = "mom", name = "Alice"), loaded = true)

        val EVENT = Event(
            id = "event-1",
            title = "Swimming",
            startDateTime = LocalDateTime(2030, 5, 13, 16, 0),
            endDateTime = LocalDateTime(2030, 5, 13, 17, 0),
            eventType = "general",
            parentOwner = "mom",
            createdAt = LocalDateTime(2026, 5, 1, 9, 0),
            updatedAt = LocalDateTime(2026, 5, 1, 9, 0)
        )
    }
}
