# CoPlanly for iPhone: one Kotlin codebase, an iPhone that feels like an iPhone

Owner request, 27 October 2026: "I want a version for iPhone too … the idea is to keep the changes
in sync for both iPhone and Android. Of course we finish and test Android first."

This document records what the owner decided in that conversation, what was left to the builder,
and the order the work goes in. **Nothing here starts before the Android app is released** (owner
decision). Until then the document is a plan of record and the code does not change for it.

`docs/ROADMAP.md` MON-17 says "a native iOS app is an owner decision and not planned". This
document is that decision. The roadmap entry for the work is **MON-24**.

## 1. Intent

Two separated parents do not choose each other's phones. Today a parent with an iPhone gets
only the read-only calendar feed (MON-17). The goal is the whole app on an iPhone, working with
the Android app as one product.

"In sync" means two things, and the owner wants both:

1. **Data.** A parent on an iPhone and a parent on Android see one calendar, one chat, one ledger
   and one set of requests. The backend already holds all of it: Firestore, the rules, the Cloud
   Functions and Storage. What the iPhone needs is a client that reads and writes **exactly** the
   documents the Android client writes.
2. **Code.** A feature is written once and ships on both platforms. A rule fixed once is fixed on
   both.

## 2. Decisions

| Decision | Answer | Who |
| --- | --- | --- |
| Is there an iPhone app? | Yes. | Owner |
| When does work start? | After the Android release, and after its first round of feedback. | Owner |
| What does the first iPhone version do? | Everything the Android app does, as far as the platform allows (§6). | Owner |
| How is it built? | One Kotlin codebase, Compose Multiplatform, iOS-adapted on the iPhone ("the middle way", §3). | Owner, after seeing both looks on the canvas |
| How should it look? | As much like an iPhone app as one codebase allows. | Owner |
| Hardware and accounts | The owner has a Mac and an Apple Developer account. They do not have an iPhone yet; one is needed before TestFlight (§8). | Owner |

The two mockups the owner looked at:

- the Android screens framed as an iPhone: https://claude.ai/artifact/3N9KWQFhGQ3YwgMspgtByj
- the Apple-style screens (Home, Calendar, Chat, New event): https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH

The second is the target look. The middle way will reach most of it, though not all of it (§3).

## 3. Approaches

| | One look (Compose Multiplatform as is) | **The middle way (chosen)** | Kotlin logic + SwiftUI screens |
| --- | --- | --- | --- |
| Shared | Logic, data, sync, screens | Logic, data, sync, screens | Logic, data, sync, ViewModels |
| Written twice | Widget only | Widget, the app shell, a small set of adaptive controls | Every screen (≈72k lines of Compose today) |
| iPhone look | The Android design inside an iPhone frame | iOS navigation, tab bar, sheets, pickers, switches, SF Symbols and SF type. CoPlanly's own drawing (custody grid, cards) is the same on both | Fully native, Liquid Glass everywhere |
| First iPhone release | ≈4–6 months | **≈5–7 months** | ≈5–8 months |
| Each later feature | Once | Once (rarely, an adaptive control) | Logic once, screens twice (+40–60 %) |

Why the middle way: it keeps the owner's main goal, a feature written once, and buys most of the
iPhone look where a user touches it most. Full SwiftUI was dropped because every feature would be
built twice for good. The plain shared look was dropped because the owner wants the iPhone to feel
like an iPhone.

**What the middle way cannot do.** Compose cannot draw Apple's Liquid Glass material. The glass
therefore appears only where native UIKit or SwiftUI controls are hosted (§5.2): the tab bar, the
navigation bars and system sheets. Inside a screen, the surfaces are CoPlanly's own.

The estimates are for full parity. They assume the same way of working as the Android app: Claude
sessions write the code, a Mac and CI build it, and the owner tests on devices.

## 4. Goals, non-goals, anti-goals

**Goals**

- One Kotlin codebase for both platforms: domain, data, sync, ViewModels and screens.
- The iPhone client is byte-for-byte compatible with the Android client on every collection,
  codec and push. `app/src/test/resources/wire/` is the contract both platforms run.
- On iPhone the app uses Apple's navigation, tab bar, sheets, date and time pickers, switches,
  menus, share sheet, icons (SF Symbols) and type (SF Pro, Dynamic Type).
- The first iPhone release has full parity with Android (§6 lists the platform substitutes).
- Android stays shippable at every step of the migration. CI stays green on every merge.

**Non-goals (first iPhone release)**

- A separate iPad design. An iPad runs the phone layout and the two-pane layout Android tablets
  already get (CLAUDE.md design item 19). It gets no layout of its own.
