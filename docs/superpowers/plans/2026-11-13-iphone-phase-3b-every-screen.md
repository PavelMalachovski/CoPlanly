# iPhone phase 3, part B: every screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every one of the 51 destinations is reachable on the iPhone under a system bar, every form opens as a sheet with its confirm button in the bar, the `coplanly://` links open their screens, a parent can delete their account from the iPhone, and the part-A findings recorded for part B are closed. Android draws and does exactly what it does today.

**Architecture:** Part A's machinery stays: `ScreenBar`/`AppTopBar`/`LocalBarHost` describe a bar, `NativeBars` draws it on UIKit, `StackHost` mirrors the shared back stack, `FormSheet` presents a `FORM` destination. Part B widens the bar vocabulary (a title composable, a menu, a main action), adds two shell locals that Android never provides (`LocalBackSteps`, `LocalScreenVisible`), and then converts the remaining 39 bars (34 screens with a Material `TopAppBar` today, plus the calendar, chat and the root flows) in batches. Every iPhone-only branch is decided by `LocalBarHost.current != null` (spelled `shellDrawsBars()`), never by a platform check.

**Tech Stack:** as part A: Kotlin 2.4.21, Compose Multiplatform 1.9.3, JetBrains navigation-compose 2.9.2, Koin 4.1.1, GitLive 2.7.0 (firestore, functions, auth), Firebase iOS SDK 11.8.0, XcodeGen, XCUITest on GitHub-hosted `macos-latest`.

**Spec:** `docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` (§5.2–§5.6 and §6 row B), with its parent `docs/superpowers/specs/2026-10-27-iphone-app-design.md`. Part A's plan, for the shapes this one extends: `docs/superpowers/plans/2026-11-10-iphone-phase-3a-proof-and-skeleton.md`. The carried findings: `docs/ROADMAP.md`, MON-24, "Still owed from part A".

## Rulings made while planning (the owner's §7 answers are not in yet)

- **Large titles stay as part A built them** (always shown, never collapsing), and **content stays padded below the bars** (§7.2 and §7.3 unanswered). §5.5's "content runs under the bars" is not built in part B; it needs the scroll view §7.2's first try needs, and both wait for the owner's run of the part-A checklist. Cost if wrong: one later task that touches the shell's frame and each list's content padding.
- **Images (`PlatformImages`) and Storage (`StorageBucket`) are part C**, with the pickers that produce what they show. Part B leaves photos hidden on the iPhone, as today.
- **The calendar's title is inline, not large**, so the grid keeps its height (the redesign's "the grid gets the height", design item 5).
- **Custom-scheme links only.** `coplanly://pair`, `coplanly://guest`, `coplanly://chat`. Universal Links on `/pair/` and a tapped push are phase 4 (parent design §5.4).
- **A hidden tab's thread is closed by the shell, not by a lifecycle change Android would share.** `LocalScreenVisible` is provided only by the iPhone shell; Android's chat is unchanged.

## Global Constraints

- **Android draws and does exactly what it does today.** The 470 screenshot baselines must not change; the full Android CI (unit, screenshots, three emulator legs, e2e, upgrade, R8 probe, lint, detekt) stays green on every task. With no `LocalBarHost`, `AppTopBar` must draw the Material `TopAppBar` each screen drew before, with `screenTopAppBarColors()`, the same title, arrow, cross and icons in the same order.
- **An iPhone-only branch is `shellDrawsBars()`** (`LocalBarHost.current != null`), never a `Platform` check, so a Robolectric test can exercise both branches by providing a host.
- **SF Symbols** are written as their system names (`gearshape`, `plus`, `xmark`, `ellipsis.circle`, `magnifyingglass`, `calendar`, `line.3.horizontal.decrease.circle`, `square.and.arrow.up`, `trash`, `pencil`, `arrow.left.arrow.right`); a symbol not in iOS 17's set is a bug.
- **Nothing on the iPhone may lead to a stand-in** (design item 8): a bar action whose capability is `No…` is left out of the bar.
- **No string a parent reads without a key in all five `composeResources/values*/` files** (CLAUDE.md "Localization"); `node tools/check-invariants.js` must pass.
- **No server, rules or schema change.** Account deletion calls the existing `deleteAccount` callable.
- **Minimum iOS 17.0, iPhone only, portrait only** (part A). Firebase iOS SDK 11.8.0 and GitLive 2.7.0 exactly.
- English only in the repository; KDoc on public declarations; Conventional Commits; every commit ends with the session's two attribution lines. No model identifiers anywhere.
- **No Gradle in the cloud session** (Google Maven is unreachable). Verification is CI: push to `ccr-ef59168f-16qmlu`, then read the run; locally `node tools/check-invariants.js` and `node --test tools/test/*.test.js`. A task is done only when its CI run is green, `ios-app` included.

