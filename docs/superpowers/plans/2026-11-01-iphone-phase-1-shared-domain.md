# iPhone phase 1: the domain in a shared Kotlin module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move CoPlanly's domain layer into a Kotlin Multiplatform module (`:shared`). The module
compiles for Android and iOS. The Android app keeps doing exactly what it does today, and every
document it writes stays byte for byte the same.

**Architecture:** A new Gradle module, `:shared`, with an Android library target and two iOS
targets. Domain files move into `shared/src/commonMain` **with their package names unchanged**,
so no import in the app has to change because of a move. Only five kinds of change edit the app:
- a type that leaves `java.time`;
- a leak out of the domain that is cut (Task 3);
- a moved file's test;
- Task 1's toolchain bump;
- Task 2's dependency on `:shared`.

JVM-only APIs in the domain are replaced by small `expect`/`actual` seams or pure-Kotlin
equivalents, each held to the JVM original by a parity test. `java.time` becomes `kotlinx-datetime`
in the domain. App code that touches a moved type switches with it, and `java.time` stays only at
Android edges: Room converters, display formatting, pickers and WorkManager.

**Tech Stack:** Kotlin Multiplatform (Android + `iosArm64` + `iosSimulatorArm64`), kotlinx-datetime
(≥ 0.7, `kotlin.time.Instant`), kotlinx-coroutines-core, kotlin.test, Gradle (Kotlin DSL, no version
catalog), GitHub Actions with the owner's Mac as a self-hosted runner.

**Spec:** `docs/superpowers/specs/2026-10-27-iphone-app-design.md` (§5.1 steps 1–2, §5.6, §9.3).
Read it, and the parts of `CLAUDE.md` each task names, before starting.

**Start gate.** Do not start before the Android app is released and its first feedback round is
handled (spec §2, owner decision). This plan was written on 1 November 2026. **Before Task 1, run
the inventory commands in each task again**: the counts and file names below are from that date.

**One correction to the spec, made by this plan.** Spec §5.1 step 2 puts "Gson becomes
`kotlinx.serialization`" in the domain step. The domain imports no Gson; all 24 Gson users are in
`data/`, `di/` and one ViewModel. So that change moves to phase 2 with the data layer. Task 8
corrects the spec's wording.

## Global Constraints

- **The wire is frozen.** `CurrentWireFixturesTest` must pass **without** `UPDATE_WIRE_FIXTURES`.
  `WireContractTest` must pass, and so must the `upgrade` job, which runs the base build's
  `WireContractTest` over this branch. A fixture diff in this phase is a bug, never a
  regeneration (spec §5.1 step 2).
- **Android behaviour does not change.** Every existing CI job stays green on every PR: build-test,
  static, detekt, release + the R8 mapping check, r8-runtime, screenshots (no baseline change),
  instrumented, e2e, upgrade, invariants, functions, web and rules. No Roborazzi baseline is
  regenerated in this phase, apart from the toolchain-bump PR if the renderer moved, and that is
  stated in the PR.
- **A move keeps its package.** `com.coparently.app.domain.…` stays `com.coparently.app.domain.…`
  under `shared/src/commonMain/kotlin/`. Moves are `git mv`, so `git diff -M` shows them as renames.
- **minSdk 26.** Nothing in `:shared`'s Android target may need a higher API level.
- **Every invariant in `CLAUDE.md` survives unchanged.** Where an invariant names a path that
  moves, `CLAUDE.md` says where it went (Task 8). It is not reworded.
- **Commits:** Conventional Commits, each subject tagged `(MON-24)`. The attribution trailer goes on
  every commit, as the session requires.
- **No Android SDK in cloud sessions.** Gradle cannot run here. A verification step that says
  "CI" means: push the branch, open or update the PR, and read the "CI summary" comment. Run the
  same command locally first wherever an SDK exists.
- **The Mac runner never runs fork code.** Every job on it carries
  `if: github.event_name == 'push' || github.event.pull_request.head.repo.full_name == github.repository`.

## Review Focus

These are the failure modes most likely to reach a parent, with no task's ordinary tests on them.
Each has its test in the owning task.

1. **Date-time text on the wire.** `java.time` writes `2026-10-27T10:00` through `toString()`, but
   `2026-10-27T10:00:00` through `ISO_LOCAL_DATE_TIME`. kotlinx-datetime's `toString()` must give
   each writer's exact current output for zero seconds, non-zero seconds and non-zero nanoseconds,
   on both JVM and iOS. Tests: `WireTimeTextTest` (commonTest) and `WireTimeParityTest`
   (androidUnitTest), Task 6.
