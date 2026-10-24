# Calendar colours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An 18-colour named grid for a parent's colour, and accepted custody proposals that change the schedule only from their start date forward.

**Architecture:** Part A adds 14 entries to the `ParentColorChoice` enum (stored as `sw1|<name>` codes) and replaces the picker's radio list with a grid; the collision fallback keeps walking the enum, whose first four entries stay the original presets. Part B adds a pure `CustodyHistory` helper that turns the outgoing base pattern into lowest-priority `SeasonalLayer`s (`hist-…`) covering the days before the new schedule's effective date; `CustodyProposalTransition.accept` calls it, the layer editor hides those layers, and the calendar's proposal preview starts at the effective date. No Firestore key, rule, Room column or schema change.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 1.4), JUnit4 + MockK, Roborazzi screenshots, mocha (Cloud Functions).

**Spec:** `docs/superpowers/specs/2026-10-24-calendar-colours-design.md`

## Global Constraints

- New user-facing strings go into all five locales (`values`, `values-cs`, `values-de`, `values-ru`, `values-uk`) in the same commit; `node tools/check-invariants.js` must pass.
- English is sentence case.
- No literal hex in screens; colours through `ParentColors` / `ParentColor`. Corners from `theme/Shape.kt`, spacing from `theme/Spacing.kt`, icon/swatch sizes are named constants.
- KDoc on public classes/functions; code and comments in English.
- `minSdk = 26`: no `LocalDate.ofInstant`; use `Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()`.
- Do not raise `SeasonalLayer.MAX_LAYER_DAYS` (366) or `SeasonalLayerCodec.MAX_LAYERS` (32): older builds would stop drawing.
- Existing preset stored codes (`#E91E63`, `#1976D2`, `#7B1FA2`, `#D84315`) and tones are unchanged.
- Conventional Commits; run `./gradlew assembleDebug testDebugUnitTest` before each commit, `./gradlew lint detekt` before the last commit of each part.

## Review Focus

1. **Two parents both chose Pink (existing family)** → slot 2 still draws Blue after the change, not a new grid colour. Pinned in Task 1 (`ParentPaletteTest`, existing assertion kept).
2. **Proposal accepted the same day as its start date, or with a start date in the past** → effective date is the acceptance day; yesterday stays old. Pinned in Task 3.
3. **A second change months after the first** → history layers do not overlap, earlier history is untouched, and the budget of 32 layers never drops a real (non-history) layer. Pinned in Task 3.
4. **Parent edits the seasonal layers after a history layer exists** → history layers are neither shown nor deleted by the save. Pinned in Task 5.
5. **Old base pattern had contact windows** → the frozen past keeps its afternoons. Pinned in Task 3.

---

## Part A — the colour grid

### Task 1: Fourteen new presets

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/theme/ParentPalette.kt` (enum `ParentColorChoice`, companion, tone constants at the bottom)
- Modify: `app/src/main/res/values/settings_strings.xml` and the four locale variants (wherever `parent_color_pink` lives: `git grep -l 'name="parent_color_pink"' app/src/main/res`)
- Test: `app/src/test/java/com/coparently/app/presentation/theme/ParentPaletteTest.kt`, `ParentColorsTest.kt` (no edit expected; it iterates `entries`)

**Interfaces:**
- Produces: enum entries, **in this declaration order**: `PINK, BLUE, PURPLE, ORANGE` (unchanged, first) then `TOMATO, FLAMINGO, TANGERINE, BANANA, SAGE, BASIL, PEACOCK, BLUEBERRY, LAVENDER, GRAPE, COCOA, CHERRY, OLIVE, INDIGO`.
- Produces: `ParentColorChoice.GRID: List<ParentColorChoice>` — display order `TOMATO, FLAMINGO, PINK, TANGERINE, BANANA, SAGE, BASIL, PEACOCK, BLUE, BLUEBERRY, LAVENDER, GRAPE, PURPLE, COCOA, ORANGE, CHERRY, OLIVE, INDIGO`.
- Produces: `ParentColorChoice.ORIGINAL: List<ParentColorChoice>` = `listOf(PINK, BLUE, PURPLE, ORANGE)` (onboarding).
- Produces: `const val ParentColorCode.SWATCH_PREFIX = "sw1|"`; new entries' `storedCode` = `"sw1|" + name.lowercase()` (e.g. `sw1|tomato`).

- [ ] **Step 1: Write the failing tests** in `ParentPaletteTest`:

```kotlin
@Test
fun `every grid colour round-trips through its stored code`() {
    ParentColorChoice.entries.forEach { choice ->
        assertEquals(choice, ParentColorChoice.fromStored(choice.storedCode))
        assertEquals(choice.color, ParentColor.fromStored(choice.storedCode))
    }
    assertEquals(ParentColorChoice.entries.size, ParentColorChoice.entries.map { it.storedCode.uppercase() }.toSet().size)
}

