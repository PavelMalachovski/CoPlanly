package com.coparently.app.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.notification.NotificationManager
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.remote.google.CredentialAuthenticator
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.domain.pairing.InstallReferrer
import com.coparently.app.domain.pairing.InstallReferrerSource
import com.coparently.app.domain.pairing.PairingUri
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.presentation.auth.AndroidCredentialSignIn
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.navigation.PendingChatLink
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.navigation.startDestinationFor
import com.coparently.app.presentation.platform.LocalCredentialSignIn
import com.coparently.app.presentation.platform.LocalGoogleCalendarSignIn
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.sync.AndroidGoogleCalendarSignIn
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.sync.signInAnswer
import com.coparently.app.presentation.widget.TodayWidgetRefresher
import com.coparently.app.resources.Res
import com.coparently.app.resources.sync_google_sign_in_cancelled
import com.coparently.app.resources.sync_google_sign_in_failed
import com.coparently.app.utils.AppLocale
import com.coparently.app.utils.ClockFormat
import com.coparently.app.utils.follow
import com.coparently.app.utils.inAppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

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
 *
 * It speaks the language [AppLocale] decides (the owner's rule: the per-app choice, else the
 * phone's first language when the app ships it, else English): its base context is put in that
 * locale before AppCompat's delegate sees it ([attachBaseContext]), and the process's default
 * locale follows it once AppCompat has applied a per-app choice ([onCreate], [onResume]).
 *
 * A thin shell since MON-24 phase 2 (Task 12): it reads the launch, holds the splash, hosts the
 * activity-bound sign-ins and provides the platform, and its content is `:shared-ui`'s
 * [CoPlanlyApp] — the theme, the app-wide locals and the navigation graph.
 */
class MainActivity : AppCompatActivity() {

    private val notificationManager: NotificationManager by inject()

    private val preferencesRepository: PreferencesRepository by inject()

    private val selectedFamilySource: SelectedFamilySource by inject()

    /** Google Calendar's sign-in client, and the redeeming of its answer. */
    private val credentialManagerService: CredentialManagerService by inject()

    /** Credential Manager's sheets for the app's own sign-in, hosted on this activity. */
    private val credentialAuthenticator: CredentialAuthenticator by inject()

    private val todayWidgetRefresher: TodayWidgetRefresher by inject()

    /** The pairing code an invitation's Play link carried through the install, read once. */
    private val installReferrerSource: InstallReferrerSource by inject()

    private val _darkThemeState = MutableStateFlow<Boolean?>(null)
    private val darkThemeState: StateFlow<Boolean?> = _darkThemeState

    /**
     * A pairing code carried by a `coplanly://pair` deep link, awaiting hand-off
     * to the pairing screen. `NavGraph` consumes it (setting it back to null)
     * once it has navigated there — see [readLaunchLink] for why redeeming it
     * is never automatic.
     */
    private val _pendingPairingCode = MutableStateFlow<String?>(null)
    private val pendingPairingCode: StateFlow<String?> = _pendingPairingCode

    /** The code the Play install referrer offered on this launch, if any — see [InstallReferrerSource]. */
    private var referrerCode: String? = null

    /**
     * A `coplanly://chat` deep link (opened by a chat-message push notification), awaiting
     * hand-off to the Chat tab or a specific thread — null while none is pending. `NavGraph`
     * consumes it (setting it back to null) once it has navigated there — same hand-off shape
     * as [pendingPairingCode], bundled into a single [PendingChatOpen] so `NavGraph`'s own
     * parameter count does not grow by two for every deep link it gains (see `NavGraph`'s
     * doc).
     */
    /**
     * A guest code carried by a `coplanly://guest` deep link, awaiting hand-off to the
     * guest-accept screen. Separate from [pendingPairingCode], and that is the point: the two
     * codes are indistinguishable six-character strings, so the host the link arrived on is
     * the only thing that says which callable may redeem it. Redeeming is never automatic
     * here either — the guest confirms on the screen, for the same reason
     * [readLaunchLink] gives.
     */
    private val _pendingGuestCode = MutableStateFlow<String?>(null)
    private val pendingGuestCode: StateFlow<String?> = _pendingGuestCode

    /**
     * The two invitation codes, bundled for `NavGraph` — see [PendingInviteCodes] for why they
     * travel together and why they stay distinct fields.
     */
    private val pendingInviteCodes = PendingInviteCodes(
        pairing = _pendingPairingCode,
        onPairingConsumed = {
            // The referrer is used up only when its own code reached a signed-in pairing screen.
            if (InstallReferrer.isHandOff(_pendingPairingCode.value, referrerCode)) {
                installReferrerSource.markHandedOff()
            }
            _pendingPairingCode.value = null
        },
        guest = _pendingGuestCode,
        onGuestConsumed = { _pendingGuestCode.value = null }
    )

    private val _pendingChatLink = MutableStateFlow<PendingChatLink?>(null)
    private val pendingChatOpen = PendingChatOpen(
        link = _pendingChatLink,
        onConsumed = { _pendingChatLink.value = null }
    )

    /** The screen a tapped push names (D-13), awaiting hand-off to `NavGraph`. */
    private val _pendingDestination = MutableStateFlow<PushDestination?>(null)
    private val pendingDestinationOpen = PendingDestinationOpen(
        destination = _pendingDestination,
        onConsumed = { _pendingDestination.value = null }
    )

    /**
     * Every hand-off above, as [CoPlanlyApp] takes them. [readLaunchIntent] arms them, after the
     * family switch a push names (CLAUDE.md item 15).
     */
    private val appLaunch = AppLaunch(
        inviteCodes = pendingInviteCodes,
        chatOpen = pendingChatOpen,
        destinationOpen = pendingDestinationOpen
    )

    /**
     * Google Calendar's sign-in answer goes here. The same instance [CoPlanlyApp] gets from
     * `koinViewModel()` and hands Settings: both are scoped to this activity (`ViewModelScopeTest`).
     */
    private val syncViewModel: SyncViewModel by viewModel()

    /**
     * What the navigation graph decides its first screen from, read here to hold the splash until
     * that is known. The same instances `NavGraph` gets from `koinViewModel()`: both are scoped to
     * this activity.
     */
    private val authStateViewModel: AuthStateViewModel by viewModel()
    private val telemetryConsentViewModel: TelemetryConsentViewModel by viewModel()

    // Google Sign-In Activity Result launcher for sync
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        // Google's sign-in answers a refusal, and the parent backing out, with RESULT_CANCELED and
        // the status inside the intent. Reading only RESULT_OK told a parent whose build Google
        // refused that they had cancelled; the intent's status decides now, when there is one.
        if (data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            lifecycleScope.launch {
                syncViewModel.handleSignInResult(credentialManagerService.signInAnswer(task))
            }
        } else {
            val isCanceled = result.resultCode == RESULT_CANCELED
            val message = UiText.Res(
                if (isCanceled) {
                    Res.string.sync_google_sign_in_cancelled
                } else {
                    Res.string.sync_google_sign_in_failed
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
        // Again here: the framework resets the default locale on a configuration change, and one
        // it counts as no change at all reaches no callback that would set it back.
        AppLocale.follow(this)
        ClockFormat.follow(this)
    }

    /**
     * Gives the activity its base context in the language the app speaks ([AppLocale]), before
     * AppCompat's delegate wraps it. AppCompat keeps a locale it finds on the base context as part
     * of its configuration overlay, applies night mode beside it, and puts a per-app choice below
     * Android 13 on top, so a choice still wins.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inAppLanguage())
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
        // After AppCompat has applied a per-app choice (and set the default locale list itself)
        // and before anything is composed: Compose's Locale.current, and so Compose
        // Multiplatform's strings, java.time and number formats, follow the process's default,
        // which this makes the language the app speaks. Below Android 13 the choice is mirrored
        // for the pushes and workers of a process no activity has started.
        AppLocale.rememberChoice(this)
        AppLocale.follow(this)
        // Before the first frame, so no time is ever drawn in the language's format first.
        ClockFormat.follow(this)

        val splashDeadline = SystemClock.uptimeMillis() + SPLASH_HOLD_MAX_MS
        splashScreen.setKeepOnScreenCondition {
            firstScreenUnknown() && SystemClock.uptimeMillis() < splashDeadline
        }

        // While the app is on screen, the Today widget learns the parents' names from the same
        // source every screen uses; the widget itself cannot reach the co-parent's. And its lines
        // follow a per-app language chosen below Android 13, which recreates this activity and
        // reaches no other callback.
        todayWidgetRefresher.followParents(this)
        todayWidgetRefresher.followLanguage()

        // Enable edge-to-edge display for modern Android UI
        // This makes the app draw behind the system bars
        enableEdgeToEdge()

        // Notification permission is requested contextually (Settings push toggle,
        // event reminder selection) instead of on every cold start — see
        // :shared-ui's PermissionRequesters.kt.

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

        // An invited parent who installed from the invitation's Play link finds the code waiting:
        // it opens the pairing screen once they are signed in, as a deep link would. Only into
        // an empty slot — a deep link the parent tapped wins — and only ever as a prefill. Read on
        // every creation until it is handed off, because the slot does not survive a recreation
        // or a process death before sign-in.
        lifecycleScope.launch {
            installReferrerSource.pendingPairingCode()?.let { code ->
                referrerCode = code
                if (_pendingPairingCode.value == null) _pendingPairingCode.value = code
            }
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

            // Google's sign-in for Google Calendar (`presentation/platform/GoogleCalendarSignIn`),
            // launched through this activity's result, whose answer reaches [syncViewModel].
            val googleCalendarSignIn = remember(googleSignInLauncher) {
                AndroidGoogleCalendarSignIn(credentialManagerService) { intent -> googleSignInLauncher.launch(intent) }
            }
            // The sign-in screen's credential sheets (`presentation/platform/CredentialSignIn`), on
            // this activity, which Credential Manager hosts them on; the ViewModel keeps none of it.
            val credentialSignIn = remember { AndroidCredentialSignIn(this@MainActivity, credentialAuthenticator) }

            // Every platform capability the shared screens read (`presentation/platform/`, MON-24
            // phase 2, Task 11), from this activity, and the two sign-ins only an activity can
            // host; then the app itself, from `:shared-ui` (Task 12).
            ProvideAndroidPlatform {
                CompositionLocalProvider(
                    LocalGoogleCalendarSignIn provides googleCalendarSignIn,
                    LocalCredentialSignIn provides credentialSignIn
                ) {
                    CoPlanlyApp(launch = appLaunch, darkTheme = darkTheme)
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
     * switch has to land **before** the links are armed: `NavGraph` navigates the moment a
     * pending link appears, and a chat thread, a proposal or an event opened while the device
     * still shows the other family would be read against the wrong co-parent. So with a family
     * to switch to, the links are armed from the coroutine that switched; without one they are
     * armed at once, exactly as before.
     *
     * [SelectedFamilySource.select] refuses a family the signed-in account is not in, so a
     * stale notification — from a family since left, or for an account since signed out — opens
     * on whatever is showing rather than blanking the co-parent every screen reads. A family this
     * device has not learnt of yet — the `pairing_accepted` push of a second family, tapped before
     * the pairing listener or a sync brought it in — is switched to a moment later, by the
     * reconcile that brings it; the links are armed at once all the same. The extra is
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
        readLaunchLink(intent)
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
     * Reads the link [intent] opened the app with and arms the hand-off it names: a `coplanly://`
     * link through [LaunchLinks.parse], which the iPhone's shell reads too, else the pairing link's
     * web form ([readWebPairingLink]).
     *
     * - `coplanly://pair?code=…`: the code is only pre-filled on the pairing screen — redeeming it
     *   still needs an explicit confirmation, because a share link may have been forwarded on to
     *   someone else. A link with no code (the one a pairing-status push opens, see
     *   [com.coparently.app.data.remote.firebase.CoPlanlyMessagingService]) still lands on the
     *   pairing screen, without a prefill: the empty string is that "link present, no code" signal,
     *   because `NavGraph`'s deep-link effect only navigates when the flow holds a non-null value,
     *   and null is reserved for "no pairing link is pending" so a plain launch forces no
     *   navigation. `Screen.Pairing.routeWithCode` treats null and empty alike.
     * - `coplanly://guest?code=…`: a separate hand-off from the pairing code, and that is the point.
     *   The cost of the two paths crossing is a guest redeemed through `acceptPairingInvitation`,
     *   which would make them a co-parent. A guest link with no code is ignored ([LaunchLinks]
     *   answers null): no push opens a bare one, and an empty guest-accept screen with no
     *   explanation would be worse than nothing.
     * - `coplanly://chat` (opened by a chat-message push): the thread its `conversationId` names, or
     *   the Chat tab's list when it names none (a manual test push, an older payload, or a
     *   hand-typed link). The family comes from the push's extra ([readLaunchIntent]), never from
     *   the link.
     */
    private fun readLaunchLink(intent: Intent?) {
        val data = intent?.data ?: return
        when (val link = LaunchLinks.parse(data.toString())) {
            is LaunchLink.Pair -> _pendingPairingCode.value = link.code
            is LaunchLink.Guest -> _pendingGuestCode.value = link.code
            is LaunchLink.Chat -> _pendingChatLink.value = PendingChatLink(link.conversationId)
            null -> readWebPairingLink(data)
        }
    }

    /**
     * Extracts a pairing code from the pairing link's web form,
     * `https://coparently-a39c9.web.app/pair/…`, once that opens the app (see [PairingUri]),
     * pre-filling it as a `coplanly://pair` link's code is ([readLaunchLink]).
     */
    private fun readWebPairingLink(data: Uri) {
        if (!PairingUri.isPairingUri(data.scheme, data.host, data.path)) return
        _pendingPairingCode.value = PairingUri.extractCode(data.toString()).orEmpty()
    }
}