2. **Reading what older builds wrote.** Every input today's readers accept must still be read the
   same way, and every input they reject must still be rejected. Inputs to cover:
   - `…T10:00:00.000`;
   - a value with an offset (`EventTimestamp.fromWire`);
   - `""` for a missing end time (the e2e defect `EventDocument` once had);
   - a malformed date.
   Test: `WireTimeParsingTest`, Task 6.
3. **Money rounding.** `BigDecimal.valueOf(double).setScale(2, HALF_UP)` rounds the **decimal
   string** of the double: `2.675` → `2.68`. Plain `round(x * 100)` gives `2.67`. Expense amounts
   are `Double`, and the PDF, CSV and payment QR print them. Tests: `DecimalTextTest` (commonTest,
   fixed vectors) and `DecimalTextParityTest` (androidUnitTest, against `BigDecimal` on a corpus
   and 10 000 seeded random amounts), Task 4.
4. **Percent-encoding of a seasonal layer's name** (`SeasonalLayerCodec`, a wire format), the
   install referrer and the message report. Byte parity with
   `URLEncoder`/`URLDecoder(UTF-8)` is required for:
   - Cyrillic;
   - emoji (surrogate pairs);
   - `+ * ~ %`;
   - a space.

   A malformed `%` escape or malformed UTF-8 must still fail, so an unreadable layer is kept
   verbatim (CLAUDE.md item 30). Tests: `PercentCodecTest` and `PercentCodecParityTest`, Task 4.
5. **Time zones and DST.** Five behaviours must stay exactly as `java.time` gives them today:
   - a local time in the spring gap: Europe/Prague, 2026-03-29 02:30;
   - a local time in the autumn overlap: 2026-10-25 02:30;
   - the device zone on both platforms;
   - the date line: Pacific/Kiritimati at UTC+14 and Pacific/Pago_Pago at UTC−11, the zones the
     e2e chat test uses;
   - the custody and recurrence code that derives days from instants.

   Test: `ZoneRulesParityTest`, Task 6. Its expected values are computed from `java.time` once and
   written into commonTest, so iOS is held to them too.

---

### Task 1: Toolchain bump (Android only)

Kotlin/Native must support the Xcode on the owner's Mac. kotlinx-datetime ≥ 0.7 needs Kotlin
≥ 2.1.20. Today the project is on Kotlin 2.1.0, KSP `2.1.0-1.0.29` and Roborazzi 1.60.0, pinned for
Kotlin 2.1 (`build.gradle.kts` comment).

**Files:**
- Modify: `build.gradle.kts` (plugin versions), `app/build.gradle.kts` (coroutines,
  Roborazzi library versions), `CLAUDE.md` (Toolchain line in "UX/UI overhaul" item 2, the
  Roborazzi paragraph in "Build & verify").

**Interfaces:**
- Produces: the Kotlin version every later task builds with. It is written down in the PR body
  and in `CLAUDE.md`.

- [ ] **Step 1: Choose the versions.** Kotlin is the newest stable release for which all three
  exist:
  - a Compose Multiplatform release that supports it (phase 2 needs this);
  - a KSP release;
  - a Roborazzi release built with a Kotlin no newer than it.

  The Kotlin Compose compiler plugin takes the same version. Record the four versions and the
  supported Xcode range in the PR body.
- [ ] **Step 2: Bump them.** Change the four plugin versions in `build.gradle.kts`, Roborazzi's
  library versions in `app/build.gradle.kts`, and `kotlinx-coroutines-*` to the release paired
  with that Kotlin. Update the KSP and Roborazzi comments in `build.gradle.kts` to the new reasoning.
- [ ] **Step 3: Verify.** CI: every job green. Run `./gradlew verifyRoborazziDebug`. It must pass.
  If it fails only because the renderer moved, regenerate through the Regenerate workflow, put the
  PNG diff in the PR, and say so.
- [ ] **Step 4: Commit.** `chore: bump Kotlin, KSP and Roborazzi for multiplatform (MON-24)`

### Task 2: The `:shared` module, its CI and the Mac runner

**Files:**
- Create:
  - `shared/build.gradle.kts`;
  - `shared/src/commonMain/kotlin/com/coparently/app/domain/files/HexDigest.kt`, moved with
    `git mv` from `app/src/main/java/...`;
  - its test, moved to `shared/src/commonTest/kotlin/...`;
  - `docs/IOS-RUNNER.md`.
