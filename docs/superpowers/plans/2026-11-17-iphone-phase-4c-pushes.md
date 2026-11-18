# iPhone phase 4 part C — pushes and local notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone receives every push Android does, worded on the phone by one shared Kotlin wording, shows event and child support reminders as local notifications, and the request reminders of both platforms come from the server.

**Architecture:** A new KMP module, `:push-wording`, holds the text of every notification as pure common Kotlin over a strings seam and two formatting seams; Android reads its strings from its own resources, the iPhone's Notification Service Extension from `.strings`/`.stringsdict` files generated from the same XML, so each platform's own plural rules apply. The server adds an `apns` alert with a neutral fallback to every FCM message and sends `request_reminder` from part B's sweep. The iPhone registers its FCM token, shares the signed-in uid and the app's language with the extension through an App Group, and schedules local notifications through `UNUserNotificationCenter`.

**Tech Stack:** Kotlin Multiplatform (new `:push-wording`, `:shared-data`, `:shared-ui`, the app), Swift (a Notification Service Extension target in `iosApp/project.yml`), Firebase Messaging iOS through Swift Package Manager, Cloud Functions v1 (mocha), Firestore rules (`firestore-tests`), the e2e suite on the emulators, XCUITest with `xcrun simctl push`.

**Spec:** `docs/superpowers/specs/2026-11-17-iphone-phase-4c-pushes-design.md` (approved by the owner on 17 November 2026). Read it before your task; section numbers below refer to it. Part B's spec (`2026-11-17-iphone-phase-4b-server-clocks-design.md`) and CLAUDE.md items 15 and 42 are the rules this builds on.

## Global Constraints

