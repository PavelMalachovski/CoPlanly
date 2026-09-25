package com.coparently.app.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.notification.NotificationManager
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.chat.ChatUri
import com.coparently.app.domain.guests.GuestInviteUri
import com.coparently.app.domain.pairing.PairingUri
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.LocalPhotoViewerUid
import com.coparently.app.presentation.common.ParentPaletteViewModel
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.rememberAppMessages
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.navigation.NavGraph
import com.coparently.app.presentation.navigation.PendingChatLink
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.navigation.startDestinationFor
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.LocalParentPalette
import com.coparently.app.presentation.widget.TodayWidgetRefresher
import com.coparently.app.utils.ClockFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompositionLocal for providing Google Sign-In callback throughout the app.
 */
val LocalGoogleSignInCallback = staticCompositionLocalOf<((android.content.Intent) -> Unit)?> {
    null
}

/**
 * The longest the system splash is held for the first screen to be known. As long as the Compose
 * splash it replaced took to play; a start slower than that shows the loading screen.
 */
private const val SPLASH_HOLD_MAX_MS = 1_500L

/**
 * Main Activity for CoPlanly app.
 * Entry point of the application.
 * Handles Google Sign-In result, Push Notifications, and the system splash screen.
 *
 * Extends [AppCompatActivity] (not ComponentActivity) so that per-app language
 * preferences set via AppCompatDelegate.setApplicationLocales are applied to this
 * activity's configuration on every recreation, including on Android < 13.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var selectedFamilySource: SelectedFamilySource

    /** Source of [LocalPhotoViewerUid]: who is signed in decides which record photos draw (L-4). */
    @Inject
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var todayWidgetRefresher: TodayWidgetRefresher

    private val _darkThemeState = MutableStateFlow<Boolean?>(null)
    private val darkThemeState: StateFlow<Boolean?> = _darkThemeState

    /**
     * A pairing code carried by a `coplanly://pair` deep link, awaiting hand-off
     * to the pairing screen. [NavGraph] consumes it (setting it back to null)
     * once it has navigated there — see [readPairingCode] for why redeeming it
     * is never automatic.
     */
    private val _pendingPairingCode = MutableStateFlow<String?>(null)
    private val pendingPairingCode: StateFlow<String?> = _pendingPairingCode

    /**
     * A `coplanly://chat` deep link (opened by a chat-message push notification), awaiting
     * hand-off to the Chat tab or a specific thread — null while none is pending. [NavGraph]
     * consumes it (setting it back to null) once it has navigated there — same hand-off shape
     * as [pendingPairingCode], bundled into a single [PendingChatOpen] so [NavGraph]'s own
     * parameter count does not grow by two for every deep link it gains (see [NavGraph]'s
     * doc).
     */
    /**
     * A guest code carried by a `coplanly://guest` deep link, awaiting hand-off to the
     * guest-accept screen. Separate from [pendingPairingCode], and that is the point: the two
     * codes are indistinguishable six-character strings, so the host the link arrived on is
     * the only thing that says which callable may redeem it. Redeeming is never automatic
     * here either — the guest confirms on the screen, for the same reason
     * [readPairingCode] gives.
     */
    private val _pendingGuestCode = MutableStateFlow<String?>(null)
    private val pendingGuestCode: StateFlow<String?> = _pendingGuestCode

    /**
     * The two invitation codes, bundled for [NavGraph] — see [PendingInviteCodes] for why they
     * travel together and why they stay distinct fields.
     */
    private val pendingInviteCodes = PendingInviteCodes(
        pairing = _pendingPairingCode,
        onPairingConsumed = { _pendingPairingCode.value = null },
        guest = _pendingGuestCode,
        onGuestConsumed = { _pendingGuestCode.value = null }
    )

    private val _pendingChatLink = MutableStateFlow<PendingChatLink?>(null)
    private val pendingChatOpen = PendingChatOpen(
        link = _pendingChatLink,
        onConsumed = { _pendingChatLink.value = null }
    )

    /** The screen a tapped push names (D-13), awaiting hand-off to [NavGraph]. */
    private val _pendingDestination = MutableStateFlow<PushDestination?>(null)
    private val pendingDestinationOpen = PendingDestinationOpen(
        destination = _pendingDestination,
        onConsumed = { _pendingDestination.value = null }
    )

    private val syncViewModel: SyncViewModel by viewModels()

    /**
     * What the navigation graph decides its first screen from, read here to hold the splash until
     * that is known. The same instances `NavGraph` gets from `hiltViewModel()`: both are scoped to
     * this activity.
     */
    private val authStateViewModel: AuthStateViewModel by viewModels()
    private val telemetryConsentViewModel: TelemetryConsentViewModel by viewModels()

    /** Source of [LocalParentPalette] for the whole tree (UX-15). */
    private val parentPaletteViewModel: ParentPaletteViewModel by viewModels()

    // Google Sign-In Activity Result launcher for sync
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            lifecycleScope.launch {
                syncViewModel.handleSignInResult(task)
            }
        } else {
            val isCanceled = result.resultCode == RESULT_CANCELED
            val message = UiText.Res(
                if (isCanceled) {
                    com.coparently.app.R.string.sync_google_sign_in_cancelled
                } else {
                    com.coparently.app.R.string.sync_google_sign_in_failed
                }
            )
            Log.w("MainActivity", "Google sign-in aborted: resultCode=${result.resultCode}")
            syncViewModel.handleSignInCancellation(message)
        }
    }

    /**
     * Reads the device's 12/24-hour setting each time the app comes forward, so every time it
     * prints follows the reader's clock ([ClockFormat], release audit R-9) — including a change
     * made in system settings while the app was in the background.
     */
    override fun onResume() {
        super.onResume()
        ClockFormat.follow(this)
    }

    /**
     * Handles a `coplanly://pair` link (or any other) arriving while the app is
     * already running. `MainActivity` is `singleTask`, so a warm launch is
     * routed here instead of creating a new instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunchIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // One splash: the system's, installed before super.onCreate and held until the
        // navigation graph knows its first screen, so a launch goes from the icon to the app and
        // never through the loading screen. A branded Compose splash used to follow it with an
        // entrance and a hold of its own — two splashes and about 1.2 s on every cold start
        // (docs/AUDIT-2026-10-design.md D-25). The hold is bounded: a start that has not resolved
        // by then shows the loading screen rather than an icon that seems stuck.
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        // Before the first frame, so no time is ever drawn in the language's format first.
        ClockFormat.follow(this)

        val splashDeadline = SystemClock.uptimeMillis() + SPLASH_HOLD_MAX_MS
        splashScreen.setKeepOnScreenCondition {
            firstScreenUnknown() && SystemClock.uptimeMillis() < splashDeadline
        }

        // While the app is on screen, the Today widget learns the parents' names from the same
        // source every screen uses; the widget itself cannot reach the co-parent's.
        todayWidgetRefresher.followParents(this)

        // Enable edge-to-edge display for modern Android UI
        // This makes the app draw behind the system bars
        enableEdgeToEdge()

        // Notification permission is requested contextually (Settings push toggle,
        // event reminder selection) instead of on every cold start — see
        // NotificationPermission.kt.

        // Initialize notifications
        try {
            notificationManager.initializeNotifications()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing notifications", e)
        }

        // The recents thumbnail is a screenshot the system keeps of whatever was on screen —
        // a child's medical profile, the chat — and shows to anyone who opens the app switcher.
        // Blank it. `FLAG_SECURE` is deliberately not set: a parent needs to be able to
        // screenshot a message or a schedule as a record (docs/DESIGN-court-record.md).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        // Load theme preference
        lifecycleScope.launch {
            try {
                preferencesRepository.getDarkThemeFlow().collect { isDark ->
                    _darkThemeState.value = isDark
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading theme preference", e)
                _darkThemeState.value = null // Use system default on error
            }
        }

        // Only a genuine cold start carries a launching deep link worth reading.
        // A config-change recreation (e.g. rotation) reports a non-null
        // savedInstanceState; re-reading the same launching intent there would
        // re-arm the confirmation dialog for a code the user already handled.
        if (savedInstanceState == null) {
            readLaunchIntent(intent)
        }

        setContent {
            val darkTheme by darkThemeState.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()

            // Use saved preference or fall back to system default
            val useDarkTheme = darkTheme ?: systemDarkTheme

            // The family's chosen parent colours, provided once for every ParentColors call in
            // the app (UX-15). Lifecycle-aware so the ParentsSource upstream can stop while the
            // app is in the background.
            val parentPalette by parentPaletteViewModel.palette.collectAsStateWithLifecycle()

            // The signed-in uid, for the record photos only the family's two parents may see
            // (L-4). Follows sign-in and sign-out, so a photo never draws for the wrong account.
            val photoViewerUid by remember(firebaseAuthService) {
                firebaseAuthService.getAuthStateFlow().map { it?.uid }
            }.collectAsStateWithLifecycle(initialValue = firebaseAuthService.getCurrentUser()?.uid)

            // Provide Google Sign-In callback through CompositionLocal
            val googleSignInCallback: (android.content.Intent) -> Unit = remember(googleSignInLauncher) {
                {
                        intent ->
                    googleSignInLauncher.launch(intent)
                }
            }

            CoPlanlyTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    // Snackbars that outlive the screen that raised them (D-25), shown by the
                    // navigation graph's root Scaffold.
                    val appMessages = rememberAppMessages()

                    CompositionLocalProvider(
                        LocalGoogleSignInCallback provides googleSignInCallback,
                        LocalParentPalette provides parentPalette,
                        LocalAppMessages provides appMessages,
                        LocalPhotoViewerUid provides photoViewerUid
                    ) {
                        NavGraph(
                            navController = navController,
                            syncViewModel = syncViewModel,
                            pendingInviteCodes = pendingInviteCodes,
                            pendingChatOpen = pendingChatOpen,
                            pendingDestinationOpen = pendingDestinationOpen
                        )
                    }
                }
            }
        }
    }

    /** Whether the navigation graph would still start on its loading screen. */
    private fun firstScreenUnknown(): Boolean = startDestinationFor(
        telemetryConsent = telemetryConsentViewModel.consent.value,
        isLoading = authStateViewModel.isLoading.value,
        isAuthenticated = authStateViewModel.isAuthenticated.value,
        needsOnboarding = authStateViewModel.needsOnboarding.value
    ) == Screen.Loading.route

    /**
     * Reads every deep link [intent] may carry, after switching to the family it names.
     *
     * A push tap carries the family the push belongs to ([PushPayload.FAMILY_ID], M-8). The
     * switch has to land **before** the links are armed: [NavGraph] navigates the moment a
     * pending link appears, and a chat thread, a proposal or an event opened while the device
     * still shows the other family would be read against the wrong co-parent. So with a family
     * to switch to, the links are armed from the coroutine that switched; without one they are
     * armed at once, exactly as before.
     *
     * [SelectedFamilySource.select] refuses a family the signed-in account is not in, so a
     * stale notification — from a family since left, or for an account since signed out — opens
     * on whatever is showing rather than blanking the co-parent every screen reads. The extra is
     * removed once read, so a configuration change cannot replay the switch after the parent has
     * chosen another family.
     */
    private fun readLaunchIntent(intent: Intent?) {
        val familyId = intent?.getStringExtra(PushPayload.FAMILY_ID)?.takeIf { it.isNotBlank() }
        if (intent == null || familyId == null) {
            readDeepLinks(intent)
            return
        }
        intent.removeExtra(PushPayload.FAMILY_ID)
        lifecycleScope.launch {
            runCatching {
                if (selectedFamilySource.selected()?.familyId != familyId) {
                    selectedFamilySource.select(familyId)
                }
            }.onFailure {
                // A switch that fails still opens the link, on the family already showing —
                // the same outcome as a push from a build that sent no family at all.
                Log.w("MainActivity", "Could not switch to the family a notification named", it)
            }
            readDeepLinks(intent)
        }
    }

    private fun readDeepLinks(intent: Intent?) {
        readPairingCode(intent)
        readGuestCode(intent)
        readChatDeepLink(intent)
        readPushDestination(intent)
    }

    /**
     * Reads the screen a push tap names ([PushDestination.EXTRA], D-13) and arms
     * [pendingDestinationOpen]. Only a value [PushDestination.fromKey] knows is accepted: this
     * activity is exported, and the extra may do no more than pick one of the app's own screens.
     * The extra is removed once read, so a configuration change cannot open the screen again.
     */
    private fun readPushDestination(intent: Intent?) {
        val destination = PushDestination.fromKey(intent?.getStringExtra(PushDestination.EXTRA)) ?: return
        intent?.removeExtra(PushDestination.EXTRA)
        _pendingDestination.value = destination
    }

    /**
     * Extracts a pairing code from a `coplanly://pair?code=…` intent.
     *
     * The code is only pre-filled on the pairing screen — redeeming it still
     * needs an explicit confirmation, because a share link may have been
     * forwarded on to someone else.
     *
     * A `coplanly://pair` link with no `code` (e.g. the one a pairing-status
     * push notification opens — see [com.coparently.app.data.remote.firebase.CoPlanlyMessagingService])
     * still needs to land on the pairing screen, just without a prefill. Empty
     * string is used as that "link present, no code" signal rather than null,
     * because [NavGraph][com.coparently.app.presentation.navigation.NavGraph]'s
     * deep-link effect only navigates when this flow holds a non-null value;
     * null is reserved for "no pairing link is pending" so a plain app launch
     * does not force a navigation. `Screen.Pairing.routeWithCode` already
     * treats null and empty identically, so the pairing screen itself sees no
     * difference from the existing code-less navigations (e.g. the Settings
     * menu entry).
     */
    private fun readPairingCode(intent: Intent?) {
        val data = intent?.data ?: return
        if (!PairingUri.isPairingUri(data.scheme, data.host)) return
        _pendingPairingCode.value = PairingUri.extractCode(data.toString()).orEmpty()
    }

    /**
     * Extracts a guest code from a `coplanly://guest?code=…` intent.
     *
     * Deliberately a second reader rather than a `host` branch inside [readPairingCode]. The
     * cost of the two paths crossing is not a broken screen: it is a guest redeemed through
     * `acceptPairingInvitation`, which would make them a co-parent.
     *
     * Unlike the pairing link, a `coplanly://guest` link with no code is ignored. There is no
     * push notification that opens a bare one, and a guest-accept screen with an empty field
     * and no explanation of how they got there would be worse than nothing.
     */
    private fun readGuestCode(intent: Intent?) {
        val data = intent?.data ?: return
        if (!GuestInviteUri.isGuestUri(data.scheme, data.host)) return
        _pendingGuestCode.value = GuestInviteUri.extractCode(data.toString()) ?: return
    }

    /**
     * Recognises a `coplanly://chat` intent (opened by a chat-message push
     * notification — see [com.coparently.app.data.remote.firebase.CoPlanlyMessagingService])
     * and arms [pendingChatOpen] so [NavGraph] navigates to the specific thread, or the
     * Chat tab's list when the link carries no `conversationId` (a manual test push, an
     * older payload, or a hand-typed link).
     */
    private fun readChatDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (!ChatUri.isChatUri(data.scheme, data.host)) return
        _pendingChatLink.value = PendingChatLink(ChatUri.extractConversationId(data.toString()))
    }
}
