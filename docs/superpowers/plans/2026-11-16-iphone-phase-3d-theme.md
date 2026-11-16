# iPhone phase 3 part D — theme — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iPhone app follow the app's theme choice, dark mode and Increase Contrast down
to its native parts, set its text in SF Pro, draw its icons as SF Symbols, and hold up under
Dynamic Type. Android must not move by one pixel.

**Architecture:**
- Every change is either in `iosMain`/`iosTest`, or behind an `expect`/common entry point whose
  Android half is today's call, unchanged.
- **The shell owns the window.** It collects the theme preference, sets the window's interface
  style and its tint, and passes the choice into every hosted composition.
- **Icons go through one common composable**, `AppIcon`. On Android it is Material's `Icon`. On the
  iPhone it rasterises the mapped SF Symbol inside Compose and never uses a native view.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.9.3, UIKit via Kotlin/Native cinterop,
XCUITest, GitHub Actions `ios-app` job.

**Spec:** `docs/superpowers/specs/2026-11-16-iphone-phase-3d-theme-design.md`. Parent:
`docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` (§2, §6 row D). Boards:
the fourth row of https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH, approved by the owner on
16 November 2026, together with the symbol table
`docs/superpowers/plans/2026-11-16-iphone-phase-3d-sf-symbols.json`. That table has 132 rows, 12 of
them `keepMaterial: true`; the approval makes it binding.

## Global Constraints

- **Android is unchanged.**
  - The 470 Roborazzi baselines and the detekt baseline are never regenerated.
  - Android halves are today's calls, line for line.
- **The iPhone keeps the November redesign's look**: lavender ground, white cards, indigo
  `#4F46E5` tint in the light standard scheme.
- **Increase Contrast maps to `ContrastLevel.MEDIUM`, never HIGH** (owner, 16 November 2026).
- **SF Pro is the iPhone's body family** (`FontFamily.Default`). The wordmark `coplanly.` stays
  Onest Bold on both platforms.
- **The native tint follows the active colour scheme's `primary`**: `#4F46E5` light standard,
  `#C2C1FF` dark standard, the generated scheme's primary at MEDIUM.
- **The switch stays the system's green.**
- **No `UIKitView` draws an icon.** Part C's interop rules hold (design refresh item 1 of
  `CLAUDE.md`).
- **iOS deployment target is 17.0.** A symbol that `UIImage(systemName:)` cannot load draws the
  Material icon.
- **Every icon keeps the `contentDescription` and the tint its call site gives.**
- **Don't change the symbol table's choices.** A symbol that fails to load falls back to Material,
  never to another symbol.
- **Repository rules.** Repository content is in English. Commits are Conventional Commits with
  "(MON-24)". No model identifiers anywhere in the repository.
- **Local checks.** Each task ends with `./gradlew :shared-ui:compileKotlinIosSimulatorArm64
  :shared-ui:compileTestKotlinIosSimulatorArm64 :shared-ui:testAndroidHostTest detekt` green, plus
  the Android commands its steps name. iOS tests run only in CI (the `ios-app` job, once at the end
  of the plan).

## Review Focus

1. **A theme choice changed while a sheet, alert or picker is up.** The presented controller must
   flip with the window.
   - Test: Task 1's `WindowAppearanceTest#presentedControllerFollowsWindowStyle`.
2. **System dark with the app set to Light.** The content and the native bars must be light, and
   reading the system theme must not feed back into the override. Following the system must use
   `UIUserInterfaceStyleUnspecified`, never the resolved style.
   - Test: Task 1's `WindowAppearanceTest#systemChoiceLeavesStyleUnspecified`.
3. **Increase Contrast toggled while the app runs.** The Compose scheme and the tint must both
   update without a relaunch.
   - Test: Task 2's `IosContrastLevelTest#notificationUpdatesLevel`.
4. **An icon whose symbol is missing on the running iOS.** It must draw the Material vector, not
   nothing.
   - Test: Task 5's `SfSymbolIconTest#missingSymbolFallsBackToMaterial`.
5. **An icon with a non-default tint** (a parent colour, the error colour). The symbol must take
   that tint, not black or the system tint.
   - Test: Task 5's `SfSymbolIconTest#tintIsAppliedToTemplate`.

---

