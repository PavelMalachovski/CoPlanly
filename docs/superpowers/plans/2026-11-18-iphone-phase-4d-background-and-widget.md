# iPhone phase 4 part D: background work and the Today widget — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iPhone refreshes itself in the background when iOS allows, runs the school import daily, and shows the "Today" widget (small and medium) on the home screen, saying exactly what Android's widget says.

**Architecture:** The widget's model and wording move from the Android app to `:shared-ui` behind two seams (`WordingStrings` from `:push-wording`, a new `WidgetClock`), so both platforms word it once. The iPhone app writes a seven-day `WidgetSnapshot` (worded lines plus resolved colours) as JSON into the App Group; a SwiftUI WidgetKit extension draws it, one timeline entry per day. A `BGAppRefreshTask` runs the existing sync chain (to which a daily school import is added) and rewrites the snapshot, and ends at once while the phone is locked.

**Tech Stack:** Kotlin Multiplatform (`:shared-ui`, `:shared-data`, `:push-wording`), kotlinx-datetime, `StoredJson`, Room KMP's `InvalidationTracker`, Swift 5, SwiftUI, WidgetKit, BackgroundTasks, XcodeGen.

**Spec:** `docs/superpowers/specs/2026-11-18-iphone-phase-4d-background-and-widget-design.md`

## Global Constraints

- One computation and one wording with Android (CLAUDE.md design item 18): Android's widget behaviour, `TodayWidgetTextTest`'s assertions and the UI tour's widget shots do not change.
- Home screen only: `.systemSmall` and `.systemMedium`. No lock screen, StandBy, large size, interactive widget or Live Activity.
- The snapshot holds lines and colours only: never a record, a uid, a token or an e-mail address. A private event of the co-parent never reaches it.
- Sign-out (and deletion, and an account switch) writes the signed-out snapshot **before** the session ends.
- The database keeps `NSFileProtectionComplete` (owner decision (b), 18 November 2026): the background chain ends at once, reporting success, while `UIApplication.isProtectedDataAvailable` is false, and touches nothing first.
- The school import runs at most once a day per account, after the launch and foreground syncs as well as in the background.
- Task identifier `app.coplanly.refresh`; next request `earliestBeginDate` 30 minutes ahead, submitted on entering the background and at the end of each run; cancelled at sign-out.
- Widget target `CoPlanlyWidget`, bundle id `app.coplanly.widget`, App Group `group.app.coplanly` only; snapshot path `<group container>/widget/today.json`, written through a `.part` file and a rename.
- Tap opens `coplanly://calendar`: the Calendar tab on today.
- Every new user-facing string in all five locales (`values`, `-cs`, `-de`, `-ru`, `-uk`), passing `node tools/check-invariants.js`.
- Every `@Serializable` stored model goes through `StoredJson`, with a `GsonParityTest` case, a `StoredJsonVectorsTest` vector and an `R8JsonProbe` case (checks 4–5 of `check-invariants.js`).
- No local Gradle or Xcode in the cloud session: Kotlin and Swift compile in CI (`workflow_dispatch` runs allowed early); never regenerate detekt or screenshot baselines by hand; no `@Suppress` to silence a new finding.
- Commits: Conventional Commits, English, ending with the session's `Co-Authored-By` and `Claude-Session` lines.

## Review Focus

1. **A phone switched from account A to B**: the widget must never show A's names or events after B signs in (the names cache refuses another uid; the sign-out snapshot is written first). → Task 2's `TodayWidgetNamesTest`, Task 4's `widgetSnapshotOfAnotherAccountIsNeverKept`.
2. **The language changed in the phone's Settings**: the next foreground writes a snapshot in the new language. → Task 4's `languageChangeRewritesTheSnapshot`.
3. **The phone left untouched for more than a week**: after the last day the widget shows the stale line, never last week's custody. → Task 3's `lastEntryIsTheStaleLine` and Task 5's timeline (`.atEnd` after the stale entry).
4. **A daylight-saving night**: days are keyed by local `LocalDate`, and the Swift timeline starts each entry at that date's local midnight (`Calendar.current.startOfDay`), never at "24 hours after". → Task 3's `daysAreConsecutiveLocalDatesAcrossDst`.
5. **A background run while a foreground sync is running**: one run, no overlap; the background chain awaits the coalesced run. → Task 6's `awaitedRequestJoinsTheRunningSync`.

