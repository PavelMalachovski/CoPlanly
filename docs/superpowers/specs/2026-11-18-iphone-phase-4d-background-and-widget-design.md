# iPhone app, phase 4 part D: background work and the Today widget — design

Status: approach agreed with the owner on 18 November 2026 (chat); the locked-phone question
answered the same day (option b). Approved for planning once the owner approves this document. Parent design:
`docs/superpowers/specs/2026-11-16-iphone-phase-4-design.md` §2 and §4 (part D). Parts A–C are
merged (pull requests #167–#169).

## 1. Intent

An iPhone parent should find the family's calendar current when they open the app, and see today
at a glance on the home screen, as an Android parent already does. Today the iPhone does neither
without the app open:
- it syncs only at launch and on every return to the foreground (`ForegroundSyncRunner`);
- the school import's daily run is not scheduled (`NoSchoolImportJob`), so it runs only from
  its button;
- there is no widget.

Part D closes both gaps. It is the last piece of phase 4 that changes what a parent sees day to
day; part E is release readiness.

## 2. Goals

1. **Background refresh.** A `BGAppRefreshTask` runs, when iOS grants one, the chain the app
   already runs on opening:
   - the sync (`SyncService.performFullSync` through the existing runner's rule);
   - the Google Calendar import (`GoogleCalendarAutoImport.importIfDue`, as after every sync);
   - the school import, at most once a day, as Android's `school_import_daily`;
   - the local reminders planned again (`IosLocalReminders.requestRearm`, part C);
   - the widget's snapshot written again (goal 2).

   It schedules its own next request every time it runs and when the app goes to the
   background.
2. **The Today widget on the home screen** (owner: home screen only, the small and the medium
   size, as on Android — choice A of 18 November 2026). Same content as Android's (CLAUDE.md
   design item 18):
   - the date;
   - whose day it is;
   - the day's contact windows with the other parent;
   - the next handover (a day and a person, never an hour);
   - today's events.

   The small size counts the events, as Android's compact layout does. The medium size lists up
   to four events and says how many it left out, as Android's tall layout does. A tap opens the
   app on the Calendar tab.
3. **One computation and one wording with Android** (item 18). The model (`TodayWidgetModel`)
   and the wording (`TodayWidgetText`) move from the Android app to `:shared-ui`, unchanged in
   what they decide and say. Android draws from them as today; the iPhone draws from them too.
4. **The widget stays right without the app.** It switches to the next day at midnight on its own,
   and shows the next days correctly for a week even if neither the app nor a background task
   runs.

## 3. Non-goals

- The lock screen, StandBy, the large size and an interactive widget (owner, choice A).
- A Live Activity for a handover (CLAUDE.md design item 20: not allowed, and the schema has no
  handover hour).
- Background *processing* tasks (`BGProcessingTask`), silent pushes that wake the app to sync, or
  a background fetch on a server's signal. The refresh is best-effort by design.
- Any change to what Android's widget says or how it refreshes, beyond reading the moved code.

## 4. Anti-goals (it fails even if it works)

- **The widget says something the app would not.** A different day owner, a handover the app
  does not show, a private event of the co-parent, a parent called "Mom" or "Dad", or a line in a
  language the app does not speak.
- **The widget shows the previous account.** After sign-out or an account switch, the widget must
  say "signed out" (Android's line) before anything else can be read from it.
- **The widget carries more than it draws.** The snapshot holds the lines and colours to draw,
  never a record, a uid, a token or an e-mail address. It sits in the App Group, which the
  notification extension also reads.
- **A background run harms what a foreground run does.** Two runs at once, a sync cut off in a way
  that leaves the outbox inconsistent, or a school import twice in a day.
- **A background run that cannot finish keeps the app awake.** It must end when iOS takes its time
  back (`expirationHandler`), and report the end to iOS in every path.

## 5. Constraints

- **The widget extension cannot run the data layer.** It has about 30 MB of memory, cannot open the
  app's database (in the app's own container, not the App Group) and should not link the 100 MB+
  `Shared` framework. It is plain Swift (SwiftUI and WidgetKit) and reads one file.
- **The App Group `group.app.coplanly`** is the only shared storage (part C). Today it holds two
  keys for the notification extension. The widget's snapshot is one more item there, a JSON file in
  the group's container.
- **The database is unreadable while the phone is locked** (`NSFileProtectionComplete`, the owner's
  substitute for SQLCipher, CLAUDE.md item 20). Owner decision of 18 November 2026: the protection
  stays. A background run therefore does its work only while protected data is available (the
  phone unlocked since its last lock, the app merely in the background) and ends at once otherwise;
  the widget's week of lines covers the locked hours.
- **`BGAppRefreshTask` is best-effort.** iOS decides when, from the parent's use of the app, and
  may never run it (Low Power Mode, Background App Refresh switched off). A run has about 30
  seconds. Nothing may depend on it running.
- **The simulator can trigger a task** only through the debugger
  (`_simulateLaunchForTaskWithIdentifier`); CI has no debugger session. So the background chain is
  tested as a function called directly, and the registration is checked by the app launching with
  it.
- **Words and dates.** The widget's lines are worded in the app's language (`AppLanguageRule`, as
  the notification extension's), with the reader's clock through `ReaderDateText`'s iPhone half.
  The 12/24-hour follow-up deferred from part C applies here too: the widget uses whatever
  `ReaderDateText` does on the iPhone at the time.
- **Android's behaviour is held by its tests.** `TodayWidgetTextTest` (Robolectric, English and
  Russian) and the UI tour's widget shots must pass unchanged after the move.

## 6. Approaches

The owner chose 1 on 18 November 2026.

1. **The app prepares the lines; the widget only draws them. Chosen.** After every sync, every
   change to the rows the widget reads, every background run, every change of language or account,
   the shared Kotlin code builds the model and the worded lines for today and the next six days,
   for both sizes, and writes them as one JSON file into the App Group. It then asks WidgetKit to
   reload. The Swift widget reads the file, makes one timeline entry per day starting at local
   midnight, and draws the day's lines. One computation and one wording with Android. The extension
   uses almost no memory. The cost: the widget is never newer than the app's last run or its last
   background run.
2. **The widget reads the database through the Kotlin framework.** It would need the database
   moved into the App Group container, safe access from two processes, and the whole framework in
   an extension with about 30 MB. Too much risk for a little more freshness. Rejected.
3. **The widget computes in Swift.** It would break "one computation, one wording" and drift from
   Android over time. Rejected.

**Background work** has one sensible shape: `BGAppRefreshTask`, running the chain of goal 1 under
the existing runner's rule. A `BGProcessingTask` would add nothing a refresh does not do here, and
iOS runs it even less often.

## 7. Design

### 7.1 The model and the wording move to `:shared-ui`

- `TodayWidgetModel` (its `of` and `signedOut`), `TodayWidgetLines`, `WidgetLine`,
  `WidgetEventLine`, `TodayWidgetContentState` and `compactAtLargeText` move to `:shared-ui`'s
  `commonMain`, package `presentation/widget`, on kotlinx-datetime. They are pure.
- `TodayWidgetText` (the wording) moves with them. It reads Compose Multiplatform resources where it
  read `R.string`/`R.plurals`, and `ReaderDateText` where it read `localizedDate`/`shortTime`. It
  takes a strings reader as a parameter, not a `Context`, so it can word outside composition. On
  Android it reads the app's own resources through `inAppLanguage()`, as today.
- Android's `TodayWidget`, `TodayWidgetContent`, `TodayWidgetRefresher`, `TodayWidgetNames` and the
  Glance code stay in the app and read the moved code. `ShellBoundaryTest` already allows the widget
  there.
- `TodayWidgetTextTest` moves or stays so that it still holds English and Russian to the same
  strings, and the UI tour's widget shots do not change.

### 7.2 The snapshot

`WidgetSnapshot` (in `:shared-ui`, `@Serializable`, through `StoredJson`, with a case in the R8
probe as every stored model):
- `version` (1);
- `writtenAtMillis`;
- `language` (the BCP 47 tag it was worded in);
- `signedIn`;
- `days`: a list of seven entries, from today, each with `date` (ISO), `compact` and `tall` (the
  two `TodayWidgetLines`), and each line's colour already resolved to two hex values, light and
  dark, from the family's palette (`ParentColors` on the widget's own background), so the Swift
  side never computes a colour.

