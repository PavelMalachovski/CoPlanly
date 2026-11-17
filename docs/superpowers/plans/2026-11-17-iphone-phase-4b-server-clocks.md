# iPhone phase 4 part B — request clocks on the server (MON-25) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A scheduled Cloud Function runs every request clock's deadline notice and end for both platforms, pushing the proposer, and the phones stop running those two steps.

**Architecture:** A Firestore trigger keeps a server-only `serverNextStepAt` on each `request_deadlines` document; `sweepRequestClocks` runs every five minutes, takes the due clocks and, per clock in a transaction, does the step the proposer's phone does today (a port of `RequestClockSteps` and `RequestRecords`). The state arithmetic is ported to a pure JS module held to the Kotlin `RequestClock` by one shared fixture. The phones keep the answering parent's reminders only.

**Tech Stack:** Cloud Functions v1 (Node, `firebase-functions` as `functions/index.js` uses it, mocha), Firestore rules (`firestore-tests`), Kotlin Multiplatform (`:shared`, `:shared-data`), the Android app's WorkManager worker, the e2e suite on the Firebase emulators.

**Spec:** `docs/superpowers/specs/2026-11-17-iphone-phase-4b-server-clocks-design.md` (approved by the owner on 17 November 2026). Read it before your task; section numbers below refer to it.

## Global Constraints

- Every function is declared through `regional` (`europe-west3`); scheduled ones as `regional.pubsub.schedule('every 5 minutes')` (the v1 API the file uses).
- The four server fields are exactly `serverNextStepAt`, `serverToldDeadlineAt`, `serverToldNoAnswerAt`, `serverSettledAt` (epoch millis). No client may create or change them.
- The two push types are exactly `request_deadline_passed` and `request_no_answer`; server-only (not in the client allow-list of `firestore.rules`); each carries `familyId` and `actorName` (the answering parent's first name through `previewFirstName`).
- Every step acts in the clock's own `familyId`, never the family on screen (CLAUDE.md item 42).
- No `firestore.rules` change may refuse a write an older build makes to `request_deadlines` today.
- `RequestClock`'s arithmetic does not change (24 h button, 24 h last chance, the cap's margins).
- New user-facing strings go into all five locales, following CLAUDE.md "Localization" (check 7: an apostrophe as `'`, no `%%`).
- Gradle cannot configure in the cloud session (Google Maven 403): Kotlin is compiled by CI only. Node suites (`functions`, `firestore-tests`) and `node tools/check-invariants.js` and `node --test tools/test/*.test.js` run locally.
- Repository content in English; Conventional Commits; commit trailers
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01Dch2jKGkvRLpUk46Q4aPa1`; never regenerate the detekt or screenshot baselines.

## Review Focus

1. **A request answered between the sweep's query and its close** must not close: the close transaction re-reads the record. Test in Task 4: `doesNotCloseWhenTheRecordWasAnsweredInsideTheTransaction`.
2. **A clock whose family ended** (unpaired before the deadline) must be settled, not closed and not pushed. Test in Task 4: `aClockOfAnEndedFamilyIsSettledWithoutAPush`.
3. **A clock already closed by an older Android build** (outcome set, the phone's card already sent) must get no push, no card, and only its withdrawal checked. Test in Task 5: `aClockAnOldPhoneClosedOnlyChecksTheWithdrawal`.
4. **A last chance given after the deadline push** moves the end; the sweep must not close at the old end. Test in Task 5: `aLastChanceGivenAfterTheDeadlinePushMovesTheEnd`.
5. **A responder with no name on their profile** still gets a push with a blank `actorName` that the phone words through its fallback, never an address. Test in Task 3 (phone wording) and Task 4 (`actorNameIsAFirstNameNeverAnAddress`).

---

### Task 1: The clock's arithmetic on the server, held to Kotlin by one fixture

**Files:**
- Create: `functions/request-clocks.js` (pure: no Firestore)
- Create: the shared fixture, e.g. `functions/test/fixtures/request-clock-cases.json` (location is the builder's, both suites read the same file)
- Create: `functions/test/request-clocks.test.js`
- Create: a Kotlin test reading the same fixture, `RequestClockFixtureTest`, in `:shared`'s `androidHostTest` (JVM, so it can read a file by path)

**Interfaces:**
- Produces (`functions/request-clocks.js`, `module.exports`):
  - constants `LAST_CHANCE_MILLIS`, `BUTTON_MILLIS`, `CAP_MARGIN_MILLIS`, `MIN_LAST_CHANCE_MILLIS` (values from `RequestClock`);
  - `stateOf(clock, nowMillis) -> 'OPEN' | 'NO_ANSWER_LAST_CHANCE_POSSIBLE' | 'LAST_CHANCE' | 'NO_ANSWER'`;
  - `lastChanceWindowMillis(clock, nowMillis) -> number | null`, `buttonUntilMillis(clock)`, `endMillis(clock)`;
  - `nextStepAt(doc) -> number | null` — §5.1's table over a raw clock document (`answerByMillis`, `capMillis`, `lastChanceUntilMillis`, `outcome`, `closedAt` as millis, `kind`, and the four server fields); null means "no field".
  - `withdrawnAtEnd(kind) -> boolean` (`proposal`, `split`; read the stored kind names from `RequestDeadlineDocument`/`RequestKind`).
- `clock` objects use the stored field names of `RequestDeadlineDocument`.

- [ ] **Step 1:** Write the fixture: at least 30 cases covering every branch of `RequestClock.stateOf`, `lastChanceWindowMillis` and `endMillis` (no cap; a cap under two hours past the deadline; a cap shortening the last chance; a last chance given; an outcome set), each `{clock, nowMillis, state, lastChanceWindowMillis, endMillis}`; plus `nextStepAt` cases for every row of §5.1, including "settled → null", "outcome set by a phone, custody → closedAt", "outcome set, change request → null", "deadline push sent → endMillis".
- [ ] **Step 2:** Write `request-clocks.test.js` (every fixture case through the JS functions) and `RequestClockFixtureTest` (the state, window and end cases through `RequestClock`; `nextStepAt` cases are JS-only). Run `cd functions && npx mocha test/request-clocks.test.js`; expected FAIL (module missing).
- [ ] **Step 3:** Implement `functions/request-clocks.js`.
- [ ] **Step 4:** `cd functions && npm test && npm run lint`; expected PASS. (The Kotlin test runs in CI.)
- [ ] **Step 5:** Commit: `feat(functions): port the request clock's arithmetic, held to Kotlin by one fixture (MON-25)`

