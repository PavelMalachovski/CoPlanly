# Custody per child (FAM-4)

September 2026. Status: **built** (schema 42); what is left is the Regenerate run, the rules
deploy and a look on a phone. `docs/ROADMAP.md` FAM-4 carries the live status.

## 1. What it is

One schedule per pair stays the default. A **per-child override** is the rarer case — an infant
who stays with one parent, a teenager who negotiated their own alternate weeks — and it replaces
the family pattern **for that child only**. A child with no override follows the family schedule
exactly as today, seasonal layers, contact windows and accepted swaps included.

## 2. Where it lives

Inside the pair's one custody document, `custody_models/{familyId}`, under a new key
`childOverrides` — never a second document per child. Multiplying the documents would multiply
the last-writer comparison (`lastModifiedAtMillis`, SEC-4) that decides which phone's schedule
survives, which is why this item waited for SEC-4 in the first place.

The key holds a list of `ChildOverrideCodec` strings (`domain/custody/ChildScheduleOverride.kt`):

```
C1;child:<childId>;<anchor>;<patternDays>;<slot-1 days>;<windows>
C1;child:baby-1;2026-09-07;1;0;                                   # always with slot 1
C1;child:teen-1;2026-09-07;14;7,8,9,10,11,12,13;2|15:00|19:00|mom # alternate weeks + an afternoon
```

- The child is its `FamilyMemberRef` stored form (FAM-2). A pet, or a kind a newer build knows,
  makes the entry unreadable rather than guessed.
- Windows are `ContactWindowCodec` strings; item 24 holds inside an override.
- `encodeAll` is canonical (sorted, de-duplicated). Unreadable entries — another version, a field
  that does not validate, a second entry for the same child, anything past 16 — are **kept
  verbatim** and written back, as `SeasonalLayerCodec` does, so an older build never erases a
  newer one's override.
- Never Gson over the data class (R8, as every codec in the package says).

## 3. What an override replaces, and what it does not

An override is **complete and self-contained**: a cycle, an anchor, slot 1's days and contact
windows. For its child it answers on every date. Deliberately **not** applied to an overridden
child:

- **The family's seasonal layers.** A family summer of alternating weeks would otherwise move the
  infant whose whole point is staying put. Per-child layers are a later, separate decision.
- **Accepted one-off swaps.** A swap is offered and answered against the grid both parents see,
  which is the family schedule; nobody agreed to it about this child.

Both are conservative readings; either can be widened later without a wire change (a layer or a
swap naming a child is a new field, not a new meaning for an old one).

## 4. The wire rules — item 24's three, under the key `childOverrides`

1. **A missing key is an older build's write**, never "no overrides": the mirror keeps its own copy.
2. **A pattern write always writes the key**, `[]` for none.
3. **Proposal and swap writes carry the stored list verbatim.** `firestore.rules`'
   `childOverridesKeptOrDropped` refuses a proposal-only or swap write that *changes* the list and
   allows one that *drops* it (an older build's `set()`). The key is in both `hasOnly` lists.
   Pinned by `firestore-tests/rules/custody-models.test.js` → "per-child overrides (FAM-4)";
   removing the function's body turns two of those cases red.

**Changing an override is a pattern change**: it goes through `submitPattern`, so a paired family
gets a proposal (the proposal map carries `childOverrides` like it carries `seasonalLayers`), never
an overwrite. Saving the base pattern must carry the agreed overrides, as `withActiveLayers` does
for layers — otherwise every fortnight edit would propose deleting them.

## 5. Where it shows — and it appears at two, never at one

`domain/custody/ChildCustody.kt` answers the three questions; nothing new appears unless the family
has **at least two children and at least one override** (FAM-1).

| Surface | Behaviour |
| --- | --- |
| Calendar grid (`getCustody`) | Stays the family band. Only when FAM-3's member filter narrows to **exactly one member, a child with an override**, does the band follow that child (`overrideForFilter` → `resolver`). No new colour: the band is still a parent's tint. |
| Home's handover hero | Stays the single family sentence unless the children are with different parents that day; then it names each child **with the parent's name** (`whereaboutsOn`) — never a colour, a member is a name (FAM-2). |
| Calendar banners | No new banner. A proposal that changes an override is a proposal, and uses the existing one. |
| Custody setup | A "Different schedule for a child" section, visible at two or more children, opening the same pattern editor scoped to that child. |
| Calendar feed (`functions/calendar-feed.js`) | **Stays the family schedule**, and ignores the key (pinned by a test). A subscribed calendar has no member filter, and titles naming which child is where would be read by whoever glances at the subscriber's calendar. |
| Holiday fairness (MON-20), export | Family schedule, unchanged. |

## 6. How it is wired

- **Room (schema 42):** `custody_models.childOverridesJson TEXT`, nullable, null = none (so a row
  with no overrides is byte-identical to one written before the column, which the mirror's equality
  guard needs). `MIGRATION_41_42` adds it; `CoPlanlyDatabaseMigrationTest.migration41To42_…` proves
  it once the Regenerate workflow exports `42.json`. `ChildOverrideJson` is the Room form: a JSON
  array of the codec strings, never Gson over the data class.
- **Model:** `CustodyModel.childOverrides` / `unreadableChildOverrides`, `childOverridesWire()`,
  `childOverrideFor(childId)`. `complemented` flips each override with the slots; `isEquivalentTo`
  compares the canonical wire lists, so a pairing conflict that differs only in a child's schedule
  is shown to a person rather than settled silently. `getCustodyFor` does not read them.
- **Document:** `SharedCustody.childOverridesWire` and `CustodyProposal.childOverridesWire`, both
  "exactly as stored, or null for no key". `FirestoreCustodyDataSource` writes the key only when the
  wire is non-null, reads a proposal with no key as keeping the agreed overrides, and
  `CustodyProposalTransition` states the list on `propose` and makes it the agreed one on `accept`.
- **Repository:** the mirror keeps its copy for a missing key (`ChildOverrideJson.mirrored`);
  `pushToFirestore` always writes it (`[]` for none); `withActiveLayers` carries the agreed
  overrides into every base-pattern save; `submitChildOverride(childId, override?)` replaces one
  child's entry (null removes it) through `submitPattern`.
- **Calendar:** `ChildCustodyBand.of` returns the family's `GridCustody` unless
  `ChildCustody.overrideForFilter` finds the one child; then the band, the proposal preview and the
  contact windows are the child's, and `followsFamily = false` turns off the swap markers and the
  swap long-press. The swap sheet itself keeps reading the family `getCustody`.
- **Home:** `ChildrenToday.of` → `HomeUiState.Dashboard.childrenToday` → `ChildrenTodayLines` in
  the hero. The children come from `FamilyMembersSource`, added to `HomeIdentityDependencies` so the
  ViewModel's constructor did not grow.
- **Custody setup:** `ChildSchedulesSection` (hidden below two children), `editSchedule(child)`
  scoping the one editor, `ChildScopeHeader` with "Follow the family schedule again"
  (`save(followFamily = true)`). An override has no stored type, so it reopens as a custom pattern
  with the same days; a custom child schedule may give every day to slot 2.

## 7. Not built

- Per-child seasonal layers and per-child one-off swaps (§3). Either is a new field later, not a
  new meaning for this one.
- A marker on an individual event chip saying which child it is about (FAM-5).
- The calendar feed stays the family schedule (§5).
