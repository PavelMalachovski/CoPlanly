# Calendar colours: a colour grid, and a schedule change that keeps the past

Date: 2026-10-24. Owner: Pavel. Status: approach chosen, builder-checked; awaiting owner approval.

Two independent changes, built and reviewed one after the other: **A. the colour grid** and
**B. schedule history**. Either can ship without the other.

## Why

- **A.** Parents get four presets and a hue wheel. The owner wants many more ready colours, picked
  from a grid "as in Google Calendar", with one tap.
- **B.** When Zuzana proposed a new custody schedule and it was accepted, the calendar changed
  colour, which is right, but it also changed **backwards**: past days were repainted as if the new
  schedule had always applied. The past must stay as it was lived.

## A. The colour grid

### What the parent sees

Settings → Family → *My colour* opens a grid of **18 named colours**, five per row, plus a
**Custom colour…** button that opens the existing hue wheel. One tap picks a colour. The mockup
the owner approved is variant A of `palette-mockup.html` (session scratchpad), without Graphite and
Black.

The 18, in this order (names are the working English names; final wording goes into all five
locales):

| # | Name | Reference hex | Note |
|---|---|---|---|
| 1 | Tomato | `#D50000` | new |
| 2 | Flamingo | `#E67C73` | new |
| 3 | Pink | `#E91E63` | **existing `PINK`**, code kept |
| 4 | Tangerine | `#F4511E` | new |
| 5 | Banana | `#F6BF26` | new |
| 6 | Sage | `#33B679` | new |
| 7 | Basil | `#0B8043` | new |
| 8 | Peacock | `#039BE5` | new, hue ≈199°, outside the reserved teal band |
| 9 | Blue | `#2979FF` | **existing `BLUE`**, code kept |
| 10 | Blueberry | `#3F51B5` | new |
| 11 | Lavender | `#7986CB` | new |
| 12 | Grape | `#8E24AA` | new |
| 13 | Purple | `#AA00FF` | **existing `PURPLE`**, code kept |
| 14 | Cocoa | `#795548` | new |
| 15 | Orange | `#E65100` | **existing `ORANGE`**, code kept |
| 16 | Cherry | `#AD1457` | new |
| 17 | Olive | `#827717` | new |
| 18 | Indigo | `#283593` | new |

The reference hex is what the colour is *meant* to be. The tones actually drawn are tuned per
colour (below), and **the swatch in the grid is drawn in the tone the calendar will use** (light
theme `fill`, dark theme `darkFill`), so the grid never promises a colour the calendar does not
draw. The four existing presets keep their current tones exactly.

### Goals / non-goals / anti-goals

- Goal: one-tap choice among 18 named colours; the wheel stays for anything else.
- Goal: a parent who already chose one of the four presets, or a custom hue, sees no change.
- Non-goal: lightness/saturation picking, white, grey, black (grey is the weekend layer, white
  vanishes in the light theme, black in the dark — owner decision: drop them).
- Anti-goal: two parents who cannot be told apart on screen. **Owner decision: "not confused"
  beats "exactly the colour I picked".** The existing rule stays: two colours under 30° of hue
  apart are one colour, a pick beats a default, slot 2 yields to slot 1, the yielding parent is
  drawn in the first grid colour clear of the other (`ParentColor.closeTo`, `ParentPalette`).
  With 18 colours this fires more often (Tomato/Flamingo/Tangerine, Blueberry/Indigo/Lavender,
  Sage/Basil); that is accepted.
- Anti-goal: a fill that fails 3:1 or text that fails AA. Every new colour holds what
  `ParentColorsTest` holds the presets to, in both themes and at both contrast levels, including
  on the custody wash.

### Constraints and how they are met

- **Stored form.** A grid colour is a preset: an entry of `ParentColorChoice` with a fixed
  `storedCode` written to `users/{uid}.colorCode`. Hue-only storage (`hue1|…`) cannot be used:
  Tomato and Flamingo have nearly the same hue. New codes use a new versioned prefix,
  **`sw1|<name>`** (e.g. `sw1|tomato`), never a bare hex — the `#FF4081`-means-"not chosen"
  problem `ParentColorCode` documents applies to any hex. The four existing entries keep their
  hex codes.
- **Older builds** read an `sw1|…` code as "not chosen" and draw the slot default, exactly as they
  already do for `hue1|…`. Accepted.
