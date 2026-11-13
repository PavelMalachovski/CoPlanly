# iPhone phase 3, part A: proof and skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An iPhone app (`app.coplanly`) that builds in CI and on the owner's Mac, signs in by e-mail and Google, shows a family's real data under native UIKit tab and navigation bars on one proven path, and has passed a write gate on the Firebase emulators before it touches the real project.

**Architecture:** Kotlin drives UIKit from `:shared-ui`'s `iosMain`: a `UITabBarController` with one `UINavigationController` per tab, each pushed screen a `ComposeUIViewController` hosting one shared destination. The shared `NavHostController` stays the source of truth; an iOS host mirrors its back stack into the UIKit stacks. Destinations become data (`AppDestination`) that Android's `NavHost` and the iOS host both consume, and a screen describes its bar (`ScreenBar`) instead of drawing one. Swift stays a thin app delegate plus Google Sign-In behind a Kotlin interface.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.9.3, JetBrains navigation-compose 2.9.2, Koin 4.1.1, GitLive firebase-kotlin-sdk 2.7.0 (firestore, functions, **auth**), Firebase iOS SDK 11.8.0 (Swift Package Manager in the app, CocoaPods `linkOnly` for the Kotlin test executable), XcodeGen, GoogleSignIn-iOS, GitHub-hosted `macos-latest`.

**Spec:** `docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` (approved 10 November 2026), with its parent `docs/superpowers/specs/2026-10-27-iphone-app-design.md`. Read both, and the CLAUDE.md sections each task names.

## Global Constraints

