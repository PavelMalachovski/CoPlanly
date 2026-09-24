# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repository.

## What this project is

CoPlanly — an Android shared-calendar app for separated parents. Kotlin + Jetpack Compose
(Material 3), Clean Architecture with Hilt, Room as the offline-first source of truth,
Firebase (Auth/Firestore/FCM) for sync between the two parents, Google Calendar integration.
**No AI:** the Gemini subsystem was deleted in August 2026 (MON-7) — ~3,200 lines reachable from
no navigation graph, with the API key shipping in every APK. If AI returns it goes behind the
Cloud Function proxy (SEC-1), never with a key in the client. See `docs/AUDIT-2026-08.md` §6.

**The plan of record is `docs/ROADMAP.md`** — one document, merged on 2026-08-25 from
`docs/BACKLOG.md` and `docs/CoPlanly/MVP_phases.md`, both now deleted (`.cursor/roadmap.md` is the
historical original plan). MVP 1 **and** MVP 2 are complete: §2 re-baselines all three phases
against the code rather than against memory, which is what this line failed to do for months while
it said "MVP 2 is next". Two things to read before planning anything: **§1**, which says for every
open item whether a cloud session, a CI job, or a machine with an Android SDK and a phone can do
it, and **§10**, the dependency order.

**The latest full audit lives in `docs/AUDIT-2026-08.md`** (`AUDIT-2026-07.md` is the previous
one; `AUDIT-2026-09.md` adds Play closed-test readiness, motion and UI/UX on top of it); `docs/ROADMAP.md` §3 is the live version of its §5. The app still cannot be published: no
hosted privacy policy, no signing config, no Play listing. Two claims that paragraph used to make
are **no longer true** and were corrected here rather than left to mislead — in-app account
deletion ships (server-side teardown plus a local wipe, PR #68), and the `applicationId` is now
`app.coplanly`, decided while it still could be. What is still permanent at first upload is that
id, so REL-1's console half has to be finished before anything is uploaded.

## Design refresh (August 2026) — implemented, keep consistent

Second pass over the six main screens, from a Claude Design audit. It builds on (does not
replace) the July 2026 overhaul below — those invariants still hold except where noted here.

1. **Shared UI primitives** live in `presentation/common/DesignSystem.kt`: `SectionGroup`
   (one tonal container per run of rows; call its scope's `Divider()` between rows — this line
   used to say they were inserted for you, and they are not), `SectionRow` (icon,
   title, status/value, **at most one** trailing control), `GroupLabel`, `PillChip`, and
   `EmptyState` (UX-9, September 2026: icon on a tonal disc, title, optional description, optional
   primary action; takes the caller's `modifier` so Scaffold padding applies, and scrolls when its
   height is bounded — every empty list renders through it, so don't add a bespoke column or a
   `Card { Text }` for one, and don't pass an action that does nothing). Home,
   Settings, Expenses and Chat all render through these — do not reintroduce
   `Card { ListItem { … } }` per row, which is what the audit called "double surfaces".
2. **Parent colours go through `presentation/theme/ParentColors.kt`**: `fill()` for dots,
   bars and tints; `text()` for anything that is a foreground (it picks the theme-aware
   `*Light`/`*Dark` partner). The raw `MomPink`/`DadBlue` are fill-only — using them as text
   fails AA. The luminance test lives in `ParentColors`, not copy-pasted per screen.
3. **Colours come from `MaterialTheme.colorScheme`, not literal hex.** The prototype was
   authored in dark and its palette *is* `DarkColorScheme`, so every value has a role
   (`#1F1F25` = `surfaceContainer`, `#C2C1FF` = `primary`, …). Using roles is what keeps
   light theme working on the redesigned screens.
4. **Settings is four labelled groups** — Family, Sync, App, Account, in that order (Family
   first: it is the product, and it used to sit below the sync cards that depend on it).
   Google Calendar's actions expand from its row rather than stacking buttons in a card.
5. **Calendar header is one row**: title (which *is* the Month/Week/Day picker), Today,
   Filters, gear. Change requests are inline banners over the grid
   (`components/CalendarBanners.kt`), not a badged glyph. School vacation is neither a banner (it
   changed the grid's height between months) nor the old teal strip: since MON-13 it is a thin
   neutral line along each vacation day's bottom edge (`DayCellFill.schoolVacation`, theme
   `outline`, no height). Month
   cells carry event **dots**, tapping a day selects it **and opens Day view** (an owner
   decision from the Aug 2026 walkthrough — a select-only tap left no route to creating an
   event on a chosen day; an empty hour slot in Day view is that route), and
   the grid fills its screen. *(Aug 2026, second pass: the `DayAgendaCard` no longer sits
   under the grid — it renders on Home as the "today" card (`HomeWeek.todayOf`), fed by the
   same `DayAgendaCard` composable so the two surfaces cannot drift. Month paging is snapped
   by `MonthView`'s own nestedScroll settle — one 500 ms tween, identical in both
   directions — with `calendarScrollPaged = false`; don't hand snapping back to the library,
   whose spring read differently per direction.)*
6. **There is no weekly-summary screen any more** (commit `340af30` removed the screen, its
   ViewModel, the route and the strings; this line used to say it had "exactly one entry point").
   Home's seven-day card is the surviving surface — don't add a summary route back, and don't
   resurrect the unlabelled `view_list` action in the calendar header either.
7. **The Chat tab renders the thread in place** when there is exactly one conversation
   (`ConversationsScreen` composes `ChatScreen` with `onBack = null`). Do not "fix" this by
   navigating instead: that drops the tab route, hides the bottom bar, and makes Back bounce
   off a list that immediately forwards again.
8. **No affordance may promise a feature that doesn't exist.** The composer's `+` was
   captioned "attach" and opened message templates; templates are now a labelled chip, and the
   attach button came back only when attachments shipped (MON-23, item 31) — a paperclip
   *beside* `MessageInput`, which opens a real picker and a real upload. Same rule shaped the thread
   header (`ChatThreadHeader`): it shows the co-parent's initial, their name and whether
   **your own** messages left the device (derived from `Message.status`), not the mock's
   "Synced just now" — the app tracks no chat sync timestamp, so printing one would be the
   same defect. Destructive actions follow the sign-out anatomy: a red `SectionRow` that
   confirms, not a filled error button (Settings sign-out, Pairing unpair).
9. **New user-facing strings go into all five locales** (`values`, `values-cs`, `values-de`,
   `values-ru`, `values-uk`) in the same commit — see "Localization" below.
10. **The weekend is a base layer, never a competing fill.** `presentation/calendar/DayCellFills.kt`
    decides a cell's `base` (neutral grey on Saturday/Sunday, in every grid row *including* the
    days borrowed from the neighbouring months) and its `overlay` (custody, public holiday,
    today) separately; `MonthView` and `DayWeekView` draw both as two chained
    `Modifier.background` calls. A single `when` picking one background is what made the weekend
    unreachable: `CustodyModel.getCustodyFor` never returns null, so on any account with an
    active custody model every in-month cell matched a custody branch and only the neighbouring
    months' days kept a tint. `WeekendBackgroundLight`/`Dark` are neutral greys applied at full
    strength — the old per-call-site 0.3/0.5 alphas are gone, so month and week read as one
    system. Don't "fix" a weekend that looks too subtle by putting it back ahead of custody:
    weekends are the days a separated parent checks first.
    **The custody band runs to the edge of the grid, borrowed days included** (Aug 2026, second
    pass). Those cells used to take the base and no overlay at all, so a band stopped mid-row and
    the reader had no rule to infer the rest of the pattern from — the same complaint the weekend
    fix answered, one layer up. They carry the band and the handover diagonal now, at
    `ADJACENT_MONTH_TINT_SCALE` of the custody alpha, because the grid must still say which month
    it is showing. What a borrowed cell still refuses is everything you would *act on*: the
    holiday tint, the proposal preview, the swap arrows, the long press. That is the line — a
    pattern crosses the month boundary, a thing you would answer does not. The school-vacation
    line (MON-13) is a pattern too and crosses into borrowed days at the same scale; it is a line,
    never a fill or a hue.

11. **Motion has one vocabulary** (September 2026 audit, `docs/AUDIT-2026-09.md` §4).
    `presentation/theme/Motion.kt` holds the only durations — `SHORT_MS` 150 (fades, crossfades),
    `MEDIUM_MS` 300 (navigation, forms), `LONG_MS` 500 (a month changing, the splash exit) — and
    `MONTH_PAGING_MS` is an alias of `LONG_MS`. Detail screens push (`slideInFromRight` …, also
    the `NavHost` default so a route that names nothing does not get Navigation's own 700 ms
    fade); **the four tabs fade-through between each other** (`tabEnter`/`tabExit` in
    `NavGraph.kt`), because peers have no direction. Don't add a literal duration — pick a
    token, or add one here with its reason.
12. **The chosen parent colour reaches every screen through one CompositionLocal** (UX-15,
    September 2026). `MainActivity` provides `LocalParentPalette` (`theme/ParentColors.kt`) from
    `ParentPaletteViewModel`, which maps `ParentsSource`'s `Parents.palette`; `ParentColors.fill`,
    `text`, `container` and `chipFill` are `@Composable` and read it as their default argument.
    So a render site needs no plumbing, and **a raw `CoPlanlyColors.MomPink`/`DadBlue` in a
    screen is a bug** — it draws pink for a parent who chose purple. Resolve the colour in
    composable scope before a draw lambda if you need it there. A label *on* a solid parent
    colour uses `chipFill` (the deep tone) with `ParentColors.onFill(...)` for the text, never
    white on the full hue (4.35:1 on pink). The picker (Settings → Family, onboarding profile
    step) was hidden behind `PARENT_COLOUR_PICKER_ENABLED` until this landed; the flag is gone.
13. **The family switcher is one state and one dialog, and it appears at two** (M-8, September
    2026). `presentation/common/FamilySwitcher.kt` holds `FamilySwitcherChip` (Home and Expenses
    top bars, beside the gear) and `FamilySwitcherDialog`, which the Settings row opens too; both
    read `FamilySwitcherViewModel`, observed off the signed-in Room row. Don't give Settings its
    own copy of the family list again — two sources for "which family is on screen" is how they
    come to disagree. With one co-parent the chip renders nothing. It is deliberately not on the
    Calendar header (item 5's fixed four). It is not on Chat either — the original reason (chat
    followed the first co-parent, whatever the switcher said) is fixed, and what remains is a
    layout call: the tab renders the thread in place (item 7) and its header already names the
    co-parent. **The chip and each dialog row carry a dot — never a count — when a family *not* on
    screen has something waiting**: chat newer than my read mark (the conversation *document*),
    a pending change request from that co-parent (a `limit(1)` query keyed on `requestedTo`, the
    field the rule reads), or a schedule proposal or day swap awaiting me (`custody_models/{id}`
    by **id** — `allow get` only, never a query). Only the selected family is mirrored, so no
    figure for another family could be backed. `data/family/OtherFamiliesSignals` holds those
    listeners per other family and reports which `FamilySignal` kinds are waiting; the chip's
    content description and the dialog row's line name them. Shared process-wide (`shareIn`,
    `WhileSubscribed`), **none at one family**, re-derived and cancelled on a switch, pairing
    change or sign-out, and bounded like `reconnecting()` per listener. Don't turn the dot into a
    number, don't attach a listener per composable, and don't let it create anything — it only
    reads (ROADMAP M-8).

## UX/UI overhaul (July 2026 design review) — implemented, keep consistent

Direction agreed after a live walkthrough and shipped on `feature/ux-overhaul`.
When touching the UI, keep these invariants:

1. **Bottom navigation bar** (Home / Calendar / Chat / Expenses) is the top-level
   navigation — see `presentation/navigation/BottomNavDestination.kt`. It shows only on
   those routes (`BottomNavDestination.topLevelRoutes`); detail screens hide it and keep
   an up-arrow. **Settings is NOT a tab** — it opens from a gear action in each top-level
   screen's top bar and is a detail screen (`onNavigateUp = popBackStack`, bottom bar
   hidden). `QuickActionsBottomSheet` was dead code and is gone — genuinely so as of the
   August 2026 audit; the file had in fact survived this note by several months.
   *(Aug 2026: budgets no longer open from an unlabelled Expenses top-bar action — they are
   a chip strip on the Expenses screen itself. Tab switches, including Home's stat-tile deep
   links, go through `NavHostController.navigateToTab` so they share one back-stack policy.)*
2. **Toolchain**: compileSdk/targetSdk 36, Kotlin 2.1 (+ `kotlin.plugin.compose`),
   Compose BOM 2025.10 (Material 3 1.4 / M3 Expressive), Room 2.7.2 (2.6.x breaks on
   Kotlin 2.x metadata), Hilt and Room on **KSP** (`2.1.0-1.0.29`; kapt is gone — move KSP with Kotlin), Navigation 2.9.3, Hilt 2.56.2, predictive back on.
3. **Calendar**: month view is a classic grid from the 1st with horizontal month paging
   (kizitonwose `HorizontalCalendar`); day/week use `HorizontalPager` with fling physics.
   Event chips are single-line (`softWrap = false` + ellipsis). School vacation is a thin
   neutral line along the cell's bottom edge (theme `outline`, 2 dp, no height), never a
   full-cell fill (it used to drown custody colors) and never a hue (teal is the calendar friend).
4. **Custody coloring** must go through the unified lookup in `CalendarScreen`
   (`getCustody`): active `CustodyModel` first, legacy `CustodyScheduleEntity` as fallback.
   Don't read the legacy schedules directly in a view — model-based custody would vanish.
5. **Event tap opens a preview bottom sheet** (`EventPreviewSheet`, details + Edit/Delete);
   the editor is the second step — on Home too since September 2026 (`HomeViewModel.
   openPreview`; Home passes `onDelete = null` because it has no delete-with-undo). The event form has a sticky bottom Save button.