### Task 1: Theme choice, window style and native tint

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/WindowAppearance.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/ScreenController.kt:197`
  (`AppProviders(darkTheme = null)` → the shell's current choice)
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/NativeShell.kt` (owns the
  collection)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/ContrastLevel.kt`
  (`colorSchemeFor` becomes callable from iosMain if it is not already: it is `internal` and in the
  same module, so no change is expected)
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/shell/WindowAppearanceTest.kt`

**Interfaces:**
- Consumes: `PreferencesRepository.getDarkThemeFlow(): Flow<Boolean?>`; `colorSchemeFor(darkTheme:
  Boolean, contrast: ContrastLevel): ColorScheme`.
- Produces:
  - `internal object WindowAppearance` with `fun apply(window: UIWindow, choice: Boolean?, contrast:
    () -> ContrastLevel)`. It sets `overrideUserInterfaceStyle` (null → Unspecified, true → Dark,
    false → Light) and `tintColor`.
  - The tint is a dynamic `UIColor` whose provider returns
    `colorSchemeFor(traits.userInterfaceStyle == Dark, contrast()).primary`, so it re-resolves on
    every trait change.
  - `NativeShell.themeChoice: StateFlow<Boolean?>`, read by every `ScreenController`'s
    `AppProviders(darkTheme = shell.themeChoice.collectAsState().value)`.

- [ ] **Step 1: Write the failing tests** in `WindowAppearanceTest` (a real `UIWindow`, no scene
  needed):
  - `systemChoiceLeavesStyleUnspecified`: after `apply(window, null, { STANDARD })`,
    `window.overrideUserInterfaceStyle == UIUserInterfaceStyleUnspecified`.
  - `darkChoiceForcesDark`: after `apply(window, true, …)`, the style is
    `UIUserInterfaceStyleDark`; after `false`, it is `UIUserInterfaceStyleLight`.
  - `tintIsPrimaryOfScheme`: the tint resolved with a light trait collection equals `#4F46E5` (to
    1/255). Resolved with a dark one, it equals `DarkColorScheme.primary` (`#C2C1FF`). With
    `contrast = { MEDIUM }` and light traits, it equals `LightMediumContrastColorScheme.primary`.
  - `presentedControllerFollowsWindowStyle`: a `UIViewController` set as the window's root and made
    key reports `traitCollection.userInterfaceStyle == Dark` after `apply(window, true, …)`.
- [ ] **Step 2: Confirm the tests compile and fail.** Run `./gradlew
  :shared-ui:compileTestKotlinIosSimulatorArm64`; it is expected to fail on the missing
  `WindowAppearance`.
- [ ] **Step 3: Implement `WindowAppearance.apply`.**
- [ ] **Step 4: Wire it in the shell.**
  - `NativeShell` collects `getDarkThemeFlow()` (from Koin, on the main dispatcher) into
    `themeChoice`.
  - On every value, and on every contrast change (Task 2's `iosContrastLevelChanges`; until Task 2
    exists, apply once), it calls `WindowAppearance.apply` on the shell's own window. That is the
    window holding the tab bar controller, not a scene lookup.
  - `ScreenController.Shared` passes `themeChoice`'s value to `AppProviders`.
  - Status bar: rely on the window style (the hosting controllers keep the default
    `preferredStatusBarStyle`). Write in `SystemBarsAppearance`'s iOS KDoc that the window style
    decides it.
- [ ] **Step 5: Compile.** Run `./gradlew :shared-ui:compileKotlinIosSimulatorArm64
  :shared-ui:compileTestKotlinIosSimulatorArm64 detekt`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit.** Message: `feat(ios): follow the app's theme choice in the window, its
  bars and its tint (MON-24)`

### Task 2: Increase Contrast → MEDIUM

**Files:**
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/theme/ContrastLevel.ios.kt`
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/theme/IosContrastLevelTest.kt`

**Interfaces:**
- Produces:
  - `internal fun contrastLevelFor(darkerSystemColors: Boolean): ContrastLevel`. It returns MEDIUM
    when true and STANDARD when false.
  - `internal fun currentIosContrastLevel(): ContrastLevel`, read from
    `UIAccessibilityDarkerSystemColorsEnabled()`.
  - `internal val iosContrastLevelChanges: Flow<ContrastLevel>`, from
    `UIAccessibilityDarkerSystemColorsStatusDidChangeNotification` through `NSNotificationCenter`,
    starting with the current value.
  - `actual fun rememberSystemContrastLevel()` collects that flow as state.
  - Task 1's shell re-applies the tint on each emission.

