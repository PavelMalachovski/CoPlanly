package com.coparently.app.di

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.family.OtherFamiliesSignals
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.di.koin.appModules
import com.coparently.app.domain.friends.FriendCalendarRead
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.FamilyViewViewModel
import com.coparently.app.presentation.common.ParentPaletteViewModel
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.friends.FriendCalendarViewModel
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.testing.RobolectricTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `koinViewModel()` keeps the scope Hilt's ViewModel lookup had (MON-24 phase 2).
 *
 * Two promises the app's screens rely on. The ViewModels the whole tree shares — the chat badge's
 * [ChatViewModel], [ParentPaletteViewModel], [FamilyViewViewModel] and [FamilySwitcherViewModel] —
 * are created once in the activity's `ViewModelStore` and found again by every composable that
 * asks from the activity's level. And a destination's ViewModel is created in its back-stack entry
 * with that entry's arguments in its `SavedStateHandle`, which is how `FriendCalendarViewModel`
 * learns which family to read.
 *
 * The ViewModels are the graph's own definitions ([appModules]); only what they read is replaced,
 * by mocks that answer with empty flows. The host is Compose's test activity rather than
 * `MainActivity`, whose `onCreate` needs the Keystore, SQLCipher's native library and the splash
 * screen, none of which a JVM test has; the owner the composables ask is an activity's either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class ViewModelScopeTest {

    @get:Rule
    val rule = createComposeRule()

    private val friendRepository = mockk<FriendRepository>(relaxed = true) {
        every { observeMyGrants() } returns flowOf(emptyList())
        every { observeFamilyEvents(any(), any(), any()) } returns flowOf(FriendCalendarRead.Failed)
    }

    /** What the five ViewModels read, answered with nothing. */
    private val readers = module {
        single<ParentsSource> { mockk(relaxed = true) { every { observe() } returns emptyFlow() } }
        single<FamilyViewSource> { mockk(relaxed = true) { every { observe() } returns emptyFlow() } }
        single<SelectedFamilySource> { mockk(relaxed = true) }
        single<OtherFamiliesSignals> {
            mockk(relaxed = true) { every { signals } returns emptyFlow() }
        }
        single<UserRepository> {
            mockk(relaxed = true) { every { observeCurrentUserId() } returns flowOf(null) }
        }
        single<MessageRepository> { mockk(relaxed = true) }
        single<EventRepository> { mockk(relaxed = true) }
        single<ChatPartnerSource> { mockk(relaxed = true) { every { observe() } returns emptyFlow() } }
        single<EncryptedPreferences> { mockk(relaxed = true) }
        single<PreferencesRepository> {
            mockk(relaxed = true) { every { getPauseBeforeSendingFlow() } returns flowOf(false) }
        }
        single<DepartedThreadSource> { mockk(relaxed = true) { every { observe() } returns emptyFlow() } }
        single<FriendRepository> { friendRepository }
    }

    @Before
    fun startGraph() {
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModules + readers)
        }
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    @Test
    fun theTreeWideViewModelsAreOneInstancePerActivity() {
        var first: SharedViewModels? = null
        var second: SharedViewModels? = null
        rule.setContent {
            Column {
                FirstReader { first = it }
                SecondReader { second = it }
            }
        }
        rule.waitForIdle()

        val a = checkNotNull(first) { "the first composable resolved nothing" }
        val b = checkNotNull(second) { "the second composable resolved nothing" }
        assertSame(a.chat, b.chat)
        assertSame(a.palette, b.palette)
        assertSame(a.familyView, b.familyView)
        assertSame(a.switcher, b.switcher)
    }

    /**
     * `MainActivity` reads its ViewModels with `by viewModel()` and its content, `CoPlanlyApp` in
     * `:shared-ui`, with `koinViewModel()` (MON-24 phase 2, Task 12). They must be one instance
     * whichever asks first: Google Calendar's sign-in answer reaches the activity's `SyncViewModel`,
     * and Settings reads the one `CoPlanlyApp` hands it.
     */
    @Test
    fun theActivityAndItsContentResolveOneInstanceWhicheverAsksFirst() {
        var paletteFromActivity: ParentPaletteViewModel? = null
        var paletteFromContent: ParentPaletteViewModel? = null
        var familyViewFromContent: FamilyViewViewModel? = null
        var familyViewFromActivity: FamilyViewViewModel? = null
        rule.setContent {
            val activity = LocalContext.current as ComponentActivity
            paletteFromActivity = remember { activity.getViewModel<ParentPaletteViewModel>() }
            paletteFromContent = koinViewModel()
            familyViewFromContent = koinViewModel()
            familyViewFromActivity = remember { activity.getViewModel<FamilyViewViewModel>() }
        }
        rule.waitForIdle()

        assertSame(checkNotNull(paletteFromActivity), checkNotNull(paletteFromContent))
        assertSame(checkNotNull(familyViewFromContent), checkNotNull(familyViewFromActivity))
    }

    @Test
    fun aDestinationViewModelReceivesItsArgumentThroughSavedStateHandle() {
        var resolved: FriendCalendarViewModel? = null
        rule.setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = START) {
                composable(START) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.FriendCalendar.createRoute(FAMILY_ID))
                    }
                }
                composable(Screen.FriendCalendar.route) {
                    val viewModel: FriendCalendarViewModel = koinViewModel()
                    resolved = viewModel
                    // Collecting starts the read, which names the family the handle carried.
                    viewModel.uiState.collectAsState()
                }
            }
        }
        rule.waitForIdle()

        assertNotNull("the destination resolved no ViewModel", resolved)
        verify { friendRepository.observeFamilyEvents(FAMILY_ID, any(), any()) }
    }

    private companion object {
        const val START = "start"
        const val FAMILY_ID = "family-42"
    }
}

/** One composable's view of the tree-wide ViewModels. */
private class SharedViewModels(
    val chat: ChatViewModel,
    val palette: ParentPaletteViewModel,
    val familyView: FamilyViewViewModel,
    val switcher: FamilySwitcherViewModel
)

/** Asks for the tree-wide ViewModels from the activity's level, as `NavGraph` and its bars do. */
@Composable
private fun FirstReader(onRead: (SharedViewModels) -> Unit) {
    onRead(readSharedViewModels())
}

/** A second, unrelated composable asking for the same four. */
@Composable
private fun SecondReader(onRead: (SharedViewModels) -> Unit) {
    onRead(readSharedViewModels())
}

@Composable
private fun readSharedViewModels(): SharedViewModels = SharedViewModels(
    chat = koinViewModel(),
    palette = koinViewModel(),
    familyView = koinViewModel(),
    switcher = koinViewModel()
)