- Modify:
  - `settings.gradle.kts` (`include(":shared")`);
  - `build.gradle.kts` (the `org.jetbrains.kotlin.multiplatform` plugin, `apply false`);
  - `app/build.gradle.kts` (`implementation(project(":shared"))`; Kover merges `:shared`);
  - `.github/workflows/ci.yml` (two jobs, `report.needs`);
  - `tools/ci-changes.js`, `tools/test/ci-changes.test.js`;
  - `tools/manual-test-plan.js` (map `shared/` like `app/src/main/.../domain/`);
  - `CLAUDE.md` (Build & verify, the CI paragraph, the architecture map).
- Test: `shared/src/commonTest/kotlin/com/coparently/app/domain/files/HexDigestTest.kt`.

**Interfaces:**
- Produces:
  - Gradle project `:shared`, with Android namespace `com.coparently.app.shared`, compileSdk 36,
    minSdk 26, and targets `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`;
  - the iOS framework `baseName = "Shared"`, static;
  - commonMain dependencies: `kotlinx-coroutines-core`, `kotlinx-datetime`;
  - commonTest: `kotlin("test")`, `kotlinx-coroutines-test`;
  - androidUnitTest: JUnit 4 (for parity tests that call JVM APIs);
  - the `tools/ci-changes.js` output `shared`.

- [ ] **Step 1: Write the failing CI test.** Add this case to `tools/test/ci-changes.test.js`: a
  diff of `shared/src/commonMain/kotlin/x.kt` sets `shared: true`, `android: true` and `e2e: true`,
  and runs every API leg of the emulator matrix. A diff of `docs/x.md` sets `shared: false`.
- [ ] **Step 2: Run it.** `node --test tools/test/ci-changes.test.js` → FAIL (no `shared` output).
- [ ] **Step 3: Implement.** Add `shared` to `tools/ci-changes.js`. It is true for `shared/**`, the
  root and module build files, and `.github/workflows/**`. A `shared/` path also counts as an
  Android path for every existing output, because the app compiles it.
- [ ] **Step 4: Run it.** `node --test tools/test/*.test.js` → PASS.
- [ ] **Step 5: Create the module.** Write `shared/build.gradle.kts` with the targets and
  dependencies above, and apply detekt to it with the app's config. Move `HexDigest.kt` and its
  test, converting the test to `kotlin.test`: `@Test` and `assertEquals` from `kotlin.test`.
  `HexDigest.kt` is the first move because it imports nothing.
- [ ] **Step 6: Add the CI jobs.** Add two jobs to `ci.yml`, both gated on `changes.outputs.shared`
  and both in `report.needs`.
  - `shared` (ubuntu): `./gradlew :shared:testDebugUnitTest :shared:compileKotlinIosSimulatorArm64`
    with `kotlin.native.enableKlibsCrossCompilation=true` if the chosen Kotlin still needs the
    flag. It publishes JUnit as the check run "Shared tests (JVM)". If that Kotlin cannot
    cross-compile Apple klibs on Linux, drop the second task here: the Mac job is then the only
    iOS compile gate, and the PR says so.
  - `shared-ios`, on `runs-on: [self-hosted, macOS, ARM64, coplanly]`, with the fork guard from
    Global Constraints: `./gradlew :shared:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64`.
    It publishes "Shared tests (iOS simulator)" and sets `require_tests`.
- [ ] **Step 7: Write the runner doc.** `docs/IOS-RUNNER.md` is the owner's (💻) steps:
  - install Xcode and a JDK 17;
  - register a repository-scoped runner with labels `macOS, ARM64, coplanly`;
  - run it as a launch agent;
  - make sure no other repository uses that runner;
  - turn it off and on again (the jobs then queue; nothing fails silently).
- [ ] **Step 8: Verify.** CI shows all of these green:
  - the app jobs;
  - "Shared tests (JVM)", running `HexDigestTest`;
  - once the runner exists, "Shared tests (iOS simulator)", with the same test.

  Until the runner exists, the `shared-ios` job waits in the queue. Merge only after it has run
  green once.
- [ ] **Step 9: Commit.** `build: add the shared multiplatform module and its CI (MON-24)`

### Task 3: Cut the domain's leaks into `data/` and `utils/`