- Apple Watch, macOS, CarPlay and Siri.
- Importing from Apple Calendar (EventKit). Google Calendar import is kept, and Apple Calendar
  keeps reading the MON-17 feed.
- Live Activities, for the reason CLAUDE.md design item 20 gives for Android's Live Updates.

**Anti-goals: a failure even if it works**

- An iPhone build that writes a field, codec entry, timestamp or audience differently from
  Android. The other parent's phone then shows a wrong schedule or a wrong balance, or drops a
  record. This is the one failure the product must not have.
- A migration step that breaks or regresses the Android app.
- Rules, Cloud Functions or a key relaxed or added "for iOS". The server stays the same server,
  apart from what §5.5 adds.
- An iPhone control that promises something the platform does not do (CLAUDE.md design item 8).
- The privacy promises weakening on iPhone. Receipt OCR, dictation and the journal stay on the
  device. Pushes carry a type, never a sentence.

## 5. How it is built

### 5.1 The migration (Android only, before any iPhone screen)

The Android app moves into Kotlin Multiplatform without changing behaviour. Each step is its own
pull request with the full CI. The order is chosen so that each step is small and can be reverted.

1. **Toolchain.** Kotlin, KSP and Compose versions that Compose Multiplatform supports. A
   `shared` module (`commonMain`, `androidMain`, `iosMain`), and an iOS target that compiles
   empty. A CI job on a macOS runner builds the iOS framework.
2. **Domain to `commonMain`.** `java.time` becomes `kotlinx-datetime`. Gson becomes
   `kotlinx.serialization`. `WireContractTest`, `CurrentWireFixturesTest` and the `upgrade` job
   must show **no fixture diff**. A diff means the wire format moved, and that is a bug, not a
   regeneration. The R8 probe's Gson cases shrink as Gson leaves.
3. **Dependency injection.** Hilt is Android-only. It is replaced by a multiplatform container.
   Koin is the default and kotlin-inject the alternative; the builder picks one, and the choice is
   recorded with its reason.
4. **Data layer.** Each Android-only dependency gets a common interface and two implementations:
   - Room becomes Room KMP; the schema and `app/schemas/` stay as they are.
   - Firebase goes through a common wrapper, either GitLive's `firebase-kotlin-sdk` or
     `expect`/`actual` over each native SDK. The builder spikes Firestore offline persistence and
     `Source.SERVER` reads on both before choosing.
   - OkHttp becomes Ktor (Bakaláři, Google Calendar).
   - `EncryptedPreferences` becomes Keystore on Android and Keychain on iOS, through one interface.
   - WorkManager stays on Android; iOS gets its own implementation (§5.4).
5. **Presentation to `commonMain`.**
   - Lifecycle ViewModel and Navigation come from their multiplatform artifacts.
   - Strings become Compose Multiplatform resources, all five locales;
     `tools/check-invariants.js` learns the new location.
   - Coil 2 becomes Coil 3, and the calendar library its multiplatform artifact.
   - Roborazzi screenshots and every Android test keep running from the Android target.
6. **The Android app becomes a thin shell** over `shared`. The release, R8, upgrade, e2e and
   UI-tour jobs all still pass.

Every invariant in `CLAUDE.md` survives the move unchanged. The migration moves code; it does not
rewrite rules. Where an invariant names a file path, the path is updated in the same pull request.

### 5.2 The iPhone shell

The app's outer frame is native SwiftUI:

- a `TabView` with the four tabs (Home, Calendar, Chat, Family), so the tab bar is Apple's own,
  with its glass;
- each tab's content is a Compose screen hosted through `ComposeUIViewController`.

Inside a tab, navigation stays the shared Compose `NavHost`, with an iOS-styled top bar
(`AdaptiveTopBar`).

**The builder's first iPhone task is a two-day spike of this shell**, with two things to test:

- whether the shared navigation can drive native `NavigationStack` bars and the edge-swipe back;
- whether the Compose bars and Compose's own back gesture are close enough.

The spike decides which way is used. The result goes back into this document.

### 5.3 Adaptive controls

These are a small, closed set in `commonMain`. Each has an Android `actual` that is today's
Material component and an iOS `actual` that hosts a UIKit control or draws to Apple's spec:

| Control | iPhone |
| --- | --- |
| Date picker, time picker | `UIDatePicker` (compact, inline wheel) |
| Switch | `UISwitch` |
| Segmented choice (Month / Week / Day) | `UISegmentedControl` look |
| Menu (Repeat, Reminder, overflow) | `UIMenu` |
| Sheet | iOS sheet with detents and grabber |
| Confirmation dialog | `UIAlertController` (action sheet for destructive) |
| Share, file and photo pickers | System sheets (already system on Android) |
| Icons | SF Symbols via `UIImage(systemName:)`, mapped one-to-one from today's Material icons |
| Type | SF Pro with Dynamic Type on iPhone; Onest stays on Android (§7) |
| Grouped rows | `SectionGroup`/`SectionRow` draw inset-grouped on iPhone (larger radius, iOS separators) |