- **Android draws and does exactly what it does today.** The 470 screenshot baselines must not change; the full Android CI (unit, screenshots, three emulator legs, e2e, upgrade, R8 probe, lint, detekt) stays green on every task. A task that touches common code proves it on Android first.
- **Bundle identifier `app.coplanly`.** Minimum iOS **17.0**. iPhone only (`TARGETED_DEVICE_FAMILY = 1`), portrait only.
- **Firebase iOS SDK 11.8.0** exactly (GitLive 2.7.0's bindings); GitLive **2.7.0**; no other Firebase or GitLive version.
- **No `GoogleService-Info.plist` in the repository.** Without one the app runs on project `demo-coplanly` against the emulators; with one (owner's Mac) on the real project.
- **The iPhone writes nothing to the real project before Task 7's gate is green.** CI never reaches production.
- **No new server code and no rule changes.** If the gate finds a rules mismatch, stop and report it.
- **No string in a composable or a Swift file that a parent reads** without a key in all five `composeResources/values*/` files (CLAUDE.md "Localization").
- **Nothing on iPhone may lead to a stand-in** (design item 8): a row, button or switch whose capability is still `No…` stays hidden.
  *[Correction, final review: Settings' delete-account row is not hidden. `SettingsScreen` draws it unconditionally and on the iPhone it calls `NoAccountDeletion`, which fails with a message and deletes nothing; it needs an `AccountDeletion.available` flag or real deletion before the App Store (ROADMAP MON-24). The event form's Reminder row is drawn too (`NoEventReminders`).]*
- English only in the repository; KDoc on public declarations; Conventional Commits; every commit ends with the session's two attribution lines. No model identifiers anywhere.
- **No Gradle in the cloud session**: Google Maven is unreachable. Every task's verification is CI (`git push`, then read the run), plus `node tools/check-invariants.js` and `node --test tools/test/*.test.js` locally. A task is done only when its CI run is green.

## Review Focus

1. **A UIKit pop the shared controller did not ask for** (back button, edge swipe, a swipe started and cancelled, a pop of several screens by tapping the selected tab): the shared back stack must end exactly where UIKit's stack is, with no duplicate entry, no lost ViewModel, and the popped entries' ViewModels cleared. Owner: Task 8.
2. **A form sheet with edits swiped down**: it must ask "Discard changes?" and stay open on "Keep editing", as `rememberDiscardGuard` does on Android. Owner: Task 8.
3. **Reinstalling the app on the same iPhone**: the first launch after a delete must start with no sync cursor, no Google token and no school connection from the previous install. Owner: Task 1.
4. **A date or time shown after the phone changes time zone while the app runs, and on a spring-forward night**: the text must be the wall-clock value the data holds, never shifted. Owner: Task 1.
5. **Signing in as a different account on the same iPhone**: the previous account's rows, drafts and preferences must be gone before the new account's first screen. Owner: Task 5.

---

### Task 1: The iPhone half's fixes from the final review

**Files:**
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/utils/ReaderDateText.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/utils/ReaderMoneyText.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/common/ReaderDates.ios.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/common/HolidayNames.ios.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/IosPlatform.kt` (the `NoAppLanguages` comment)
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/utils/ReaderLocale.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/platform/FreshInstall.kt`
- Modify: `shared-data/src/iosMain/kotlin/com/coparently/app/data/platform/KeychainTextStore.kt` (`keychainSecurePreferences()`)
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/local/CommonMigrations.kt`
- Modify: `shared-data/src/androidMain/kotlin/com/coparently/app/di/DatabaseBuilder.kt`, `shared-data/src/iosMain/kotlin/com/coparently/app/data/local/IosDatabase.kt`
- Modify: `shared-data/build.gradle.kts` (`ios.deploymentTarget = "17.0"`, one line)
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/utils/ReaderDateTextIosTest.kt`, `shared-data/src/commonTest/kotlin/com/coparently/app/data/platform/FreshInstallTest.kt`

**Interfaces:**
- Produces: `internal fun readerLocale(): NSLocale` (iosMain, `utils/ReaderLocale.kt`); `class FreshInstall(private val marker: InstallMarker, private val store: SealedTextStore)` with `fun clearIfFirstLaunch()`; `interface InstallMarker { fun isSet(): Boolean; fun set() }`; `val COMMON_MIGRATIONS: Array<Migration>` (empty today).

- [ ] **Step 1: Write the failing iOS tests** in `ReaderDateTextIosTest` (iosTest, runs on the simulator in `shared-ios`):
  - `a date reads the same after the default time zone changes`: set `NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName("Europe/Prague")!!)`, format `LocalDate(2026, 3, 29)` with `ReaderDateText.date(d, "yMMMd")`, switch the default to `"Europe/London"`, format again; assert equal and both contain `"29"`. Restore the zone in `@AfterTest`.
  - `a wall-clock time on a spring-forward night is printed as written`: default zone `"Europe/Prague"`, `ReaderDateText.time(LocalTime(2, 30), is24Hour = true)` equals `"02:30"`.
  - `dateWithTime follows the clock it is given`: `ReaderDateText.dateWithTime(LocalDateTime(2026,1,5,18,0), "MMMd", " ", is24Hour = false)` ends with `"6:00 PM"` under locale `en_US` (pass the locale through a test seam on `readerLocale`, below).
  - `the reader's locale follows the owner's rule`: with preferred languages `["sk-SK","cs-CZ"]` → identifier `"en_150"`; `["cs-CZ"]` → `"cs_CZ"`; `["de"]` → `"de"`.
- [ ] **Step 2:** Push; the `shared-ios` job fails on these tests.
- [ ] **Step 3: Implement.**
  - `ReaderLocale.kt`: `internal fun readerLocale(): NSLocale` = `NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(AppLanguageRule.effective(null, preferredFirst())))` where `preferredFirst()` is `NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"`; iOS's own per-app language puts the app's choice first in `preferredLanguages`, which is why the per-app argument is null. Give it an `internal var preferredLanguagesForTest: List<String>?` seam. Cache by the first preferred language.
  - Every `NSLocale.currentLocale` in the five files above becomes `readerLocale()`; `IosDeviceRegion` stays on `currentLocale` (the phone's region, as Android's `AndroidDeviceRegion`).
  - One `private val utc = NSTimeZone.timeZoneWithName("UTC")!!`; the `gregorian` calendar and every `NSDateFormatter` set `timeZone = utc` (dates here are zone-less wall clocks).
  - `dateWithTime` passes its `is24Hour` to `time(...)`.
  - Correct `NoAppLanguages`' KDoc: the app follows the rule through `readerLocale()`, and iOS Settings holds the per-app choice.
  - `FreshInstall.clearIfFirstLaunch()`: if `!marker.isSet()`, `store.delete()` (a `KeychainException` is logged, never thrown), then `marker.set()`. iOS marker: `NSUserDefaults.standardUserDefaults` key `"coplanly.installed"` (user defaults are deleted with the app; the Keychain is not). `keychainSecurePreferences()` runs it before building `SealedSecurePreferences`.
  - `CommonMigrations.kt`: `val COMMON_MIGRATIONS: Array<Migration> = emptyArray()` with KDoc saying every migration from 47 on goes here (CLAUDE.md item 1); Android's builder adds it after `ALL_MIGRATIONS`, iOS's `buildCoPlanlyDatabase` adds it.
  - `FreshInstallTest` (commonTest, fakes): first launch deletes then marks; second launch touches nothing; a delete that throws still marks.
- [ ] **Step 4:** Push; `shared`, `shared-ios` and the Android jobs green (the pod cache key changes with the deployment target: one cold pod build, about 20 minutes).
- [ ] **Step 5: Commit** `fix: the iPhone's dates, locale, reinstall and migrations from the final review (MON-24)`.

### Task 2: Destinations as data

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/navigation/AppDestination.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/navigation/NavGraph.kt`, `ReadablePane.kt`, `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/CoPlanlyApp.kt`
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/navigation/AppDestinationsTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class DestinationKind { TAB_ROOT, PUSH, FULL_WIDTH_PUSH, FORM, ROOT_FLOW }
  class AppDestination(
      val route: String,
      val arguments: List<NamedNavArgument>,
      val kind: DestinationKind,
      val transitions: DestinationTransitions?,   // Android only; null = the NavHost default
      val content: @Composable (NavBackStackEntry) -> Unit,
  )
  @Composable fun rememberAppDestinations(navController: NavHostController, syncViewModel: SyncViewModel, authStateViewModel: AuthStateViewModel): List<AppDestination>
  @Composable fun AppProviders(darkTheme: Boolean?, content: @Composable () -> Unit)   // the theme and the app-wide locals CoPlanlyApp provides today
  fun interface TabSwitcher { fun select(tab: BottomNavDestination) }
  val LocalTabSwitcher: ProvidableCompositionLocal<TabSwitcher>
  ```
  `CoPlanlyApp(launch, darkTheme)` keeps its signature: it becomes `AppProviders(darkTheme) { NavGraph(...) }`.
  `ROOT_FLOW` = loading, telemetry, sign-in, onboarding (no tab bar); `FORM` = presented as a sheet on iPhone (add/edit event, add/edit expense, child, pet, journal entry, change request); `FULL_WIDTH_PUSH` = Expenses (design item 19's exception).

- [ ] **Step 1: Write the failing test** `AppDestinationsTest` (commonTest, compose-free): the routes of `AppDestinationCatalog.all` (a plain list of `route` → `kind`, the data half of the catalogue) contain exactly the 51 routes of `Screen` used today; the four `BottomNavDestination.route`s are `TAB_ROOT`; `Screen.Expenses.route` is `FULL_WIDTH_PUSH`; `AddExpense`, `EditExpense`, `AddEvent`, `EditEvent`, `EditChildInfo`, `EditPet`, `JournalEditor`, `RequestChange` are `FORM`; `Loading`, `PrivacyConsent`, `Auth`, `Onboarding` are `ROOT_FLOW`.
- [ ] **Step 2:** Push; the `shared` job fails.
- [ ] **Step 3: Implement.** Move each destination's body out of the `NavHost { }` builder into `rememberAppDestinations(...)` unchanged (same lambdas, same callbacks), keyed by the catalogue's kind. Android's `NavGraph` builds its `NavHost` from the list: `TAB_ROOT` → `composable` with the tab transitions, `PUSH`/`FORM`/`ROOT_FLOW` → `pane` with the transitions they name today, `FULL_WIDTH_PUSH` → `composable`. Extract `AppProviders` out of `CoPlanlyApp` (the theme, `Surface`, `LocalParentPalette`, `LocalFamilyView`, `LocalAppMessages`, `LocalPhotoViewerUid`) so the iOS shell (Task 8) wraps each hosted screen in the same providers without composing `NavGraph`. Every tab switch inside a destination (Home's chat tile, the push effects) calls `LocalTabSwitcher.current.select(tab)`, whose Android value `NavGraph` provides as today's `navController.navigateToTab(tab)`; the private helpers the list needs (the four effects, `rememberChatUnreadCount`) become `internal`.
- [ ] **Step 4:** Push; full Android CI green with **no screenshot or e2e change** (this is a pure move); `shared` green.
- [ ] **Step 5: Commit** `refactor: describe the destinations as data both shells read (MON-24)`.

### Task 3: A screen describes its bar

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/ScreenBar.kt`
- Modify: `home/HomeScreen.kt`, `family/FamilyScreen.kt`, `expenses/ExpenseScreen.kt`, `expenses/AddExpenseScreen.kt`, `settings/SettingsScreen.kt`
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/common/ScreenBarTest.kt`, the existing screenshot suite (unchanged images)

**Interfaces:**
- Produces:
  ```kotlin
  data class BarAction(val label: String, val icon: ImageVector, val sfSymbol: String, val onClick: () -> Unit,
      val enabled: Boolean = true, val primary: Boolean = false, val dot: Boolean = false)
  sealed interface BarLeading { data object None : BarLeading; data class Back(val label: String, val onClick: () -> Unit) : BarLeading; data class Close(val label: String, val onClick: () -> Unit) : BarLeading }
  data class ScreenBar(val title: String, val largeTitle: Boolean = false, val subtitle: String? = null,
      val leading: BarLeading = BarLeading.None, val actions: List<BarAction> = emptyList(),
      val trailingContent: (@Composable () -> Unit)? = null)   // the family switcher chip
  fun interface BarHost { fun show(bar: ScreenBar) }
  val LocalBarHost: ProvidableCompositionLocal<BarHost?>   // null = draw it here (Android)
  @Composable fun AppTopBar(bar: ScreenBar)                // draws today's TopAppBar when LocalBarHost is null; else hands the bar to the host and draws nothing
  ```
  A form's primary action (`StickyActionBar` on Android) is a `BarAction(primary = true)` handed to the host on iPhone; on Android the form keeps its `bottomBar = { StickyActionBar(...) }` and passes no primary action to `AppTopBar`.

- [ ] **Step 1: Write the failing test** `ScreenBarTest`: a `BarHost` fake receives exactly the bar `AppTopBar` was given when `LocalBarHost` provides it (compose test in `androidHostTest` is not needed; test the pure `ScreenBar` equality and a `BarHostRecorder` used by `AppTopBar` through a `rememberBarReport(bar)` helper that is pure).
- [ ] **Step 2:** Push; `shared` fails.
- [ ] **Step 3: Implement** and convert the five screens' `topBar = { TopAppBar(...) }` to `topBar = { AppTopBar(ScreenBar(...)) }` so that Android draws **the same** `TopAppBar` (title, subtitle for Home's date line, navigation icon, actions in the same order, `FamilySwitcherChip` as `trailingContent` first). SF Symbol names: gear `gearshape`, back is the system's, add `plus`, close `xmark`.
- [ ] **Step 4:** Push; the screenshot job verifies with no changed image; Android green.
- [ ] **Step 5: Commit** `refactor: five screens describe their bars (MON-24)`.

### Task 4: The Xcode project, the entry point and the iOS app job

**Files:**
- Create: `iosApp/project.yml`, `iosApp/CoPlanly/AppDelegate.swift`, `iosApp/CoPlanly/Info.plist`, `iosApp/CoPlanly/CoPlanly.entitlements`, `iosApp/CoPlanly/Assets.xcassets/` (app icon set from `app/src/main/res/mipmap-*` foreground, accent colour indigo), `iosApp/README.md`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosApp.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosFirebase.kt`
- Modify: `.github/workflows/ci.yml` (new job `ios-app`, `report.needs`), `tools/ci-changes.js` (+ `ios` output), `tools/test/ci-changes.test.js`, `.gitignore` (`iosApp/CoPlanly.xcodeproj/`, `iosApp/**/GoogleService-Info.plist`, `iosApp/build/`)
- Test: `tools/test/ci-changes.test.js`

**Interfaces:**
- Produces (Kotlin, exported to Swift through the `Shared` framework):
  ```kotlin
  object IosApp {
      @Throws(Throwable::class) fun start(config: IosAppConfig)
      fun rootViewController(): UIViewController   // Task 4: one ComposeUIViewController with CoPlanlyApp; Task 8 replaces it with the native shell
  }
  data class IosAppConfig(val emulatorHost: String?, val versionName: String, val privacyPolicyUrl: String?, val termsUrl: String?, val supportEmail: String?)
  ```
- `IosFirebase.configure(config)`: with `emulatorHost != null` call `Firebase.initialize(options = FirebaseOptions(applicationId = "1:000000000000:ios:0000000000000000", apiKey = "A-fake-key-for-the-firebase-emulators-x", projectId = "demo-coplanly", storageBucket = "demo-coplanly.appspot.com"))` and point Firestore (8080), Functions (5001, region `europe-west3`) and, from Task 5, Auth (9099) at it; otherwise `Firebase.initialize()` from the bundled plist.

- [ ] **Step 1: Write the failing test** in `ci-changes.test.js`: `iosApp/CoPlanly/AppDelegate.swift` alone → `ios=true`, `shared=false`, `android=false`, no emulator legs; a `shared-ui/src/iosMain/...` path → `ios=true`; a `shared-ui/src/commonMain/...` screen path → `ios=true`; `docs/x.md` → `ios=false`; `--all` → `ios=true`.
- [ ] **Step 2:** `node --test tools/test/ci-changes.test.js` fails.
- [ ] **Step 3: Implement.**
  - `ci-changes.js`: `ios = shared || kept.some(p => /^iosApp\//.test(p))`; `iosApp/` is not an Android path.
  - `project.yml` (XcodeGen 2.x): target `CoPlanly`, `PRODUCT_BUNDLE_IDENTIFIER: app.coplanly`, `IPHONEOS_DEPLOYMENT_TARGET: 17.0`, `TARGETED_DEVICE_FAMILY: 1`, portrait only, a pre-build script phase `cd "$SRCROOT/.." && ./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode`, `FRAMEWORK_SEARCH_PATHS` to `$(SRCROOT)/../shared-ui/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, `OTHER_LDFLAGS: -framework Shared -lsqlite3`, Swift packages `https://github.com/firebase/firebase-ios-sdk` exact `11.8.0` (FirebaseCore, FirebaseFirestore, FirebaseFunctions; Task 5 adds FirebaseAuth) and, from Task 9, GoogleSignIn-iOS.
  - `AppDelegate.swift`: `@main`, reads `COPLANLY_EMULATOR_HOST` from the environment or `Info.plist` key `CoPlanlyEmulatorHost`, falls back to emulators on `127.0.0.1` when no `GoogleService-Info.plist` is bundled, calls `IosApp.shared.start(config:)` and sets `window.rootViewController = IosApp.shared.rootViewController()`.
  - `IosApp.start`: `IosFirebase.configure`, `startKoin { modules(dataModules + platformDataModule + uiModules + platformUiModule + iosAppModule) }` where `iosAppModule` is empty in this task. `rootViewController()` = `ComposeUIViewController { ProvideIosShell(buildValues) { CoPlanlyApp(launch = IosLaunch.none(), darkTheme = null) } }` (`IosLaunch.none()` builds an `AppLaunch` of empty `MutableStateFlow`s).
  - CI job `ios-app` ("iPhone app — build and launch (macOS)"), `needs: changes`, `if: needs.changes.outputs.ios == 'true'`, `runs-on: macos-latest`, `timeout-minutes: 75`: checkout, JDK 17, setup-gradle, the `~/.konan` cache, Android SDK platform-tools (configuration needs it), `brew install xcodegen`, `cd iosApp && xcodegen generate`, `xcodebuild -project iosApp/CoPlanly.xcodeproj -scheme CoPlanly -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath iosApp/build build`, then boot that simulator, `xcrun simctl install`, `xcrun simctl launch --console-pty booted app.coplanly` in the background, wait 20 s, assert the process is alive (`xcrun simctl spawn booted launchctl list | grep app.coplanly`), `xcrun simctl io booted screenshot iosApp/build/launch.png`, upload it as artifact `ios-launch`. Add `ios-app` to `report.needs`.
  - `iosApp/README.md`: how the owner runs it on their Mac (Xcode 26, `brew install xcodegen`, `xcodegen generate`, open, run; emulators with `firebase emulators:start --project demo-coplanly` from `firestore-tests/`; the real project by dropping `GoogleService-Info.plist` into `iosApp/CoPlanly/`, never committed).
- [ ] **Step 4:** `node --test` passes; push; `ios-app` green with a launch screenshot showing the sign-in or telemetry screen drawn by Compose; Android untouched.
- [ ] **Step 5: Commit** `feat: an iPhone app that builds and launches (MON-24)`.

### Task 5: Signing in on the iPhone

**Files:**
- Modify: `shared-data/build.gradle.kts` (iosMain `implementation("dev.gitlive:firebase-auth:2.7.0")`; pod list gains `"FirebaseAuth"` on the same line), `iosApp/project.yml` (FirebaseAuth product)
- Create: `shared-data/src/iosMain/kotlin/com/coparently/app/data/remote/firebase/IosFirebaseAuth.kt`, `shared-data/src/iosMain/kotlin/com/coparently/app/data/session/IosAccountSwitch.kt`, `shared-data/src/iosMain/kotlin/com/coparently/app/data/session/IosAccountTraces.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/auth/IosAuthErrors.kt`
- Modify: `shared-data/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformDataModule.ios.kt`, `IosUnavailable.kt` (remove the replaced stand-ins), `shared-ui/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformUiModule.ios.kt`, `IosUiUnavailable.kt`
- Modify: `.github/workflows/ci.yml` (`ios-app` runs the emulator tests, below)
- Test: `shared-data/src/iosTest/kotlin/com/coparently/app/data/remote/firebase/IosFirebaseAuthEmulatorTest.kt`, `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/auth/IosAuthErrorsTest.kt`

**Interfaces:**
- Consumes: `SignedInUser`, `SignedInAccount`, `AuthSession`, `AccountSignIn`, `AccountConsistency`, `AccountTraces` (shared-data, signatures unchanged), `AuthErrors`, `AuthError` (shared-ui).
- Produces: `class IosFirebaseAuth(private val auth: FirebaseAuth) : SignedInUser, AuthSession, AccountSignIn` (GitLive `dev.gitlive.firebase.auth.FirebaseAuth`); `class IosAccountSwitch(database: CoPlanlyDatabase, auth: SignedInUser, preferences: SecurePreferences, reminders: ReminderScheduler, clocks: RequestClockScheduler, traces: AccountTraces, lastUid: LastSignedInUid) : AccountConsistency`; `interface LastSignedInUid { fun get(): String?; fun set(uid: String) }` (NSUserDefaults `"coplanly.lastSignedInUid"`); `class IosAccountTraces(directories: AppDirectories) : AccountTraces`; `object IosAuthErrors : AuthErrors`.

- [ ] **Step 1: Write the failing tests.**
  - `IosAuthErrorsTest`: GitLive's exception for `FIRAuthErrorCodeWrongPassword` and `InvalidCredential` → `InvalidCredentials`; `EmailAlreadyInUse` → `EmailAlreadyInUse`; `WeakPassword` → `WeakPassword`; `InvalidEmail` → `InvalidEmail`; `UserDisabled` → `UserDisabled`; `TooManyRequests` → `TooManyRequests`; `NetworkError` → `Network`; anything else → `Unknown`; `fromGoogle` of a user cancel (`GIDSignInError.canceled`, mapped in Task 9 as `GoogleSignInCancelled`) → null. Build the exceptions through the same factory the implementation reads (`NSError` with `FIRAuthErrorDomain` and the code).
  - `IosFirebaseAuthEmulatorTest` (skips itself unless `COPLANLY_EMULATOR_HOST` is set, and **fails** on skip when `COPLANLY_REQUIRE_EMULATORS=1`, as `web-tests` does): create an account, `observeCurrentUid()` emits its uid, `getCurrentAccount()` has the e-mail and provider `password`, sign out → `awaitSignedInUid()` null; a wrong password is `Result.failure` whose cause `IosAuthErrors.fromEmailPassword` calls `InvalidCredentials`; `IosAccountSwitch` with a different last uid clears every table (insert one user row first, then assert `userDao` empty) and `SecurePreferences` (a `putString` key is gone; a `school_connection_` key is gone).
- [ ] **Step 2:** Push; the tests fail (compile errors count).
- [ ] **Step 3: Implement**, mirroring `FirebaseAuthService`, `AccountSwitchGuard` and `DeviceTraces` (`docs`: recon in this plan's Task 5 references; read the Android classes in `app/src/main/java/com/coparently/app/data/`) line for line, on GitLive's `Firebase.auth`: `authStateChanged` for `observeCurrentUid`, `GoogleAuthProvider.credential(idToken, null)`, `useEmulator` from `IosFirebase` when a host is set. `IosAccountTraces` deletes `cache/{shared_files, receipts, exports, payment_codes}` and `files/chat_outbox`. Bind in `platformDataModule` and `platformUiModule`. In `ios-app`: install Node 22 and Java 21, `npm ci` in `functions/` and `firestore-tests/`, then run `firestore-tests/node_modules/.bin/firebase emulators:exec --only auth,firestore,functions,storage --project demo-coplanly "COPLANLY_EMULATOR_HOST=127.0.0.1 COPLANLY_REQUIRE_EMULATORS=1 ./gradlew :shared-data:iosSimulatorArm64Test :shared-ui:iosSimulatorArm64Test"` (this also starts running `shared-ui`'s iOS tests, `IosSignInStandInsTest` included, which ROADMAP carried). Publish the JUnit as check run "iPhone tests on the emulators", `require_tests: true`.
- [ ] **Step 4:** Push; `shared-ios`, `ios-app` green; Android untouched.
- [ ] **Step 5: Commit** `feat: e-mail sign-in on the iPhone (MON-24)`.

### Task 6: What a signed-in iPhone needs to run

**Files:**
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/FirestorePushQueue.kt`, `shared-data/src/commonMain/kotlin/com/coparently/app/data/sync/ForegroundSyncRunner.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosWordings.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosLifecycle.kt`
- Modify: `IosApp.kt` (start), `PlatformDataModule.ios.kt`, `IosUnavailable.kt` (`NoPush` keeps `PushRegistration` only), `PlatformUiModule.ios.kt`
- Test: `shared-data/src/commonTest/kotlin/com/coparently/app/data/sync/ForegroundSyncRunnerTest.kt`, `shared-data/src/commonTest/kotlin/com/coparently/app/data/remote/firebase/FirestorePushQueueTest.kt`

**Interfaces:**
- Produces:
  - `class FirestorePushQueue(private val firestore: FirestoreClient, private val signedInUser: SignedInUser) : PushDevice` — `queueNotificationForUser` writes `notification_queue/{newDocumentId}` with exactly `targetUserId`, `data` (stamped with `PushPayload.FAMILY_ID` = `FamilyKey.orNull(uid, target)` unless present), `createdAt` (Long epoch millis, `Clock.System.now().toEpochMilliseconds()`), `status = "pending"`, as `FcmService.queueNotificationForUser` does; `getCurrentToken()` null; `updateUserToken` succeeds without a write (no token on iPhone until phase 4). Android keeps `FcmService` unchanged.
  - `class ForegroundSyncRunner(private val sync: suspend () -> Result<Unit>, private val afterSync: suspend () -> Unit, private val scope: CoroutineScope) : SyncRequester` — one run at a time; a request during a run schedules exactly one more run after it; `afterSync` (the Google import) runs after each sync and its failure is logged, never fails the run.
  - `class IosPairingWording : PairingWording` and `class IosSchoolImportWordings(childInfo: ChildInfoRepository) : SchoolImportWordings`, reading the same keys as the Android classes (`pairing_default_partner_name`, `school_import_hours_title`, `school_import_day_off_named`, `school_import_director_day`, `school_import_day_off`) through Compose Multiplatform's `getString`/`getString(res, args)` in `readerLocale()`'s language.
  - `IosLifecycle.observe(onForeground: () -> Unit)` on `UIApplicationWillEnterForegroundNotification`.
- [ ] **Step 1: Write the failing tests.** `ForegroundSyncRunnerTest` (virtual time): two requests during one run → exactly two runs; three requests while idle and one run → no overlap, at most one follow-up; `afterSync` throwing does not stop the next run. `FirestorePushQueueTest` (a recording `FirestoreClient` fake): the written map's keys and values equal the Android shape for `targetUserId="b"`, signed-in `"a"`, data `{type: "event_important"}` → data gains `familyId = FamilyKey.of("a","b")`; a payload with `familyId` already set is not overwritten; `createdAt` is a `Long`.
- [ ] **Step 2:** Push; `shared` fails.
- [ ] **Step 3: Implement**; `IosApp.start` then does, after Koin: `get<ChatMirror>().start()`, `get<SessionProfileSynchronizer>().start()`, `get<SyncRequester>().requestSyncNow()`, and `IosLifecycle.observe { get<SyncRequester>().requestSyncNow() }` — the twin of `CoPlanlyApplication.onCreate` minus telemetry, the widget and the periodic job (phase 4). Bind `SyncRequester` to `ForegroundSyncRunner(sync = { get<SyncService>().performFullSync() }, afterSync = { get<GoogleCalendarAutoImport>().importIfDue() }, scope = an app-lifetime `CoroutineScope(SupervisorJob() + Dispatchers.Default)`)`.
- [ ] **Step 4:** Push; green.
- [ ] **Step 5: Commit** `feat: the iPhone syncs, tells the co-parent and words its records (MON-24)`.

### Task 7: The write gate

**Files:**
- Create: `shared-data/src/iosTest/kotlin/com/coparently/app/gate/IosWriteGateTest.kt`, `tools/ios/check-ios-writes.js`, `tools/test/check-ios-writes.test.js`
- Modify: `.github/workflows/ci.yml` (`ios-app`: after the emulator tests, run the checker against the emulator before it stops)

**Interfaces:**
- Consumes: the production repositories as the e2e parents build them (`app/src/androidTest/java/com/coparently/app/e2e/EmulatorParent.kt` is the model), `PairingFunctions`, `FirestorePushQueue`.
- Produces: `node tools/ios/check-ios-writes.js --emulator 127.0.0.1:8080 --fixtures app/src/test/resources/wire/current` exits non-zero on any mismatch, one `::error::` line per mismatch.

- [ ] **Step 1: Write the failing checker test** `check-ios-writes.test.js`: given a fixture `{a: 1, b: "x", c: {d: true}, e: ["s"]}` and a document `{a: 1.5, b: "x", c: {d: true}, e: ["s"]}` → one mismatch "a: integer expected, double found"; a missing key the fixture has → mismatch; an extra key → mismatch; a Firestore `timestampValue` where the fixture holds an ISO string → mismatch; equal shapes → none.
- [ ] **Step 2:** `node --test` fails.
- [ ] **Step 3: Implement.**
  - `IosWriteGateTest` (emulator-only, fails on skip under `COPLANLY_REQUIRE_EMULATORS=1`): two accounts on the Auth emulator in two named GitLive apps (`Firebase.initialize(name = "alice"|"bob", …)`; never deleted, as on Android), paired through `PairingRepository`'s invitation and the `acceptPairingInvitation` callable; Alice's production repositories write, in the pair's family: an event (shared), an important event, an expense, a child with a medical profile, a pet, a chat message, a change request, a custody model with a day swap, a request deadline, a push (`event_important`); after each, Bob's production reader reads it back and the test asserts the domain value equals what was written (for the event, `EventDocument.toEntity` of Bob's read equals Alice's entity, timestamps aside). Each document's path is written to `build/ios-gate/written.txt`.
  - `check-ios-writes.js` reads each path over the emulator's REST API (`/v1/projects/demo-coplanly/databases/(default)/documents/...` with `Authorization: Bearer owner`) and compares each value's Firestore type, key set and nested shapes with the matching `wire/current/<collection>/*.json` fixture (the collection's first fixture; `notification_queue` against a literal shape in the script).
  - `ios-app` runs the gate inside the same `emulators:exec` and the checker after the tests, in the same command.
- [ ] **Step 4:** Push; green. If a mismatch appears, fix the iPhone side; if the rules refuse a write the Android client makes, stop and report (Global Constraints).
- [ ] **Step 5: Commit** `test: the iPhone's writes match Android's on the emulators (MON-24)`.

### Task 8: The native shell

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/NativeShell.kt`, `StackHost.kt`, `StackMirror.kt`, `ScreenController.kt`, `NativeBars.kt`, `FormSheet.kt`, `SfSymbols.kt`
- Modify: `IosApp.kt` (`rootViewController()` returns the shell)
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/navigation/StackMirrorTest.kt` (the mirroring rules are pure Kotlin over route lists, so they live in common code: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/navigation/StackMirror.kt`)

**Interfaces:**
- Consumes: `AppDestination`, `DestinationKind`, `rememberAppDestinations`, `AppProviders`, `TabSwitcher`/`LocalTabSwitcher`, `ScreenBar`, `BarHost`, `LocalBarHost` (Tasks 2–3).
- Produces:
  ```kotlin
  // commonMain, pure
  sealed interface StackChange { data class Push(val entryId: String) : StackChange; data class Pop(val count: Int) : StackChange; data class Replace(val entryIds: List<String>) : StackChange }
  object StackMirror { fun diff(shown: List<String>, wanted: List<String>): List<StackChange> }
  ```
  `NativeShell`: a `UITabBarController` (four tabs, labels from `nav_home`/`nav_calendar`/`nav_chat`/`nav_family`, SF Symbols `house`, `calendar`, `bubble.left.and.bubble.right`, `person.2`, the Chat badge from `ChatViewModel.unreadCount`), one `UINavigationController` per tab with `prefersLargeTitles = true`, and a root-flow navigation controller without the tab bar swapped in while `startDestinationFor(...)` is a `ROOT_FLOW` route. Each tab owns its own `NavHostController` built from the same `AppDestination` list (the graph's start is the tab's root, registered with an empty content lambda; the shell keeps route → `AppDestination` itself); the screens a tab hosts get that tab's controller through `rememberAppDestinations(navController = thatTab, …)`, and the shell's `TabSwitcher.select(tab)` selects the tab (Home's chat tile, the money push opening Family then Expenses on Family's stack). `ScreenController(entry)` = `ComposeUIViewController` that composes, inside `ProvideIosShell` and `AppProviders` (their state held once by the shell and shared by every screen), `entry.LocalOwnersProvider(saveableStateHolder) { destination.content(entry) }` with `LocalBarHost` provided as a host that applies `ScreenBar` to that view controller's `navigationItem` (title, `largeTitleDisplayMode`, `rightBarButtonItems` with `UIImage(systemName:)`, the family chip as a `UIBarButtonItem` with a custom view hosting Compose). `StackHost` watches the tab controller's `currentBackStack` and applies `StackMirror.diff` to the `UINavigationController`; `UINavigationControllerDelegate.navigationController(_:didShow:)` pops the shared controller when UIKit popped by itself, and calls `ComposeNavigator.onTransitionComplete(entry)` for entries shown or removed so their lifecycles reach RESUMED and DESTROYED. A `FORM` destination is presented as a page sheet (`UISheetPresentationController`, `.large()` detent, grabber) whose `isModalInPresentation` follows the form's `DiscardGuard.dirty`, and whose `presentationControllerDidAttemptToDismiss` triggers the form's discard question; `TAB_ROOT` screens show a large title, `PUSH` an inline one, Settings large. Calendar and Chat roots keep their Compose header in part A (`navigationBarHidden = true` on those two roots); part B converts them.
- [ ] **Step 1: Write the failing test** `StackMirrorTest`: `diff([a,b],[a,b,c])` = `[Push(c)]`; `diff([a,b,c],[a])` = `[Pop(2)]`; `diff([a,b],[a,c])` = `[Pop(1), Push(c)]`; `diff([a],[a])` = `[]`; `diff([a,b],[x])` = `[Replace([x])]` (a different root).
- [ ] **Step 2:** Push; `shared` fails.
- [ ] **Step 3: Implement** the pure `StackMirror`, then the UIKit host. Keep each file under detekt's limits; UIKit interop through `platform.UIKit`. The shell keeps a `ViewModelStoreOwner` for what Android scopes to the activity. *(As built, after the review's sign-out finding: `SyncViewModel`, `AuthStateViewModel` and `ConnectivityViewModel` live as long as the shell; `ParentPaletteViewModel`, `FamilyViewViewModel` and the badge's `ChatViewModel` belong to a session owner scoped to the signed-in session and cleared at sign-out, stricter than Android. The telemetry question is not asked on the iPhone, spec §5.2.)* The offline line (`ConnectivityBanner`) is drawn at the top of each hosted screen's content, under the bar. Deep links arm as on Android, after the family switch (CLAUDE.md item 15) — `IosLaunch` stays empty in part A.
- [ ] **Step 4:** Push; `shared`, `shared-ios`, `ios-app` green; `ios-app` now also saves screenshots of Home, Family, Expenses, the Add expense sheet and Settings on the emulators' seeded family (reuse `tools/e2e/pairing-smoke.js` to create a pair, then drive the app with `xcrun simctl openurl` deep links is **not** available yet — drive it with a small XCUITest target `CoPlanlyUITests` that signs in by e-mail and taps the tabs and rows by accessibility label; screenshots attached as `XCTAttachment`s and exported with `xcrun xcresulttool`). Android untouched.
- [ ] **Step 5: Commit** `feat: native tab and navigation bars on the iPhone (MON-24)`.

### Task 9: Google sign-in on the iPhone

**Files:**
- Create: `iosApp/CoPlanly/GoogleSignInBridge.swift`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosCredentialSignIn.kt`
- Modify: `iosApp/project.yml` (GoogleSignIn-iOS package, URL scheme from the plist's `REVERSED_CLIENT_ID`), `IosApp.kt` (`start` takes the bridge), `IosPlatform.kt` (`ProvideIosShell` takes the `CredentialSignIn`)
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/IosCredentialSignInTest.kt`

**Interfaces:**
- Produces (Kotlin, implemented in Swift):
  ```kotlin
  interface GoogleIdTokenSource { val available: Boolean; fun requestIdToken(completion: (idToken: String?, cancelled: Boolean, error: String?) -> Unit) }
  class IosCredentialSignIn(private val google: GoogleIdTokenSource) : CredentialSignIn   // savedPassword() fails with NoSavedPassword: iOS autofill fills the fields instead
  ```
  `IosApp.start(config, google: GoogleIdTokenSource)`; `GoogleSignInBridge.swift` implements it with `GIDSignIn.sharedInstance.signIn(withPresenting:)`, `available = Bundle.main` has `GoogleService-Info.plist` with a `CLIENT_ID`. With no plist (CI, emulators) `available` is false and the sign-in screen offers e-mail alone, as the screen's existing `credentials.available` check already does.
- [ ] **Step 1: Write the failing test** `IosCredentialSignInTest` (fake source): a token → `Result.success(token)`; cancel → failure that `IosAuthErrors.fromGoogle` maps to null; an error → failure mapped to `Unknown`; `available` mirrors the source.
- [ ] **Step 2:** Push; fails.
- [ ] **Step 3: Implement.** The e-mail field and the password field get `textContentType` hints through Compose's `ContentType` semantics (`ContentType.EmailAddress`, `ContentType.Password`, `NewPassword` on sign-up); this is a common change that Android's autofill reads too — prove it changes no screenshot. *(As built: Compose Multiplatform 1.9.3 ignores these semantics on iOS (CMP-7154); iOS autofill offers saved passwords through the fields' keyboard types, `Email` and `Password`, and `NewPassword` is not honoured on iOS yet.)*
- [ ] **Step 4:** Push; green. The real Google sheet is checked by the owner on their Mac (Task 10).
- [ ] **Step 5: Commit** `feat: Google sign-in on the iPhone (MON-24)`.

### Task 10: The owner's proof run, and the record

**Files:**
- Create: `docs/superpowers/plans/2026-11-10-iphone-phase-3a-owner-check.md` (the owner's checklist)
- Modify: `CLAUDE.md` (the architecture map's `iosApp/` line; the CI section's `ios-app` job; a "Where an iPhone path lives" sentence), `docs/ROADMAP.md` (MON-24: part A done, §7 of the spec answered), `docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` (§7: each open point's answer), `docs/IOS-RUNNER.md` (the app job)

- [ ] **Step 1:** Write the owner's checklist: on the Mac with Xcode 26, `brew install xcodegen`, `cd iosApp && xcodegen generate`, open, run on an iPhone 16 simulator against the emulators (one command from `firestore-tests/`), then with `GoogleService-Info.plist` against a **test pair** of accounts. What to look at, each with "same as the canvas board?" yes/no: the tab bar and its glass; Home and Family large titles collapsing on scroll (§7.2); the bar edge over scrolling content (§7.3); the back button and the edge swipe, including a swipe started and cancelled (§7.1); the Add expense sheet, its swipe-down with edits (§7.5); the keyboard in Add expense and in the chat thread (§7.4); switching tabs back and forth with a stack three deep (§7.6); Google sign-in; reinstalling the app and signing in again (Review Focus 3).
- [ ] **Step 2:** Run `node tools/check-invariants.js` and `node --test tools/test/*.test.js`; update the docs listed above.
- [ ] **Step 3:** Push; full CI green.
- [ ] **Step 4:** Hand the checklist to the owner. Their answers on §7.2 and §7.3 decide whether part B keeps the system large title or falls back (spec §7).
- [ ] **Step 5: Commit** `docs: record phase 3's part A (MON-24)`.