Run this inventory: `grep -rlE "^import com\.coparently\.app\.(data|utils)" app/src/main/java/com/coparently/app/domain`.
On 1 November it found ten files. They fall into two groups.

**Fixed here:** types that are domain facts but are declared in `data/`.
- `AcceptGuestResult`, `AcceptCalendarFriendResult` and `AcceptProfessionalResult` are declared in
  `data/remote/firebase/PairingFunctions.kt:328-349`. They move to
  `domain/guests/AcceptGuestResult.kt`, `domain/friends/AcceptCalendarFriendResult.kt` and
  `domain/professionals/AcceptProfessionalResult.kt`.
- `EventVersionKind` is declared in `data/versions/EventVersionDocument.kt:188`. It moves to
  `domain/versions/EventVersionKind.kt`, unchanged, `wire` values included.

**Left in the app for phase 2:**
- `CreateEventUseCase`, `UpdateEventUseCase` and `DeleteEventUseCase`: analytics, Crashlytics and
  `@Inject`.
- `ErrorHandler`: Crashlytics, `NetworkMonitor` and `@Inject`.

**Files:**
- Create: the four files above.
- Modify: `PairingFunctions.kt`, `EventVersionDocument.kt`, and every importer, found with
  `git grep -l "AcceptGuestResult\|AcceptCalendarFriendResult\|AcceptProfessionalResult\|EventVersionKind"`.
- Test: the existing tests of those importers, unchanged except their imports.

**Interfaces:**
- Produces: the same declarations under their new packages.

- [ ] **Step 1: Move the declarations.** Update every import. No other line changes.
- [ ] **Step 2: Verify locally.** `git grep -n "data.remote.firebase.Accept\|data.versions.EventVersionKind"`
  → no output.
- [ ] **Step 3: Verify in CI.** All jobs green. `WireContractTest`'s `event_versions` contract
  passes, since the kind's `wire` strings did not change.
- [ ] **Step 4: Commit.** `refactor: declare pairing results and revision kinds in the domain (MON-24)`

### Task 4: Platform seams and pure ports

Each item replaces a JVM-only call that domain files make today. Nothing calls the replacements
until Task 5 and Task 6. This task only proves them equal to the originals.

**Files (all under `shared/src/`):**

| Create | Replaces | Used by (today) |
| --- | --- | --- |
| `commonMain/.../domain/platform/Sha256.kt` (`expect object Sha256 { fun digest(bytes: ByteArray): ByteArray }`), `androidMain` (`MessageDigest`), `iosMain` (`CC_SHA256` via `platform.CoreCrypto`) | `java.security.MessageDigest` | `PlanCitation`, `RecordVerification`, `SchoolImportItems` |
| `commonMain/.../domain/platform/SecureRandomBytes.kt` (`expect fun secureRandomBytes(size: Int): ByteArray`), `androidMain` (`SecureRandom`), `iosMain` (`SecRandomCopyBytes`) | `java.security.SecureRandom` | `InviteCodeGenerator` |
| `commonMain/.../domain/platform/Unicode.kt` (`expect fun String.canonicalDecomposition(): String`), `androidMain` (`Normalizer.Form.NFD`), `iosMain` (`decomposedStringWithCanonicalMapping`) | `java.text.Normalizer` | `TextFold`, `SchoolEvents` |
| `commonMain/.../domain/platform/Lzma1Raw.kt` (`expect object Lzma1Raw { fun encode(input: ByteArray): ByteArray? }`), `androidMain` (`org.tukaani:xz`, the exact settings `PaymentQr` uses now), `iosMain` (`null`) | `org.tukaani.xz` | `PaymentQr` |
| `commonMain/.../domain/text/PercentCodec.kt` (`object PercentCodec { fun encode(text: String): String; fun decode(text: String): String? }`), pure Kotlin | `URLEncoder`/`URLDecoder` (UTF-8) | `SeasonalLayerCodec`, `InstallReferrer`, `MessageReport` |
| `commonMain/.../domain/text/Crc32.kt` (`object Crc32 { fun of(bytes: ByteArray): Long }`) | `java.util.zip.CRC32` | `PaymentQr` |
| `commonMain/.../domain/payment/Mod97.kt` (`fun mod97(digits: String): Int`, chunked `Long`) | `BigInteger.mod` | `Iban` |
| `commonMain/.../domain/money/DecimalText.kt` (`object DecimalText { fun halfUp(value: Double, scale: Int): String; fun plain(units: Long, scale: Int): String; fun parse(plain: String, scale: Int): Long? }`) | `BigDecimal.valueOf(…).setScale(…, HALF_UP).toPlainString()`, `BigDecimal.valueOf(bp, 2).stripTrailingZeros().toPlainString()` | `RecordFormat`, `ActivityFallbackText`, `PaymentQr`, `Payment`, `SupportAgreement` |

