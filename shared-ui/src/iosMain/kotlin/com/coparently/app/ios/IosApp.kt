package com.coparently.app.ios

import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.notification.IosLocalReminders
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.AppleTokens
import com.coparently.app.data.remote.firebase.IosPushRegistration
import com.coparently.app.data.remote.firebase.IosPushToken
import com.coparently.app.data.remote.firebase.NotificationPermission
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.session.SessionEndingHook
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.session.SignInProviderSession
import com.coparently.app.data.session.clearPreviousInstall
import com.coparently.app.data.sync.ForegroundSyncRunner
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.di.koin.dataModules
import com.coparently.app.di.koin.platformDataModule
import com.coparently.app.di.koin.platformUiModule
import com.coparently.app.di.koin.uiModules
import com.coparently.app.ios.shell.NativeShell
import com.coparently.app.presentation.AppLaunch
import com.coparently.app.presentation.LaunchLink
import com.coparently.app.presentation.LaunchLinks
import com.coparently.app.presentation.PushTap
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.navigation.CalendarTabTaps
import com.coparently.app.presentation.navigation.PendingChatLink
import com.coparently.app.presentation.navigation.PendingChatOpen
import com.coparently.app.presentation.navigation.PendingDestinationOpen
import com.coparently.app.presentation.navigation.PendingInviteCodes
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.widget.TodayWidgetNames
import com.coparently.app.utils.readerLanguageTag
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSLog
import platform.UIKit.UIViewController
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

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
 * opened with reaches [openUrl], which arms it for the shell (part B, Task 10), and a tapped push
 * reaches [openPush] (phase 4 part C).
 */
object IosApp {
    /** The links waiting for the shell to open them, filled by [openUrl]. */
    private val pendingLinks = IosLaunch()

    /** Where a link's family switch runs, for the life of the process. */
    private val scope = MainScope()

    /** Claimed first thing by [start], so a second call runs nothing. */
    private val started = StartOnce("IosApp.start runs once per process")

    /** Set by [start]; what the shell's platform values are built from. */
    private var buildValues: AppBuildValues? = null

    /** Set by [start]: the graph the shell resolves its ViewModels from. */
    private var koin: Koin? = null

    /** Set by [start]: the credential sheets every screen is drawn with. */
    private var credentialSignIn: CredentialSignIn? = null

    /** Set by [start]: Google's sheet, which also connects Google Calendar (the shell's sign-in). */
    private var google: GoogleIdTokenSource? = null

    /** The shell, built by the first [rootViewController]. */
    private var shell: NativeShell? = null

    /** A push tapped before [start] has finished, opened once it has ([openPush]). */
    private var pendingPush: Map<String, String>? = null

    /**
     * Starts Firebase ([IosFirebase]), clears what an earlier install left in the Keychain on the
     * first launch after an install (`clearPreviousInstall`: the sealed preferences, Firebase Auth's
     * session and Google Sign-In's, which an uninstall does not delete on the iPhone), then starts
     * the Koin graph the shared code reads: the data layer's and the UI's modules with the iPhone's
     * platform halves, and [iosAppModule]. Then it starts what runs for the whole session
     * ([startSession]). All before the first screen.
     *
     * The once-per-process guard is claimed before any of it, so a second call changes nothing,
     * even after a first call that failed part-way: Firebase and Koin refuse to start twice.
     *
     * @param google Google's sign-in sheet, which the app's Swift side runs (`GoogleSignInBridge`);
     *   the sign-in screen offers it only where it is available ([IosCredentialSignIn]), Settings
     *   connects Google Calendar through it where the build names the web client
     *   (`IosGoogleCalendarSignIn`), and its session ends with ours ([SignInProviderSession]).
     * @param apple Apple's sign-in sheet, which the app's Swift side runs (`AppleSignInBridge`); the
     *   sign-in screen and the sign-in methods offer it only while the bundle's `CoplanlyAppleSignIn`
     *   flag is on ([IosCredentialSignIn.appleAvailable]).
     * @param pushToken This iPhone's Firebase Messaging token, which the app's Swift side holds
     *   (`PushTokenBridge`): what the push registration keeps on the profile and detaches at
     *   sign-out (phase 4 part C).
     * @param widgetReload Asks WidgetKit to draw the Today widget again, which the app's Swift side
     *   implements (`WidgetReloadBridge`): what the widget's snapshot writer calls once it has written
     *   a snapshot that changed (phase 4 part D).
     * @throws IllegalStateException when called a second time.
     */
    @Throws(Throwable::class)
    fun start(
        config: IosAppConfig,
        google: GoogleIdTokenSource,
        apple: AppleIdSource,
        pushToken: IosPushToken,
        widgetReload: WidgetReload,
    ) {
        started.claim()
        reportUncaughtExceptions()
        IosFirebase.configure(config)
        // After Firebase, which the sign-out needs; before Koin builds the preference store.
        clearPreviousInstall(google.session())
        val koin = startKoin {
            modules(
                dataModules + platformDataModule + uiModules + platformUiModule +
                    iosAppModule(google, pushToken, widgetReload, appGroupWidgetDirectory())
            )
        }.koin
        startSession(koin)
        this.koin = koin
        this.google = google
        val appleSignIn = bundleAppleSignIn()
        credentialSignIn = IosCredentialSignIn(
            google = google,
            apple = apple,
            appleSignIn = appleSignIn,
            appleAccount = GitLiveAppleAccount(Firebase.auth, koin.get<AppleTokens>()),
        )
        buildValues = AppBuildValues(
            versionName = config.versionName,
            privacyPolicyUrl = config.privacyPolicyUrl,
            termsUrl = config.termsUrl,
            supportEmail = config.supportEmail,
            pairingWebLink = PAIRING_WEB_LINK,
            // The EduPage import is not offered on the iPhone yet (as `platformUiModule` binds it).
            edupageImport = false,
            appleSignIn = appleSignIn,
        )
        // Defensive: `AppDelegate` sets the notification delegate only after this returns, so a tap
        // that launched the app arrives after it; one that came sooner would have been held here.
        pendingPush?.let { data ->
            pendingPush = null
            openPush(data)
        }
    }

