# iPhone app, phase 4 part B: request clocks on the server (MON-25) — design

Status: the approach was agreed with the owner on 17 November 2026 (chat). This document waits for
the owner's approval before a plan is written.

Parent design: `docs/superpowers/specs/2026-11-16-iphone-phase-4-design.md` §2 and §4 (part B).
ROADMAP: MON-25. The behaviour being moved is CLAUDE.md "Things that are easy to get wrong" item 42
and the phase 3a spec `docs/superpowers/specs/2026-10-15-zuzana-feedback-phase-3a-requests-never-hang-design.md`.

## 1. Intent

A request can carry a deadline (item 42). Today the proposer's phone runs every timed step of its
clock with WorkManager jobs:
- **the deadline notice:** at the deadline, while the request still waits, it tells the proposer
  that there was no answer and that they can give more time;
- **the end:** when no last chance was given or a last chance ran out, it closes the clock as
  "No answer", posts a local notice, posts a `REQUEST_UNANSWERED` card in the family's thread, and
  withdraws a waiting custody or expense split proposal.

An iPhone cannot run work at a set time, so on an iPhone none of this would ever happen: a custody
proposal would block the other parent's own proposals for ever. The owner decided on 27 October
2026 that a scheduled Cloud Function does these steps for both platforms. Part B is required before
an iPhone build reaches a user.

What the owner decided on 17 November 2026:
- **The proposer is told by a push from the server**, both at the deadline and at the end.
- **A scheduled check every five minutes** decides what is due, over a server-only field.

## 2. Goals

1. A scheduled function, `sweepRequestClocks`, runs every five minutes in `europe-west3` and does,
   for every clock whose next step is due, what the proposer's phone does today, with the same
   decisions (§5.3).
2. At the deadline, if the request still waits and a last chance can still be given, the proposer
   receives a push, `request_deadline_passed`, once.
3. At the end, if the request still waits, the server closes the clock (`outcome: "no_answer"`,
   `closedAt` by the server's clock), pushes `request_no_answer` to the proposer once, posts the
   `REQUEST_UNANSWERED` card in the clock's family's thread, and withdraws a custody or split
   proposal that is still the same one.
4. New builds of both apps no longer schedule or run the deadline and end steps. They keep the
   answering parent's reminders, which stay on the phone (local notifications; on the iPhone those
   come in part C).
5. Older Android builds that still run their own jobs keep working beside the server, with no
   double closure, no double card and no proposal withdrawn twice.
6. Clocks open at deploy time are picked up by a one-off operator pass.

## 3. Non-goals

- The answering parent's reminders. They stay local, planned in that phone's own zone (DPIA R22's
  quiet hours).
- The last chance itself. The proposer still gives it from the app, and its push
  (`request_last_chance`) is still the client's.
- Pushes on the iPhone. The two new pushes reach an iPhone proposer once part C registers the token;
  until then that proposer sees the state in the app.
- Any change to what a clock's states are, or to the arithmetic of `RequestClock` (24 h button, 24 h
  last chance, the cap's margins).

## 4. Anti-goals (it fails even if it works)

- **A request answered at the last minute is closed as "No answer".** The server must decide from
  the record as the server holds it, at the moment it closes.
- **A push or a card is sent twice by the server.** Two overlapping sweeps, a retry after a crash or
  a trigger that fires again must not repeat a push or a card.
- **A different proposal is withdrawn.** A custody or split proposal is withdrawn only if its
  `proposedAt` is still the clock's.
- **A step acts in the family on screen.** Every step acts in the clock's own `familyId` (item 42).
- **An older build is locked out.** Nothing in `firestore.rules` may start refusing what an older
  build writes to `request_deadlines` today.

## 5. Design

### 5.1 The schedule field

The server keeps one field on each clock that only it writes:
- `serverNextStepAt` (epoch millis, a number): the moment the sweep should next look at this clock.
  It is absent when the clock needs nothing more from the server.
- `serverToldDeadlineAt` (epoch millis): set when the deadline push has been sent, so it is sent
  once.
- `serverToldNoAnswerAt` (epoch millis): set when the server closed the clock and sent its push and
  card, so they are sent once.

A Firestore trigger on every write to `request_deadlines/{id}` (`onRequestClockWritten`) computes
`serverNextStepAt` from the document and writes it only when it changed, so its own write does not
loop:
- `OPEN` → `answerByMillis`;
- `NO_ANSWER_LAST_CHANCE_POSSIBLE` → `answerByMillis` if the deadline push has not been sent, else
  `endMillis`;
