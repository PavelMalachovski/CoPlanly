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

Then the people outside the pair, and the plan:
- `FriendViewModel` and `FriendCalendarViewModel` (`friendsUiModule`), `GuestAcceptViewModel`
  (`guestsUiModule`; `errorRes`/`messageFor` → `StringResource`), `ProfessionalsViewModel`,
  `ProfessionalCalendarViewModel` and `ProfessionalPlanViewModel` (`professionalsUiModule`;
  `actionError` → `StringResource`) with `ProfessionalAgenda.kt`, `ParentingPlanViewModel`
  (`parentingPlanUiModule`; `CrashlyticsManager` → `CrashReporter`, `Log` → `logger`).
- kotlinx-datetime: `ProfessionalDay.date`, both calendars' window and
  `ProfessionalCalendarViewModel.start` (today `Clock.System.todayIn`, `plusDays`/`minusDays` → `plus`/`minus(n, DateTimeUnit.DAY)`,
  `isBefore`/`isAfter` → comparisons); `System.currentTimeMillis()` → `Clock.System`.
- Screen edges: `FriendCalendarScreen` and `ProfessionalCalendarScreen` format `day.date.toJavaLocalDate()`;
  `ProfessionalsScreen`'s snackbar through `rememberUiTextResolver` (its `LocalContext` went);
  `GuestAcceptScreen` CMP `stringResource`; `FriendScreenshots`' fixture day `toKotlinLocalDate()`.
- `@InternalSharedApi`: `coParentOnScreen`, `observeSignedInUser` and `projectedCoParentUid` are
  `internal` again (their last app caller moved), and the seven `:shared-ui` files that opted in for
  them no longer do.
- Tests: the seven to androidUnitTest, `java.time` still the calendars' oracle (converted at the
  edge), `Res.string` where they compared `R.string`, `CrashReporter.record` where they verified
  `recordException`. The app's `TestFamilyViewSource.kt` and `TestFamilyMembersSource.kt` were
  deleted (no user left); its `testParentsSource` stays for `FriendsScreenTest`.

Then the export and the school import:
- `ExportViewModel` (`exportUiModule`): new seam `data/export/RecordFileWriter` in `:shared-data`
  (`render`, `save`), with `ExportedFile` moved beside it (`uri` a `String`); the app's
  `ExportFileWriter` implements it (bound in `dataModule`) and keeps `PdfDocument` and the
  `FileProvider`, and the app reads the URI back with `ExportedFile.contentUri` (`ExportScreen`'s
  share intent, `ExportFileWriterTest`). `ExportUiState.from`/`to`, `setFrom`/`setTo` →
  kotlinx-datetime (`ZoneId.systemDefault()` → `TimeZone.currentSystemDefault()`, `minusMonths(3)` →
  `minus(3, DateTimeUnit.MONTH)`); `Log` → `logger`; `System.currentTimeMillis()` → `Clock.System`.
  iPhone: `platformDataModule` binds `NoRecordFiles` (render and save throw `IOException`).
- `ConnectSchoolViewModel` and `SchoolImportViewModel` (`schoolUiModule`): `lowercase(Locale.ROOT)` →
  `lowercase()`. iPhone: `platformDataModule` binds `BakalariSchoolDirectory` over
  `httpClient(HttpTimeouts.SCHOOL)`, as it binds `BakalariClient`.
- A JVM `verify()` of `dataModules + uiModules` with the iPhone's bindings resolves (185
  definitions), and fails without either new binding.
- Screen edges: `ExportScreen` (`toJavaLocalDate()` into the picker, `toKotlinLocalDate()` out,
  `UiText.Date(state.from)` directly; the share intent through `contentUri`).
- Tests: the three to androidUnitTest; `ExportViewModelTest` mocks `RecordFileWriter`, its files carry
  a URI string where they carried a mocked `Uri`, and its range assertions convert at the edge.