`Payment.amount`, `SupportAgreement.amount` and `PaymentQr`'s amount stop being `BigDecimal`.
They become `Long` minor units at scale 2: whole cents, which is what their wire strings already
mean (CLAUDE.md items 45, 48 and 49). That type change, and its app callers, happen in Task 5
when the files move, not here.

`Lzma1Raw` is `null` on iOS on purpose. `PaymentQr` already draws no code for an account it cannot
encode and still offers the IBAN to copy and share (CLAUDE.md item 45). So a Slovak account on an
iPhone gets no PAY by square code until phase 4 ports an encoder. That is recorded in Task 8's
spec edit, and no screen claims otherwise.

- [ ] **Step 1: Write the failing commonTest tests**, with fixed vectors:
  - **`Sha256Test`:** `"abc"` → `ba7816bf…f20015ad`, and `""` → `e3b0c442…b855`, through
    `HexDigest`.
  - **`PercentCodecTest`:**
    - `encode("a b+č*~%😀") == "a+b%2B%C4%8D*%7E%25%F0%9F%98%80"`;
    - `decode("%ZZ") == null`;
    - `decode("%C4") == null` (malformed UTF-8);
    - `decode("a+b") == "a b"`.
  - **`Crc32Test`:** `"123456789"` → `0xCBF43926`.
  - **`Mod97Test`:** `"08000000192000145399123565"` → `1` (the rearranged digits of
    `CZ6508000000192000145399`), and a one-digit change → not `1`.
  - **`DecimalTextTest`:**
    - `halfUp(2.675, 2) == "2.68"`;
    - `halfUp(0.005, 2) == "0.01"`;
    - `halfUp(1.0E7, 2) == "10000000.00"`;
    - `halfUp(-2.675, 2) == "-2.68"`;
    - `plain(1250, 2) == "12.50"`;
    - `parse("12.5", 2) == 1250`;
    - `parse("12.505", 2) == null`;
    - `parse("abc", 2) == null`;
    - the percent case: `plain(5000, 2)` with trailing zeros stripped → `"50"` and `"50 %"`, as
      `ActivityFallbackText` prints it.
  - **`UnicodeTest`:** `"čas".canonicalDecomposition().length == 4`.
  - **`SecureRandomBytesTest`:** 32 bytes; two calls differ.
- [ ] **Step 2: Write the failing parity tests** in `androidUnitTest`. Each runs the JVM original
  and the port over the same input and asserts equality. The inputs are a fixed corpus plus
  10 000 seeded randoms (`Random(20261101)`):
  - `PercentCodecParityTest`: strings from the Cyrillic, Latin-2, emoji and ASCII-punctuation
    ranges;
  - `DecimalTextParityTest`: doubles from `0.0` to `1.0E9` with up to 4 decimals, negatives
    included;
  - `Crc32ParityTest`;
  - `Mod97ParityTest`: random 30-digit strings;
  - `Lzma1RawTest`: the bytes match today's `PaymentQr` output for the existing `PaymentQrTest`
    document.
- [ ] **Step 3: Run them.** `./gradlew :shared:testDebugUnitTest` → FAIL (nothing exists).
- [ ] **Step 4: Implement.** Write each declaration in the table.
  - `PercentCodec` follows `URLEncoder`'s form encoding: unreserved `A–Z a–z 0–9 . - * _`, a space
    becomes `+`, everything else becomes uppercase `%XX` of its UTF-8 bytes.
  - `PercentCodec.decode` uses `decodeToString(throwOnInvalidSequence = true)` inside `runCatching`.
  - `DecimalText.halfUp` rounds the shortest round-trip decimal form of the double. On JVM that is
    what `BigDecimal.valueOf` reads; the parity test is the judge. Where Kotlin/Native's
    `Double.toString()` differs (exponent forms), normalise it before rounding.
- [ ] **Step 5: Run them.** `./gradlew :shared:testDebugUnitTest :shared:iosSimulatorArm64Test` →
  PASS on both. The parity tests run on JVM; the fixed vectors run on both.
