# iPhone app, phase 3 part D: theme — design

Status: agreed with the owner on 16 November 2026 (chat). It implements row D of
`docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` §6. That spec's §2
records the owner's ruling of 15 November: the iPhone keeps the November redesign's look, and part D
keeps SF Pro, Dynamic Type, SF Symbols in content, Increase Contrast and dark mode, but not the
system's background colours. Parts A–C are merged (pull requests #163–#165).

## 1. Intent

After part C the iPhone has the system's bars and controls, and everything else is drawn as on
Android: Onest text, Material icons, a theme that ignores the app's own theme choice, and no answer
to Increase Contrast. Part D makes the iPhone read and behave like an iPhone app in four respects,
without changing the look the owner agreed on (the lavender ground, white cards with a hairline,
the indigo `#4F46E5` tint, and the wordmark header on Calendar and Home):

- the type is the system's;
- the icons are the system's;
- dark mode, the app's theme choice and Increase Contrast reach every pixel, the native ones included;
- Dynamic Type holds up at large sizes.

**Android does not change.** The 470 screenshot baselines and the detekt baseline stay as they are.
Every change is either in `iosMain`, or behind an `expect`/seam whose Android half is the code that
runs today, line for line.

## 2. Goals, non-goals, anti-goals

**Goals**

1. **Dark mode and the app's theme choice.**
   - Settings' Light/Dark/System choice (`PreferencesRepository.getDarkThemeFlow()`,
     `SettingsViewModel.darkThemeFlow`) is applied on the iPhone, as `MainActivity` applies it on
     Android. Today the shell passes `AppProviders(darkTheme = null)`, so the choice is ignored.
   - The same choice sets the window's `overrideUserInterfaceStyle`: unspecified for System, light
     or dark otherwise. That way the tab bar, navigation bars, alerts, menus, pickers, sheets and the
     status bar match the Compose content. Today a dark system with a light choice would show dark
     native bars over light content.
   - Every in-place native control's `background` (part C's interop rule) follows the theme. It is
     read from the colour scheme in composition, so this should already hold; it is verified, not
     assumed.
   - The redesign's dark ground (`background` → `DarkGroundEnd`) and dark cards are checked on the
     seven boards' screens.
2. **SF Pro.**
   - On the iPhone the type scale is set in the system font (`FontFamily.Default`, which Compose
     Multiplatform draws in SF Pro on iOS), in the same sizes, weights and line heights as
     `Typography`.
   - The wordmark `coplanly.` (`BrandHeader`) stays in Onest Bold, as the boards draw it.
   - Android keeps Onest everywhere.
3. **Dynamic Type.**
   - The font scale already follows the system text size on iOS (`adaptiveDimensions` reads
     `LocalDensity.fontScale`).
   - Part D verifies the large sizes (up to the largest standard size, and AX1–AX5 where a screen
     scrolls) on the screens design item 15 names. Money is never cut, and pairs stack from scale 1.3.
   - It fixes what an iPhone-only layout breaks: native bars, sheets and the date pill.
4. **SF Symbols in content.**
   - Each Material icon the shared UI draws (132 in commonMain at the time of writing) is mapped one
     to one to an SF Symbol and drawn as that symbol on the iPhone.
   - Where no faithful symbol exists, the Material icon stays, and the mapping table says so.
5. **Increase Contrast.**
   - On the iPhone, `rememberSystemContrastLevel()` returns `ContrastLevel.MEDIUM` while
     Settings → Accessibility → Display & Text Size → Increase Contrast is on
     (`UIAccessibilityDarkerSystemColorsEnabled`), and `STANDARD` otherwise.
   - It follows changes while the app runs (`UIAccessibilityDarkerSystemColorsStatusDidChangeNotification`).
   - MEDIUM rather than HIGH is the owner's choice (16 November): Apple's one switch is milder than
     Material's high step.

**Non-goals**

- The system's background colours: the owner kept the redesign's ground and cards (spec §2).
- A new dark palette. Dark mode uses the existing `DarkColorScheme` and the redesign's dark
  partners. A dark tone that fails on the iPhone is fixed in the shared scheme only if Android's
  screenshots prove unchanged; otherwise it is recorded for the owner.
- Push notifications, reminders, background jobs and Sign in with Apple. These are phase 4.
- Native views for icons. An icon is never a `UIKitView`; see §4.

**Anti-goals** (it fails even if it works)

- **Any pixel of Android moves.**
- **An icon disappears, changes meaning or loses its accessibility label on the iPhone.** A
  symbol that reads differently from the Material icon is worse than keeping the Material one.