- **Tones are hand-set per entry** (`fill`, `darkFill`, `light`, `dark`), as the four presets are,
  starting from the reference hex and adjusted only as far as the tests require. Pastels (Banana,
  Flamingo, Lavender, Sage) will draw darker than their reference in the light theme.
- `ParentColorsTest` iterates `ParentColorChoice.entries`, so the new entries are covered by
  adding them; add a test that every stored code round-trips and no two codes collide.
- `ParentPalette`'s fallback ("first preset clear of the other", `firstPresetClearOf`) **keeps
  its own order, not the grid's**: Pink, Blue, Purple, Orange first, then the new colours. Walking
  the grid order would turn an existing "both chose Pink" family's second parent from Blue to
  Tangerine, breaking the no-change goal. `ParentPaletteTest`'s "first preset clear of pink is
  BLUE" stays; its "no two presets collide" assertion is narrowed to the four original presets.
  (My call, from the stated goal.)
- Teal stays out of reach (`CustomHueRange.RESERVED`); no grid colour is in 158°–188°.
- Each colour has a name string in all five locales (`values`, `-cs`, `-de`, `-ru`, `-uk`) for the
  label and TalkBack; the grid cell's content description is the name, selected state announced.
- Screenshot baselines for the "My colour" picker change → run Regenerate on the branch.
- Design item 12 of `CLAUDE.md` describes "four presets"; update it in the same PR.

### Builder's calls (mine, open to the owner)

- Onboarding's profile step keeps the four presets it shows today (CLAUDE.md design item 12 says
  so); only Settings gets the grid. Cheap to change later.
- Grid cell size ≥ 48 dp touch target; five columns at phone width.

## B. Schedule history: a change applies from its start date forward

### Behaviour (owner decisions)

- **Paired, change through a proposal:** the new schedule applies from
  **E = max(the start date the proposer chose, the day the co-parent accepted)**. Every day before
  E keeps the schedule that was in force, colour and all. Examples: proposed from 1 Oct, accepted
  10 Oct → old schedule to 9 Oct, new from 10 Oct. Proposed from 1 Nov, accepted 10 Oct → old to
  31 Oct, new from 1 Nov.
- **Repeated changes** each freeze their own stretch: the past becomes a sequence of periods,
  each drawn with the schedule that applied then.
- **Unpaired (no co-parent, or the pair has no shared document yet):** saving the schedule
  rewrites everything, past included. This is today's behaviour (`saveAndActivate`); a parent
  correcting a setup mistake must be able to.
- Accepted day swaps on past days stay as they are (they already sit above everything in
  `CustodyResolver`). Real seasonal layers on past dates (a past summer) still win over the frozen
  history.

### Approach (owner chose B)

| | A. New history key | **B. Frozen past as seasonal layers (chosen)** |
|---|---|---|
| Model | separate list of past periods in the custody document | the old pattern becomes one or more `SeasonalLayer`s covering the days before E |
| Older builds | do not understand the key → their past repaints | already draw layers → past is kept on both phones at once |
| Rules / feed / export | new key in rules, feed port, export | none: `seasonalLayers` is already carried, ported and resolved |
| Cost | — | history layers must be hidden from the layer editor; an older build shows them in its list and could delete one |

Chosen: **B**, because the point is that the past stops repainting, and only B achieves that while
the co-parent may still be on an older build.

### How it works

1. **Where.** `CustodyProposalTransition.accept` (pure, `domain/custody/`) builds the accepted
   document. It gains the acceptance date (the accepter's local date, from `atMillis` and the
   device zone, passed in) and appends history layers to the accepted model's
   `seasonalLayers` before encoding `seasonalLayersWire`. Accepting is already a pattern write,
   which the rules allow to change `seasonalLayers` (`firestore.rules`, the
   `proposalWriteTouchesOnlyProposal` comment). No rule changes.
2. **When a history layer is made.** Only when the base pattern actually changes for days before
   E would otherwise be repainted: skip when the old and new base patterns agree
   (`CustodyModel.isEquivalentTo` on the base, ignoring layers), and skip when E is on or before
   the oldest day to freeze (nothing in the past to keep).
3. **What it holds.** The **old base pattern** as a layer: its `patternDays`, `momDayIndices`,
   `startDate` (the old anchor, so the rhythm is exact) and its `contactWindows`. Lowest
   priority, `SeasonalLayer.PRIORITY_RANGE.first` (−1000), so any real layer covering the same
   days still decides them, and the history beats the new base pattern.
