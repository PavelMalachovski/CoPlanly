# Bright, custom and live parent colours — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every parent colour vivid, let a parent pick any colour of their own, and have a
colour change (theirs or the co-parent's) show on both phones within seconds.

**Architecture:** The co-parent's profile becomes a Firestore snapshot listener inside
`PairingRepositoryImpl.observePairingState()` instead of a one-shot read, so name, photo, slot and
colour are live wherever `ParentsSource` is collected. A parent colour becomes a `ParentColor`
(four tones and a hue) built either from a preset (`ParentColorChoice`) or from a stored custom hue
(`"hue1|<degrees>"` in `users/{uid}.colorCode`); tones for a custom hue are derived by pure math
(`ParentToneMath`) at fixed WCAG luminances, so every hue clears the same contrast tests as the
presets. The calendar's custody wash gets per-theme alphas and a theme-aware tone.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3 1.4), Firebase Firestore, JUnit 4 +
kotlin.test + MockK + Turbine.

**Spec:** the owner's request of 2026-10-13 in chat: "make all colours bright — they look dull
now — add a custom colour, and when I or my co-parent change a colour it syncs to my phone"
(answering "blue, bright" to "which colour would you pick").

## Global Constraints

- minSdk 26; no new dependency.
- The four preset stored codes never change: `#E91E63`, `#1976D2`, `#7B1FA2`, `#D84315`.
- A custom colour is stored as `hue1|<0..359>`; anything else unrecognised (including the legacy
  default `#FF4081` older builds wrote for "not chosen") reads as "not chosen".
- Custom hues `158..188` (the calendar friend's teal, `FriendTeal` ≈ 173°) are never drawn.
- Two parents whose drawn hues are under 30° apart are drawn apart: slot 1 keeps theirs, slot 2
  takes the first preset at least 30° away.
- Custody wash alpha: light 0.20, dark 0.30; proposal wash: light 0.12, dark 0.18; borrowed days
  keep `ADJACENT_MONTH_TINT_SCALE` (0.6).
- Dark-theme wash tone = the colour's dark fill, capped at relative luminance 0.30.
- Holiday day numbers: `HolidayRed` `#A31515`, `HolidayRedDark` `#FFA8A0`.
- Contrast held by tests for every preset and every allowed custom hue: fill/darkFill ≥ 3:1 on the
  four surfaces of their theme; text tones ≥ 4.5:1 on them; chip label ≥ 4.5:1; on the custody
  wash over surface and weekend: day number ≥ 4.5, holiday number ≥ 4.5, the colour's own text
  tone ≥ 4.5, a week-view event block's title ≥ 4.5; an event dot ≥ 3:1 against its halo.
- New strings in all five locales; English in sentence case.

## Review Focus

1. A co-parent's listener that fails (permission denied mid-unpair) must not blank a name that
   was already known — keep the last summary; blank only when nothing was ever read.
2. A garbage `colorCode` written by any client (`hue1|`, `hue1|999`, 10 kB of text) must read as
   "not chosen", never crash the palette.
3. An older build reading `hue1|…` draws its slot default — documented, not testable here.
4. Both parents picking near-identical custom hues must still be drawn apart, identically on both
   phones.
5. Unpairing must detach the co-parent listener (no listener leak per pairing transition).

---

### Task 1: Live co-parent profile

**Files:** Modify `app/src/main/java/com/coparently/app/data/repository/PairingRepositoryImpl.kt`;
Test `app/src/test/java/com/coparently/app/data/repository/PairingRepositoryImplTest.kt`.

**Produces:** `observePairingState()` re-emits `Paired` when `users/{partnerId}` changes.

- [ ] Tests: `the co-parent's colour change reaches the paired state as it happens`,
  `a co-parent rename reaches the paired state as it happens`, `a failed co-parent listener keeps
  the profile already read`, `a co-parent listener that fails before any read reports the bare
  partner`, `the co-parent listener is detached when the link ends`; adapt the two existing
  `Paired` tests to emit the partner snapshot through the listener.
- [ ] Implement: own document → `(partnerId, pairedAt)` → `distinctUntilChanged` → `flatMapLatest`
  onto a `callbackFlow` listener on `users/{partnerId}`; bounded retry (3 attempts, 2 s doubling).
- [ ] Update the "three listeners" wording in `ParentsSource`, `TodayWidgetNames` and CLAUDE.md.

### Task 2: Tone math and the custom code

**Files:** Create `presentation/theme/ParentToneMath.kt`, `presentation/theme/ParentColorCode.kt`;
tests `ParentToneMathTest`, `ParentColorCodeTest`.

**Produces:** `ParentToneMath.deriveTones(hue: Float): ParentTones` (ARGB ints `fill`, `darkFill`,
`light`, `dark`), `luminance(argb)`, `contrast(a, b)`, `hueOf(argb)`, `capLuminance(argb, cap)`,
`compositeOver(argb, alpha, background)`; `ParentColorCode.custom(hue)`, `customHueOf(code)`;
`CustomHueRange.hueAt(position)`, `positionOf(hue)`, `allowed(hue)`, `RESERVED`.

- [ ] Tests first: every allowed hue clears the Global Constraints' contrast lines; codec round
  trip and rejection cases; the slider never lands in the reserved band.
- [ ] Implement with HSL at saturation 1 and a binary search on lightness for a target luminance
  (fill 0.20; dark fill 0.27..0.45; text on dark 0.50 at lightness ≥ 0.70; text on light 0.08).

### Task 3: `ParentColor`, brighter presets, palette resolution

**Files:** Modify `presentation/theme/ParentPalette.kt`, `ParentColors.kt`, `Color.kt`; tests
`ParentPaletteTest`, `ParentColorsTest`, `ParentPaletteViewModelTest`, `screenshots/ScreenshotVariant.kt`.

**Produces:** `ParentColor(storedCode, fill, darkFill, light, dark, hue, preset)` with `darkWash`,
`ParentColor.fromStored(code)`, `ParentColor.custom(hue)`, `ParentColorChoice.color`;
`ParentPalette(slot1: ParentColor, slot2: ParentColor)`; `ParentColors.custodyTintAlpha`,
`ParentColors.proposalTintAlpha`, `ParentColors.choiceFill(ParentColor)`.

- [ ] Presets: PINK `#E91E63`/`#FF4081`/`#FF99BB`/`#A0124F`; BLUE `#2979FF`/`#448AFF`/`#9EC2FF`/
  `#1550C0`; PURPLE `#AA00FF`/`#E040FB`/`#EEA2FF`/`#7B1FA2`; ORANGE `#E64A19`/`#FF6E40`/`#FFA98F`/
  `#A02F00` (fill / dark fill / text on dark / text on light).
- [ ] Wash constraints tested over presets and a hue sweep; collision rule tests.

### Task 4: Calendar and screens

**Files:** `calendar/MonthView.kt` (per-theme alphas, dot halo), `calendar/DayWeekView.kt`
(proposal alpha, window label in `onSurface`), `custody/CustodySetupScreen.kt` (default wash),
widget `TodayWidgetLines.kt`/`TodayWidgetContent.kt` (`ParentColor`).

### Task 5: The picker

**Files:** Create `presentation/settings/ParentColorDialog.kt` (dialog + `ParentColorPicker`
content); modify `SettingsScreen.kt`, `SettingsViewModel.kt`; strings `parent_color_custom`,
`parent_color_custom_hue` in five locales; screenshot test of the picker content; ViewModel test.

### Task 6: Docs and baselines

CLAUDE.md (design item 12, July item 6, item 18), `docs/DEVICE-CHECKLIST.md`, and a line in
`.github/regenerate-request` so the Regenerate workflow compiles the branch and records the new
screenshot baselines.
