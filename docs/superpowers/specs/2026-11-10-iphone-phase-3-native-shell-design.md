# CoPlanly for iPhone, phase 3: the native shell

**Status:** approved by the owner, 10 November 2026. A builder read the draft; §10 lists the calls
made on its questions.
**Parent design:** `docs/superpowers/specs/2026-10-27-iphone-app-design.md` (§5.2, §5.3, §6 phase 3).
This document replaces that design's §5.2 and narrows its §5.3 into parts. Where the two disagree,
this one is newer.
**Roadmap item:** MON-24.
**The look:** the Design canvas "CoPlanly для iPhone",
https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH. Seven pairs: the Android screen today on the
left, the iPhone screen on the right.

## 1. Intent

Phases 1 and 2 made the app one Kotlin codebase. Nothing of it runs on an iPhone yet: there is no
Xcode project, no entry point, and every iPhone capability is a stand-in.

Phase 3 makes an iPhone app that starts, signs in and shows a family's real calendar, chat, money
and requests on the simulator. Its frame is Apple's own: the tab bar, the navigation bars, the
back gesture, the sheets. The screens inside are the shared Compose screens.

The owner wants the iPhone to feel like an iPhone, and approved that look on the canvas in October
and again on 10 November. This phase is where that look is first built.

## 2. Decisions

| Decision | Answer | Who, when |
| --- | --- | --- |
| How is the shell built? | **Everything native**: tab bar, navigation bars and the back gesture are the system's, on every screen. The parent design's spike was to choose between this and Compose-drawn bars; the owner chose without it. | Owner, 10 Nov 2026 |
| Who judges how it feels? | The owner, on the simulator on their own Mac. CI builds it and takes screenshots. | Owner, 10 Nov 2026 |
| Which sign-in does phase 3 have? | E-mail and password, and Google. Sign in with Apple stays in phase 4. | Owner, 10 Nov 2026 |
| Bundle identifier | `app.coplanly`, the Android application id. Permanent after the first App Store upload. | Owner, 10 Nov 2026 |
| Tab bar on pushed screens | Always visible. Android hides its bar on detail screens; the iPhone does not. | Owner, 10 Nov 2026 |
| Lists of records | One grouped list with separators on iPhone (expenses, children, pets, documents), not a card per record. | Owner, 10 Nov 2026 |
| The seven iPhone boards on the canvas | Accepted as the target for the shell: Home, Calendar, Chat, New event (October), Family, Expenses, Settings (November). | Owner, 10 Nov 2026 |
| What a screen shows where a board differs from Android | What Android shows: the boards bind the frame, the controls and the style, not a screen's content. | Owner, 10 Nov 2026 |
| The release build on the new toolchain | Built from `main` after the merge (`release.yml`), then installed over the beta on a phone. | Owner, 10 Nov 2026 |
| Before merging phases 1–2 | A final review of the whole branch, then the full CI, then the merge of pull request #158. Phase 3 starts from `main` after it. | Owner, 10 Nov 2026 |

How design is agreed from here on:

- **A new kind of thing gets a before/after pair on the canvas before it is built**, drawn by the
  session that plans the part: a kind of screen the owner has not seen on iPhone (sign-in,
  onboarding, a form sheet other than New event) and each kind of adaptive control (part C).
- **A screen that only applies rules already agreed needs no pair first.** §5.3 to §5.5 are those
  rules for bars, sheets and insets.
- **Everything built is shown after**, as simulator screenshots beside the Android ones.

**What the boards bind.** They bind the frame (bars, tab bar, sheets), the controls and the style.
They do not bind a screen's content: what a screen shows is what the shared screen shows on
Android, because a screen is written once. Where a board's content differs from the Android screen
(§10 lists the places), the Android content stands: the owner confirmed it on 10 November.

## 3. Approaches

| | A. Native bars everywhere (**chosen**) | B. Native tab bar, Compose bars | C. All Compose |
| --- | --- | --- | --- |
| Tab bar | System, Liquid Glass on iOS 26 | System, Liquid Glass | Drawn |
| Top bar, back | System bar, system edge swipe | Drawn to Apple's spec, Compose's swipe | Drawn |
| Shared code touched | Every screen's top bar becomes a description; navigation gets a second host | One adaptive bar component | None |
| Cost | Highest | Middle | Lowest |