- **A native view is laid over Compose content to draw a symbol.** Part C learned that this erases
  what it covers.
- **Dark mode where native and Compose disagree** (a light bar over dark content, or the reverse).
- **Text clipped at a Dynamic Type size the screenshot matrix does not render.**

## 3. Constraints

- Compose Multiplatform 1.9.3, Kotlin 2.4.21. Every rule of the toolchain line in `CLAUDE.md`
  holds.
- iOS deployment target as in `iosApp/project.yml`. The SF Symbols used must exist on it; each
  symbol's minimum iOS goes in the mapping table.
- SF Pro must draw the five languages, including Ukrainian і ї є ґ ʼ, ₴ and №. SF Pro covers Latin
  Extended and Cyrillic; the builder confirms ₴ and № on the simulator and records it.
- Every icon's `contentDescription` and semantics stay exactly as the call site gives them.
- The design rules of `CLAUDE.md` design refresh item 1 apply: icon sizes from `IconSizes`, tints
  from the colour scheme and `ParentColors`.
- New user-facing strings, if any, go into all five locales. None are expected.

## 4. Approaches

**Icons.** Three ways were considered.

1. **Rasterise the SF Symbol inside Compose (chosen).**
   - A common composable stands in for `Icon` at every call site. Its Android `actual` is exactly
     `androidx.compose.material3.Icon(imageVector, …)`.
   - Its iPhone `actual` looks the vector's name up in a table (`ImageVector.name`, such as
     `"Filled.Add"`). It draws `UIImage(systemName:)` at the requested size and weight as an
     `ImageBitmap` painted with the call site's tint, cached per symbol, size, weight and scale.
   - Pros: no native view, so no interop hazard; tint, size and semantics behave as before; one
     table.
   - Cons: bitmaps, so a cache, and the symbol is redrawn when the tint or size changes.
2. **A `UIKitView` with a `UIImageView` per icon.** Rejected. It means hundreds of native views in
   lists, it is drawn under the Compose canvas, and it needs an opaque background behind every icon
   on every surface. That is part C's lesson.
3. **Export SF Symbols as SVG into resources.** Rejected. Apple's licence does not allow bundling
   the symbols as artwork. The glyphs must come from the system at run time.

**Type.** Two ways were considered.

1. **An `expect` font family (chosen).** The theme asks for the app's body family; Android answers
   with Onest, the iPhone with `FontFamily.Default`. The wordmark asks for Onest explicitly.
2. **Bundle SF Pro as a resource.** Rejected. Apple's licence allows SF Pro only through the
   system, and the system font also brings Dynamic Type's optical sizes.

**Theme choice.** Only one way fits: the iPhone's `ScreenController` collects the same preference
flow `MainActivity` reads, passes it to `AppProviders`, and sets the window's interface style. A
second store or a per-screen read would let the two disagree.

## 5. How it is built (what the owner and this design decided)

- **One icon entry point.**
  - Every `Icon(imageVector = Icons.…)` in `:shared-ui`'s `commonMain` goes through a common
    composable. The builder names it; `AppIcon` is suggested.
  - This includes the components that take an `ImageVector` and draw it: `SectionRow`, `HeroIcon`,
    `EmptyState`, `InlineBanner`, chips and buttons.
  - Android's half makes the identical Material call. That is why the screenshots cannot move, and
    the screenshot job proves it.
  - A test or check stops a new direct Material `Icon(ImageVector)` call in commonMain, so the
    table cannot fall behind.
- **The mapping table** lives in `iosMain`. It covers every icon in use. Each row records the
  Material name, the SF Symbol name, its minimum iOS, and whether the symbol is filled or outlined
  as the Material one was.
  - A test asserts two things: every icon `commonMain` references has a row, and every symbol
    named loads (`UIImage(systemName:) != null`) on the CI simulator.
  - A row may say "keep Material" with a reason. The canvas board lists those rows.
  - Once the owner approves the board, its symbol choices are binding; the build changes one only
    if it does not load, and then it falls back to Material, never to another symbol unseen.
  - **Minimum iOS** (my call): the deployment target is iOS 17.0 and the CI simulator runs a newer
    iOS, so the table's minimum-iOS column is checked by hand against Apple's SF Symbols data, and
    at run time a symbol that `UIImage(systemName:)` cannot load draws the Material icon. No iOS 17
    runtime is added to CI.
- **Weight and size.** A symbol is drawn at the call site's size (from `IconSizes`), with the
  symbol weight that matches the surrounding text (regular by default, semibold in emphasised
  rows). The builder picks the exact mapping from text weight to symbol weight and writes it down.