    /**
     * What Android's `CoPlanlyApplication.onCreate` starts once the graph is up, on the iPhone (Task
     * 6), and in its order: the chat mirror, the signed-in account's profile, a sync now and one on
     * every return to the foreground ([IosLifecycle]). Since phase 4 part C also the signed-in account
     * shared with the notification extension ([AppGroupShare]: on every change of the uid, and again
     * on the foreground) and the notification permission read without asking
     * ([NotificationPermission.refresh], on start and on the foreground, where the Settings switch
     * and the push registration read it), and the push registration is told of every change of
     * the uid ([IosPushRegistration.sessionChanged]), as the local reminders are planned again
     * ([IosLocalReminders.requestRearm]). Each is harmless while nobody is signed in,
     * as on Android: the mirror waits for a co-parent, the profile for an account, and a sync with
     * nobody signed in ends at once. Telemetry and the periodic job are not here: the iPhone collects
     * nothing and has no background job until phase 4. Since phase 4 part D the Today widget's
     * snapshot is written on every trigger `followWidget` lists, and on every return to the
     * foreground.
     *
     * After the first-launch cleanup and Koin, never before: the sync's account check reads the last
     * uid and the preference store the cleanup has just made consistent.
     */
    private fun startSession(koin: Koin) {
        koin.get<ChatMirror>().start()
        koin.get<SessionProfileSynchronizer>().start()
        val sync = koin.get<SyncRequester>()
        sync.requestSyncNow()
        val signedIn = koin.get<SignedInUser>()
        val permission = koin.get<NotificationPermission>()
        scope.launch { shareSignedInAccount(signedIn.observeCurrentUid()) { readerLanguageTag() } }
        // A sign-out's detach holds until the uid changes (a token refreshed mid-sign-out is not kept).
        val registration = koin.get<IosPushRegistration>()
        scope.launch { signedIn.observeCurrentUid().collect { registration.sessionChanged(it) } }
        // The local reminders are planned on start and again for every account that signs in or out
        // (phase 4 part C, Task 7): signed out, none stays armed; the sync's re-arm follows each sync.
        val reminders = koin.get<IosLocalReminders>()
        scope.launch { signedIn.observeCurrentUid().collect { reminders.requestRearm() } }
        scope.launch { permission.refresh() }
        val widget = koin.getOrNull<WidgetSnapshotWriter>()?.let { followWidget(scope, koin, it, sync, signedIn) }
        IosLifecycle.observe {
            sync.requestSyncNow()
            scope.launch { permission.refresh() }
            val uid = signedIn.getCurrentUid()
            AppGroupShare.write(uid, uid?.let { readerLanguageTag() })
            // A language changed in the phone's Settings shows here, as for the App Group's key.
            widget?.invoke()
        }
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
            checkNotNull(google),
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
     * [SelectedFamilySource.select] defers a family this phone does not know yet (it remembers the
     * request for a minute and applies it once the family arrives, CLAUDE.md item 39), so such a
     * link opens on the family already showing until then; a family the account is not in never
     * arrives, and a switch that fails opens on the family showing too.
     *
     * @return whether [url] was one of the app's links; false leaves it to nobody.
     */
    fun openUrl(url: String): Boolean {
        val link = LaunchLinks.parse(url) ?: return false
        val families = koin?.get<SelectedFamilySource>()
        if ((link as? LaunchLink.Chat)?.familyId == null || families == null) {
            pendingLinks.arm(link)
        } else {
            scope.launch { openLink(link, { familyId -> families.showFamily(familyId) }, pendingLinks::arm) }
        }
        return true
    }

    /**
     * Opens what a tapped notification names (phase 4 part C, spec §7.2), from the push's data
     * (`userInfo`, its string keys and values), as Android's `MainActivity.readLaunchIntent` reads
     * the intent `PushNotifier` built ([LaunchLinks.forPush]): nothing when nobody is signed in or
     * the push names another account (CLAUDE.md item 23: the server's neutral alert of a push for
     * the account that signed out still shows, and opens nothing); otherwise the family it names is
     * switched to **first**, and only then is its link or screen armed for the shell (CLAUDE.md item
     * 15), with [openTap]. A family this phone does not know yet is deferred by
     * [SelectedFamilySource.select], as for a chat link ([openUrl]).
     *
     * A push tapped before [start] has finished is held and opened at its end, as the shell holds a
     * link until the tabs show. Defensive: `AppDelegate` sets the notification delegate after
     * [start] returns, so even a tap that launched the app arrives once the graph is up.
     */
    fun openPush(data: Map<String, String>) {
        val graph = koin
        if (graph == null) {
            pendingPush = data
            return
        }
        val tap = LaunchLinks.forPush(data, graph.get<SignedInUser>().getCurrentUid())
        if (tap == null) {
            logger.i(TAG, "A tapped push for an account not signed in here opens nothing")
            return
        }
        val families = graph.get<SelectedFamilySource>()
        scope.launch {
            openTap(tap, { familyId -> families.showFamily(familyId) }, pendingLinks::arm, pendingLinks::armDestination)
        }
    }

    /**
     * A new or refreshed Firebase Messaging token (the Swift side's `PushTokenBridge`, as Android's
     * `onNewToken`): kept on the signed-in profile through the push device, which writes nothing
     * while pushes are off or nobody is signed in. Before [start] there is no graph, and the next
     * sync keeps the token instead.
     */
    fun pushTokenChanged(token: String) {
        val device = koin?.get<PushDevice>() ?: return
        scope.launch {
            device.updateUserToken(token).onFailure { logger.w(TAG, "Could not keep the push token", it) }
        }
    }

    private const val TAG = "IosApp"

    /** The longest line [reportUncaughtExceptions] writes; the system log keeps about a kilobyte. */
    private const val LOG_LINE_LIMIT = 800

    /**
     * Writes an exception nothing caught to the system log (`NSLog`) before Kotlin/Native ends the
     * process, as it would anyway: the shared logger prints to standard output, which a simulator's
     * system log and a phone's crash report do not keep, so a crash otherwise leaves only native
     * frames to read. It then ends the process as Kotlin/Native would without a hook: a hook that
     * returned would keep the app running past the failure.
     */
    @OptIn(ExperimentalNativeApi::class)
    private fun reportUncaughtExceptions() {
        setUnhandledExceptionHook { failure ->
            // The text is the format itself, its percent signs doubled: a Kotlin string handed to
            // NSLog's variadic arguments is no NSString, and `%@` over one crashed the process. One
            // line at a time, since the system log may cut a long one.
            "CoPlanly uncaught exception: ${failure.stackTraceToString()}".lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line -> NSLog(line.take(LOG_LINE_LIMIT).replace("%", "%%")) }
            terminateWithUnhandledException(failure)
        }
    }