## Review Focus

1. **An Android screen whose bar moved to `AppTopBar`** must be pixel-identical, including a subtitle line, a title that is not plain text, and a disabled arrow while saving. Owner: every conversion task; `AppTopBarTest` gains a case per new shape (Task 1).
2. **Back on the iPhone while a screen has an inner step** (CustodySetup's open section, a child's schedule, Chat search, ConnectSchool's steps, Onboarding's later steps, the swap selection): the system back button and the edge swipe must undo the step, not leave the screen. Owner: Task 2.
3. **A thread left open in a hidden Chat tab, and the app in the background**: no message that arrives meanwhile is marked READ, and the draft is kept. Owner: Task 3.
4. **A form sheet's Save in the bar while saving or invalid**: disabled exactly when Android's sticky button is, never tappable twice, and a failed save leaves the sheet open with its edits. Owner: Task 4.
5. **A `coplanly://` link arriving while signed out, or for another family**: the invitation waits for sign-in as on Android, and a chat link switches the family before it opens (CLAUDE.md item 15). Owner: Task 10.

---

### Task 1: The bar vocabulary the remaining screens need

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/ScreenBar.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/StickyActionBar.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/NativeBars.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/expenses/AddExpenseScreen.kt`, `.../expenses/ExpenseScreen.kt` (the two part-A screens brought to the new shapes)
- Test: `app/src/test/java/com/coparently/app/presentation/common/AppTopBarTest.kt`

**Interfaces:**
- Produces:
  - `fun shellDrawsBars(): Boolean` — `@Composable`, `LocalBarHost.current != null`.
  - `data class BarMenuItem(val label: String, val sfSymbol: String?, val onClick: () -> Unit, val destructive: Boolean = false, val enabled: Boolean = true)`.
  - `BarAction` gains `val menu: List<BarMenuItem>? = null` (a non-null menu opens on tap instead of `onClick`; Android: a `DropdownMenu` of `DropdownMenuItem`s; iPhone: a `UIMenu` as the item's `menu`, destructive items with `UIMenuElementAttributesDestructive`).
  - `ScreenBar` gains `val titleContent: (@Composable () -> Unit)? = null` (Android draws it in place of the title text; iPhone sets it as `navigationItem.titleView` through the same Compose-in-UIKit host the family chip uses). `visibleOnly()`/`delegatingTo()` treat it as they treat `trailingContent`.
  - `StickyActionBar` draws nothing when `shellDrawsBars()`; the screen then puts the same action in its bar as `BarAction(primary = true, …)`.
  - The form rule, used by Tasks 4–9: on the iPhone a `FORM` destination's leading is `BarLeading.Close` (`xmark`) and its main action is a `primary` `BarAction` with the sticky button's label, `enabled = enabled && !busy`; on Android both stay as today.

- [ ] **Step 1: Write the failing tests** in `AppTopBarTest` (Robolectric, `RobolectricTestApplication`):
  - `a menu action opens its items and runs the tapped one` (no host): one `BarAction(menu = listOf(item("Report"), item("Settings")))`; click the icon by its label; both item texts are displayed; click "Report"; the item's lambda ran once and the menu closed.
  - `titleContent replaces the title text` (no host): `ScreenBar(title = "Lana", titleContent = { Text("L · Lana") })` shows "L · Lana" and no node with text exactly "Lana".
  - `a host receives the menu and the title content and no Material bar is drawn`: provide a recording `BarHost`; the shown bar's `actions[0].menu!!.size == 2` and `titleContent != null`; no node has the `TopAppBar`'s title text.
  - `a menu change alone reaches the host again`: change one item's label; the host was called twice. Change only a lambda; still twice.
  - `the sticky bar draws nothing under a host`: compose `StickyActionBar("Save", {})` inside `LocalBarHost provides recordingHost`; no node with text "Save".
- [ ] **Step 2:** Push; `build-test` fails on these.
- [ ] **Step 3: Implement** the produced API in `ScreenBar.kt`, `StickyActionBar.kt` and `NativeBars.kt` (`VisibleAction` gains the menu's labels, symbols, destructive and enabled flags, so a menu change redraws; the title view is rebuilt only when `titleContent` appears or goes).
- [ ] **Step 4: Bring part A's screens to the shapes.** `AddExpenseScreen`: on the iPhone `BarLeading.Close` and a primary Save in the bar (its `StickyActionBar` now hides itself); Android unchanged. `ExpenseScreen`: on the iPhone the floating "+" is not drawn and the bar carries `BarAction(label = expenses_add, sfSymbol = "plus")`; `ScrollAwareFab` and its clearance stay Android's.
- [ ] **Step 5:** Push; CI green, screenshots unchanged, `ios-app` walk green (the walk's Add expense step finds Save in the sheet's bar: update `ShellUITests.swift` to tap the bar's "Save" rather than the sticky button).
- [ ] **Step 6: Commit** `feat: give screen bars a menu, a title view and a main action (MON-24)`.

### Task 2: Inner back steps on the iPhone

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/BackSteps.kt`
- Modify: the eight `BackHandler` call sites: `school/ConnectSchoolScreen.kt:133`, `custody/CustodySetupScreen.kt:166,200`, `chat/ChatScreen.kt:245`, `onboarding/OnboardingScreen.kt:171`, `calendar/components/DaySwapSelectionBar.kt:67`, `calendar/CalendarScreen.kt:685`, `calendar/SwapSelectionChrome.kt:86`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/ScreenController.kt`, `EdgeSwipeBack.kt`, `NativeBars.kt`, `StackHost.kt`
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/common/BackStepsTest.kt`, `iosApp/CoPlanlyUITests/ShellUITests.swift`