- `LAST_CHANCE` → `lastChanceUntilMillis`;
- `NO_ANSWER` with no `outcome` → now (the end is due);
- `outcome` set → absent, unless the clock's kind is withdrawn at the end (custody, split) and the
  server has not confirmed the withdrawal; then now, so a failed withdrawal is retried.

The sweep queries `serverNextStepAt <= now`, ordered by it, in pages (a single-field index; the
query never matches a clock without the field).

**The rules refuse the three fields from clients**: on create (the create shape's `hasOnly` already
excludes them) and on update (no client update shape may affect them). Clients write with `update()`
of named fields (`FirestoreRequestDeadlineDataSource`), so a client write never drops them. The
client reader ignores unknown keys, so older builds read the document unchanged. Pinned by
`firestore-tests/rules/request-deadlines.test.js`.

### 5.2 The sweep

`sweepRequestClocks` (`onSchedule`, every 5 minutes, declared through `regional`). For each due
clock it runs one step in a Firestore transaction that re-reads the clock, so two overlapping runs
cannot both act: the run that wins writes the next `serverNextStepAt` (or the told mark) in the same
transaction, and the other finds the step no longer due.

A run does at most a bounded number of clocks (the builder picks the page size and the run's time
budget) and leaves the rest to the next run; nothing is lost because the field stays due.

### 5.3 What each step decides (a port of `RequestClockSteps`)

The decision is the phone's today, moved:

| State at the sweep | Record (read by the server) | What the server does |
| --- | --- | --- |
| `OPEN` (fired early) | — | nothing; reschedules |
| `NO_ANSWER_LAST_CHANCE_POSSIBLE`, deadline push not sent | pending | push `request_deadline_passed` to the proposer; mark it told; next step at the end |
| same | answered or gone | nothing more; field removed |
| `LAST_CHANCE` (fired early) | — | nothing; reschedules to `lastChanceUntil` |
| `NO_ANSWER`, no outcome | pending | close the clock; push `request_no_answer`; post the card; withdraw a custody or split proposal |
| same | answered or gone | nothing; field removed (an orphaned clock shows nothing, as today) |
| outcome set, withdrawal not confirmed | pending | retry the withdrawal; nothing is told twice |
| any | the read failed | nothing; the field stays due, the next run tries again |

**The record's look is ported per kind** from `RequestRecords.lookInLiveFamily`, read by the admin
SDK (which is always the server's copy): a change request's status; an event's acceptance for the
answering parent; a swap group's entries in `custody_models/{familyId}.dayOverrides`; the custody
document's `proposal.proposedAt`; `family_settings/{familyId}.proposal.proposedAtMillis`. A family
that is no longer live (`families/{id}` gone) makes the record "gone", as `familyIsLive` does.

**The state arithmetic is ported from `RequestClock`** into `functions/request-clocks.js` (pure,
like `calendar-feed.js`), and **one shared fixture** of clocks, moments and expected states is read
by both `RequestClockTest` (Kotlin) and the functions' mocha test, so the two cannot drift. A change
to `RequestClock` changes the fixture and both ports.

**The close** writes `outcome: "no_answer"`, `closedAt` (server timestamp) and
`serverToldNoAnswerAt` in one transaction that re-reads the record first and refuses to close when it
is no longer pending. The push and the card follow the committed transaction; the card's write is
keyed so a retry cannot post it twice (a deterministic message id derived from the clock id).

**The card** is the `REQUEST_UNANSWERED` message the phone posts today, with the same activity
fields and base-English fallback text, in the clock's family's thread, sent in the proposer's name
(`senderId` the proposer), so screens, the export and older builds read it unchanged. It is already
in `SILENT_CARD_KINDS`, so `notifyOfChatMessage` pushes nobody for it.

**The withdrawal** of a custody or split proposal is a transaction that removes the proposal only if
its `proposedAt`/`proposedAtMillis` is still the clock's `ref`, as
`CustodyModelRepository.withdrawUnanswered` and `FamilySettingsRepository.withdrawUnanswered` do.
It writes what those write, field for field, so the co-parent's phone sees the same document an
Android proposer would have left.

### 5.4 The two pushes

`request_deadline_passed` and `request_no_answer` are new push types and go through item 15's four
places: `PushPayload`, `PushNotifier.PUSH_TEXT` and the five `push_strings.xml` (their text reuses the
local notices' wording: "{name} didn't answer by the deadline" / "Nothing has changed in the
calendar. You can give more time.", and "No answer from {name}" / "The request is closed as "No
answer". Nothing has changed in the calendar."). They are **server-only**, like `pairing_accepted`:
not in the client allow-list of `firestore.rules`. Each carries `familyId` and the answering
parent's first name as `actorName` (`previewFirstName`, never an address). `PushRouting` opens the
Requests inbox (`CHANGE_REQUESTS`) on the schedule channel, as `request_last_chance` does.

They are sent at any hour: the server does not know the proposer's zone, and a chat message is
pushed at night too (owner, 17 November 2026).

### 5.5 The phones

New builds:
- stop scheduling the deadline and end jobs (`RequestClockScheduler.scheduleDeadline`/`scheduleEnd`
  and their callers in `RequestClockKeeper` and `RequestClockActions`) and remove
  `RequestClockSteps.deadline`/`end` and `RequestClockNotices.deadlinePassed`/`noAnswer`;
- keep the answering parent's reminders and their sweep on every full sync;
- on Android, cancel by unique name any deadline or end job an older build left queued (the worker
  for those kinds finishes as done without acting, and the first sweep after the update cancels
  them);
- on the iPhone nothing changes in part B: `NoRequestClockJobs` and `NoRequestClockNotices` stay
  until part C plans the local reminders.

**Older Android builds** still run their own deadline and end jobs. Side by side with the server:
- the closure is written once (the clock's `outcome` is write-once in the rules, and the server's
  close refuses a closed clock), so only one of them posts the card and withdraws;
- the proposer may see the deadline notice twice, once local and once pushed, and the same for "No
  answer" when the old phone closes first and the server then finds the clock closed (it sends
  nothing then). Accepted: it lasts until the proposer updates.