    /**
     * [openUrl]'s order, apart for its test: a chat link that names a family is armed only once
     * [switchTo] has returned for that family (or failed, which is logged); any other link is armed
     * at once.
     */
    internal suspend fun openLink(link: LaunchLink, switchTo: suspend (String) -> Unit, arm: (LaunchLink) -> Unit) {
        val familyId = (link as? LaunchLink.Chat)?.familyId
        openTap(PushTap(familyId = familyId, link = link, destination = null), switchTo, arm) {}
    }

    /**
     * [openPush]'s order, apart for its test, and [openLink]'s: the family [tap] names is switched to
     * first ([switchTo] has returned, or failed, which is logged, and the tap then opens on the
     * family showing, as on Android), and only then is its link [arm]ed and its screen
     * [armDestination]ed.
     */
    internal suspend fun openTap(
        tap: PushTap,
        switchTo: suspend (String) -> Unit,
        arm: (LaunchLink) -> Unit,
        armDestination: (PushDestination) -> Unit,
    ) {
        val familyId = tap.familyId
        if (familyId != null) {
            try {
                switchTo(familyId)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                logger.w(TAG, "Could not switch to the family a link or a push named", e)
            }
        }
        tap.link?.let(arm)
        tap.destination?.let(armDestination)
    }

    /** Shows [familyId] unless it already shows. */
    private suspend fun SelectedFamilySource.showFamily(familyId: String) {
        if (selected()?.familyId != familyId) select(familyId)
    }