**Interfaces:**
- Produces:
  - `class BackSteps` with `fun register(step: BackStep): () -> Unit` (returns the unregister), `val hasEnabledStep: Boolean`, `fun handle(): Boolean` (runs the most recently registered enabled step and answers true, or answers false).
  - `class BackStep(var enabled: Boolean, var onBack: () -> Unit)`.
  - `val LocalBackSteps: ProvidableCompositionLocal<BackSteps?>` (`staticCompositionLocalOf { null }`; Android provides none).
  - `@Composable fun ScreenBackStep(enabled: Boolean = true, onBack: () -> Unit)`: always calls Compose Multiplatform's `BackHandler(enabled, onBack)` (so Android is unchanged), and also registers with `LocalBackSteps.current` when there is one, keeping `enabled` and `onBack` current.
- Consumes: `ScreenController` provides one `BackSteps` per hosted screen.

- [ ] **Step 1: Write the failing tests** in `BackStepsTest` (commonTest, names without commas): `no step answers false`; `a disabled step is skipped`; `the latest enabled step runs first`; `an unregistered step never runs`; `hasEnabledStep follows enabled`.
- [ ] **Step 2: Implement `BackSteps.kt`** and replace each of the eight `BackHandler` calls with `ScreenBackStep` (same `enabled` and lambda).
- [ ] **Step 3: Wire the iPhone shell.** `ScreenController` provides `LocalBackSteps`. The native back button (`NativeBars`'s leading `Back` item, and the system back of a screen with a Compose bar) first calls `backSteps.handle()` and pops only when it answers false. `EdgeSwipeBack.gestureRecognizerShouldBegin` also answers false while the top screen's `backSteps.hasEnabledStep` (the swipe cannot be half-run against a step; the back button remains).
- [ ] **Step 4: Extend the XCUITest walk**: open Settings → Data sources is not enough; add a step that opens Family → Custody (CustodySetup), opens one section, taps the system back button, and asserts the overview's rows are shown and the screen is still CustodySetup; a second back leaves it.
- [ ] **Step 5:** Push; CI green, `ios-app` walk green.
- [ ] **Step 6: Commit** `fix: let the iPhone's back undo a screen's inner step (MON-24)`.

### Task 3: A hidden tab stops reading the chat

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/ScreenVisibility.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/chat/ChatScreen.kt` (the open-thread effect, around line 217)
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/NativeShell.kt` (`TabShell`), `StackHost.kt`, `ScreenController.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosLifecycle.kt`
- Test: `app/src/test/java/com/coparently/app/presentation/chat/ChatScreenVisibilityTest.kt` (Robolectric), `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/shell/ScreenVisibilityIosTest.kt`

**Interfaces:**
- Produces: `val LocalScreenVisible: ProvidableCompositionLocal<State<Boolean>?>` (default null = always visible, Android's answer). The iPhone's `ScreenController` provides a state that is true only while its stack's tab is selected, it is the top screen of that stack (or under a presented sheet), and the scene is in the foreground.
- ChatScreen: the thread is open (`onThreadOpened(conversationId)`) while visible and closed (`onThreadClosed()`) the moment it is not; it reopens on becoming visible again. With no local, the effect is exactly today's `DisposableEffect(conversationId)`.

- [ ] **Step 1: Write the failing tests.** `ChatScreenVisibilityTest`: a fake `ChatViewModel` collaborator counting opens and closes (use the ViewModel with mocked repositories, as `ChatViewModelTest` does, and read `openedConversationId`); with `LocalScreenVisible provides mutableStateOf(true)`, flip to false → closed once; back to true → opened again; with no local → opened once and never closed while composed. `ScreenVisibilityIosTest`: the shell's visibility for a stack is false after another tab is selected and after the scene enters the background, true again on return.
- [ ] **Step 2: Implement** `ScreenVisibility.kt`, the ChatScreen effect, and the shell's state (tab delegate, `UINavigationControllerDelegate`'s did-show, and `IosLifecycle`'s foreground flow).
- [ ] **Step 3:** Push; CI green, screenshots unchanged.
- [ ] **Step 4: Commit** `fix: stop a hidden iPhone Chat tab marking messages read (MON-24)`, and in the same commit remove the blocker bullet from ROADMAP MON-24 "Still owed from part A".

### Task 4: Every form is a sheet with its Save in the bar

**Files:**
- Modify: `event/AddEditEventScreen.kt`, `changerequests/RequestChangeScreen.kt`, `journal/JournalEditorScreen.kt`, `childinfo/AddEditChildInfoScreen.kt`, `pets/AddEditPetScreen.kt` (all under `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/`)
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/StackHost.kt` (`NATIVE_BAR_ROUTES` gains the seven form routes)
- Test: `app/src/test/java/com/coparently/app/presentation/common/FormBarsTest.kt` (Robolectric)

**Interfaces:**
- Consumes: Task 1's form rule, `shellDrawsBars()`, `BarLeading.Close`, primary `BarAction`; `rememberDiscardGuard` stays the up arrow's and the close's handler.

- [ ] **Step 1: Write the failing tests** in `FormBarsTest`, one per form, each composing the screen with its ViewModel's dependencies mocked as the existing screen tests do (`AddEditEventScreenTest` is the model) under a recording `BarHost`: the shown bar's leading is `Close`; exactly one action is `primary`, labelled as the form's sticky button, `enabled` false while the form is invalid and true once valid; clicking it runs the save (verify the ViewModel call). Without a host: a node with the sticky label exists and the bar's leading is the arrow.
- [ ] **Step 2: Convert** each form's `TopAppBar` to `AppTopBar(ScreenBar(…))` with the title it drew (`titleContent` where its title is not plain text), Android's leading `Back` (its `enabled` exactly as today's arrow), and on the iPhone `Close` plus the primary Save.
- [ ] **Step 3:** Add the seven form routes to `NATIVE_BAR_ROUTES`.
- [ ] **Step 4:** Push; CI green, screenshots unchanged; extend the walk: Calendar → "+" opens New event as a sheet; Save is disabled until a title is typed; typing then swiping down asks "Discard changes?".
- [ ] **Step 5: Commit** `feat: open every form as an iPhone sheet with Save in its bar (MON-24)`.

### Task 5: The family's pushed screens

**Files:** under `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/`: `childinfo/ChildInfoScreen.kt`, `childinfo/ChildDetailScreen.kt`, `pets/PetsScreen.kt`, `contacts/ContactsScreen.kt`, `documents/FamilyDocumentsScreen.kt`, `journal/JournalListScreen.kt`, `stickers/MyStickersScreen.kt`, `family/EarlierFamiliesScreen.kt`, `family/UnassignedRecordsScreen.kt`, `review/MonthReviewScreen.kt`, `export/ExportScreen.kt`, `support/SupportScreen.kt`; `StackHost.kt` (`NATIVE_BAR_ROUTES`: ChildInfo, ChildDetail, Pets, Contacts, Documents, Journal, Stickers, EarlierFamilies, EarlierFamily, UnassignedRecords, MonthReview, Export, Support).
**Test:** `app/src/test/java/com/coparently/app/presentation/common/PushedBarsTest.kt` (shared by Tasks 5–6).

- [ ] **Step 1: Write the failing tests** in `PushedBarsTest`: for each screen, under a recording host, the bar's title is the screen's title string, leading is `Back`, and its actions are exactly the list below; without a host the Material bar shows the same title.
  - ChildInfo, Pets, Journal: `BarAction(sfSymbol = "plus")` labelled as today's floating button; the floating button is not drawn under a host.
  - ChildDetail: its edit action (`pencil`) as today's.
  - every other screen: today's actions, each with its symbol; none where today has none.
- [ ] **Step 2: Convert** each screen to `AppTopBar`, floating "+" buttons to bar actions under a host only, and add the routes.
- [ ] **Step 3:** Push; CI green, screenshots unchanged.
- [ ] **Step 4: Commit** `feat: give the family's iPhone screens their system bars (MON-24)`.

### Task 6: Agreements, people, sign-in links and Settings' sub-screens

**Files:** under `.../presentation/`: `changerequests/ChangeRequestsScreen.kt`, `parentingplan/ParentingPlanScreen.kt`, `custody/CustodySetupScreen.kt`, `pairing/CustodyConflictScreen.kt`, `pairing/PairingScreen.kt`, `professionals/ProfessionalsScreen.kt`, `professionals/ProfessionalCalendarScreen.kt`, `professionals/ProfessionalPlanScreen.kt`, `friends/FriendsScreen.kt`, `friends/FriendDetailScreen.kt`, `friends/FriendProfileScreen.kt`, `friends/FriendCalendarScreen.kt`, `guests/GuestAcceptScreen.kt`, `profile/ProfileScreen.kt` (MyProfile and CoParentProfile), `school/SchoolImportScreen.kt`, `school/ConnectSchoolScreen.kt`, `settings/CalendarFeedScreen.kt`, `settings/DataSourcesScreen.kt`; `StackHost.kt` (their routes).
**Test:** `PushedBarsTest` (Task 5's), one case per screen.

- [ ] **Step 1: Write the failing tests** as Task 5's: title, `Back` (or `None` where today draws no arrow: CustodyConflict), today's actions with symbols. CustodySetup: the title follows the open section as today. ConnectSchool: the title follows the step as today, and its back runs the step's back (Task 2).
- [ ] **Step 2: Convert** and add the routes. ProfileScreen's Save (a `StickyActionBar`) follows Task 1's form rule though it is a `PUSH` destination: on the iPhone a primary bar action, `Back` kept.
- [ ] **Step 3:** Push; CI green, screenshots unchanged.
- [ ] **Step 4: Commit** `feat: give agreements, people and settings' iPhone screens their system bars (MON-24)`.

### Task 7: Chat

**Files:** `chat/ConversationsScreen.kt`, `chat/ChatScreen.kt` (its thread bar around 480–520), `chat/ChatThreadHeader.kt`, `chat/ChatOverflowMenu` (where defined); `StackHost.kt` (Conversations, Chat).
**Test:** `app/src/test/java/com/coparently/app/presentation/chat/ChatBarsTest.kt`

**Interfaces:**
- Consumes: `titleContent`, `BarAction.menu` (Task 1).
- The thread's bar: `titleContent` is `ChatThreadHeader`'s initial, name and send status; actions are search (`magnifyingglass`) and the overflow as a menu (`ellipsis.circle`) with today's entries in today's order, Report destructive-free, each entry hidden where today hides it. While searching, the bar is today's search bar: `titleContent` is the search field, leading `Close` closes the search (Task 2's step does the same for back).
- Conversations: large title on the iPhone, the gear (`gearshape`), the new-chat floating button as a `plus` bar action under a host.
- Android: `ChatOverflowMenu` stays what Android draws if `BarAction.menu`'s Android rendering would differ by a pixel; the menu then exists for the iPhone only, through `shellDrawsBars()`.

- [ ] **Step 1: Write the failing tests** in `ChatBarsTest` under a recording host: the thread's bar has `titleContent`, two actions, the second with a menu whose labels equal today's overflow entries; in search mode the leading is `Close` and running it closes the search. Conversations: `largeTitle`, gear, plus.
- [ ] **Step 2: Implement**; add the routes.
- [ ] **Step 3:** Push; CI green, screenshots unchanged; the walk opens Chat, the thread's bar shows the co-parent's name, the overflow menu opens and closes.
- [ ] **Step 4: Commit** `feat: draw the chat's bars natively on the iPhone (MON-24)`.

### Task 8: The calendar

**Files:** `calendar/CalendarScreen.kt`, `calendar/components/CalendarHeader.kt`, `calendar/components/CalendarMonthBar.kt` (or wherever `CalendarMonthBar` lives), `calendar/SwapSelectionChrome.kt`; `StackHost.kt` (Calendar).
**Test:** `app/src/test/java/com/coparently/app/presentation/calendar/CalendarBarsTest.kt`

**Interfaces:**
- On the iPhone (under a host), spec §5.3: the bar's title is the month title the grid card shows today (`largeTitle = false`); actions in this order: Today (`calendar`, today's "Today" label), Filters (`line.3.horizontal.decrease.circle`), Settings (`gearshape`), and the primary "+" (`plus`, today's new-event label). The brand row and "Your family calendar" lines are not drawn; the Month/Week/Day segmented control stays as today's third header row, in the content. The grid card's month bar keeps its arrows and the swap button and drops Today and "+" under a host. Tapping the title's "Jump to date" stays on the card's title.
- While choosing days for a swap, the bar is `SwapSelectionHeader`'s content: title = its title, leading `Close` = its close, actions = its month arrows (`chevron.left`, `chevron.right`).
- Android: unchanged, every row.

- [ ] **Step 1: Write the failing tests** in `CalendarBarsTest` (compose `CalendarScreen` with mocked ViewModels as `CalendarScreenshots` does): under a host the shown bar's title equals the month title, the four actions in order with their labels; tapping "+" calls the new-event callback; no node with the wordmark's text. In swap selection the shown bar's leading is `Close` and its two actions page the month. Without a host the brand header is drawn.
- [ ] **Step 2: Implement**; add the route.
- [ ] **Step 3:** Push; CI green, screenshots unchanged (the calendar's baselines are the strictest check of this task); the walk's Calendar tab shows the month in the bar and "+" opens New event (Task 4's step).
- [ ] **Step 4: Commit** `feat: put the calendar's month and actions in the iPhone's bar (MON-24)`.

### Task 9: Root flows, every route native, and the keyboard once

**Files:** `onboarding/OnboardingScreen.kt`, the sign-in and consent screens' bars if they draw one (`auth/AuthScreen.kt`, `consent/TelemetryConsentScreen.kt`); `StackHost.kt`; `ScreenController.kt` (`ScreenFrame`).
**Test:** `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/shell/NativeBarRoutesTest.kt`, the walk.

- [ ] **Step 1: Write the failing test** `NativeBarRoutesTest`: every route of `AppDestinationCatalog.all` except those in `StackHost.NO_BAR_ROUTES` is native; `NO_BAR_ROUTES` holds exactly the routes whose screens draw no bar at all (Loading, and Auth if it draws none) — the test lists them literally.
- [ ] **Step 2:** Convert Onboarding (its back is Task 2's step; title as today). Replace `NATIVE_BAR_ROUTES` by `NO_BAR_ROUTES`: the native bar shows for every other route. Grep `shared-ui/src/commonMain` for `TopAppBar(`: only `ScreenBar.kt` may remain (a second hit fails this task).
- [ ] **Step 3: The keyboard, once per host.** `ScreenFrame` applies `imePadding()` to its content once; no screen adds its own on the iPhone (design item 14). The walk types into the chat composer and asserts the send button is hittable, and types into New event's description and asserts the field is hittable.
- [ ] **Step 4:** Push; CI green.
- [ ] **Step 5: Commit** `feat: draw every iPhone screen's bar natively and handle the keyboard once (MON-24)`.

### Task 10: `coplanly://` links on the iPhone

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/LaunchLinks.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/MainActivity.kt` (`readLaunchIntent` uses `LaunchLinks.parse` for the three custom-scheme hosts; behaviour unchanged)
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosApp.kt` (`fun openUrl(url: String): Boolean`, and the launch's pending holders replace `AppLaunchesOnIos.none()`)
- Modify: `iosApp/CoPlanly/SceneDelegate.swift` (both `scene(_:willConnectTo:)`'s `connectionOptions.urlContexts` and `scene(_:openURLContexts:)` hand every URL to Google first, then to `IosApp.shared.openUrl`), `iosApp/project.yml` (a second `CFBundleURLTypes` entry, scheme `coplanly`, kept beside Google's in the build phase that writes them)
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/LaunchLinksTest.kt`, the walk

**Interfaces:**
- Produces: `sealed interface LaunchLink { data class Pair(val code: String); data class Guest(val code: String); data class Chat(val familyId: String?, val conversationId: String?) }` and `object LaunchLinks { fun parse(url: String): LaunchLink? }`, reading exactly what `MainActivity` reads today (pairing through `domain/pairing/PairingUri`).
- Chat links switch the family (`SelectedFamilySource.select`) before arming, as `readLaunchIntent` does.

- [ ] **Step 1: Write the failing tests** in `LaunchLinksTest`: one per form `MainActivity` accepts today (copy the strings from its tests or the manifest's filters), each parsed to the expected value; an https `/pair/` link, an unknown host and garbage parse to null.
- [ ] **Step 2: Implement** the parser, switch `MainActivity` to it (Android's tests unchanged and green), and wire the iPhone.
- [ ] **Step 3: Extend the walk**: `XCUIApplication().open(URL(string: "coplanly://pair?code=ABC123")!)` while signed in shows the "Link with …?" path (a code the emulators do not know answers with the field's error, which is enough to prove the link reached Pairing).
- [ ] **Step 4:** Push; CI green.
- [ ] **Step 5: Commit** `feat: open coplanly:// links on the iPhone (MON-24)`.

### Task 11: Deleting the account on the iPhone

**Files:**
- Create: `shared-data/src/iosMain/kotlin/com/coparently/app/data/session/IosAccountDeletion.kt`
- Modify: `shared-data/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformDataModule.ios.kt` (bind it in place of `NoAccountDeletion`), `di/koin/IosUnavailable.kt` (remove `NoAccountDeletion`)
- Test: `shared-data/src/iosTest/kotlin/com/coparently/app/data/session/IosAccountDeletionTest.kt` (emulators, like the write gate), the walk

**Interfaces:**
- `class IosAccountDeletion(functions: CallableFunctions, database: CoPlanlyDatabase, preferences: SecurePreferences, traces: AccountTraces, authSession: AuthSession) : AccountDeletion` — calls the `deleteAccount` callable; only on its success: `database.clearAllTables()` (on `Dispatchers.IO`), `preferences.clear()`, the school prefixes removed as Android removes them, `traces.clearAfterSignOut()`, then `authSession.signOutCompletely()`. A failure deletes nothing locally and returns the failure; a cancellation is rethrown (Android's `AccountDeletionService` is the model, line by line).

- [ ] **Step 1: Write the failing test**: on the emulators, sign up a fresh account, write a profile and one event, call `deleteAccount()`; it succeeds, the Room tables are empty, the sealed store is empty, nobody is signed in, and the Auth emulator no longer has the account. A second case with the Functions emulator unreachable: failure, the event still in Room.
- [ ] **Step 2: Implement and bind.**
- [ ] **Step 3: Extend the walk**: Settings → Delete account → confirm → the app shows sign-in.
- [ ] **Step 4:** Update `docs/legal/PRIVACY-POLICY.md` and `RECORDS-OF-PROCESSING.md` only if they say deletion is Android-only (grep; Legal item 1).
- [ ] **Step 5:** Push; CI green.
- [ ] **Step 6: Commit** `feat: delete the account from the iPhone (MON-24)`.

### Task 12: Part A's small carried findings

**Files:** as each item names.

- [ ] **Google's session ends with ours.** `GoogleSignInBridge.swift` gains `signOut()`; the Kotlin interface it implements gains `fun signOut()`; `IosAccountTraces.clearAfterSignOut` (or the sign-out path that calls it) and `FreshInstall`'s first-launch clean both call it. Test: `IosCredentialSignInTest` with a fake bridge counts one `signOut` per app sign-out.
- [ ] **`IosApp.start` checks its double-start guard first**, before any side effect. Test in `IosKoinGraphTest` or a new `IosAppStartTest`: a second `start` changes nothing.
- [ ] **`CFBundleLocalizations`** in `iosApp/CoPlanly/Info.plist` lists exactly `en, cs, de, ru, uk` (`AppLanguageRule.SHIPPED`), so iOS's chrome follows the same rule; `ShippedLanguagesTest` (app) gains a check that reads the plist and compares.
- [ ] **`tools/ci-changes.js`: an unknown path sets `ios` true**, as every other output does; a case in `tools/test/ci-changes.test.js`.
- [ ] **The write gate's two minors**: `IosWriteGateTest` sends one message with a non-empty `attachments` list (a reference string, no upload) and records one event delete, and `tools/ios/check-ios-writes.js`'s rules compare both (drop the `NO_STORAGE` exemption for the message's `attachments` only).
- [ ] **Settings' sign-out is not cut short**: the `AuthStateViewModel` Settings uses on the iPhone is the shell's (the one deciding the start destination), not one scoped to the Settings entry. Test: `IosKoinGraphTest` or the walk's sign-out step from Settings ends on sign-in with the session cleared.
- [ ] Push; CI green. **Commit** `fix: close part A's carried iPhone findings (MON-24)`, and remove each closed bullet from ROADMAP MON-24 "Still owed from part A".

### Task 13: The iPhone tour and the record

**Files:**
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift` (a `testTour` that visits every destination reachable from the seeded family and attaches an `XCUIScreenshot` named after the route), `.github/workflows/ci.yml` (`ios-app` uploads the attachments as artifact `ios-tour`, extracted with `xcrun xcresulttool`), `tools/ios/seed-shell-family.js` (only what the tour needs to reach a screen: a child, a pet, a journal entry, a document index row, a change request)
- Modify: `CLAUDE.md` ("Where an iPhone path lives": part B's shapes — `shellDrawsBars`, `BackSteps`, `LocalScreenVisible`, `LaunchLinks`, `IosAccountDeletion`), `docs/ROADMAP.md` MON-24 (part B built; the remaining stand-ins), the spec's §7 table (what part B changed), `iosApp/README.md`
- Create: `docs/superpowers/plans/2026-11-13-iphone-phase-3b-owner-check.md` (the owner's Mac run: every tab, each form sheet, back in CustodySetup, a `coplanly://` link, account deletion with a test account)

- [ ] **Step 1:** The tour test; a screen it cannot reach is recorded in its log with the reason, never a failure (as the Android UI tour).
- [ ] **Step 2:** CI uploads `ios-tour`; push; green; read the artifact's list against `AppDestinationCatalog.all` and name every missing route in the owner check.
- [ ] **Step 3:** The docs.
- [ ] **Step 4: Commit** `docs: record iPhone phase 3 part B (MON-24)`.