The parents' names come from a cache the app writes while it shows a signed-in account (the twin of
Android's `TodayWidgetNames`, in `SecurePreferences`, cleared with the session and refused for
another uid), so a background run never attaches `ParentsSource`'s Firestore listeners for a label
(author's call). Each day also carries a third variant, `compactLarge` (`compactAtLargeText` applied
in Kotlin), which the small widget draws at the accessibility text sizes, so Android's drop rule is
not rewritten in Swift (author's call).

It holds lines, never records: a private event of the co-parent never reaches it, because the
model already drops it (the same rule as Home). A signed-out snapshot holds one day with the
signed-out line and nothing else.

Written by `WidgetSnapshotWriter` (iOS only, `shared-ui` `iosMain`) to
`<App Group container>/widget/today.json`, through a `.part` file and a rename, then
`WidgetCenter.reloadAllTimelines()` through a small Swift bridge (`WidgetReloadBridge`). A write
whose JSON equals the stored one skips the reload.

**When it is written** (the iPhone's twin of `TodayWidgetRefresher`'s triggers):
- after every sync run (foreground or background);
- on a Room change to `events`, `custody_models` or `users` (the same `WATCHED_TABLES`), debounced;
- on every change of the signed-in uid (sign-in, sign-out, switch, deletion) — sign-out writes the
  signed-out snapshot **before** the session ends;
- on every return to the foreground (a language changed in Settings shows here, as for the App
  Group's language key);
- on a change of the family on screen (the widget draws the family on screen, as Android's does).

### 7.3 The widget extension

A new target, `CoPlanlyWidget` (`app.coplanly.widget`), Swift, SwiftUI and WidgetKit, embedded in
the app, with the App Group entitlement only. One widget, "Today", families `.systemSmall` and
`.systemMedium`.

- **Timeline.** One entry per day in the snapshot, at that day's local midnight, today's at "now";
  policy `.atEnd`. After the last day it shows the signed-out-looking empty line
  ("Open CoPlanly to update") — new copy, in the five languages, written into the snapshot by the
  app so the extension needs no strings of its own.
- **Layout.** Mirrors Android's (sketch below): the title, the custody line in the day parent's
  colour, the contact windows, the handover, then the events (medium only) with a 3 pt mark in the
  owner's colour, then the footer. System font (SF Pro, as the app's text since part D of phase 3),
  sizes from the widget's own text styles, Dynamic Type followed by the system. The small size uses
  `compactAtLargeText` from the accessibility sizes, as Android does from font scale 1.3.
- **Colours.** The background is the system's widget background (`containerBackground`), the
  neutral text the system's primary and secondary colours; a parent's line uses the snapshot's
  light or dark hex by the widget's colour scheme. Dark mode follows the system, as on Android.
- **Tap.** `widgetURL(coplanly://calendar)`, which `LaunchLinks` learns to read as "the Calendar tab
  on today" (the tab tap's rule, design item 5).
- **No snapshot yet** (placed before the app ever ran): the signed-out wording in English, the
  extension's one fallback.

```
small                         medium
┌─────────────────────┐      ┌──────────────────────────────────────────┐
│ Today · Thu 19 Nov  │      │ Today · Thu 19 Nov                        │
│ Today with Alex     │      │ Today with Alex          (Alex's colour)  │
│ Bob 15:00–19:00     │      │ Bob 15:00–19:00                           │
│ Handover to you in  │      │ Handover to you in 2 days                 │
│ 2 days              │      │ ▌09:00  Dentist – Emma                    │
│ 3 events today      │      │ ▌14:00  Swimming                          │
└─────────────────────┘      │ +1 more                                   │
                             └──────────────────────────────────────────┘
```

### 7.4 Background refresh

- **Identifier** `app.coplanly.refresh`, in `BGTaskSchedulerPermittedIdentifiers`, and
  `UIBackgroundModes` `fetch` in `Info.plist`.
- **Registration** in `AppDelegate.didFinishLaunching`, before it returns (iOS requires it), handing
  the task to `IosApp.runBackgroundRefresh(completion)`.
- **The chain** (`BackgroundRefresh`, `shared-data` or `shared-ui` `iosMain`, a plain suspend
  function a test calls):
  1. stop at once, reporting success, when protected data is not available
     (`UIApplication.isProtectedDataAvailable` false: the phone is locked; owner decision (b) of
     18 November 2026) or nobody is signed in — no step touches the database first;
  2. the sync, through the existing `ForegroundSyncRunner` so a foreground run and a background run
     never overlap (it already coalesces requests); the run awaits that request's completion;
  3. the Google Calendar import, as after any sync (already the runner's `afterSync`);
  4. the school import when the day's has not run (`SchoolImporter.sync` for each connection,
     remembered per account per day in the sealed preferences, as Android's daily `KEEP` work).
     **The same daily check also runs after the launch and foreground syncs** (author's call: a
     parent with Background App Refresh off, in Low Power Mode or always locked overnight would
     otherwise never get the daily import); at most once a day per account either way;
  5. the local reminders planned again;
  6. the widget snapshot written.

  Each step's failure is logged and does not stop the next. `expirationHandler` cancels the
  coroutine; the task reports success only when the chain ran to its end.
- **A background launch starts the app as any launch does** (author's call): `IosApp.start` runs
  unchanged, `startSession` included; the chain awaits only its own sync request. A separate
  background start mode would change the entry point for little saved time.
- **Scheduling.** `BGAppRefreshTaskRequest` with `earliestBeginDate` 30 minutes ahead, submitted on
  entering the background and at the end of each run. `NoSchoolImportJob` becomes
  `IosSchoolImportJob`: `schedule()` and `cancel()` only record whether any connection exists, since
  the daily import rides the refresh. Sign-out cancels the pending request (`BGTaskScheduler.cancel`).

## 8. Tests

- **The moved code:** Android's `TodayWidgetTextTest` passes unchanged in what it asserts; the
  model's and the wording's pure tests run in `commonTest` (on the JVM and the iOS simulator), and
  the UI tour's widget shots do not change.
- **The snapshot:** `WidgetSnapshotTest` (commonTest): seven days from today; a signed-out account
  gives one signed-out day; the co-parent's private event never appears; colours resolved for light
  and dark; the language recorded; `StoredJsonVectorsTest` and the R8 probe case for the new model.
- **The writer** (iosTest, on the simulator): writes into a test App Group directory, skips an
  unchanged write, and the sign-out snapshot replaces the previous account's.
- **The chain** (iosTest): signed out does nothing; each step's failure does not stop the next; the
  school import runs once a day per account; a cancelled chain reports no success.
- **The widget** (`ios-app` job): the extension builds, its memory is not measured in CI (a widget
  runs in its own process the simulator does not expose to XCUITest). A Swift unit test target is
  not added: the extension's logic is "read a file, pick today's entry", and the tour shoots the
  widget gallery's preview of the placed widget is not reachable either. So the device check (below)
  holds the drawn widget.
- **The locked case** (iosTest): with protected data unavailable the chain touches nothing and
  reports success.
- **Device checks** (`docs/DEVICE-CHECKLIST.md`, new §3.44): place both sizes; lock the phone and
  let a refresh fire (nothing happens, nothing fails);  sign out and see the
  signed-out line; change language; leave the phone a night and see the next day; let a background
  refresh run (Xcode's simulate command on a development build) and see a co-parent's new event.

## 9. Documents

- CLAUDE.md: design item 18 gains the iPhone's half; the architecture map and "Where an iPhone path
  lives" gain `CoPlanlyWidget/`; ROADMAP MON-24's stand-ins list loses `NoSchoolImportJob`.
- Legal: nothing new is collected or sent. The privacy policy's App Group sentence (part C) gains
  "and the widget's lines"; `DATA-SAFETY.md` unchanged.

## 10. Left to the builder

- Where `BackgroundRefresh` lives (`shared-data` or `shared-ui` iosMain) and how the runner exposes
  "await this request's run".
- The debounce interval for Room changes (Android uses its own; reuse it).
- The exact SwiftUI type sizes, as long as they are the system's text styles.