### Task 2: The rules refuse the server's fields from clients

**Files:**
- Modify: `firestore.rules` (the `request_deadlines` block, around line 1931)
- Modify: `firestore-tests/rules/request-deadlines.test.js`

- [ ] **Step 1:** Add tests: a client create carrying any of the four fields is refused; each of the five update shapes with any of the four fields added or changed is refused; a document that already carries the four fields (written by the admin SDK in the test setup) still accepts every client update shape an older build makes today (delivery, first opening, reminder shown, last chance, closure). Run `cd firestore-tests && npm test -- --grep request-deadlines`; expected FAIL on the refusals.
- [ ] **Step 2:** Change the rules so no client write may affect the four keys (create's key list already excludes them; add them to every update shape's forbidden keys). Change nothing else.
- [ ] **Step 3:** `cd firestore-tests && npm test`; expected PASS, the whole suite.
- [ ] **Step 4:** Commit: `feat(rules): keep the request clocks' schedule fields server-only (MON-25)`

### Task 3: The two push types on the phone

**Files:**
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/PushPayload.kt` (add `REQUEST_DEADLINE_PASSED = "request_deadline_passed"`, `REQUEST_NO_ANSWER = "request_no_answer"` to the vocabulary and its list)
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushNotifier.kt` (`PUSH_TEXT`)
- Modify: `app/src/main/java/com/coparently/app/data/remote/firebase/PushRouting.kt` (`CHANGE_REQUESTS`, `PushChannel.SCHEDULE`)
- Modify: the five `push_strings.xml` under `shared-ui/src/commonMain/composeResources/values*/`
- Test: `PushRoutingTest`, `androidTest/.../PushNotificationTest` (its table of worded types)