C was dropped: no glass anywhere, against the owner's "as much like an iPhone as one codebase
allows". B was the author's recommendation as a base, with A tried on one tab. The owner chose A
outright: it is the look on the approved canvas, and a bar that only resembles the system's is the
kind of near-miss people notice.

What A costs, stated so it is not a surprise later:

- 45 top bars and 51 destinations in the shared code are re-declared (§5.3, §5.4). Android must
  draw exactly what it draws today.
- Navigation has two hosts: Compose's `NavHost` on Android, a UIKit stack on iPhone. Both are
  driven by the same navigation controller type, the same routes and the same graph (§5.2).
- Compose content is not a system scroll view. A large title that collapses on scroll, and the
  blur at a bar's edge, are built for one. §7 says what is tried and what the fallback is.

## 4. Goals, non-goals, anti-goals

**Goals**

- An iPhone app (`app.coplanly`, iOS 17 or later) that builds in CI and on the owner's Mac.
- A parent signs in with e-mail or Google and sees their family's real data, in step with the
  other parent's Android phone.
- The tab bar, every navigation bar, the back button and gesture, and form sheets are the
  system's. The seven boards on the canvas are met.
- The adaptive controls of the parent design's §5.3, and the iPhone theme (system colours, SF Pro
  with Dynamic Type, SF Symbols).
- One feature, one code: a screen never asks which platform it is on (parent design §5.3).
- Android draws and does exactly what it does today. The 470 screenshot baselines do not change.

**Non-goals (phase 3)**

- Pushes, reminders, background work, the widget, receipt OCR, QR scanning, dictation, the PDF
  export, Sign in with Apple, Universal Links, the Google Calendar import. These are phase 4. Their
  stand-ins stay, and the screens keep hiding what they stand for.
- A real iPhone, TestFlight, the App Store (phases 5 and 6).
- An iPad design (parent design §4).

**Anti-goals**

- A changed pixel or behaviour on Android.
- An iPhone build that writes a document differently from Android (parent design §4). Phase 3 is
  the first time the iPhone writes to the real server, so this is where it would first show.
- A bar button, row or control on iPhone that leads to a stand-in (CLAUDE.md design item 8).
- A screen that draws its own imitation of a system control where the system's can be used.

## 5. How it is built

### 5.1 The app and its entry point