- [ ] **Step 6: Commit.** `feat: add multiplatform seams for hashing, randomness, text and money (MON-24)`

### Task 5: Move the platform-free domain, then the files the seams free

**Inventory:** a domain file can move when two things hold:
- it imports nothing from `java.`, `javax.`, `android`, `androidx`, `org.tukaani` or
  `com.coparently.app.(data|utils|presentation)`;
- every `com.coparently.app.domain` type it uses has already moved.

Build the list with a script in the scratchpad, not a committed tool. The script loops: take the
files that meet both conditions, move them, and repeat until nothing more qualifies. On
1 November, 93 of 184 domain files had no such import. How many of them can actually move depends
on the second condition. For example, `Event` still uses `java.time` until Task 7.

**Also in this task:** convert these files to the Task 4 seams, and then move them:
- `PlanCitation`, `RecordVerification`, `SchoolImportItems` (`Sha256`; `"%02x".format` becomes
  `HexDigest`);
- `InviteCodeGenerator` (`secureRandomBytes`; the `random: Random` parameter stays for tests);
- `TextFold`, `SchoolEvents` (`canonicalDecomposition`; `lowercase(Locale.ROOT)` becomes
  `lowercase()`, which is locale-invariant in Kotlin);
- `InstallReferrer`, `MessageReport` (`PercentCodec`);
- `Iban` (`mod97`);
- `PaymentQr`, `Payment`, `SupportAgreement`, `ActivityFallbackText` (`Crc32`, `Lzma1Raw`,
  `DecimalText`, `Long` minor units), with their app callers;
- `SharedFilePolicy` (`lowercase()`);
- `AnswerBy` (`TimeUnit` becomes `kotlin.time.Duration`).

Each still has to meet the java.time condition above. A file that also uses `java.time` waits for
Task 6 or Task 7.

**Stays in the app in phase 1.** These are DI-annotated, Android-API or `File`-typed. Phase 2
moves them with Hilt's replacement.
- `domain/usecase/*` with `@Inject`;
- `domain/error/ErrorHandler.kt`;
- `domain/activity/ActivityAnnouncer.kt`;
- `domain/model/MessageTemplate.kt` (`@StringRes`);
- `domain/repository/FamilyDocumentRepository.kt` (`java.io.File`);
- anything that depends on them.

**Files:** `git mv` for each file, `app/src/main/java/com/coparently/app/domain/<p>/<F>.kt` →
`shared/src/commonMain/kotlin/com/coparently/app/domain/<p>/<F>.kt`. Its test goes to
`shared/src/commonTest/...` when it uses only JUnit asserts, which are converted to `kotlin.test`.
It goes to `shared/src/androidUnitTest/...` when it uses MockK or coroutines-test with
JVM-only helpers. On 1 November, three domain tests used MockK.

**Interfaces:**
- Consumes: Task 4's seams.
- Produces: the same public declarations, same packages; the money types change to `Long` minor
  units.

- [ ] **Step 1: Move in batches.** Each batch is the files one loop of the inventory script frees,
  plus their tests. One commit per batch.
- [ ] **Step 2: Convert the seam users** listed above, one commit per row of Task 4's table. Each
  commit leaves the file's existing test unchanged in its assertions; only imports and constructors
  change. `PaymentQrTest` keeps its byte-for-byte PAY by square expectation as an
  `androidUnitTest`.
- [ ] **Step 3: Verify.** CI all green, and these checks:
  - "Shared tests (iOS simulator)" runs every moved commonTest;
  - `CurrentWireFixturesTest` passes with no update;
  - `git diff -M --stat origin/main -- app/src/main/java/com/coparently/app/domain shared/src`
    shows renames, with only the converted files changed beyond their paths.
- [ ] **Step 4: Commit messages.** `refactor: move platform-free domain into shared (MON-24)`,
  then `refactor: <file> through the multiplatform <seam> (MON-24)` per seam.

### Task 6: Time on the wire in kotlinx-datetime

The wire-critical time code moves first and alone, so its PR shows nothing but that. The files
(inventory: `grep -rlE "DateTimeFormatter|ISO_LOCAL|toEpochMilli|ofEpochMilli" app/src/main/java/com/coparently/app/domain`):
- `domain/events/EventTimestamp.kt`;
- `domain/custody/CustodyTimestamp.kt`;
- `ContactWindowCodec` (in `ContactWindow.kt`);
- `SeasonalLayerCodec` (in `SeasonalLayer.kt`; it also uses `PercentCodec` from Task 4);
- `DayOverrideDecisionCodec`;
- `ChildOverrideCodec`;
- `CustodyHistory` (its `hist-` id date);
- `domain/export/RecordFormat.kt`.