- **Android's text does not change, byte for byte**, for any push, chat card push, event reminder, support reminder or request notice (§11 call 2). The oracle is the old code, kept in the app's tests until the end of Task 2.
- One push token per account: `users/{uid}.fcmToken` stays a single value (§2 decision 1).
- The new push type is exactly `request_reminder`: server-only (refused from clients by `firestore.rules`), carries `familyId`, `actorName` (the proposer's first name through `previewFirstName`), `requestKind` and `answerBy`; routed to the Requests inbox on the schedule channel; in item 15's four places.
- Server-only fields on `request_deadlines` are exactly `serverReminderTimesMillis` (list of epoch millis) and `serverRemindersSent` (integer); part B's rule holds: no client write may carry a `server…` field.
- The profile field is exactly `users/{uid}.timeZone`: an IANA zone name, a string of at most 64 characters, written only by the profile's owner.
- The `apns` fallback is exactly title `CoPlanly`, body `You have a new update in CoPlanly.`, with `mutable-content: 1`. Android's message (`data`, `android.priority`) is unchanged.
- The App Group is exactly `group.app.coplanly`; it carries the signed-in uid and the app's resolved language (`AppLanguageRule`) and nothing else, both cleared at sign-out and deletion.
- Quiet hours are `ReminderPlan`'s: 21:00–08:00 on the answering parent's clock; nothing is sent or shown inside them.
- At most 60 pending local notifications on the iPhone (iOS allows 64; four stay free).
- The extension never shows a push whose `targetUserId` is not the shared uid when it can avoid it; until Apple's filtering entitlement is granted, the server's neutral fallback is what appears (§2 decision 4).
- New user-facing strings go into all five locales (CLAUDE.md "Localization", check 7).
- Gradle cannot configure in the cloud session (Google Maven 403): Kotlin and Swift are compiled by CI only. Node suites (`functions`, `firestore-tests`), `node tools/check-invariants.js` and `node --test tools/test/*.test.js` run locally.
- Repository content in English; Conventional Commits; commit trailers
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01Dch2jKGkvRLpUk46Q4aPa1`; never regenerate the detekt or screenshot baselines by hand; never commit `GoogleService-Info.plist` or `google-services.json`.

## Review Focus

1. **A push for the previous account on this iPhone** (signed out, another signs in, an old push arrives): the extension must not word it with the new account's data and must not show a worded text; the neutral fallback is the most that appears. Test in Task 6: `aPushForAnotherUidIsNotWorded` (XCUITest with `simctl push`).
2. **A reminder due while the sweep is delayed into the night** must move to the next 08:00, or be dropped when that is past the deadline, never sent at 23:00. Test in Task 4: `aReminderDueAtNightWaitsForTheMorning`.
3. **A reminder planned by the server, then a phone delivers its own plan** after reminder 1 was sent: reminder 1 is not sent again, the phone's later times are used. Test in Task 4: `aPhonePlanAfterAServerReminderNeverRepeatsIt`.
4. **An event deleted on the other phone** must cancel this iPhone's local reminder on the next sync, and an event list longer than 60 reminders keeps the nearest. Tests in Task 7: `aDeletedEventsReminderIsCancelledOnSync`, `onlyTheNearestSixtyAreScheduled`.
5. **A plural in Czech, Russian or Ukrainian** ("2 dny", "5 dnů"; "2 дня", "5 дней") worded by the iPhone's `.stringsdict` must match Android's `getQuantityString`. Test in Task 1: the generator's plural cases, and in Task 2 the iOS run of the golden corpus for the day-count types.

---

### Task 1: `:push-wording` — the module, its seams and the iPhone's strings

**Files:**
- Create: `push-wording/build.gradle.kts` (KMP: Android KMP library target, `iosArm64`, `iosSimulatorArm64`; a static framework `PushWording` for the iOS targets, exporting nothing else), and include it in `settings.gradle.kts`
- Create: `push-wording/src/commonMain/kotlin/com/coparently/app/pushwording/WordingStrings.kt`, `ReaderClock.kt`, `PushText.kt`, `FormatArgs.kt`
- Create: `push-wording/src/androidMain/.../AndroidWordingStrings.kt`
- Create: `push-wording/src/iosMain/.../BundleWordingStrings.kt`
- Create: `tools/ios/generate-push-strings.js` and `tools/test/generate-push-strings.test.js`
- Modify: `.github/workflows/ci.yml`, `tools/ci-changes.js` (+ its test): `push-wording/**` is a shared path (runs `shared`, `shared-ios`, `ios-app` and every Android job, like `shared-data/**`)

**Interfaces:**
- Produces:
  - `data class PushText(val title: String, val body: String)` (moves here from the app; the app imports it).
  - `interface WordingStrings { fun string(key: String, vararg args: Any): String; fun plural(key: String, count: Int, vararg args: Any): String }`.
  - `interface ReaderClock { fun answerBy(millis: Long): String; fun dayInSentence(isoDate: String?): String; fun dayOfMillis(millis: Long): String; fun swapDays(isoDates: List<String>): String; fun eventStart(millis: Long): String; fun money(amount: String, currency: String): String? }` — every date, time and amount a notification prints; `money` answers null for an unreadable amount or currency.
  - `fun formatArgs(template: String, args: Array<out Any>): String` — replaces `%<n>$s` and `%<n>$d` exactly as Compose Multiplatform does (`toString()` of the argument), nothing else.
  - `AndroidWordingStrings(context: Context)`: `resources.getIdentifier(key, "string"|"plurals", packageName)` then `getString`/`getQuantityString`; an unknown key throws `IllegalArgumentException`.
  - `BundleWordingStrings(languageTag: String)`: reads `PushStrings.strings`/`PushStrings.stringsdict` from the `<lang>.lproj` of the extension's bundle (`en` when the language has none), formats with `formatArgs`; plurals through `NSString.localizedStringWithFormat` over the `.stringsdict` entry.
  - `node tools/ios/generate-push-strings.js <out-dir> --keys <file>`: reads `shared-ui/src/commonMain/composeResources/values*/*.xml`, writes `<lang>.lproj/PushStrings.strings` and `PushStrings.stringsdict` for the keys listed in `push-wording/push-keys.txt` (one key per line; Task 2 fills it). Text unescaped as Compose Multiplatform does (`\n`, `\t`, `\uXXXX`, `\\`); plurals as `.stringsdict` `NSStringPluralRuleType` entries with the XML's quantities (`zero one two few many other`); format specifiers rewritten so `%<n>$d` becomes `%<n>$d` and `%<n>$s` becomes `%<n>$@`.

- [ ] **Step 1:** Write `tools/test/generate-push-strings.test.js`: an English string with `'` and `\n`, a Czech plural with `one few many other`, a Russian plural, an argument `%1$s` written as `%1$@`, a missing locale falling back to the base, an unknown key in `push-keys.txt` failing with its name. Run `node --test tools/test/generate-push-strings.test.js`; expected FAIL.
- [ ] **Step 2:** Implement the generator; run the test; expected PASS.
- [ ] **Step 3:** Write `push-wording/src/commonTest/.../FormatArgsTest.kt`: `formatArgs("%1$s pays %2$d", arrayOf("Bob", 3)) == "Bob pays 3"`, a template with no arguments unchanged, `%2$s` before `%1$s`.
- [ ] **Step 4:** Create the module, the seams and their two halves; wire `ci-changes.js` (test: a `push-wording/` path turns on `shared`, `ios` and the Android outputs); `node --test tools/test/*.test.js`; expected PASS. Kotlin compiles in CI.
- [ ] **Step 5:** Commit: `feat(push-wording): add the shared notification wording module and its strings seams`

### Task 2: Every notification's wording moves into `:push-wording`, Android unchanged

**Files:**
- Create: `push-wording/src/commonMain/.../PushWording.kt` (the push types: `compose(type: String?, data: Map<String, String>): PushText?`), `CardWording.kt` (the chat card's key and arguments), `ReminderWording.kt` (event, support and request reminders)
- Create: `push-wording/src/androidMain/.../AndroidReaderClock.kt` (the `java.time` and `NumberFormat` calls `PushNotifier`, `DueReminders`, `SupportReminderWorker` and `RequestNotices` make today — `answerByText`, `localizedDate`, `isoDateText`, `swapDateList(locale)` move here from `shared-ui/src/androidMain/.../LocalizedDates.kt` and `app/.../utils/SwapDateList.kt`, packages unchanged; `:shared-ui` depends on `:push-wording`)
- Create: `push-wording/src/iosMain/.../FoundationReaderClock.kt` (the same texts through `NSDateFormatter`/`NSNumberFormatter`, as `ReaderDateText.ios`/`ReaderMoneyText.ios` write them)
- Create: `push-wording/push-keys.txt`
- Modify: `app/.../data/remote/firebase/PushNotifier.kt` (keeps channels, intents, tray ids, `BigTextStyle`; `compose` delegates), `SwapPushWording.kt` (moves to the module), `app/.../presentation/chat/CardPushText.kt` (deleted; its decisions are `CardWording`), `shared-ui/.../presentation/chat/ActivityCardText.kt` (reads `CardWording`'s key and maps it with `Res.allStringResources`), `DueReminders.kt`, `SupportReminderWorker.kt`, `RequestNotices.kt` (word through `ReminderWording`), `res/raw/card_push_keep.xml` (keeps every key in `push-keys.txt` from the resource shrinker)
- Test: `app/src/test/.../pushwording/PushWordingGoldenTest.kt` (Robolectric, five locales) with `LegacyPushText.kt` beside it — a copy of the current `PushNotifier.compose`, `cardPushText` and the three reminder wordings, made at the start of the task, as the oracle; `push-wording/src/iosTest/.../PushWordingIosTest.kt`

**Interfaces:**
- Consumes: Task 1's seams.
- Produces:
  - `class PushWording(strings: WordingStrings, clock: ReaderClock) { fun compose(type: String?, data: Map<String, String>): PushText? }` — exactly the old `compose`'s results: null for a type it cannot word; the chat preview relayed; a chat card worded through `CardWording`.
  - `object CardWording { fun of(card: ActivityAnnouncement, readerUid: String?): CardText? }` with `data class CardText(val key: String, val args: List<Any>, val pluralCount: Int? = null)`.
  - `class ReminderWording(strings: WordingStrings, clock: ReaderClock) { fun event(title: String, startMillis: Long): PushText; fun support(dueMillis: Long, amount: String, currency: String, payeeName: String?): PushText; fun request(proposerName: String?, answerByMillis: Long): PushText }`.
  - `PushWording.TYPES: Set<String>` — every type it words (used by Task 3's routing test and Task 6).

- [ ] **Step 1:** Copy the current wording code into `LegacyPushText.kt` (test source) unchanged but for its package; write `PushWordingGoldenTest`: a corpus of at least one payload per `PUSH_TEXT` type, every swap wording, a deadline, an expense with and without a share, every chat card kind, a blank actor, an unknown type, and the three reminders, in `en`, `cs`, `de`, `ru`, `uk`, asserting `PushWording.compose(...) == LegacyPushText.compose(...)` (and the reminders likewise). Expected FAIL (module code missing).
- [ ] **Step 2:** Move the wording into the module; point the app and `ActivityCardText` at it; fill `push-keys.txt` with every key the module reads (a test asserts the file equals the set of keys `PushWording`, `CardWording` and `ReminderWording` reference).
- [ ] **Step 3:** CI (`build-test`, `shared`, `static`, `screenshots`): golden test PASS; `PushNotificationTest`, `PushRoutingTest`, `PushNotifierRequestCodeTest`, `ChatCardPushTest`, the screenshots and `UiTextParityTest` unchanged and PASS.
- [ ] **Step 4:** Write `PushWordingIosTest` (simulator): the English corpus through `BundleWordingStrings` and `FoundationReaderClock` equals the golden English texts for every type whose text has no date or amount, and the day-count plural in `cs`, `ru`, `uk` equals Android's. Wire the generator into the iOS test resources (a Gradle task running the node script before `iosSimulatorArm64Test`).
- [ ] **Step 5:** Commit: `refactor: word every notification through :push-wording, Android's text unchanged`

### Task 3: The server — the `apns` alert, `request_reminder`, the profile's zone

**Files:**
- Modify: `functions/index.js` (`buildFcmMessage` gains the `apns` block), `firestore.rules` (`users/{uid}.timeZone`; `request_reminder` not in the client allow-list), `shared-data/.../PushPayload.kt` (`REQUEST_REMINDER`, `REQUEST_KIND`), `PushNotifier.PUSH_TEXT` / `PushWording` (the type, worded by `ReminderWording.request`), `push_strings.xml` ×5 (title and body copied from `request_notice_reminder_*`), `PushRouting` (inbox, schedule channel)
- Modify: `shared-data/.../UserRepositoryImpl.kt` (writes `timeZone` with the profile: on start and when it differs from the stored one), `AuthStateViewModel`/the app start (asks for it once per process)
- Test: `functions/test/build-fcm-message.test.js`, `firestore-tests/rules/profile-timezone.test.js`, `PushRoutingTest`

**Interfaces:**
- Produces: `buildFcmMessage(token, data)` → adds `apns: { payload: { aps: { alert: { title: 'CoPlanly', body: 'You have a new update in CoPlanly.' }, 'mutable-content': 1, 'thread-id': <thread> } }, headers: { 'apns-collapse-id': <id>, 'apns-push-type': 'alert' } }` where `<thread>` is the `conversationId` for `chat_message` else the type, and `<id>` is the conversation id for a message, `card_<activityEntityId>` for a card, else omitted. `PushPayload.REQUEST_REMINDER = "request_reminder"`, `PushPayload.REQUEST_KIND = "requestKind"`. `UserRepository.recordTimeZone(zone: String)`.

- [ ] **Step 1:** Tests first: `build-fcm-message.test.js` (the block for a typed push, a chat message's thread and collapse id, a card's collapse id, `data` and `android` exactly as before); `profile-timezone.test.js` (owner writes `Europe/Prague`; a 65-character value, a number and another user's write refused; a create carrying it allowed); a rules case refusing `request_reminder` from a client; `PushRoutingTest` gains the type. Run the node suites; expected FAIL.
- [ ] **Step 2:** Implement; `cd functions && npm test && npm run lint`, `cd firestore-tests && npm test`, `node tools/check-invariants.js` (the four-way agreement); expected PASS.
- [ ] **Step 3:** Commit: `feat: an APNs alert for every push, the request_reminder type and the profile's time zone`

### Task 4: The sweep sends the request reminders; the phones stop

**Files:**
- Create: `functions/reminder-plan.js` (port of `ReminderPlan.plan` and `isQuietAt`, IANA zones through `Intl.DateTimeFormat`), `functions/test/fixtures/reminder-plan-cases.json`, `functions/test/reminder-plan.test.js`, and `ReminderPlanFixtureTest` in `:shared`'s `androidHostTest` reading the same fixture
- Modify: `functions/request-clocks.js` (`nextStepAt` also returns the next unsent reminder), `functions/request-clock-steps.js` (`stepClock` sends a due reminder), `firestore.rules` (the two server fields can never be written by a client — already true by part B's shape; add the cases)
- Modify: Android: `RequestClockKeeper.sweep` keeps delivering clocks (the phone's plan stays its delivery write) but schedules no reminder; `RequestClockScheduler.scheduleReminder`, `RequestClockNotices.remind`, their Android halves and iPhone stand-ins are deleted; an older build's queued reminder job finishes without acting (`runRequestClockStep`)
- Modify: `shared-ui/.../CardClock.kt:301-303`, `RequestClockText.kt:63` (planned = `remindersPlanned ?: serverReminderTimesMillis.size`; sent = `max(serverRemindersSent, remindersShown)`), `RequestDeadlineDocument`/`RequestDeadline` (read the two server fields)
- Test: `functions/test/request-clock-sweep.test.js`, `TwoParentServerClocksTest` (e2e), `CardClockTest`

**Interfaces:**
- Consumes: `REQUEST_REMINDER` (Task 3), part B's `stepClock`, `serverNextStepAt`.
- Produces: `plan(sentAtMillis, answerByMillis, notBeforeMillis, zone) -> number[]`, `isQuietAt(millis, zone) -> boolean` (`functions/reminder-plan.js`); `RequestDeadline.serverReminderTimesMillis: List<Long>`, `RequestDeadline.serverRemindersSent: Int`.

- [ ] **Step 1:** Fixture of at least 25 cases (three, two and one reminders; a reminder moved out of the night; to the evening before; a DST change in `Europe/Prague`; a zone of UTC+14 and UTC−11; reminders before `notBefore` dropped) and both tests over it. Expected FAIL.
- [ ] **Step 2:** Implement `reminder-plan.js`; expected PASS.
- [ ] **Step 3:** Sweep tests, each against the emulator-free fakes part B's suite uses: `aDeliveredPlanIsSentAtItsTimes`, `anUndeliveredClockIsPlannedFromTheProfileZone` (writes `serverReminderTimesMillis` once), `anUndeliveredClockWithNoZoneWaits`, `noReminderForAnAnsweredRequest`, `noReminderForAWithdrawnRequest`, `noReminderDuringTheLastChance`, `aReminderDueAtNightWaitsForTheMorning`, `aPhonePlanAfterAServerReminderNeverRepeatsIt`, `theReminderGoesToTheAnsweringParentInTheClocksFamily` (the queued push's `targetUserId`, `familyId`, `actorName`, `answerBy`). Expected FAIL; implement in `stepClock`/`nextStepAt`; PASS.
- [ ] **Step 4:** Remove the phones' reminder path (Android and the iPhone's stand-ins), update the two screens; e2e `TwoParentServerClocksTest.theAnsweringParentIsRemindedByTheServer` (sweep through the emulator-only callable at a planned time; a push `request_reminder` queued to Bob, none after he answers); `tools/e2e/coverage.json` names it.
- [ ] **Step 5:** Commit: `feat(functions): the request reminders come from the server for both platforms`

### Task 5: The iPhone registers for pushes

**Files:**
- Modify: `iosApp/project.yml` (FirebaseMessaging product; `aps-environment` and `com.apple.security.application-groups: [group.app.coplanly]` in `CoPlanly.entitlements`; background mode `remote-notification` not needed)
- Modify: `iosApp/CoPlanly/AppDelegate.swift` (register for remote notifications, hand the APNs token to `Messaging`, `UNUserNotificationCenter` delegate: a tap hands `userInfo` to `IosApp.openPush(data)`; foreground presentation: banner and sound)
- Create: `shared-data/src/iosMain/.../data/remote/firebase/IosPushRegistration.kt` (replaces `NoPush`: `available = true`, the switch, `unregisterToken` deleting the token and the Firestore field), `IosPushToken.kt` (a Kotlin interface the Swift side implements, as `GoogleSignInBridge` is), `shared-ui/src/iosMain/.../ios/AppGroupShare.kt` (writes `uid` and `language` to `group.app.coplanly`; called on start, sign-in, sign-out, deletion and language change)
- Modify: `FirestorePushQueue` (`getCurrentToken`/`updateUserToken` read and write the real token), `IosPlatform.kt` (`PermissionRequester`'s iPhone half asks `UNUserNotificationCenter` for alert, sound and badge), `LaunchLinks`/`IosApp` (`openPush(data: Map<String, String>)`: the family switched before the `PushDestination` is armed, as `MainActivity.readLaunchIntent`)
- Test: `IosPushRegistrationTest`, `AppGroupShareTest`, `LaunchLinksTest` (iosTest)

**Interfaces:**
- Produces: `interface IosPushToken { suspend fun current(): String?; suspend fun delete() }`; `object AppGroupShare { fun write(uid: String?, language: String?); val uid: String?; val language: String? }`; `IosApp.openPush(data: Map<String, String>)`.

- [ ] **Step 1:** Tests: registration writes the token through `PushDevice.updateUserToken`; `unregisterToken` deletes token and field; turning the switch off removes them; `AppGroupShare` cleared at sign-out; `openPush` with another family's `familyId` switches before navigating; a push for another uid opens nothing. Expected FAIL.
- [ ] **Step 2:** Implement; bind in `platformDataModule`/`ProvideIosShell`; CI's `ios-app` job builds and runs the tests.
- [ ] **Step 3:** Commit: `feat(ios): register for pushes and share the signed-in account with the extension`

### Task 6: The Notification Service Extension

**Files:**
- Create: `iosApp/CoPlanlyNotifications/NotificationService.swift`, `Info.plist`, `CoPlanlyNotifications.entitlements` (the App Group)
- Modify: `iosApp/project.yml` (an `app-extension` target embedded in `CoPlanly`, linking the `PushWording` framework built by a pre-build script, `:push-wording:linkReleaseFrameworkIosArm64`/`…SimulatorArm64` by SDK, as `Shared` is built; a build phase running `generate-push-strings.js` into the extension's resources)
- Create: `push-wording/src/iosMain/.../ExtensionEntry.kt` (`fun wordPush(data: Map<String, String>, sharedUid: String?, language: String?): PushText?` — null when `targetUserId` ≠ `sharedUid` or the type cannot be worded)
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift` (or a new `PushUITests.swift`), `iosApp/scripts/simulator.sh` (a `push` step using `xcrun simctl push` with a JSON payload)
- Test: `ExtensionEntryTest` (iosTest), the XCUITest cases

**Interfaces:**
- Consumes: `PushWording`, `BundleWordingStrings`, `FoundationReaderClock`, `AppGroupShare` (the keys, read in Swift through `UserDefaults(suiteName: "group.app.coplanly")`).
- Produces: the extension: on `didReceive`, `wordPush(...)`; a text replaces `title`/`body`; null leaves the server's fallback; `serviceExtensionTimeWillExpire` delivers the fallback.

- [ ] **Step 1:** `ExtensionEntryTest`: an `expense_added` in German worded; `aPushForAnotherUidIsNotWorded` (null); an unknown type null; a blank `sharedUid` (signed out) null. Expected FAIL; implement; PASS.
- [ ] **Step 2:** The target and the build phases; XCUITest: with the app signed in as the seeded family, `simctl push` an `expense_added` and read the banner's worded text; push one for another uid and read the fallback text; push a `chat_message` and tap it, landing in the thread.
- [ ] **Step 3:** Record the extension's peak memory on the simulator in the job's log (a `simctl spawn … log` line the extension writes once per push with `task_info`'s resident size); the job fails above 20 MB.
- [ ] **Step 4:** Commit: `feat(ios): word every push on the iPhone through a Notification Service Extension`

### Task 7: Local reminders on the iPhone

**Files:**
- Create: `shared-data/src/iosMain/.../data/notification/IosEventReminders.kt` (replaces `NoEventReminders`), `IosSupportReminders.kt` (replaces `NoSupportReminderJob`), `LocalNotificationCenter.kt` (a narrow Kotlin interface over `UNUserNotificationCenter`, so tests use a fake)
- Modify: `PlatformDataModule.ios.kt`, `IosUnavailable.kt` (stand-ins removed), the iPhone's sync runner and app start (re-arm), the sign-out/switch/deletion paths (`cancelAll`)
- Test: `IosEventRemindersTest`, `IosSupportRemindersTest` (iosTest, with the fake center)

**Interfaces:**
- Consumes: `ReminderPlanner`, `ReminderWording` (Task 2), `SupportReminderRule`, `SupportReminders`, `SupportAgreementStore`, `PaymentStore`.
- Produces: `interface LocalNotificationCenter { suspend fun pending(): List<String>; fun add(id: String, atMillis: Long, title: String, body: String, data: Map<String, String>); fun remove(ids: List<String>) }`; ids `event-<eventId>` and `support-<familyId>-<yyyy-MM>`.

- [ ] **Step 1:** Tests: `anEventReminderIsArmedAtItsInstant` (one id per event, the next occurrence); `aDeletedEventsReminderIsCancelledOnSync`; `onlyTheNearestSixtyAreScheduled` (with 70 events, and with a support reminder taking one slot); `cancelAllRemovesEveryEventAndSupportReminder`; `theSupportReminderSkipsAPaidMonth`; `theSupportReminderIsOutsideTheQuietHours`; `recordingIPaidCancelsIt`; the reminder's text is `ReminderWording`'s in the app's language. Expected FAIL.
- [ ] **Step 2:** Implement; `available = true` on the support scheduler; CI's `ios-app` runs the tests.
- [ ] **Step 3:** Commit: `feat(ios): event and child support reminders as local notifications`

### Task 8: Documents, coverage and the record

**Files:**
- Modify: `CLAUDE.md` (item 15: the `apns` block, the extension, `:push-wording`, `request_reminder`; item 42: reminders on the server, "a reminder counts when sent"; item 9: the iPhone's local reminders; the architecture map: `push-wording/`; "Where an iPhone path lives": the extension), `docs/ROADMAP.md` (MON-24: `NoPush`, `NoEventReminders`, `NoRequestClockJobs`, `NoRequestClockNotices`, `NoSupportReminderJob` replaced; MON-25: reminders), `docs/legal/PRIVACY-POLICY.md` and `web/privacy/` (regenerated per `web/README.md`), `RECORDS-OF-PROCESSING.md`, `DATA-SAFETY.md`, `DPIA.md` R22 (APNs as a processor; the profile's zone and why; reminders sent by the server), `docs/DEVICE-CHECKLIST.md` (a new §: pushes on a real iPhone, the extension's memory, the fallback on the lock screen, a server reminder at its planned time; §0: the APNs key in the Firebase console and the filtering entitlement request), `tools/manual-test-plan.js` RULES, `functions/README.md` (deploy order: rules, then functions), `tools/e2e/coverage.json`
- Test: `node --test tools/test/*.test.js`, `node tools/check-invariants.js`, `tools/check-e2e-coverage.js`

- [ ] **Step 1:** Write the documents; run the three checks; expected PASS.
- [ ] **Step 2:** Commit: `docs: record phase 4 part C (pushes and notifications)`