---

### Task 1: Move the widget's model and lines to `:shared-ui`

**Files:**
- Move (git mv, package unchanged `com.coparently.app.presentation.widget`): `app/src/main/java/com/coparently/app/presentation/widget/TodayWidgetModel.kt` → `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/TodayWidgetModel.kt`
- Split out of `TodayWidgetLines.kt` into `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/TodayWidgetLines.kt`: `WidgetLine`, `WidgetEventLine`, `TodayWidgetLines`, `TodayWidgetContentState`, `compactAtLargeText` (the `TodayWidgetText` object stays in the app until Task 2)
- Move tests: `app/src/test/.../widget/TodayWidgetModelTest.kt`, `CompactAtLargeTextTest.kt` → `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/widget/`
- Modify: the app's widget files that import `java.time.LocalDate` for the model

**Interfaces:**
- Produces: `TodayWidgetModel(signedIn: Boolean, today: kotlinx.datetime.LocalDate, dayParent: String?, contactWindows: List<ContactWindow>, handover: HandoverInfo?, events: List<Event>)`, `TodayWidgetModel.signedOut(today: LocalDate)`, `TodayWidgetModel.of(today: LocalDate, userId: String, events: List<Event>, model: CustodyModel?, overrides: Map<String, DayOverride>)` — same parameters as today, `today` now kotlinx-datetime. `WidgetLine(text, parent: ParentColor?)`, `WidgetEventLine(time, title, parent: ParentColor?)`, `TodayWidgetLines(...)` and `TodayWidgetContentState(compact, tall)` unchanged; `TodayWidgetLines.compactAtLargeText()` unchanged.

- [ ] **Step 1:** `git mv` the model and its test; convert `java.time.LocalDate` to `kotlinx.datetime.LocalDate` (drop `toKotlinLocalDate()` calls it made internally); move the line types and `compactAtLargeText` as above. Test names without commas or parentheses (Kotlin/Native).
- [ ] **Step 2:** In the app, convert at the edge: `TodayWidget.contentState` passes `LocalDate.now().toKotlinLocalDate()` (or `Clock.System.todayIn(TimeZone.currentSystemDefault())`), and anything formatting `model.today` converts with `toJavaLocalDate()`.
- [ ] **Step 3:** Commit `refactor(widget): move the Today widget's model and lines to shared-ui`.
- [ ] **Step 4:** CI compile check is allowed here (`workflow_dispatch`); otherwise carry on.