**Interfaces:**
- Produces: the two type names (Global Constraints). Push fields read: `familyId`, `actorName`.
- Strings: `push_request_deadline_passed_title` = "%1$s didn't answer by the deadline", `push_request_deadline_passed_body` = "Nothing has changed in the calendar. You can give more time.", `push_request_no_answer_title` = "No answer from %1$s", `push_request_no_answer_body` = "The request is closed as “No answer”. Nothing has changed in the calendar." — the local notices' English (`request_notice_*`); the four other locales take the matching `request_notice_*` translations. A blank `actorName` uses the same fallback name `PushNotifier` uses for other `actorName` pushes.

- [ ] **Step 1:** Add the two types to `PushRoutingTest` and `PushNotificationTest` (worded in English and German, routed to the Requests inbox on the schedule channel).
- [ ] **Step 2:** Implement the four places. `firestore.rules`' client allow-list does **not** get them (server-only).
- [ ] **Step 3:** `node tools/check-invariants.js` and `node --test tools/test/*.test.js`; expected PASS (if the four-way check needs a server-only list, add the two types there).
- [ ] **Step 4:** Commit: `feat: word the server's two request clock pushes on the phone (MON-25)`

### Task 4: The server's steps

**Files:**
- Create: `functions/request-clock-steps.js` (Firestore through the admin SDK, no scheduling)
- Create: `functions/test/request-clock-steps.test.js` (fake db; extend `functions/test/support/` as needed)

**Interfaces:**
- Consumes: Task 1's module.
- Produces (`module.exports`):
  - `lookRecord(db, clock) -> Promise<{status: 'pending'|'answered'|'gone'|'unknown', card: {entityType, entityId, title} | null}>` — a port of `RequestRecords.lookInLiveFamily` and `familyIsLive` (spec §5.3: live = the clock's `familyId` names both, and the proposer's profile names the responder in `partnerIds` or `partnerId`); `unknown` on a read error.
  - `stepClock(db, clockId, nowMillis, deps) -> Promise<{action: string}>` — one clock, one step, in a transaction that re-reads the clock and is a no-op when `serverNextStepAt` is no longer due; `deps` carries `serverTimestamp()` and a clock for tests. Actions: `rescheduled`, `toldDeadline`, `closed`, `withdrawalRetried`, `settled`, `skipped` (read failed).
  - `unansweredCardFor(clock, card, proposerName) -> {id, data}` — the `REQUEST_UNANSWERED` message: id derived deterministically from the clock id; `data` the same keys and types `MessageRepositoryImpl` writes for a card (read the message mapper and `app/src/test/resources/wire/current/messages/`), `senderId` the proposer, `content` the base-English fallback `ActivityFallbackText` writes for this kind.
  - `withdrawProposal(db, clock) -> Promise<boolean>` — a transaction that deletes only the keys the phone's withdrawal empties (custody: `CustodyProposalTransition.withdraw`; split: the settings' withdrawal) with `update()`, only while `proposedAt`/`proposedAtMillis` equals the clock's `ref`.
