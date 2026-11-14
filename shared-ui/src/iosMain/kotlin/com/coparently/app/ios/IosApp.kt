package com.coparently.app.ios

import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.session.clearPreviousInstall
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.di.koin.dataModules
import com.coparently.app.di.koin.platformDataModule
import com.coparently.app.di.koin.platformUiModule
import com.coparently.app.di.koin.uiModules
import com.coparently.app.ios.shell.NativeShell
import com.coparently.app.presentation.AppLaunch
import com.coparently.app.presentation.LaunchLink
import com.coparently.app.presentation.LaunchLinks
import com.coparently.app.presentation.navigation.PendingChatLink
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.CredentialSignIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
 * The root is the native shell (Task 8): UIKit's tab and navigation bars around the shared screens,
 * each screen its own Compose view controller ([NativeShell]). A `coplanly://` link the app is
 * opened with reaches [openUrl], which arms it for the shell (part B, Task 10).
 */
object IosApp {
    /** The links waiting for the shell to open them, filled by [openUrl]. */
    private val pendingLinks = IosLaunch()

    /** Where a link's family switch runs, for the life of the process. */
    private val scope = MainScope()

    /** Set by [start]; what the shell's platform values are built from. */
    private var buildValues: AppBuildValues? = null

    /** Set by [start]: the graph the shell resolves its ViewModels from. */
    private var koin: Koin? = null

    /** Set by [start]: the credential sheets every screen is drawn with. */
    private var credentialSignIn: CredentialSignIn? = null

    /** The shell, built by the first [rootViewController]. */
    private var shell: NativeShell? = null

    /**
     * Starts Firebase ([IosFirebase]), clears what an earlier install left in the Keychain on the
     * first launch after an install (`clearPreviousInstall`: the sealed preferences and Firebase
     * Auth's session, which an uninstall does not delete on the iPhone), then starts the Koin graph
     * the shared code reads: the data layer's and the UI's modules with the iPhone's platform
     * halves, and [iosAppModule]. Then it starts what runs for the whole session ([startSession]).
     * All before the first screen.
     *
     * @param google Google's sign-in sheet, which the app's Swift side runs (`GoogleSignInBridge`);
     *   the sign-in screen offers it only where it is available ([IosCredentialSignIn]).
     * @throws IllegalStateException when called a second time.
     */
    @Throws(Throwable::class)
    fun start(config: IosAppConfig, google: GoogleIdTokenSource) {
        check(buildValues == null) { "IosApp.start runs once per process" }
        IosFirebase.configure(config)
        // After Firebase, which the sign-out needs; before Koin builds the preference store.
        clearPreviousInstall()
        val koin = startKoin {
            modules(dataModules + platformDataModule + uiModules + platformUiModule + iosAppModule)
        }.koin
        startSession(koin)
        this.koin = koin
        credentialSignIn = IosCredentialSignIn(google)
        buildValues = AppBuildValues(
            versionName = config.versionName,
            privacyPolicyUrl = config.privacyPolicyUrl,
            termsUrl = config.termsUrl,
            supportEmail = config.supportEmail,
            pairingWebLink = PAIRING_WEB_LINK,
            // The EduPage import is not offered on the iPhone yet (as `platformUiModule` binds it).
            edupageImport = false,
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
     * The window's root: the native shell, which shows the screens before the app proper or the
     * four tabs. Call after [start]; every call answers the one shell.
     *
     * @throws IllegalStateException when [start] has not run.
     */
    fun rootViewController(): UIViewController {
        val values = checkNotNull(buildValues) { "IosApp.start must run before rootViewController" }
        val current = shell ?: NativeShell(
            checkNotNull(koin),
            values,
            checkNotNull(credentialSignIn),
            pendingLinks.launch
        ).also {
            shell = it
            it.start()
        }
        return current.root
    }

    /**
     * Opens a link the app was handed (the scene's `openURLContexts` and its connection options,
     * after Google's sign-in has had its turn): a `coplanly://pair`, `guest` or `chat` link
     * ([LaunchLinks]), armed for the shell, which opens it once the tabs show a signed-in account.
     *
     * A chat link that names a family switches to it first, and is armed only once the switch has
     * landed (CLAUDE.md item 15), as Android's `MainActivity.readLaunchIntent` does: a thread opened
     * while the other family still shows would be read against the wrong co-parent.
     * [SelectedFamilySource.select] refuses a family the account is not in, so such a link opens on
     * the family already showing; a switch that fails does too.
     *
     * @return whether [url] was one of the app's links; false leaves it to nobody.
     */
    fun openUrl(url: String): Boolean {
        val link = LaunchLinks.parse(url) ?: return false
        val familyId = (link as? LaunchLink.Chat)?.familyId
        val families = koin?.get<SelectedFamilySource>()
        if (familyId == null || families == null) {
            pendingLinks.arm(link)
            return true
        }
        scope.launch {
            runCatching {
                if (families.selected()?.familyId != familyId) families.select(familyId)
            }.onFailure {
                logger.w(TAG, "Could not switch to the family a link named", it)
            }
            pendingLinks.arm(link)
        }
        return true
    }

    private const val TAG = "IosApp"

    /**
     * Which pairing link an invitation carries: the hosted `web/pair/` page, as the Android build's
     * `publishedPairingWebLink` says. Nothing on the iPhone shares a link yet (no share sheet).
     */
    private const val PAIRING_WEB_LINK = true
}

/**
 * The iPhone app's own definitions, beside the shared modules. Empty so far: the sign-in (Task 5)
 * and the sync, push queue and wordings (Task 6) went into the platform modules, where the shared
 * graph asks for them, the native shell (Task 8) resolves what it needs itself, and Google's sheet
 * (Task 9) is handed to [IosApp.start] and reaches the screens through the shell.
 */
internal val iosAppModule: Module = module { }

/**
 * What a launch hands the shell on the iPhone: the pending holders `MainActivity` keeps on Android,
 * in the same shape and with the same meaning ([AppLaunch]). [IosApp.openUrl] fills them ([arm]),
 * and the shell clears each once it has opened it. A push's screen is never armed yet: the iPhone
 * receives no push until phase 4.
 */
internal class IosLaunch {
    /** A pairing code; empty for a link with none, as on Android. */
    private val pairing = MutableStateFlow<String?>(null)
    private val guest = MutableStateFlow<String?>(null)
    private val chat = MutableStateFlow<PendingChatLink?>(null)
    private val destination = MutableStateFlow<PushDestination?>(null)

    /** The holders, as the shell reads and clears them. */
    val launch: AppLaunch = AppLaunch(
        inviteCodes = PendingInviteCodes(
            pairing = pairing,
            onPairingConsumed = { pairing.value = null },
            guest = guest,
            onGuestConsumed = { guest.value = null },
        ),
        chatOpen = PendingChatOpen(link = chat, onConsumed = { chat.value = null }),
        destinationOpen = PendingDestinationOpen(destination = destination, onConsumed = { destination.value = null }),
    )

    /** Arms [link]'s hand-off, replacing one of its kind still waiting. */
    fun arm(link: LaunchLink) {
        when (link) {
            is LaunchLink.Pair -> pairing.value = link.code
            is LaunchLink.Guest -> guest.value = link.code
            is LaunchLink.Chat -> chat.value = PendingChatLink(link.conversationId)
        }
    }
}