### Task 2: Word the widget in common code

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/WidgetClock.kt`
- Move the `TodayWidgetText` object to `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/TodayWidgetText.kt`
- Move `TodayWidgetNames.kt` to `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/TodayWidgetNames.kt` (on `SecurePreferences`)
- Create: `app/src/main/java/com/coparently/app/presentation/widget/AndroidWidgetClock.kt`
- Modify: `app/src/main/java/com/coparently/app/presentation/widget/TodayWidget.kt`, `TodayWidgetRefresher.kt`, `app/.../di/koin/PresentationModule.kt` (the names now a `single` in `:shared-ui`'s `uiModules`, removed here), `push-wording/push-keys.txt`, `app/src/main/res/raw/card_push_keep.xml`, `tools/test/push-keys.test.js`
- Tests: `app/src/test/.../widget/TodayWidgetTextTest.kt` (calls the new signature, same assertions), `TodayWidgetNamesTest.kt` (moved to `shared-ui/src/androidHostTest/` or kept, over a `SecurePreferences` fake)

**Interfaces:**
- Consumes: Task 1's types; `com.coparently.app.pushwording.WordingStrings`.
- Produces:
  ```kotlin
  interface WidgetClock {
      /** The title's date, as Android's localizedDate("MMMEEEd") writes it ("Thu, 19 Nov"). */
      fun titleDate(date: LocalDate): String
      /** A time of day on the reader's clock, as the today card writes it. */
      fun time(time: LocalTime): String
  }
  object TodayWidgetText {
      fun contentState(strings: WordingStrings, clock: WidgetClock, model: TodayWidgetModel, parents: Parents?): TodayWidgetContentState
      fun lines(strings: WordingStrings, clock: WidgetClock, model: TodayWidgetModel, parents: Parents?, maxEvents: Int): TodayWidgetLines
  }
  class TodayWidgetNames(preferences: SecurePreferences) { fun remember(parents: Parents): Boolean; fun recall(currentUid: String): Parents?; fun clear() }
  ```
  Android's halves: `AndroidWordingStrings(context.inAppLanguage())` and `AndroidWidgetClock(context.inAppLanguage())` (the existing `localizedDate("MMMEEEd", locale)` and `shortTime(locale, DateFormat.is24HourFormat(context))`).

- [ ] **Step 1:** Change `TodayWidgetText` to read every `R.string`/`R.plurals` it reads today by key name through `strings.string(key, …)`/`strings.plural(key, count, …)`, and dates and times through `clock`. Same keys, same arguments, same order. Its event times use `Event.startDateTime.time`/`endDateTime?.time` through `clock.time`.
- [ ] **Step 2:** Add every key `TodayWidgetText` reads to `push-wording/push-keys.txt` (with a comment line "the Today widget"), to `card_push_keep.xml`, and make `tools/test/push-keys.test.js` also scan `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/TodayWidgetText.kt`. Run `node --test tools/test/push-keys.test.js` → PASS.
- [ ] **Step 3:** Update `TodayWidgetTextTest` to build the two Android halves from its English and Russian contexts; its assertions do not change. Move `TodayWidgetNames` onto `SecurePreferences` (`getString`/`putString`, `clear()` removing its keys) and keep its test's assertions, plus `recallRefusesAnotherAccount` (stored for uid A, `recall("B")` is null).
- [ ] **Step 4:** Android's `TodayWidget`, `TodayWidgetRefresher` and `MainActivity` call the moved code. Commit `refactor(widget): word the Today widget in shared code over WordingStrings and WidgetClock`.

### Task 3: The widget snapshot

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/widget/WidgetSnapshot.kt`, `WidgetSnapshotBuilder.kt`
- Modify: the five `widget_strings.xml` (new key `widget_today_stale`), `push-wording/push-keys.txt`, `card_push_keep.xml`; `app/src/r8Test/.../R8JsonProbe.kt`; the `GsonParityTest` and `StoredJsonVectorsTest` model lists
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/widget/WidgetSnapshotBuilderTest.kt`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces:
  ```kotlin
  @Serializable data class WidgetSnapshot(val version: Int = 1, val writtenAtMillis: Long, val language: String,
      val signedIn: Boolean, val days: List<WidgetDay>, val staleLine: String)
  @Serializable data class WidgetDay(val date: String /* ISO */, val compact: WidgetDayLines,
      val compactLarge: WidgetDayLines, val tall: WidgetDayLines)
  @Serializable data class WidgetDayLines(val title: String, val custody: WidgetSnapshotLine?,
      val windows: List<WidgetSnapshotLine>, val handover: WidgetSnapshotLine?,
      val events: List<WidgetSnapshotEvent>, val footer: String?)
  @Serializable data class WidgetSnapshotLine(val text: String, val colour: WidgetColour?)
  @Serializable data class WidgetSnapshotEvent(val time: String, val title: String, val mark: WidgetColour?)
  @Serializable data class WidgetColour(val light: String /* "#RRGGBB" */, val dark: String)
  object WidgetSnapshotBuilder {
      const val DAYS = 7
      fun build(today: LocalDate, writtenAtMillis: Long, language: String, userId: String?,
          eventsOn: (LocalDate) -> List<Event>, model: CustodyModel?, overrides: Map<String, DayOverride>,
          parents: Parents?, strings: WordingStrings, clock: WidgetClock): WidgetSnapshot
  }
  ```
  A line's colour is the Android widget's: text `light = ParentColor.dark`, `dark = ParentColor.light`; event mark `light = ParentColor.fill`, `dark = ParentColor.darkFill`. Null `userId` gives one signed-out day.

- [ ] **Step 1: Write the failing tests** in `WidgetSnapshotBuilderTest`:
  - `sevenConsecutiveDaysFromToday` — `days.map { it.date }` equals the seven ISO dates from `today`.
  - `daysAreConsecutiveLocalDatesAcrossDst` — `today = 2026-10-24`: dates run 2026-10-24 … 2026-10-30 with no gap or repeat.
  - `signedOutGivesOneSignedOutDay` — `userId = null`: `signedIn == false`, `days.size == 1`, the footer is `widget_today_signed_out`'s text.
  - `coParentPrivateEventNeverAppears` — a co-parent's `isPrivate` event on today: no title in any variant.
  - `coloursAreTheWidgetsTones` — the day parent's line carries `ParentColor.dark`/`light` as hex; an event mark `fill`/`darkFill`.
  - `compactLargeIsCompactAtLargeText` — equals `compact.compactAtLargeText()` mapped.
  - `lastEntryIsTheStaleLine` — `staleLine` is `widget_today_stale`'s text.
  Use a fake `WordingStrings` that returns `"key(args)"` and a fake `WidgetClock`.
- [ ] **Step 2:** Implement. `widget_today_stale`: en "Open CoPlanly to update", cs "Otevřete CoPlanly pro aktualizaci", de "Öffne CoPlanly zum Aktualisieren", ru "Откройте CoPlanly, чтобы обновить", uk "Відкрийте CoPlanly, щоб оновити" (apostrophes and quotes per "Localization").
- [ ] **Step 3:** Add `WidgetSnapshot` (fully qualified, as a string literal) to `R8JsonProbe`, and the model to `GsonParityTest` and `StoredJsonVectorsTest`. Run `node tools/check-invariants.js` → PASS.
- [ ] **Step 4:** Commit `feat(widget): a seven-day snapshot of the Today widget's lines`.