**A screen never branches on the platform itself.** It uses the adaptive control. That keeps "one
feature, one code" true. A new adaptive control is added to this table first.

**CoPlanly's own visual language stays shared:**

- parent colours (`ParentColors`);
- the custody wash, the weekend base and the handover diagonal (`DayCellFills`);
- `MiniMonth`;
- the cards.

On iPhone the theme maps Material roles to Apple's system colours (system background, grouped
background, label, secondary label, separator), and the CoPlanly indigo is the tint.

### 5.4 Platform features without a shared implementation

| Android today | iPhone |
| --- | --- |
| FCM data push, worded on the phone (`PushNotifier`) | APNs through FCM. A **Notification Service Extension** words the push from the app's own strings, so the server still sends a type and never a sentence (CLAUDE.md item 15). |
| WorkManager: reminders, request clocks (item 42), support reminders, the school import, sync | **Local notifications** for anything at a known time (event reminders, request reminders, support reminders). `BGTaskScheduler` for best-effort sync and the school import. Request-clock steps that must happen on time are owner question 2 (§9). |
| "Today" widget (Glance) | WidgetKit, in Swift, reading `TodayWidgetModel` from the shared framework through an App Group. The same rules as CLAUDE.md design item 18: one computation, one wording. |
| SQLCipher (SEC-2) | Owner question 1 (§9). |
| Keystore-sealed preferences (SEC-5) | Keychain |
| ML Kit receipt OCR, QR scanning (CameraX) | ML Kit's iOS SDK, or Apple Vision and AVFoundation. On the device either way (item 10). |
| On-device dictation | `SFSpeechRecognizer` with `requiresOnDeviceRecognition`. No button where on-device recognition is unavailable (item 28). |
| PDF export (`android.graphics.pdf`) | `UIGraphicsPDFRenderer`. `RecordLayout` is already pure Kotlin and stays shared. |
| Google sign-in, Credential Manager | Google Sign-In for iOS, plus **Sign in with Apple** (App Store guideline 4.8). Firebase Auth links both to one account. |
| Google Calendar import | The same read-only scope, through Google Sign-In for iOS (Legal item 10). |
| Pairing link, Play install referrer | Universal Links on `/pair/` (`web/.well-known/apple-app-site-association`). iOS has no install referrer: a system paste button instead (§7). |
| Per-app language (AppCompat) | iOS per-app language in Settings, which is native. |
| Edge-to-edge insets (design item 14) | The shell owns the safe areas; screens see consumed insets as on Android. |

### 5.5 Server changes

The following change on the server:

- an iOS app registered in the Firebase project, with an APNs key;
- `apple-app-site-association` hosted beside `assetlinks.json`;
- `notification_queue` delivery sends an APNs alert with `mutable-content` and a neutral fallback
  line for the extension to replace. The fallback is fixed text, never client text;
- Sign in with Apple enabled in Firebase Auth;
- legal documents updated (Apple and APNs as processors, the App Store privacy label, the privacy
  manifest).

Rules and data shapes do not change.

### 5.6 Tests and CI

- Shared logic tests move to `commonTest` and run on both JVM and iOS.
- The wire fixtures run from `commonTest`, so the iOS reader and writer are held to the same
  contract as Android's.
- A macOS job builds the iOS app and runs `commonTest` on the iOS simulator. A smoke XCUITest
  signs in on the emulators and opens the four tabs.
- **One cross-platform e2e test.** An iPhone simulator parent and an Android emulator parent on the
  same Firebase emulators exchange an event, a message, a swap and an expense, and each reads the
  other's. It belongs in `tools/e2e/coverage.json` as a new column.