- **Mirroring.** `AutoMirrored` icons map to symbols that mirror in right-to-left layouts, or to
  the `.forward`/`.backward` variants. No right-to-left locale ships today; the rule is still kept.
- **SF Pro.**
  - The typography `CoPlanlyTheme` hands Material on the iPhone uses the system family for every
    role.
  - `BrandHeader`'s wordmark sets Onest Bold itself, so it does not depend on the theme's family.
  - Android's `rememberAppTypography` is untouched.
- **Theme choice and window style.**
  - The shell reads `PreferencesRepository.getDarkThemeFlow()`: null means System, true Dark, false
    Light.
  - It passes the value to every `AppProviders(darkTheme = …)` it composes; each hosted screen has
    its own.
  - It sets `overrideUserInterfaceStyle` on the key window and on every presented controller's
    window: alerts, page sheets and picker sheets (part C's `presentWhenSettled`).
  - A change applies at once, with no relaunch.
- **The native tint follows the active colour scheme** (my call, from the anti-goal "native and
  Compose disagree"): the window's `tintColor` is set from `MaterialTheme.colorScheme.primary` of
  the effective theme and contrast level, beside `overrideUserInterfaceStyle`. That is `#4F46E5` in
  the light standard scheme (unchanged), `#C2C1FF` in dark (where `#4F46E5` on a black bar is about
  2.6:1), and the generated scheme's primary at MEDIUM. The `AccentColor` asset stays as the value
  before Compose has drawn. The dark boards show it, so the owner sees it before it is built.
- **Status bar.** It follows the effective style. Part A left `SystemBarsAppearance` a no-op on the
  iPhone; part D makes the hosting controllers report the matching `preferredStatusBarStyle`, or
  relies on the window style if that is enough. The builder decides which and verifies it.
- **Increase Contrast** as in goal 5, through the existing generated `ContrastSchemes`. The
  parent colours, weekend grey and holiday reds do not change with the level, exactly as on
  Android (design item 17).
- **Reduce Motion** already works (`Motion.ios.kt`) and is not touched.

## 6. What the owner sees first (before/after boards)

As in part C, a new kind of thing gets a before/after pair on the canvas
(https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH) before it is built. A fourth row is added with:

1. The seven iPhone boards in **dark**: Home, Calendar, Chat, New event, Family, Expenses,
   Settings.
2. **Type**: one screen's text in Onest (before) and SF Pro (after), with the wordmark still in
   Onest.
3. **Icons**: the mapping table drawn as pairs (Material → SF Symbol), grouped by where they appear,
   with the "keep Material" rows marked.
4. **Increase Contrast**: one screen at the standard and medium levels.

The owner approves the row before the plan is written.

## 7. Tests and CI

- Android: the `screenshots` job verifies the 470 baselines unchanged. `testDebugUnitTest`, lint
  and detekt are unchanged.
- iPhone:
  - an `iosTest` for the mapping table (complete, and every symbol loads);
  - one for the contrast level's mapping;
  - one for the theme choice reaching `overrideUserInterfaceStyle`, through a recorded window;
  - one asserting that the wordmark's family is Onest while body text is the system family.
- The `ios-app` job's walk stays green.
- The tour runs twice, in light and dark (or dark for a subset if the job's time budget demands
  it; the builder decides and records it). It also shots one screen with Increase Contrast and one
  at a large Dynamic Type size, as artifacts beside the canvas.
- The device checklist gains:
  - dark mode switched while the app runs;
  - Increase Contrast on a phone;
  - the largest Accessibility text size on Home, Expenses and the event form;
  - VoiceOver reading icons (labels unchanged).

## 8. Left to the builder

- The composable's name and file, and the exact guard against direct `Icon` calls (a test that
  scans sources, or a detekt rule if one fits without a baseline change).
- Each symbol choice, within the rule "same meaning, same fill". Disputed ones go to the board's
  "keep Material" list rather than being guessed.
- The bitmap cache's size and keys, and whether to draw at the screen's scale.
- How the status bar style is wired (§5).
- Whether the tour's dark pass covers every destination or a subset.

## 9. Open points recorded for the owner

None at writing. The owner agreed the design as proposed on 16 November 2026, including MEDIUM for
Increase Contrast and Onest kept for the wordmark only. Calls made after that, for the owner to
check on the boards: the native tint follows the colour scheme (§5), and the minimum-iOS check is
by hand with a Material fallback (§5). The plan is written only after the owner approves the
fourth row (§6).
