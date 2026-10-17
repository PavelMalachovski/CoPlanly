# Virtual tester Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A manually triggered workflow that walks the real app like a human tester — Home, Calendar,
Chat, Expenses, receipts, two isolated families, a random-tap pass — asserting every step and
publishing a tester's report with a screenshot per step.

**Architecture:** Scenario classes in a new androidTest package `com.coparently.app.qa`, on top of
the existing on-screen harness (`AliceOnScreenTest`, `EmulatorParent`, `UiTourDriver`,
`UiTourVariant`). Each step goes through `QaReport`, which runs it, records a verdict, a screenshot,
visual findings and how the change was delivered, and appends one JSON line per step. A runner
script (modelled on `tools/ui-tour/run-ui-tour.sh`) runs the package with `am instrument`, pulls the
lines, and `tools/virtual-tester/report.js` turns them into `index.html`, `results.json` and a
Markdown summary; a workflow (modelled on `ui-tour.yml`) publishes them.

**Tech Stack:** Kotlin, Compose UI test, Hilt, Firebase emulators, Espresso-Intents (new
androidTest dependency), ML Kit (already in the app), Node 22 (no dependencies), Python 3 + Pillow
(fixture generator only), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-17-virtual-tester-design.md`

## Global Constraints

- Triggered only by `workflow_dispatch` or a commit touching `.github/virtual-tester-request`; never on a PR or a push to `main`.
- Results: one fresh commit force-pushed to `virtual-tester/<branch with "/" → "-">` plus an artifact `virtual-tester`; the job summary lists failed steps.
- The workflow is green when the run started and finished, failed steps included; red only when it could not start, crashed before writing any step, or the script exited non-zero.
- Verdicts: `passed`, `failed`, `could_not_run` (with reason), `known` (a failure matched by `tools/virtual-tester/known.json`). Visual findings never change a verdict.
- Default variant `light-cs-100`; a `variants:` line in the request file replaces it (same grammar as the UI tour: `^(light|dark)-[a-z]{2}-[0-9]{2,3}(-wide)?$`).
- A `classes:` line in the request file runs only those simple class names from `com.coparently.app.qa`.
- The on-screen parent and the co-parent are **Zuzana** and **Igor** (their daughter **Lena**); the second family is Zuzana and **Carol** (her son **Max**). Titles the family types are Czech.
- No `google-services.json`. Every tester class skips itself unless `-e coplanlyEmulatorHost` and `-e coplanlyVirtualTester true` are both given.
- No production code changes; every hook lives in androidTest (`AliceOnScreenTest` and `PausedClock.kt` are androidTest).
- Receipt recognition stays on the device (CLAUDE.md item 10); receipt fixtures name fictional shops only.
- Every wait bounded; the Compose clock paused as `AliceOnScreenTest` does; per-test timeout 10 minutes under the tester flag.
- Code, comments, workflow and report prose in English; Conventional Commits; commit trailer as the session reminder gives.
- No emulator in the cloud session: a scenario task is verified by `./gradlew assembleDebugAndroidTest` locally and then by one workflow run restricted to its class (`classes:` line), whose report is read before the task is marked done.

## Review Focus

1. **A step that passes on a stale screen** — a title already on screen from earlier satisfies the matcher. Every typed title and message carries `shortId()`; pinned in Task 1's `QaReportTest`-equivalent smoke and in each scenario's create steps.
2. **Czech number formatting** — "1 290 Kč" uses a no-break or narrow no-break space; a money matcher or the ellipsis check that only knows an ASCII space misses it. Pinned in Task 1 (the smoke scenario's `money_pattern` step).
3. **A crash mid-run losing the report** — the process dies and nothing is written. Steps are appended and flushed one per line, the runner pulls after every class, and `report.js` reads a truncated last line without failing. Pinned in Task 2 (`readSteps tolerates a truncated last line`).
4. **The random-tap pass doing something destructive** — signing out, deleting the account, unpairing, deleting a family. A label deny-list, pinned in Task 11's unit check of `ExplorationPolicy`.
5. **Deadline steps landing in quiet hours** — reminders are suppressed at night on the runner's clock, so the "no answer" path must not wait for a reminder. Task 6 asserts only the deadline notice, last chance and end, never a reminder.

---

## File Structure

```
app/src/androidTest/java/com/coparently/app/
  e2e/AliceOnScreenTest.kt          modify: open onScreenName / coParentName
  e2e/EmulatorEnvironment.kt        modify: virtualTester flag, 10-minute timeout, assumeVirtualTester()
  testing/PausedClock.kt            modify: iconOnlyControlProblems() beside the assert
  qa/QaReport.kt                    step runner, verdicts, screenshots, steps.jsonl
  qa/QaVisualChecks.kt              money/clipping/accessibility findings for the screen on show
  qa/QaOnScreenTest.kt              base class: names, report, driver, delivery helper, variant
  qa/QaFamily.kt                    small seeding helpers (Lena, Max, agreed split, custody)
  qa/SmokeScenarioTest.kt           four tabs, one step each (harness proof)
  qa/IsolationScenarioTest.kt
  qa/CalendarScenarioTest.kt
  qa/SwapScenarioTest.kt
  qa/CustodyScenarioTest.kt
  qa/ChatScenarioTest.kt
  qa/ExpensesScenarioTest.kt
  qa/ReceiptScenarioTest.kt
  qa/HomeScenarioTest.kt
  qa/ExplorationScenarioTest.kt     + ExplorationPolicy (pure, same file)