### Task 4: Write the snapshot on the iPhone, and open the Calendar from a link

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/WidgetSnapshotWriter.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosWidgetClock.kt`, `iosApp/CoPlanly/WidgetReloadBridge.swift`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/IosApp.kt` (triggers, sign-out order), `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/LaunchLinks.kt` (`coplanly://calendar`), `shared-data/src/commonMain/kotlin/com/coparently/app/data/sync/ForegroundSyncRunner.kt` (a `completedRuns: SharedFlow<Unit>`), the sign-out and deletion paths that end the session on the iPhone
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/WidgetSnapshotWriterTest.kt`, `LaunchLinksTest` (common)

**Interfaces:**
- Consumes: Task 3; `AppGroupShare.SUITE`; `appReminderWording`'s language rule (`readerLanguageTag()`), `BundleWordingStrings(tag)`.
- Produces:
  ```kotlin
  interface WidgetReload { fun reloadAll() }   // Swift: WidgetCenter.shared.reloadAllTimelines()
  class WidgetSnapshotWriter(directory: Path, reload: WidgetReload, /* sources */ …) {
      suspend fun write(): Boolean   // true when the file changed and a reload was asked
      suspend fun writeSignedOut()
  }
  LaunchLink.Calendar   // data object; LaunchLinks.parse("coplanly://calendar") == LaunchLink.Calendar
  ```
  `IosApp.start` gains a parameter `widgetReload: WidgetReload` (Swift passes the bridge).

- [ ] **Step 1: Write the failing tests** (`WidgetSnapshotWriterTest`, over a temporary directory and a counting reload):
  - `writesThroughAPartFile` — after `write()`, `today.json` exists and no `.part` remains.
  - `unchangedSnapshotSkipsTheReload` — two writes with the same inputs and `writtenAtMillis` excluded from the comparison: one reload.
  - `widgetSnapshotOfAnotherAccountIsNeverKept` — written for A, then `writeSignedOut()`: the file is signed-out and names nothing of A's.
  - `languageChangeRewritesTheSnapshot` — same data, language `en` then `cs`: two reloads, `language == "cs"`.
- [ ] **Step 2:** Implement the writer: it reads today in the phone's zone, the events of the seven days (`EventRepository.getEventsByDateRange` over the week, split per day as `HomeWeek.todayOf` does), the stored custody (`CustodyModelRepository.storedCustody()`), the names (`TodayWidgetNames.recall`), words through `BundleWordingStrings(tag)` and `IosWidgetClock(tag)` (Foundation: the date template "MMMEEEd" and the time style the phone's 12/24 setting picks), encodes with `StoredJson`, compares without `writtenAtMillis`, writes `.part` then renames, then `reload.reloadAll()`.
- [ ] **Step 3:** Triggers in `IosApp.startSession`: `completedRuns` of the sync runner; Room's `invalidationTracker.createFlow("events", "custody_models", "users")` debounced 2 s; every change of the signed-in uid (a null uid → `writeSignedOut()`); every foreground (`IosLifecycle.observe`); every change of the family on screen; `ParentsSource.parents` collected for the session, each loaded answer handed to `TodayWidgetNames.remember`, a change rewriting the snapshot. Sign-out and deletion call `writeSignedOut()` and `TodayWidgetNames.clear()` before the session ends.
- [ ] **Step 4:** `LaunchLinks.parse("coplanly://calendar")` returns `LaunchLink.Calendar`; `IosApp.openUrl` arms it as `PushDestination.CALENDAR` (the part C path that selects the Calendar tab on today). Add `calendarLinkOpensTheCalendar` to `LaunchLinksTest`.
- [ ] **Step 5:** Commit `feat(ios): write the Today widget's snapshot into the App Group`.