- [ ] **Step 1: Write the failing tests.**
  - `mapsDarkerColorsToMedium`: `contrastLevelFor(true) == MEDIUM` and
    `contrastLevelFor(false) == STANDARD`.
  - `notificationUpdatesLevel`: the flow emits again after posting the notification name on the
    default centre. Expect the value `currentIosContrastLevel()` returns. The simulator's switch
    cannot be set from a test, so this pins the re-read, not the value.
- [ ] **Step 2: Implement** the three functions and the `actual`. Update the file's KDoc: Increase
  Contrast is MEDIUM (owner, 16 November 2026).
- [ ] **Step 3: Wire it.** Use `iosContrastLevelChanges` in `NativeShell` (Task 1's re-apply).
  Compile as in Task 1, Step 5.
- [ ] **Step 4: Commit.** Message: `feat(ios): answer Increase Contrast with the medium scheme
  (MON-24)`

### Task 3: SF Pro on the iPhone, Onest for the wordmark

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/Type.kt`
  (`rememberAppTypography` uses `appFontFamily()`)
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/AppFontFamily.kt`
  (`@Composable internal expect fun appFontFamily(): FontFamily`)
- Create: `shared-ui/src/androidMain/kotlin/com/coparently/app/presentation/theme/AppFontFamily.android.kt`
  (`actual = onestFontFamily()`)
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/theme/AppFontFamily.ios.kt`
  (`actual = FontFamily.Default`)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/BrandHeader.kt:85`
  (style `headlineSmallEmphasized.copy(fontFamily = onestFontFamily())`)
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/theme/IosTypographyTest.kt`

- [ ] **Step 1: Write the failing test** `bodyIsSystemFamilyAndWordmarkIsOnest`.
  - Compose `CoPlanlyTheme` in `runComposeUiTest`, which is already used in shared-ui's iosTest
    (check `ios/controls` tests for the harness).
  - Assert `MaterialTheme.typography.bodyLarge.fontFamily == FontFamily.Default`.
  - Assert the wordmark's resolved style family equals `onestFontFamily()`. Expose the wordmark
    style as `internal fun wordmarkStyle(base: TextStyle, onest: FontFamily): TextStyle` in
    `BrandHeader.kt` and test that function.
- [ ] **Step 2: Implement** the expect/actual and the wordmark style.
  - Android must stay identical: `Typography.withFontFamily(onestFontFamily())` exactly as before.
  - The wordmark's `copy(fontFamily = …)` equals the theme's family on Android.
- [ ] **Step 3: Verify Android.**
  - Run `./gradlew :shared-ui:testAndroidHostTest :app:testDebugUnitTest
    :shared-ui:compileKotlinIosSimulatorArm64`. Expected: green.
  - If an Android SDK is available, also run `./gradlew verifyRoborazziDebug`. Expected: no image
    differs. If it cannot run here, say so in the report; CI's `screenshots` job is the proof.
- [ ] **Step 4: Check the glyphs on the simulator.** Coverage of ₴ and № is checked on the
  simulator by Task 7's tour (TypeCompare's sample line on the Settings screen is not in the app).
  Add `docs/DEVICE-CHECKLIST.md` §3.41's line for it in Task 8.
- [ ] **Step 5: Commit.** Message: `feat(ios): set the iPhone's text in SF Pro and keep the wordmark
  in Onest (MON-24)`

### Task 4: `AppIcon`, the one icon entry point (Android identical)

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/AppIcon.kt`
  (`@Composable expect fun AppIcon(imageVector: ImageVector, contentDescription: String?, modifier:
  Modifier = Modifier, tint: Color = LocalContentColor.current)`)
- Create: `shared-ui/src/androidMain/kotlin/com/coparently/app/presentation/common/AppIcon.android.kt`
  (`actual` = `androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)`)
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/common/AppIcon.ios.kt`
  (for now the same Material call; Task 5 replaces it)
- Modify: every `shared-ui/src/commonMain` file calling material3 `Icon(` with an `ImageVector` (75
  files import `androidx.compose.material3.Icon`). Call sites with a `painter` or `bitmap`, if any,
  stay.
- Test: `shared-ui/src/androidHostTest/kotlin/com/coparently/app/presentation/common/AppIconGuardTest.kt`

- [ ] **Step 1: Write the failing guard test** `noDirectMaterialIconInCommonMain`.
  - Walk `shared-ui/src/commonMain/kotlin`. Find the path as the existing source-scanning tests do;
    see `ShellBoundaryTest` in `app/src/test` for the root lookup.
  - Fail on any file that imports `androidx.compose.material3.Icon` (the exact import line, not
    `IconButton`), listing the files.
  - Allow-list: none in commonMain (the actual's file is in androidMain).
- [ ] **Step 2: Run it.** Run `./gradlew :shared-ui:testAndroidHostTest --tests '*AppIconGuardTest*'`.
  Expected: FAIL listing 75 files.
- [ ] **Step 3: Implement `AppIcon`** (the three files above).
- [ ] **Step 4: Replace every material3 `Icon(` call in commonMain with `AppIcon(`.** Same
  arguments, named as at the call site. Drop the `androidx.compose.material3.Icon` imports and add
  `com.coparently.app.presentation.common.AppIcon`.
- [ ] **Step 5: Verify.**
  - Run `./gradlew :shared-ui:testAndroidHostTest :app:testDebugUnitTest detekt
    :shared-ui:compileKotlinIosSimulatorArm64 assembleDebug`. Expected: green; the guard passes.
  - Run `verifyRoborazziDebug` if possible, as in Task 3, Step 3.
  - detekt must not need a baseline change. If a baseline entry keys on a changed signature,
    restore the signature instead.
- [ ] **Step 6: Commit.** Message: `refactor: draw every shared icon through AppIcon (MON-24)`

### Task 5: SF Symbols on the iPhone

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/SfSymbolTable.kt`
  - `internal val SF_SYMBOLS: Map<String, String>`, keyed by `ImageVector.name`.
  - Material's names: `Icons.Default.X`/`Icons.Filled.X` → `"Filled.X"`, `Icons.Outlined.X` →
    `"Outlined.X"`, `Icons.AutoMirrored.Filled.X` → `"AutoMirrored.Filled.X"`. The builder confirms
    these against the icons' own `name` in a test.
  - Data only, from `docs/superpowers/plans/2026-11-16-iphone-phase-3d-sf-symbols.json`. The 12
    `keepMaterial` rows are absent from the map and listed in a KDoc with their reasons.
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/common/AppIcon.ios.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/common/SfSymbolBitmaps.kt`
  (rasterising and cache)
- Test: `shared-ui/src/androidHostTest/kotlin/com/coparently/app/presentation/common/SfSymbolTableTest.kt`
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/common/SfSymbolIconTest.kt`

**Interfaces:**
- Consumes: `AppIcon` (Task 4). Reuse the UIImage→`ImageBitmap` conversion that
  `presentation/platform/IosImages.kt` uses; extract a shared `internal` helper if it is private.
- Produces: `internal fun sfSymbolBitmap(name: String, sizePx: Int, weight: FontWeight):
  ImageBitmap?`. It returns null when `UIImage.systemImageNamed(name)` is null. The result is
  cached in a bounded LRU keyed on the three arguments (the builder picks the size). It is drawn as
  a template, so Compose applies the tint with `ColorFilter.tint`.

- [ ] **Step 1: Write the failing JVM test** `SfSymbolTableTest`.
  - `everyIconInUseHasARowOrIsKeptMaterial`: scan commonMain for `Icons\.(Default|Filled|Outlined|
    AutoMirrored\.(Filled|Outlined))\.(\w+)`. Translate each match to its `ImageVector.name` form.
    Assert every match is a key of `SF_SYMBOLS` or is one of the 12 keep-Material names, which the
    test repeats as a set.
  - `keysAreRealVectorNames`: for a sample of 6 icons, one per prefix form, assert
    `Icons.Default.Add.name == "Filled.Add"` and the like, and that the key is in the map. This
    proves the translation.
  - `tableMatchesApprovedJson`: read the JSON file. Every non-keep row's `sfSymbol` equals the
    map's value for its translated key. The JSON has 132 rows, 12 of them keep.
- [ ] **Step 2: Write the failing iOS tests** `SfSymbolIconTest`.
  - `everySymbolLoads`: `UIImage.systemImageNamed(v) != null` for every value of `SF_SYMBOLS`.
    The CI simulator is newer than iOS 17, so this proves the names, not the minimum iOS.
  - `missingSymbolFallsBackToMaterial`: `sfSymbolBitmap("no.such.symbol.coplanly", 48, Normal) ==
    null`, and `AppIcon` with a vector whose name maps to no symbol composes the Material vector.
    Test it with `runComposeUiTest`: a node with the given content description exists and draws
    non-transparent pixels via `captureToImage`.
  - `tintIsAppliedToTemplate`: render `AppIcon(Icons.Default.Add, "add", tint = Color.Red)` at
    24 dp. The captured image's opaque pixels are red (R > 200, G < 60, B < 60).
  - `semanticsKeepContentDescription`: the node has `contentDescription "add"` and role `Image`,
    as Material's `Icon` sets them.
- [ ] **Step 3: Implement** the table, `sfSymbolBitmap` and the iOS `AppIcon`.
  - Weight: `FontWeight.Normal` → `UIImageSymbolWeightRegular`; SemiBold and up →
    `UIImageSymbolWeightSemibold`. The default call site has no weight, so use regular.
  - The symbol is centred in the 24 dp default box (or the modifier's size) at the size Material's
    glyph occupies: symbol point size = box × 20/24. Material's live area is 20 dp in 24.
  - Semantics: as Material's `Icon` (contentDescription and `Role.Image`); none when null.
  - Fallback: no row, a keep row, or a null bitmap → `androidx.compose.material3.Icon(imageVector,
    …)`.
- [ ] **Step 4: Verify.** Run `./gradlew :shared-ui:testAndroidHostTest --tests '*SfSymbolTable*'
  :shared-ui:compileTestKotlinIosSimulatorArm64 detekt`. Expected: green.
- [ ] **Step 5: Commit.** Message: `feat(ios): draw shared icons as SF Symbols, with Material as the
  fallback (MON-24)`

### Task 6: Dynamic Type

**Files:**
- Read first: how Compose Multiplatform 1.9.3 sets `LocalDensity.fontScale` on iOS. Does it follow
  `UIApplication.preferredContentSizeCategory`?
- Modify (only if it does not): `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/ScreenController.kt`
  provides a `Density` whose `fontScale` comes from `fontScaleFor(category)`.
- Create (only if needed): `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/theme/DynamicTypeScale.kt`
  with `internal fun fontScaleFor(category: UIContentSizeCategory): Float`. Use Apple's body sizes
  over 17 pt: XS 14 → 0.82, S 15 → 0.88, M 16 → 0.94, L 17 → 1.0, XL 19 → 1.12, XXL 21 → 1.24,
  XXXL 23 → 1.35, AX1 28 → 1.65, AX2 33 → 1.94, AX3 40 → 2.35, AX4 47 → 2.76, AX5 53 → 3.12.
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/theme/DynamicTypeTest.kt`

- [ ] **Step 1: Write the failing test.**
  - `largeCategoryIsOne`: `fontScaleFor(Large) == 1f`. Values as listed above, within 0.01.
  - Also `densityFollowsCategory` if the shell provides it.
  - If CMP already maps the category, write instead `cmpFontScaleFollowsCategory`. It is a doc test
    recording the finding: compose and read `LocalDensity.current.fontScale`, which equals 1 at the
    simulator default. Record the finding in the report, and implement no mapping.
- [ ] **Step 2: Implement the mapping,** only if CMP lacks it. React to
  `UIContentSizeCategoryDidChangeNotification`.
- [ ] **Step 3: Fix layouts that break at large sizes on the iPhone only.**
  - The tour's large-text pass (Task 7) is the evidence: native bar titles, the date pill and
    sheets.
  - Shared layouts already follow design item 15. Do not change one unless Android's screenshots
    stay identical.
- [ ] **Step 4: Commit.** Message: `feat(ios): follow Dynamic Type's text size (MON-24)`, or
  `test(ios): …` if only the doc test landed.

### Task 7: The tour in dark, Increase Contrast and large text

**Files:**
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift`. Add `testTourAppearance`: the seven board
  destinations (Home, Calendar, Chat, New event, Family, Expenses, Settings), shot as
  `tour-<variant>-<route>`.
- Modify: `iosApp/scripts/simulator.sh`. Add a `tour-appearance` command that runs
  `testTourAppearance` three times on the seeded tour family:
  - `xcrun simctl ui <udid> appearance dark`, then back to light;
  - `xcrun simctl ui <udid> increase_contrast enabled`, then disabled;
  - `xcrun simctl ui <udid> content_size accessibility-extra-large`, then large.
  - Each run's PNGs go under `iosApp/build/tour/appearance/<variant>/`.
- Modify: `.github/workflows/ci.yml`, the `ios-app` job.
  - Add a step after the tour, `continue-on-error`, with a 25-minute step limit. It runs
    `simulator.sh tour-appearance`.
  - Upload the PNGs in the `ios-tour` artifact.
  - Only the `tour-crashed` marker fails the job, as for the tour. The §3.40 known-crash warning
    applies here too.
- Modify: `tools/ci-changes.js` only if a new path needs gating (it should not).

- [ ] **Step 1: Write `testTourAppearance`,** reusing `testTour`'s sign-in, navigation helpers and
  screenshot helper. A route it cannot reach is a `tour.txt` line with the reason, never a failure.
- [ ] **Step 2: Add the `tour-appearance` command.** Restore light, contrast off and Large at the
  end, even on failure (`trap`).
- [ ] **Step 3: Add the CI step,** after the existing tour step.
- [ ] **Step 4: Check locally.** Run `bash -n iosApp/scripts/simulator.sh`, then `node -e` to
  YAML-parse `.github/workflows/ci.yml` (use `js-yaml` from `functions/node_modules` if present),
  then `node --test tools/test/*.test.js`. Expected: all pass. Swift is not compiled here; the CI
  run is its compile.
- [ ] **Step 5: Commit.** Message: `ci(ios): shoot the board screens in dark, Increase Contrast and
  large text (MON-24)`

### Task 8: Record part D

**Files:**
- Modify: `CLAUDE.md`. In design refresh item 1, add an iPhone paragraph:
  - SF Pro with the Onest wordmark;
  - `AppIcon` and `SF_SYMBOLS` (never call Material's `Icon` in commonMain; a new icon gets a row
    or a keep-Material reason);
  - the window style and tint from the theme choice;
  - Increase Contrast → MEDIUM.
  In "Where an iPhone path lives", add part D's shapes: `WindowAppearance`, `AppIcon.ios.kt`,
  `SfSymbolBitmaps.kt`, `testTourAppearance`.
- Modify: `docs/DEVICE-CHECKLIST.md`. Add a §3.41 iPhone theme section:
  - dark mode switched while the app runs (with a sheet open);
  - the app set to Light over system Dark;
  - Increase Contrast on a phone;
  - the largest Accessibility size on Home, Expenses and the event form;
  - VoiceOver reading icon labels;
  - ₴ and № in SF Pro.
- Modify: `docs/ROADMAP.md`. In MON-24, part D is done; what remains (phase 4) is unchanged.
- Modify: `docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` §2. Add the
  row "Part D's boards (fourth row) approved, with the 12 keep-Material icons | Owner, 16 Nov
  2026".
- Create: `docs/superpowers/plans/2026-11-16-iphone-phase-3d-owner-check.md`. The owner's check on
  their Mac:
  1. Switch the theme in Settings, and in iOS.
  2. Turn on Increase Contrast.
  3. Use the largest text size.
  4. Compare with the fourth row of the canvas.
  5. Compare with the `ios-tour` artifact's appearance shots.

- [ ] **Step 1: Write the edits.** Use the same voice as the surrounding text (see "Language
  conventions" and the existing iPhone paragraphs).
- [ ] **Step 2: Run `node tools/check-invariants.js && node --test tools/test/*.test.js`.**
  Expected: pass. The checklist sections `tools/manual-test-plan.js` names must still exist.
- [ ] **Step 3: Commit.** Message: `docs: record the iPhone app's part D, the theme (MON-24)`