- Cloud sessions still cannot build iOS. iOS work is ⚙️ (CI) or 💻 (the owner's Mac and iPhone)
  in ROADMAP §1 terms.
- iOS has no screenshot test with the reach of Roborazzi. A UI tour on the iOS simulator (the
  `UiTourTest` idea, using `XCUIScreenshot`) fills the review role. The builder chooses the tool.

## 6. Order of work

| Phase | What | Leaves |
| --- | --- | --- |
| 0 | Android released; first feedback round handled. | — |
| 1 | §5.1 steps 1–2: toolchain, domain in `commonMain`, wire format proven unchanged. | Android unchanged |
| 2 | §5.1 steps 3–6: DI, data layer, presentation, thin Android shell. | Android unchanged, now multiplatform |
| 3 | §5.2 spike, then the iPhone shell, the adaptive controls and the theme. | iPhone app running on the simulator with real data |
| 4 | §5.4 platform features; §5.5 server. | iPhone at parity |
| 5 | Cross-platform e2e, device checklist for iPhone, TestFlight with testers who use iPhones. | Beta |
| 6 | App Store listing, privacy label, review. | Release |

Phases 1–2 are about a third of the work, phases 3–4 another third, and 5–6 the last third.

## 7. Left to the builder, and calls already made

Left to the builder:

- Koin or kotlin-inject (§5.1 step 3).
- GitLive or `expect`/`actual` Firebase (§5.1 step 4), decided by the spike.
- Native or Compose navigation bars inside a tab (§5.2), decided by the spike.
- ML Kit iOS or Apple Vision for OCR and QR.
- The iOS UI tour tool.
- The order of pull requests inside each phase.

Calls made while writing this document. They are the author's, not the owner's; the owner may
overturn any of them.

- **No Android feature freeze during phases 1–2.** Each layer moves in one pull request. Once a
  layer is in `shared`, new code for it is written there. A feature in flight is rebased onto the
  move, not the other way round.
- **A late platform is an older build.** The two stores will not release on the same day. That
  needs nothing new: the wire contract already requires every build to live beside an older one
  (CLAUDE.md item 5, items 24, 30, 33 and 44). Both platforms ship from the same commit when they
  can.
- **Minimum iOS 17.** On-device speech, background tasks, the paste control and current Compose
  Multiplatform are all there. The app is built with the newest SDK, so iOS 26 and later draw the
  Liquid Glass bars, and older versions draw the classic ones by themselves.
- **SF Pro on iPhone** (Dynamic Type), Onest on Android. This follows the owner's "as much like an
  iPhone as possible". The custody grid, the cards and the parent colours stay shared.
- **The invitation code after an App Store install.** The pairing screen gets a system paste
  button (`UIPasteControl`). It reads the clipboard without iOS's permission prompt, and
  `web/pair/` copies the code before it sends the parent to the App Store. Tapping the link again
  also works (Universal Links).
- **A push the extension cannot word.** An unknown type, or one addressed to another account,
  should be dropped, as on Android (items 15 and 23). Hiding a notification from a Notification
  Service Extension needs Apple's filtering entitlement
  (`com.apple.developer.usernotifications.filtering`), which is requested. Until Apple grants it,
  such a push shows one fixed neutral line ("Something changed in CoPlanly"), never server or
  client text.
- **Sign in with Apple on both platforms.** Firebase Auth offers Apple sign-in on Android through
  the web flow, so a parent who started on an iPhone can move to Android. Account deletion revokes
  the Apple token (Apple requires it) in `deleteAccount`.

## 8. Constraints

- A Mac with Xcode, for the shell, the extension, the widget and signing. The owner has one.
- An iPhone before TestFlight. Pushes, Sign in with Apple, the widget, background work and the
  Keychain behave differently on a simulator. The owner does not have one yet.
- Apple Developer account. The owner has one.
- GitHub's macOS runners cost about ten times Linux minutes. The iOS jobs run only when the diff
  reaches shared or iOS code (`tools/ci-changes.js` gains an `ios` output).
- App Store review: account deletion in the app (it exists), Sign in with Apple, a privacy
  manifest, and a privacy label that matches `DATA-SAFETY.md`.

## 9. Owner answers, 27 October 2026

1. **Database encryption on iPhone: Apple's standard.** The database file carries iOS Data
   Protection (`NSFileProtectionComplete`), so the system encrypts it whenever the phone is
   locked. No SQLCipher on iOS and no custom Room driver. SEC-2 keeps SQLCipher on Android. The
   DPIA and the privacy policy describe the two mechanisms side by side.
2. **Steps that must happen on time move to the server, for both platforms.** A request's
   deadline notice, its last-chance window ending and its "No answer" end (CLAUDE.md item 42) run
   in a scheduled Cloud Function instead of on the proposer's phone. This is a change to the
   Android app too, so it is its own roadmap item and lands **before phase 2**. It needs its own
   design pass: the clock document, the five rule shapes, the server-only writes, the withdrawal
   of a custody or split proposal at the end, and the DPIA (R22). Reminders stay local
   notifications.
3. **CI for iOS: the owner's Mac as a self-hosted runner.** The macOS jobs run on it; Linux jobs
   stay hosted. The workflow must not run untrusted pull requests from forks on it. The owner sets
   up the runner (💻); the job definitions are cloud work.
4. **Paid features count for the family, wherever they were bought.** When MON-11 is built, the
   entitlement is held by the server per family and fed by both Google Play Billing and StoreKit
   (App Store guideline 3.1.1). This shapes MON-11; it does not block the iPhone app.