**Interfaces:**
- Produces: the same public functions, with these parameter and return types:
  - `java.time.LocalDate` → `kotlinx.datetime.LocalDate`;
  - `LocalDateTime` → `kotlinx.datetime.LocalDateTime`;
  - `LocalTime` → `kotlinx.datetime.LocalTime`;
  - `Instant` → `kotlin.time.Instant`;
  - `ZoneId` → `kotlinx.datetime.TimeZone`.

  Epoch-millis and `String` signatures do not change. App callers adapt in the same PR with
  `toKotlinLocalDate()` and its siblings; Task 7 removes those conversions.

- [ ] **Step 1: Write the failing tests** in commonTest. Each expected string is computed today
  from `java.time` and written into the test as a literal.
  - **`WireTimeTextTest`:** for each writer above, the exact output for
    - `2026-10-27T10:00`;
    - `2026-10-27T10:00:07`;
    - `2026-10-27T10:00:07.123`;
    - `2026-03-29` (a DST day).

    One example: `EventTimestamp.toWire(1793095200000L) == "2026-10-27T10:00:00"`. The executor
    computes the rest the same way, from the current code, before changing it.
  - **`WireTimeParsingTest`:** what each reader returns, or how it fails, for
    - `"2026-10-27T10:00"`;
    - `"2026-10-27T10:00:00.000"`;
    - `"2026-10-27T10:00:00+02:00"` (`EventTimestamp.fromWire` honours the offset);
    - `"2026-10-27T10:00:00Z"`;
    - `""`;
    - `"27.10.2026"`.

    The expectations match what today's code does: a value, or the same exception kind, mapped to
    the same `null`/skip at the caller.
  - **`ZoneRulesParityTest`:**
    - Europe/Prague `2026-03-29T02:30` → instant `2026-03-29T01:30:00Z` (the gap moves forward);
    - Europe/Prague `2026-10-25T02:30` → `2026-10-25T00:30:00Z` (the earlier offset, as
      `atZone` picks);
    - `1793095200000L` → the local date in Pacific/Kiritimati and in Pacific/Pago_Pago.
  - **`RecordFormatTest`'s existing cases** move to commonTest unchanged in their expected strings
    (`"yyyy-MM-dd HH:mm:ss xxx"` prints `+00:00`, never `Z`).
- [ ] **Step 2: Write the parity test.** `WireTimeParityTest` (androidUnitTest) runs each writer and
  reader over 5 000 seeded random instants and dates, against the `java.time` code it replaces. The
  old code is kept in the test source as the oracle.
- [ ] **Step 3: Run them.** → FAIL (the new signatures do not exist yet).
- [ ] **Step 4: Rewrite the files** on kotlinx-datetime: `LocalDateTime.Format { … }`,
  `UtcOffset.Formats.ISO` with `Z` replaced, and `TimeZone.of`. Move them to `shared/`.
- [ ] **Step 5: Run them.** → PASS on JVM and on the iOS simulator. In CI,
  `CurrentWireFixturesTest`, `WireContractTest` and the `upgrade` job pass unchanged.
- [ ] **Step 6: Commit.** `refactor: write and read wire times with kotlinx-datetime (MON-24)`

### Task 7: The domain models, and the app code that touches them

Inventory: `grep -nE "val [a-zA-Z]+: (LocalDate|LocalDateTime|Instant|LocalTime|YearMonth)" app/src/main/java/com/coparently/app/domain/model/*.kt`.
On 1 November it found 24 fields across 9 model files. `java.time` was imported by 243 of 736 main
files and 198 test files.

**The rule for app code** (decided here):
- **Android-only code that reads or writes a moved type uses kotlinx-datetime too.** This covers
  `data/`, `presentation/` and `di/`.
- **`java.time` remains only at an Android edge:**
  - Room `Converters` and `*Entity` fields (the data layer moves in phase 2), with mappers
    converting;
  - `utils/LocalizedDates.kt`, which gains kotlinx overloads and converts inside;
  - `presentation/common/PickerDates.kt`;
  - WorkManager delays;
  - `CalendarScreen`'s kizitonwose `YearMonth`, until phase 2 swaps the library.