app/src/androidTest/assets/qa/receipts/*.jpg + expected.json
tools/virtual-tester/run-virtual-tester.sh
tools/virtual-tester/report.js
tools/virtual-tester/known.json
tools/virtual-tester/coverage.json
tools/virtual-tester/make-receipts.py
tools/test/virtual-tester-report.test.js
tools/ci-changes.js (+ tools/test/ci-changes.test.js)
.github/workflows/virtual-tester.yml
.github/virtual-tester-request
CLAUDE.md, docs/ROADMAP.md
```

---

### Task 1: The harness — report, visual checks, base class, smoke scenario

**Files:**
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/AliceOnScreenTest.kt` (`signUpAlice`, the `EmulatorParent.create(context, "Bob")` call)
- Modify: `app/src/androidTest/java/com/coparently/app/e2e/EmulatorEnvironment.kt`
- Modify: `app/src/androidTest/java/com/coparently/app/testing/PausedClock.kt`
- Modify: `app/build.gradle.kts` (androidTest dependency)
- Create: `qa/QaReport.kt`, `qa/QaVisualChecks.kt`, `qa/QaOnScreenTest.kt`, `qa/SmokeScenarioTest.kt`

**Interfaces:**
- Produces:
  - `AliceOnScreenTest.onScreenName: String` (open, default `"Alice"`), `coParentName: String` (open, default `"Bob"`), used for the display names at sign-up.
  - `EmulatorEnvironment.virtualTester: Boolean` (argument `coplanlyVirtualTester == "true"`), `assumeVirtualTester()` (assumes emulators too), `testTimeout()` = 10 min when `virtualTester`.
  - `fun ComposeTestRule.iconOnlyControlProblems(): List<String>`; `assertIconOnlyControlsAreAccessible` now asserts that list is empty (behaviour unchanged).
  - `enum class QaVerdict { PASSED, FAILED, COULD_NOT_RUN }` (wire names lowercase snake: `passed`, `failed`, `could_not_run`).
  - `enum class QaDelivery { LISTENER, FORCED_SYNC }` (wire `listener`, `forced_sync`).
  - `class QaStep { var actual: String; var delivery: QaDelivery?; val findings: MutableList<String>; fun couldNotRun(reason: String): Nothing }`.
  - `class QaReport(rule: ComposeTestRule, variant: UiTourVariant, scenario: String)` with `fun begin(method: String)`, `fun step(id: String, title: String, expected: String, independent: Boolean = false, block: QaStep.() -> Unit)`, `fun finish()` (throws `AssertionError` naming the failed step ids when any failed).
  - `fun ComposeTestRule.visualFindings(): List<String>`.
  - `abstract class QaOnScreenTest : AliceOnScreenTest` with `report: QaReport`, `driver: UiTourDriver`, `variant: UiTourVariant`, `fun awaitDelivered(step: QaStep, description: String, matcher: SemanticsMatcher, expectsListener: Boolean)`, `fun coParentSync()`.

- [ ] **Step 1:** Add `androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")` beside espresso-core (used from Task 7 on; added now so every later task compiles against one dependency set).
- [ ] **Step 2:** Make the two names open properties on `AliceOnScreenTest` and use them in `signUpAlice` and in `EmulatorParent.create`. Existing subclasses keep Alice and Bob.
- [ ] **Step 3:** Add the tester flag to `EmulatorEnvironment`: `VIRTUAL_TESTER_ARGUMENT = "coplanlyVirtualTester"`, `virtualTester`, `assumeVirtualTester()`, and `VIRTUAL_TESTER_TIMEOUT_MINUTES = 10L` in `testTimeout()`.
- [ ] **Step 4:** Split `assertIconOnlyControlsAreAccessible` into `iconOnlyControlProblems()` (returns the offender lines it builds today) and the assert.
- [ ] **Step 5: `QaVisualChecks.kt`.** `visualFindings()` returns, for the current composition:
  - every `iconOnlyControlProblems()` line, prefixed `accessibility: `;
  - `money cut off: "<text>"` for each text node matching `moneyText` whose `GetTextLayoutResult` reports `isLineEllipsized` on any line or `hasVisualOverflow`;
  - `text clipped: "<first 40 chars>"` for any other text node with `hasVisualOverflow`.
  `internal val moneyText = Regex("""\d[\d\u00A0\u202F ., ]*\s?(Kč|CZK|€|EUR|\$|USD|zł|PLN|£|GBP)""")` (the escapes as written, in Kotlin source).
- [ ] **Step 6: `QaReport.kt`.** Behaviour, each a requirement:
  - Writes to `<filesDir>/virtual-tester/<variant>/steps.jsonl` and `shots/NNN_<scenario>_<id>.png` (full-device `UiAutomation.takeScreenshot()`, as `UiTourCamera.save`). One JSON object per line, written and flushed before the next step starts. Keys: `variant, scenario, method, id, title, expected, actual, verdict, delivery, screenshot, findings, startedAtMillis, durationMs`.
  - `step` runs `block`; normal return → `PASSED`; `QaCouldNotRun` (thrown by `couldNotRun`) → `COULD_NOT_RUN` with the reason as `actual`; any other `Throwable` → `FAILED` with `actual` = its message (or class name). `actual` defaults to `expected` on a pass when the block set nothing.
  - After a `FAILED` step, every later step of the same method is recorded `COULD_NOT_RUN` with `actual = "an earlier step failed: <id>"` and its block is not run — unless the step is `independent` (a check that does not build on the steps before it, such as one recognised receipt field); an `independent` step's own failure does not block later steps either. `begin(method)` resets this.
  - After every step (any verdict): `composeTestRule.settle()`, screenshot, `findings += visualFindings()`; a screenshot failure is a finding, never a verdict change.
  - Logs `step("qa: <scenario>/<id> <verdict>")` through `EmulatorEnvironment.step`.
- [ ] **Step 7: `QaOnScreenTest.kt`.**
  - `@get:Rule val testName = TestName()`; `onScreenName` is `"Igor"` when the method name ends in `AsIgor`, else `"Zuzana"`; `coParentName` is the other.
  - `assumeRunnable()` = `EmulatorEnvironment.assumeVirtualTester()`; `strings` = `variant.localized(context)`; `beforeLaunch()` applies the variant; `@After` resets the language (as `UiTourTest` does).
  - `report` is created in `@Before` after launch with `scenario = this::class.simpleName.removeSuffix("ScenarioTest")` and `begin(testName.methodName)`; `@After` calls `report.finish()` **before** the base tear-down closes the activity.
  - `awaitDelivered`: when `expectsListener`, pump up to `LISTENER_WAIT_MS = 30_000` for `matcher` → `delivery = LISTENER`. Otherwise, or on timeout, `syncService.performFullSync()` then pump up to `30_000` → `delivery = FORCED_SYNC`; when a listener was expected, add the finding `"needed a forced sync where a live listener should have delivered: <description>"`. Never found → `AssertionError("<description> did not appear")`.
  - `coParentSync()` runs `bob.syncService.performFullSync()` (the co-parent side is the data layer; its delivery is not reported).
  - KDoc states which surfaces expect a listener (chat, custody document incl. swaps and proposals, change requests, request clocks, the pairing/family list) and which arrive by sync or push in production (events, expenses, children, pets).
- [ ] **Step 8: `SmokeScenarioTest`.** One method `fourTabsOpen`: steps `home`, `calendar`, `chat`, `expenses`, each opening the tab and expecting its top-bar title (the tab's own label string) on screen; then a step `money_pattern` asserting `moneyText` finds an amount in `"1\u00A0290 Kč"`, `"12,50 €"` and `"1\u202F290,00 CZK"` and nothing in `"Lena 2026"`.
- [ ] **Step 9: Verify it compiles and nothing else moved.**
  Run: `./gradlew assembleDebugAndroidTest detekt > /tmp/t1.log 2>&1; tail -5 /tmp/t1.log`
  Expected: `BUILD SUCCESSFUL`.
  Run: `grep -rn "Alice\"\|\"Bob\"" app/src/androidTest/java/com/coparently/app/e2e/AliceOnScreenTest.kt`
  Expected: only the two default values.
- [ ] **Step 10: Commit** — `test: add the virtual tester's report, visual checks and base class`.

### Task 2: The report generator

**Files:**
- Create: `tools/virtual-tester/report.js`, `tools/virtual-tester/known.json` (`[]`), `tools/virtual-tester/coverage.json`
- Test: `tools/test/virtual-tester-report.test.js`

**Interfaces:**
- Consumes: the `steps.jsonl` line format from Task 1.
- Produces: `node tools/virtual-tester/report.js <site dir> [variant ...]` writes `<site>/index.html`, `<site>/results.json`, `<site>/summary.md`; exports `readSteps(file) -> Step[]`, `applyKnown(steps, known) -> Step[]`, `summarise(steps, coverage) -> {total, passed, failed, couldNotRun, known, findings, notCovered: string[]}`, `renderSummaryMarkdown(summary, steps) -> string`, `renderHtml(steps, summary, source) -> string`.
- `known.json`: `[{ "step": "<Scenario>/<id>", "reason": "...", "ref": "CLAUDE.md item 42" }]`. `coverage.json`: `[{ "area": "Calendar", "scenario": "Calendar" }, …]`, one per spec area (Home, Calendar, Swap, Custody, Chat, Expenses, Receipt, Isolation, Exploration, Smoke).

- [ ] **Step 1: Write the failing tests** (`node:test`, no dependencies), at least:
  - `readSteps tolerates a truncated last line` — two good lines and `{"variant":"li` → 2 steps.
  - `applyKnown turns a matching failure into known and leaves passes alone` — `known = [{step:"Swap/no_answer_history", …}]`; a failed `Swap/no_answer_history` becomes `verdict: "known"` with `knownReason`; a passed one with the same id stays `passed`.
  - `summarise counts verdicts and lists scenarios with no steps` — coverage of `Calendar` and `Chat`, steps only for `Calendar` → `notCovered` is `["Chat"]`.
  - `renderSummaryMarkdown lists each failed step with its expected and actual` — contains `| Swap | no_answer_history |` and both texts; says `All steps passed` when none failed.
  - `renderHtml escapes text and links screenshots relatively` — an `actual` of `<b>` appears as `&lt;b&gt;`; `src="virtual-tester/light-cs-100/shots/001_Smoke_home.png"`.
- [ ] **Step 2:** Run `node --test tools/test/virtual-tester-report.test.js` — Expected: FAIL (module not found).
- [ ] **Step 3: Implement `report.js`.** Page: one section per variant, then per scenario, then per method; each step a row with verdict badge, title, expected, actual, delivery, findings, thumbnail linking the full image; summary at the top, failed and found-visual first; "Not covered in this run" list; `VIRTUAL_TESTER_SOURCE` printed under the title like the gallery does. Plain HTML/CSS inline, light and dark via `prefers-color-scheme`, readable on a phone. `results.json` = `{ summary, steps }`.
- [ ] **Step 4:** Run `node --test tools/test/*.test.js` — Expected: all pass (the existing tests too).
- [ ] **Step 5: Commit** — `test: render the virtual tester's report`.

### Task 3: The runner, the workflow and the first run

**Files:**
- Create: `tools/virtual-tester/run-virtual-tester.sh`, `.github/workflows/virtual-tester.yml`, `.github/virtual-tester-request`
- Modify: `tools/ci-changes.js`, `tools/test/ci-changes.test.js`

**Interfaces:**
- Consumes: Task 1's argument names, Task 2's CLI.
- Produces: env `VIRTUAL_TESTER_VARIANTS` (default `light-cs-100`), `VIRTUAL_TESTER_CLASSES` (space-separated simple names; empty = the whole package), `VIRTUAL_TESTER_OUT` (default `build/virtual-tester-site`); output `<out>/virtual-tester/<variant>/{steps.jsonl,shots/}`, `<out>/logs/`, plus Task 2's files.

- [ ] **Step 1: Failing test in `ci-changes.test.js`:** `decide(['.github/virtual-tester-request'])` and `decide(['.github/workflows/virtual-tester.yml', 'tools/virtual-tester/report.js'])` → `nothing()`; with a `SyncService.kt` path added, the Android outputs are on. Run `node --test tools/test/ci-changes.test.js` — Expected: FAIL.
- [ ] **Step 2:** Add `VIRTUAL_TESTER_ONLY = /^(\.github\/virtual-tester-request$|\.github\/workflows\/virtual-tester\.yml$|tools\/virtual-tester\/)/` dropped exactly as `UI_TOUR_ONLY` is. `tools/test/virtual-tester-report.test.js` is under `tools/test/`, so `invariants` still runs it. Re-run — Expected: PASS.
- [ ] **Step 3: `run-virtual-tester.sh`** — copy the UI tour runner's shape (outside/inside emulators, `pairing-smoke.js` first, install, `clear_system_dialogs`, per-variant `pm clear` + night mode + font scale + wide display, `run-as tar` pull). Differences:
  - instruments `-e package com.coparently.app.qa` or, when `VIRTUAL_TESTER_CLASSES` is set, `-e class` with each name prefixed `com.coparently.app.qa.`; arguments `-e coplanlyEmulatorHost $HOST -e coplanlyVirtualTester true -e coplanlyUiTourVariant $variant`;
  - runs **one `am instrument` per class** (the class list from the argument, or from `ls app/src/androidTest/java/com/coparently/app/qa/*ScenarioTest.kt`), pulling after each, so a crash costs one class; a class whose instrumentation reports `Process crashed` gets a synthetic line appended to its variant's `steps.jsonl`: `{scenario, method:"(process)", id:"crash", verdict:"failed", actual:"the app process crashed — see logs/<variant>-<class>-logcat.txt"}`;
  - pulls `files/virtual-tester/<variant>`; then `node tools/virtual-tester/report.js "$OUT" $VARIANTS`;
  - exits non-zero only when no `steps.jsonl` line was written in any variant.
- [ ] **Step 4: `virtual-tester.yml`** — copy `ui-tour.yml`; differences: name "Virtual tester", triggers on `.github/virtual-tester-request`, branches-ignore `virtual-tester/**`, concurrency `virtual-tester-${{ github.ref }}`, reads `variants:` and `classes:` lines (validate each class against `^[A-Z][A-Za-z]+ScenarioTest$`), publishes to `virtual-tester/<branch>`, appends `summary.md` to `$GITHUB_STEP_SUMMARY`, uploads artifact `virtual-tester`. Header comment documents triggers, the two lines, where results land and that it is not CI, like `ui-tour.yml`'s.
- [ ] **Step 5: `.github/virtual-tester-request`** — a comment block explaining the two lines, then `classes: SmokeScenarioTest`.
- [ ] **Step 6: Local checks.** `bash -n tools/virtual-tester/run-virtual-tester.sh`; `node tools/check-invariants.js`; `node --test tools/test/*.test.js` — Expected: all clean.
- [ ] **Step 7: Commit and push** — `ci: add the manual virtual tester workflow`. Push the branch.
- [ ] **Step 8: First run.** Wait for the "Virtual tester" run started by the push (GitHub MCP `actions_list`). Expected: job green; summary shows 4 steps of `Smoke`; `virtual-tester/claude-zuzana-testing-feedback-ntk5s5` holds `index.html` and 4 screenshots. If not, debug with the published logs before Task 4 (systematic-debugging).

### Task 4: Isolation of two families

**Files:**
- Create: `qa/QaFamily.kt`, `qa/IsolationScenarioTest.kt`

**Interfaces:**
- Consumes: `QaOnScreenTest`, `AliceOnScreenTest.newParent`.
- Produces: `class QaFamily(test-side repositories)` with `suspend fun addChild(name: String, on: EmulatorParent? = null): String` (returns the child id), `suspend fun pairCarol(): EmulatorParent` (Carol redeems Zuzana's code, both sides awaited), `suspend fun agreeSplit(zuzanaShareBasisPoints: Int)`, `suspend fun weekOnWeekOff()`; later scenarios reuse it.

Setup in `@Before` of the class (after launch): Lena in family 1 (by Zuzana), Carol paired, Max in family 2 (by Carol). Two methods, `familyOneOnScreen` and `familyTwoOnScreen` (switch through `FamilySwitcherChip` first). In each, for each kind create one record in **each** family — family 1's through the screen where the form exists (event, expense, chat message), the rest through the repositories (child, pet, change request, day swap with deadline, custody pattern, vault document, record photo, request clock via the swap) — titles carrying `shortId()`.

Steps (ids), each asserting **both** that the on-screen family shows its own record and that the other family's is absent; the data half reads the other family's co-parent phone after `coParentSync()` / `carol.syncService.performFullSync()`:
`events_calendar`, `events_home`, `children_family_screen`, `pets`, `expenses_list_and_balance`, `chat_thread`, `requests_inbox_and_count`, `swap_home_popup`, `custody_grid`, `documents_vault`, `photos` (record photo reference on the other phone null/absent), `switcher_dot` (dot only for the family not on screen with something waiting), `pushes` (each push in `notification_queue` addressed to the right family's parent, `EmulatorEnvironment.awaitQueuedPush`), `export_names` (export screen's parent names are the on-screen family's).

- [ ] **Step 1:** Write `QaFamily` and the class with the steps above.
- [ ] **Step 2:** `./gradlew assembleDebugAndroidTest > /tmp/t4.log 2>&1; tail -3 /tmp/t4.log` — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 3:** Commit `test: virtual tester isolates two families`; set the request file to `classes: IsolationScenarioTest`, commit `chore: run the virtual tester on isolation`, push.
- [ ] **Step 4:** Read the run's report. Every step passed or is a real finding (record real findings in the ledger for the owner — do not fix product code in this task); harness defects (wrong matcher, wrong wait) are fixed and re-run until each step's verdict reflects the app. Expected: no `could_not_run` caused by the tester itself.

### Task 5: Calendar — events and who sees them

**Files:** Create `qa/CalendarScenarioTest.kt`.

Methods and steps (each event created **through the event form on screen**, title `"<Czech title> <shortId>"`):
- `createKinds`: `one_off` (appears in month dots, week and day view; Igor has it after `coParentSync()`; Carol does not — pair Carol through `QaFamily` only in this method), `weekly` (three consecutive weeks show it in week view; Igor's Room has one row with the recurrence), `all_day`, `reminder` (the event's `reminderMinutes` stored; a unique work tagged `event_reminder` enqueued), `about_lena` (member chip; the member filter for Lena shows it, for nobody-else hides it — with Lena alone the strip is absent, so add a second child first).
- `privateEvent`: `private_not_on_server` (`EmulatorEnvironment.documentExists("events/<id>")` false; Igor never has it).
- `editPreserves`: Igor creates an event; Zuzana edits the title on screen; `edit_seen_by_coparent` (Igor's copy has the new title) and `edit_keeps_fields` (`sharedWith`, `createdByFirebaseUid`, `familyId` unchanged on the server).
- `deleteUndo`: `undo_restores`, `delete_is_tombstone` (Igor's server copy has `deletedAtMillis`).
- `dragReschedule`: in day view drag the event chip an hour down: `moved` (new start shown and stored); drag onto the delete button: `dropped_on_delete` (gone, Undo offered).
- `awaitingAnswer`: an event that waits for Igor's answer: `waits_on_coparent` (the Requests count on Igor's side via his repository is 1), Igor accepts via repository, `creator_sees_answer` (on screen, History shows accepted).
- `navigation`: `month_paging`, `today_button`, `day_from_month_back_to_month`, `filters_sheet`.

Delivery: events arrive by sync — `awaitDelivered(…, expectsListener = false)` on the screen side.

- [ ] **Step 1:** Write the class. **Step 2:** compile (as Task 4). **Step 3:** commit `test: virtual tester walks the calendar`, request `classes: CalendarScenarioTest`, push. **Step 4:** read the report; fix harness defects until verdicts reflect the app; ledger findings.

### Task 6: Swaps with deadlines, and custody proposals with the Christmas layer

**Files:** Create `qa/SwapScenarioTest.kt`, `qa/CustodyScenarioTest.kt`.

`SwapScenarioTest` (custody `weekOnWeekOff()` seeded first):
- `proposeOnScreen` (Zuzana on screen): long-press a day of Igor's → swap sheet; `deadline_picker` (choose an "Answer by" chip; the sheet shows it); send; `coparent_sees_deadline` (Igor's clock document holds that deadline; his queued push carries `answerBy`); Igor accepts via repository; `grid_redraws` (the day takes Zuzana's colour — assert the cell's content description names Zuzana), delivery expected **listener**.
- `answerOnScreenAsIgor` (Igor on screen): Zuzana (data layer) proposes a swap with a deadline; `home_popup` (the pop-up on Home names Zuzana and the deadline), `requests_count` (Home's Requests row reads 1), decline on screen; `proposer_sees_decline` (Zuzana's repository state), `history_declined` (History row on screen).
- `noAnswer` (Zuzana on screen): start a swap whose clock is 45 s through `RequestDeadlineRepository.start` and run the clock steps directly as `TwoParentRequestDeadlinesTest` does (reuse `RecordingClockScheduler`); `last_chance_button` (the card offers last chance), press it; let the end pass; `no_answer_history_proposer` (History on screen says "No answer"), `no_answer_history_coparent` (Igor's derived state is no answer via `RequestClock`). No step waits for a reminder.

`CustodyScenarioTest`:
- `christmasLayerProposed` (Zuzana on screen): seasonal layer editor, name "Vánoce v Praze", Lena with Igor 24–25 December, with Zuzana from the 26th; send → `proposal_sent`; Igor accepts via repository; `grid_follows` (24 Dec cell description names Igor, 26 Dec names Zuzana), listener.
- `patternAnswerOnScreenAsIgor` (Igor on screen): Zuzana proposes a new pattern; `proposal_banner`, accept on screen; `both_follow` (Zuzana's model active pattern equals the proposal).

- [ ] Steps as Task 5 (write, compile, commit `test: virtual tester proposes swaps and custody changes`, request `classes: SwapScenarioTest CustodyScenarioTest`, push, read, fix harness, ledger findings).

### Task 7: Chat

**Files:** Create `qa/ChatScenarioTest.kt`.

- `sendAndRead`: `send` (bubble shown with "sending/sent" tick), `coparent_unread` (Igor's unread count 1), Igor marks read; `ticks_read` (the read tick's content description on screen), listener.
- `unreadBadgeAsIgor`: Zuzana (data) sends; `badge_counts` (tab badge 1), open Chat, `badge_clears`.
- `search`: Igor sends "Máme čas v pátek?"; search "cas" → `diacritics_ignored`.
- `holdUndo`: send then Undo within the pause → `undo_keeps_unsent` (Igor never receives it; the draft holds the text).
- `attachment`: `Intents.init()`; stub the picker (`intending(not(isInternal())).respondWith(...)` returning `ActivityResult(RESULT_OK, Intent().setData(uri))`, `uri` = `FileProvider.getUriForFile(targetContext, "app.coplanly.fileprovider", file)` for `qa/files/plan.pdf` from the test APK's assets copied into `<target cacheDir>/shared_files/qa/` (the app's provider already serves `shared_files/`; the PDF is written by Task 9's generator — run it here first if Task 9 has not run); `uploads` (bubble without "Not uploaded yet"); `coparent_opens` (Igor's `SharedFileCache` download matches SHA-256). `Intents.release()` in `finally`.
- `failedUploadAsIgor`: Zuzana's (data) uploads switched off (`SwitchableUploads`), she sends with an attachment; `not_delivered_until_upload` (Igor's screen does not show it), switch on, outbox retry; `arrives` (listener).
- `cards`: an event card opens the event preview (`card_opens_event`); a change-request card opens the inbox (`card_opens_inbox`).

- [ ] Steps as Task 5 (commit `test: virtual tester chats`, `classes: ChatScenarioTest`).

### Task 8: Expenses and the split

**Files:** Create `qa/ExpensesScenarioTest.kt`.

- `addShared`: agreed split 50/50 seeded; add "Boty <id>", 1 290 Kč, paid by Zuzana, tagged Lena, through the form → `listed`, `balance_sentence` (the sentence names Igor owing Zuzana **645 Kč**, read as text, not ellipsized), `coparent_balance` (Igor's computed balance: he owes 645).
- `splitProposalAsIgor` (Igor on screen): Zuzana (data) proposes 40/60 (Zuzana 40 %); `proposal_banner`, accept; then Zuzana adds 1 000 Kč paid by her → `priced_at_new_split` (Igor owes 600); an expense added before acceptance keeps its 50/50 snapshot (`old_expense_unchanged`).
- `splitDeclined`: Zuzana proposes on screen, Igor declines → `declined_shown`, agreement stays 50/50.
- `twoCurrencies`: one CZK and one EUR expense → `two_summaries`, `no_cross_total`.
- `deleteUndo`: swipe to delete, Undo → `restored`; delete again → `gone_for_coparent`.

Delivery: by sync (`expectsListener = false`), except the split proposal (`family_settings` listener — confirm in `FamilySettingsRepository`; if it has no listener, mark false and say so in KDoc).

- [ ] Steps as Task 5 (commit `test: virtual tester splits expenses`, `classes: ExpensesScenarioTest`).

### Task 9: Receipts

**Files:**
- Create: `tools/virtual-tester/make-receipts.py` (also writes `app/src/androidTest/assets/qa/files/plan.pdf`), `app/src/androidTest/assets/qa/receipts/{shoes-cz.jpg, shop-sk.jpg, supermarket-long.jpg, shoes-angled.jpg, expected.json}`, `qa/ReceiptScenarioTest.kt`

**Interfaces:** `expected.json`: `[{ "file": "shoes-cz.jpg", "total": 1290.0, "currency": "CZK", "date": "2026-10-14", "merchant": "Obuv Horák", "category": "CLOTHING" }, …]` — category is an `ExpenseCategory` name; the generator writes both images and this file.

- [ ] **Step 1: `make-receipts.py`** (Pillow, DejaVu Sans Mono from `/usr/share/fonts/truetype/dejavu/`, deterministic seed). Four receipts: Czech shoe shop "Obuv Horák" (CZK, one line `Boty dětské vel. 32  1 290,00`, DPH 21 %, `CELKEM 1 290,00 Kč`, date 14.10.2026, IČO line); Slovak "Detský svet Kováč" (EUR, `SPOLU 34,90 €`, 12.10.2026); long supermarket "Potraviny U Lípy" (18 lines, a discount line, a VAT table, `K ÚHRADĚ 847,50`); the shoe receipt again rendered on a grey table, rotated ~6°, perspective-skewed and with a light gradient (`shoes-angled.jpg`). Paper texture: slight noise. JPEG quality 85, about 1000 px wide.
- [ ] **Step 2:** Run `python3 tools/virtual-tester/make-receipts.py` — Expected: 4 JPEGs + `expected.json` written; open each with the Read tool and confirm it looks like a photographed receipt and every line is legible.
- [ ] **Step 3: `ReceiptScenarioTest`** — one method per receipt (`shoesCz`, `shopSk`, `supermarketLong`, `shoesAngledByCamera`). The first three stub the gallery picker (`PickVisualMedia`) with a `content://` URI of the asset copied into `<target cacheDir>/receipts/qa/` through the same provider (as Task 7); the last stubs `MediaStore.ACTION_IMAGE_CAPTURE` with `respondWithFunction` writing the asset into the intent's `EXTRA_OUTPUT` (camera permission granted with `UiAutomation.grantRuntimePermission`). Steps per receipt:
  - `recognised_total`, `recognised_currency`, `recognised_date`, `recognised_merchant`, `recognised_category` — each compares the form field on screen with `expected.json`; `actual` is what the form shows. A miss is `failed` (spec: a field read wrong is a finding), recorded with `independent = true` so it does not block the rest.
  - `saved_with_photo` (after correcting any wrong field to the expected value on screen, save; the list row shows a receipt mark; opening the expense shows the photo full size).
  - `coparent_sees_receipt` (after `coParentSync()`: Igor's expense has the same amount, date and title, and his `RecordPhotoAccess`/`SharedFileCache` download of the receipt matches the reference's SHA-256).
- [ ] **Step 4:** compile; commit `test: virtual tester scans receipts`; request `classes: ReceiptScenarioTest`; push; read the report (the screenshot of each filled form is what the owner reviews); fix harness defects; ledger recognition findings.

### Task 10: Home and the family switcher

**Files:** Create `qa/HomeScenarioTest.kt`.

- `todayAgreesWithCalendar`: custody seeded; `whose_day` (hero names today's parent), `today_events` (an event created today by Igor shows after sync), `next_handover` (hero sentence present), `same_as_calendar` (Day view for today lists the same events).
- `requestsRow`: Igor (data) sends a change request and a swap → `count_two`; answer one on screen → `count_one`.
- `changesFeed`: Igor creates an event and answers a request → `feed_shows_both`.
- `statTiles`: an expense and an unread message exist → `expenses_tile_amount` (figure as on Expenses), `unread_tile` (1), `tile_opens_tab` (each opens its tab).
- `switcher`: Carol paired (QaFamily); `switch_changes_names` (switch to Carol's family: the new-event form names Carol, the Chat tab shows Carol's thread, Expenses shows family 2's list).

- [ ] Steps as Task 5 (commit `test: virtual tester checks Home and the switcher`, `classes: HomeScenarioTest`).

### Task 11: The exploratory pass

**Files:** Create `qa/ExplorationScenarioTest.kt` (with `internal object ExplorationPolicy` in the same file).

**Interfaces:** `ExplorationPolicy.isAllowed(label: String, deny: List<String>): Boolean`; `deny` built from the variant's strings for: sign out, delete account, unpair, remove co-parent, delete family document, withdraw consent, disconnect Google Calendar, export share (leaves the app).

- One method `randomTaps`: seed = `System.currentTimeMillis()` unless `-e coplanlyQaSeed` is given; the seed is the step's `expected` text. 150 actions (`ACTIONS = 150`), each: with probability 0.6 tap a random clickable node whose label passes `isAllowed`, 0.15 scroll the largest scroller, 0.1 type `"test <n>"` into a random editable, 0.15 Back (never Back from Home with the tab bar shown — that leaves the app). After each action pump up to 5 s; a pump that never idles is `failed` "frozen after action <n>"; leaving the app (no node of the app on screen) → bring it back with `ActivityScenario.launch` and note a finding. Screenshot every 25 actions and at the end. One step `explore` whose verdict is passed unless frozen or crashed ; the seed is logged with `EmulatorEnvironment.step("qa: exploration seed <seed>")` before the first action, so a crash — reported by the runner's synthetic crash line (Task 3) — still leaves the seed in the published logcat.
- A JVM-free check of the deny-list: step `deny_list` asserts `isAllowed` is false for each deny label in the variant and true for "Kalendář".

- [ ] Steps as Task 5 (commit `test: virtual tester explores at random`, `classes: ExplorationScenarioTest`).

### Task 12: Documentation and the full run

**Files:** Modify `CLAUDE.md` (Build & verify: a bullet "A fourth workflow, not CI: the virtual tester" after the UI tour's paragraph — triggers, the two request lines, the package, the verdicts and `known.json`, the per-class instrumentation, that a new screen or feature gets a scenario), `docs/ROADMAP.md` (a line under the testing items), `docs/DEVICE-CHECKLIST.md` (one sentence at the top: the virtual tester's report shows which flows already ran; the checklist stays for what it cannot see).

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Set `.github/virtual-tester-request` to no `classes:` line (the whole package) and `variants: light-cs-100`; commit `docs: describe the virtual tester` and push.
- [ ] **Step 3:** Read the full run's report. Expected: every scenario present (no "Not covered" except areas deliberately left), no tester-caused `could_not_run`. Add confirmed known limitations to `known.json` with their refs (commit `test: record known limitations for the virtual tester`), re-run once if any were added.
- [ ] **Step 4:** Hand the owner the report link and a Russian summary of the real findings, sorted by what a parent would hit first, with a recommendation of what to fix before Wednesday.