    /**
     * Which pairing link an invitation carries: the hosted `web/pair/` page, as the Android build's
     * `publishedPairingWebLink` says. Nothing on the iPhone shares a link yet (no share sheet).
     */
    private const val PAIRING_WEB_LINK = true
}

/**
 * Writes the Today widget's snapshot ([writer]) whenever something it draws may have changed
 * (phase 4 part D, spec §7.2), from `IosApp.startSession`, in its [scope]: the iPhone's twin of
 * Android's `TodayWidgetRefresher`:
 * - after every sync run, success or failure ([ForegroundSyncRunner.completedRuns]);
 * - on a Room write to the tables the day is computed from (`events`, `custody_models`, and
 *   `users`, whose row says whose family is on screen), debounced, as a sync writes in bursts;
 * - on every change of the signed-in uid; a sign-out (null) writes the signed-out snapshot and
 *   forgets the remembered names (the sign-out itself wrote it already, before the session
 *   ended, through [widgetSessionEnding]);
 * - on a change of the family on screen ([FamilyViewSource]);
 * - when [ParentsSource] loads names that differ from the remembered ones, which this one
 *   collector hands to [TodayWidgetNames.remember] (it has no lock: one writer, on the main
 *   thread);
 * - on every return to the foreground, through the function returned, which `IosApp`'s
 *   foreground block calls.
 *
 * Every ask goes through one conflated channel and one consumer, so two triggers never write
 * at once and a burst of them writes once more at most; the writer's own lock covers the
 * sign-out's direct write.
 *
 * @return what asks for a write.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private fun followWidget(
    scope: CoroutineScope,
    koin: Koin,
    writer: WidgetSnapshotWriter,
    sync: SyncRequester,
    signedIn: SignedInUser,
): () -> Unit {
    val requests = Channel<Unit>(Channel.CONFLATED)
    scope.launch { requests.consumeEach { writeQuietly { writer.write() } } }
    val ask: () -> Unit = { requests.trySend(Unit) }
    // The runner is the iPhone's `SyncRequester` (`platformDataModule`); anything else runs no
    // sync of this kind, and the Room trigger still follows what a sync writes.
    (sync as? ForegroundSyncRunner)?.let { runner -> scope.launch { runner.completedRuns.collect { ask() } } }
    val database = koin.get<CoPlanlyDatabase>()
    scope.launch {
        database.invalidationTracker.createFlow(*WIDGET_TABLES, emitInitialState = false)
            .debounce(WIDGET_DEBOUNCE_MS)
            // The database could not be opened (a locked phone, say): the other triggers still
            // write, as Android's refresher still redraws at midnight.
            .catch { logger.w(WIDGET_TAG, "The Today widget stopped following Room", it) }
            .collect { ask() }
    }
    val names = koin.get<TodayWidgetNames>()
    scope.launch {
        signedIn.observeCurrentUid().distinctUntilChanged().collect { uid ->
            if (uid == null) {
                names.clear()
                writeQuietly { writer.writeSignedOut() }
            } else {
                ask()
            }
        }
    }
    val families = koin.get<FamilyViewSource>()
    scope.launch {
        signedIn.observeCurrentUid()
            .flatMapLatest { families.observe() }
            .map { it.onScreen }
            .distinctUntilChanged()
            .catch { logger.w(WIDGET_TAG, "The Today widget stopped following the family on screen", it) }
            .collect { ask() }
    }
    val parents = koin.get<ParentsSource>()
    scope.launch { parents.observe().collect { if (names.remember(it)) ask() } }
    return ask
}

/**
 * Runs [write], logging its failure: a snapshot that could not be built (Room unreadable while the
 * phone is locked, say) leaves the last one up until the next trigger, and must never end the
 * collector it runs in, whose failure would take the app down.
 */
private suspend fun writeQuietly(write: suspend () -> Unit) {
    runCatching { write() }.onFailure { failure ->
        if (failure is CancellationException) throw failure
        logger.w(WIDGET_TAG, "Could not write the Today widget's snapshot", failure)
    }
}