@Test
fun `the grid holds eighteen colours, every entry once`() {
    assertEquals(18, ParentColorChoice.GRID.size)
    assertEquals(ParentColorChoice.entries.toSet(), ParentColorChoice.GRID.toSet())
}

@Test
fun `new colours are stored under the swatch prefix and the four originals keep their hex`() {
    assertEquals("sw1|tomato", ParentColorChoice.TOMATO.storedCode)
    assertEquals(listOf("#E91E63", "#1976D2", "#7B1FA2", "#D84315"), ParentColorChoice.ORIGINAL.map { it.storedCode })
}

@Test
fun `no grid colour sits in the calendar friend's teal band`() {
    ParentColorChoice.entries.forEach { assertEquals(it.name, it.color.hue.roundToInt(), CustomHueRange.allowed(it.color.hue.roundToInt())) }
}
```

Change the existing `no two presets are close enough to collide` to iterate `ParentColorChoice.ORIGINAL` instead of `entries`. Keep every "first preset clear of pink is BLUE" assertion as it is.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*ParentPaletteTest*'` — Expected: FAIL (unresolved `TOMATO`, `GRID`, `ORIGINAL`).

- [ ] **Step 3: Implement.** Add the 14 entries with four tone constants each (`fill`, `darkFill`, text-on-dark, text-on-light — same constructor order as `PINK`), named `<NAME>_FILL`, `<NAME>_DARK_FILL`, `<NAME>_TEXT_ON_DARK`, `<NAME>_TEXT_ON_LIGHT`. Start `fill` from the spec's reference hex (Tomato `#D50000`, Flamingo `#E67C73`, Tangerine `#F4511E`, Banana `#F6BF26`, Sage `#33B679`, Basil `#0B8043`, Peacock `#039BE5`, Blueberry `#3F51B5`, Lavender `#7986CB`, Grape `#8E24AA`, Cocoa `#795548`, Cherry `#AD1457`, Olive `#827717`, Indigo `#283593`) and darken/lighten only as far as `ParentColorsTest` requires (pastels will move most). Add `SWATCH_PREFIX` to `ParentColorCode` with KDoc explaining why it is not a hex (copy the reasoning of `CUSTOM_PREFIX`). Add `GRID`, `ORIGINAL` in the companion with KDoc; update the enum's KDoc ("One of the four colours…" → eighteen, and why enum order ≠ grid order: `firstPresetClearOf` walks `entries`, so the originals stay first and no existing family's fallback colour moves). Add 14 strings `parent_color_<name>` in five locales (working English names: Tomato, Flamingo, Tangerine, Banana, Sage, Basil, Peacock, Blueberry, Lavender, Grape, Cocoa, Cherry, Olive, Indigo).

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*ParentPaletteTest*' --tests '*ParentColorsTest*' --tests '*ParentColorCodeTest*' && node tools/check-invariants.js` — Expected: PASS. If `ParentColorsTest` fails, adjust that colour's tones, never the test.

- [ ] **Step 5: Commit** `feat: add fourteen named parent colours`.

### Task 2: The grid picker

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/settings/ParentColorDialog.kt` (`ParentColorPicker`, `ColourOption`, `colourFor`)
- Modify: `app/src/main/java/com/coparently/app/presentation/onboarding/OnboardingScreen.kt:505` (`entries` → `ParentColorChoice.ORIGINAL`)
- Modify: `app/src/test/java/com/coparently/app/screenshots/FeatureScreenshots.kt` (picker screenshot, if it needs a preset that still exists — it should not need changes)
- Modify: `CLAUDE.md` design refresh item 12 ("one of four presets" → "one of eighteen grid colours … onboarding keeps the four originals")
- Test: new `app/src/test/java/com/coparently/app/presentation/settings/ParentColorPickerTest.kt` (Robolectric Compose, as other `presentation/settings` UI tests do)

**Interfaces:**
- Consumes: `ParentColorChoice.GRID`, `ParentColorChoice.ORIGINAL`, `ParentColors.choiceFill(choice)` (Task 1).
- `ParentColorPicker(chosen, customHue, onChoose, onCustomHue, modifier)` keeps its signature.

- [ ] **Step 1: Write the failing test** `ParentColorPickerTest`:
  - `the grid shows every colour by name` — set content with `chosen = "PINK"`; for each `GRID` entry assert a node with content description `stringResource(labelRes)` exists and is selectable; the `PINK` node `assertIsSelected()`.
  - `tapping a swatch chooses it` — click the Tomato node; assert `onChoose` received `"TOMATO"`.
  - `custom colour stays a separate option and opens the wheel` — click the node labelled `parent_color_custom`; assert `onChoose("CUSTOM")`; with `chosen = CUSTOM_COLOUR` the node with content description `parent_color_custom_hue` exists.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*ParentColorPickerTest*'` — Expected: FAIL (no Tomato node).

- [ ] **Step 3: Implement.** Replace the `entries.forEach { ColourOption … }` radio rows with a grid of `GRID` in rows of 5 (`GRID.chunked(GRID_COLUMNS)` into `Row`s with `Arrangement.spacedBy(Spacing.S)` — not a `LazyVerticalGrid`, the body already scrolls inside the dialog). Each cell: a `Box` of `SWATCH_CELL_SIZE = 40.dp` circle filled with `ParentColors.choiceFill(choice)`, `Modifier.selectable(selected, role = Role.RadioButton)` with `minimumInteractiveComponentSize()`, `semantics { contentDescription = label }`, a selected ring drawn with `MaterialTheme.colorScheme.onSurface` border (`IconSizes`/`Dimensions` style constant for the 3 dp width, named `SELECTED_RING_WIDTH`) and a check icon (`Icons.Filled.Check`, `IconSizes.Small`, tinted `ParentColors.onFill(...)`). Below the grid keep the existing "Custom" `ColourOption` row and `HueSlider`. `colourFor` keeps resolving by `ParentColorChoice.entries`. Update `ParentColorPicker`'s KDoc ("the four presets" → the grid).

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*ParentColorPickerTest*' && ./gradlew assembleDebug` — Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Accept the new screenshots.** Touch `.github/regenerate-request` (commit it with this task) so the Regenerate workflow re-records the picker baselines; note in the commit body that the "My colour" baselines change on purpose.

- [ ] **Step 6: Commit** `feat: pick a parent colour from a grid`.

---

## Part B — a schedule change keeps the past

### Task 3: `CustodyHistory`, the pure freezer

**Files:**
- Create: `app/src/main/java/com/coparently/app/domain/custody/CustodyHistory.kt`
- Modify: `app/src/main/java/com/coparently/app/domain/custody/SeasonalLayer.kt` (add `isHistory`)
- Test: `app/src/test/java/com/coparently/app/domain/custody/CustodyHistoryTest.kt`

**Interfaces:**
- Produces:
  - `val SeasonalLayer.isHistory: Boolean` — `id.startsWith(CustodyHistory.ID_PREFIX)`.
  - `object CustodyHistory` with
    - `const val ID_PREFIX = "hist-"`
    - `const val PRIORITY = -1000` (= `SeasonalLayer.PRIORITY_RANGE.first`)
    - `const val MAX_YEARS_BACK = 3L`
    - `fun effectiveFrom(proposed: CustodyModel, acceptedOn: LocalDate): LocalDate` = `maxOf(proposed.startDate, acceptedOn)`
    - `fun freeze(outgoing: CustodyModel, incoming: CustodyModel, acceptedOn: LocalDate): CustodyModel` — `incoming` with history layers merged into `seasonalLayers`.
    - `fun layerName(until: LocalDate): String` = `"Earlier schedule until $until"` (ISO date; stored data, never translated — same status as a parent-typed layer name. This replaces the spec's "string in five locales": the repository has no `Context`, and stored text must not carry one parent's language. Update the spec's §B.6 line in this task's commit.)

`freeze` rules (from the spec, §B "How it works"):
1. `e = effectiveFrom(incoming, acceptedOn)`.
2. Return `incoming` unchanged when the base patterns agree — compare `outgoing.copy(seasonalLayers = emptyList(), unreadableLayers = emptyList(), childOverrides = emptyList(), unreadableChildOverrides = emptyList())` with the same of `incoming` via `isEquivalentTo` — or when `outgoing.patternDays <= 0`.
3. Existing history = `(outgoing.seasonalLayers + incoming.seasonalLayers).filter { it.isHistory }`, de-duplicated by id (incoming carries them already when the proposal was built through `withActiveLayers`).
4. `from = max(lastHistory.toDate + 1 day ?: outgoing.startDate, e.minusYears(MAX_YEARS_BACK))`; `to = e - 1 day`. If `from > to`, no new layers.
5. Split `[from, to]` into consecutive chunks of ≤ `SeasonalLayer.MAX_LAYER_DAYS` days, oldest first. Each chunk: `SeasonalLayer(id = "hist-${e.format(BASIC_ISO_DATE)}-$n", name = layerName(to), fromDate, toDate, patternDays = outgoing.patternDays, momDayIndices = outgoing.momDayIndices, startDate = outgoing.startDate, contactWindows = outgoing.contactWindows, priority = PRIORITY)`. Skip (no history) when `outgoing.patternDays > MAX_LAYER_DAYS`.
6. Result layers = real (non-history) layers of `incoming` + all history (existing + new). While `real.size + history.size + incoming.unreadableLayers.size > SeasonalLayerCodec.MAX_LAYERS`, drop the history layer with the earliest `fromDate`. Never drop a real layer.

- [ ] **Step 1: Write the failing tests** in `CustodyHistoryTest` (old = 14-day week-on/week-off anchored 2026-08-03 with slot 1 on indices 0..6; new = same cycle with 7..13):
  - `accepted after its start date, the new schedule starts on the acceptance day` — start 2026-10-01, accepted 2026-10-10: `freeze(...).getCustodyFor(2026-10-09) == old.getCustodyFor(2026-10-09)`, `getCustodyFor(2026-10-10) == new.getCustodyFor(2026-10-10)`.
  - `accepted before its start date, the old schedule runs to the day before it` — start 2026-11-01, accepted 2026-10-10: day 2026-10-31 old, 2026-11-01 new.
  - `the same pattern makes no history` — `freeze(old, old.copy(id = "x"), d).seasonalLayers.none { it.isHistory }`.
  - `history reaches back to the old anchor and no further than three years` — old anchor 2020-01-06, e = 2026-10-10: earliest history `fromDate == 2023-10-10`; every layer `spanDays <= 366`; layers contiguous (each `fromDate == previous.toDate + 1`), last `toDate == 2026-10-09`.
  - `a second change freezes only its own stretch` — freeze old→new at 2026-10-10, then new→third at 2027-02-01: first-change layers unchanged; new layers start 2026-10-10 and use `new`'s pattern; 2026-09-01 still old, 2026-12-01 new, 2027-02-01 third.
  - `a real layer on a past date still decides it` — outgoing and incoming carry a real layer (priority 0) covering 2026-07-01..2026-08-31 giving everything to slot 2: `getCustodyFor(2026-07-15) == "dad"` after freeze.
  - `the frozen past keeps its contact windows` — old with `ContactWindow(dayIndex = 9, 15:00, 19:00, "dad")`: `contactWindowsOn` a past day at index 9 returns it.
  - `the layer budget drops the oldest history, never a real layer` — incoming with 30 real layers (non-overlapping one-day layers in 2027), old anchor 2020: result has all 30 real layers, `size <= 32`, and the history kept is the newest (last `toDate == e - 1`).
  - `history layers carry the lowest priority and the hist- id` — every new layer `priority == -1000`, `id.startsWith("hist-")`, `isHistory`.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CustodyHistoryTest*'` — Expected: FAIL (unresolved `CustodyHistory`).

- [ ] **Step 3: Implement** `CustodyHistory` and `isHistory` per the rules above, with KDoc on the object stating the why (owner decision 2026-10-24: a change after pairing applies forward only; layers chosen so older builds, the feed and the export draw the past without a new key).

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*CustodyHistoryTest*' --tests '*SeasonalLayerTest*'` — Expected: PASS.

- [ ] **Step 5: Commit** `feat(custody): freeze the outgoing schedule as history layers`.

### Task 4: Accepting a proposal freezes the past

**Files:**
- Modify: `app/src/main/java/com/coparently/app/domain/custody/CustodyProposalTransition.kt:96-122` (`accept`)
- Modify: `app/src/main/java/com/coparently/app/data/repository/CustodyModelRepository.kt:488-490` (`acceptProposal`)
- Test: `app/src/test/java/com/coparently/app/domain/custody/CustodyProposalTransitionTest.kt`, `app/src/test/java/com/coparently/app/data/repository/CustodyModelRepositoryTest.kt`

**Interfaces:**
- Consumes: `CustodyHistory.freeze(outgoing, incoming, acceptedOn)` (Task 3).
- Produces: `fun accept(current: SharedCustody, byUid: String, atIso: String, atMillis: Long, acceptedOn: LocalDate): Result<SharedCustody>` — `model = CustodyHistory.freeze(current.model, pending.model, acceptedOn)`, and `seasonalLayersWire` encoded from that frozen model (not from `pending.model`). Everything else unchanged.

- [ ] **Step 1: Write the failing tests.**
  - `CustodyProposalTransitionTest`: `accepting keeps the days before the start date with the old pattern` — `current` agreed (anchored 2026-08-03), `pending` proposes `wanted` with `startDate = 2026-08-10`, accept with `acceptedOn = 2026-08-09`: `next.model.getCustodyFor(2026-08-05) == agreed.getCustodyFor(2026-08-05)`, `next.model.getCustodyFor(2026-08-10) == wanted.getCustodyFor(2026-08-10)`, and `next.seasonalLayersWire` contains an entry starting `"L1;hist-20260810-"`. Update the three existing `accept(...)` calls to pass `acceptedOn = LocalDate.of(2026, 8, 9)`.
  - `CustodyModelRepositoryTest`: in the existing accept test (find it by `acceptProposal()`), assert the document written through `firestoreCustodyDataSource.transact`/`setCustody` (whichever `decideProposal` uses — mirror the existing verification) carries a `hist-` layer when the patterns differ.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CustodyProposalTransitionTest*' --tests '*CustodyModelRepositoryTest*'` — Expected: FAIL (wrong arity / no hist layer).

- [ ] **Step 3: Implement.** `accept` gains `acceptedOn` and freezes as above (update its KDoc: the accepted pattern applies from `CustodyHistory.effectiveFrom`, older days keep the outgoing one). In `acceptProposal`, pass `Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()` — the accepter's own date. `saveAndActivate` (unpaired) is untouched: unpaired saves rewrite the past by owner decision.

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*Custody*'` — Expected: PASS.

- [ ] **Step 5: Commit** `feat(custody): an accepted schedule change keeps the past`.

### Task 5: History is invisible in the editor and the preview starts at the effective date

**Files:**
- Modify: `app/src/main/java/com/coparently/app/presentation/custody/SeasonalScheduleViewModel.kt:159` (`layers = …`)
- Modify: `app/src/main/java/com/coparently/app/presentation/calendar/CalendarScreen.kt:318-325` (`getProposedCustody`)
- Test: `app/src/test/java/com/coparently/app/presentation/custody/SeasonalScheduleViewModelTest.kt`, and a pure test for the preview rule (below)

**Interfaces:**
- Consumes: `SeasonalLayer.isHistory`, `CustodyHistory.effectiveFrom` (Task 3).
- Produces: nothing new; `CalendarScreen` calls `CustodyHistory.effectiveFrom(proposed, LocalDate.now())` directly.

- [ ] **Step 1: Write the failing tests.**
  - `SeasonalScheduleViewModelTest`: `history layers are not listed` — active model with one real and one `hist-` layer: `state.layers.map { it.id } == listOf(realId)`.
  - `SeasonalScheduleViewModelTest`: `saving a layer keeps the history layers` — `saveLayer(...)` for a new draft: the list passed to `custodyModelRepository.submitSeasonalLayers` contains the `hist-` layer unchanged.
  - `SeasonalScheduleViewModelTest`: `deleting a layer keeps the history layers` — same with `deleteLayer(realId)`.
  - `CustodyHistoryTest`: `the preview shows nothing before the effective date` — `effectiveFrom(proposed with start 2026-10-01, today 2026-10-24) == 2026-10-24`.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*SeasonalScheduleViewModelTest*' --tests '*CustodyHistoryTest*'` — Expected: the "not listed" test FAILS; the save/delete ones may already pass (the edits work on `active.seasonalLayers`) — keep them as guards.

- [ ] **Step 3: Implement.** `layers = model?.seasonalLayers.orEmpty().filterNot { it.isHistory }`. Check any overlap/limit check in the editor ignores history layers (grep `seasonalLayers` in `presentation/custody/`), and `HolidayFairnessCard`'s "Propose a change" draft is built from real layers only. In `CalendarScreen`, `getProposedCustody` returns `null` for `date < CustodyHistory.effectiveFrom(proposed, today)` where `today = LocalDate.now()` captured in the `remember`; add `today` to its keys only if the screen already has a date-change key (it does not need one otherwise). `RequestCap.forProposal` already scans from today — leave it.

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*Seasonal*' --tests '*Custody*' && ./gradlew assembleDebug` — Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** `feat(custody): hide history layers from the editor and the preview`.

### Task 6: The feed, the wire contract and the docs

**Files:**
- Modify: `functions/test/calendar-feed.test.js` (beside `picks the higher priority where layers overlap…`, ~line 280)
- Create: `app/src/test/resources/wire/custody_models/history-layer.json` (copy `pattern-write.json`'s shape)
- Modify: `CLAUDE.md` ("Things that are easy to get wrong" item 30), `docs/DEVICE-CHECKLIST.md` (custody section), `docs/superpowers/specs/2026-10-24-calendar-colours-design.md` (status line)

- [ ] **Step 1: Write the tests.**
  - `calendar-feed.test.js`: `serves a history layer below a real layer and above the base pattern` — `seasonalLayers: ['L1;hist-20261010-0;-1000;2026-03-01;2026-10-09;2026-08-03;14;0,1,2,3,4,5,6;;Earlier%20schedule%20until%202026-10-09', <a priority-0 July layer>]` with a base pattern giving 2026-09-01 to the other parent: 2026-09-01 resolves to the history's parent, a July day to the July layer's parent, 2026-10-10 to the base pattern's.
  - `history-layer.json`: a `custody_models` document whose `seasonalLayers` holds that `hist-` string; its `reads` expectation names the layer as readable and its round trip loses nothing (follow how `pattern-write.json` declares `reads`/`notPreserved`).
- [ ] **Step 2: Run** `cd functions && npm test && npm run lint` and `./gradlew testDebugUnitTest --tests '*WireContractTest*' --tests '*CurrentWireFixturesTest*'` — Expected: PASS (the feed already ports the codec; if it does not, fix `functions/calendar-feed.js`'s decoder to accept a negative priority).
- [ ] **Step 3: Docs.** CLAUDE.md item 30, one paragraph: history layers (`hist-` prefix, priority −1000, made only by `CustodyProposalTransition.accept` through `CustodyHistory`, at most three years and inside the 32-layer budget, hidden from the editor, carried verbatim like every layer, never by an unpaired save; an older-build accepter makes none — accepted loss, owner decision 2026-10-24). DEVICE-CHECKLIST: "Accept a proposal whose start date is in the past on one phone; on both phones (one on the previous build) a day before acceptance keeps its old colour." Spec status → "approved; implemented".
- [ ] **Step 4: Run** `node tools/check-invariants.js && ./gradlew lint detekt` — Expected: PASS.
- [ ] **Step 5: Commit** `test(custody): history layers in the feed and the wire contract; document them`.