- Push documents go to `notification_queue` in the shape the other server pushes use (see `notifyOfChatMessage`'s queue write), addressed to the proposer.

- [ ] **Step 1:** Write the tests, one per row of spec §5.3 for each kind (change, event, swap, proposal, split) with the record pending, answered, gone and unknown; plus `doesNotCloseWhenTheRecordWasAnsweredInsideTheTransaction`, `aClockOfAnEndedFamilyIsSettledWithoutAPush`, `actorNameIsAFirstNameNeverAnAddress`, `twoOverlappingStepsActOnce`, `aRetryAfterTheCloseSendsNoSecondCardOrPush`, `aDifferentProposedAtIsNotWithdrawn`, `withdrawalKeepsKeysTheServerDoesNotKnow`, `theCardMatchesTheMessagesWireFixture`. Run them; expected FAIL.
- [ ] **Step 2:** Implement `functions/request-clock-steps.js`.
- [ ] **Step 3:** `cd functions && npm test && npm run lint`; expected PASS.
- [ ] **Step 4:** Commit: `feat(functions): the request clock's deadline and end steps on the server (MON-25)`

### Task 5: The trigger, the sweep and the backfill

**Files:**
- Modify: `functions/index.js` (export `onRequestClockWritten`, `sweepRequestClocks`, `backfillRequestClockSchedule` and their `…Impl` functions)
- Create: `functions/test/request-clock-sweep.test.js`
- Modify: `firestore.indexes.json` only if the query needs it (a single-field order on `serverNextStepAt` does not)
- Modify: `tools/e2e/coverage.json` (`backfillRequestClockSchedule`: operator exemption like `backfillRecordFamilyIds`; the scheduled and trigger functions as `check-e2e-coverage.js` requires)

**Interfaces:**
- Consumes: Tasks 1 and 4.
- Produces:
  - `onRequestClockWritten` — `regional.firestore.document('request_deadlines/{id}').onWrite`; ignores a write whose changed keys are only the four server fields; writes `serverNextStepAt` from `nextStepAt(doc)` only when it changed (deletes it on null). Never throws (logs), like `recordServerEventRevision`.
  - `sweepRequestClocksImpl(db, nowMillis, deps) -> Promise<{stepped: number, left: number}>` — queries `serverNextStepAt <= now`, ordered, paged; steps each with `stepClock`; stops at its time budget (builder's numbers), leaving the rest due.
  - `sweepRequestClocks` — `regional.pubsub.schedule('every 5 minutes').timeZone('UTC').onRun`.
  - `backfillRequestClockSchedule` — an `onCall` callable limited to `BACKFILL_ADMIN_UIDS`; sets `serverNextStepAt` on every clock with no outcome, and on closed custody or split clocks whose proposal still waits; returns counts; safe to rerun.

- [ ] **Step 1:** Write the tests: the trigger's value for each §5.1 row; `theTriggerIgnoresAWriteOfOnlyServerFields`; `theTriggerDoesNotLoop`; the sweep's paging and budget; `aClockAnOldPhoneClosedOnlyChecksTheWithdrawal`; `aLastChanceGivenAfterTheDeadlinePushMovesTheEnd`; the backfill's selection, counts and rerun; the callable refuses a non-admin. Run; expected FAIL.
- [ ] **Step 2:** Implement the three functions.
- [ ] **Step 3:** `cd functions && npm test && npm run lint`, then `node tools/check-invariants.js` (runs `check-e2e-coverage.js`); expected PASS.
- [ ] **Step 4:** Commit: `feat(functions): sweep the request clocks every five minutes (MON-25)`

### Task 6: The phones stop running the deadline and end

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/notification/RequestClockScheduler.kt` (remove `scheduleDeadline`, `scheduleEnd`)
- Modify: `shared-data/.../data/requests/RequestClockSteps.kt` (remove `deadline`, `end` and what only they use), `RequestClockKeeper.kt` (sweep keeps reminders only), `RequestClockActions.kt` (a last chance schedules nothing), `RequestClockNotices.kt` (remove `deadlinePassed`, `noAnswer`), `data/repository/RequestDeadlineRepositoryImpl.kt` (no deadline or end job at delivery)
- Modify: `shared-data/src/iosMain/.../di/koin/IosUnavailable.kt` (the stand-ins follow the interfaces)
- Modify: `app/.../data/notification/WorkRequestClockScheduler.kt`, `app/.../data/requests/RequestClockWorker.kt` (a queued `STEP_DEADLINE` or `STEP_END` job from an older build finishes as success without acting), `app/.../data/requests/RequestNotices.kt`, and `RequestClockWork.kt` if a name becomes unused
- Test: the existing tests of the keeper, actions, steps, worker and notices, updated; strings only the removed notices used are removed from all five locales together (check that nothing else reads them first)

**Interfaces:**
- Consumes: nothing from Tasks 1–5 (the server does the steps).
- Produces: `RequestClockScheduler` with `scheduleReminder` and `cancelAll` only; `RequestClockNotices` with `remind` only.

- [ ] **Step 1:** Update the tests first: the keeper's sweep arms only reminders; delivery and a last chance schedule no deadline or end job; `anOldDeadlineOrEndJobFinishesWithoutActing` for the worker.
- [ ] **Step 2:** Remove the code; keep detekt's limits (no suppression).
- [ ] **Step 3:** `node tools/check-invariants.js && node --test tools/test/*.test.js`; expected PASS. Kotlin compiles in CI (start an early CI run after pushing).
- [ ] **Step 4:** Commit: `refactor: the phones leave a request's deadline and end to the server (MON-25)`

### Task 7: Two parents on the emulators

**Files:**
- Create: `app/src/androidTest/java/com/coparently/app/e2e/TwoParentServerClocksTest.kt`
- Modify: `tools/e2e/run-two-parent-tests.sh` and/or `firebase.json` only as the trigger mechanism needs
- Modify: `tools/e2e/coverage.json` (the two push types → this test, `"checklist"` naming the device check Task 8 adds)

**Interfaces:**
- Consumes: Tasks 4–6 (`sweepRequestClocksImpl` must be reachable on the emulators only: how is the builder's — e.g. the Pub/Sub emulator publishing to the schedule's topic, or a test-only path enabled solely by `FUNCTIONS_EMULATOR` — but nothing but the schedule may run it in production).

- [ ] **Step 1:** Write `aChangeRequestPastItsEndIsClosedByTheServer`, `aCustodyProposalPastItsEndIsWithdrawnByTheServer`, `aSplitProposalPastItsEndIsWithdrawnByTheServer`, `theProposerIsPushedAtTheDeadlineAndAtTheEnd` (read back from `notification_queue` with `EmulatorEnvironment.awaitQueuedPush`, addressed to the proposer, carrying `familyId`): each creates the request and its clock with `answerByMillis` in the past through the production repositories, makes the sweep run, and asserts the clock's outcome, the card in the family's thread on both phones, and the proposal gone.
- [ ] **Step 2:** Make it pass locally if an Android emulator is available; otherwise the `e2e` CI job is its first run (say so in the report).
- [ ] **Step 3:** `node tools/check-invariants.js`; expected PASS.
- [ ] **Step 4:** Commit: `test(e2e): the server ends a request's clock between two parents (MON-25)`

### Task 8: Record part B

**Files:**
- Modify: `docs/legal/DPIA.md` (R22: the server decides the end and pushes the proposer; the push switch turns those pushes off), `docs/legal/RECORDS-OF-PROCESSING.md` (server-side processing of request records for the deadline), `docs/PRIVACY-POLICY.md` and `web/privacy/` regenerated per `web/README.md` (the two pushes), `docs/DEVICE-CHECKLIST.md` (the two pushes as drawn on an Android phone in two languages; a section number the checklist does not have yet), `tools/manual-test-plan.js` `RULES` if it maps these paths
- Modify: `CLAUDE.md` item 42 (the deadline notice and the end run on the server; the four fields; the trigger and the sweep; older builds beside it) and item 15 (the two server-only types), `docs/ROADMAP.md` MON-25 (built) and MON-24's stand-ins, `functions/README.md` (deploy order of spec §5.7: functions and rules, then the backfill, then the clients)
- Modify: the spec's status line (approved, built)

- [ ] **Step 1:** Write the edits from the code as built.
- [ ] **Step 2:** `node tools/check-invariants.js && node --test tools/test/*.test.js`; expected PASS.
- [ ] **Step 3:** Commit: `docs: record the iPhone's phase 4 part B, request clocks on the server (MON-25)`