6. **Color semantics**: Mom-pink/Dad-blue are parent identity ONLY, applied via
   `ParentColors` (which resolves the family's chosen palette — design refresh item 12), never
   `CoPlanlyColors.MomPink/DadBlue` directly in a screen. The theme's `secondary` slot is a neutral
   indigo (`CoPlanlyColors.Neutral*`), so generic Material selected states (FilterChips)
   are neutral — never wire pink through `colorScheme.secondary`. **Saturation rule** (so
   the day-cell wash and the event chip read as one system, not two pinks): a custody
   *day background* is the parent hue at ~14% alpha (`MomPink.copy(alpha = 0.14f)`), while
   a *chip / dot / marker* is the same hue at full strength. Same token, different alpha —
   intentional, keep it that way.
7. **Notification permission** is requested contextually via
   `rememberNotificationPermissionRequester()` (push toggle, reminder selection), never on
   cold start.
8. **Destructive list actions** use M3 `SwipeToDismissBox` with an Undo snackbar
   (see `EventListScreen`; the delete runs from `SwipeToDismissBox`'s `onDismiss`, material3 1.4,
   not the deprecated `confirmValueChange`); Undo re-creates the captured event (id is preserved).
   Danger actions (e.g. "Sign out of app") live at the bottom of their screen, not
   mid-list.
9. **User-facing strings** live in tracked, feature-named `res/values/*_strings.xml`
   files (`chat_strings.xml`, `expenses_strings.xml`, `settings_account_strings.xml`,
   `navigation.xml`, `event_preview.xml`, …). All `strings.xml` files are tracked too
   (secrets were moved to BuildConfig long ago); prefer the feature files for new keys.
   Never hardcode user-visible text in composables — see "Localization (i18n)" below.

DB note: installs older than the migration chain (schema < v5) are wiped via
`fallbackToDestructiveMigrationFrom(1,2,3,4)` in `DatabaseModule` — a v3 install used to
crash with "migration from 3 to 9 required but not found".

## Build & verify

```bash
./gradlew assembleDebug          # main build — run after every code change
./gradlew testDebugUnitTest      # JVM unit tests (MockK + coroutines-test + Turbine)
./gradlew lint detekt            # static analysis (detekt config in app/config/detekt)
```

```bash
cd functions && npm test && npm run lint    # Cloud Functions (mocha + eslint)
cd firestore-tests && npm test              # firestore.rules + storage.rules on the emulators
cd web-tests && npm test                    # web/verify/ in Chromium + the calendar feed as RFC 5545,
                                            # on the emulators (npm ci in functions/ and firestore-tests/ first)
tools/e2e/run-two-parent-tests.sh           # two parents on Auth/Firestore/Functions emulators;
                                            # needs a running Android emulator (see the e2e job)
```

- **Never debug `firestore.rules` by deploying to production and watching a phone.** That
  is how a broken `expenses` delete rule shipped once already. `firestore-tests/` runs the
  rules offline against the Firestore emulator; add a case there first. See its README —
  it needs a JDK 21+ on `PATH`, not just in `JAVA_HOME`. It covers **`storage.rules` too**
  as of the September 2026 pass (`rules/storage.test.js`, Storage emulator on 9199), which
  had no coverage at all before — the directory name is older than its contents. Those tests
  prove the ruleset *in this repository*; only a deploy settles what the live bucket enforces,
  which is exactly the gap the `pet_photos` entry below describes.
- Windows dev machine; Gradle wrapper works from Git Bash and PowerShell.
- **What only a phone can prove** (SEC-2's conversion of real data, REL-7's Gson-after-R8, the
  dark cold start, cross-time-zone chat, …) is one ordered script: `docs/DEVICE-CHECKLIST.md`.
  Add a check there when you ship something CI cannot see.
- `google-services.json` is required for the Google Services plugin, but the build
  degrades gracefully if it is missing (see the conditional apply in `app/build.gradle.kts`).
- **GitHub CI runs on every pull request, and on every push to `main`** — a push to a
  feature branch with no PR open is not built (`.github/workflows/ci.yml`, added
  August 2026 — this line used to say there was none). Fourteen jobs that test (this line used to
  say thirteen, before `web`; eleven before `upgrade` and `r8-runtime`; ten before detekt left the lint job; eight before `screenshots`
  and `e2e`; seven before `instrumented`), plus `report`, which only reads them (below): `changes` (a cheap gate,
  below), four Android ones — `build-test` (`assembleDebug` + `testDebugUnitTest` in a
  single invocation), `static` (`lint` alone — the id is kept), `detekt` (its own job since
  September 2026: the two ran in sequence, lint 5:15 then detekt 0:52), `release` (`assembleRelease`, where
  R8 runs, and where `node tools/check-r8-mapping.js` then reads R8's own `mapping.txt` and
  fails if a field a keep rule names came out renamed) — plus Cloud Functions, `web` (below), the
  Firestore rules suite against the emulator, and `invariants` (`node tools/check-invariants.js`, no
  dependencies and no Android SDK: locale completeness, format-argument agreement across the
  five locales, the four-way push-type agreement item 15 states, and the rule that every type
  Gson reflects over is covered by a `-keepclassmembers ... { <fields>; }` rule *and* has a case
  in the R8 runtime probe). The last two
  are one defect from two sides — the source says a rule exists, the mapping says it worked, and
  a typo in a package name passes the first and fails the second. A third side, the minified app
  actually *running*, is `r8-runtime` (below). They run **in parallel**; the Android three were one sequential job until the August 2026 CI
  pass, which is why a run took 13:22 for about 7 minutes of critical path. Two caveats, both
  deliberate. **detekt gates again** as of CQ-12 — do not add `continue-on-error` back to turn a
  red build green; fix the finding, or regenerate the baseline through the Regenerate workflow so
  that accepting debt is a visible commit. The **`instrumented` job** closes **CQ-1** as far as it
  can be closed, and the shape of "as far as" matters. `reactivecircus/android-emulator-runner`
  with the KVM udev rule runs `connectedDebugAndroidTest` as a **matrix of up to three emulators**
  (all three on `main` and when a PR reaches what they test — see "only the jobs its diff can
  affect" below)
  (`fail-fast: false`, AVD cached per level): **API 26** (minSdk, 32-bit x86 — a newer-API call
  only throws on an old device, and it is a second ABI for SQLCipher's native library), **API 30**
  (where the job was first made green), and **API 35 on `google_apis_ps16k`** (16 KB memory pages,
  which Play requires; a misaligned `.so` fails `System.loadLibrary` there). **The 16 KB leg runs
  no Hilt test** (`notAnnotation=…HiltAndroidTest` in the matrix's `test-args`): MockK's own
  inline-mocking agent, `libmockkjvmtiagent.so`, does not dlopen on 16 KB pages even at 1.14.0
  ("empty/missing DT_HASH/DT_GNU_HASH"), and every Hilt test mocks through it in
  `FakeFirebaseModule` — a test-tool failure that says nothing about the app. What the leg exists
  to prove is `NativeLibrariesTest`'s job, which is non-Hilt and mock-free: it loads SQLCipher and
  writes an encrypted database, and runs ML Kit OCR and barcode scanning on a blank image. Lift
  the filter only once a MockK release loads there. Every test check run sets `require_tests`, so
  a job that died before writing results cannot publish a green check. One caveat on 26:
  mockk mocks *final* classes only on API 28+, so if a Firebase type `FakeFirebaseModule` mocks is
  final, the Hilt UI tests fail on that leg alone — replace that mock with an open fake, do not
  drop the leg. When the job ran at API 30 alone, its first attempt failed for two unrelated reasons, both older than the job and neither previously observed.
  **(1) Firebase.** `AuthScreenTest` and `SettingsScreenTest` start the real `MainActivity`,
  whose Hilt graph reaches `FirebaseModule.provideFirebaseMessaging` →
  `FirebaseMessaging.getInstance()`, and CI has no `google-services.json` (it is gitignored), so
  the process died with "Default FirebaseApp is not initialized" and took the run with it at 15
  of 34 tests. `HiltTestRunner` substituting `HiltTestApplication` does not help: it stops
  `CoPlanlyApplication.onCreate` running, not the graph being built. Fixed by
  `androidTest`'s `FakeFirebaseModule`, a `@TestInstallIn` replacing `FirebaseModule` with
  relaxed mocks — except in a run that carries the emulator host (the `e2e` job, below), where it
  hands Auth, Firestore, Storage and Functions the real SDKs of `EmulatorEnvironment.appUnderTest`
  so the app's own screens talk to the emulators; Messaging, Analytics and Crashlytics stay mocks
  there too. **Do not "simplify" that by committing a fake `google-services.json`** — the
  Google Services and Crashlytics plugins apply only when that file is present, so adding one
  changes what every Android job builds in order to fix something that belongs to the tests.
  Room is deliberately left real, which is what makes this the first thing anywhere to execute
  the SEC-2 SQLCipher open path rather than merely compile it. **`EncryptedDatabaseTest`** goes
  further (September 2026): it builds every on-disk state `SqlCipherMigration` names — fresh
  install, plaintext upgrade with and without a stored passphrase, a stale export beside the
  original, an export whose rename never happened, a leftover beside an encrypted file, a lost
  passphrase — and opens each through `buildCoPlanlyDatabase`, the builder `DatabaseModule`
  itself calls, under a database name of its own. It snapshots and restores `DatabaseKey`'s
  preferences around each case, because that store has one fixed name the UI tests' real
  database also depends on; keep that if you add a case that forgets or mints a passphrase.
  **(2) Missing schemas.** `CoPlanlyDatabaseMigrationTest` held 14 test methods when the job
  was added, and only the six covering 11→12, 12→13 and 13→14 could run. The other eight name
  14→15 through 24→25 and need `15.json`–`24.json`, which do not exist and cannot be
  regenerated — `app/schemas/` holds 2–14, then 33, 34 and 36. Those eight have **never passed anywhere**; they were written against
  schemas that were already gone. They carry `@Ignore` naming the versions they want, so the
  job is green on what can run and the intent survives for whoever restores a schema. Do not
  read that as ordinary quarantine: an `@Ignore` normally hides a defect, and this one records
  missing data that no fix to the code can supply. The migrations a test can prove are those
  six plus 33→34 (MON-5's parenting plan), 34→35 (MON-13's region) and 35→36 (MON-6b's contact
  windows) — this line once credited a 33→34 test to MON-5 before one existed; it was written in
  September 2026 from `33.json` and `34.json`. The last two each run 34→36 through both
  migrations, because **`35.json` does not exist**: the build exports only the current version, and v35 and v36 landed on the
  same branch before the Regenerate workflow ran, so 35 was never current there. A schema
  version that is skipped this way is a new gap of the CQ-1 kind; run Regenerate after each
  version bump, not after a batch of them.
  **(3) Device checks** (September 2026) run in the same job: every date picker in two UTC+ and
  two UTC− zones (`PickerDatesTest`, `LocalDatePickerDialogTest` — which is why every `LocalDate`
  picker opens `presentation/common/PickerDates.kt`'s `LocalDatePickerDialog`; don't give a screen
  its own copy of the millis conversion again), the per-app locale switch, the export's files and
  share intent, and a signed-in walk of the main screens with a basic accessibility sweep;
  `docs/DEVICE-CHECKLIST.md` marks what they cover **[CI]**. Two things to know before adding one.
  A test that launches `MainActivity` signs in through `androidTest`'s `testing/SignedInSession` —
  a stubbed `currentUser` on the mocked `FirebaseAuth`, a real Room row with onboarding done, the
  telemetry question answered "no", all undone in `@After` because the emulator's files outlive the
  test — and **pauses the Compose clock** (`testing/PausedClock.kt`): the splash and the list
  skeletons animate for as long as a screen waits on a Firestore that never answers, so with the
  clock running every `waitForIdle` times out. And the accessibility sweep is a semantics check
  (unnamed or sub-48 dp icon-only controls), not ATF: `enableAccessibilityChecks()` needs an
  artifact this build does not declare.
  What stops the gap growing is a **step in `ci.yml`**: `git status --porcelain -- app/schemas`
  after the build, failing when the build produced a schema nobody committed. It is deliberately
  *not* `DatabaseSchemaExportTest`, which this line used to credit and which cannot do it — KSP
  (Room) writes that directory during the build immediately before the test reads it, so the file it
  looks for has just been created whether or not it is in the repository.

  The **`e2e` job** ("Android — two parents on the Firebase emulators", September 2026) is the
  two-phone round a runner *can* do. `app/src/androidTest/java/com/coparently/app/e2e/` runs two
  parents in one process — each an `EmulatorParent`: a **named** `FirebaseApp` built from
  `FirebaseOptions` for the credential-free `demo-coplanly` project, its own in-memory Room, and
  the production data layer constructed by hand from the constructors Hilt calls (one process has
  one `SingletonComponent`, and this needs two of everything). `tools/e2e/run-two-parent-tests.sh`
  wraps `firebase emulators:exec --only auth,firestore,functions,storage` around a Node smoke
  (`tools/e2e/pairing-smoke.js`, which pairs two accounts over REST in seconds and fails with a
  reason before an APK is installed) and `connectedDebugAndroidTest` filtered to that package with
  `-e coplanlyEmulatorHost 10.0.2.2`. What it proves, all against the real `firestore.rules` and
  the real `acceptPairingInvitation`: pairing on both phones (profiles, `families/{id}.slots`, the
  Room projection, one conversation); an event readable through the sync's own `array-contains`
  query, a private event absent from the server, a tombstone delivered as a tombstone; chat
  **across the date line** (UTC+14 and UTC−11) reaching unread, DELIVERED and READ on the right
  phones — **CQ-18's logic, closed as far as software can close it**; a shared expense that puts
  half the amount on the *other* parent's balance (the `splitBetween` class); and M-8's second
  family keeping its audience, `familyId` and announcement thread. Since September 2026 also
  **files** (`TwoParentAttachmentsTest`, Storage emulator and the real `storage.rules`): a chat
  attachment stays off the server while its upload fails and arrives after the outbox retry, the
  co-parent's download matches its bytes, a stranger's is refused, and a vault document opens for
  the co-parent, who cannot delete it, while the uploader's delete is a tombstone. Each
  `EmulatorParent` has `files`, `cache`, `no_backup` and preference names of its own
  (`PhoneDirectories`) — shared, Bob would "open" Alice's file from her leftover copy, and two
  `EncryptedPreferences` over one file overwrite each other (SEC-5). And **one parent on screen**
  (`OneParentOnScreenTest`): `MainActivity` as Alice, signed up on the Auth emulator and paired
  with Bob through the callable — Bob's event is drawn on her Home after one
  `SyncService.performFullSync()` (the call `SyncWorker` makes), his message in her thread, and
  what she types into the real composer reaches his phone. It starts `ChatMirror` itself, because
  `HiltTestApplication` never runs `CoPlanlyApplication.onCreate`. Its setup is the abstract
  `AliceOnScreenTest`, which two more classes share: `OnScreenAgreementsTest` (Bob's change request
  raising the calendar banner and accepted from the inbox; his custody proposal, day swaps and a
  seasonal layer popping up on Home and answered there, the layer then naming him in today's
  month-cell description) and `OnScreenFamiliesTest` (a third parent's message dotting the family
  switcher, the switch bringing her thread onto the Chat tab). They find everything through the
  app's own string resources and content descriptions — no test tag was added for them — with
  the Compose clock paused and every wait bounded. Five things not to undo.
  **No `google-services.json`** here either, for the reason given above. **The tests skip
  themselves without the host argument**, so the `instrumented` job runs them as skipped and
  keeps `FakeFirebaseModule` for everything else — and the `e2e` job fails on any skip, so the
  same switch cannot turn it green by running nothing. **Cleartext to `10.0.2.2` and `127.0.0.1`
  is allowed in `app/src/debug/res/xml/network_security_config.xml`, debug only**: Auth and
  Functions reach their emulators over plain HTTP through the platform stack, and an
  `androidTest` manifest cannot carry the exception because instrumentation runs under the
  *app's* policy. **Two JDKs**: the emulators take 21 through `FIREBASE_JAVA_HOME`, Gradle stays
  on 17 through `JAVA_HOME`. And the job is gated on its own `changes` output, `e2e`, which unlike
  `android` stays true for `functions/` and rules changes — it is the one job that runs the
  callable and the rules together. It found two defects on its first local run, both fixed in the
  same branch: `admin.firestore.FieldValue` is `undefined` under the Functions emulator's proxy
  (so `functions/index.js` now imports `FieldValue`/`Timestamp` from `firebase-admin/firestore`),
  and `EventDocument` threw on the `""` `toFirestoreMap()` writes for a missing end time, so the
  co-parent's sync skipped every event without one. **What it cannot do** stays on the device
  checklist: real FCM delivery (no emulator exists for it — the queue document is written, the
  push is not sent), two screens at once (Bob's side is the data layer), a file opened in a viewer
  app, and the push from another family switching families on tap (M-8).

  **A feature that works between two phones ships with its two-parent test** (September 2026).
  `tools/check-e2e-coverage.js` (run in `invariants`) discovers every shared surface from the
  sources that define it — each top-level `match` in `firestore.rules`, each prefix in
  `storage.rules`, each push type in `PushPayload.kt`, each callable and HTTPS function exported
  from `functions/index.js` — and fails unless `tools/e2e/coverage.json` names, for each one, a
  test in `app/src/androidTest/.../e2e/` (`"Class"` or `"Class#method"`, checked to exist) or an
  `exempt` reason. So a new collection, bucket path, push type or callable turns CI red until a
  test covers it; an entry naming a test that was renamed away turns it red too. Three things not
  to do. **Don't exempt what a test could run** — an exemption is for what no two-phone run can
  reach (Google OAuth, a type nothing produces any more), and its reason is read in review. **Don't
  cover a push by asserting a count alone**: read it back from `notification_queue` addressed to
  the right parent (`EmulatorEnvironment.awaitQueuedPush`, `EmulatorParent.queuedFor`), because
  the queue is exactly what the rules and the four-way agreement of item 15 decide. And **name the
  `checklist` sections** a test's mechanism covers (`"checklist": ["5.5"]`): the PR comment's
  manual plan then tells the tester the mechanism already ran and only what is drawn, the push
  and the real network are left — which is the point of the whole map.

  The **`upgrade` job** ("Android — upgrade over main", September 2026) is `docs/DEVICE-CHECKLIST.md`
  §2.1's install-over, for one release step, on the API 30 AVD. It builds the **base build** —
  the PR's base commit, or on `main` the commit before the push (`github.event.before`) — in a
  second checkout (`upgrade-base/`) and this branch's app and test APKs in the same job, so both
  are signed by the one `~/.android/debug.keystore` (`tools/upgrade/run-upgrade-test.sh` compares
  the three certificates with `apksigner` before installing anything). Then: install the base
  app and the test APK fresh; `am instrument` `upgrade/UpgradeSeedTest`, which runs **in the base
  build's process** and writes the real `coparently_database` through `buildCoPlanlyDatabase`
  (rows in eight tables, by SQL) and the real sealed preference store (a refresh token, settings,
  the telemetry answer), and leaves a marker; `adb install -r` this branch's app, keeping the
  data; `am instrument` `upgrade/UpgradeVerifyTest`, which opens the database through this build
  and asserts the passphrase was **recovered, not re-minted** (the wrapped value unchanged), the
  schema is the newest exported one with Room's identity hash, every row reads back (by SQL and
  through the DAOs), the file is still ciphertext, `lastUpdateTime` moved while
  `firstInstallTime` did not, and the preferences and consent answer survived. Four things not to
  undo. **The seed runs against the base build's classes**, so it calls only the signatures
  `UpgradeFixture`'s KDoc lists and writes rows by SQL adapted to `PRAGMA table_info`, never
  through a DAO: an entity's constructor changes with every added column, which is exactly the
  pull request this job is for. A PR that changes a listed signature fails the seed with
  `NoSuchMethodError` — adapt the fixture (reflection, or SQL) until the change is on the base.
  **Both classes skip themselves without `-e coplanlyUpgradePhase seed|verify`**, which is what
  keeps them — they write the real database and preference store — out of the `instrumented`
  job; the seed also refuses to run over an existing database. **A skip is a failure here**:
  `am instrument` prints "OK (1 test)" for one, so `tools/upgrade/instrument-results.js` (tested
  in `invariants`) judges each phase from the raw per-test status codes and writes the JUnit XML
  the check run and the PR comment read, with a skip as a failure. And **the status file decides
  the job**, as in the other emulator jobs. **What it cannot prove**: an upgrade from a build older
  than the base (a longer migration chain — `CoPlanlyDatabaseMigrationTest`'s job, as far as
  schemas exist), SEC-2's plaintext → encrypted conversion of an install older than SQLCipher (the
  base already encrypts, so this is encrypted → encrypted), a reboot, and a real phone's
  hardware-backed Keystore — those stay on the device checklist. **The job also runs the base
  build's `WireContractTest` over this branch's `wire/current/`** (item 5 of "Things that are easy
  to get wrong"), after the base's APK and before the emulator: skipped with a notice while the base
  predates that class or when the fixtures are unchanged, `continue-on-error` so the install-over
  still runs, and failed at the end by its own step. It costs the base's unit-test compile, and only
  on a wire-format change — `app/src/test/resources/wire/current/` is one of the job's inputs.

  **A second workflow file exists and is not part of CI**: `.github/workflows/regenerate.yml` runs
  `detektBaseline` and exports the Room schema, then commits both back to the branch it ran on.
  It exists because those are the two artefacts only a machine with an Android SDK can produce,
  and it is **manual on purpose** — regenerating a baseline accepts every violation that exists
  at that moment. Trigger it with `workflow_dispatch` from `main`, or, on a branch that has not
  merged, by touching `.github/regenerate-request`.

  **A third, also not CI: the UI tour** (`.github/workflows/ui-tour.yml`, September 2026) — full
  device screenshots of every main screen of the real app, for a design review by someone without
  a phone. `e2e/UiTourTest` and `UiTourOnboardingTest` are `AliceOnScreenTest`s that assert
  nothing: `UiTourSeed` fills both phones with a realistic family through the production
  repositories (custody with contact windows and a seasonal layer, Emma, Leo and Max, events,
  expenses in two currencies, budgets, plan, chat, a vault document, Bob's pending swap and
  request), `UiTourDriver` walks the app's own navigation by string resource, and `UiTourCamera`
  saves `UiAutomation.takeScreenshot()` PNGs plus a `manifest.json` in which a screen it could not
  reach is **skipped with its reason, never a failure**. `tools/ui-tour/run-ui-tour.sh` runs them
  with `am instrument` (not Gradle, which uninstalls the app and its files) three times on an API
  30 Pixel 6 emulator — `light-en-100`, `dark-en-100`, `light-ru-130`, the device's font scale set
  between runs — and `tools/ui-tour/gallery.js` writes the side-by-side `index.html`. The workflow
  force-pushes one fresh commit to `ui-tour/<branch, "/" → "-">` (and uploads the `ui-tour`
  artifact); it never writes to the branch it ran for. Trigger it with `workflow_dispatch`, or by
  touching `.github/ui-tour-request` on any branch — a change to that file, the workflow or
  `tools/ui-tour/` alone runs no CI job (`tools/ci-changes.js`). Two things to keep: the tour
  needs `-e coplanlyUiTour true` on top of the emulator host (`EmulatorEnvironment.assumeUiTour`),
  and `tools/e2e/run-two-parent-tests.sh` excludes its classes by name — skipped, they would fail
  the `e2e` job's no-skip check. A new screen gets a `camera.shot` in the tour.

  Still run the build locally before pushing — CI is a backstop, not a substitute.
  After switching branches, prefer `clean` — stale Hilt/KSP generated sources from another branch cause
  errors like "Could not find class file for '…Application'".
- **The `r8-runtime` job runs the minified app** ("Android — minified build at runtime (R8)",
  REL-7, September 2026). `release` and the mapping check prove R8 ran and kept the field names;
  neither sees R8 full mode's runtime failures — a `TypeToken` whose generic signature was stripped
  (Gson throws), a constructor or member removed, a class merged — nor that the JSON the app writes
  reads back. The `r8Test` build type (`app/build.gradle.kts`) is `initWith(release)`: the same
  proguard files plus `app/proguard-r8test.pro`, which keeps **only** the probe's entry point;
  non-debuggable (AGP runs R8 in a weaker debug mode for a debuggable build), signed with the debug
  key, telemetry flags off, no `applicationIdSuffix` (so a local `google-services.json` still
  matches). Its own source set, `app/src/r8Test/`, adds `R8ProbeInstrumentation`, a
  **self-targeting `<instrumentation>`**: `am instrument` starts it inside the minified process, it
  skips `Application.onCreate` (whose Hilt graph needs a default FirebaseApp, which CI has no
  `google-services.json` for — and must not get one), and `R8GsonProbe` builds the production
  classes by hand the way the e2e parents do: a child and a pet through `ChildInfoRepositoryImpl`/
  `PetRepositoryImpl` into an in-memory Room, signed out on a named `demo-coplanly` FirebaseApp, then
  the stored JSON columns and the repositories' own read-back; `DayOverrideJson`; the draft Gson from
  `SerializationModule`; the chat and revision mappers' `TypeToken`s; `Converters`; the calendar's
  `@Key` models. `tools/run-r8-probe.sh` installs and runs it on API 30; `tools/check-r8-probe.js`
  fails, one `::error::` each, on a key that is not the source field name (`bloodType`, never `a`),
  a value that does not read back equal, a probe that did not finish, and a Gson model
  `check-invariants.js` discovers with no case — and `invariants` already refuses that last one
  (check 5), so **a new Gson model arrives with a probe case**: name its fully-qualified type as a
  string literal in `R8GsonProbe` (a literal, because R8 renames the classes the report is about).
  Four things to know. **Never add an app keep rule to `proguard-r8test.pro`** — the probe would
  pass on a class the shipped build still breaks. **It is not byte-identical to `release`**: the
  probe adds callers, so R8 may inline differently; what it decides by rule (names, signatures) is
  the same, and that is what is checked. **It never reaches Firestore** — the documents' keys and a
  second phone reading them stay `docs/DEVICE-CHECKLIST.md` §4.1, argued for by the probe only as
  far as `toFirestoreMap()`'s medical profile is the same Gson call as the Room column it checks.
  And it is gated on `changes`' `r8runtime` (Android changes outside screens, resources and tests,
  plus the build, rules, workflow and the probe); `main` always runs it.
- **The `web` job tests what a court and an iPhone see** ("Web — verification page and calendar
  feed", September 2026; check run "Web — verify page and ICS", artefact `junit-web`). `web-tests/`
  is its own small package (Playwright and Mozilla's `ical.js`), deliberately **not** under `web/`,
  which `firebase.json` hosts whole, and not in `functions/`, whose dependencies ship.
  `web-tests/run-with-emulators.sh` starts Auth, Firestore and Functions with `firebase
  emulators:exec` from `firestore-tests/`' pinned CLI (JDK 21 through `FIREBASE_JAVA_HOME`, as the
  `e2e` job does, but no Android emulator) and runs one Playwright suite with a JUnit reporter.
  `verify-page.spec.js` serves `web/` from loopback and drives `web/verify/` in Chromium: receipts
  reserved and registered through the real callables in the order `ExportViewModel` uses, then the
  exported file (match, with time, period, format, size), a one-byte-tampered copy (no match), the
  file against another record ID, the ID as people retype it, an unknown ID and a bare reservation
  (not found), Czech and English, 375 px with no sideways scroll, and — on every answer — no uid,
  `familyId`, e-mail or name on the page or in the response, and nothing but the fingerprint in the
  request. `calendar-feed.spec.js` validates `.ics` as a client would, not as `calendar-feed.js`
  intends: `buildFeed` in all five languages and the real `calendarFeed` endpoint behind a link
  `createCalendarFeed` minted, through `ical.js` plus our own octet-level RFC 5545 check (CRLF,
  75-octet folds never inside a UTF-8 character, TEXT escaping, required properties, DTEND after
  DTSTART, UNTIL floating like its DTSTART, UIDs unique and stable, titles round-tripping), and a
  test that breaks each rule once so a pass means something. Four things not to undo. **The page
  reaches the emulator only through `?functions=`, which it honours only when it is itself served
  from a loopback address and the value names one too** — a hosted copy ignores it (a test serves
  the file under an https origin to prove it), so no link can redirect a verifier's fingerprint;
  don't widen it to "any origin" or read it from anywhere else. **Every test blocks
  `*.cloudfunctions.net` at the browser**, so nothing reaches production.
  **`COPLANLY_REQUIRE_EMULATORS=1`** (CI) fails an emulator test that would otherwise skip — the job
  cannot pass by running only the stubbed half. And **one worker, no retries**: `verifyExport`
  allows 30 lookups per address per ten minutes per instance, and the suite makes about ten. Gated
  on `changes`' `web` (everything but docs and the Android app and its build). Behind a proxy that
  ignores `NO_PROXY` for loopback the Functions emulator cannot register its Firestore triggers
  ("Unable to parse JSON") — unset the proxy variables for the run; nothing in it needs the network.
- **The `screenshots` job is how UI is reviewed without a phone** (September 2026). Roborazzi on
  Robolectric's native graphics renders the tests in `app/src/test/java/com/coparently/app/
  screenshots/` — Home's cards, the month grid with every `DayCellFills` layer, the calendar
  banners, a Settings group, `EmptyState`, the Expenses summary header, a chat thread, the event
  preview body, the consent screen and the family switcher chip — over a variant matrix of theme,
  the five languages, 1.0×/1.5× font scale and the default vs a purple/orange parent palette
  (`ScreenshotVariants`: nine variants for text-heavy components, four for the rest, 121 images — the count the committed baselines hold).
  **To view:** open the run's `screenshots` artefact, unzip, open `index.html`
  (`tools/screenshot-gallery.js`, no dependencies, filters by component/language/theme/scale/
  palette). Locally: `./gradlew recordRoborazziDebug` writes into `app/src/test/screenshots/` — don't commit what a laptop records (below).
  Five things to know. **It verifies against committed baselines** in `app/src/test/screenshots/`
  (`roborazzi { outputDir }`, the one directory record writes and verify reads), and **only the
  Regenerate workflow records them** — on the same runner image and JDK, because Robolectric's
  native renderer is pixel-stable per platform and font set, not across them. The job runs
  `verifyRoborazziDebug` whenever that directory holds an image (falling back to record, with a
  notice, while it holds none) and fails when a screenshot differs from its baseline or has none.
  **Changing the UI on purpose — or adding a screenshot test — means running Regenerate on the
  branch** (touch `.github/regenerate-request`); the new baselines arrive as a bot commit whose PNG
  diff is the visible acceptance, which is why it stays manual like the detekt baseline. On a
  mismatch the `screenshot-diffs` artefact holds Roborazzi's `<variant>_compare.png` and
  `_actual.png` per component, the gallery marks those cards "changed", and the PR comment lists
  them (`screenshot-summary` → `tools/ci-report.js`). `ScreenshotMatrix.optionsFor` puts the compare
  output in a folder per component because Roborazzi writes the diff under the bare file name, and
  every component shares variant names — keep it. **A
  Roborazzi task runs only the screenshot package and `testDebugUnitTest` excludes it**
  (`roborazziRequested` in `app/build.gradle.kts`), so `build-test` stays fast and a rendering
  failure cannot redden it. **Robolectric runs SDK 34, not 36** (`SCREENSHOT_SDK`): 4.16.1
  supports 36 but only on JDK 21, and every job builds on 17. **Roborazzi stays at 1.60.0**, the
  last release built with Kotlin 2.0; later ones are built with Kotlin 2.3, whose metadata this Kotlin 2.1 compiler is
  not guaranteed to read — upgrade the two together. And **a private composable a test needs becomes
  `internal`**, never public (`HandoverHero`, `StatTiles`, `TimelineRow`, `ChatThreadHeader`), and
  a sheet's body is split out of the sheet (`EventPreviewContent`), because a `ModalBottomSheet`
  opens its own window that a node capture does not see. Every fixture date is pinned (May 2026,
  `ScreenshotFixtures`) so an image does not change with the calendar — keep it that way, or the
  suite can never move to verify.
- **A pull request runs only the jobs its diff can affect** (September 2026). The `changes` job
  pipes `git diff --name-only` into `tools/ci-changes.js`, which decides seven outputs and is tested
  by `tools/test/ci-changes.test.js` in `invariants`: docs/functions/rules only → no Android job;
  a screen-only change (`presentation/` outside `common/`, `res/`, `app/src/test/`) → no e2e
  (`common/` stays in because the e2e parents construct `ParentsSource`); nothing the screenshots
  render → no screenshots; and the **emulator matrix is API 30 alone** unless the diff reaches
  what API 26 and 16 KB exist for — `data/local/` (SQLCipher, Room), the manifest, `androidTest/`,
  `src/debug/`, the emulator script — or the build; and the `upgrade` job runs when the diff reaches
  what stored data depends on (`data/local/`, `data/security/`, `di/DatabaseModule.kt`, the
  telemetry answer's form, `app/schemas/`, the manifest, its own tests and scripts, the
  `wire/current/` fixtures), the build, or
  any path the script does not know; and the `web` job runs on anything but docs and the Android
  app and its build — `web/`, `web-tests/`, `functions/`, `firebase.json`, `firestore-tests/`' lock
  file, the workflow, or an unfamiliar path. A push to `main` always runs everything, which
  is the backstop for the legs a PR skipped; lint's NewApi check is the per-PR guard for a
  newer-API call. Three things not to get wrong. Every skip list is deliberately narrow — a path
  wrongly *on* one silently stops testing real changes, which is far worse than a path wrongly off
  it costing a few free runner minutes — so an unfamiliar path runs everything. The build files and
  `.github/workflows/**` run everything, because editing the workflow is exactly when you want the
  build it describes to run. And a gated-out job reports as *skipped*, which branch protection
  counts as passing; nothing is a required check today, so this is safe, but marking one required
  later means a PR merges on a skip rather than a build.
- **An emulator leg is decided by the tests' exit status, not by the emulator step** (September
  2026). The emulator repeatedly never exited after the action's `adb emu kill` with every test
  passed — on API 26 first, and on PR #102 on API 30 too, 9–11 minutes a leg, which made the legs
  the whole run's critical path. `tools/with-screen-recording.sh` writes the tests' status to
  `$STATUS_FILE`, then (with `STOP_EMULATOR=true`) stops the emulator itself through
  `tools/stop-emulator.sh` — synchronously: `adb emu kill`, wait, SIGKILL the qemu process, wait —
  where the earlier attempt was a *detached* `pkill` nothing waited for — and then SIGKILLs the
  emulator's `crashpad_handler`, which outlives it and holds the output pipe the action reads (on
  PR #103 the e2e job's emulator stopped at 09:27 and the action waited on that handler until the
  job's 50-minute limit). The `e2e` job has the same status file and backstop since. The step keeps
  `continue-on-error` and a 10-minute limit as the backstop, and "The instrumented tests finished
  and passed" fails the job unless the status file exists and says 0. When the suite outgrows the
  limit that step says "did not finish" — raise the limit. **The APKs compile while the emulator
  boots** (`tools/ci-background-build.sh start` before the AVD restores, `wait` as the script's
  first line; a compile error still fails the leg through `$STATUS_FILE`), in `instrumented` and
  in `e2e`, which also restores the API 30 AVD snapshot (restore-only, so the two jobs never race
  to save it) and, like `rules`, caches the Firebase emulator jars.
- **Gradle's configuration cache is on** (`gradle.properties`). CI keeps it between runs only when
  the `GRADLE_ENCRYPTION_KEY` repository secret is set (`setup-gradle`'s `cache-encryption-key`);
  without it the cache still helps within a job. An incompatible plugin or script fails the build
  with a report — fix it rather than turning the cache off.
- **No Gradle invocation in CI passes `--no-daemon`** — `gradle/actions/setup-gradle` manages
  the daemon itself and asks you not to, and without one every invocation re-pays JVM and
  Kotlin-compiler startup. `org.gradle.caching=true` and a 4 GB heap live in
  `gradle.properties` and apply locally too; the build cache is local-only (there is no
  remote cache), so in CI it pays off on a re-run of the same branch, where `setup-gradle`'s
  per-job cache of `~/.gradle/caches` carries the previous run's task outputs forward.
- **A CI result is meant to be read without opening a log** (September 2026). Three layers:
  - **Check runs.** Each test job publishes its JUnit XML through
    `mikepenz/action-junit-report@v6` as its own check run — "Unit tests (JVM)", "Instrumented
    tests (API n)", "Cloud Functions tests", "Firestore and Storage rules tests", "Web — verify
    page and ICS" (Playwright's own JUnit reporter) — with failures
    as annotations. Mocha writes JUnit through `tools/mocha-ci-reporter.js` (spec output *and*
    xunit, no dependency), enabled only in CI: `functions` passes `--reporter`, `firestore-tests`
    has a `test:ci` script. Plain `npm test` is unchanged. Those jobs carry
    `permissions: {contents: read, checks: write}`; naming one permission drops the rest to none,
    which is why `contents: read` is restated.
  - **The sticky PR comment.** The `report` job (`needs:` every other job, `if: always()`) runs
    `tools/ci-report.js`, which reads the `junit-*` and `coverage-report` artifacts and the run's
    jobs and artifacts through the API, and posts **one comment per PR, headed "CI summary",
    edited on every run** (`marocchino/sticky-pull-request-comment@v2`, header `ci-summary`):
    job → result, test counts per suite, failed tests with the first line of their message,
    Kover line coverage, artifact links, and the manual plan below. The same facts sit in an
    HTML comment as JSON (`<!-- ci-report-json … -->`) for an assistant reading the PR through
    the API. On a push to `main` it goes to the run's job summary only. It lists jobs from the
    API, so a new job appears without editing it — but **add a new job to `report`'s `needs`**,
    or its row can read "in progress". It needs `actions: read` and `pull-requests: write`, and
    never fails the run over the report.
  - **Manual checks this PR needs.** `tools/manual-test-plan.js` maps the PR's changed paths to
    sections of `docs/DEVICE-CHECKLIST.md` (the `RULES` table; unmapped app sources are listed,
    not dropped) and the comment includes it. `node --test tools/test/*.test.js` runs in
    `invariants` and fails when a rule names a section the checklist no longer has — renumbering
    the checklist means updating `RULES` in the same commit.

  **What testers download** (each linked from the comment, 14-day retention, GitHub login
  needed): `coplanly-debug-apk` from `build-test` — **a UI-only build**: CI has no
  `google-services.json` (and must not get one, see above), so sign-in and sync do not work in
  it; for full testing build locally with the file. `emulator-video-api<n>-<target>` from the
  `instrumented` legs that record — `tools/with-screen-recording.sh` records in 170 s segments around
  `connectedDebugAndroidTest`, keeps the tests' exit status, and cannot fail the job. **Only API 26
  records** (`record` in `tools/ci-changes.js`): on the API 30 image screenrecord's software
  encoder aborted `media.codec` and surfaceflinger followed, which Gradle reports as "System has
  crashed" mid-suite — the recorder had become the failure it was meant to explain.
  `coverage-report` — Kover (`org.jetbrains.kotlinx.kover` 0.9.9, `app/build.gradle.kts`, Hilt/Room/
  Compose-generated classes filtered out). Coverage is **visibility, not a gate**: there is no
  verification rule, and its step is the one place in `ci.yml` where `continue-on-error` is
  right, because failing to *report* a number must not turn a green build red. `screenshots` (the gallery:
  unzip, open `index.html`) and, on a failed verify, `screenshot-diffs`; the comment also carries a
  Screenshots section saying whether the run verified or only recorded, and which images no longer
  match.

## Hard project rules

- **Jetpack Compose only** — never add XML layouts.
- **Stateless composables** — state lives in ViewModels (`StateFlow`), UI receives values
  and callbacks. Follow the existing `UiState` sealed-class pattern.
- **Hilt** for all DI. New modules go to `app/src/main/java/com/coparently/app/di/`.
- **minSdk = 26** — beware of newer `java.time` additions
  (e.g. `LocalDate.ofInstant` is API 34+; use `Instant.atZone(...).toLocalDate()`).
- **KDoc** on public classes/functions; code and comments in **English**.
- Material 3 components; theme tokens from `presentation/theme/`
  (`CoPlanlyColors`, `Typography`, `CoPlanlyShapes`, `dimensions()`).
- **Parent colours identify a person, not a role.** The app never shows the words "Mom" or
  "Dad": every parent label goes through `presentation/common/ParentLabels.kt` and renders
  that person's name. `"mom"`/`"dad"` survive as the two *slot identifiers* in Room, in the
  Firestore document schema and in `firestore.rules`, and are never renamed — `Event.parentOwner`
  is part of the schema `EventRepositoryImpl.toFirestoreMap()` defines, and a co-parent on an
  older build must keep reading it. Pairing assigns the slots (`functions/index.js`,
  `assignSlots`), nobody chooses one; the *colour* is each person's own choice
  (`theme/ParentPalette.kt`), defaulting to pink for slot 1 and blue for slot 2.
- **A calendar friend sits beside the two slots and never occupies one** (item 16, Aug 2026).
  A guardian/friend/grandparent with their own account reads the family's calendar through a
  **central** grant, `calendar_friends/{friendUid}` — never by being fanned out into every
  event's `sharedWith`, so admitting or revoking one is a single write and no event document is
  rewritten. The `events` read rule consults it in a **last** disjunct (a parent's own read
  short-circuits before the `get()`), with expiry compared against `request.time`.
  **The grant names one family, not one person** (M-6, Aug 2026): it carries the `familyId` it
  was issued for, and `isCalendarFriendOf` requires the event's own `familyId` to match *and* its
  creator to be one of that family's two parents. Keying on the creator alone is what leaked —
  a grandmother admitted by Alice-and-Bob matched every event **Alice** created, including the
  ones in Alice's family with Carol. Two consequences for anything built on top: a friend's list
  query must filter `whereEqualTo("familyId", …)` — the old
  `whereIn("createdByFirebaseUid", [a, b])` shape is now rejected outright, pinned by a test that
  says so — and a grant or an event with no `familyId` admits nothing until
  `backfillRecordFamilyIds` has run. Do **not** soften that with a fallback to `familyParents`
  alone: it restores exactly the check M-6 removed, which is how the same leak survived once
  already in `expenses`.
  `acceptCalendarFriendInvitation` is a **third** callable beside pairing and guest and
  `acceptPairingInvitation` refuses its `kind` outright — redeeming a friend code there would
  run `assignSlots` and hand a friend a permanent parent slot. `Event.friendParticipates`
  records who takes part and is **not** an owner: `parentOwner` stays a slot, because whose day
  an event falls on is a fact about custody. The friend's colour is `CoPlanlyColors.FriendTeal`
  — never a parent hue, and never the theme's neutral `secondary`, which is for controls.
  **Faces come from the Google account, never from an upload.** A friend's `photoUrl` is seeded
  from Firebase Auth at their first profile save and copied into `calendar_friends/{uid}` by the
  callable, so the parents' list names *and* pictures them without a second read of a document
  that is not theirs; the parents' own faces come from `users/{uid}.profilePhotoUrl` through
  `NamedParent.photoUrl` and `ParentNames.photoForUid(uid)` — keyed on the uid, because a pair
  still sharing one slot would otherwise return the same face twice. `AccountAvatar`'s
  initial-letter fallback is load-bearing, not decorative: an email/password account has no
  picture. Nothing here is ever overwritten by a later re-derivation — a friend who set their own
  picture keeps it. Not built: a photo **upload** (the field and rules admit one; the Storage
  wiring does not exist, and a button that did nothing is the promise item 8 above forbids).
  **Lapsed grants are swept** (September 2026): `sweepLapsedCalendarFriends` deletes a grant daily
  at 05:00 UTC once `expiresAtMillis` has passed — cleanup, not enforcement, since the rule already
  refuses an expired read at `request.time`. It is a range query, so a grant with no positive
  numeric expiry is never deleted; the callable never writes one and the rule admits nothing
  through it, so do not "fix" the sweep into treating a missing expiry as expired or as permanent.
- **Only the signed-in user has a Room `users` row.** Nothing writes one for the co-parent, so
  `userRepository.getAllUsers()` can never answer "who is the other parent" — it returns one
  row, and on a device where two accounts have signed in over time it returns rows for accounts
  that are not paired with anyone. The co-parent's name *and slot* come from their own
  `users/{uid}` document via `PartnerSummary`, and `presentation/common/ParentsSource.kt` is the
  single place that joins the two halves. A ViewModel that needs the two parents exposes
  `parents: StateFlow<Parents>` from there; a composable resolves the fallback strings with
  `rememberParentNames` and passes one `ParentNames` down its tree.
- **How many children or pets a family has is derived, never stored** (FAM-1, Aug 2026). Nothing
  asks "one child or several", and no flag records the answer: the onboarding wizard's child and
  pet steps are repeatable lists that collect *names*, and everything downstream reads
  `children.size`. A stored count is a fact that goes stale the day a second child arrives or a
  pet dies, and would then need a settings toggle to correct; a derived one cannot disagree with
  the records. It is the same reasoning `FamilyKind` documents for reading an unanswered account
  as "show everything". The visible consequence, and the rule for every screen that grows a
  per-child affordance: **it appears at two, not at one.** A family with one child must see the
  screen they saw before — a picker for a set of one is design item 8 in miniature. The wizard
  was the last place in the app insisting on exactly one of anything (`ChildInfoScreen`,
  `PetsScreen` and `ContactDirectory` were already plural). The calendar caught up in FAM-3:
  `Event.forMembers` names the children and pets an event is about, and the grid has a filter
  strip that appears at two. What it still does not do is mark an *individual chip* — see
  **FAM-5** before adding one, because the two obvious channels are both spoken for.
- **Who a record is about goes through `domain/family/FamilyMemberRef`** (FAM-2, Aug 2026) — one
  file defining the stored vocabulary, like `Tombstone.kt` and `PushPayload.kt`. Children *and*
  pets, because a vet's bill is an expense and the `Expense.childId` it replaced had nowhere to
  put it. `Expense.forMembers` and `Budget.forMembers` are lists of it; the wire form is a JSON
  array of the prefixed strings (`"child:abc"`, `"pet:xyz"`), never a Gson serialisation of the
  type — R8 rewrote a Gson model's field names once already and it shipped. Three things not to
  invert. **Naming nobody is not naming everybody:** an untagged record shows in the unfiltered
  list and under no chip, or every chip shows the same untagged pile and the filter says nothing;
  a budget naming members is charged only what names them back. **An unrecognised reference
  survives a round trip** as `FamilyMemberRef.Unknown`, so an older build cannot erase a tag a
  newer one wrote — dropping it on read is data loss, not a missing feature. And **a member is a
  name, never a colour**: pink and blue are the parent slots, teal is a calendar friend, neutral
  grey is the weekend, and a fifth colour channel breaks what `DayCellFills.kt` protects. The
  `childId` columns survive on `ExpenseEntity`/`BudgetEntity`, dead and documented: dropping a
  SQLite column needs a table rebuild, and while `MIGRATION_12_13` is one — proved row by row by
  `CoPlanlyDatabaseMigrationTest`, *because* `app/schemas/12.json` exists for
  `MigrationTestHelper` to build from — `app/schemas/` stops at v14 (**CQ-1**), so no such test
  can be written for a v26 database.
  `Event.forMembers` joined them in FAM-3 (schema 28) and adds one rule of its own: **it is not
  `parentOwner`.** That stays a custody slot — whose *day* an event falls on does not change
  because it is one child's dentist appointment and not the other's — and the two must never be
  collapsed. On the wire, `data/sync/EventDocument.kt` is the one place the events format is
  defined in both directions, so `SyncService`'s two event maps convert through it rather than
  repeating the conversion (item 5 above).
- **Telemetry has exactly one switch, and a provider may only ever close it** (REL-5, Aug 2026).
  `data/telemetry/TelemetryConsentApplier` is the sole caller of `setAnalyticsCollectionEnabled`
  and `setCrashlyticsCollectionEnabled`; `FirebaseModule`'s providers pass `false` and nothing
  else touches either. That is not tidiness — before it, `FirebaseModule` applied
  `BuildConfig.ENABLE_CRASHLYTICS` and `CoPlanlyApplication.onCreate` then called
  `setCrashlyticsCollectionEnabled(true)` unconditionally a moment later, so the debug flag was
  overruled on every launch and nothing failed. **A gate any other line may overrule is not a
  gate.** Three more things not to undo. Collection needs the build flag **and**
  `TelemetryConsent.GRANTED` — `telemetryCollectionEnabled` ANDs them, and neither may stand in
  for the other; a granted consent must not switch a debug build back into the production
  project. The manifest's `firebase_analytics_collection_enabled=false` and
  `firebase_crashlytics_collection_enabled=false` are what stop collection *before* any app code
  runs, and deleting either reopens that window silently — while
  `firebase_analytics_collection_deactivated` is a **different** knob that must stay `false`,
  because `true` disables Analytics permanently and no runtime call can undo it. And
  `UNANSWERED` is a third state on purpose: "said no" and "was never asked" collect the same
  nothing, but only one of them still owes the user a question.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).

## Architecture map

```
domain/    — models, repository interfaces, use cases, holidays, ReminderScheduler
data/      — Room (v43 + migrations), Firestore/Google clients, repository impls, sync
presentation/ — Compose screens per feature + ViewModels + theme
di/        — Hilt modules (Database, Firebase, Google, UseCase, Notification, …)
```

Data flow: UI → ViewModel → UseCase → Repository → Room (source of truth) → Firestore sync.

### Things that are easy to get wrong

1. **Room schema changes** require: entity change → version bump in `CoPlanlyDatabase` →
   migration in `DatabaseMigrations` (it is auto-registered via `ALL_MIGRATIONS`).
   Exported schemas live in `app/schemas/`.
2. **Event editing must preserve fields.** `AddEditEventScreen` keeps a snapshot of the
   loaded event and uses `copy()`. Never rebuild an `Event` from scratch on save —
   that wipes `sharedWith`/`permissions`/`createdByFirebaseUid` (this was a real bug).
3. **Private events (`isPrivate`)** must never be written to Firestore. Both
   `EventRepositoryImpl` and `SyncService` filter them — keep any new sync path consistent.
4. **Recurring events** are stored once and expanded to occurrences at query time via
   `RecurrenceExpander` (wired in `EventRepositoryImpl.getEventsByDateRange`).
   Occurrences share the master event id — don't use the id as a unique list key.
5. **The Firestore document schema for events** is defined in one place, `data/sync/EventDocument.kt`:
   `fromEvent` (what `EventRepositoryImpl.toFirestoreMap()` delegates to), `uploadDocument`
   (`SyncService`'s `set()` of a queued row — which dropped `reminderMinutes` until the wire contracts
   below caught it) and `toEntity`, the reader. `SyncService`'s conflict-branch `update()` map must stay
   in sync with them.
   **A wire-format change arrives with its fixture diff** (September 2026) — how "a co-parent on the
   previous build" is checked without a second phone. `app/src/test/resources/wire/` is the contract
   between builds: `wire/<collection>/` holds documents *other* builds write, by hand — older shapes
   with keys missing, newer ones with unknown keys, `FamilyMemberRef`s and codec versions (`C2;…`,
   `L2;…`, `p2|…`) — and `wire/current/<collection>/` holds what *this* build writes, generated.
   `WireContractTest` (`app/src/test/java/com/coparently/app/wire/`) runs every fixture through the
   production reader and writer of its collection (one `WireContract` each: `events`,
   `custody_models`, `messages`, `child_info`, `pets`, `expenses`, `budgets`, `event_versions`) and
   checks that it reads what `reads` says (or is skipped, where `skipped` says why), and that a
   read-then-write loses exactly the paths `notPreserved` declares, each with its reason — so an
   unreadable codec entry, an unknown member or a proposal's citation that stops surviving turns it
   red, and so does a loss that was fixed but is still declared. `CurrentWireFixturesTest` fails when a
   writer's output no longer matches `wire/current/`: regenerate with
   `UPDATE_WIRE_FIXTURES=1 ./gradlew testDebugUnitTest --tests '*CurrentWireFixturesTest*' --rerun`
   and review the diff like a screenshot baseline. **The other direction runs in the `upgrade` job**:
   it copies this branch's `wire/current/` into the base checkout and runs the *base's own*
   `WireContractTest` over it, so the previous build's code reads what this one writes and writes it
   back; a key an older build may lose that way must be declared in `CurrentWrite.olderBuildsMayDrop`.
   Three things to know. **A new top-level key is not safe from an older build**: these collections
   are rewritten with `set()`, so anything a newer build must not lose goes in a list older builds
   carry verbatim (items 24, 30, 33), not in a new key — the fixtures record what drops today (an
   unknown top-level key everywhere, an unknown `medicalProfile` field, a pet species read as
   `OTHER`, a swap in an unknown state), and a newer expense category makes the whole expense
   invisible to an older build. **A new synced collection or full-document writer gets a contract and
   fixtures** — `CurrentWireFixturesTest` also fails on a contract without hand-written fixtures. And
   **it is not the device check**: real sync timing, the older build's screens and the conflict rule
   stay in `docs/DEVICE-CHECKLIST.md` §3.5, §3.11 and §5.3.
6. **Calendar query ranges** come from `queryRangeFor()` in `CalendarScreen.kt` —
   extend that function instead of inlining new range math.
7. **View modes** are `MONTH, WEEK, DAY` (roadmap order). There is no 3-day view anymore.
8. **Holidays come from the parent's country** (MON-13). `domain/holidays/HolidayCountry` maps a
   stored `users.countryCode` (schema 33, `NOT NULL DEFAULT 'CZ'`, so every pre-existing account
   is Czechia) to a `HolidayProvider`; the calendar reads that, never a provider directly. Three
   rules. **A country with no table draws no holidays**, and the picker says what each country
   draws (`HolidayCountry.coverage` → `coverageNote()`), because drawing Czech holidays for a
   German family is the bug this replaced and drawing nothing — or less than the row implies —
   silently would be design item 8's forbidden affordance. Czechia, Slovakia, Germany (the nine
   nationwide days, plus the chosen Land's own — below), Austria and Russia (statutory art. 112
   days, no annual transfer decree) have tables; **Ukraine deliberately has none** — its holidays are not days off under martial
   law, and the row says so. **School vacations are sourced, never invented** (September 2026):
   Czechia's are computed (`CzechHolidays`); Slovakia's and Austria's *nationwide* periods, each
   **Slovak kraj's** spring week (`SlovakRegion.kt`) and each German **Land's** list are dated
   tables (`SchoolVacation.kt`, `SlovakHolidays.kt`, `GermanSchoolVacations.kt`) from
   the OpenHolidays dataset (`github.com/openpotato/openholidaysapi.data`, ODbL 1.0 — the official
   KMK/BMBWF/MŠVVaM sites and the APIs are blocked from cloud sessions, the GitHub data repo is
   not), read at a pinned commit by `tools/generate-school-vacation-fixture.py` and held period by
   period by `SchoolVacationReferenceTest`. From school year 2025/26 to whatever the dataset
   publishes — no extrapolation. What is set per region the app does not model stays out: Austrian
   semester and summer breaks (by Land; the dataset's later ones are all `Provisional`), and
   Germany without a Land and Slovakia without a kraj draw no regional breaks. Russia has none. Day view
   labels a school-vacation day and the month grid underlines it; the grid reads
   `HolidayProvider.schoolVacationDaysInRange`, not the `holidaysInRange` map, because that map
   names a public holiday first and would break the line over Christmas. The OpenHolidays data's
   ODbL 1.0 attribution lives in Settings → App → Data sources and licences
   (`DataSourcesScreen.kt`, held by `DataSourcesTest`); a new dataset gets a row there.
   The tables were written against the Python `holidays` library (September 2026, superseding the
   August decision to wait for verified data — this is that data) and are **pinned to it**:
   `HolidayReferenceTest` compares every date and name, 2020–2035, with a fixture generated by
   `tools/generate-holiday-fixture.py`. Change a table by regenerating the fixture, never by
   editing both sides to agree.
   **The country is a property of the person, not the family**: two separated parents can live in
   two countries. The cost is that the school-vacation strips follow the viewer too, which is
   recorded rather than hidden. And **`Holiday.nameLocal` carries `localLanguage`** — the UI shows
   the local name when the device language matches and English otherwise, which is what
   `MonthView` already did, hardcoded to `"cs"`. `CzechHolidays` itself is unchanged: pure,
   computed, Easter via computus (now shared as `gregorianEasterSunday`), the nationwide MŠMT
   vacations, and the district-dependent spring break still intentionally excluded.
   **A region sits under the country, and only where it changes the grid** (schema 35,
   `users.regionCode`, nullable = nationwide). `HolidayProvider.regions`/`forRegion` and
   `HolidayLocation` carry it; the calendar reads `HolidayLocation.provider`, and
   `HolidayCountry.regionOrNull` drops a code that is not the country's, so a parent who moved
   from Germany to Austria never keeps drawing Bavaria. **A region code means nothing
   without its country** — `NI` is Lower Saxony and Nitra — so the UI names a region by
   `HolidayCountry.regionNameRes(code)`, never by code alone. Two countries have regions: a German
   **Land** adds both its public holidays and its school vacations; a Slovak **kraj**
   (`SlovakRegion`, the dataset's eight codes, named in Slovak and not translated) adds only its
   spring week (jarné prázdniny) — Slovak public holidays are national, which
   `HolidayReferenceTest` checks for every kraj, so Slovakia has no `--regions` fixture. The
   picker's label, summary and note are worded per country (`CountryPicker.kt`'s
   `RegionWording`), and the row appears for any country whose `regions` is non-empty. Austria's Länder add no *public* holiday in
   the reference data (the patron-saint days are bank holidays) and no *final* school dates past
   2025/26, so it gets no picker — a row that changed nothing is item 8 again. The picker's note
   reads `HolidayCountry.coverageIn(region)`, so "school vacations" appears for Germany only
   once a Land is chosen and Slovakia asks for a kraj to add its spring holidays. The German
   states are pinned by a second fixture (`--regions`, only what each state *adds*), the kraje by
   `SK-<kraj>` keys in the school-vacation fixture, and the
   library's `catholic` category and the Augsburg pseudo-state are excluded on purpose —
   `GermanState`'s KDoc says why. The Room schema JSON for v36 (which carries this column) is exported by the Regenerate
   workflow, not by hand.
9. **Reminders** are scheduled through the `ReminderScheduler` domain interface
   (WorkManager impl `EventReminderScheduler`), hooked into the event use cases —
   schedule on create/update, cancel on delete.
10. **Receipt OCR is on-device only** (`ReceiptTextRecognizer`/ML Kit, parsed by
    `ReceiptParser`, wired up in `AddExpenseScreen`/`ExpenseViewModel.scanReceipt`) — no
    receipt text or photo may be sent to a model or any other remote service without an
    explicit product decision. The rule outlived the AI subsystem on purpose: on-device OCR is
    a privacy asset worth keeping, not an accident of what happened to be wired up.
11. **Pairing writes never touch the other parent's user document from the client.**
    Accepting an invitation and unpairing go through the `acceptPairingInvitation` /
    `unpairCoParent` callables (`functions/index.js`) — `firestore.rules` allows a user
    to write only their own `users/{uid}`, and the old client-side path is why the
    permissive `firestore.rules.simple` had to be deployed. The strict rules are live
    as of this change.
12. **A Firestore list query needs a `where` filter matching whatever field the security
    rule keys its `allow read` on.** Firestore validates a *query* by checking whether
    its structure guarantees every possible result satisfies the rule — it does not
    execute the rule per already-fetched document and drop the ones that fail. An
    unfiltered collection query is rejected outright (`PERMISSION_DENIED`) the moment the
    rule references a field the query doesn't constrain, even if, coincidentally, every
    document in the collection would have passed. This is why
    `FirestoreExpenseDataSource.getAllExpenses()` takes a `creatorUids` list and filters
    with `.whereIn("createdByFirebaseUid", creatorUids)` — mirroring
    `FirestoreEventDataSource.observeEventsSharedWith()` — instead of reading the whole
    `expenses` collection. Also keep the rule's field names in sync with what the writer
    actually sets: the expenses rule used to reference a `sharedWith` array that
    `ExpenseRepositoryImpl.addExpense()` never writes (the model shares expenses via the
    `partnerId` pairing relationship, not a per-document list), so the co-parent's own
    expenses were unreadable even by document id until the rule was changed to
    `isPartnerOf(resource.data.createdByFirebaseUid)`. A `whereIn`/`whereEqualTo` +
    `orderBy` combination on different fields also needs a composite index
    (`firestore.indexes.json`) — Firestore's error message links directly to the fix.
13. **The conversation id is derived, never generated.** `ConversationKey.of(uidA, uidB)`
    sorts the two UIDs and joins them, so both devices compute the same id without
    coordination and creating the conversation is idempotent. Randomly generated ids are
    what made the two phones settle on separate threads. Read and delivery state live on
    the conversation as `{uid: epochMillis}` maps — one write per event — and the ticks and
    unread badge are derived from them by `ChatReadState`, never stored per message.
    Message times are stored the same way: `Message.sentAtMillis`, epoch millis (Room
    schema v13, since superseded — the database is at v43), not a naive `LocalDateTime`, so two
    parents in different time zones agree
    on what a mark means and on when a message was sent. The Firestore field keeps its name
    (`timestamp`) and the read path still accepts a legacy ISO string, so a co-parent on an
    older build stays readable. Deliberately *not* changed: `Event`, `Expense`, `Budget`
    and `ChildInfo` dates, where a naive local time is often the right model — whether a
    custody handover follows the child's zone or the viewer's is an unmade product
    decision, not an oversight. **`CustodyModelEntity.lastModifiedAtMillis` made the same move
    (SEC-4, schema 29)**, and for a sharper reason than chat's: it is not merely displayed, it
    decides which phone's schedule survives. Its wire form is the one to copy when the same
    question comes up again — see `domain/custody/CustodyTimestamp.kt`, which explains why the
    Firestore field kept both its name *and* its type and only changed the zone it expresses.
    **`EventEntity.updatedAtMillis` copied it (MON-4, schema 39)**: `ConflictResolver` compares the
    instant, `events.updatedAt` carries it as offset-free UTC text (`domain/events/EventTimestamp.kt`
    — no `Z`, because an older build's `ISO_LOCAL_DATE_TIME` parse would throw and skip the event),
    and `EventRepositoryImpl.toEntity` derives it from the `updatedAt` wall clock every save already
    stamps, so no save path can forget it. `Event.updatedAt` stays a `LocalDateTime` for display.
    **`ChildInfoEntity`/`PetEntity.updatedAtMillis` followed (schema 40)**: `resolveChildInfoConflict`
    compares the instant; `ChildInfoRepositoryImpl`, `PetRepositoryImpl` and `SyncService`'s two
    child maps write `updatedAt` through `EventTimestamp` as offset-free UTC text, and `toEntity`
    derives the instant from the wall clock each save stamps. Pets have no conflict comparison;
    their column keeps the two collections on one wire form.
14. **A delete is a tombstone, never a document removal** (CQ-3). `data/sync/Tombstone.kt` is
    the one definition: the client writes `deletedAtMillis` (epoch millis) and `deletedBy` onto
    the document with `update()` — never `set()`, which would replace the `createdByFirebaseUid`
    and `sharedWith` the read rules are keyed on and leave a tombstone the co-parent may not
    read. Room's `deletedAtMillis` on `events`/`expenses` (schema 25) is a **pending-tombstone
    outbox**: hidden from every read query, retried on each sync, and hard-deleted only once the
    remote write lands. Four things not to undo. **Do not reconcile by absence** — "delete what
    is not in the snapshot" takes the whole calendar the first time `sharedWith` narrows at
    unpair, a download window bounds the query (CQ-5), or a snapshot comes back partial.
    **Do not decide a deletion by timestamp**: `updatedAt` names an instant since MON-4, but an
    older build still writes its own wall clock there, so a tombstone beats a concurrent edit by
    rule, deliberately —
    an event that should not exist is visible and can be deleted again, an edit that loses is
    gone. **Do not filter tombstones out of `getUnsyncedEvents`/`getUnsyncedExpenses`**, which
    are the outbox. And **do not shorten the 90-day sweep** (`sweepDeletedDocuments`): it is
    the deadline for a co-parent's phone to come back and collect the deletion, and sweeping
    early reintroduces exactly the bug. `FirestoreEventDataSource.deleteEvent` still removes a
    document outright and has exactly one legitimate caller — an event turned private has to
    leave Firestore with no trace.
15. **A push carries a type, never a sentence** (SEC-3). `data/remote/firebase/PushPayload.kt`
    is the vocabulary; `PushNotifier`, which `CoPlanlyMessagingService` hands every message to,
    writes the text from *its own* string resources and **drops a type it has no wording for**. Never reintroduce a `title`/`body`
    fallback for an unrecognised type — that fallback is the forgery, not a nicety, and
    `firestore.rules` refuses both keys from a client precisely so nothing legitimate needs
    one. Two halves, and both are load-bearing: the rule's **allow-list** of client types keeps
    `pairing_accepted`, `pairing_removed` and `chat_message` producible only by Cloud Functions
    (which write as admin and bypass rules), so a co-parent cannot announce a pairing that did
    not happen. Adding a type means four places agreeing — `PushPayload`, the rule's allow-list,
    `PushNotifier.PUSH_TEXT`, and the five `push_strings.xml` — and a type missing
    from any of them is a push that silently never appears. This is also why service-layer
    string extraction (**CQ-14**) was *not* a prerequisite: the string is read on the receiving
    device, which has a `Context` and all five translations.
    **A push also names its family** (`PushPayload.FAMILY_ID`, M-8) — a *field*, which every type
    may carry and an older build ignores, so the four-place rule does not apply to it.
    `FcmService.queueNotificationForUser` stamps it for every client push (a pair is a family, so
    it is derived there, never by each payload builder), the functions stamp `chat_message` and
    `pairing_accepted`, and `firestore.rules`' `isPushFamily` bounds it and requires the sender
    and the addressee both to be in it. The tap carries it as an intent extra **and** in the
    PendingIntent request code, and `MainActivity.readLaunchIntent` switches the family **before**
    arming any deep link — arming first would let `NavGraph` open the target on the wrong family.
    **What the phone then shows is tested** (`androidTest/.../PushNotificationTest`, every leg
    including 16 KB): every worded type posted and read back from `activeNotifications` in English
    and German, composed in all five, an unknown type and another account's push posting nothing,
    and each tap's PendingIntent matched to its deep link, family extra and request code. It words
    through a configuration context because the service does: on API 32 and below AppCompat's
    per-app language reaches activities only, so a push follows the *device* language there.
16. **`sharedWith` is computed at upload time and never recomputed for a row already marked
    synced.** An event created while the account was unpaired is uploaded with an audience of
    one uid, and nothing revisits it — so it stays unreadable by a co-parent who arrives later.
    Pairing repaired this only for the *accepter*, and only by accident: `EventDao.reslotOwner`
    clears `syncedToFirestore` as part of the slot re-stamp. The inviter keeps their slot
    (`PairingViewModel.withSlotReslot`), `ParentSlotMigrator.reslot` returns 0 on `from == to`,
    and their whole pre-pairing history — Google Calendar imports included — stayed private
    forever. `SyncService.backfillAudienceForPartner` now re-queues this user's own non-private
    events once per co-parent uid, from the sync path rather than from pairing, so it also
    repairs pairs that already exist without them unpairing. Two rules for anything similar:
    key the marker on the **partner uid**, not a boolean, or it never re-arms on re-pairing; and
    exclude private rows **in the statement**, because a row with the flag cleared is a row
    queued for upload. Rows whose `createdByFirebaseUid` is null are deliberately not matched —
    nothing distinguishes this user's un-stamped event from anybody else's.
17. **A save path never reads a `WhileSubscribed` StateFlow's `.value`.** Every ViewModel shares
    `ParentsSource`/`FamilyKindSource` with `SharingStarted.WhileSubscribed`, so in a ViewModel
    instance no screen has collected — which is exactly what a **form-only route** is — `.value`
    is still the initial value and always will be. `ExpenseViewModel.sharedWith` read
    `parents.value` to decide who a shared expense divides between, and the Add Expense screen
    collects `agreedRatio` but not `parents`: every expense was written naming only the payer, so
    the payer's month looked right and the co-parent's showed nothing owed at all. The cheap
    facts have suspend accessors for this — `ParentsSource.signedInSlot()` and
    `ParentsSource.coParentUid()`, both one Room row — and a save path must use those. The
    stream is for what the screen *renders*. Same reason a Settings dialog seeds from
    `FamilyKindSource.observeMine()` and not `observe()`: the union of both parents' answers is
    what the app *shows*, while the dialog *writes* this parent's row alone, so seeding it with
    the union made every checkbox a lie.
18. **`familyId` names a relationship; nothing reads it yet, and that is deliberate.** Every
    shared record — event, expense, budget, child, pet, change request — carries the
    `FamilyKey.of(myUid, partnerUid)` id of the co-parenting relationship it belongs to (Room
    schema 30). It is stamped **at create, never on the sync path**, for the reason
    `createdByFirebaseUid` is: a private event never syncs and an offline device leaves
    `syncedToFirestore` false indefinitely, so a field written only when a record uploads stays
    null on exactly the rows that most need it. It is **never re-derived** — a re-pairing must
    not move a child, an expense or a settled month into a different household — and **null is a
    value**, meaning "mine alone", which is what every record written before its owner paired
    honestly is. `FamilyIdBackfill` turns those nulls into a family once there is one to name,
    locally, once per co-parent; it deliberately does **not** clear `syncedToFirestore`, because
    re-queuing all six tables would put the co-parent's own downloaded rows into this device's
    outbox where the create rule rejects them forever.
    The field is not in `firestore.rules` and no query filters on it. That is the sequencing, not
    an omission: a read path keyed on `familyId` has to pin the field against a non-creator writer
    the way `sharedWith` is pinned, and pinning it while the two phones are still catching up
    denies the app's own writes — a device whose backfill has not run writes `familyId ?: ""` over
    a stamped document. See `docs/DESIGN-multi-family.md` M-2 for why the relaxed version of that
    pin is not a pin at all. The switch is M-4, where `familyId` **replaces** `sharedWith` rather
    than joining it, after a server-side pass has stamped the documents themselves.
19. **The parent slot and `caresFor` belong to a relationship, not to a person** (M-3, Aug 2026).
    Both live on `families/{id}` as maps keyed by uid — `slots` written only by Cloud Functions,
    `caresFor` written by each parent for **their own key only**, which `firestore.rules` enforces
    with two nested `hasOnly` checks. The asymmetry is the point: a slot decides whose events are
    whose, so a parent who could set their own would take the co-parent's colour and re-point what
    `parentOwner` means across the calendar, while `caresFor` only decides which sections are
    drawn — exactly the authority a parent already had over their own profile. Both sides of the
    `caresFor` diff read through `.get('caresFor', {})`, because a family created before the field
    existed carries no such key and a missing key is an evaluation error, not null.
    As with item 18, **the client writes the new location and still reads the old one.**
    `UserRepositoryImpl.updateUser` mirrors `caresFor` onto the family from the one choke point a
    parent's answer changes through — do not add a second call site in Settings or the wizard —
    and `users/{uid}.caresFor` keeps being written until M-5 so a co-parent on an older build
    still sees the change. Nothing reads the family's copy yet: until the switcher exists a person
    has one family, so a family-scoped slot *is* the profile slot, and the read switch would buy
    no behaviour while adding a Firestore listener to `ParentsSource`/`FamilyKindSource` — shared
    flows this project has already had to optimise twice for that. `ParentSlotMigrator` cannot
    take its `familyId` scope yet either: a row whose backfill has not run carries null, so
    scoping the re-stamp on it would silently skip exactly the rows that need it.
20. **The database file is encrypted, and the passphrase is the one piece of state that must never
    be re-minted** (SEC-2, Aug 2026). Room opens through SQLCipher: `EncryptedDatabase` builds the
    open helper and converts an existing plaintext file on the way, `DatabaseKey` holds 256 random
    bits wrapped by `EncryptionManager` under a Keystore key, and `SqlCipherMigration` decides —
    from the files on disk, never from a flag — where a killed process left off. Four things not to
    undo. **The passphrase does not go in `EncryptedPreferences`**, whose recovery deletes an
    unreadable store and starts empty: correct for the OAuth token it was written for, and here it would
    hand out a different key on the next launch and leave the database unopenable. **It is written
    with `commit`, not `apply`** — it has to be on disk before anything is encrypted with it.
    **The plaintext file is deleted only after a verified encrypted copy exists beside it under a
    different name**, which is the whole safety argument; a failure that leaves it intact falls
    back to opening it unencrypted and retries next launch, because crashing makes the app unusable
    and wiping trades data the user has for a property they did not have a moment ago. And
    **field-level encryption is not the smaller version of this**: `child_info` syncs and the key is
    device-bound, so an encrypted field arrives at the co-parent's phone as ciphertext their
    Keystore cannot open — `SensitiveMedicalData` was deleted for saying otherwise. The SQLCipher
    calls **run in CI on emulators** (API 26, 30 and 35 with 16 KB pages): `EncryptedDatabaseTest`
    drives every state `SqlCipherMigration` names through the production builder, and the `upgrade`
    job opens a database the *previous build* wrote (encrypted → encrypted, one release step; see
    the CI section). What neither can prove is an upgrade from an older build, or under a phone's
    hardware-backed Keystore, so the first launch on a device holding real data is still an
    acceptance step (`docs/DEVICE-CHECKLIST.md` §2.1).
    **The preference store follows the same idea since SEC-5** (September 2026):
    `EncryptedPreferences` is no longer `security-crypto`'s alpha `EncryptedSharedPreferences` but
    one file, `no_backup/secure_prefs.bin` — the whole map (`PreferenceBlobCodec`, never Gson)
    sealed with the same Keystore key through `EncryptionManager`, written to a `.part` file and
    renamed, held in memory by `InMemorySharedPreferences`, which persists every commit under its
    lock. The library is kept **read-only**, to copy the old store once on the first launch of
    that build; the old file is deleted only after the new one is written. Don't write through the
    library again, and don't make a second `EncryptedPreferences` over the same file — each keeps
    its own map and they overwrite each other (the e2e phones get their own directories for that
    reason).
21. **A parenting plan is two halves and a derived agreement, and neither half may write the
    other** (MON-5, Aug 2026, schema 34). `parenting_plans/{familyId}` holds `answers`,
    `agreedTo`, `catalogueVersions` and `updatedAt` as maps keyed by uid, and `firestore.rules`
    lets each parent's write touch **their own key only** — the same nested `hasOnly` shape M-3
    uses for `caresFor`, and here it is the feature rather than a safeguard: a parent who could
    edit the other's half could put words in their mouth in a document the two of them may hand
    to a court. Three things not to invert. **An agreement records the wording, not the
    question**: `ParentingPlanEntry.agreedTo` maps a question id to *the co-parent's answer text
    this parent ticked*, so an edit on either side makes the comparison stop matching and the
    agreement lapse on both phones at once — a boolean flag would need a cross-write to clear,
    which the rule refuses, and would go on claiming two people had settled text that no longer
    exists. **An answer under a retired question id is kept, not deleted**, and progress is
    counted over the catalogue rather than over the stored answers, so rewording the plan never
    destroys what a parent wrote. And **the wording is not the Ministry of Justice's form**:
    `ParentingPlanCatalogue` holds ids only, the five `parenting_plan_strings.xml` hold this
    project's own questions over the areas § 858 OZ names, and `parenting_plan_disclaimer` says
    so on screen. Replacing it with the official text is a data edit plus a [VERSION] bump;
    `PlanStringsTest` fails the build if the ids and the wording drift apart. Until that happens
    the disclaimer stays — see ROADMAP MON-5 for why the form could not be fetched.

22. **Onboarding links the co-parent first, and everything after that step is written to open on
    what the link brought back** (September 2026, owner decision). `OnboardingStep.stepsFor` walks
    `CoParent → Intro → Family → Profile → …`; the wizard used to end with the invitation, which
    made the second parent retype everything the first one had entered. Six things hold it up,
    and each closes a defect that was found by trying the flow end to end:
    - **`SyncRequester` asks for a sync on the Paired transition** (`PairingRepositoryImpl.
      onPairingStateObserved`), on both phones. The mirror there says *that* the phone is paired;
      the sync is what widens the inviter's audiences and downloads them on the accepter's side.
      Before it both waited on the fifteen-minute tick. `SyncWorker.syncNow` also remembers a
      request that lands mid-run and appends one more run — `ExistingWorkPolicy.KEEP` used to drop
      it, and during pairing that was the normal case.
    - **The wizard observes Room rather than reading once**, and the drafts take an emission only
      while every one of them is blank (`orStoredChildren`). That guard is the whole difference
      from the `ChildInfoViewModel` defect below: a form with anything typed into it is never
      touched. `ChildDraft.byCoParent` is what lets the step say whose records it opened on.
    - **Only records this account created are evidence that it has been through the wizard**
      (`OnboardingState.isOwnRecord`). A second parent's phone holds the first one's children
      within seconds of pairing, and a Google sign-in arrives with a name, so "named, and there
      is a child" was true of an account that had answered nothing.
    - **The split step writes only a slider that was moved**, and converts this parent's share to
      slot 1's on save from a fresh read of the slot. An untouched Next on a paired account would
      otherwise create the pair's agreement out of "half each" ahead of the ratio the first parent
      chose. `submitRatio`'s first-write branch now announces `SPLIT_RATIO_AGREED` like
      `publishCachedRatioIfMissing` does.
    - **`CustodyModelRepository.publishLocalIfMissing` runs on every sync.** A schedule saved
      before pairing never left the phone: the save pushes only when paired, the mirror only over
      a document that exists, and the accepter reconciles only when the accepter has a pattern.
      It writes only on a read that *proved* the document absent — never on `Unavailable`.
    - **A backfill announces itself once.** The audience backfills' re-uploads are silent per
      record and `SyncService.announceSharedRecords` queues one `RECORDS_SHARED` push at the end
      of the pass; before that every re-uploaded event arrived on the co-parent's phone as
      "created", years-old ones included. The push is also the wake-up the other phone needs.
    **Records uploaded before pairing are re-stamped server-side** (September 2026). They upload
    with `familyId: ""` and the client never re-stamps the remote copy (`FamilyIdBackfill` is
    Room-only by design, item 18), so under the family-keyed rules the co-parent never saw them.
    `stampOwnBlankFamilyIds` (`functions/index.js`) is the remote half of `FamilyIdBackfill`, over
    all six collections, run by the `onFamilyCreated` trigger when a pair forms and by
    `backfillRecordFamilyIds` as the re-runnable backstop. Three things not to loosen. **It stamps
    only when the family is not a guess**: exactly one live co-parent who names the author back
    (`partnersOf`, never the singular `partnerId`, which since M-4 is just the family a phone is
    showing), and no trace of an earlier relationship — an unfinished `pendingRevocationOf`, an
    accepted co-parent invitation with somebody else, or one of the author's records naming
    another family or adult. Anything else is skipped with a reason and its blanks counted as
    `unresolved`; a stamp from an old household is exactly the move item 18 forbids. **It writes
    only `familyId`, only where it is blank** — never a tombstone's deletion fields, never a
    record that already names a family. And **it is not a per-record write trigger**: that would
    bill every write, cannot help a record uploaded while unpaired, and races the budgets
    `familyId` pin. Still open, in `docs/ROADMAP.md`: the `ambiguous`/`priorRelationship` blanks,
    which only a person can assign.
23. **A per-document audience is bound to the writer, server-side** (September 2026 audit).
    `firestore.rules` `isMyAudience` requires every uid in `sharedWith` (guests excepted, on
    `child_info`) to be the caller or one of their live co-parents, and `familyIsMineOrBlank`
    requires a stamped `familyId` to name a family the caller is in — on `events`, `child_info`,
    `pets`, `expenses`, `budgets` and `change_requests`. Any signed-in account could otherwise
    write a document *into* somebody's calendar or ledger, have both phones' sync pull it in, and
    leave them unable to delete it. Three consequences for a writer: a **stale audience is
    refused on create** (the client's own intersecting `shareTargets` already produced the right
    one; the widen-only shape the unpair tests keep as a mirror is now refused by the rules too),
    a non-creator may add nothing but the guests it names, and the creator can never be written
    out of `sharedWith`. On an **update** the bound is on what the write *adds*
    (`onlyFamilyAdded`, `onlyFamilyOrGuestsAdded`), not on the whole list — deliberately: a uid
    already there came through `create`, the server or an older rule, and a parent editing the
    title of a record whose audience still names a swept ex-partner must not be locked out of
    their own record until some other write tidies the list. `myAudience()` reads the caller's
    profile and treats a missing one as a family of one rather than as an error, because an
    erroring rule denies and the first upload can race `ensureProfile`.
    `messages` create additionally requires the pairing behind the thread to be
    live, and `notifyOfChatMessage` re-checks it and takes the sender's name from their profile
    — an unpaired ex could otherwise keep posting under any name and have it pushed. Client
    writes to `users/{uid}` may no longer move `role`, `partnerId` or `partnerIds` (repeating the
    stored value is fine: `diff().affectedKeys()`). Three things not to undo on the client:
    `CoPlanlyMessagingService` drops a push whose `targetUserId` is not the signed-in uid, and
    `FcmService.unregisterToken` runs on sign-out and deletion — a token names a *device*, and a
    device that changed hands used to show the previous account's chat; `EncryptedDatabase.
    fallBackTo` **throws** when an encrypted file is on disk rather than handing it to the
    framework helper, whose `onCorruption` deletes a file it cannot parse; and
    `CredentialManagerService.signOut` clears the Google credential *before* the network revoke,
    because an offline sign-out used to leave the previous account's calendar token for the next
    one.

24. **A contact window sits on top of the whole-day pattern and never splits it** (MON-6b,
    September 2026, owner decision; schema 36). `domain/custody/ContactWindow.kt` is the one
    definition: `{cycle day, from, to, parent slot}`, repeating with the cycle like
    `momDayIndices`. **`getCustodyFor` stays whole-day** — do not teach it about windows: the
    grid's colour, the handover walk, swaps and every older build read it, and whose *day* it is
    does not move for an afternoon. `CustodyModel.contactWindowsOn(date)` is the separate question,
    and `CustodyResolver.contactWindowsResolver` drops a window naming the day's own parent — the
    one filter the grid and Home's today card both read, so don't re-inline it. Four things
    not to undo. **The wire form is `ContactWindowCodec` strings** (`"9|15:00|19:00|dad"`), never
    Gson over the data class, and `encodeAll` is canonical (sorted, de-duplicated). **A missing
    `contactWindows` key is not "none"**: an older build rewrites the whole document without it on
    every save, so the mirror keeps its own copy when the key is absent, and this build always
    writes the key on a pattern write (`[]` for none). **Proposal and swap writes carry the stored
    list verbatim** (`SharedCustody.contactWindowsWire`, `CustodyProposal.contactWindowsWire`):
    `firestore.rules`' `contactWindowsKeptOrDropped` refuses a proposal-only or swap write that
    *changes* the list — a pattern change riding on a write whose banner is suppressed — and allows
    one that *drops* it, which is what an older co-parent's write does. Re-encoding from the model
    there would be refused the day a newer build wrote an entry this one cannot decode. And **the
    MON-6 midweek toggle is not a window**: it is the whole day with the overnight, stays as it
    was, and no saved schedule is converted between the two. On the grid a window is a band in the
    window parent's tint with a full-hue edge (Day/Week) and a full-hue corner triangle (Month),
    both over the `DayCellFills` layers rather than a new fill competing with them.

25. **Every saved revision of a shared event is kept whole, and nobody can change it afterwards**
    (MON-4, September 2026, owner decision; schema 37). `docs/DESIGN-court-record.md` §4 is the
    design. Each create, update and delete of a non-private event through `EventRepositoryImpl`
    queues one row in Room's `event_version_outbox` and `data/versions/EventVersionRecorder`
    uploads it to the top-level `event_versions/{versionId}`: the event document exactly as
    `toFirestoreMap()` built it for that save (plus the tombstone fields on a delete),
    `editorUid`, `deviceTimeMillis`, and `recordedAt`, which the rule pins to `request.time`.
    `data/versions/EventVersionDocument.kt` is the one definition of that wire form. Six things
    not to undo. **`update` and `delete` stay `false` for every client** — that one line is the
    guarantee the export sells; the only path that removes a revision is account deletion, as
    admin, and only the departing parent's own. **`recordedAt` is written with
    `FieldValue.serverTimestamp()`, never a client value** — the rule refuses anything else, and
    the export labels the two clocks separately because they answer different questions (when the
    parent acted; when the server saw it). **A revision is queued in Room before the event's own
    upload, and deleted only once the server has it** — the event write paths discard their
    `Result`, so a revision riding on them would be lost exactly when the phone was offline; a
    `PERMISSION_DENIED` on a retry is checked with `exists()` against the server, because a second
    `set()` of a landed id is an update the rule refuses. **`event_versions` is not in
    `TOMBSTONED_COLLECTIONS`, and not in `SHARED_AUDIENCE_COLLECTIONS`** — a revision survives its
    event's 90-day sweep (a deletion is the edit a dispute is about), and unpair does not narrow it
    (the ex-partner keeps what they could see, as with the chat). **Private events produce no
    revision** (item 3), checked by the callers *and* by `EventVersionRecorder.record`. And
    **there is no stored revision number**: two phones offline would mint the same one and the
    create-only rule would refuse the second for ever; order comes from the two clocks and the
    export numbers revisions when it renders. Calendar friends cannot read revisions — the
    history is the parents' communication record, not the calendar. Done since (schema 39): the
    compared timestamp is `EventEntity.updatedAtMillis` (item 13), so a revision's embedded
    `updatedAt` is UTC text from an upgraded build and a wall clock from an older one — the export
    keeps labelling `deviceTimeMillis` and `recordedAt` as the clocks. **An older build's edits are
    recorded by the server** (September 2026, design §11): `recordServerEventRevision`
    (`functions/event-revisions.js`) writes `event_versions/srv_<eventId>_<commit time>` with
    `recordedBy: 'server'`, `deviceTimeMillis: null` and the editor the saved document names, only
    when no phone's revision matches the write key (`saved|<updatedAt>` / `deleted|<deletedAtMillis>`,
    defined in `EventVersionDocument.writeKey` and the function alike — change both). It skips a
    write that leaves the key unchanged (sweeps, backfills, re-uploads), a removed document and a
    private one. The export drops a server revision when a phone's revision of the same save exists
    and labels the rest `export_action_server_recorded`. Clients may neither write `recordedBy` nor
    create a `srv_` id. Do not make the events rule require a revision instead — it would refuse
    every edit from an older build. A server revision proves that the document changed, not who
    changed it: `lastModifiedBy` is not pinned.

26. **The export is a communication record, says so on its face, and is made on the phone**
    (MON-3, September 2026; the owner's MON-4 answer). Settings → Family → *Export the record*
    (`presentation/export`) picks a period and writes a CSV or an A4 PDF to `cache/exports/`,
    shared through the existing `FileProvider` (`file_paths.xml` → `exports/`). What goes in is
    decided in pure Kotlin — `domain/export/CommunicationRecordBuilder` builds the model,
    `CommunicationRecordCsv` and `RecordLayout` lay it out, `data/export/ExportFileWriter` only
    draws with `android.graphics.pdf.PdfDocument` — so the JVM tests reach every rule. Seven things
    not to undo. **The `export_statement_*` paragraphs are printed first in both formats** —
    "a record of what the parents recorded and wrote … not of what happened" is the owner's
    answer, not copy; don't shorten it, and a new format prints it too. **Revisions show both
    clocks under their own labels**, a revision still in the outbox says "not yet received by the
    server" rather than borrowing its device time, and an event saved before revisions existed is
    printed as its *current state*, never dressed up as a "created" revision dated today. **Read
    from the server with `Source.SERVER`, and say so when it failed** — `RecordSources.serverReached`
    false prints `export_record_incomplete` on the face; a record assembled silently from the cache
    is a record of one phone. **Never a private event, never a child's or pet's record** — the
    source reads events, messages and expenses only, and the builder drops `isPrivate` again
    whatever it is handed (item 3; design §4 on the medical profile). **Names, never roles** —
    every uid and slot goes through `parentLabelByUid`/`parentLabel` (the hard rule above).
    **CSV is RFC 4180 plus a formula guard** — CRLF, quoted fields with doubled quotes, one width
    for every record (the preamble is padded), and a cell starting `= + - @ \t \r` gets a leading
    apostrophe *inside* the quotes: half the file is the other parent's words. And **it is
    ungated**: MON-1 has not set a price, so there is no entitlement check and none is to be faked
    with a flag — the gate is MON-11's. Times use `RecordFormat` (fixed `Locale.ROOT` patterns
    with the offset printed); event start/end are the naive wall-clock values the schema stores,
    and the statement says so.
    **A registered export carries a record ID the server holds its SHA-256 under** (MON-16,
    `docs/DESIGN-court-record.md` §10; `functions/export-receipts.js`, `web/verify/`). Six more
    things not to undo. **The ID is reserved before the file is rendered, and the hash is of the
    exact bytes saved** — `ExportViewModel` reserves, renders with the ID, hashes, registers, then
    saves those same bytes; `ExportFileWriter.render`/`save` are split for that and nothing may
    touch the bytes between. **No file names an ID the server holds no hash for**: a phone that
    cannot reserve renders `export_verify_not_registered` on the face (and in every PDF footer),
    and one whose registration fails after a reservation renders the file *again* without the ID —
    never "registered" by default (`CommunicationRecord.verification` defaults to `Unregistered`),
    and the screen says so before the share sheet opens. **`verifyExport` is unauthenticated and
    answers with the receipt alone** — registered at, period, format, size and the fixed words "one
    of the family's parents"; never a name, a uid, a `familyId` or whether the account still exists.
    **`export_receipts` is closed to every client** (`allow read, write: if false`); only the
    callables touch it, as admin. **`recordedAt` is the function's clock** and a receipt is
    create-once: a second hash under a registered ID is refused, the same hash returns the original
    time. And **account deletion scrubs a registered receipt, never deletes it** — `generatorUid`
    and `familyId` blanked, the hash kept — because erasing one parent must not un-verify evidence
    the other has filed; reservations that never received a hash are deleted. The verification
    address is `BuildConfig.EXPORT_VERIFY_URL`, blank until `web/verify/` is hosted, and blank omits
    the line rather than printing a dead link. The chat immutability pin §4 called missing lives in
    `firestore-tests/rules/event-versions.test.js`'s last block: both parents, every field,
    `set()`, delete and a stranger, with `isRead` as the control.
    **The parenting plan is an optional last section** (MON-5 in the record, September 2026; a
    checkbox, on by default, `domain/export/RecordPlan.kt`). It prints `parenting_plan_disclaimer`
    and says it is the plan as it stood at export time, not for the period; lists every catalogue
    question, each parent's answer **by name**, and agreement exactly as
    `ParentingPlanComparison.statusOf` derives it — never a stored flag; keeps answers under retired
    ids under a "no longer asked" heading rather than dropping them (item 21); reads with
    `Source.SERVER` and, when that fails, prints this phone's copy *labelled as such* and marks the
    record incomplete; and says "no parenting plan recorded" rather than inventing one.

27. **A calendar-feed token is the whole authorisation, so it is hashed, scoped and never served
    past what the app itself would show** (MON-17, September 2026). `functions/calendar-feed.js`
    holds the pure half (token, RFC 5545 text, the custody port); `functions/index.js` the
    `createCalendarFeed`/`listCalendarFeeds`/`revokeCalendarFeed` callables and the `calendarFeed`
    HTTPS function; `presentation/settings/CalendarFeedScreen.kt` the one screen. Five things not
    to undo. **Only `sha256(token)` is stored** — it is the `calendar_feeds` document id — and the
    token leaves the server once, in `createCalendarFeed`'s URL; the app lists and revokes by a
    separate random `feedId` and keeps nothing on the device. Never log a feed path or a token.
    **`calendar_feeds` is closed to every client** (`allow read, write: if false`, pinned by
    `firestore-tests/rules/calendar-feeds.test.js`): a readable collection says which families have
    a link, a writable one mints a feed into somebody else's family. **A private event (item 3) and
    a tombstoned one (item 14) are never served**, nor one whose `familyId` is not the feed's or
    whose creator is not one of that family's two parents — the M-6 rule, and do not soften it with
    a fallback for unstamped events. **Parents are named, never slotted**: titles come from
    `users/{uid}.name`, the slot from `families/{id}.slots`, and a pair still sharing one slot gets
    no custody layer rather than a guess. And **the custody port must agree with
    `CustodyResolver`/`ContactWindowCodec`/`SeasonalLayerCodec`** — accepted swaps first, then the
    deciding seasonal layer (item 30), then the whole-day pattern, windows dropped when they name
    the day's own parent; change the Kotlin, add a fixture to
    `functions/test/calendar-feed.test.js`. A link is served only while its family is live (both
    profiles name each other); an unpair, an account deletion or 90 idle days end it with the same
    404 as an unknown token.

28. **Chat search reads this phone's Room copy of the open thread and nothing else; the pause
    before sending never blocks and never keeps what it looked at** (MON-15, MON-19, September
    2026). `ChatSearchRepository` has no Firestore branch on purpose — a server-side search would
    need an index that shows message text to a service — and it is bounded by the conversation on
    screen, which follows `ChatPartnerSource` (M-8). `MessageDao.searchCandidates` is only a
    `LIKE … ESCAPE '\'` prefilter: SQLite folds ASCII case and nothing else, so the decision is
    `domain/chat/ChatSearch` over `TextFold` (case and diacritics ignored, "cas" finds "čas"). Don't
    "simplify" it into a bare `LIKE` — four of the five languages break — and don't put an FTS4 table
    in front of it: `unicode61` matches token prefixes and does not fold й/ё/ї, so it would drop
    messages the search accepts (measured, ROADMAP MON-15; `ChatSearchTest` pins both). If a thread
    is ever measured slow, the prefilter is a `TextFold`ed column under the same `LIKE`. The hold (`SendHold`) keeps a
    message out of Room and the outbox until the pause ends, which is what makes Undo real, and hands
    a held message back to the draft store if the ViewModel is cleared rather than sending it. The
    hint (`ToneCheck`) is three string tests computed while rendering: it never disables Send, is
    never stored, logged or sent, and never calls itself "AI" — a tone model is MON-12.

29. **A professional reads one family, with both parents' consent, until a date, and never the
    chat** (MON-18, September 2026). A mediator, lawyer, guardian ad litem or therapist holds
    `professional_grants/{familyId}__{proUid}`, written only by `acceptProfessionalInvitation` —
    a **fourth** callable beside pairing, guest and calendar friend. `acceptPairingInvitation`
    refuses `kind: 'professional'` by name (redeeming it there would make a mediator a parent of
    the family they observe), and the guest and friend callables refuse it as not theirs; test all
    three whenever a kind is added. Five things not to invert. **Two consents to open, one to
    close**: `consents` is a `{parentUid: epochMillis}` map, the callable writes only the inviting
    parent's key, the rules let each parent add **only their own** (the nested `hasOnly` of item
    21), and `isProfessionalOf` admits nothing until the map `hasAll(familyParents)`; either parent
    **deletes** the grant alone, and there is deliberately no "withdraw my consent" edit — a parent
    who no longer consents revokes. **Always expiring**: the invitation rule refuses an end more
    than 180 days out, the callable clamps to 180 days from redemption and refuses a missing end,
    the rule compares `expiresAtMillis` against `request.time`, and `sweepLapsedProfessionalGrants`
    only tidies the row afterwards (it shares `sweepLapsedByExpiry` with the friend sweep, so a
    grant with no numeric expiry is never matched). **One family**: the grant id is built from the
    record's `familyId` and the caller's uid, the stored `familyId`/`proUid` must repeat it, and
    an event additionally needs its creator in `familyParents` — the same two checks as
    `isCalendarFriendOf`, for the same reason (M-6). Unpair deletes the family's grants; account
    deletion deletes both directions. **Read-only, and three collections only**: `events` (last
    disjunct), `parenting_plans/{familyId}` and `custody_models/{familyId}` (`get`, including a
    document not yet written). Never `conversations`/`messages`, `expenses`, `budgets`,
    `child_info`, `pets`, `family_settings`, `families` or `users` — `professional-access.test.js`
    pins each; don't widen it for an export, attach the export instead (MON-3/MON-16). **No Room
    table**: grants and the professional's reads are Firestore listeners (`ProfessionalRepository`),
    so a professional's phone never stores somebody else's family and the schema did not move. The
    professional's calendar is a list that **names** whose day it is rather than colouring it —
    this phone cannot know the palette each parent chose (design item 12). The push
    `professional_access_requested` is server-only, like `pairing_accepted`, and says consent is
    being asked for, never that access began.

30. **A seasonal layer replaces the pattern for its dates, lives inside the one custody document,
    and reaches the co-parent only as a proposal** (MON-14, September 2026; schema 38).
    `domain/custody/SeasonalLayer.kt` is the one definition — `{id, name, from, to (inclusive),
    patternDays, momDayIndices, startDate, contactWindows, priority}` — and
    `CustodyModel.getCustodyFor` answers from the highest-precedence layer covering a date
    (`SeasonalLayer.PRECEDENCE`: priority, then the later start, then the id) before the base
    pattern; `CustodyResolver` still puts accepted swaps above both, and stays the one lookup.
    `contactWindowsOn` answers from the deciding layer too — a layer replaces the whole pattern,
    afternoons included — and item 24 holds inside it. Five things not to undo. **The wire form is
    `SeasonalLayerCodec` strings** (`L1;id;priority;from;to;anchor;cycle;days;windows;name`, name
    percent-encoded), never Gson over the data class, and `encodeAll` is canonical. **An entry this
    build cannot read is kept verbatim** (`CustodyModel.unreadableLayers`), decides nothing and is
    written back — dropping it would let an older build erase a newer one's summer. **Item 24's
    three wire rules apply unchanged under the key `seasonalLayers`**: a missing key is an older
    build's write and the mirror keeps its copy; a pattern write always writes the key (`[]` for
    none); proposal and swap writes carry the stored list verbatim
    (`SharedCustody.seasonalLayersWire`, `CustodyProposal.seasonalLayersWire`), and
    `firestore.rules`' `seasonalLayersKeptOrDropped` refuses one that changes it. **Saving the base
    pattern carries the agreed layers** (`CustodyModelRepository.withActiveLayers`) — the form
    knows nothing of layers, and without that every fortnight edit would propose deleting the
    summer. And **a layer change is a pattern change**: `submitSeasonalLayers` goes through
    `submitPattern`, so a paired family gets a proposal, never an overwrite, and the section
    refuses to send while the co-parent's own proposal waits (the repository's fallback there is
    a local save). The grid shows a layer only through the custody band it already draws — **no
    new colour, and no per-month banner** (the variable-height strip `CalendarScreen` removed for
    school vacations). `functions/calendar-feed.js` ports the codec and the precedence; change the
    Kotlin, change the fixture both suites share. **Holiday fairness (MON-20) only counts**:
    `HolidayFairnessCalculator` reads the same resolver, so swaps and layers count as drawn and a
    contact window is never a night; its "Propose a change" opens the layer editor.

31. **A shared file is indexed in Firestore, stored in Storage under its family's path, and never
    reached by a download URL** (MON-23, September 2026). Two surfaces: the **vault**,
    `family_documents/{docId}` (Settings → Family → Documents, `presentation/documents`), with the
    bytes at `family_documents/{familyId}/{docId}/{fileName}`; and **chat attachments**, bytes at
    `chat_attachments/{conversationId}/{messageId}/{fileName}`, referenced from the message.
    `domain/files/SharedFilePolicy` holds the cap (under 20 MB) and the types (PDF, JPEG, PNG,
    HEIC/HEIF, WebP) that both rule files repeat — change all three together. Eight things not to
    undo.
    **One Room table, the vault index cache, and nothing else** (schema 43,
    `family_documents_cache`). The vault is a Firestore listener (`FamilyDocumentIndex`);
    `FamilyDocumentIndexCache` stores every **server-confirmed** snapshot as the family's whole set
    of rows (tombstones kept with `deletedAtMillis`, never listed) and lists them, under
    `documents_possibly_outdated`, only while the listener fails or answers from Firestore's own
    cache. It is the **index only** — the bytes stay in Storage and `SharedFileCache` — with **no
    outbox and no upload**: every write goes to Firestore first and reaches the cache only through
    the next server answer. Every read is scoped to one `familyId`, the repository refuses a family
    the signed-in uid is not in, and `clearAllTables` wipes it on an account switch. An empty cache
    is not an empty vault: with nothing stored it says "unavailable" or nothing, never "no
    documents". Don't add a column that uploads, a second cache for file bytes, or a read that isn't
    scoped to a family. A chat reference rides the `attachments` list `Message` already had, as a
    `ChatAttachmentCodec` string (`att1|path|type|size|sha256|name`) — never Gson over the data
    class, and never a new column.
    **The Storage gate is the path.** `storage.rules`' `isOneOfPair` splits the first segment —
    `FamilyKey.of`, the two uids — so the emulator runs every case
    (`firestore-tests/rules/storage-shared-files.test.js`); the cross-service `firestore.get()`
    it cannot run is not used. The cost is written in the rule: a path names its pair for ever,
    so an ex-partner who kept one can still fetch that file after an unpair, although the
    vault's index narrows (`family_documents` is in `SHARED_AUDIENCE_COLLECTIONS`). Don't widen
    a block to "signed in"; don't add `list`.
    **No download URL, ever.** A token URL bypasses Storage rules for whoever holds it.
    `SharedFileStorage` has no `downloadUrl` call; a reader downloads as themselves, and
    `vaultKeysOnly` refuses a `downloadUrl` field.
    **The digest is checked, not decorative.** Every upload stamps `uploader` and `sha256` in custom
    metadata (the rule requires both), `SharedFileCache` renames a download into place only when
    its SHA-256 matches, and the export lists attachments by name and SHA-256
    (`RecordFormat.messageText`), never by their bytes. Nothing may overwrite a stored object
    (`resource == null` in the rule — the emulator treated an overwrite as a create).
    **A chat message is written only after its file is stored.** `MessageRepositoryImpl.deliver`
    calls `AttachmentUploadGate.ensureUploaded` (`ChatAttachmentOutbox`) before the Firestore write,
    so a message whose upload failed stays SENDING/ERROR, shows "Not uploaded yet" and no tick, and
    the ordinary outbox retries it with its staged file in `files/chat_outbox/{messageId}/`. Don't
    move the gate after the write. A bubble only opens a reference stored under its own message
    (`ChatAttachmentCodec.belongsTo`).
    **Shared by definition.** A vault document has no private form (item 3 does not apply), needs
    a family (`isFamilyMember`, not `familyIsMineOrBlank`), and its audience is bounded by
    `isMyAudience` *and* by the family id, so a parent with two families cannot file one family's
    court order into the other's. The screen says so before anything is added.
    **Only the uploader renames, re-files or deletes, and a delete is a tombstone** (item 14).
    `sweepDeletedDocuments` removes a vault tombstone **and its file** after 90 days
    (`FILES_SWEPT_WITH_TOMBSTONE`); no client can delete a chat file at all.
    **Account deletion reaches both**: the departing parent's vault documents and their folders
    (`AUTHORED_FILES.family_documents`, keyed on the stored `familyId`, never a blank prefix), and
    every conversation's `chat_attachments/{id}/` with the thread.
    None of it works live until `firebase deploy --only storage` — the known issue below.

32. **The parenting plan never becomes a schedule by itself; a proposal cites it and never parses
    it** (MON-21, September 2026, owner decision). `domain/parentingplan/PlanScheduleLink.kt`
    names the schedule questions (`SCHEDULE_QUESTIONS`: `care_weekday` → base pattern,
    `holidays_school`/`holidays_special` → a seasonal layer) and offers **Propose as the schedule**
    only when `ParentingPlanComparison.statusOf` is `AGREED`, the account is paired, and no
    co-parent proposal waits (`CustodyProposalTransition.pendingFromCoParent`, shared with the
    seasonal section). Four things not to undo. **The parent builds the pattern** in the ordinary
    editor under a read-only quote of the answer; nothing turns the free text into a pattern, and
    saving goes through `submitPattern`/`submitSeasonalLayers` unchanged — a paired family gets a
    proposal, never an overwrite. **The citation is one top-level key, `proposalPlanCitation`,
    beside `proposal`**, as a `PlanCitationCodec` string (`"p1|<questionId>|<16 hex of SHA-256>"`),
    never Gson; `CustodyProposal.planCitationWire` carries it **verbatim**, unreadable ones
    included, so a swap write re-sends what was there. The hashed text is both agreed answers,
    sorted and joined (one text when identical), so either phone derives the same hash. **The
    rules bound it and tie it to the proposal**: it is in the proposal-only and swap `hasOnly`
    lists, `planCitationValid` requires a string of at most 128 characters beside a `proposal` and
    lets only that proposal's author put or change it, and `planCitationKeptOrDropped` keeps a swap
    from changing it while an older build may drop it; accept, decline and withdraw clear it with
    the proposal. And **the reader re-derives, never trusts**: the proposal card says "From the
    parenting plan" only while the plan still hashes to the citation, "changed since" once it does
    not, and nothing for a missing key (an older build), an unreadable one or an unknown question
    — never an error. The calendar feed never reads `proposal`, so it never reads this.
    The same live `pendingProposalCitation` is printed by Home's proposal pop-up
    (`presentation/home/AwaitingDialogs.kt`, which takes a `ProposalAsk` and an `AwaitingActions`
    so its signature stays small) and by the calendar's review banner (`ChangeRequestBanner`'s one
    optional `detail` line, worded by `planCitationShortLine`). Don't re-derive it per screen.
    **The export reads the citation from the chat, not the custody document**: the
    `CUSTODY_PROPOSED` card carries the same codec string as `activity.planCitation`, because
    messages can't be edited and the document drops the key once the proposal is answered. The
    record prints the question as cited when the proposal was made (`RecordFormat.planCitation`),
    never re-hashed against today's plan.

33. **A child's own schedule overrides the family's inside the one custody document, and is the
    family schedule everywhere it is not asked for** (FAM-4, September 2026; schema 42).
    `domain/custody/ChildScheduleOverride.kt` is the one definition — `{childId, patternDays,
    momDayIndices, startDate, contactWindows}` — stored under `childOverrides` as
    `ChildOverrideCodec` strings (`C1;child:<id>;<anchor>;<cycle>;<slot-1 days>;<windows>`, the
    child in its `FamilyMemberRef` form), never Gson, never a second document per child (SEC-4's
    comparison would multiply). Room keeps it in `custody_models.childOverridesJson` (null = none,
    `ChildOverrideJson`). Five things not to undo. **An override is self-contained**: the family's
    seasonal layers and accepted swaps do not move an overridden child, and `getCustodyFor` never
    reads overrides — `ChildCustody` is the separate question. **Item 24's three wire rules apply
    under `childOverrides`**: a missing key keeps the mirror's copy (`ChildOverrideJson.mirrored`),
    a pattern write always writes the key, proposal and swap writes carry
    `SharedCustody`/`CustodyProposal.childOverridesWire` verbatim, and `firestore.rules`'
    `childOverridesKeptOrDropped` refuses one that changes it; unreadable entries (another version,
    a second entry for one child, past 16) are kept verbatim. **Saving the base pattern carries the
    agreed overrides** (`CustodyModelRepository.withActiveLayers`), and **a change is a pattern
    change** — `submitChildOverride` goes through `submitPattern`, so a paired family gets a
    proposal. **It appears at two, never at one**: the calendar band follows a child only when the
    member filter is exactly that child (`presentation/calendar/ChildCustodyBand.kt`, which also
    switches the swap markers and long-press off — a swap is about the family schedule), Home's
    hero adds "<child> is with <parent> today" per child only on a day they are apart
    (`ChildrenToday`, names, never colours), and custody setup's "Different schedule for a child"
    scopes the same editor to one child (`CustodySetupViewModel.editSchedule`) and is hidden below
    two children. **The calendar feed stays the family schedule** and ignores the key. See
    `docs/DESIGN-custody-per-child.md`.

34. **The private journal never leaves the phone** (MON-22, schema 41). `journal_entries` has no
    `syncedToFirestore` column, no Firestore data source, no rule and no push;
    `JournalRepositoryImpl` depends on `JournalDao` alone, and every query is scoped to the author.
    `clearAllTables` (account switch, deletion) wipes it; sign-out keeps it. `familyId` is stamped
    at create and never re-derived (item 18). The only exit is the export's "My private journal"
    checkbox, **off by default**, which prints this parent's entries for the period after the
    expenses, labelled in both formats as one parent's private notes the other never saw, with that
    phone's clock (`domain/export/RecordJournal.kt`). Don't add a sync path, a backup or an outbox
    column.

## Known issues / do not "fix" silently

**Check an entry against the code before acting on it.** Two entries in this section, and one
claim in `storage.rules`, have described defects that were already fixed or limits that were
never real — a reader following them would have re-fixed working code, or accepted a constraint
that does not exist. When an item here turns out to be stale, correct it in the same commit as
whatever you were doing; a stale "known issue" costs more than a missing one.

- ~~**Deleting a child or a pet removes the Firestore document outright.**~~ **Fixed (CQ-19,
  schema 32.)** Both now take the treatment `data/sync/Tombstone.kt` defines for events and
  expenses, and the four rules item 14 states apply unchanged. Two things specific to these two
  tables are worth knowing before touching them. **`SyncService` syncs `child_info` as well as
  the repository does**, so the outbox split had to be made in both places — sending a pending
  tombstone through `upsertChildInfo`, which is a `set()`, would rewrite the document from a row
  that exists only to record its own deletion and resurrect the child on both phones. And
  **`getChildInfoById`/`getPetById` deliberately still return a tombstoned row**, mirroring
  `EventDao.getEventById`: the sync path needs "there is a row this device has deleted" and
  "there is no such row" to be different answers, so the filtering for a *user's* question
  happens at the repository boundary. The hard-delete methods on both Firestore data sources
  were removed rather than left beside the tombstone writers, since neither had a caller left —
  unlike `FirestoreEventDataSource.deleteEvent`, which keeps one (an event turned private).

- ~~**A change request says "Sent" whether or not it left the phone.**~~ **Fixed (CQ-20)**, and
  this entry outlived the fix by a while: `ChangeRequestsScreen` shows a Queued chip on
  `!syncedToFirestore` and the ViewModel's state is `Saved`, not `Sent`. Kept for the rule it
  states — "sent" is a claim the app can only make once the write landed, and `MessagesList`'s
  honest tick is the model for any new outbox.

- ~~**A proposed split ratio cannot be withdrawn, and the proposer is told nothing.**~~ **Fixed
  (UX-17)**: `FamilySettingsRepository.withdrawProposal` is called from `ExpenseViewModel`, and the
  Expenses screen carries the "waiting for your co-parent" banner with its Withdraw action.

- ~~**A ratio agreed before pairing reaches the pair silently.**~~ **Fixed (UX-18)** with the push
  type this entry asked for, `PushPayload.SPLIT_RATIO_AGREED`, sent by `publishCachedRatioIfMissing`
  **and** (September 2026) by `submitRatio`'s first-write branch — a pair that has no document yet
  gets its agreement from whichever screen writes first, and with the co-parent link now made
  *first* in onboarding that branch is the second parent's ordinary path, not a corner. The rule
  the entry gave still holds: never route the first agreement through `propose`.

- **`storage.rules` has never been deployed past its July 2026 state, and that is why attaching a
  photo to a pet fails** — and why the MON-23 vault and chat attachments (item 31) cannot upload
  anything live yet. The file in this repo covers `receipts/`, `event_images/`,
  `medical_photos/`, `pet_photos/`, `family_documents/` and `chat_attachments/`; the live bucket, on the evidence, still covers only the
  first two, so `pet_photos/**` falls through to `match /{allPaths=**} { allow read, write: if
  false; }` and every pet — and, silently, every medical — photo upload is refused. The client
  path is sound and was ruled out end to end. **The fix is an ops action nobody has taken:
  `firebase deploy --only storage`**, which also closes the still-unchecked box at
  `docs/REVIEW-2026-07-23.md:65`. Nothing caught this for a long time: `firebase.json` configured a
  Firestore emulator only, and Storage rules had no test coverage at all. They do now
  (`firestore-tests/rules/storage.test.js`, September 2026) — and the suite passes, which is
  the point worth understanding rather than a contradiction. It exercises the ruleset **in
  this repository**, where `pet_photos/**` is present and correct; the failure is that the
  bucket enforces an older deploy. A test can prove the file is right and still not tell you
  it was shipped. Deleting the `pet_photos` block does turn the suite red, so the coverage is
  real — it just cannot substitute for the deploy. The upload handlers now write a
  `Log.e` line so the next occurrence is at least diagnosable on a device — they reported only
  through Crashlytics before, which writes nothing to logcat.

- **The expense split is agreed per pair, and each expense is priced at the split in force when it
  was recorded.** `family_settings/{pairId}` (same derived id as `custody_models`) holds the agreed
  `momShareBasisPoints` and any pending proposal; `Expense.splitBasisPoints` is a **snapshot** of
  it. Do not "simplify" that by reading the current agreement when the balance is computed — the
  whole point is that renegotiating cannot re-price a month the two parents have already settled
  and argued about. A null on an expense means it predates the agreement and divides evenly, which
  is what it was. The ratio is also ignored while both parents still read the same slot, the same
  condition that leaves `ExpenseBalance.splitKnown` false: applying a slot-keyed share there would
  charge one parent the other's part.

- **`Expense.splitBetween` used to be empty on every row production ever wrote**, so
  `calculateExpenseBalance`'s guard never fired, `currentUserOwes` was always zero, and both
  parents were told at once that the other owed them their whole month's spend. `addExpense` now
  names both parents on a shared expense. The unit suite missed it for months because
  `ExpenseBalanceTest`'s fixture defaults `splitBetween` to both parents — a shape production never
  produced. Be suspicious of any fixture whose default is the thing under test.

- `Expense.currency` is a real per-expense field. A month mixing currencies is now summarised
  **per currency** (`calculateExpenseBalancesByCurrency` → one `ExpenseSummaryHeader` per currency
  on the Expenses screen; the Home "this month" tile joins per-currency subtotals). There is still
  no FX conversion between currencies (spec §10) — deliberately: totals stay honest within each
  currency rather than being normalised. Do not reintroduce a single cross-currency total.

- **A failed chat Firestore listener now reconnects, but only for a while.** Both mirror branches
  in `MessageRepositoryImpl` go through `reconnecting()` — `retryWhen` with exponential backoff,
  eight attempts, capped at a minute apart — before reaching the `.catch` that ends the mirror.
  That covers the case seen in production: on the first launch after install both listeners were
  denied ~0.5 s before `ensureConversation` created the conversation document, and the whole
  session then ran on local data while looking entirely healthy. **CQ-8 is closed** (this
  paragraph used to say it was open): `data/chat/ChatMirror` awaits `ensureConversation` before
  it subscribes and restarts the mirrors itself, so an outage longer than the backoff no longer
  ends in a degraded state that lasts until the process restarts. What the rest of this entry
  describes is the mechanism that made the old defect permanent, kept because it is the shape to
  avoid: `catch` *completes* the mirror flow, so
  `merge(mirror, local)` runs on Room alone afterwards, and `SharingStarted.WhileSubscribed`
  cannot restart it — `rememberChatUnreadCount()` in `NavGraph` holds an Activity-scoped
  `ChatViewModel` collecting `unreadCount` for the whole process lifetime, so the subscriber count
  never reaches zero. The structural fixes are awaiting `ensureConversation` before subscribing,
  or dropping that Activity-scoped collector. Don't "fix" it by removing the `.catch` — an
  uncaught failure in `viewModelScope.launch` terminates the process — and don't make the retry
  unbounded: a genuinely broken rule would then reconnect for the life of the process, and any
  test of the give-up path spins on the virtual clock instead of finishing.

- ~~**Chat follows the *first* co-parent, not the selected family.**~~ **Fixed (M-8, September
  2026).** `ChatViewModel.coParentLink`/`unreadCount` and `ChatMirror` used to key on
  `PairingRepository.observePairingState()` — the **server's** `partnerId`, `partnersOf(...)[0]` —
  so with two families the Chat tab, its badge and the process-wide mirror stayed on the first
  family whatever the switcher said. Both now read `data/chat/ChatPartnerSource`, which splits the
  question in two: the server decides **whether** there is a co-parent (`Loading` stays
  `Resolving`, `NotPaired` stays `None`, so a stale projection cannot invent a thread), and the
  projection `SelectedFamilySource` writes decides **which**, falling back to the server's partner
  for the moment after a first pairing when the Room row has not caught up. A one-family account
  resolves to the same uid either way and does not even re-emit. `ChatMirror` keeps every CQ-8
  guarantee — `ensureConversation` awaited before either listener attaches, the outer restart
  loop, the bounded inner retry, the `.catch` — and its `collectLatest` now also cancels the old
  thread's listeners on a switch: **the process-wide mirror follows exactly one family.** That is
  why cross-family badges *count* nothing: a Room `COUNT(*)` over a family nothing is mirroring
  would say 0 when it is not. The honest version shipped instead — a conversation-document
  listener per non-selected family and a **dot** on the switcher (design item 13) — so don't
  paper over it with a count. Unverified on two
  phones: see M-8's acceptance note.

- **Cross-time-zone chat is verified between two clients, not yet on two screens.** The August
  2026 chat sync moved message times to epoch millis specifically so two parents in different
  zones agree (see item 13 above), and it is covered by unit tests that drive the two zones
  explicitly (`ChatReadStateTimeZoneTest`) plus a 12→13 migration test. Since September 2026 the
  `e2e` CI job runs the acceptance scenario's *logic* end to end: two accounts, the production
  `MessageRepositoryImpl` on each, the real rules, one parent at UTC+14 and the other at UTC−11 —
  the message arrives unread, the Room badge count is 1 and clears on `markRead`, and the
  sender's ticks reach DELIVERED and then READ (`TwoParentChatTest`). What is still **not run** is
  the part only phones show: the badge and ticks as drawn, the times as displayed, and the push
  that wakes the other phone. Keep the device check for those; do not re-open the logic.

- ~~**The shared custody schedule orders the two phones' writes by a naive local date-time.**~~
  **Fixed (SEC-4, schema 29).** `CustodyModelEntity.lastModifiedAtMillis` is epoch millis and
  `CustodyModelRepository.isNewer` is a `>` on two of them, so two parents in different zones
  order their writes by real time. It mattered more than a displayed date: the side `isNewer`
  judges newer is not merely kept, it is **re-pushed over the other**, so the wrong schedule
  could win *and overwrite*.
  **Read `domain/custody/CustodyTimestamp.kt` before touching the wire form**, because the two
  obvious ways to carry an instant are both wrong here and the file says why. The Firestore
  field keeps its name *and* its ISO-string type, and only the zone it expresses changed, to
  UTC. Changing the type would leave a co-parent on an older build reading a blank — and a
  blank compares equal to their last dismissal, so every future change would go silently
  un-announced, which is the one failure this product must not have. Adding a numeric field
  beside it would put a new key in `affectedKeys()`, and `firestore.rules` gates a proposal or
  swap write with `hasOnly([...])` — the first such write from an upgraded build would be
  denied outright. Widening those lists is not the way out: `lastModifiedAt` is absent from
  them precisely so a swap cannot re-date the document and win every later comparison.
  Two things that still hold: a value written by an older build carries no offset to recover,
  so reading it as UTC is wrong by that device's offset — irreducible, and no worse than it
  already was; and `saveReslotted`/`archiveRejected` still keep the stored dates on purpose,
  because re-dating them makes this device win every comparison forever
  (`CustodyModelRepositoryTest` pins both).

- ~~**The calendar never renders `EventUiState.Error`.**~~ **Fixed** — and this entry described
  the defect for some time after it was gone. `CalendarScreen`'s `LaunchedEffect(uiState)` has an
  `is EventUiState.Error` branch raising a snackbar with a Retry action wired to
  `EventViewModel.refresh()`, which is the fix this entry used to prescribe, strings and all.
  Kept rather than deleted because the reasoning is worth finding: `EventViewModel` raises
  `Error` from eleven places, a failed *write* is the dangerous one (a parent drags an event, the
  optimistic UI moves it, the write fails, the next sync puts it back), and `Loading` is still the
  wrong thing to render on the grid — the query flips to `Loading` on every re-anchor and the grid
  would flicker.

- `firestore.rules` (strict) was realigned with the real document schema (ISO **string**
  dates, presence-based key validation, `change_requests`/`expenses` collections added,
  over-strict `lastModifiedBy`/`canModify` gates dropped) so it no longer rejects the app's
  own writes, and now also covers `invitations`, `conversations` and `messages` for
  co-parent pairing and chat, plus `custody_models` for the shared custody schedule. (This
  sentence used to name `custody_schedules` instead: that block was dead — the Room table it
  was named after has no Firestore data source — and it has since been **deleted**. Do not put
  it back; see the audit bullet below, which exists for exactly that reflex.) It was
  deployed live to `coparently-a39c9`
  as of this change (`firebase deploy --only firestore:rules`), replacing the permissive
  `firestore.rules.simple` the project ran on until the client's last write to another
  user's `users/{uid}` document was removed. `firestore.rules.simple` remains in the repo
  only as a historical fallback — it is no longer deployed.
- The `budgets` collection now has a rule block (`firestore.rules`, gated on
  `createdByFirebaseUid` + `isPartnerOf`, deployed live). The gap this closed was worse
  than the pre-fix `expenses` bug: budget documents written by
  `BudgetRepositoryImpl.addBudget()` carried **no owner field at all** — not even a wrong
  one — so there was nothing a rule could gate on. The fix stamps `createdByFirebaseUid`
  on write and filters `FirestoreBudgetDataSource.getAllBudgets()` on it via
  `creatorUids`/`whereIn`, the same shape as `expenses`. `addBudget`/`deleteBudget` also
  gained the same try/catch guard `ExpenseRepositoryImpl` has, since an uncaught
  `PERMISSION_DENIED` (or any Firestore error) from an unguarded suspend call inside
  `viewModelScope.launch` crashes the app, not just fails the sync. **Caveat:** any
  `budgets` documents that synced to Firestore *before* this fix have no
  `createdByFirebaseUid` field and will silently stop matching the filtered read query
  (no error — they're just excluded from `whereIn`'s results). Room stays the source of
  truth so nothing visibly disappears on the device that created them, but they won't
  restore on a reinstall or a second device until re-saved. No backfill migration was run
  as part of this fix.
- A full audit (grep every `.collection(...)` call in `app/src/main/java` and
  `functions/index.js`, diff against `firestore.rules`' match blocks) found two more
  mismatches. One is now fixed; the other is left as-is because it is not reachable in
  production:
  - `FirestoreMedicalDataSource`/`FirestoreEducationDataSource` and their unbound
    `MedicalRepositoryImpl`/`EducationRepositoryImpl` were the unreachable half of this,
    and they have since been **deleted** — this bullet described them as still present for
    some time after they were gone. The rule it argued for was correctly never written:
    don't add rules for collections no client reaches.
  - `custody_schedules` (Room's `CustodyScheduleEntity`/`CustodyScheduleDao`, the legacy
    per-parent custody table) has no Firestore data source and never will — it stays
    Room-only. Its rule block, which matched no client code, has been deleted from
    `firestore.rules`; the live custody rule now guards the real synced collection,
    `custody_models` (one document per pair, gated on `participants`, with `allow get`
    rather than `allow read` so no list query can ever be issued, and with
    `lastModifiedBy == request.auth.uid` required on create and update — the change banner
    suppresses a reader's own uid, so an unvalidated author field lets either parent
    overwrite the shared schedule without the other being told). Do not resurrect the
    `custody_schedules` block just because the Room table name is still there.
- `strings.xml` is **no longer gitignored** (older docs/audit §2.1 claim otherwise —
  stale). No secrets live in resources: the OAuth client secret is injected via
  BuildConfig (`GOOGLE_CLIENT_SECRET` gradle property / env var). `GEMINI_API_KEY` is gone
  with the AI subsystem — don't reintroduce a model key in the client. Real secrets belong in
  `gradle.properties`/env vars only.
- **Text a ViewModel or a service produces is a `UiText`, resolved in composition** (CQ-14,
  September 2026). `presentation/common/UiText.kt` holds *which* string — `Res` with arguments,
  `Plural`, `Date` (formatted in the reader's locale at resolution), or `Raw` for what is already
  the user's own words — and the screen calls `asString()`, or `asString(context)` with the
  **Activity's** `Context` inside a snackbar/Toast lambda. Still don't inject `Context` into a
  ViewModel: the application's configuration can lag AppCompat's per-app locale on older APIs,
  while composition follows the Activity. Three rules. **A screen that branches on an outcome
  gets a typed code, not text** — `CalendarScreen` used to compare the literal
  `"Event rescheduled"` to decide whether to offer Undo (UX-12); it now reads
  `EventOperation.RESCHEDULED`. **Never render `e.message`**: it is English and sometimes a class
  name — log it, and show a localised sentence (`UiError.message` is logs-only). **Which sentence
  is decided by what the ViewModel knows** (CQ-11): one that knows which operation failed uses its
  own resource (`change_request_error_apply_failed`, `pets_delete_failed`, …); only one whose
  failures arrive as an arbitrary `Throwable` from a use case — today `EventViewModel` alone —
  classifies them with `ErrorHandler.handleError` into an `AppError` and words that by type
  (`presentation/common/ErrorText.kt`). Don't route the first kind through `ErrorHandler`: a
  sentence chosen by exception type is vaguer than the one the call site already has.
  **The data layer reports facts, not sentences** — `CalendarSyncRepository`'s `SyncResult`
  carries counts, dates and a `SyncFailure`, and `SyncViewModel` words them. Stored fallbacks
  (`"Untitled Event"` on an import, a chat `senderName` of `"Unknown"`) are data, not UI text,
  and stay as they are: localising them would write one parent's language into a record the
  other reads.
- Calendar range/day queries now match multi-day & overnight events by overlap
  (`getSingleEventsByDateRange` / `getEventsByDate`), not start date only.
- Unit tests for ChildInfo/Pairing/Settings/Sync ViewModels were once removed as stale (they
  targeted long-gone APIs). **All four are back**: `ChildInfoViewModelTest`,
  `PairingViewModelTest`, `SyncServiceTest` and — since September 2026, starting with the push
  switch — `SettingsViewModelTest`. As of CQ-13 **every ViewModel has a test file**; a new
  ViewModel arrives with one.

- **`ChildInfoViewModel`'s editor state is loaded by id, never from the head of a list.**
  `loadChildInfo()` serves the list screen and touches nothing else; `loadChildInfoById()` is the
  only writer of `currentChildInfo`, and it cancels a previous observation before starting the
  next. This used to be the other way round — `init` collected `getAllChildInfo()` for the
  ViewModel's whole lifetime and set `_currentChildInfo = childInfoList.first()` on **every**
  emission — so while a parent edited child B, any write touching `child_info` (a background sync
  tick was enough) reset the state to child A and repopulated the visible form. The damage was at
  save: the snapshot-and-`copy()` base had become child A, so the write landed on **child A's real
  row**, id and `createdAt` included, carrying child B's field values. Keep the split: a screen
  that shows every child reads the list it already holds (`ChildInfoScreen` reads
  `state.childInfoList`), and an editor that owns exactly one child observes that one by id.

## Localization (i18n) — July 2026, keep consistent

The app ships in 5 languages: **English (base `values/`), Czech, German, Russian,
Ukrainian** (`values-cs/`, `values-de/`, `values-ru/`, `values-uk/`). Rules:

- **Language selection**: device locale by default, plus a manual per-app override — the
  "Language" card in Settings (`presentation/settings/AppLanguage.kt`,
  `AppCompatDelegate.setApplicationLocales`). The choice is persisted by the
  `autoStoreLocales` manifest service and mirrored to the Android 13+ system per-app
  language setting. There is no DataStore/ViewModel state for it — AppCompat is the
  source of truth.
- **Infra invariants**: `MainActivity`/`QRScannerActivity` must stay `AppCompatActivity`
  (not `ComponentActivity`) and `Theme.CoPlanly` must stay an AppCompat theme — per-app
  locales silently stop working otherwise. (It is `Theme.AppCompat.DayNight.NoActionBar` since
  UX-13, with a per-theme `@color/window_background` so a dark cold start does not flash white;
  `values-night/` is a night qualifier holding that colour, not a locale, and holds no strings.)
  `res/xml/locales_config.xml`, the `AppLanguage` enum, and the `values-<language>` folders must
  list the same locale set.
- **Adding a string** = add the key to the feature's base `values/<feature>_strings.xml`
  AND to all four locale variants of that file. Missing translations fall back to English
  at runtime. **Lint will not catch a missing one**: `MissingTranslation` is switched off
  outright in `app/build.gradle.kts` (`disable += "MissingTranslation"`), not demoted to a
  warning — a disabled check does not run, so it reports nothing under any severity. Verify
  locale completeness by grep instead, e.g. `git grep -c 'name="your_key"' -- app/src/main/res/values*/*.xml`,
  which should return five files and also catches a duplicate the lint check never would.
  `node tools/check-invariants.js` does that sweep over every key at once — and compares each
  translation's format arguments, since a dropped `%1$s` throws `IllegalFormatException` only on
  the device of whoever reads that language. CI runs it as the `invariants` job.
- In composables use `stringResource(...)`; for text consumed inside non-composable
  lambdas (snackbars, coroutines) capture the string in composable scope first. Text that comes
  from a ViewModel or a service is a `UiText` (`presentation/common/UiText.kt`, CQ-14) — never a
  `String` built there. Language
  endonyms in the picker ("Čeština", "Русский", …) are `translatable="false"`.
- Dates/day/month names come from `java.time` formatters with the default locale —
  never from string arrays.
- There is no `values-en/` — base `values/` IS English; don't recreate it.

## Language conventions

- The user communicates in Russian; reply in Russian in chat.
- All repository content — code, comments, docs, commit messages — is **English**.
