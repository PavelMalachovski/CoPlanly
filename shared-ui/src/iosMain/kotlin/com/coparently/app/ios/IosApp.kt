package com.coparently.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.session.clearPreviousInstall
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.di.koin.dataModules
import com.coparently.app.di.koin.platformDataModule
import com.coparently.app.di.koin.platformUiModule
import com.coparently.app.di.koin.uiModules
import com.coparently.app.presentation.AppLaunch
import com.coparently.app.presentation.CoPlanlyApp
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.ProvideIosShell
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * What the iPhone app's own build hands the shared code at launch (MON-24 phase 3), read by its
 * `AppDelegate` from the environment and the app's `Info.plist`.
 *
 * @property emulatorHost Where the Firebase emulators run, or null for the real project from the
 *   bundled `GoogleService-Info.plist` ([IosFirebase]).
 * @property versionName The app's version, as Settings shows it.
 * @property privacyPolicyUrl The hosted privacy policy, or null to hide its links.
 * @property termsUrl The hosted terms of service, or null to hide their links.
 * @property supportEmail The chat's report address, or null to hide the report action.
 */
data class IosAppConfig(
    val emulatorHost: String?,
    val versionName: String,
    val privacyPolicyUrl: String?,
    val termsUrl: String?,
    val supportEmail: String?,
)

/**
 * The iPhone app's entry point into the shared code (MON-24 phase 3), which its `AppDelegate`
 * calls: [start] once, then [rootViewController] for the window.
 *
 * Task 4's skeleton: one Compose view controller drawing [CoPlanlyApp], as `MainActivity` does on
 * Android, inside [ProvideIosShell]'s stand-ins. The native shell (Task 8) replaces the root.
 */
object IosApp {
    /** Set by [start]; what the shell's platform values are built from. */
    private var buildValues: AppBuildValues? = null

    /**
     * Starts Firebase ([IosFirebase]), clears what an earlier install left in the Keychain on the
     * first launch after an install (`clearPreviousInstall`: the sealed preferences and Firebase
     * Auth's session, which an uninstall does not delete on the iPhone), then starts the Koin graph
     * the shared code reads: the data layer's and the UI's modules with the iPhone's platform
     * halves, and [iosAppModule]. Then it starts what runs for the whole session ([startSession]).
     * All before the first screen.
     *
     * @throws IllegalStateException when called a second time.
     */
    @Throws(Throwable::class)
    fun start(config: IosAppConfig) {
        check(buildValues == null) { "IosApp.start runs once per process" }
        IosFirebase.configure(config)
        // After Firebase, which the sign-out needs; before Koin builds the preference store.
        clearPreviousInstall()
        val koin = startKoin {
            modules(dataModules + platformDataModule + uiModules + platformUiModule + iosAppModule)
        }.koin
        startSession(koin)
        buildValues = AppBuildValues(
            versionName = config.versionName,
            privacyPolicyUrl = config.privacyPolicyUrl,
            termsUrl = config.termsUrl,
            supportEmail = config.supportEmail,
            pairingWebLink = PAIRING_WEB_LINK,
        )
    }

    /**
     * What Android's `CoPlanlyApplication.onCreate` starts once the graph is up, on the iPhone (Task
     * 6), and in its order: the chat mirror, the signed-in account's profile, a sync now and one on
     * every return to the foreground ([IosLifecycle]). Each is harmless while nobody is signed in,
     * as on Android: the mirror waits for a co-parent, the profile for an account, and a sync with
     * nobody signed in ends at once. Telemetry, the widget and the periodic job are not here: the
     * iPhone collects nothing, has no widget yet and no background job until phase 4.
     *
     * After the first-launch cleanup and Koin, never before: the sync's account check reads the last
     * uid and the preference store the cleanup has just made consistent.
     */
    private fun startSession(koin: Koin) {
        koin.get<ChatMirror>().start()
        koin.get<SessionProfileSynchronizer>().start()
        val sync = koin.get<SyncRequester>()
        sync.requestSyncNow()
        IosLifecycle.observe { sync.requestSyncNow() }
    }

    /**
     * The window's root: one Compose view controller with the whole app. Call after [start].
     *
     * @throws IllegalStateException when [start] has not run.
     */
    fun rootViewController(): UIViewController {
        val values = checkNotNull(buildValues) { "IosApp.start must run before rootViewController" }
        return ComposeUIViewController {
            ProvideIosShell(values) {
                CoPlanlyApp(launch = IosLaunch.none(), darkTheme = null)
            }
        }
    }

    /**
     * Which pairing link an invitation carries: the hosted `web/pair/` page, as the Android build's
     * `publishedPairingWebLink` says. Nothing on the iPhone shares a link yet (no share sheet).
     */
    private const val PAIRING_WEB_LINK = true
}

/**
 * The iPhone app's own definitions, beside the shared modules. Empty so far: the sign-in (Task 5)
 * and the sync, push queue and wordings (Task 6) went into the platform modules, where the shared
 * graph asks for them; the native shell (Task 8) and Google (Task 9) may add theirs here.
 */
internal val iosAppModule: Module = module { }

/** What a launch hands the navigation graph on the iPhone. */
internal object IosLaunch {
    /**
     * A launch with nothing pending: no invitation code, no chat link and no push's screen. The
     * iPhone reads none of those yet (links and pushes come with Tasks 6 and 8).
     */
    fun none(): AppLaunch = AppLaunch(
        inviteCodes = PendingInviteCodes(
            pairing = MutableStateFlow(null),
            onPairingConsumed = {},
            guest = MutableStateFlow(null),
            onGuestConsumed = {},
        ),
        chatOpen = PendingChatOpen(link = MutableStateFlow(null), onConsumed = {}),
        destinationOpen = PendingDestinationOpen(destination = MutableStateFlow(null), onConsumed = {}),
    )
}