### 5.6 Clocks open at deploy

The trigger fires only on writes. An operator callable, `backfillRequestClockSchedule` (the
`BACKFILL_ADMIN_UIDS` allow-list, as the other backfills), computes `serverNextStepAt` for every
clock with no outcome, and for closed custody or split clocks whose proposal still waits. It is run
once right after the functions deploy, and is safe to run again.

### 5.7 Order of deployment

1. Deploy the functions and the rules.
2. Run `backfillRequestClockSchedule`.
3. Release the client builds that stop running the steps.

Until step 3 both run, which §5.5 makes safe.

## 6. Constraints

- Every function is declared through `regional` (`europe-west3`), Legal item 2.
- The push types follow item 15's four places; the server-only ones stay out of the client
  allow-list.
- No `firestore.rules` change may refuse a write an older build makes today (anti-goal 5).
- Nothing new is stored about a parent: the three fields are times.
- The documents change in the same pull request (Legal item 1): DPIA R22 (the server now decides the
  end and pushes the proposer), the records of processing (server-side processing of request
  records for the deadline), the privacy policy's push list, CLAUDE.md item 42 and item 15, ROADMAP
  MON-25, and the device checklist.
- Every new shared surface is covered in `tools/e2e/coverage.json` (`tools/check-e2e-coverage.js`):
  the two push types by a two-parent test that reads them back from `notification_queue`, the
  backfill callable by a test or an operator exemption like its siblings.

## 7. Approaches (agreed 17 November 2026)

1. **A sweep every five minutes over a server-only "next step" field** (chosen). At most five
   minutes late, which is nothing for a deadline set in hours or days; an outage is caught up by the
   next run; 288 runs a day with one indexed query; tested on the emulators like the other
   functions.
2. **A Cloud Task per clock.** Precise to the second, but the task must be moved by every last
   chance and dropped by every answer or withdrawal, it needs a queue configured in the console, and
   the emulator supports it less well. The precision buys nothing here.
3. **A sweep without the field**, scanning every past-deadline open clock. Simpler, but a withdrawn
   request's clock never closes, so such clocks would be scanned on every run for ever.

## 8. Tests

- **Functions (mocha, fake db):** the state port against the shared fixture; each row of §5.3 with
  the record pending, answered, gone and unreadable for every kind; two overlapping runs act once; a
  retry after the close posts no second card or push; a different `proposedAt` is not withdrawn; the
  trigger's next-step computation, and that it does not loop; the backfill.
- **Rules (`firestore-tests`):** clients cannot create, set or change the three server fields; every
  write an older build makes today is still allowed.
- **Kotlin:** `RequestClockTest` reads the shared fixture; the keeper and actions no longer schedule
  deadline or end jobs; an old deadline or end job finishes without acting.
- **Two parents on the emulators (e2e):** a change request, a custody proposal and a split proposal
  with a past deadline reach "No answer" through the server: the clock closed, the card in the
  family's thread, the proposal withdrawn, `request_deadline_passed` and `request_no_answer`
  queued for the proposer.
- **Device checklist:** the two pushes as drawn on an Android phone, in two languages.

## 9. Left to the builder

- The page size and the run's time budget for the sweep.
- How the e2e test makes the sweep run on the emulators, provided nothing but the schedule runs it
  in production.
- The deterministic message id's exact form.
- Where the shared fixture lives, as long as both test suites read the same file.
