# iPhone phase 2: the whole Android app on Kotlin Multiplatform — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move dependency injection, the data layer and the presentation layer into Kotlin
Multiplatform modules, so that `app/` becomes a thin Android shell. The Android app keeps doing
exactly what it does today, and every document it writes stays byte for byte the same.

**Architecture:** Two new modules join phase 1's `:shared`:
- `:shared` keeps the domain.
- `:shared-data` holds Room, Firebase, HTTP, preferences, the repositories and sync.
- `:shared-ui` holds the ViewModels, the screens, the theme, navigation and the resources. It
  produces the one iOS framework phase 3 links, exporting the other two modules.

Packages stay unchanged, as in phase 1. Every Android-only dependency is reached through an
interface in `commonMain`:
- the Android implementation is today's code, in the module's `androidMain`;
- the iOS implementation is either real (storage, network, Firebase: phase 3 needs it) or reports
  "unavailable" (the platform features of spec §5.4, which phase 4 builds).

Hilt is replaced by Koin. The app keeps what only an Android app has: the `Application`,
`MainActivity`, the manifest, the services and workers, the Glance widget, notifications and the
Android implementations of platform features.

**Tech Stack:** Kotlin Multiplatform, Koin 4 (`koin-core`, `koin-compose-viewmodel`,
`koin-androidx-workmanager`, `koin-test`), Room KMP with `BundledSQLiteDriver` on iOS and SQLCipher
on Android, kotlinx.serialization, Ktor (OkHttp engine on Android, Darwin on iOS), Compose
Multiplatform with its resources, multiplatform Lifecycle and Navigation, Coil 3, the
kizitonwose calendar's multiplatform artifact. Firebase goes through GitLive's
`firebase-kotlin-sdk` or through `expect`/`actual` wrappers; Task 1 decides which.

**Spec:** `docs/superpowers/specs/2026-10-27-iphone-app-design.md` (§4, §5.1 steps 3–6, §5.4, §5.6,
§7, §8, §9). Phase 1's plan, `docs/superpowers/plans/2026-11-01-iphone-phase-1-shared-domain.md`,
fixed the conventions this plan reuses. Read both, and the parts of `CLAUDE.md` each task names,
before starting.

**Execution method: subagent-driven** (owner decision, 1 November 2026): a fresh implementer per
task and a fresh reviewer before the next task starts, then a whole-branch review.

**Start gate.** Three conditions, all required:
1. Phase 1 (PR #158) is merged. That happens after the Android release (spec §2).
2. **MON-25 is done**: the request clocks' on-time steps run in a scheduled Cloud Function (spec
   §9.2, "lands before phase 2"). Otherwise this plan would carry the clock's WorkManager steps into
   `commonMain` only for MON-25 to delete them.
3. **Re-run each task's inventory command before starting it.** The counts below are from 1 November
   2026: 19 Hilt modules, 57 `@HiltViewModel`s, about 200 `@Inject` classes, 12 workers, 24 Gson
   users, 30 `EncryptedPreferences` users, 31 Firestore files, 16 entities, 15 DAOs, 41
   migrations, 2,335 strings and plurals in 65 files, about 73,000 lines in `presentation/`.

**Pull requests.** Spec §5.1 makes each step its own pull request with the full CI. Here each task
is one pull request off `main`, merged before the next one starts, except Task 1, which merges no
code. A task too large to review in one sitting may split into several pull requests along the
seams its steps name; it is reviewed as one task. Spec §7 applies: no Android feature freeze, and
a feature in flight is rebased onto a move.

## Global Constraints

- **The wire is frozen.** `CurrentWireFixturesTest` passes **without** `UPDATE_WIRE_FIXTURES`,
  `WireContractTest` passes, and so does the `upgrade` job's run of the base build's
  `WireContractTest`. A fixture diff is a bug, never a regeneration (spec §5.1 step 2, §4 anti-goals).
- **Stored bytes are frozen too.** Room columns, the sealed preference store, drafts and the
  calendar-feed text are read and written exactly as today. The `upgrade` job and the R8 probe hold
  this.
- **Android behaviour does not change.** On every pull request these jobs stay green, with the
  `full-ci` label added at the task's end:
  - build-test, static, detekt;
  - release with the mapping and baseline-profile checks;
  - r8-runtime, instrumented (all three legs), e2e, upgrade;
  - screenshots, with **no baseline change**;
  - invariants, functions, web, rules, shared, shared-ios.
- **A move keeps its package and its class name**, and is a `git mv`. The `upgrade` job's seed runs
  this branch's test APK against the base build's classes. So a signature `UpgradeFixture`'s KDoc
  lists may change only through reflection in the fixture (CLAUDE.md, the `upgrade` job).
- **No server change.** Rules, Cloud Functions, indexes and Storage paths stay as they are (spec
  §4 anti-goals, §5.5).
- **Privacy promises hold on both platforms.** Receipt OCR, dictation and the journal never leave
  the device, and pushes carry a type, never a sentence (spec §4).
- **An iOS implementation is real or says "unavailable".** It is never a stub that pretends to
  work. A screen hides an affordance whose capability reports unavailable (CLAUDE.md design
  item 8).
- **minSdk 26, iOS 17** (spec §7). Nothing in an `androidMain` may need a higher API level.
- **Every invariant in `CLAUDE.md` survives unchanged.** Paths are updated as phase 1 did it: one
  note per module root, not a rewording of each item (Task 14).
- **Commits:** Conventional Commits, each subject tagged `(MON-24)`, with the session's attribution
  trailer.
- **No Android SDK in cloud sessions.** Where a step says "CI", it means: push, then read the PR's
  "CI summary". iOS steps run on the hosted macOS job, or on the owner's Mac (💻) where the step
  says so.

## Review Focus

These failure modes are the ones most likely to reach a parent, and no ordinary test in a task
covers them. Each has a test in the task that owns it.

1. **Gson's exact output.** Gson escapes `< > & = '` as `<` and similar, leaves out nulls,
   keeps default values, writes fields in declaration order and prints doubles with
   `Double.toString` (`1.0E10`). kotlinx.serialization does none of these by default. The
   medical profile rides inside `toFirestoreMap()` as a JSON string, so this is the wire, not only
   storage.
   Tests (Task 3):
   - `GsonParityTest` (androidUnitTest), against Gson, over a corpus;
   - `StoredJsonVectorsTest` (commonTest), the same vectors on iOS.
2. **Strings after the move.** Five things can break:
   - Android escapes (`\'`, `\"`, `\n`, `\@`) — the base locale has 107 `\'`;
   - Czech, Russian and Ukrainian plural categories;
   - `%1$s`, `%1$d` and `%%`;
   - the per-app language on API 26–32 inside composition;
   - text that Android-only code reads outside composition (pushes, the widget, notification
     channels).

   Tests (Task 9):
   - `StringsParityTest` and `PluralsParityTest` (Robolectric, every key and locale);
   - the existing per-app locale instrumented test;
   - `PushNotificationTest` and `TodayWidgetTextTest`, unchanged.
3. **ViewModel scope and saved state.**
   - The activity-scoped ViewModels stay one instance per activity: chat unread, parent palette,
     family view, family switcher.
   - Navigation arguments still reach `SavedStateHandle`.
   - Form drafts (`FormDraft`, `rememberSaveable`) survive rotation and process death.

   Tests (Task 2): `ViewModelScopeTest` (Robolectric), plus the existing ViewModel tests.
4. **Opening an existing encrypted database.**
   - Room KMP with SQLCipher's open helper.
   - The 41 legacy migrations kept Android-only.
   - Room's Kotlin code generation, which is stricter about nullability in queries (Task 4).
   - The first migration written in `commonMain` must run under the SQLCipher path.

   Tests (Tasks 4 and 7):
   - `EncryptedDatabaseTest`, `CoPlanlyDatabaseMigrationTest` and the `upgrade` job, unchanged;
   - a new `ConnectionMigrationOnSqlCipherTest` (instrumented).