- **Conversions use kotlinx-datetime's own JVM converters**
  (`toJavaLocalDate()`/`toKotlinLocalDate()` and siblings). No project wrapper.

**Slices.** One PR each, in this order, because each later model uses an earlier one:
1. `CustodyModel` (and `DayOverride`, `ContactWindow`, `SeasonalLayer`, `ChildScheduleOverride`
   if not already moved) and the custody logic (`CustodyResolver`, `HandoverCalculator`,
   `HolidayFairnessCalculator`);
2. `Event`, `RecurrenceExpander`, `ReminderPlanner`, `AllDayEvent`;
3. `Expense`, `ChangeRequest` and the request-clock domain (`RequestClock`, `AnswerBy`,
   `RequestCap`);
4. `Message`, `Conversation`, `User`, `ChildInfo`, `Pet` and `MedicalProfile`.

**Files:** per slice, the model files, their domain users, and every app file the compiler names.
Tests change with their code.

**Interfaces:**
- Consumes: Task 6's codecs.
- Produces: models whose date-time properties are kotlinx-datetime types:
  - `LocalDate` → `kotlinx.datetime.LocalDate`;
  - `LocalDateTime` → `kotlinx.datetime.LocalDateTime`;
  - `Instant` → `kotlin.time.Instant`;
  - `YearMonth` → `kotlinx.datetime.YearMonth` if the chosen version has it, else a domain
    `data class YearMonth(val year: Int, val month: Int)` in `domain/time/YearMonth.kt`.

- [ ] **Step 1: Change each slice's model types,** then follow the compiler through the app. In
  each step, the existing tests of the touched code stay identical in their assertions; only the
  constructed values change type.
- [ ] **Step 2: Verify in CI.** All jobs green. Then four checks:
  - `CurrentWireFixturesTest` passes with no update;
  - the `upgrade` job passes;
  - `verifyRoborazziDebug` passes with no baseline change (a screen that draws a date must draw
    the same pixels);
  - the e2e job passes, including `TwoParentChatTest` across the date line.
- [ ] **Step 3: Commit each slice.** `refactor: <models> on kotlinx-datetime (MON-24)`

### Task 8: Move the rest of the domain, and record what phase 1 left

**Files:**
- Move: every domain file that now meets Task 5's inventory condition, the holiday tables
  included (`domain/holidays/*`). `HolidayReferenceTest` and `SchoolVacationReferenceTest` move to
  commonTest; their fixtures move to `shared/src/commonTest/resources`, read through a small
  `expect fun readResource(path: String): String`. If that is not worth an expect, they stay in
  `androidUnitTest`.
- Modify:
  - `CLAUDE.md`:
    - the architecture map gains `shared/` and says that `domain/…` paths in this file now live
      under `shared/src/commonMain/kotlin/com/coparently/app/domain/…`, except the phase-1 stay
      list;
    - Build & verify gains the two shared tasks;
    - Localization is not touched (strings move in phase 2).
  - `docs/superpowers/specs/2026-10-27-iphone-app-design.md`:
    - §5.1 step 2: Gson moves with the data layer in phase 2, not in the domain step;
    - §5.4: PAY by square has no code on iPhone until phase 4 ports an LZMA1 encoder.
  - `docs/ROADMAP.md` MON-24: phase 1 done, with the stay list.
  - `tools/check-invariants.js`: any check that reads a domain path (for example check 6's
    Google Calendar scope, `GoogleCalendarAccess`, and the push vocabulary) follows the file to
    `shared/` if it moved.
- Test: `node tools/check-invariants.js` and `node --test tools/test/*.test.js`.

- [ ] **Step 1: Move the remaining eligible files** and their tests, as in Task 5.
- [ ] **Step 2: Write down the stay list.** Run the inventory and paste what remains under
  `app/src/main/java/com/coparently/app/domain` into ROADMAP MON-24, each file with its reason
  (`@Inject`, `android.util.Log`, `@StringRes`, `File`).
- [ ] **Step 3: Update `CLAUDE.md`, the spec and `tools/check-invariants.js`** as listed.
- [ ] **Step 4: Verify.**
  - `node tools/check-invariants.js` → exit 0.
  - CI all green; "Shared tests (iOS simulator)" runs every commonTest in the module.
  - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` on the Mac produces `Shared.framework`.
- [ ] **Step 5: Commit.** `docs: record phase 1 of the iPhone work (MON-24)`, after the moves'
  own `refactor:` commits.