- **Task 10 is done but for `AuthViewModel` and `SyncViewModel`** (Task 11's carried notes).

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
- While `platformDataModule` binds `NoRecordFiles`, the shared export screen must not offer an
  export on the iPhone (it always fails there); say instead that the record is made on Android for
  now, or hide the row on the Family tab.
- While `platformUiModule` binds `NoInviteQrCodes`, the shared pairing screen must not draw the QR
  frame on the iPhone (it would wait for a code that never comes); the code and the link are how an
  invitation leaves the phone there.
- **For 11f: the iPhone cannot send an invitation yet.** `iosPlatformServices`' share sheet and
  clipboard are stand-ins (`available = false`), so on the iPhone the code and the link can only be
  read off the screen. The shared pairing screen hides Share and Copy there rather than offer them,
  and says nothing that assumes them; phase 3's `UIActivityViewController` and `UIPasteboard` give
  them back.
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

**Slice 11a notes (November 2026).** The foundation, in four groups.
- Group 1, the dependencies and the platform interfaces:
  - `:shared-ui` `commonMain` gains `koin-compose-viewmodel` 4.1.1, JetBrains'
    `lifecycle-runtime-compose` 2.9.6 and `org.jetbrains.compose.ui:ui-backhandler` 1.9.3 (POMs read
    on Maven Central: on Android the first asks for activity-compose 1.10.1 and
    lifecycle-viewmodel-compose 2.9.3, the second is androidx's 2.9.4, the third calls
    androidx.activity's handlers and asks for activity-compose 1.8.0; all already in the app's graph
    at those versions or above).
  - `presentation/platform/`: `PlatformCapability` (`available`) and `PlatformLauncher`; `ShareSheet`,
    `FilePicker`, `CameraCapture` (`CaptureTarget`, `CaptureResult`), `QrScanner`, `Dictation`,
    `PermissionRequester` (`PlatformPermission`, `PermissionRequest`), `OpenUrl`, `SensitiveClipboard`
    and `AppBuildValues`; `PlatformServices` (no defaults), `ProvidePlatform` and the locals, whose
    default throws; `PlatformLocals.all`.
  - Android: `ProvideAndroidPlatform`/`androidPlatformServices` in the app, from today's code;
    `MainActivity`, `QRScannerActivity` and the screenshot frame provide it. iPhone:
    `iosPlatformServices(buildValues)`, every capability unavailable.
  - Tests: `PlatformServicesTest` (commonTest), `AndroidPlatformTest` (app, Robolectric).
  - No screen reads the locals yet; each slice moves its screens onto them.
- Group 2, the date seam:
  - `utils/LocalizedDates.kt` → `:shared-ui` `androidMain`, same package and public names, so the app's
    callers (screens, widget, pushes, notices) compile unchanged. `ClockFormat` (now `follow(Boolean)`),
    `monthInFormatForm`, `unbreakableDate`, `DAY_IN_SENTENCE` and `DAY_WITH_WEEKDAY` →
    `commonMain` `utils/ClockFormat.kt`; `ClockFormat.follow(context)` and
    `ClockFormat.localeUses24Hour(locale)` stay Android extensions (`MainActivity` imports `follow`).
  - `utils/ReaderDateText` (`expect object`): `date(LocalDate, skeleton)`, `month(YearMonth,
    skeleton)` (the first day formatted, as the mini month and the month review do), `pattern(date
    or month, fixed pattern)` (the money screens' "LLLL yyyy", "EEE", "d MMMM"), `time(LocalTime or
    LocalDateTime)`, `dateWithTime`, `hour`, `answerBy`, `isoDate`. Android: those `java.time`
    functions in `Locale.getDefault()`, byte for byte by construction; `ReaderDateTextTest`
    (androidUnitTest) holds the delegation in six languages and both clocks (in Gradle's plain JVM
    `getBestDateTimePattern` has no ICU, so the skeleton cases fall back to the medium style on both
    sides; a device's patterns are not what it checks). iPhone: `NSDateFormatter` templates and fixed patterns in the
    current locale, Gregorian; `languageUses24Hour` from the `j` template.
- Group 3, the image seam:
  - `presentation/platform/PlatformImages` (`Image(model, contentDescription, modifier, contentScale,
    fullResolution, placeholder)`) and `AppImage`, provided as `PlatformServices.images`
    (`LocalPlatformImages`).
  - Android `CoilImages`: `SubcomposeAsyncImage` with the placeholder as `loading` and `error`;
    `ImageRequest` with `Size.ORIGINAL` and `crossfade(true)` for `fullResolution`; else `AsyncImage`.
    iPhone `NoImages`: a `Box` with the modifier and the placeholder.
  - Every `AsyncImage`/`SubcomposeAsyncImage` in the app's screens now calls `AppImage` with the same
    arguments: `AccountAvatar`, `ZoomableImage`, `PhotoStrip`, the event form and preview, the
    expense list and form, the chat's attachment thumbnail. No `coil` import is left outside
    `AndroidPlatform.kt`.
- Group 4, the common components the seams allow (`git mv`, packages unchanged, `R.string` →
  `Res.string`):
  - `common/AccountAvatar`, `PhotoStrip` (`PhotoStripStrings` → `StringResource`; its two callers,
    `childinfo/components/MedicalPhotoStrip`, moved too, and `AddEditPetScreen`, pass `Res.string`),
    `ZoomableImage`, `SignedInAsRow`, `ListSkeleton` with `components/SkeletonLoading`,
    `RecordPlaceMark`, `DiscardGuard` (`androidx.compose.ui.backhandler.PredictiveBackHandler`, opt-in
    `ExperimentalComposeUiApi`), `FamilySwitcher` (private `@StringRes` getters →
    `StringResource`), `TwoPane` (`internal expect fun windowWidthDp()`: Android
    `LocalConfiguration.current.screenWidthDp`, iPhone `LocalWindowInfo.containerSize`; the two
    widths and `WideColumn` public).
  - `NotificationPermission.kt` and `MicrophonePermission.kt` → one common `PermissionRequesters.kt`,
    the same two `remember…Requester` functions and requester classes over
    `LocalPermissionRequester`; `hasNotificationPermission`/`hasMicrophonePermission(context)` →
    the app's `common/AndroidPermissionChecks.kt`.
  - Left in the app, for the screens' slices: the `java.time` signatures (`AnswerByField`,
    `HolidayNames`, `MonthPaging`, `PickerDates`/`LocalDatePickerDialog`, `Today`/`rememberToday`,
    `VaccinationListEditor`, `components/TimePicker`), the `@StringRes` label helpers and their
    readers (`BloodTypeLabel`, `EventTypeLabel`, `PetSpeciesLabel`, `CountryPicker`,
    `MedicalProfileEditor`, `FamilyMemberChips`), the Android halves (`SharedFileViewer`,
    `SensitiveClipboard`, `PrivacyPolicyLink`, `TermsOfServiceLink`), `DesignSystemPreviews.kt`.
- For phase 3, from the 11a review:
  - **A wall-clock time in a daylight-saving gap prints an hour off on the iPhone.** The iOS
    `ReaderDateText` turns a kotlinx-datetime wall-clock value into an `NSDate` through the phone's
    zone (`dateFromComponents` with the system time zone) and formats it back in that zone, so a time
    that does not exist there — 02:30 on a spring-forward day — prints as "03:30", where `java.time`
    prints a `LocalDateTime` as it is. The fix: set the Gregorian calendar's and both formatters'
    `timeZone` to GMT, so a wall-clock value round-trips exactly, with a commonTest vector on a gap
    day. Nothing calls it on a device before phase 3.
  - `PlatformImages` says whether it is `available`; the iPhone's `NoImages` says no, so the photo
    strips leave themselves out. The event and receipt photos (11c, 11e) and the attachments' thumbnail
    (11b) read the same flag as they move.

**Slice 11b notes (November 2026).** Chat's screens, in two groups.
- Group 1, the seams chat needs beyond 11a's:
  - `presentation/platform/FileDetails` (a picked file's name and size, a size in the reader's
    words: Android's `ContentResolver` and `Formatter.formatShortFileSize` in the Activity's language;
    the iPhone's `NSByteCountFormatter`) and `QrCodes` (`encode` to a `QrMatrix`, `shareImage`:
    Android's ZXing and the PNG through the file provider, `AndroidQrCodes`, the settle-up code's
    code unchanged; none on the iPhone), both in `PlatformServices` and `PlatformLocals.all`.
  - `utils/ReaderMoneyText` (an `expect`): an amount as plain decimal text in the reader's
    conventions; Android `BigDecimal` and `currencyFormat`, which moved to `:shared-ui`'s `androidMain`
    (`presentation/expenses/CurrencyFormat.kt`, public, package unchanged); iPhone
    `NSNumberFormatter`.
  - `readerMediumDate` beside `ReaderDateText` (a thread's day separator, `FormatStyle.MEDIUM` on
    Android; a top-level `expect`, because the object is at detekt's function limit).
  - `CoilImages` maps a kotlinx-io `Path` to the `File` the attachment thumbnail handed Coil.
- Group 2, the screens (`git mv` to `:shared-ui` `commonMain`, packages unchanged, `R.string` →
  `Res.string`):
  - every file of `presentation/chat/`, with `event/EventPreviewSheet.kt` (a card opens the event's
    preview; `EventPreviewContent` and `eventTimeLine` public for the screenshots and the app's
    `FriendEventDetails`), `ai/AiConsentUi.kt`, `stickers/StickerText.kt` (`StringResource`s; the app's
    `MyStickersScreen` and `StickerViews` read them through CMP's `stringResource`) and
    `domain/model/MessageTemplate.kt` (`StringResource`s; the stay list is empty).
  - The Android calls, each behind a seam: the document picker (`FilePicker`, the entry hidden when
    unavailable), the picked file's name and size and the chip's size (`FileDetails`), a file in a
    viewer, the report's mail draft and the app's settings (`OpenUrl`; the report and the settings
    button hidden when unavailable), the support address (`AppBuildValues.supportEmail`, trimmed as
    before), the IBAN's copy (`SensitiveClipboard`, hidden when unavailable), the payment code
    (`QrCodes`), dictation (`Dictation.rememberLanguageTag`, and `available` beside the ViewModel's
    own), `BackHandler` (CMP's), the reply's language (Compose's `Locale.current`), a template's body
    (read in composition, so the sheet hands the text over), the report's text
    (`rememberUiTextResolver`), every date (`ReaderDateText`, `readerMediumDate`), every amount
    (`ReaderMoneyText`; `MinorUnits.fromText` as `DecimalText.parseHalfUp`, which it delegates to),
    and the thumbnail (`AppImage` over its `Path`, hidden when `PlatformImages` is unavailable).
  - `ActivityCardText.resourceFor` carries an explicit `@Suppress("CyclomaticComplexMethod")`: its
    signature changed with the `StringResource`, so the baseline entry keyed on the old one no
    longer matches (left for the next Regenerate). `MessageItem`'s two entries keep their key.
  - Left in the app: `presentation/chat/ChatPreviews.kt` (the three previews) and
    `presentation/chat/DictationLanguage.kt` (`rememberDictationLanguageTag`, which `AndroidDictation`
    answers with).
  - Tests: `ActivityCardTextTest` and `StickerTextTest` compare `Res.string`s; `ReaderMoneyTextTest`
    (androidUnitTest) holds the money seam to `currencyFormat`; `ReaderDateTextTest` holds
    `readerMediumDate` to `FormatStyle.MEDIUM`.

**Slice 11c notes (November 2026).** The calendar's and the events' screens.
- First, the 11b review's four notes: the event preview draws its photo only where
  `PlatformImages` is available (the iPhone's `NoImages` no longer leaves an empty box that opens a
  black viewer); a chat attachment is tappable only where `OpenUrl` is available, and stays as
  information otherwise; the iPhone's `ReaderMoneyText` writes an amount with no currency as
  `BigDecimal.toPlainString()` does (the scale kept: "12.50", where `NSDecimalNumber` gave "12.5"),
  and reads it in the POSIX locale, held on both platforms by `ReaderMoneyPlainTextTest`
  (commonTest); CLAUDE.md's screenshots paragraph no longer gives `ChatThreadHeader` as an
  `internal` example.
- Group 1, the helpers the calendar and the event form read (`git mv`, packages unchanged, onto
  kotlinx-datetime and `StringResource`): `Today` (with `LocalFixedToday`; the app's `JavaToday.kt`
  gave the screens still there `rememberJavaToday()` until round 2 deleted it), `PickerDates` with `LocalDatePickerDialog`
  (the app's `JavaPickerDates.kt` keeps `java.time` overloads that call the shared conversion),
  `components/TimePicker` (`ClockFormat.is24Hour` for the dial; `JavaTimePicker.kt` kept a `java.time`
  overload and the previews, until the overload went in 11d and the file became `TimePickerPreviews.kt`), `AnswerByField`, `HolidayNames` (`readerLanguage()`, an `expect`),
  `EventTypeLabel`, `FamilyMemberChips`, and `MONTH_PAGING_MS`/`monthPagingTween`
  (`MonthPagingTween.kt`; the direction and transition stay with Expenses for 11e). `TodayTest` →
  `:shared-ui` `commonTest`; `PickerDatesTest` reads `fromPickerMillisJava`; `HolidayNamesTest` and
  `EventTypeLabelTest` read the `StringResource`s' keys.
- Group 2, the event form: `event/AddEditEventScreen.kt` and `EventFormRows.kt` (`git mv`), its fields,
  helpers and saved state on kotlinx-datetime (`formStartDateTime`, `formEndDateTime`, `endDaySpanOf`,
  `endDaySpanFor`, `defaultStartTime`, `defaultEndTime`, and `plusHour()` for `plusHours(1)`; the saved
  dates and times as ISO text through `common/DateTimeSavers.kt`, never Java serialization, whose
  kotlinx-datetime proxy no R8 rule keeps — the round 1 review's I1), its text
  through `ReaderDateText`, the photo through `FilePicker` (hidden, with its preview, unless both
  `FilePicker` and `PlatformImages` are available), `Uuid`, `wallClockNow()`, `logger`; the strings its
  lambdas used are read in composition. `utils/ValidationUtils` moved with it (its unused email and URL
  checks, on Android's `Patterns`, left out; two detekt baseline entries,
  `MagicNumber:ValidationUtils.kt$ValidationUtils$254` and `TooManyFunctions:ValidationUtils.kt$ValidationUtils`,
  are stale since, and harmless: the next Regenerate run drops them; so are three more since the
  calendar's round 2 moved `CalendarHeader`'s preview, and its date `LocalDate.of(2026, 7, 20)`, to the
  app's `CalendarPreviews.kt` as a property: `MagicNumber:CalendarHeader.kt$20`,
  `MagicNumber:CalendarHeader.kt$2026` and `MagicNumber:CalendarHeader.kt$7`; the baseline is not
  edited by hand). `NavGraph` parses the date with
  kotlinx-datetime. Its two JVM tests run in `commonTest`, their names without commas.
- Round 1's review notes, done before the calendar: the form's saved state as text (above); the
  iPhone's `ReaderMoneyText` reads an amount whose scale does not fit an `Int` ("1e-2147483648") as
  nothing, as `BigDecimal` refuses it, where it threw (`ReaderMoneyPlainTextTest`); and the custody
  start date, the seasonal layer's range and the journal's day call the kotlinx `LocalDatePickerDialog`
  directly instead of converting through the app's `java.time` twin, which keeps its other callers
  (the export, expenses, the child records, the change request and two androidTest classes).
- **Left for 11c's next round: the calendar** *(superseded by "Round 2" below, which is what was
  done: `JavaToday.kt` was deleted rather than kept for 11d, and `SwapSelection`, `SwapTap`,
  `OpeningHour`, `GridCustody`, `ChildCustodyBand`, `swapMarks`, `drawSwapMark`, `LocalDateSetSaver` and
  `DayCellColors`' helpers stayed `internal`, their tests having moved with them; the list is kept as
  the round's plan)* (`presentation/calendar/` but the ViewModels and models
  Task 10 moved, and `calendar/components/`). Moving it piece by piece costs more than moving it at
  once: the helpers (`SwapSelection`, `SwapTap`, `SwapMark`, `OpeningHour`, `ChildCustodyBand`,
  `DateRangeHelper`, `DayCellColors`, `DayCellFills`, `EventMemberMark`) are called from about twenty
  places in `CalendarScreen`, `MonthView` and `DayWeekView` over `java.time` days, so a partial move
  would convert at each and convert back a commit later. Group 3 should move the calendar whole:
  - **kizitonwose:** take `com.kizitonwose.calendar:compose-multiplatform:2.9.1` in `:shared-ui`
    `commonMain`. Its API is kotlinx-datetime's `YearMonth`/`LocalDate`/`DayOfWeek` (it asks for
    kotlinx-datetime 0.7.1; every kotlinx-datetime member its desktop jar references exists in
    0.8.0's JVM jar, checked with `javap`), Compose Multiplatform 1.9.0 (on Android androidx Compose
    1.9.0, below the BOM's), kotlin-stdlib 2.2.20 and kotlinx-serialization 1.9.0. 2.10.x asks for
    Compose Multiplatform 1.10, which lifts Compose past the BOM: not allowed. Its packages are the
    Android artifact's (`com.kizitonwose.calendar.compose`, `.core`), so the same commit removes
    `com.kizitonwose.calendar:compose:2.6.1` from `app/build.gradle.kts`, or the classes clash.
    Between 2.6.1 and 2.9.1 `HorizontalCalendar`, `CalendarMonths` and the month data are the same
    layout (2.9.1 wraps each month in a `Box(Modifier.onPlaced(…))` and keys and saves months by
    their ISO text); `calendarScrollPaged`, `animateScrollToMonth`, `firstVisibleMonth` and
    `layoutInfo`, which `MonthView`'s own settle (design item 5) reads, keep their API. **The binary
    check was `javap` on the desktop (JVM) jar only**: it proves the Android link. Kotlin/Native links
    the iPhone's klib by signature, so a kotlinx-datetime 0.7 → 0.8 change it relies on would show only
    at `:shared-ui:linkDebugFrameworkIosSimulatorArm64`; the `shared-ios` job settles it.
  - **What the calendar reads from other features, each to move or put behind a seam:**
    `utils/SwapDateList.kt` (`swapDateList`, `java.util.Locale` → `ReaderDateText`);
    `parentingplan/PlanReferenceCard.kt`'s `planCitationShortLine` and `ParentNames.coParentLabel`;
    `custody/CustodyModelTypeLabel.kt`'s `labelRes` (`@StringRes`); `changerequests/SwapMonths`;
    `navigation/CalendarTabTaps` (Task 12) and `data/sync/SyncWorker` (WorkManager), which
    `CalendarScreen` should take as parameters or a seam; `utils.format`, `localizedDate`,
    `shortTime` and `hourLabel` → `ReaderDateText`; `DayOfWeek`/`Month.getDisplayName(TextStyle…)` →
    `ReaderDateText.pattern` with "EEEEE", "EEE" or "LLLL" (`getDisplayName` formats through
    `DateTimeFormatterBuilder.appendText`, the same text provider a pattern letter reads);
    `LocalConfiguration.locales[0]` (the swap bar and chips) → the reader's language;
    androidx.activity's `BackHandler` → Compose Multiplatform's; the previews to app files.
  - **Visibility:** what the app calls once the calendar is in `:shared-ui` — `DayCellColors`'
    `internal` helpers and constants, `SwapSelection`, `SwapTap`, `swapTapFor`, `LocalDateSetSaver`,
    `OpeningHour`, `GridCustody`, `ChildCustodyBand`, `swapMarks`, `drawSwapMark`, and, for
    `changerequests/` and `home/` (11d), `MiniMonth`, `DayAgendaCard`, `DaySwapSheet`,
    `SwapSheetOffer`, `SwapSheetActions`, `SwapSideGroup` and `isConsecutive`; and what the
    screenshots render (`MonthView`, `SwapDaysButton`, the banners, `CustodyLegend`,
    `DaySwapSelectionBar`, `DaySwapSheetContent`).
  - **`EventMemberMark.of`'s `locale` parameter can go:** the initial is upper-cased in
    `Locale.getDefault()`, which is always one of the five languages, none of which cases a letter
    differently from `uppercase()`; its first code point is a surrogate-pair check in common code.
  - **Tests to port** to `:shared-ui` (`commonTest`, names without commas): `CalendarQueryRangeTest`,
    `ChildCustodyBandTest`, `DayCellFillsTest`, `EventMemberMarkTest`, `OpeningHourTest`,
    `SwapMarkTest`, `SwapSelectionTest` (its `isConsecutive` case with `DaySwapSheet`), `SwapTapTest`,
    `TitleLinesTest` and `components/DaySwapStripStateTest`; and `CalendarScreenshots`,
    `HomeScreenshots` and `RequestScreenshots` convert their fixtures' days at the call.
  - **The app's calendar screens call `rememberJavaToday()`** (group 1) until they move; the moved
    calendar reads `rememberToday()`, and ~~`JavaToday.kt` stays for Home, custody and the requests
    until 11d~~ (deleted in round 2: Home, custody and the requests read `rememberToday()`).
- **Round 2, the calendar (done):** every file of `presentation/calendar/` and `calendar/components/`
  (`git mv`, packages unchanged; round 1's stashed helper conversion was the start of it, the stash
  dropped once committed), in one commit with the dependency switch: `:shared-ui` takes
  `com.kizitonwose.calendar:compose-multiplatform:2.9.1` and the app drops `compose:2.6.1`. On
  kotlinx-datetime throughout, `queryRangeFor` and the pager's month arithmetic included; the event
  blocks' minute arithmetic through `domain/time/WallClock.kt` (`plus`/`minus` in a unit,
  `durationUntil`), never kotlinx's own (it has none for a `LocalDateTime`); Week view's number
  `DateRangeHelper.isoWeekNumber`, held to `WeekFields.ISO` day by day from 1990 to 2060
  (`IsoWeekNumberTest`, `androidUnitTest`); the weekday and month names `ReaderDateText.pattern` with
  "EEE", "EEEEE" and "LLLL" over a date of that day or month. `BackHandler` is Compose
  Multiplatform's; the pull to refresh asks the data layer's `SyncRequester` (`koinInject`), which on
  Android is `SyncWorker.syncNow`, as the screen called before, and on the iPhone `NoSyncRequests`;
  the partial-swap snackbar resolves through `rememberUiTextResolver`. Moved with it: `CalendarTabTaps`
  (to `:shared-ui`'s `navigation/`, ahead of Task 12, the app's bar unchanged), `SwapMonths` (its own
  file), `PlanStrings` (a `StringResource` map) with `planCitationLine`, `planCitationShortLine` and
  `coParentLabel` (`PlanCitationText.kt`; `PlanReferenceCard` stays in the app), `CustodyModelType.
  labelRes()` (a `StringResource`), and `utils/SwapDays.kt` (`SWAP_DAY_SKELETON`, `swapDateList(dates)`;
  the app's `SwapDateList.kt` keeps the locale overloads for the push). The app's callers of those
  (custody, family, onboarding, the plan, professionals, the export, the requests) read them through
  Compose Multiplatform's `stringResource`. `EventMemberMark.of` lost its locale. `JavaToday.kt` is
  deleted (Home, custody's preview and the proposal preview read `rememberToday()` now);
  `JavaPickerDates.kt` and `JavaTimePicker.kt` stay, for expenses, the child records, the change
  request, the export and two androidTest classes *(`JavaTimePicker.kt`'s overload went in 11d, the file
  renamed `TimePickerPreviews.kt`; `JavaPickerDates.kt` is retired in 11e and 11g)*. The ten tests run in `commonTest`, their names
  without commas or parentheses; `CalendarScreenshots`, `HomeScreenshots` and `RequestScreenshots`
  convert their fixtures at the call. Public for the app: `MonthView`, `SwapDaysButton`,
  `DaySwapSelectionBar`, `DaySwapSheet`, `DaySwapSheetContent`, `SwapSheetOffer`, `SwapSheetActions`,
  `SwapSideGroup`, `isConsecutive`, `CustodyLegend`, `SwapMonths`; the rest stays `internal` (the
  plan's list above assumed the tests would stay in the app). The previews are the app's
  `calendar/CalendarPreviews.kt`. **Not yet proven:** the screenshots (the month grid is the most
  covered component; kizitonwose 2.9.1 wraps each month in an `onPlaced` box) and the iPhone's link of
  kizitonwose's klib against kotlinx-datetime 0.8 (`shared-ios`).

**Slice 11d notes (November 2026).** Custody and the requests.
- First, the 11c round 2 review's four notes (docs only): the three stale `MagicNumber:CalendarHeader.kt`
  baseline entries named beside the `ValidationUtils` ones, the pre-round calendar list marked
  superseded, ROADMAP's carried list striking the calendar's day model, and the architecture map's
  `presentation/` line saying which screens have moved.
- Group 1, custody's screens (`git mv`, packages unchanged, `R.string` → `Res.string`): every file of
  `presentation/custody/` (`ChildScheduleSection`, `ContactWindowsSection`, `CustodyDiffText`,
  `CustodyOverview`, `CustodySection`, `CustodySetupScreen`, `HolidayFairnessCard`,
  `SchedulePreviewMonth`, `SeasonalLayerDialog`, `SeasonalScheduleSection`), with
  `pairing/CustodyConflictScreen.kt` and `parentingplan/PlanReferenceCard.kt` (the custody editors
  quote it; it reads only `PlanStrings` and `Res`). The Android calls, each replaced:
  `DayOfWeek.getDisplayName(TextStyle.SHORT/FULL/NARROW, Locale.getDefault())` →
  `utils/WeekdayNames.kt`'s `DayOfWeek.readerName("EEE"/"EEEE"/"EEEEE")`, the pattern letters over a
  date of that day (both reach `DateTimeFormatterBuilder.appendText` with the same style; 189 names in
  nine locales compared on the JVM, 0 differences); `ofLocalizedDate(FormatStyle.MEDIUM)` →
  `readerMediumDate`, `SHORT` and `FULL` → the new `readerShortDate` and `readerFullDate` beside it
  (Android: the same formatter over `toJavaLocalDate()`; iPhone: Foundation's short and full styles,
  one private `styledDate` for the three); `format(shortTime())` → `ReaderDateText.time`;
  `format(localizedDate("MMMEd"))` → `ReaderDateText.date`; `getQuantityString` on `LocalContext` →
  CMP's `pluralStringResource` (`custodyDiffDescription`); `LocalDate.now()` → `Clock.System.todayIn`
  in the system zone; the conflict screen's fortnight on kotlinx-datetime (`plus(n, DAY)`, `day`);
  `@StringRes Int` → `StringResource`; androidx.activity's `BackHandler` → CMP's (opted in for
  `CustodySetupScreen.kt`'s file, so the baseline's `LongMethod` key on the screen's own `@OptIn` still
  matches). Public for the app: `CustodyPatternForm`, `CustodyPatternActions`, `createTempModel`,
  `custodyPreviewText` (onboarding) and `SchedulePreviewMonth` (onboarding and `FeatureScreenshots`).
  No app test sat in `presentation/custody/`.
- Group 2, the requests' screens (`git mv`, packages unchanged): `changerequests/`'s `AgreementRows`,
  `AwaitingEventCard`, `CardClock`, `ChangeRequestsScreen`, `DaySwapCard`, `FoldedMonth`, `InboxItems`,
  `ProposedSchedulePreview`, `RequestChangeScreen`, `RequestClockText`, `RequestHistoryGroup`,
  `RequestText`, `SwapDayAnswers` and `SwapOfferContent`. The Android calls, each replaced:
  `answerByText` → `ReaderDateText.answerBy` (Android: `answerByText` in `zone.toJavaZoneId()`, the
  system zone by default, as before); `format(localizedDate(…))`, `format(shortTime())` and
  `format(dateWithTime(…))` → `ReaderDateText.date`, `time` and `dateWithTime` (a `LocalDateTime`
  formatted with a date skeleton is its date's text); `ofLocalizedDate(MEDIUM)` → `readerMediumDate`;
  `swapDateList(dates, LocalConfiguration.current.locales[0])` → `swapDateList(dates)` (the reader's
  locale, as the calendar's swap bar since 11c); `RequestClockText.historyMeta`'s `ZoneId` → a
  kotlinx-datetime `TimeZone` (`Instant.fromEpochMilliseconds(…).toLocalDateTime(zone).date`);
  `@StringRes Int` → `StringResource` (`statusLabel`, `daySwapStatusLabel`, `swapAcceptLabel`, whose
  caller in the app, Home's `AwaitingDialogs`, reads it with CMP's `stringResource`); and
  `RequestChangeScreen`'s saved `java.time` fields → kotlinx-datetime with `LocalDateSaver`,
  `LocalTimeSaver` and a new `NullableLocalTimeSaver` (never Java serialization), `LocalDateTime(…)`
  for `LocalDateTime.of`, the event form's `plusHour()` for `plusHours(1)`, and
  `Clock.System.now().toEpochMilliseconds()` for `System.currentTimeMillis()`. Moved beside it, because
  the inbox's rows read them: `support/SupportText.kt` (`supportAmount` as
  `ReaderMoneyText.amount(DecimalText.plain(amountMinor, 2), currency)`, which on Android is
  `currencyFormat` over the same `BigDecimal`; `supportMonth` as `ReaderDateText.pattern(month,
  "LLLL yyyy")` with `titlecase()`, which equals `titlecase(Locale.getDefault())` in the five
  languages) out of the app's `SupportScreen.kt`, and `professionals/ProfessionalRoleLabel.kt`
  (`ProfessionalRole.labelRes()` a `StringResource`) out of `ProfessionalText.kt`, whose three callers
  now read it through CMP's `stringResource`. **Kept in the app: `changerequests/SplitShares.kt`**,
  whose three `%%` keys Compose Multiplatform would print as written (the brief gives their rewording
  to 11e). `ChangeRequestsScreen` takes `splitSharesText: @Composable (SplitRatio, ParentNames) ->
  String`, which `NavGraph` passes as the app's own and the screen provides to the split card through
  a file-private local (its default throws: nothing else draws the card); the screen's body is
  `InboxScreen`, unchanged. Public for the app: `AwaitingEventCard`, `SupportRequestRow`,
  `ProfessionalRequestRow`, `PlanRequestRow`, `RequestHistoryGroup`, `CardClockBanner`,
  `CardClockChip`, `CardClockDetails` (screenshots, `RequestHistoryGroupTest`), `SwapDayAnswers`,
  `SwapOfferSides`, `SwapNoteQuote`, `sidesAsked`, `swapOfferHeading`, `swapAcceptLabel`,
  `swapDatesText` (Home). Tests: `CardClockTest` and `InboxItemsTest` → `commonTest` (kotlin.test,
  kotlinx-datetime fixtures, `domain/time`'s `plus` for `plusDays`, names without commas);
  `RequestClockTextTest` → `androidUnitTest` (it holds the history line to `answerByText` in UTC);
  `RequestHistoryGroupTest` stays in the app. The java twins: `JavaTimePicker.kt`'s overload had no
  caller left and is gone (the previews are `components/TimePickerPreviews.kt`); `JavaPickerDates.kt`
  stays for `ExportScreen`, `AddExpenseScreen` (expenses left it in 11e), the child records' `DatePickerDialog`, `PickerDatesTest`
  and `LocalDatePickerDialogTest`.

**Slice 11e notes (November 2026).** The family's and the money's screens.
- Group 1, the `%%` keys: the three `expenses_split_shares_*` take the percent sign as their last
  argument (`%5$s` in `_named`, `%4$s` in the two "you" forms), from the new base-only
  `expenses_percent_sign` (`%`, `translatable="false"`), in all five languages, so the rendered text
  is what it was ("Alice pays 70 %, Bob pays 30 %", checked through Compose Multiplatform 1.9.3's own
  `replaceWithArgs` and `String.format`). A bare `%` with no arguments reads alike in both (aapt2 does
  not count a trailing `%`, Android's `getString` and CMP's `getString` return it as written). Check
  7 lost its `PERCENT_WITH_ARGUMENTS` exception, so `%%` beside arguments is refused in every key;
  `StringsParityTest` lost its special case and its pinned-difference test, which now holds the
  shares alike. The app's `SplitShares.kt` and the `InlineBanner` screenshot fixture pass the sign;
  no screenshot's text changes.
- Group 2, the family's screens (`git mv`, packages unchanged, `R.string` → `Res.string`), with the 11d
  review's two notes (the two `JavaTimePicker.kt` lines above marked as gone in 11d, and
  `WeekdayNamesTest`, `androidUnitTest`: `readerName("EEE"/"EEEE"/"EEEEE")` against
  `DayOfWeek.getDisplayName(SHORT/FULL/NARROW, Locale.getDefault())` for the seven days in en-150, en,
  cs, de, ru and uk, the format forms the custody screens used): `family/FamilyScreen.kt`,
  `EarlierFamiliesScreen.kt`, `UnassignedRecordsScreen.kt` and `UnassignedRecordsEntries.kt`; beside
  them, what they read from screens still in the app: `changerequests/SplitShares.kt` (`splitSharesText`
  public; `ChangeRequestsScreen`'s 11d parameter and local and `NavGraph`'s lambda gone, the screen's
  body back in `ChangeRequestsScreen`), `stickers/StickerViews.kt` (`roleOrNull`, `senderLine` public for
  `MyStickersScreen`), the new `settings/FamilyDialogs.kt` (`caresForSummary`, `FamilyKindDialog`,
  `SplitRatioDialog`, cut from `SettingsScreen.kt`, which did not call them), the new `home/MoneyText.kt`
  (`formatMoney`, `balanceCaption`, cut from `HomeScreen.kt`) and `home/SettleUpPosition.kt`
  (`SettleUpPositionTest` → `commonTest`, two names without commas). The money seam gained
  `ReaderMoneyText.amount(value: Double, currency: String)` (Android: `currencyFormat(currency).format(value)`,
  what `formatMoney` and the family screens' `currencyFormat(…).format(…)` were; iPhone: Foundation's
  currency style over an `NSNumber`) and `percent(fraction)` (Android: the default locale's
  `NumberFormat.getPercentInstance` with no fraction digits, for the analytics in group 3). Replaced:
  `utils.format(localizedDate(skeleton))` → `ReaderDateText.date`; the unassigned records' snackbar,
  worded with the Activity's `Context`, → a `UiText` resolved by `rememberUiTextResolver()`; `@StringRes
  Int` → `StringResource`. Public for the screenshots: `FamilyMoneyCard`.
- Group 3, the money's screens (`git mv`, packages unchanged): every file of `presentation/expenses/` but
  the two ViewModels (`AddExpenseScreen`, `CategoryPalette`, `CurrencySelector`, `ExpenseAnalytics`,
  `ExpenseCategoryLabel`, `ExpenseList`, `ExpenseScreen`, `ExpenseSummaryHeader`, `MonthSwipe`,
  `SettleUpDialog`) and `common/MonthPaging.kt` (on kotlinx-datetime's `YearMonth`, whose `compareTo`
  orders months as `java.time`'s; `monthPagingDirection` still coerced to one step). The Android calls,
  each replaced: `currencyFormat(currency)` held as a `NumberFormat` → `MoneyFormat(currency)` (new,
  `expenses/MoneyFormat.kt`, `format(Double)` and `format(plain text)` over the money seam, the same
  `NumberFormat` on Android); `currencyFormat(…).format(…)` → `ReaderMoneyText.amount(value, currency)`;
  `NumberFormat.getPercentInstance` → `ReaderMoneyText.percent`; `PaymentStrip.amount`'s `BigDecimal` →
  plain decimal text (`DecimalText.plain(minor, 2)`, which `BigDecimal(text)` reads back as the same
  value and scale); `MinorUnits.fromDouble` → `DecimalText.parseHalfUp(value.toString(), 2)` (the
  double's own text, half up, null past a Long, as `BigDecimal.valueOf` read it); "LLLL yyyy" titles →
  `utils/MonthTitle.kt`'s `readerMonthTitle` (which `supportMonth` now calls), and the settle-up
  question's untitled month → `ReaderDateText.pattern`; `localizedDate("MMMd")` → `ReaderDateText.date`;
  `ofLocalizedDate(MEDIUM)` → `readerMediumDate`; the form's `rememberSaveable` `java.time.LocalDate` →
  kotlinx-datetime with `LocalDateSaver`, its `Uri` receipt → text; `LocalDate.now()`/`YearMonth.now()` →
  `rememberToday()` (the other-month check compares `yearMonth`s); the photo picker and the camera
  (`TakePicture` into `cache/receipts/`, the CAMERA permission, `ActivityNotFoundException`) →
  `FilePicker.rememberImagePicker` and `CameraCapture.rememberCapture(CaptureTarget.RECEIPT)`, whose
  Android half is that code (`CaptureResult.NoCameraApp` and `PermissionDenied` show the form's two
  messages); `context.getString` in the scan's snackbars → strings read in composition; `@StringRes`
  → `StringResource`. **The carried note:** the receipt section shows only where `FilePicker` and
  `PlatformImages` are available, and a photo is scanned only where `ReceiptTextRecognizer.available`
  (new, `true` by default; `NoReceiptText` answers `false`; read through
  `ExpenseViewModel.receiptScanAvailable`). Public for the app: `CollapsedMonthSummary` (screenshots),
  `ReceiptScanEffect`, `ReceiptScanFormState`, `ReceiptScanCallbacks` (`ReceiptScanEffectTest`, whose
  form date is now a kotlinx-datetime day). The header's previews moved to the app's
  `expenses/ExpensePreviews.kt`. Tests to `commonTest`: `BuildReceiptScanUpdateTest`,
  `CategoryPaletteTest` (its hex through `toString(16)`, not `String.format`), `ExpenseSummaryChoiceTest`,
  `MonthSwipeTest`, `MonthPagingTest` (kotlinx-datetime fixtures, names without commas);
  `ReaderMoneyTextTest` holds the `Double` overload and the percent to `NumberFormat`.
  `JavaPickerDates.kt` keeps the export, the child records' picker and the two androidTest classes
  (11g).
- Group 4, child support's screen: `support/SupportScreen.kt` (`git mv`; `presentation/payments/` holds no
  screen, its strip and cards being the Expenses header's and chat's, both moved). Replaced:
  `LocalDate.now()`/`YearMonth.now()` → `Clock.System.todayIn` in the system zone (`.yearMonth`); the due
  day's `ofPattern("d MMMM")` → `ReaderDateText.pattern`; `MinorUnits.toDecimal(…).toPlainString()` →
  `DecimalText.plain(…, 2)`. **The carried note:** `SupportReminderScheduler` gains `available` (`true` by
  default; `NoSupportReminderJob` answers `false`), `SupportViewModel.remindersAvailable` reads it, and the
  screen's `SupportActions.setReminder` is null where it is false, so the payer's reminder group is not
  drawn. No visibility widened; no app test sat in `presentation/support/`.

**Slice 11f notes (November 2026).** Settings, pairing, onboarding, the sign-in and the sync.
- First, the 11e review's six notes: the camera's availability read once (`remember`), the receipt
  scan effect's KDoc, `caresForSummary`, `FamilyKindDialog`, `SplitRatioDialog` and `MoneyFormat`
  `internal`, and the two CLAUDE.md lines and this plan's 11d line marked.
- Group 1, Settings and `SyncViewModel` (`git mv`, packages unchanged, `R.string` → `Res.string`):
  `settings/SettingsScreen.kt`, `ParentColorDialog.kt` (`ParentColorPicker`, `CUSTOM_COLOUR` and
  `GridSwatch` public: the screenshots, `ParentColorPickerTest`, onboarding), `CalendarFeedScreen.kt`,
  `DataSourcesScreen.kt`, `RegionDialog.kt` and `AppLanguage.kt` (the enum alone, its labels
  `StringResource`s), with `common/CountryPicker.kt` (its labels `StringResource`s) and
  `consent/HealthConsentUi.kt` and `TelemetryConsentScreen.kt`; and `sync/SyncViewModel.kt`, in
  `settingsUiModule`. The Android calls, each replaced: `LocalUriHandler` with `PrivacyPolicyLink`,
  `TermsOfServiceLink` and the data sources' `openExternal` → `OpenUrl.open` over
  `AppBuildValues.privacyPolicyUrl`/`termsUrl` (a row drawn only while set and `OpenUrl.available`;
  `AndroidOpenUrl.open` is the `ACTION_VIEW` Compose's handler started, logged when no app takes it);
  `BuildConfig.VERSION_NAME` → `AppBuildValues.versionName`; `hasNotificationPermission(context)` →
  `PermissionRequester.isGranted(NOTIFICATIONS)`; the calendar link's `ACTION_SEND` chooser and
  `copySensitive(context, …)` → `ShareSheet.shareText` and `SensitiveClipboard.copySensitive`, each row
  offered only where available, the texts read in composition; `LocalConfiguration.locales[0].language`
  → `readerLanguage()`; `ofLocalizedDate(MEDIUM)` of an `Instant` in the system zone →
  `readerMediumDate` of kotlin.time's `Instant` in `TimeZone.currentSystemDefault()`; the consent's
  `localizedDate("yMMMd")` → `ReaderDateText.date`; `format(shortTime())` → `ReaderDateText.time`;
  `AppLanguage.current()`/`apply(language, context)` → the new platform interface `AppLanguages`
  (`PlatformServices.appLanguages`, `LocalAppLanguages`; Android `AndroidAppLanguages` in the app, the
  enum's old companion code; iPhone `NoAppLanguages`, not available); `@StringRes Int` →
  `StringResource`. **Carried note (push switch):** `PushRegistration.available` (`:shared-data`, true
  by default; `NoPush` false), `SettingsViewModel.pushAvailable`; the row is not drawn where false.
  **`SyncViewModel` behind `GoogleCalendarSignIn` (carried note):** the new
  `presentation/platform/GoogleCalendarSignIn` (`open(): GoogleSignInStart` — opened, unavailable,
  failed), `GoogleSignInAnswer` (`suspend complete(): GoogleSignInCompletion`, connected with Google's
  name and address, or failed with the `GoogleSignInFailure`) and `LocalGoogleCalendarSignIn`, which
  `MainActivity` alone provides (not part of `PlatformServices`: it needs the activity's result
  launcher and `CredentialManagerService`; read elsewhere it throws). Android:
  `presentation/sync/AndroidGoogleCalendarSignIn` (the sign-in client's intent, launched through the
  activity's result; a null client unavailable, a throwing one failed and logged) and
  `CredentialManagerService.signInAnswer(task)`, which the activity's result hands to
  `SyncViewModel.handleSignInResult` as before. `startGoogleSignIn(signIn)` replaces
  `createGoogleSignInIntent()`: "opening" first, then the client, as before. The new
  `data/remote/google/GoogleCalendarAccount` (`:shared-data`; `connection`, `accessToken`, `signOut`,
  `disconnect`) is what the ViewModel holds, `CredentialManagerService` answers it (its three methods
  `override`, `accessToken`'s default from the interface), the iPhone's `NoGoogleCalendar` too; the
  store is `SecurePreferences`. `signOut` is `CredentialManagerService.signOut`, unchanged (the store
  cleared before the revoke, CLAUDE.md item 23). Settings draws the Google Calendar row only where
  `GoogleCalendarSignIn.available`. `LocalGoogleSignInCallback` and `SettingsScreen`'s
  `onStartGoogleSignIn` are gone. Tests: `DataSourcesTest` → `commonTest` (keys, names without commas);
  `PlatformServicesTest` builds the new capability; `SyncViewModelTest` (stays in the app: it runs over
  the device's credential fixture) drives `startGoogleSignIn` through `AndroidGoogleCalendarSignIn` and
  `handleSignInResult` through `signInAnswer`, with two new cases (no client, a client that throws);
  `SyncStateTextTest` stays, so `toSyncState`/`toUiText` are public.
- Group 2, pairing and onboarding (`git mv`, packages unchanged, `R.string` → `Res.string`):
  `pairing/PairingScreen.kt`, `InvitationView.kt` (public: `InvitationViewTest`, the screenshots),
  `PairingLink.kt` and `components/` (`InviteCodeCard`, `IncomingInviteCard`, `PairedPartnerCard`,
  `CodeEntryField`), and `onboarding/OnboardingScreen.kt`. The Android calls, each replaced: the
  invitation's `ACTION_SEND` chooser with the Activity's `getString` → `ShareSheet.shareText` with the
  message and title read through `rememberUiTextResolver` in a coroutine at the tap (CMP reads a
  string outside composition only as a `suspend`); `copySensitive(context, …)` →
  `SensitiveClipboard`; the scanner's `StartActivityForResult` of `QRScannerActivity` →
  `QrScanner.rememberScanner` (`AndroidQrScanner` is that launcher; the helper answers null where
  `QrScanner.available` is false, kept out of `PairingScreen` so its cyclomatic count stays at the
  threshold); `BuildConfig.PAIRING_WEB_LINK` → `AppBuildValues.pairingWebLink`, with
  `pairingLinkFor(code, web)` now taking it (no default) and the app's new `BuildPairingLink.kt` keeping
  `pairingLinkFor(code)` over `BuildConfig` for `AndroidInviteQrCodes` and `PairingLinkTest`;
  `PLAY_STORE_URL`/`playStoreUrlFor` public (the app's friend and professional invitations); the
  countdown's `System.currentTimeMillis()`/`TimeUnit` → `Clock.System` and `Duration.inWholeHours`/
  `inWholeMinutes` (both truncate, as `TimeUnit` did); the paired-since day's `ofLocalizedDate(MEDIUM)`
  → `readerMediumDate`; androidx.activity's `BackHandler` → CMP's (opted in); the onboarding's
  `hasNotificationPermission(context)` → `PermissionRequester.isGranted`; `@StringRes Int` →
  `StringResource`. **Carried notes:** `InviteQrCodes.available` (true by default; `NoInviteQrCodes` is
  an object answering false), `PairingViewModel.qrAvailable`, and `InviteCodeCard(qrAvailable, onCopy?,
  onShare?)`: no QR frame, no Share, and no copy glyph or tap (the line under the code says only how
  long it lasts) where unavailable; the scan button and the onboarding's notification button only
  where their capability is. Kept in the app: `QRScannerActivity`, `QrScannerScreen` (CameraX, ML
  Kit), `AndroidInviteQrCodes`.
- Group 3, the sign-in and the profile, with `AuthViewModel` (carried note): `auth/AuthScreen.kt`,
  `AuthLegalNotice.kt`, `AuthError.kt` (the type, with an empty companion), `AuthErrorText.kt`
  (`messageRes()` a `StringResource`), `AuthViewModel.kt` (`authUiModule`) and `profile/ProfileScreen.kt`.
  The seams: **`CredentialSignIn`** (`presentation/platform/`, `googleIdToken`, `savedPassword` →
  `SavedCredential`, `offerToSave`; `LocalCredentialSignIn`, provided by `MainActivity` alone,
  `AndroidCredentialSignIn(activity, CredentialAuthenticator)`; read elsewhere it throws), passed to
  `signIn`/`signUp`/`signInWithGoogle`/`signInWithSavedPassword` where the Activity was;
  **`AccountSignIn`** (`:shared-data` `data/remote/firebase/`, the four Firebase Auth calls;
  `FirebaseAuthService` overrides them covariantly, `Result<FirebaseUser>` for `Result<Any>`, checked
  with kotlinc; `NoAccountSignIn` on the iPhone); **`AuthErrors`** (`presentation/auth/`, the three
  mappings; `AndroidAuthErrors` in the app, the companion's code as `AuthError.Companion` extensions,
  bound in `presentationModule`; `NoAuthErrors` on the iPhone, `platformUiModule`) — a Koin seam, not
  part of `CredentialSignIn`, because `sendPasswordReset` takes no sheet. `AnalyticsManager.logLogin`/
  `logSignUp` → `data/platform/AnalyticsEvents.kt`'s `logLogin`/`logSignUp` ("login"/"sign_up" with
  "method": Firebase's `Event.LOGIN`, `Event.SIGN_UP`, `Param.METHOD`); `recordExceptionWithContext` →
  `CrashReporter.recordWithContext`. The order is the old code's line for line (the diff of the
  ViewModel is the seam names). The screen: `LocalContext.findActivity()` → `LocalCredentialSignIn`
  (the Google and saved-password buttons only where it is available); `AuthLegalNotice`'s
  `PrivacyPolicyLink`/`TermsOfServiceLink` → `AppBuildValues` and `OpenUrl` (the terms clause too only
  with its link), and the two objects deleted. The profile: `format(ofLocalizedDate(MEDIUM))` →
  `readerMediumDate`, the child records' `java.time` `DatePickerDialog` → the kotlinx
  `LocalDatePickerDialog` with `childinfo_ok`/`childinfo_cancel` (today where unset, as it was). The
  app's `viewModelModule` is deleted. Tests: `AuthViewModelTest` (stays in the app) builds the
  ViewModel with `AndroidAuthErrors` and calls it with `AndroidCredentialSignIn(activity,
  credentialAuthenticator)`, verifying `recordWithContext`; `AuthErrorTest` unchanged. **Task 11 after
  11f:** what is left is 11g's — the child and pet records, contacts, documents, the export, the journal,
  friends, guests, professionals, the parenting plan, Home, the month review, the stickers' screen,
  the school import and `common/`'s last helpers.

**Slice 11g notes (November 2026).** Everything left, the last slice of Task 11.
- Group 1, Home, the month in review and the stickers' shelf (`git mv`, packages unchanged, `R.` →
  `Res.`): `home/HomeScreen.kt` (`HandoverHero`, `StatTiles`, `TimelineRow` public: the screenshots),
  `AwaitingDialogs.kt`, `ChildrenTodayLines.kt`, `HomeAskQueue.kt` (its test to `commonTest`, two
  names without commas, `kotlin.test.Test`), `review/MonthReviewScreen.kt`,
  `stickers/MyStickersScreen.kt`. Replaced: `format(localizedDate(skeleton))` →
  `ReaderDateText.date`/`month`, the feed's date with time → `ReaderDateText.dateWithTime`,
  `currencyFormat(currency).format(x)` → `ReaderMoneyText.amount(x, currency)`,
  `LocalConfiguration.locales[0].toLanguageTag()` → Compose's `Locale.current.toLanguageTag()`,
  `uppercase(locale)` → `uppercase()`, `ActivityKind.labelRes()` and `OutcomeChip`'s label a
  `StringResource`. The two previews → the app's `home/HomePreviews.kt`.
- Group 2, the family's records: `childinfo/ChildInfoScreen.kt`, `ChildDetailScreen.kt`,
  `AddEditChildInfoScreen.kt`, `components/` (the editors, `GuestInviteSheet`, `DatePickerDialog`);
  `pets/PetsScreen.kt`, `AddEditPetScreen.kt`; `contacts/ContactsScreen.kt`; `guests/GuestAcceptScreen.kt`;
  `common/MedicalProfileEditor.kt`, `VaccinationListEditor.kt`, `BloodTypeLabel.kt`, `PetSpeciesLabel.kt`
  (`labelRes()` a `StringResource`; `BloodTypeLabelTest` to `commonTest`). Replaced: the
  `PickVisualMedia` launchers → `FilePicker.rememberImagePicker` (add only where available);
  `UUID.randomUUID()` → `Uuid.random()`; `ofLocalizedDate(MEDIUM)` → `readerMediumDate`;
  `ofLocalizedDate(LONG)` of an instant in the system zone → the new `readerLongDateAt` over the new
  `readerLongDate` expect (Android `FormatStyle.LONG`, iPhone `NSDateFormatterLongStyle`;
  `ReaderDateTextTest` holds it); `localizedDate("yMMMd")` → `ReaderDateText.date`; the dialler intent
  → `OpenUrl.dial`; `guestShareIntent` → `ShareSheet.shareText` (text worded at the tap through
  `rememberUiTextResolver`; `GuestInviteSheet.onShare` nullable, no button where unavailable). The
  child/pet `DatePickerDialog` is kotlinx-datetime now (`LocalDatePickerDialogTest` and
  `PickerDatesTest` convert their `java.time` fixtures at the call).
- Group 3, the vault, the export and the journal: `documents/FamilyDocumentsScreen.kt`,
  `FamilyDocumentsContent.kt`, `export/ExportScreen.kt`, `journal/JournalListScreen.kt`,
  `JournalEditorScreen.kt`. Replaced: the vault's `OpenDocument`/`TakePicture`/`RequestPermission`
  launchers → `FilePicker.rememberDocumentPicker` and `CameraCapture.rememberCapture(SHARED_FILE)`
  (each button only where available; `NoCameraApp` → the same message), `openSharedFile` →
  `OpenUrl.openFile`, `Formatter.formatShortFileSize` → `FileDetails.formatSize`, the row's date →
  `readerMediumDate`; the export's `Intent.createChooser(recordShareIntent(file))` →
  `ShareSheet.shareFile` (its Android half now builds through `fileShareIntent`, which the app's
  `recordShareIntent`, kept for `ExportFileWriterTest`, also calls), its pickers kotlinx without
  conversion. Carried note: `RecordFileWriter.available` (default true; `NoRecordFiles` false),
  `ExportViewModel.exportAvailable`; no format rows where it or the share sheet is unavailable.
  `JavaPickerDates.kt` deleted (no caller left).
- Group 4, the people outside the pair and the plan: every file of `friends/`, `professionals/` and
  `parentingplan/` still in the app (screens, sheets, `GuestDurationText.kt` with its test to
  `commonTest`, `ProfessionalText.kt`). Replaced: `ofLocalizedDate(FULL)` → `readerFullDate`,
  `format(shortTime())` → `ReaderDateText.time`, `ofLocalizedDate(MEDIUM)` of an instant → the new
  `readerMediumDateAt`, `System.currentTimeMillis()` → `Clock.System`, every `@StringRes Int` →
  `StringResource`, the friend's and professional's share intents → `ShareSheet.shareText` (worded at
  the tap; no Share where unavailable). Public for the screenshots: `FriendAgendaDay`,
  `FriendComparisonTable`, `FriendEventDetails`, `FriendInviteContent`.
- Group 5, the school import: `school/ConnectSchoolScreen.kt` (`androidx.activity`'s `BackHandler` →
  Compose Multiplatform's, `ExperimentalComposeUiApi` opted in), `SchoolImportScreen.kt` (the last
  import's `Instant…format(dateWithTime(…))` → `ReaderDateText.dateWithTime`). **Task 11 done**: the
  app's `presentation/` holds `MainActivity`, `widget/`, `navigation/`, the previews and the Android
  halves of the platform interfaces (`RecordShareIntent.kt` new, for `ExportFileWriterTest`).

### Task 12: Navigation in `commonMain`, and the thin Android shell

**Files:**
- Move to `shared-ui/src/commonMain/.../presentation/navigation/`: `NavGraph`, `Screen`,
  `BottomNavDestination`, `ReadablePane`, `CalendarTabTaps` and `navigateToTab` (`TwoPane` moved to
  `presentation/common/` in slice 11a).
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