5. **Firestore semantics through a wrapper.** Each of these must reach the app as it does today:
   - a failed `Source.SERVER` read (the export's "incomplete" line);
   - `hasPendingWrites` (the change request's queued chip);
   - `ServerTimestampBehavior.ESTIMATE` (request clocks);
   - `FieldValue.serverTimestamp()` (`event_versions.recordedAt`);
   - `update()` versus `set()` for tombstones (CLAUDE.md item 14);
   - named `FirebaseApp`s (the e2e parents).

   Tests: Task 1's spike criteria, then the e2e job and the unit tests in Task 8.

---

### Task 1: Firebase spike — GitLive or `expect`/`actual`

The spec leaves the choice to a spike (§5.1 step 4, §7). Nothing in this task merges. It produces
a decision and its reasons.

**Files:**
- Create, on a throwaway branch only: `firebase-spike/` (a KMP module) with
  `androidInstrumentedTest` and `iosSimulatorArm64Test`, both against the Firebase emulators.
- Modify, on the plan's branch: `docs/ROADMAP.md` MON-24 (a "Firebase on two platforms" entry: the
  choice, its reasons, and the criteria table with results) and spec §7 (strike the open item).

**Interfaces:**
- Produces: `FIREBASE_CHOICE` = `gitlive` or `expect-actual`, recorded in ROADMAP. Task 8 branches
  on it.

- [ ] **Step 1: Write the criteria as tests.** One test per row. Each runs on both an Android
  emulator and the iOS simulator, against the Auth, Firestore, Functions and Storage emulators:

  | Criterion | Used by |
  | --- | --- |
  | offline persistence: a write while offline is read back, then lands after reconnecting | every outbox |
  | `get(Source.SERVER)` fails when offline, and does not fall back to the cache | export, request clocks |
  | snapshot metadata `hasPendingWrites` on a local echo | change-request mirror |
  | `ServerTimestampBehavior.ESTIMATE` | request clocks |
  | `FieldValue.serverTimestamp()` inside `set()` | `event_versions` |
  | `update()` of two fields leaves the rest of the document alone | tombstones |
  | a transaction that reads, then writes | custody and split withdrawals |
  | `array-contains` and `whereIn` queries | sync |
  | a named `FirebaseApp` with its own options | the e2e parents |
  | a callable in `europe-west3` with structured error details | pairing, AI |
  | a Storage upload with custom metadata, a download to a file, and no download URL | MON-23, L-4 |
  | Auth with a Google ID token credential, and the Auth emulator | sign-in |
  | the FCM registration token | `FcmService` |

- [ ] **Step 2: Run each row through GitLive** on both platforms. Record pass or fail, and any
  missing API.
- [ ] **Step 3: Decide.**
  - Choose GitLive if every row passes, or if a failing row can be covered by a small
    `expect`/`actual` beside it.
  - Otherwise choose `expect`/`actual`: one interface per Firebase product in `:shared-data`
    `commonMain`, shaped by the rows above and no wider.

  GitLive's Android side wraps the same Android SDK the app uses today, so Android behaviour cannot
  move; weigh that.
- [ ] **Step 4: Record the decision.** Update ROADMAP MON-24 and spec §7. Commit on the plan's
  branch: `docs: record the multiplatform Firebase choice (MON-24)`. Delete the spike branch.

### Task 2: Hilt becomes Koin (Android only)

**Choice: Koin, recorded with its reason.** It is the spec's default. It has first-class Compose
Multiplatform ViewModel support (`koinViewModel` in `commonMain`) and a WorkManager factory.
Hilt's test pattern of replacing one module maps directly onto Koin's module overrides, and the
app has 19 such modules and 19 Hilt instrumented tests. Koin resolves at runtime. `koin-test`'s
`verify()` over every module in a JVM unit test is what replaces Hilt's compile-time check.
kotlin-inject was set aside: its compile-time graph would need a KSP run per target for the
largest graph in the project, and Hilt's `@TestInstallIn` pattern has no direct equivalent there.

Removing Dagger also lifts the Kotlin 2.4 blocker named in `CLAUDE.md`'s toolchain line (Task 13).

Inventory: `grep -rlE "dagger\.|javax\.inject|HiltViewModel|hiltViewModel|AndroidEntryPoint|HiltWorker" app/src`.

**Files:**
- Create:
  - `app/src/main/java/com/coparently/app/di/koin/` — one Koin module per Hilt module, same names
    (`DatabaseModule` → `databaseModule`, …);
  - `app/src/test/java/com/coparently/app/di/KoinGraphTest.kt`;
  - `app/src/test/java/com/coparently/app/di/ViewModelScopeTest.kt`;
  - `app/src/androidTest/java/com/coparently/app/testing/KoinTestRule.kt`;
  - `app/src/androidTest/java/com/coparently/app/testing/UsesFakeFirebase.kt`.
- Modify:
  - every `@Inject`, `@HiltViewModel` and `@AndroidEntryPoint` class, and every `@HiltWorker`;
  - `CoPlanlyApplication` (`startKoin`, the `KoinWorkerFactory`);
  - the `androidTest` runner (`HiltTestRunner` → `KoinTestRunner`);
  - `FakeFirebaseModule` (it becomes an override module);
  - `.github/workflows/ci.yml` (the 16 KB leg's
    `notAnnotation=com.coparently.app.testing.UsesFakeFirebase`);
  - `app/build.gradle.kts` and `build.gradle.kts` (remove Hilt and the Dagger plugin);
  - `CLAUDE.md` (every Hilt mention: the hard rule "Hilt for all DI", the toolchain line, the
    instrumented paragraph).
- Delete: `app/src/main/java/com/coparently/app/di/*Module.kt` (the Hilt modules).

**Interfaces:**
- Produces:
  - `val appModules: List<org.koin.core.module.Module>` in `di/koin/AppModules.kt`;
  - `@Target(AnnotationTarget.CLASS) annotation class UsesFakeFirebase`, carried by every
    instrumented test that loads the fake Firebase override (that override mocks through MockK's
    agent, which does not load on 16 KB pages).
  Constructors lose `@Inject` and keep their parameters. Later tasks move these classes and their
  Koin definitions together.

- [ ] **Step 1: Write the failing graph test.** `KoinGraphTest.everyDefinitionResolves`
  calls `appModules.verify(extraTypes = listOf(Context::class, SavedStateHandle::class, WorkerParameters::class))`.
  Run `./gradlew testDebugUnitTest --tests '*KoinGraphTest*'` → it fails to compile (`appModules` is
  missing).
- [ ] **Step 2: Write the scope test.** `ViewModelScopeTest` (Robolectric, `MainActivity`). It
  asserts that `ChatViewModel`, `ParentPaletteViewModel`, `FamilyViewViewModel` and
  `FamilySwitcherViewModel` resolve to the same instance from two composables of one activity. It
  also asserts that a destination's ViewModel receives its navigation argument through
  `SavedStateHandle`, using one route with an argument: `friend_calendar/{familyId}`.
- [ ] **Step 3: Port the graph.**
  - Each `@Provides` and `@Binds` becomes a `single`/`factory` with the same scope (Hilt
    `@Singleton` → `single`, unscoped → `factory`).
  - Each `@HiltViewModel` becomes `viewModelOf(::X)`.
  - `hiltViewModel()` becomes `koinViewModel()`, keeping each call's `viewModelStoreOwner`.
  - Workers become `workerOf(::X)`.
  - Qualifiers become `named("…")` with the Hilt qualifier's name.
- [ ] **Step 4: Port the tests.**
  - Every `@HiltAndroidTest` becomes a plain test with `KoinTestRule`, which loads `appModules` plus
    the overrides before the activity starts.
  - `@TestInstallIn` modules become override modules, and a test that loads `FakeFirebaseModule`
    carries `@UsesFakeFirebase`.
  - The e2e branch of `FakeFirebaseModule` stays exactly as `CLAUDE.md` describes it.
- [ ] **Step 5: Verify locally where an SDK exists, then in CI.** `KoinGraphTest` and
  `ViewModelScopeTest` pass. All jobs are green, the 16 KB leg runs only non-`UsesFakeFirebase`
  tests, and `git grep -n "dagger\|javax.inject\|hilt"` → only historical prose in docs.
- [ ] **Step 6: Commit.** `refactor: replace Hilt with Koin (MON-24)`

### Task 3: Gson becomes kotlinx.serialization, byte for byte

Inventory: `git grep -l "com.google.gson" app/src`, which found 24 files on 1 November.

**Files:**
- Create:
  - `shared/src/commonMain/kotlin/com/coparently/app/domain/serialization/StoredJson.kt`;
  - `shared/src/commonTest/kotlin/com/coparently/app/domain/serialization/StoredJsonVectorsTest.kt`;
  - `shared/src/androidUnitTest/kotlin/com/coparently/app/domain/serialization/GsonParityTest.kt`.
- Modify:
  - the 24 Gson users and the models they reflect over (`@Serializable`, `@SerialName` where the
    Gson field name differs from the property);
  - `SerializationModule` (now Koin);
  - `R8GsonProbe` (renamed `R8JsonProbe`, same cases);
  - `tools/check-invariants.js` check 5, `tools/check-r8-probe.js` and `app/proguard-rules.pro`;
  - `CLAUDE.md` (the R8 paragraph, item 5 of "easy to get wrong").

**Interfaces:**
- Produces: `object StoredJson { fun <T> encode(serializer: KSerializer<T>, value: T): String; fun <T> decode(serializer: KSerializer<T>, text: String): T }`.
  It is the single JSON configuration for anything stored or sent (Room columns, drafts, the
  medical profile's field in `toFirestoreMap()`), and it reproduces Gson's defaults:
  - `encodeDefaults = true` and `explicitNulls = false`;
  - Gson's HTML escaping of `< > & = '`, applied as a post-pass on the encoded text;
  - `Double` printed as `Double.toString` prints it on the JVM, on both platforms;
  - `ignoreUnknownKeys = true` and lenient reading, so that everything Gson read today is still
    read.

- [ ] **Step 1: Write `GsonParityTest`.** For every model Gson reflects over today, a corpus of
  hand-made values. Cover:
  - nulls and defaults;
  - `<`, `>`, `&`, `=`, `'`, `"`, `\`, Cyrillic, emoji and control characters;
  - the doubles `0.0`, `-0.0`, `1.0`, `2.675`, `1.0E10` and `1.0E-5`;
  - empty lists and nested lists.

  Assert `StoredJson.encode(...) == Gson().toJson(...)`. Also assert that `StoredJson.decode` of
  Gson's output equals the value, and that it decodes every string in `app/src/test/resources/wire/`
  that today goes through Gson.
- [ ] **Step 2: Write `StoredJsonVectorsTest`** in commonTest: the same values, with the Gson
  output pasted as literal expected strings, so the iOS simulator is held to them.
- [ ] **Step 3: Run both** → FAIL (no `StoredJson`).
- [ ] **Step 4: Implement `StoredJson`** and switch the 24 users to it. Gson leaves the app's
  dependencies, apart from `testImplementation`, which `GsonParityTest` keeps.
- [ ] **Step 5: Update the R8 side.** Every Gson case in the probe becomes a `StoredJson` case with
  the same key assertions. `check-invariants.js` check 5 discovers `@Serializable` classes used
  through `StoredJson` instead of Gson-reflected ones. The keep rules for Gson models go; the
  serialization plugin's own rules ship with the library.
- [ ] **Step 6: Verify.** Both tests pass on JVM and on the iOS simulator. `CurrentWireFixturesTest`,
  `WireContractTest`, r8-runtime and upgrade are green.
- [ ] **Step 7: Commit.** `refactor: replace Gson with kotlinx.serialization, byte for byte (MON-24)`

### Task 4: Room generates Kotlin

Room KMP generates Kotlin only. `app/build.gradle.kts` keeps `room.generateKotlin = false` and
says switching is "a separate change with its own review". This task is that change, kept apart
from the move.

**Files:**
- Modify:
  - `app/build.gradle.kts` (remove the argument and its comment);
  - any DAO whose query result Room's Kotlin output types differently (nullability: a single-row
    query on an empty table, a nullable column read into a non-null field).

- [ ] **Step 1: Inventory.** Build with `room.generateKotlin = true`. List every compile error and
  every DAO function whose return type changed nullability.
- [ ] **Step 2: Pin each changed function with a test** in `app/src/test/.../data/local/dao/` (Room
  in-memory under Robolectric): the empty-table and null-column cases, asserting today's results.
  Each fails against the Kotlin output until the DAO's declared type matches what callers expect.
- [ ] **Step 3: Fix the declarations**, never the callers' handling of a value they never got
  before.
- [ ] **Step 4: Verify.** CI green. `git status --porcelain -- app/schemas` is empty (the identity
  hashes are unchanged), and so are the upgrade and migration tests.
- [ ] **Step 5: Commit.** `build: generate Room's Kotlin output (MON-24)`

### Task 5: `:shared-data`, and HTTP on Ktor

**Files:**
- Create:
  - `shared-data/build.gradle.kts` (targets as `:shared`; depends on `:shared`; Koin, Ktor,
    kotlinx-serialization, kotlinx-io);
  - `shared-data/src/commonMain/kotlin/com/coparently/app/data/http/HttpClients.kt`;
  - the Bakaláři client and school directory, moved from `data/school/bakalari/`;
  - `GoogleCalendarApi`, rewritten over Ktor against Calendar v3 `events.list`;
  - their tests, moved to commonTest and rewritten on Ktor's `MockEngine`.
- Modify:
  - `settings.gradle.kts`, `app/build.gradle.kts` (`implementation(project(":shared-data"))`,
    drop OkHttp, MockWebServer and `google-api-client`);
  - `tools/ci-changes.js` and its test (`shared-data/**` counts as `shared` and as an Android path,
    as `shared/**` does);
  - `tools/manual-test-plan.js`;
  - the CI jobs' Gradle task lists (`:shared-data:testDebugUnitTest`, the iOS compile and test).

**Interfaces:**
- Produces: `fun httpClient(): io.ktor.client.HttpClient`, an `expect` with OkHttp on Android and
  Darwin on iOS. It sets no logging of bodies (the school password, the OAuth tokens).
  `GoogleCalendarApi.listEvents(...)` keeps its signature and stays the only call
  (`GoogleCalendarAccessTest`, Legal item 10).

- [ ] **Step 1: Write the request-shape tests first.**
  - `BakalariRequestShapeTest` asserts the exact method, path, headers and form body of login
    (`client_id=ANDR`, `grant_type=password` and `refresh_token`), timetable and events.
  - `GoogleCalendarRequestShapeTest` asserts `events.list`'s path, query parameters (paging,
    `timeMin`, `singleEvents`) and the `Authorization` header.

  Take the expected values from today's OkHttp and Google-client requests: record them once with
  MockWebServer before deleting it, and paste them in as literals.