private const val WIDGET_TAG = "IosWidget"

/**
 * The tables the widget's day is computed from, Android's `TodayWidgetRefresher.WATCHED_TABLES`:
 * events, the pattern with its swaps, and the signed-in parent's row (whose family is on screen).
 */
private val WIDGET_TABLES = arrayOf("events", "custody_models", "users")

/** Long enough to fold a sync's burst of writes into one snapshot, as on Android. */
private const val WIDGET_DEBOUNCE_MS = 2_000L

/**
 * The iPhone app's own definitions, beside the shared modules: Google Sign-In's session
 * ([SignInProviderSession]), which sign-out ends (`IosAccountTraces`, part B, Task 12). The sign-in
 * (Task 5) and the sync, push queue and wordings (Task 6) went into the platform modules, where the
 * shared graph asks for them, the native shell (Task 8) resolves what it needs itself, and Google's
 * sheet (Task 9) is handed to [IosApp.start] and reaches the screens through the shell. Since phase
 * 4 part C also the Swift side's push token ([IosPushToken]), which `platformDataModule`'s push
 * registration reads, and since part D the Today widget's snapshot writer ([WidgetSnapshotWriter],
 * in [widgetDirectory], reloading through [widgetReload]) with what runs before a session ends
 * ([widgetSessionEnding]).
 */
internal fun iosAppModule(
    google: GoogleIdTokenSource,
    pushToken: IosPushToken,
    widgetReload: WidgetReload = NoWidgetReload,
    widgetDirectory: Path? = null,
): Module = module {
    single<SignInProviderSession> { google.session() }
    single<IosPushToken> { pushToken }
    // The Today widget's snapshot (phase 4 part D), where the app has the App Group's container.
    if (widgetDirectory != null) {
        single {
            WidgetSnapshotWriter(
                directory = widgetDirectory,
                reload = widgetReload,
                sources = IosWidgetSources(get(), get(), get(), get(), get()),
                wording = IosWidgetWording,
            )
        }
    }
    single<SessionEndingHook> { widgetSessionEnding(getOrNull<WidgetSnapshotWriter>(), get()) }
}

/**
 * What runs before an account's session ends ([SessionEndingHook], from `IosAccountTraces` at
 * sign-out, deletion and an account switch): the remembered names are forgotten, on the main thread
 * where [IosApp]'s one collector remembers them, and the signed-out snapshot is written ([writer],
 * null where the app has no App Group container), so the home screen never names the departing
 * account's family once it has gone.
 */
internal fun widgetSessionEnding(writer: WidgetSnapshotWriter?, names: TodayWidgetNames): SessionEndingHook =
    SessionEndingHook {
        withContext(Dispatchers.Main) { names.clear() }
        writer?.writeSignedOut()
    }

/** A reload that asks nothing: a graph built without the Swift side, as a test's. */
private object NoWidgetReload : WidgetReload {
    override fun reloadAll() = Unit
}

/** [google]'s own session, as the data layer ends it, and its grant, as Google Calendar revokes it. */
internal fun GoogleIdTokenSource.session(): SignInProviderSession {
    val google = this
    return object : SignInProviderSession {
        override fun signOut() = google.signOut()

        override fun disconnect() = google.disconnect()
    }
}

/**
 * A guard that lets one caller through and refuses every later one, claimed before the guarded work
 * so a first run that fails part-way still refuses the next ([IosApp.start]).
 */
internal class StartOnce(private val refusal: String) {
    private var claimed = false

    /** Lets the first call through; every later one throws [IllegalStateException] with [refusal]. */
    fun claim() {
        check(!claimed) { refusal }
        claimed = true
    }
}

/**
 * What a launch hands the shell on the iPhone: the pending holders `MainActivity` keeps on Android,
 * in the same shape and with the same meaning ([AppLaunch]). [IosApp.openUrl] and [IosApp.openPush]
 * fill them ([arm], [armDestination]), and the shell clears each once it has opened it.
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
            // The Today widget's tap (iPhone phase 4 part D): the Calendar tab, as a push names it,
            // on today, as a tap on the tab opens it (design item 5): the screen answers the count
            // when it shows, so a tap counted before the tabs are up is not lost.
            LaunchLink.Calendar -> {
                CalendarTabTaps.tap()
                destination.value = PushDestination.CALENDAR
            }
        }
    }

    /** Arms the screen a tapped push names, replacing one still waiting. */
    fun armDestination(destination: PushDestination) {
        this.destination.value = destination
    }
}
