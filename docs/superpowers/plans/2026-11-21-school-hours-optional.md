# School hours as an option — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A per-connection switch decides whether a school import creates school-hours events.

**Architecture:** A `SchoolConnection.importHours` flag, stored in the sealed connection record only when
off; the pure `SchoolImportPlanner` drops and deletes hours when the input says so; the import screen
shows a switch that saves and re-runs the import.

**Tech Stack:** Kotlin Multiplatform (`:shared`, `:shared-data`, `:shared-ui`), Compose Multiplatform.

**Spec:** `docs/superpowers/specs/2026-11-21-school-hours-optional-design.md`

## Global Constraints

- New connection: `importHours = false`. A stored record without the key reads `true`.
- The record carries `"importHours": false` only when off; nothing is written when on.
- Off deletes only `hours` events this connection created dated today or later, through the ordinary
  delete path; past ones, days off, events, tests and homework are never touched.
- Every new string in `values`, `values-cs`, `values-de`, `values-ru`, `values-uk`, sentence case.

## Review Focus

- A parent edited an hours event by hand, then switches hours off: it is deleted (it is the import's
  own kind); covered by the planner test using a live, edited event.
- Off then on again: hours come back for the coming days (the ledger dropped them).
- An hours event already pending deletion (Room tombstone) while off: not deleted twice.
- A Bakaláři record written before this change decodes with `importHours == true`.
- Switching while an update runs: the toggle saves, then runs the same `sync` "Update now" runs, which
  is serialised per connection already.

---

### Task 1: The planner obeys `importHours`

**Files:** Modify `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolImportPlanner.kt`,
`SchoolImportItems.kt`; Test: the planner's existing commonTest file.

**Interfaces:** Produces `SchoolImportInput.importHours: Boolean = true`.

- [ ] Tests: `hours off creates no school-hours event`; `hours off deletes this connection's
  coming school-hours events and keeps past ones and days off` (deletes == the live future hours
  events; ledger has no `HOURS` entry dated today or later); `a pending deletion is not deleted again`.
- [ ] Implement: `SchoolImportItems.all` omits hours when off; `plan` adds, when off, every
  `ExistingImport.Live` of a ledger `HOURS` id with `lastDate >= today` to `deletes` and removes every
  such id from the ledger.
- [ ] Run `./gradlew :shared:testAndroidHostTest`; PASS.

### Task 2: Stored, read and passed through

**Files:** `shared/.../domain/school/SchoolConnection.kt`, `shared-data/.../school/SchoolConnectionStore.kt`
(`SchoolConnectionCodec`), `EdupageConnectionCodec.kt`, `SchoolAccounts.kt`, `SchoolImporter.kt`; Tests:
`EdupageConnectionStoreTest`, a codec case in commonTest.

**Interfaces:** Produces `SchoolConnection.importHours: Boolean = true`;
`SchoolAccounts.setImportHours(connectionId: String, on: Boolean)`.

- [ ] Tests: both codecs round-trip `importHours = false`; a record without the key decodes `true`;
  `true` writes no key.
- [ ] Implement; `connect`/`connectEdupage` create with `false`; `SchoolImporter` passes
  `connection.importHours` into the input.
- [ ] Run `:shared-data:testAndroidHostTest`; PASS.

### Task 3: The switch on the import screen

**Files:** `shared-ui/.../presentation/school/SchoolImportScreen.kt`, its ViewModel, the school strings
file in five locales.

- [ ] `SchoolImportViewModel.setImportHours(connectionId, on)` saves, then runs the import as "Update
  now" does; a row `school_import_hours_title` with `AdaptiveSwitch` in each connection's group.
- [ ] `node tools/check-invariants.js`, `:shared-ui:testAndroidHostTest --tests '*School*'`, detekt; PASS.
- [ ] Commit.