- [ ] **Step 2: Port the response tests** from MockWebServer to `MockEngine`, with the same JSON
  bodies and the same assertions.
- [ ] **Step 3: Implement** the Ktor clients. The refresh token's rotation and mutex
  (`BakalariAuthorizer`) are unchanged: store before use, and `invalid_grant` asks for the password
  again (CLAUDE.md item 35).
- [ ] **Step 4: Verify.** The tests pass on JVM and the iOS simulator, and CI is green.
  `GoogleCalendarAccessTest` passes: one public call, the read-only scope.
- [ ] **Step 5: Commit.** `refactor: move the school and calendar HTTP clients to Ktor in shared-data (MON-24)`

### Task 6: Platform services behind interfaces

The data layer's Android calls, by inventory (`grep -rlE '^import android\.' app/src/main/java/com/coparently/app/data`):
`Log` in 60 files, `Context` in 31 (files and cache directories, preferences), `Uri`,
`PackageManager`, notifications, `DateFormat.is24HourFormat`, `Base64`, Keystore.

**Files:**
- Create, in `shared-data/src/commonMain/kotlin/com/coparently/app/data/platform/`:
  - `Logger.kt`;
  - `AppDirectories.kt`;
  - `SecurePreferences.kt`;
  - `CrashReporter.kt`;
  - `Analytics.kt`;
  - `Connectivity.kt`.

  Each has an `androidMain` actual that wraps today's code, and an `iosMain` actual.
- Modify: every data-layer file that calls those Android APIs directly. `EncryptedPreferences`
  moves to `shared-data/src/androidMain` unchanged, as the Android `SecurePreferences`.
- Test:
  - `SecurePreferencesContractTest` (commonTest, run on both);
  - `KeychainSecurePreferencesTest` (iosTest).

**Interfaces:**
- Produces:
  - `interface Logger { fun e(tag: String, message: String, error: Throwable? = null); fun w(tag: String, message: String) }`;
  - `interface AppDirectories { val files: kotlinx.io.files.Path; val cache: kotlinx.io.files.Path; val noBackup: kotlinx.io.files.Path }`;
  - `interface SecurePreferences`, with the typed get, put and remove operations
    `EncryptedPreferences` exposes today, the same names, `clear()` with the same exemptions
    (school connections, slot markers), and `removeGoogleCalendarAccount()`;
  - `interface CrashReporter { fun record(error: Throwable) }`;
  - `interface Analytics { fun log(event: String, params: Map<String, Any>) }`, with
    `TelemetryConsentApplier` still the one switch (CLAUDE.md, REL-5);
  - `interface Connectivity { val online: kotlinx.coroutines.flow.StateFlow<Boolean> }`.

  On iOS:
  - `SecurePreferences` stores one sealed item in the Keychain, readable after the first unlock;
  - `AppDirectories` uses the app container, with `noBackup` excluded from backup;
  - `CrashReporter` and `Analytics` are no-ops that collect nothing, until phase 4 decides
    otherwise, which is the only honest answer while no consent screen exists there.

- [ ] **Step 1: Write `SecurePreferencesContractTest`.** Cover:
  - a round trip of every value type;
  - `clear()` keeping the exempt keys;
  - a store opened under another uid refusing a school connection (item 35);
  - a second instance over the same store seeing the first one's commit.
- [ ] **Step 2: Run it on both targets** → FAIL (no implementation).
- [ ] **Step 3: Implement the Android actuals** by delegation, so no stored byte moves. Implement
  the iOS actuals, then switch the data layer's call sites.
- [ ] **Step 4: Verify.** The contract test passes on JVM, under Robolectric with the real Android
  store, and on the iOS simulator. CI is green, `upgrade` included (the sealed store is read back).
- [ ] **Step 5: Commit.** `refactor: reach platform services through shared-data interfaces (MON-24)`

### Task 7: Room KMP in `:shared-data`

Decision: **the 41 existing migrations stay Android-only and unchanged.** An iPhone database is
always created at the current version, so it never runs them. Rewriting them for
`SQLiteConnection` would put proven code at risk for no reader. Migrations from version 47 on are
written in `commonMain` against `SQLiteConnection`.

**Files:**
- Move to `shared-data/src/commonMain/.../data/local/`: entities, DAOs, `Converters`,
  `CoPlanlyDatabase` (with `@ConstructedBy(CoPlanlyDatabaseConstructor::class)`).
- Move to `shared-data/src/androidMain/.../data/local/`:
  - `DatabaseMigrations.kt`;
  - `security/` (SQLCipher: `EncryptedDatabase`, `DatabaseKey`, `SqlCipherMigration`);
  - `buildCoPlanlyDatabase`, with its signature unchanged (the `upgrade` fixture calls it).
- Create:
  - `shared-data/src/iosMain/.../data/local/IosDatabase.kt` (`BundledSQLiteDriver`; the file and
    its `-wal`/`-shm` siblings created with `NSFileProtectionComplete`, spec §9.1);
  - `shared-data/src/iosTest/.../IosDatabaseTest.kt`;
  - `app/src/androidTest/.../ConnectionMigrationOnSqlCipherTest.kt`.