4. **Which days.** From `F` to `E − 1`, where `F` is the day after the newest existing history
   layer ends, or, when there is none, the old pattern's anchor date. Days before the very first
   anchor were never a schedule anyone agreed, so they are not frozen. History layers therefore
   never overlap each other.
5. **Size limits.** A layer covers at most 366 days (`SeasonalLayer.MAX_LAYER_DAYS`) — the range
   is split into consecutive chunks of ≤366 days, each a layer. A document holds at most 32 layers
   in all (`SeasonalLayerCodec.MAX_LAYERS`). History is capped at **3 years before E**
   (≤ 4 chunks per change); when the total would exceed 32, the **oldest history** layers are
   dropped first, never a real layer. Do not raise `MAX_LAYER_DAYS`: an older build would read a
   longer layer as unreadable and stop drawing it.
6. **Marking.** History layers have ids `hist-<E as yyyyMMdd>-<n>` (the id regex allows
   `[A-Za-z0-9-]`). `SeasonalLayer.isHistory` reads the prefix. The name is data in the
   accepter's language, e.g. "Earlier schedule (until 9 Oct 2026)", from a new string in all five
   locales; it is what an older build's layer list shows.
7. **Hidden, and carried.** The seasonal-layer editor and holiday fairness's "Propose a change"
   list do not show `isHistory` layers. Every path that rewrites the layer list keeps them
   verbatim: `withActiveLayers` already carries all active layers; `submitSeasonalLayers` must
   merge the edited real layers with the existing history layers instead of replacing the list.
   Holiday fairness keeps counting what is drawn (history included) — it reads the resolver.
8. **Proposal preview.** The pending-proposal preview on the grid and the proposal clock's "first
   day the schedule would change" must start at `max(proposal start date, today)`, not repaint the
   past in preview. Check `CustodyPatternDiff` and the preview fill in `DayCellFills`.
9. **Unpaired path unchanged.** `saveAndActivate` makes no history; it may keep or drop existing
   history layers as the parent's list says (they are invisible to the editor, so keep them —
   the builder's call is to carry them, as `withActiveLayers` does).

### What follows for free (verify, don't build)

- Calendar feed: `functions/calendar-feed.js` ports `SeasonalLayerCodec` and `PRECEDENCE`; history
  layers are served. Add a fixture with a history layer to the shared fixtures.
- Export, Home, handover walk, widget: all read `CustodyResolver`.
- `CustodyModel.complemented()` (re-slot) swaps layers' slots; history layers included.
- Wire contracts: add a `custody_models` fixture carrying a `hist-` layer to `wire/` and regenerate
  `wire/current/`.

### Non-goals

- A child's own schedule (`childOverrides`, FAM-4) does not get history; an overridden child's
  past may still repaint when their override changes. Possible follow-up.
- A change that only edits real seasonal layers (no base-pattern change) creates no history; a
  deleted past summer layer repaints that summer. Follow-up if it matters.
- **An accepter on an older build makes no history** (owner decision, accepted loss): history is
  written only by `CustodyProposalTransition.accept` on the accepting phone; an older build writes
  the proposal as it stands, and the past repaints as today. No second writer on the proposer's
  phone. It goes away once both parents have updated.
- Repairing schedules already repainted before this ships: nothing records what the old pattern
  was. Such a family sees the current colours for the past.
- No new Firestore key, rule, Room column or schema bump.

### Tests

- `CustodyProposalTransitionTest`: the two owner examples; repeated changes produce
  non-overlapping history; equivalent pattern → no history; chunking at 366 days; 3-year cap;
  32-layer budget drops oldest history first; a real past layer still wins over history; contact
  windows preserved.
- `CustodyModelTest`/resolver: the days before E resolve to the old parent, from E the new one.
- Seasonal editor ViewModel: history layers hidden and preserved on save.
- `functions/test/calendar-feed.test.js`: fixture with a history layer.
- e2e `OnScreenAgreementsTest` (custody proposal answered on Home): after acceptance a past day
  keeps the old owner.

### Docs

`CLAUDE.md` item 30 (seasonal layers) gains a paragraph: history layers, their id prefix, lowest
priority, carried verbatim, hidden from the editor. Design item 12 (four presets → grid).
`docs/DEVICE-CHECKLIST.md`: an older build on the second phone keeps the past after acceptance.

## Left to the builder

Exact tone values per colour (within the tests), the precise string wording in five locales, and
the internal shape of the chunking helper.