A new directory, `iosApp/`, holds the Xcode project. It is generated by XcodeGen from a
`project.yml`, so the project file is never edited by hand and a cloud session can change it. It
links the static `Shared` framework that `:shared-ui` already builds, and the Firebase iOS SDK
11.8.0 (the version GitLive's bindings were made against) through Swift Package Manager: Core,
Firestore, Functions, Auth, Storage.

Swift stays thin, as `MainActivity` is on Android:

- the app delegate configures Firebase, hands the launch to Kotlin and installs the root view
  controller Kotlin returns;
- anything only a Swift SDK can do sits behind an interface declared in Kotlin, which a Swift
  class implements and passes in at start. Google Sign-In is the first (§5.6). This is the rule
  for phase 4's SDKs too. A Kotlin binding to such an SDK is not added: a Linux host cannot
  generate it, and the Linux job that compiles the iPhone code on every pull request would stop.

Everything else is Kotlin in `:shared-ui`'s `iosMain`: starting Koin with the four module lists,
the twin of `CoPlanlyApplication.onCreate` (the chat mirror, the profile synchronizer, a first
sync), and the shell itself. Kotlin drives UIKit directly. One reason is that the navigation's
state then has one owner. The other is that the Linux job type-checks the shell.

`GoogleService-Info.plist` is not committed, as `google-services.json` is not. Without it the app
points at the `demo-coplanly` project and the Firebase emulators, which is what CI runs. With it,
on the owner's Mac, the app reaches the real project.

### 5.2 The shell and navigation

The shell is a `UITabBarController` with four tabs (Home, Calendar, Chat, Family), each a
`UINavigationController`. Built with the iOS 26 SDK, these draw Liquid Glass on iOS 26 and the
classic bars on iOS 17 and 18 without code of ours. Before sign-in (loading, sign-in, onboarding)
the window shows one navigation controller with no tab bar; the shell swaps the two when the start
destination changes, as `startDestinationFor` decides today. The telemetry question is not asked
on iPhone in phase 3: the iPhone collects nothing and links no Analytics or Crashlytics, so there
is nothing to ask about. The answer stays "unanswered", and phase 4 asks if it adds telemetry.

Each pushed screen is its own view controller hosting one Compose screen.

**The shared navigation stays the source of truth.** Each stack has a `NavHostController` of the
same multiplatform navigation library Android uses, with the same graph and routes. On iPhone no
`NavHost` composable draws it. A small host in `iosMain` watches the controller's back stack and
makes the UIKit stack match it: a new entry is pushed, a removed one popped. When the user goes
back by the system's button or swipe, the host pops the shared controller. So every
`navController.navigate(...)` and `popBackStack()` in the shared screens works unchanged, and
ViewModels stay scoped to their back stack entry, as on Android.

Three things follow.

- **Destinations are declared through our own builder.** The library keeps a destination's
  content private to its own `NavHost`, so the iPhone host cannot draw one declared with
  `composable()`. `NavGraph.kt` declares its 51 destinations through one function of ours (today's
  `pane()`, widened to the tabs), which on Android calls `composable()` with today's transitions
  and on iPhone registers the content with the host. The destination's route, arguments and
  content lambda do not change.
- **Tabs are stacks, not destinations.** `navigateToTab` on iPhone selects a tab. A push from
  inside a tab goes onto that tab's stack, as on Android: Home's month tile opens Expenses on
  Home's stack and Back returns to Home. A money notification's "Family, then Expenses" selects the
  Family tab and pushes onto its stack. The
  rule that every tap on the Calendar tab opens Month on today (`CalendarTabTaps`) is fed by the
  tab bar's delegate. The Chat tab's badge is the tab item's badge.
- **What the whole app shares is provided to every screen host.** The theme, the platform
  capabilities, the parent palette, the family on screen, the app-wide messages and the
  ViewModels that Android scopes to the activity come from one owner held by the shell, so the
  four tabs do not each get their own copy. *(As built in part A: the ViewModels that decide the
  start destination and the offline line (`AuthStateViewModel`, `SyncViewModel`,
  `ConnectivityViewModel`) live as long as the shell; the parent palette's, the family view's and
  the Chat badge's are scoped to the signed-in session and cleared at sign-out, stricter than
  Android's activity scope, so nothing of one account reaches the next.)*

Deep links arm as on Android: the shell switches the family first, then opens the target
(CLAUDE.md item 15).

### 5.3 Bars

A screen describes its bar instead of drawing one:

- a title, and whether it is the large title of a tab's first screen;
- the leading action: back, close, or none;
- its actions, each with a label, an icon, enabled or not, an optional dot, an optional menu, and
  whether it is the screen's main action.

On Android the description is drawn as the Material top bar that is there today, pixel for pixel.
On iPhone it sets the system bar: the title, bar buttons with SF Symbols, system menus. The
45 top bars in the shared screens move to this, in one sweep that changes no Android image.

Decided by the canvas:

- a tab's first screen has a large title and its actions as glass buttons (gear; the family
  switcher at two families);
- a pushed screen has the system's back button and an inline title; Settings keeps a large
  title, as on its board;
- a floating "+" on Android is a bar button on iPhone (Calendar, Expenses);
- the calendar's title is the month. Month/Week/Day is a segmented control under the bar on
  iPhone (the board, and the parent design's §5.3) and stays the title's menu on Android: one
  adaptive control, two looks. Today and "+" are bar buttons. Filters and the gear stay reachable
  from the bar, as on Android. Choosing days for a swap replaces the bar's content, as
  `SwapSelectionHeader` does on Android;
- the chat thread's bar shows the co-parent's initial and name, search and the overflow menu;
- the offline line sits under the bar, in the screen.

### 5.4 Forms are sheets

On iPhone a form opens as a system sheet over the screen it came from, with a close button and a
confirm button in its bar: the "New event" board. This holds for every form route: event,
expense, child, pet, journal entry, change request, and the rest of that kind. A destination is
marked as a form where it is declared; Android ignores the mark and pushes as today.

- The form's sticky Save on Android is the sheet's confirm button on iPhone. `StickyActionBar`
  becomes the description of a screen's main action; Android draws it as today.
- A form with edits is not dismissed by a swipe: the sheet asks "Discard changes?" first, by the
  same rule as `rememberDiscardGuard` (CLAUDE.md design item 16).

### 5.5 Insets and scrolling

On iPhone the screen's content runs under the bars, and the bars' heights reach it as content
padding of its scroll container, so a list scrolls beneath the glass. Design item 14 ("insets are
applied once") is kept in the form it takes here: applied once per screen host, by the shared
scaffold, never by a screen itself. The keyboard is handled the same way, once.

On Android nothing changes: the padding a screen receives is what it receives today.

### 5.6 Signing in, and what becomes real

Phase 3 replaces these stand-ins with real halves, because a signed-in app cannot run on them:

| Capability | iPhone half in phase 3 |
| --- | --- |
| Account (`SignedInUser`, `AuthSession`, `AccountSignIn`, the account switch guard and traces) | Firebase Auth through GitLive: e-mail and password, sign-out, the wipe on an account switch |
| Google sign-in (`CredentialSignIn`) | Google Sign-In for iOS, in Swift behind a Kotlin interface; the credential goes to Firebase Auth. No saved-password sheet: iOS's own password autofill fills the fields |
| Sync (`SyncRequester`) | A foreground runner: at start, on return to the foreground, on pull to refresh and when pairing changes. One run at a time, and a request during a run means one more run, the two rules `SyncWorker` holds on Android |
| Wording (`PairingWording`, the school import's titles) | The shared strings, in the app's language |
| Images (`PlatformImages`) | Account pictures and record photos |
| Links, sharing, clipboard, the file and photo pickers, the language row | The system's (part C) |
| Storage (`StorageBucket`), for attachments and photos | Firebase Storage |
| Telling the other parent (`PushQueue`) | Real in part A. Queueing a push is a Firestore write, the same on both platforms; only *receiving* one is phase 4. Without it a request made on an iPhone would reach the Android co-parent with no notification at all, since the request's chat card is one the server does not push (CLAUDE.md item 15) |
| Deleting the account (`AccountDeletion`) | Real in part B: the server's deletion and the local wipe. Hidden until then |

Three defects the final review found in the iPhone half are fixed first, before any of the above
runs for real:

- **A reinstall must start clean.** The Keychain store survives deleting the app and the database
  does not, so a reinstall would keep an old sync cursor and download almost nothing for a day,
  and would keep the previous Google and school tokens. A first-launch marker outside the Keychain
  clears the store.
- **Dates are formatted in UTC.** The date seam builds an instant in one time zone and may print
  it in another. Wall-clock dates are built and printed on UTC.
- **The owner's language rule applies on iPhone**: the phone's first language when the app ships
  it, otherwise English (`en-150`), and the app's own language setting in iOS Settings wins. One
  function answers "the reader's locale" for strings, dates and money. The Language row in
  Settings opens iOS Settings, where iOS keeps that choice.

Everything else stays a stand-in until phase 4, and each row, button or switch that leads to one
is hidden. Two are drawn today and get their check in part A: the event form's Reminder row (no
reminder fires on iPhone until phase 4; a new event still carries the default reminder, which the
co-parent's Android phone acts on) and Settings' delete-account row, which calls
`NoAccountDeletion` (it fails with a message and deletes nothing until part B; before the App Store
it needs real deletion or an `AccountDeletion.available` flag gating the row).

**Before the iPhone writes to the real project.** The wire fixtures hold what the mappers produce.
They do not hold what GitLive's iOS encoder then sends, or what the rules make of it: no test has
ever sent an iPhone write to a server. So part A has a gate before any sign-in to the production
project: on the Firebase emulators, with the real rules, the iPhone data layer writes one document
of every synced collection and every kind of push, and a reader on the other side checks each
value's type and content against what Android writes. The owner's first sign-in on the real project
is with a test pair of accounts; their own family comes after the gate is green.

## 6. Parts

Phase 3 is four parts. Each has its own plan and its own pull request, starts from the look the
owner has agreed, and ends with simulator screenshots beside the canvas.

| Part | What | Leaves |
| --- | --- | --- |
| **A. Proof and skeleton** | The Xcode project and its CI job. The shell with the four tabs. Native bars on the tabs' first screens and on one path three levels deep (Family → Expenses → Add expense as a sheet) and Settings. Sign-in by e-mail and Google. The foreground sync. The three fixes and the write gate of §5.6. | The owner signs in on their Mac and walks that path with real data. §7's open points get their answers. |
| **B. Every screen** | All 45 bars and 51 destinations on the shell. Every form a sheet. Insets as §5.5. Deep links. | Every screen reachable on iPhone with a system bar. Android unchanged. |
| **C. Adaptive controls** | The parent design's §5.3 table: date and time pickers, switch, segmented choice, menus, in-screen sheets, confirmation dialogs and action sheets, grouped rows and lists, the share, file and photo pickers. | Controls are the system's. Each kind is agreed as a before/after pair first. |
| **D. Theme** | System colours for backgrounds, labels and separators with the CoPlanly indigo as tint; dark mode; SF Pro and Dynamic Type; SF Symbols in content, mapped one to one from the 131 Material icons in use; Increase Contrast. | The seven boards are met, in light and dark. |

Part A is first because everything else stands on it and because its risks are the ones that could
change the design (§7). Parts B to D are planned in detail when A's answers are in.

## 7. What part A must prove

These are the places where "everything native" meets Compose. Each has a first try and a fallback
that keeps the bars native. If a fallback is not good enough either, the owner decides with the
simulator in front of them; nothing is swapped quietly.

1. **The stack host.** A UIKit stack mirroring the shared back stack, both ways, including a
   swipe back that is started and then cancelled, a pop of several screens at once, and state
   surviving a tab switch. No fallback: if this cannot be made sound, approach A is not built and
   the owner is told what failed.
2. **Large titles.** First try: the system's large title, collapsing as the Compose list scrolls,
   through a scroll view the host keeps in step with the list. Fallback: the large title is the
   first thing in the screen's content, as on the canvas, and the bar shows the small title once
   that has scrolled away.
3. **The edge of the bars over scrolling content** on iOS 26 (the soft blur). First try: the same
   scroll view as in 2. Fallback: none of ours; the glass buttons and the tab bar still refract the
   content beneath them.
4. **The keyboard** in the chat thread and in a form sheet: the composer and the focused field
   stay visible, as design item 14 requires on Android.
5. **A form sheet with edits** refusing a swipe and asking first (§5.4).
6. **Memory and speed** with four tabs alive and a stack three deep, each screen its own Compose
   host.

**Where part A stands** (13 November 2026). Part A is built and its CI is green (run 43316189630:
the `ios-app` job's XCUITest walk of 15 steps on an iPhone 17 Pro simulator, iOS 26.5, against the
Firebase emulators). None of the six points is decided yet: what CI proved is below, and the rest
of each answer comes from the owner's run of
`docs/superpowers/plans/2026-11-10-iphone-phase-3a-owner-check.md` (the item named beside each).

| Point | What CI and the walk proved | What the owner's run answers |
| --- | --- | --- |
| 1. The stack host | `StackMirrorTest` (16 cases: several pops, a different root, a cancelled swipe, a push during a transition). On the simulator: the edge swipe pops Expenses and the shared stack follows; a swipe let go of leaves Expenses, and Data sources under its own Compose bar, with one bar and the count of cancelled transitions up; a tap on the selected tab pops two screens; sign-out ends the tabs and signing in opens fresh ones. | How the back button, the swipe and the tab re-tap feel, and state surviving tab switches by hand (2.4, 2.5, 2.10). |
| 2. Large titles | Home, Family and Settings show the system's large title. It does not collapse: the scroll view the first try needs was not built in part A, so the title is set to show always. | Whether a fixed large title is acceptable, or part B builds the first try or the fallback (2.2). |
| 3. The edge of the bars | Nothing: each screen pads its content below the bar, so no content scrolls under the glass and the soft edge has nothing to blur. | Whether that is acceptable, or part B runs the content under the bars with point 2's scroll view (2.3). |
| 4. The keyboard | Only that the walk's sign-in completes with the keyboard up. | The form sheet and the chat thread (2.7, 2.8). |
| 5. A form sheet with edits | Add expense opens as a page sheet; a swipe down over an edit asks "Discard changes?", "Keep editing" keeps it, Close asks again, Discard closes; a form with its own Compose bar shows one bar. | How it feels (2.6). |
| 6. Memory and speed | Nothing measured. | The gauges with four tabs and a stack three deep (2.9). |

## 8. Tests and CI

- **Android is the regression test for the shared code.** Every part runs the full Android CI:
  the 470 screenshot baselines unchanged, the two-phone e2e tests, the UI tour.
- **The Linux job compiles the iPhone code** on every pull request, as today. The shell is Kotlin,
  so most of it is checked there.
- **A new macOS job builds the app** for the simulator and runs the shared tests, when the diff
  reaches shared or iPhone code (`tools/ci-changes.js` gains an `ios` output).
- **An iPhone tour**, like the Android UI tour and run the same way (by request, never on every
  push): the app on a simulator against the Firebase emulators with the tour's seeded family, a
  screenshot of each screen, pushed to a branch of their own. This is how a cloud session and the
  owner both see what was built.
- **The stack host has unit tests** for its mirroring rules, run on the simulator job.
- The halves that have never run on iOS (dates, money, the Keychain store, directories,
  connectivity, Firestore reads, callables) run for the first time in part A. The seams that write
  to Firestore are already held to the wire fixtures on the simulator.
- The cross-platform e2e test, the XCUITest smoke and the device checklist for iPhone are phase 5.

## 9. Constraints

- A cloud session cannot build or run iOS. Every iPhone build is CI's or the owner's Mac's.
- The app is built with the iOS 26 SDK (Xcode 26) for Liquid Glass. Kotlin 2.4.21 supports Xcode
  up to 26.4; the owner's Mac has run a newer one with warnings. The CI image's Xcode is pinned.
- The owner registers the iPhone app `app.coplanly` in the Firebase project, enables Google
  sign-in for it and puts the downloaded `GoogleService-Info.plist` on their Mac. Nothing else of
  phase 3 needs a console.
- The server does not change in phase 3.
- Pull request #158 (phases 1–2) is merged first, after the final review's fixes and the full CI.
- Before an iPhone build reaches any user: MON-25 (request clocks decided on the server), the
  privacy documents for the iPhone's storage, and the place of the database file (the widget of
  phase 4 reads it through an App Group, so it is chosen before the first TestFlight build). None
  of these blocks phase 3.

## 10. Left to the builder, and calls made here

Left to the builder:

- The exact shape of the bar description and of the destination builder.
- How the app-wide messages (snackbars that outlive a screen) are shown on iPhone.
- The tool that drives the iPhone tour.
- The order of screens inside part B.

Calls made while writing this. They are the author's; the owner may overturn any of them.

- **UIKit driven from Kotlin, not SwiftUI.** The parent design said a SwiftUI `TabView`. UIKit's
  tab and navigation controllers are the same system bars with the same glass, and they let
  Kotlin own the navigation. The owner sees no difference.
- **XcodeGen** for the project file, and **Swift Package Manager** for Firebase and Google
  Sign-In.
- **Minimum iOS 17**, as the parent design called it. The shared modules' deployment target (15.0
  today) is raised to match.
- **iPhone only** as the device family until phase 5 decides how an iPad runs it, and **portrait
  only**: the shared code's rail (from 600 dp) and two panes (from 840 dp) are for tablets and do
  not meet the system tab bar on a phone held sideways.
- **The boards' content is illustration** (confirmed by the owner, §2). Places where a board differs from the Android screen,
  and what is built: Home keeps its two stat tiles (the board has none); the calendar has no
  selected-day card under the grid (the board has one; on Android a tap opens Day view); the chat
  keeps its ticks and the header's send status (the board says "Read 9:44 AM"). Changing any of
  these is a change to both apps, and a decision for the owner.
- **The write gate of §5.6**, and a test pair before the owner's own family.
- **Outgoing pushes are real from part A; account deletion from part B; the telemetry question
  waits for phase 4** (§5.2, §5.6).
- **The Theme setting stays** on iPhone (Light, Dark, System), as on the Settings board.
- **Settings is pushed** onto the stack of the tab it was opened from, as on the board.
- **Swift-only SDKs sit behind Kotlin interfaces** (§5.1).