- Modify:
  - `shared-data/build.gradle.kts` (the Room Gradle plugin with
    `schemaDirectory("$rootDir/app/schemas")`, KSP for `kspAndroid`, `kspIosArm64` and
    `kspIosSimulatorArm64`);
  - `app/build.gradle.kts` (remove Room's KSP);
  - the Regenerate workflow (the schema task's new name);
  - `ci.yml`'s `app/schemas` porcelain step (unchanged path; confirm it still runs after the task
    that writes the schema);
  - `tools/ci-changes.js` (`shared-data/src/*/kotlin/com/coparently/app/data/local/**` counts as
    `data/local/` for the emulator matrix and `upgrade`).

**Interfaces:**
- Produces:
  - `expect object CoPlanlyDatabaseConstructor : RoomDatabaseConstructor<CoPlanlyDatabase>`;
  - Android: `fun buildCoPlanlyDatabase(...)`, unchanged;
  - iOS: `fun buildCoPlanlyDatabase(directories: AppDirectories): CoPlanlyDatabase`.

- [ ] **Step 1: Write `ConnectionMigrationOnSqlCipherTest`.** It opens an encrypted version-46
  database through `buildCoPlanlyDatabase`, adds a test-only `Migration(46, 47)` written against
  `SQLiteConnection` that creates a table, and opens it at 47. Assert:
  - the table exists;
  - the file is still ciphertext.

  This proves the path every future migration takes.
- [ ] **Step 2: Write `IosDatabaseTest`.** It opens a fresh database, round-trips one row in each
  table, and asserts `NSFileProtectionComplete` on the three files.
- [ ] **Step 3: Move and configure.** The schema JSON Room exports must be identical to
  `app/schemas/46.json`. Diff it, and treat any difference as a bug.
- [ ] **Step 4: Verify.** CI green, with the instrumented legs, `EncryptedDatabaseTest`,
  `CoPlanlyDatabaseMigrationTest`, `upgrade` and the porcelain step all clean. The iOS test passes
  on the hosted macOS job.
- [ ] **Step 5: Commit.** `refactor: move the Room database to shared-data on Room KMP (MON-24)`

### Task 8: Firebase and the data layer to `commonMain`

Branches on Task 1's `FIREBASE_CHOICE`:
- **GitLive:** the data sources call `dev.gitlive.firebase.*` directly from `commonMain`, and
  the Android `FirebaseApp` setup (named apps, emulator hosts, region) moves to `androidMain`.
- **`expect`/`actual`:** the interfaces Task 1 recorded live in
  `shared-data/src/commonMain/.../data/remote/firebase/`, the Android implementations wrap today's
  SDK calls, and the iOS implementations wrap the iOS SDK through cinterop.

Either way, no Firestore, Storage, Functions or Auth type escapes the `data/remote/` package into
a repository's public API.

**Files:**
- Move to `shared-data/src/commonMain/`:
  - `data/remote/`, `data/repository/`, `data/sync/` and `data/versions/`;
  - `data/chat/`, `data/family/`, `data/requests/`, `data/payment/` and `data/support/`;
  - `data/stickers/`, `data/documents/`, `data/files/`, `data/export/`'s pure part, `data/school/`
    and `data/session/`;
  - the remaining domain stay list that only waited on these (ROADMAP MON-24's list: the event use
    cases, `ErrorHandler`, `ActivityAnnouncer`).
- Keep in `app/`: `CoPlanlyMessagingService`, `PushNotifier`, every `Worker`,
  `CredentialManagerService`, `data/export/ExportFileWriter` (`PdfDocument`), `data/mlkit/` and
  `data/dictation/`. Each sits behind a `commonMain` interface whose iOS implementation reports
  unavailable.
- Move to `shared-data/src/commonTest/`: `WireContractTest`, `CurrentWireFixturesTest`, their
  contracts and every data-layer unit test that does not need Robolectric. The fixtures stay at
  `app/src/test/resources/wire/`, since the `upgrade` job copies that path. The tests read it
  through `kotlinx-io` from a path Gradle passes as a generated constant, on JVM and on the iOS
  simulator (spec §5.6).
- Modify:
  - `tools/check-e2e-coverage.js` (the new paths of `PushPayload.kt`), `tools/check-invariants.js`
    (push-type agreement), `tools/manual-test-plan.js`, `tools/ci-changes.js`;
  - the e2e `EmulatorParent` (it constructs the moved classes, unchanged in name and signature).

**Interfaces:**
- Consumes: Tasks 5–7.
- Produces: `shared-data`'s Koin modules, `val dataModules: List<Module>` (common) and
  `expect val platformDataModule: Module`. `appModules` includes both.

- [ ] **Step 1: Run the wire tests from commonTest on both targets before moving any writer.**
  They read the frozen fixtures. On iOS they fail to compile until the writers move; on JVM they
  pass. The failure list is this task's checklist.
- [ ] **Step 2: Write `FirestoreSemanticsTest`.** Unit level, over the seam:
  - a `Source.SERVER` failure sets `RecordSources.serverReached = false`;
  - a snapshot with pending writes keeps a change request unsynced;
  - `recordedAt` is written as the server timestamp sentinel;
  - a tombstone is written with `update()` of `deletedAtMillis` and `deletedBy` only.
- [ ] **Step 3: Move package by package**, in the order of the file list, building after each.
  Each Android call left in a moved file goes behind Task 6's interfaces or stays in `androidMain`.
- [ ] **Step 4: Verify.** The wire tests pass on JVM and the iOS simulator with **no fixture diff**.
  CI green, with e2e, upgrade and r8-runtime. On the owner's Mac (💻), or in the hosted job when the
  emulators run there, one iOS test signs in on the Auth emulator and reads an event written from
  JVM.
- [ ] **Step 5: Commit.** One commit per moved package group:
  `refactor: move <group> to shared-data (MON-24)`

### Task 9: `:shared-ui`, Compose Multiplatform and the resources

**Files:**
- Create:
  - `shared-ui/build.gradle.kts` (Compose Multiplatform plugin and the Compose compiler; the
    multiplatform Lifecycle ViewModel, Navigation, Coil 3 and kizitonwose artifacts;
    `compose.components.resources`; the iOS framework `baseName = "Shared"`, static, exporting
    `:shared` and `:shared-data`, replacing `:shared`'s framework);
  - `shared-ui/src/commonMain/composeResources/values*/` — the 65 string files moved with `git mv`
    from `app/src/main/res/values*/`, `font/` (Onest) and the drawables the screens use;
  - `tools/strings-to-android-res.gradle.kts`, a task generating
    `app/build/generated/res/strings/values*/` from those files, so Android-only code keeps
    `R.string` with Android's own semantics;
  - `app/src/test/.../resources/StringsParityTest.kt` and `PluralsParityTest.kt`.
- Move to `shared-ui/src/commonMain/`: `presentation/theme/` and `presentation/common/`, apart
  from the Android-only requesters (notification, microphone, camera permissions; activity-result
  launchers). Those stay in `app/` behind `commonMain` interfaces provided through
  CompositionLocals.
- Modify:
  - `tools/check-invariants.js` (locales and format arguments read from `composeResources`);
  - `res/xml/locales_config.xml` (unchanged locale list, verified against the new folders);
  - `app/config/detekt/detekt.yml` (the `ForbiddenImport` exclusion's `presentation/theme/` path);
  - `ci.yml` (`:shared-ui` tasks; `shared-ios` links `:shared-ui:linkDebugFrameworkIosSimulatorArm64`
    instead of `:shared`'s; screenshots unchanged);
  - `tools/ci-changes.js`, whose `shared-ui/**` screen and resource paths map like
    `presentation/` and `res/` today (no e2e for a screen-only change).

**Interfaces:**
- Produces: the CMP `Res` class in package `com.coparently.app.resources` with the same keys as
  today's `R.string`/`R.plurals`. `UiText` becomes
  `sealed interface UiText { Res(StringResource, args), Plural(PluralStringResource, count, args), Date(...), Raw(String) }`,
  with the same four cases and resolved through `asString()` in composition.

**Slice 9a notes (November 2026).** Done in slice 9a: the module, the string move, the generator,
both parity tests and Steps 1–3; Steps 4–6 are slice 9b.
- Versions: Compose Multiplatform **1.9.3**, not 1.10 (which lifts Compose past BOM 2025.10.00);
  its Android artifacts ask for androidx Compose runtime and foundation 1.9.4, one patch above the
  BOM. The multiplatform Lifecycle ViewModel, Navigation, Coil 3 and kizitonwose listed under Files
  are **not** added yet: each arrives with the task that first needs it in common code (Tasks
  10–11), rather than as an unused dependency now.
- The generator is the `stringsToAndroidRes` task in `app/build.gradle.kts`, not
  `tools/strings-to-android-res.gradle.kts`: a script applied with `apply(from = …)` cannot see the
  Android Gradle plugin's classes, which the `addGeneratedSourceDirectory` wiring needs (tried on a
  scratch build). Its output is `app/build/generated/res/stringsToAndroidRes/values*/`.
- 63 files per locale moved (every `*_strings.xml`, `strings.xml`, `navigation.xml`,
  `event_preview.xml`, `reminders.xml`), not 65: `colors.xml` and `themes.xml` stay.
- Source fixes (Step 3): `\'` → `\u0027` (126), `\"` → `\u0022` (2), and the straight quotes of
  `settings_language_description` → `\u0022`. The `%%` beside arguments in the three
  `expenses_split_shares_*` keys cannot be fixed in the text; `StringsParityTest` and check 7 of
  `tools/check-invariants.js` name them, and they need a new wording before Task 11 moves
  `SplitShares.kt`.

**Slice 9b notes (November 2026).** Step 4, in three commits after a test-only one:
- Every Robolectric test runs `RobolectricTestApplication` (`app/src/test/.../testing/`), which
  hands Compose Multiplatform's Android reader the context its `AndroidContextProvider` (a content
  provider) records on a device: Robolectric installs no provider from the manifest. Without it
  each `stringResource(Res.string.…)` a test composes throws.
- `presentation/theme/` moved whole. The Android-only parts are `expect`s with the code they were
  in `androidMain` (`SystemBarsAppearance`, `rememberSystemContrastLevel`, `rememberReducedMotion`,
  `adaptiveDimensions`), not CompositionLocals: none needs anything from the app, and each has an
  iPhone `actual`. The fonts are `Font(Res.font.…)`; `Typography` lost its typeface (the widget
  reads it outside composition) and the theme sets it in composition. The theme's tests moved to
  `shared-ui/src/androidUnitTest/`, which keeps `colorSchemeFor` and `ParentToneMath` internal.
  `ParentColorChoice.labelRes` became `label: StringResource`.
- Seventeen files of `presentation/common/` moved: the design system's primitives and their
  helpers. CLAUDE.md's "Where a `presentation/theme/…` … path lives" lists what stayed and why.
- Material icons: `org.jetbrains.compose.material:material-icons-extended` 1.7.3; the window size
  class: CMP's `material3-window-size-class` 1.9.0.
- Not done: `UiText` (slice 9c). Steps 5–6 wait for it; screenshots must verify unchanged on 9b's
  CI run already.

**Slice 9c notes (November 2026).** `UiText` in its Compose Multiplatform form, after
`ResolvedAppLocale` (the owner's option (a) for the two readers' fallback):
- `UiText.kt`, `ErrorText.kt` and `UiState.kt` moved to `shared-ui/src/commonMain/` (`UiState` on
  `kotlinx.io.IOException`). The interface had five cases, not the four this task's Interfaces
  lists: `Weekday` stays beside `Date`. Both carry kotlinx-datetime types and are worded by
  `rememberReaderDates`, an `expect` (Android: `java.time` in `LocalConfiguration`'s first locale,
  the code that was there; iPhone: `NSDateFormatter` in the current locale, Gregorian).
- `asString(context)` became `asString(resolver)`, with `rememberUiTextResolver()` taken in
  composition: Compose Multiplatform's suspending `getString`/`getPluralString` in the composition's
  `rememberResourceEnvironment()`, never the system environment, so a snackbar speaks the screen's
  language. Fourteen call sites in thirteen screens.
- Every `UiText.Res(R.string.x)` and `UiText.Plural(R.plurals.x)` in the app and its tests is
  `Res.string.x`/`Res.plurals.x`; `sharedFileError` and `ConnectSchoolViewModel.messageFor` take or
  return a `StringResource`. Arguments: every `%<n>$d` gets an `Int`, nothing else is formatted,
  and the three `%%` keys stay `R.string` in `SplitShares.kt`.
- `UiTextParityTest` (app, Robolectric, five languages) compares both resolutions with Android's
  resources and `java.time` for samples of every case and argument kind.

**Slice 9d notes (November 2026).** The owner's language rule (4 November 2026) in place of
`ResolvedAppLocale`: the phone's first language when the app ships it, otherwise English, a
per-app choice winning. The English fallback is European English, `en-150` (the owner's decision
on review finding I1); the phone's region is read from the phone (`DeviceRegion`).
- `shared/`'s `domain/language/AppLanguageRule` is the pure rule over BCP 47 tags, for the iPhone
  too (`AppLanguageRuleTest`, commonTest).
- The app's `utils/AppLocale.kt`: activities' base contexts in that locale before AppCompat
  (`MainActivity`, `QRScannerActivity`), `Context.inAppLanguage()` for every reader outside an
  activity (the messaging service's notifier, `RequestNotices`, the two reminder workers, the
  widget, `SchoolImportWordingSource`, `AndroidPairingWording`), the process's default locale set
  at the application's start, on each configuration change and in the activities, and a below-13
  mirror of the per-app choice. `AppLocaleTest` (Robolectric) and `ShippedLanguagesTest` hold it.
- The iPhone applies the same rule in phase 3, when its app decides its bundle's localizations.

- [ ] **Step 1: Write `StringsParityTest`** (Robolectric). For every key in every locale, the
  value from Android resources equals CMP's `getString` for that locale, with the same arguments.
  Use `"A"`/`7` per position and a key with `%%`.
- [ ] **Step 2: Write `PluralsParityTest`.** For every plural key and locale, counts 0–200 select
  the same text on both.
- [ ] **Step 3: Run both** against the moved files plus the generated Android resources → they
  fail wherever escapes or plural rules differ. Fix the **source text** so both read it the same
  way. Never fix one consumer.
- [ ] **Step 4: Move theme and common**, switching `stringResource(R.string.x)` to
  `stringResource(Res.string.x)` inside them.
- [ ] **Step 5: Verify.** Both parity tests pass. `check-invariants.js` reports five complete
  locales. Screenshots verify with **no baseline change** (fonts through CMP resources render the
  same Onest). The per-app locale instrumented test, `PushNotificationTest` and
  `TodayWidgetTextTest` pass.
- [ ] **Step 6: Commit.** `refactor: add shared-ui with Compose Multiplatform resources, theme and common UI (MON-24)`

### Task 10: The ViewModels to `commonMain`

Inventory: `git grep -l "ViewModel()" app/src/main/java/com/coparently/app/presentation`. The
ViewModels are about 57 of those files.

**Files:**
- Move each ViewModel to `shared-ui/src/commonMain/` beside where its screen will go, with its
  test to `commonTest` (or to `androidUnitTest` where it needs Robolectric or a JVM API).
- Modify: `viewModelOf` definitions move with them into `uiModules`.

**Interfaces:**
- Produces: `val uiModules: List<Module>` in `shared-ui`. `appModules` = `dataModules` +
  `platformDataModule` + `uiModules` + the app's own platform module.

- [ ] **Step 1: Move the ViewModel tests first** and run them on both targets. They fail to
  compile on iOS until their ViewModel moves; the list is the checklist.
- [ ] **Step 2: Move ViewModels in feature groups:** chat; calendar; custody and requests; family
  and expenses; settings, pairing and onboarding; everything else.
  - `@StringRes Int` becomes `StringResource`.
  - `Context` leaves every ViewModel (CLAUDE.md, CQ-14), because nothing platform-specific
    remains.
  - `SavedStateHandle`, `viewModelScope` and `StateFlow` are unchanged.
- [ ] **Step 3: Verify.** The ViewModel tests pass on JVM and the iOS simulator. `KoinGraphTest`,
  `ViewModelScopeTest` and CI are green.
- [ ] **Step 4: Commit.** One per group: `refactor: move the <group> ViewModels to shared-ui (MON-24)`

**Slice 10a notes (November 2026).** The infrastructure and the shared state first:
- `shared-ui` depends on `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` 2.9.6 (`api`;
  androidx 2.9.4 on Android, which lifts the app's lifecycle artifacts from 2.9.3) and
  `io.insert-koin:koin-core-viewmodel` 4.1.1; its tests get coroutines-test 1.11.0 and Turbine
  1.2.0 (commonTest) and MockK 1.13.13 (androidUnitTest).
- `di/koin/UiModules.kt`: `uiModules` (so far `commonUiModule`, the three sources' `single`s and the four common
  ViewModels, and `chatUiModule`) and
  `expect val platformUiModule` (empty on Android; stand-ins on the iPhone). `appModules` adds both
  after `dataModules` and `platformDataModule`.
- Moved from `presentation/common/`: `ParentsSource` (its log line through `logger`),
  `FamilyKindSource`, `FamilyMembersSource`, `CoParentOnScreen` (its three helpers public behind
  `@InternalSharedApi` for five ViewModels still in the app), `Loadable`, `FormDraft`,
  `NamedParent`, `ParentNames` (`Res.string`), `ParentLabels` and `PhotoOwner`. Tests:
  `ParentsTest`, `ParentLabelsTest`, `ParentNamesTest` and `FormDraftTest` to commonTest on
  `kotlin.test`; `ParentsSourceTest` and `FamilyKindSourceTest` (MockK) to androidUnitTest.
- `common/`'s ViewModels: `ConnectivityViewModel` (on `Connectivity.online`, which on Android is
  `NetworkMonitor.networkStatus` as a state; its test, with a fake `Connectivity`, in commonTest),
  `FamilySwitcherViewModel`, `FamilyViewViewModel` and `ParentPaletteViewModel` (tests in
  androidUnitTest, with a copy of `testParentsSource`).
- Chat: `ChatViewModel` (`SecurePreferences`, `wallClockNow`, `Uuid`), `SendHold`,
  `ChatAttachmentViewModel` (`SignedInUser`; `OpenedFile` and the thumbnails hold a `Path`, opened
  as `File(path.toString())` by `ChatAttachments` and `FamilyDocumentsScreen`),
  `ChatEventCardViewModel`, `ChatSearchViewModel`, `DictationViewModel`, `ReplySuggestionViewModel`
  with `ai/AiAssistSession`, and `SettleUpPaymentViewModel` (split out of `SettleUpPayment.kt`), in
  `chatUiModule`. `sharedFileError` moved to `common/SharedFileErrors.kt`; `AiConsentManager` to
  `:shared-data` (`aiDataModule`, its test to shared-data's androidUnitTest). The iPhone's
  `platformUiModule` binds `NoSpeechDictation`, `AiAssistAvailability(false)` and `NoAiAssist`.
  `DictationViewModelTest` and `SendHoldTest` run in commonTest (four test names lost a comma for
  Kotlin/Native); the other five chat tests (MockK) in androidUnitTest.
- Left for 10b–10f: every other ViewModel (calendar and event; custody and requests; family and
  expenses; settings, pairing, onboarding, auth and sync; the rest), and the app's copy of
  `testParentsSource` until its last user moves.

**Slice 10b notes (November 2026).** Calendar first:
- `CalendarViewModel` in `calendarUiModule`, on `SecurePreferences` (constructor parameter still
  named `encryptedPreferences`) and kotlinx-datetime: `displayedMonth`/`queryAnchorMonth` are
  `StateFlow<kotlinx.datetime.YearMonth>`, `selectedDate` a `StateFlow<kotlinx.datetime.LocalDate?>`,
  and `setSelectedDate`, `showMonth` and `getCustodyForDate` take them. Today is
  `Clock.System.todayIn(TimeZone.currentSystemDefault())`, a swap's id `Uuid.random()`, its `atIso`
  `IsoText.dateTimeShortest(wallClockNow())` (what `LocalDateTime.now().toString()` wrote).
- Moved with it because it reads them, converted to kotlinx-datetime: `CalendarViewMode.kt`
  (`CalendarViewMode`, `ParentFilter`), `CalendarSelection` (`monthsUntil` for
  `ChronoUnit.MONTHS.between`) and `CustodyHelper` (all five functions; `isoDayNumber`, `todayIn`).
- Screen edges: `CalendarScreen` reads the month and the selection through
  `collectAsJavaMonth`/`collectAsJavaDate` and converts back on every call (`showMonth`,
  `setSelectedDate`, `CalendarSelection.anchorDate`); `MonthView`, `DayWeekView` and `MiniMonth`
  call `CustodyHelper.isToday`/`isWeekend` with `toKotlinLocalDate()`; the screen's legacy lookup
  lost its `toJavaLocalDate()`.
- Tests: `CalendarSelectionTest` to commonTest (kotlinx-datetime); `CalendarViewModelTest`,
  `CalendarViewModelCustodyChangeTest` and `TestFriendRepository.kt` to androidUnitTest, mocking
  `SecurePreferences`, the first keeping `java.time` as its oracle at the ViewModel's edge.

Then the events:
- `EventViewModel` (with `EventDraft`, `EventQuery`, `EventUiState`, `EventOperation`) and
  `EventOwnerViewModel` in `eventUiModule`. `SecurePreferences` for the draft and the custom types
  (parameter still `encryptedPreferences`); the draft's write on the common `Dispatchers.IO`.
  `custodyParentOn` takes a kotlinx-datetime day; `AddEditEventScreen` converts its start date.
- `EventDraft` is `@Serializable`: `:shared-ui` applies `org.jetbrains.kotlin.plugin.serialization`,
  and `tools/check-invariants.js` scans `shared-ui/` in checks 4 and 5 (without it the model fell
  out of check 5's discovery; `tools/test/check-r8-probe.test.js` holds it).
- iPhone: `platformDataModule` (`:shared-data`'s, beside `NoStorageBucket`, since it is a data seam)
  binds `NoRecordPhotoStorage` (the domain's `RecordPhotoStorage`, which
  the app binds to `FirebaseImageStorage`): upload and delete throw `IOException`. A JVM `verify()` of
  `dataModules + uiModules` with the iPhone's bindings resolves, and fails without it.
- Tests: `EventViewModelTest` (Gson 2.11.0 added to `:shared-ui`'s androidUnitTest as the draft's
  oracle) and `EventOwnerViewModelTest` to androidUnitTest, with a copy of `testFamilyMembersSource`;
  the `autoEventOwner` assertions of the latter's no-schedule case moved to the app's
  `AddEditEventScreenTest`, since that function is the form's and stays in the app.
- Left for 10c–10f: custody and requests; family and expenses; settings, pairing, onboarding, auth
  and sync; the rest (among them `FriendCalendarViewModel` and `ProfessionalCalendarViewModel`).

**Slice 10c notes (November 2026).** Custody first:
- `CustodySetupViewModel` (shared with onboarding's schedule step), `SeasonalScheduleViewModel` and,
  from `pairing/`, `CustodyConflictViewModel`, in `custodyUiModule`, with what they read and nothing
  Android holds: `ChildScheduleForm.kt` (`toPatternModel` public for `CustodySetupScreen`'s preview;
  the rest stays `internal`), `ChildScope`, `ContactWindowDraft`, `SeasonalLayerDraft`,
  `PendingCustodyConflict` and `CustodyConflictResolver` (which `PairingViewModel`, still in the app,
  calls), and `parentingplan/PlanReferenceSource`. `PendingCustodyConflict` and `PlanReferenceSource`
  left the app's `presentationModule` for `custodyUiModule` as `single`s.
- Platform calls: `Log` → `logger`; `UUID` → `Uuid`; today (`java.time.LocalDate.now()`, in the
  form's default start and the seasonal ViewModel's year and suggestions) →
  `Clock.System.todayIn(TimeZone.currentSystemDefault())`. No public API was `java.time`, so no
  screen converts anything.
- `SavedStateHandle` (the plan question both editors are opened with): `:shared-ui` adds
  `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate` 2.9.6 (`api`), androidx's 2.9.4 on
  every target (which the app resolved already through `koin-core-viewmodel`), with
  `org.jetbrains.androidx.savedstate` 1.3.6, androidx.savedstate 1.3.3.
- Tests: `CandidateModelTest`, `ChildScheduleFormTest`, `ContactWindowDraftTest` and
  `CustodyConflictResolverTest` to commonTest on `kotlin.test` (seven names lost a comma);
  `CustodySetupViewModelTest`, `SeasonalScheduleViewModelTest` and `CustodyConflictViewModelTest`
  (MockK) to androidUnitTest, unchanged but for their location.

Then the requests:
- `ChangeRequestViewModel`, `RequestChangeViewModel` and `RequestsSource` (with `RequestsState`) in
  `requestsUiModule`; `RequestsSource` left the app's `presentationModule` (a `factory`). `Log` →
  `logger`, `UUID` → `Uuid`, `LocalDateTime.now().toString()` → `IsoText.dateTimeShortest(wallClockNow())`,
  `LocalDate.now()` → `Clock.System.todayIn(TimeZone.currentSystemDefault())`,
  `System.currentTimeMillis()` → `Clock.System.now().toEpochMilliseconds()`. `RequestsSource` keeps its
  `@InternalSharedApi` opt-in (the marker applies in its own module too).
- The inbox builds the mini months, so `calendar/MiniMonthModel.kt` moved, converted to
  kotlinx-datetime, and with it `calendar/EventsByDay.kt` (`eventsByDay` public for `CalendarScreen`).
  The custody lookups the inbox hands it are the resolver's own kotlinx lambdas, no longer wrapped.
- Screen edges: `MiniMonth` (kotlinx grid days; `toJavaYearMonth()`/`toJavaLocalDate()` to format;
  today through `toKotlinLocalDate()`), `ProposedSchedulePreview` and `SchedulePreviewMonth`
  (`toKotlinYearMonth()`, the model's kotlinx `getCustodyFor`), `CalendarScreen` (the swap sheet's days
  keyed by `SwapSides`' kotlinx days, custody through `toJavaLocalDate()`; `eventsByDay`'s index re-keyed
  to `java.time` once per list for `MonthView`), and the screenshot fixtures (`custodyOn`, a kotlinx
  day; `toKotlinYearMonth()`, `toKotlinLocalDate()`).
- Tests: `MiniMonthModelTest` and `SwapMiniMonthsTest` to commonTest, on kotlinx-datetime literals
  (two names lost a comma); `EventsByDayTest`, `ChangeRequestViewModelTest`, `ChangeRequestWithdrawTest`,
  `RequestChangeViewModelTest` and `RequestsSourceTest` to androidUnitTest, the first and second
  converting at the edge (`toKotlinLocalDate()`, `toJavaYearMonth()`), `java.time` still their oracle.
- Left for 10d–10f: family and expenses; settings, pairing, onboarding, auth and sync (pairing's
  `PairingViewModel` reads `PendingCustodyConflict` and `CustodyConflictResolver`, already moved); the
  rest.

**Slice 10d notes (November 2026).** The family first:
- `FamilyViewModel`, `EarlierFamiliesViewModel` (with the pure `EarlierFamilies.kt`) and
  `UnassignedRecordsViewModel` (`Log` → `logger`) in `familyUiModule`, with `home/MonthMoney.kt` and,
  split out of `HomeViewModel.kt` into `home/MonthSpend.kt`, `CurrencyAmount`, `MonthSpend` and
  `MonthSpendDependencies` (its `factory` left the app's `presentationModule` for `familyUiModule`;
  `HomeViewModel`, still in the app, resolves it from there).
- `EarlierFamily.lastDay` is a kotlinx-datetime `LocalDate` (it was `java.time`, made with
  `toJavaLocalDate()` from kotlinx dates); `EarlierFamiliesScreen` already formats it through
  `utils/LocalizedDates.kt`'s kotlinx `format(DateTimeFormatter)`, so it changed nothing.
- Tests: `EarlierFamiliesTest` and `MonthMoneyTest` to commonTest (kotlinx-datetime literals; four
  names lost a comma); `FamilyViewModelTest` and `UnassignedRecordsViewModelTest` (MockK) to
  androidUnitTest, with a copy of the app's `testFamilyViewSource` in `data/family/`.

Then expenses:
- `ExpenseViewModel` and `SettleUpViewModel` (with `SettleUpAsk`, `PendingSettleUp` and `SettleUpSend`,
  split out of `SettleUpDialog.kt` into `expenses/SettleUpViewModel.kt`) in `expensesUiModule`;
  `toggling` out of `FamilyMemberChips.kt` into `common/MemberToggling.kt`. `Log` → `logger` (tags
  kept), `UUID` → `Uuid`, `YearMonth.now()` → `Clock.System.todayIn(…).yearMonth`,
  `minusMonths(1)`/`plusMonths(1)` → `minusMonth()`/`plusMonth()`, the month filter
  `it.date.yearMonth == month`. Nothing about money changed.
- Screen edges: `ExpenseScreen` (`toJavaYearMonth()` for `monthPagingTransition` and the month label;
  the two `toKotlinYearMonth()` calls before the payments went), `SettleUpDialog`
  (`pending.month.toJavaYearMonth()` to format).
- iPhone: `platformDataModule` binds `NoReceiptText` (`ReceiptTextRecognizer`, a data seam beside
  `NoRecordPhotoStorage`): a scan throws `IOException`, which the form shows as a failed scan
  ("Couldn't read the receipt", `ReceiptScanState.Failed`).
  A JVM `verify()` of `dataModules + uiModules` with the iPhone's bindings resolves, and fails without
  it.
- Tests: the four `ExpenseViewModel*Test`s (MockK) to androidUnitTest, location only.

Then payments and child support:
- `PaymentsViewModel` (`paymentsUiModule`) and `SupportViewModel` (`supportUiModule`):
  `FirebaseAuthService` → `SignedInUser` (`getCurrentUid()`).
- `SupportReminderScheduler` moved out of the app's `SupportReminderWorker.kt` into
  `:shared-data`'s `data/support/` (same package; the app's WorkManager scheduler implements it).
  iPhone: `platformDataModule` binds `NoSupportReminderJob` (schedules nothing). The `verify()` of
  `dataModules + uiModules` with the iPhone's bindings resolves (152 definitions), and fails without
  it.
- `parseAmount`: `toBigDecimalOrNull()` + `MinorUnits.fromDecimal` → the new
  `DecimalText.parsePositiveAmount` in `:shared` (`signum() > 0 && scale() <= scale`, then exact minor
  units, null past a Long), with `DecimalTextParityTest` cases (corpus and 10 000 random texts) and
  `DecimalTextTest` vectors. `DecimalText`'s grammar moved to a file-private `DecimalGrammar`, so the
  object stays within detekt's `TooManyFunctions`.
- Tests: `SupportAmountTest` to commonTest (one name lost its commas). No other screen converts
  anything: both ViewModels were kotlinx-datetime already.

**Slice 10e notes (November 2026).** Settings first:
- `SettingsViewModel` and `CalendarFeedViewModel` in `settingsUiModule`. `FcmService` →
  `PushRegistration` (new, `:shared-data`'s `data/remote/firebase/`: `isPushEnabled`, `setPushEnabled`,
  `unregisterToken`; `FcmService` implements it), `AccountDeletionService` → `AccountDeletion` (new,
  `data/session/`, a `fun interface`; the service implements it), `AnalyticsManager` → `Analytics`,
  with `logNotificationsToggled` and `logThemeChanged` as extensions in `data/platform/AnalyticsEvents.kt`
  (same event names and parameters; the two members left `AnalyticsManager`), `java.io.IOException` →
  `kotlinx.io.IOException`. Parameter names unchanged. `CalendarFeedViewModel` moved unchanged.
- iPhone: `platformDataModule` binds `NoPushRegistration` (off; turning it on fails) and
  `NoAccountDeletion` (fails, wipes nothing). (Slice 10f, from the 10e review: `NoPushRegistration`
  and `NoPushDevice` became one object, `NoPush`, bound under both types, so phase 3 replaces push
  delivery and the token's unregistration together.)
- Tests: `SettingsViewModelTest` (the push mock a `PushRegistration`) and `CalendarFeedViewModelTest`
  (MockK) to androidUnitTest.

Then pairing and onboarding:
- `PairingViewModel` (`pairingUiModule`) with `PairingErrorText.kt`: `pairingMessageRes()` answers a
  `StringResource`, so `PairingFormState`'s `codeErrorRes`, `actionErrorRes` and `actionInfoRes` are
  `StringResource?`, and so are `FriendViewModel`'s and `ProfessionalsViewModel`'s `errorRes` and
  `FriendViewModel.saveError`, still in the app (10f), which call it. `Log` → `logger`,
  `AnalyticsManager.logInvitationAccepted()` → `Analytics.logInvitationAccepted()` (the member left).
- New seam `presentation/pairing/InviteQrCodes` (`:shared-ui`, `fun interface`, code →
  `ImageBitmap?`): the app's `AndroidInviteQrCodes` (`QRCodeService` over `pairingLinkFor`, so
  `BuildConfig.PAIRING_WEB_LINK` stays in the app; `presentationModule`). iPhone: `platformUiModule`
  binds `NoInviteQrCodes` (null).
- `OnboardingViewModel` (`onboardingUiModule`) with `OnboardingStep.kt`: `Log` → `logger`,
  `LocalDateTime.now().toString()` → `IsoText.dateTimeShortest(wallClockNow())`.
- Screen edges: `PairingScreen` (its snackbar through `rememberUiTextResolver`, the field's error
  through CMP `stringResource`), `InviteCodeCard` (takes the `ImageBitmap`), and the friends' and
  professionals' five error texts (CMP `stringResource`).
- Tests: `OnboardingStepTest` to commonTest (one name lost its commas); `PairingViewModelTest` and
  `OnboardingViewModelTest` (MockK) to androidUnitTest — the first verifies `InviteQrCodes.forInvite`
  and `Analytics.log("invitation_accepted", …)`, drops a dead `ValidationUtils` mock, and its
  `pairingLinkFor` check moved to the app's new `AndroidInviteQrCodesTest`; `FriendViewModelTest`
  and `ProfessionalsViewModelTest` compare `Res.string` where they compared `R.string`.

Then the session and the profile:
- `AuthStateViewModel` (`sessionUiModule`): `FirebaseAuthService` → `AuthSession` (new,
  `data/remote/firebase/`: `awaitSignedInUid` = `waitForAuthReady()?.uid`, `signOutCompletely`; the
  service implements it), `FcmService` → `PushRegistration`, `DeviceTraces` → `AccountTraces` (new,
  `data/session/`, `fun interface` `clearAfterSignOut`; `DeviceTraces` implements it), `Log` → `logger`.
  Each seam is one of sign-out's calls, so the order stays the ViewModel's. iPhone: `NoAuthSession`,
  `NoAccountTraces` in `platformDataModule`.
- `ProfileViewModel` (`profileUiModule`): `Log` → `logger`, `System.currentTimeMillis()` →
  `Clock.System.now().toEpochMilliseconds()`.
- Tests: `AuthStateViewModelTest` (its session mock answers `"u1"` where it answered a `FirebaseUser`)
  and `ProfileViewModelTest` (MockK) to androidUnitTest.
- **Left in the app, for Task 11**: `AuthViewModel` (every Credential Manager call takes the hosting
  `Activity`; `AuthError` maps `androidx.credentials` and `FirebaseAuthException` types) and
  `SyncViewModel` (Google Sign-In's `Intent` and `Task<GoogleSignInAccount>` result, through
  `CredentialManagerService`). See Task 11's carried notes.
- `@InternalSharedApi`: `coParentOnScreen`, `observeSignedInUser` and `projectedCoParentUid` stay
  public behind the marker for `HomeViewModel` and `ParentingPlanViewModel` (10f).

**Slice 10f notes (November 2026).** Home, the month in review, stickers and the consents first:
- `HomeViewModel` (with `HomeIdentityDependencies`, a `factory`) and `PutOffAsksViewModel` in
  `homeUiModule`, with `HomeFeed.kt`, `ChildrenToday.kt` and `RequestsRow.kt`. Today is
  `Clock.System.todayIn(TimeZone.currentSystemDefault())`, now `wallClockNow()`, the week's end
  `wallClockNow().plus(HomeWeek.DAYS, DateTimeUnit.DAY)` and the day's start `today.atTime(0, 0)`;
  `Log` → `logger`; "Later" in `SecurePreferences` (same key). `RequestsRowText.parts`/`relativeDay`
  take a kotlinx-datetime day (`daysUntil` for `ChronoUnit.DAYS.between`). `HomeAskQueue`,
  `SettleUpPosition` and the composables stay with the screen.
- `MonthReviewViewModel` (with `MonthReviewSources` and `MonthReviewAssist`, `factory`s) in
  `reviewUiModule`; `StickersViewModel` in `stickersUiModule`; `HealthConsentViewModel` (`Log` →
  `logger`) and `TelemetryConsentViewModel` in `consentUiModule`; `AiConsentViewModel` in
  `aiUiModule`. `HomeIdentityDependencies`, `MonthReviewSources` and `MonthReviewAssist` left the
  app's `presentationModule`.
- `data/consent/HealthConsentManager` (with `HealthConsentWithdrawal`) to `:shared-data`
  (`consentDataModule`; `System.currentTimeMillis()` → `Clock.System.now().toEpochMilliseconds()`),
  its test to `shared-data`'s `androidUnitTest`.
- Screen edge: `HomeScreen`'s Requests row passes `today.toKotlinLocalDate()`.
- Tests: `HomeFeedTest` and `ChildrenTodayTest` to commonTest (six names lost a comma); the seven
  ViewModel tests and `RequestsRowTextTest` (its `java.time` fixture converted at the call) to
  androidUnitTest.
- From the 10e review: the iPhone's `NoPushDevice` and `NoPushRegistration` are one `NoPush`, bound
  under `PushDevice`, `PushQueue` and `PushRegistration`; "replace together" notes on
  `NoAccountTraces` and `NoAccountDeletion`; `PlatformUiModule.android.kt` names `InviteQrCodes`;
  `pairingMessageRes`'s KDoc names its callers; `tools/manual-test-plan.js` maps `PushRegistration.kt`
  to §3.7 and `AnalyticsEvents.kt` to §2.2.

Then the family's records:
- `ChildInfoViewModel` (with `ChildFields`, `childInfoUiModule`) and `PetsViewModel` (with
  `PetFields`, `petsUiModule`): `FirebaseAuthService` → `SignedInUser` (`getCurrentUser()?.uid` →
  `getCurrentUid()`), `AnalyticsManager` → `Analytics` (the six record events as extensions in
  `data/platform/AnalyticsEvents.kt`; the members left), `CrashlyticsManager` → `CrashReporter`
  (`recordException` → `record`, `recordExceptionWithContext` → the new `recordWithContext`, which
  `CrashlyticsManager` overrides with its own), `Log` → `logger`; parameter names kept.
  `GuestInviteState.errorRes` and `guestMessageFor` → `StringResource`.
- `ContactsViewModel`, `FamilyDocumentsViewModel` (`SignedInUser`) and `JournalListViewModel`
  unchanged but for those; `JournalEditorViewModel`: `java.time.LocalDate` → kotlinx-datetime in
  `JournalEditorState` and `setDate`, `UUID` → `Uuid`, `System.currentTimeMillis()` →
  `Clock.System`, `Log` → `logger`. Each in its own module.
- Visibility: `ChildFields`, `PetFields` (were `internal`), `childForm`, `petForm` public for the
  editors in the app.
- Screen edges: `JournalEditorScreen` (`toJavaLocalDate()` into the picker, `toKotlinLocalDate()` out
  of it); `GuestInviteSheet` reads the error with CMP `stringResource`.
- Tests: the six to androidUnitTest (MockK); `PetsViewModelTest` verifies `Analytics.log("pet_updated",
  emptyMap())` and its uid answers a string where it answered a `FirebaseUser`.

### Task 11: The screens to `commonMain`

**Files:**
- Move `presentation/<feature>/` screens to `shared-ui/src/commonMain/`, in the same groups as
  Task 10.
- Each Android-only call in a screen goes behind an interface in
  `shared-ui/src/commonMain/.../presentation/platform/`. The Android implementations, kept in
  `app/`, are today's code. The calls, by inventory:
  - `LocalContext` (42 files);
  - activity-result launchers (10);
  - `android.*` imports (62): share sheet, file picker, camera and QR, the payment QR's share, the
    export's share intent, dictation, `Uri`.
- Screenshot tests stay in `app/src/test/.../screenshots/` and render the moved composables
  unchanged (spec §5.1 step 5).

**Interfaces:**
- Produces, in `presentation/platform/`, one interface per capability:
  - `ShareSheet`, `FilePicker`, `CameraCapture`, `QrScanner`;
  - `Dictation` (with `val available: Boolean`);
  - `PermissionRequester`, `OpenUrl`.

  Each is provided through a `staticCompositionLocalOf` by the shell. The iOS implementations of
  `CameraCapture`, `QrScanner` and `Dictation` answer `available = false` until phase 4, and the
  screens hide the affordance (CLAUDE.md design item 8, item 28).

**Carried from Task 10:**
- While the iPhone's `platformDataModule` binds `NoReceiptText` and `NoSupportReminderJob`, the
  shared screens must not offer a receipt scan or a child support reminder on the iPhone (design
  refresh item 8): a scan there always fails and a reminder is never scheduled. Hide both until the
  iPhone has the capability, as `CameraCapture` and `Dictation` are hidden.
- While `platformDataModule` binds `NoPush` (as `PushRegistration`), the shared Settings screen must
  not offer the push switch on the iPhone: turning it on always fails there, which reads as a
  network error.
- While `platformUiModule` binds `NoInviteQrCodes`, the shared pairing screen must not draw the QR
  frame on the iPhone (it would wait for a code that never comes); the code and the link are how an
  invitation leaves the phone there.
- `AuthViewModel` and `SyncViewModel` move with the auth and settings screens, once their sign-ins
  are platform interfaces the shell provides: a `CredentialSignIn` (Google's id token, a saved
  password, the save prompt — the Activity stays on Android's side of it) with `AuthError` mapped
  there from Android's and Firebase's exception types into the common type, and a
  `GoogleCalendarSignIn` launcher whose result is the connection outcome `SyncViewModel` words today.
  `AuthViewModel`'s ordering rules move unchanged: the save prompt before `onSuccess`, the `finally`
  that clears `isLoading`, cancellations rethrown. So does `SyncViewModel.signOut`'s use of
  `CredentialManagerService.signOut`, which clears the Google credential before the network revoke
  (CLAUDE.md item 23).

- [ ] **Step 1: For each group, run the screenshot tests and the group's instrumented tests**
  before the move. Record that they are green.
- [ ] **Step 2: Move the group.** Replace each Android call with its interface.
  `java.time` formatting goes through phase 1's seams and `utils/LocalizedDates.kt`'s
  `shortTime()` rule.
- [ ] **Step 3: Verify per group.** Screenshots verify with no baseline change, the group's
  instrumented tests are green, and `:shared-ui:compileKotlinIosSimulatorArm64` compiles.
- [ ] **Step 4: Commit.** One per group: `refactor: move the <group> screens to shared-ui (MON-24)`

### Task 12: Navigation in `commonMain`, and the thin Android shell

**Files:**
- Move to `shared-ui/src/commonMain/.../presentation/navigation/`: `NavGraph`, `Screen`,
  `BottomNavDestination`, `ReadablePane`, `TwoPane`, `CalendarTabTaps` and `navigateToTab`.
- `app/` keeps:
  - `CoPlanlyApplication` and `MainActivity` (the AppCompat requirement, the splash, the launch
    intent and push routing);
  - the manifest and `res/` (icons, `values-night`, `xml/`, generated strings);
  - the services, the workers and the widget;
  - the platform implementations from Tasks 8 and 11.
- Create: `app/src/test/.../ShellBoundaryTest.kt`.

**Interfaces:**
- Produces: `@Composable fun CoPlanlyApp(platform: PlatformServices)` in `shared-ui`, which
  `MainActivity` calls and phase 3's iPhone shell will call.
  `PlatformServices` bundles the Task 11 interfaces and the launch state (deep link, push
  destination and family).

- [ ] **Step 1: Write `ShellBoundaryTest`.** It fails if any file under `app/src/main/java/`
  declares a `@Composable` other than `MainActivity`'s `setContent` call and the Android
  implementations of `presentation/platform/`, or declares a `ViewModel`.
- [ ] **Step 2: Move the navigation.** `MainActivity.readLaunchIntent` still switches the family
  before arming any deep link (CLAUDE.md item 15). Insets are still applied once at the `NavHost`
  (design item 14).
- [ ] **Step 3: Verify.** `ShellBoundaryTest` passes. Every job is green: release with the
  baseline-profile check (the profile's package wildcards still reach the moved classes), r8-runtime,
  e2e, upgrade and all three instrumented legs. Run the UI tour workflow once and compare its gallery
  with the last one from `main`. **Expected: identical screens.**
- [ ] **Step 4: Commit.** `refactor: make the Android app a thin shell over shared-ui (MON-24)`

### Task 13: AGP 9 and Kotlin 2.4

`CLAUDE.md`'s toolchain line names two blockers for Kotlin 2.4: AGP 9, and Dagger 2.60. Task 2
removed Dagger, so only AGP 9 is left. ROADMAP MON-24 needs Kotlin 2.4 before phase 3. AGP 9 also
unblocks Ktor 3.6 and later, whose OkHttp 5.5 needs compileSdk 37 (Task 5 stayed on Ktor 3.5.1).

**Files:**
- Modify:
  - `build.gradle.kts`;
  - the three shared modules' build files (the KMP Android target moves to AGP 9's
    `com.android.kotlin.multiplatform.library` plugin);
  - `app/build.gradle.kts`;
  - Roborazzi and KSP versions;
  - Ktor (`shared-data/build.gradle.kts`, and `ktor-client-mock` in the app), with compileSdk 37;
  - `CLAUDE.md` (toolchain line, its Xcode note);
  - `docs/IOS-RUNNER.md` and the spec if the Xcode range changes.

- [ ] **Step 1: Choose versions.** Pick the newest stable Kotlin that has all of these:
  - a KSP release;
  - a Roborazzi release built with a Kotlin no newer than it;
  - a Compose Multiplatform release;
  - a Koin release;
  - support for the Xcode on GitHub's `macos-latest` image.

  Record the versions and the Xcode range in the pull request.
- [ ] **Step 2: Bump and migrate the KMP Android targets** to the AGP 9 plugin.
- [ ] **Step 3: Verify.** All jobs green, and screenshots verify with no baseline change. A
  renderer move goes through Regenerate and is stated in the pull request, as phase 1 Task 1 did.
- [ ] **Step 4: Commit.** `chore: move to AGP 9 and Kotlin 2.4 (MON-24)`

### Task 14: Record phase 2

**Files:**
- Modify:
  - `CLAUDE.md`: the architecture map and the "where a path lives" note, extended to `data/…` →
    `shared-data/src/{commonMain,androidMain}/…` and `presentation/…` → `shared-ui/src/commonMain/…`,
    with the list of what stays in `app/` and why. Also the hard rules: Koin instead of Hilt, and
    `composeResources` for strings in "Localization".
  - `docs/ROADMAP.md` MON-24: phase 2 done, what moved, what stayed and why, phase 3's start
    conditions.
  - The spec: §5.1 steps 3–6 marked done, with corrections where this plan departed (migrations
    kept Android-only; strings generated back into Android resources; three modules instead of one).

- [ ] **Step 1: Write the notes.** Check every `CLAUDE.md` path that moved with
  `git grep -n "presentation/\|data/" CLAUDE.md`. Each must resolve through the note or be updated.
- [ ] **Step 2: Verify.** `node tools/check-invariants.js` and `node --test tools/test/*.test.js`
  pass.
- [ ] **Step 3: Commit.** `docs: record phase 2 of the iPhone work (MON-24)`