### Task 5: The widget extension

**Files:**
- Create: `iosApp/CoPlanlyWidget/TodayWidget.swift`, `iosApp/CoPlanlyWidget/TodayWidgetViews.swift`, `iosApp/CoPlanlyWidget/Info.plist`, `iosApp/CoPlanlyWidget/CoPlanlyWidget.entitlements`
- Modify: `iosApp/project.yml` (target `CoPlanlyWidget`, `app-extension`, embedded in `CoPlanly`, deployment 17.0, bundle `app.coplanly.widget`), `iosApp/README.md`

**Interfaces:**
- Consumes: Task 3's JSON shape (decoded with `Codable` structs mirroring `WidgetSnapshot`, `version == 1`), Task 4's path.

- [ ] **Step 1:** `TimelineProvider`: read `<group container>/widget/today.json`; one entry per `days` element dated `Calendar.current.startOfDay(for: date)` (today's at `.now`), then one stale entry at the day after the last, policy `.atEnd`. No file or an unreadable one: one entry with the English signed-out line "Sign in to CoPlanly to see today." (the extension's only own text). `placeholder` and `snapshot` use a static sample.
- [ ] **Step 2:** Views: `.systemSmall` draws `compact` (or `compactLarge` when `dynamicTypeSize.isAccessibilitySize`), `.systemMedium` draws `tall`: title, custody, windows, handover, events with a 3 pt mark, footer; system text styles; a line's colour from `light`/`dark` by `colorScheme`, else `.primary`/`.secondary`; `containerBackground(for: .widget)`; `widgetURL(URL(string: "coplanly://calendar"))`. Supported families exactly `[.systemSmall, .systemMedium]`.
- [ ] **Step 3:** XcodeGen: the target, the App Group entitlement only, embed in the app. The `ios-app` job's build covers it (no new CI step).
- [ ] **Step 4:** Commit `feat(ios): the Today widget on WidgetKit`.

### Task 6: Background refresh and the daily school import

**Files:**
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/DailySchoolImport.kt`, `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/BackgroundRefresh.kt`
- Modify: `ForegroundSyncRunner.kt` (`suspend fun syncAndAwait()`), `shared-data/src/iosMain/.../di/koin/PlatformDataModule.ios.kt` (afterSync adds `DailySchoolImport.runIfDue()`; `NoSchoolImportJob` → `IosSchoolImportJob`), `IosUnavailable.kt`, `iosApp/CoPlanly/AppDelegate.swift`, `iosApp/CoPlanly/Info.plist`, `IosApp.kt` (`runBackgroundRefresh`, scheduling hooks)
- Test: `shared-data/src/commonTest/.../data/school/DailySchoolImportTest.kt`, `shared-data/src/commonTest/.../data/sync/ForegroundSyncRunnerTest.kt` (added cases), `shared-ui/src/iosTest/.../ios/BackgroundRefreshTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class DailySchoolImport(importer: SchoolImporter, preferences: SecurePreferences, signedInUser: SignedInUser,
      clock: kotlin.time.Clock = Clock.System, zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) {
      suspend fun runIfDue(): Boolean   // true when it ran today's import
  }
  // ForegroundSyncRunner
  suspend fun syncAndAwait()           // requests a run and returns when a run that started after the request ends
  class BackgroundRefresh(protectedDataAvailable: () -> Boolean, signedIn: () -> Boolean,
      sync: suspend () -> Unit, writeWidget: suspend () -> Unit) {
      suspend fun run(): Boolean        // true when it ran to its end (or ended at once by rule)
  }
  ```
  Swift: `BGTaskScheduler.shared.register(forTaskWithIdentifier: "app.coplanly.refresh", using: nil)` in `didFinishLaunching` handing the `BGAppRefreshTask` to `IosApp.shared.runBackgroundRefresh(completion:)`; `expirationHandler` cancels the run; `setTaskCompleted(success:)` in every path; `scheduleRefresh()` on `applicationDidEnterBackground` and after each run; `BGTaskScheduler.shared.cancel(taskRequestWithIdentifier:)` on sign-out (a `BackgroundScheduling` Kotlin interface the Swift side implements, passed to `IosApp.start`).

- [ ] **Step 1: Write the failing tests.**
  - `DailySchoolImportTest`: `runsOnceADayPerAccount` (two calls the same local day: one `syncAll`); `runsAgainTheNextDay`; `anotherAccountRunsItsOwn`; `nobodySignedInDoesNothing`; `aFailedImportIsNotRecordedAsDone` (syncAll throws: a later call runs).
  - `ForegroundSyncRunnerTest`: `awaitedRequestJoinsTheRunningSync` (a run in progress; `syncAndAwait` returns only after the follow-up run, and only two runs happen in total); `completedRunsEmitsAfterEachRun`.
  - `BackgroundRefreshTest`: `lockedPhoneDoesNothingAndSucceeds` (protected data unavailable: sync and widget never called, `true`); `signedOutDoesNothing`; `aFailedSyncStillWritesTheWidget`; `cancelledRunReportsNoSuccess` (cancel mid-sync: the call throws `CancellationException`, the Swift side reports `false`).
- [ ] **Step 2:** Implement. The day marker key `school_import_daily_<uid>` holds `IsoText.date(today)`, written only after `syncAll()` returns. `IosSchoolImportJob.schedule()`/`cancel()` are no-ops beyond logging (the daily run rides the syncs). `Info.plist`: `BGTaskSchedulerPermittedIdentifiers = [app.coplanly.refresh]`, `UIBackgroundModes` gains `fetch`.
- [ ] **Step 3:** Commit `feat(ios): background refresh and the daily school import`.

### Task 7: Documents

**Files:**
- Modify: `CLAUDE.md` (design item 18's iPhone half; architecture map and "Where an iPhone path lives": `CoPlanlyWidget/`, `WidgetSnapshot`, `BackgroundRefresh`, `DailySchoolImport`; "Phase 4's part D" shapes; the stand-ins list loses `NoSchoolImportJob`), `docs/ROADMAP.md` MON-24, `docs/DEVICE-CHECKLIST.md` (new §3.44, the five checks of spec §8, and `tools/manual-test-plan.js`'s `RULES` mapping `iosApp/CoPlanlyWidget/` and the widget files to §3.44), `docs/legal/PRIVACY-POLICY.md` (the App Group sentence: "and the widget's lines"), regenerated `web/privacy/` (`web/README.md`'s command), `iosApp/README.md`.

- [ ] **Step 1:** Write them from the code as merged in Tasks 1–6.
- [ ] **Step 2:** Run `node --test tools/test/*.test.js` and `node tools/check-invariants.js` → PASS.
- [ ] **Step 3:** Commit `docs: record iPhone phase 4 part D`.
