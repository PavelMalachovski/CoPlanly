# The virtual tester — design

Status: approved by the owner in conversation on 17 October 2026, pending the written review.
Owner: Pavel. Testers: Zuzana and colleagues, on real phones, Wednesday 21 October 2026.

## Why

The testers found what CI never did: families were not isolated, and children of one family
reached another. CI proved that the code does what it says. Nothing proved that a separated
parent can actually use the app: create an event and see who sees it, answer a swap before its
deadline, split a pair of shoes, read the co-parent's reply. The owner wants a run that behaves
as close to a human tester as a cloud machine can, across the four tabs (Home, Calendar, Chat,
Expenses), on demand, before the testers do — so the testers find less and the owner trusts more.

This is project 1 of 2. Project 2 (a question with a deadline from a chat message, handover
check-ins, holiday rotation and history, receipts and child support, the family subscription) is
recorded in `2026-10-17-agreements-and-billing-notes.md` and gets its own design. Every feature
of project 2 arrives with its scenarios in this tester.

## What it is

A manually triggered GitHub Actions workflow, **the virtual tester**, that:

1. Boots an Android emulator and the Firebase emulators (Auth, Firestore, Functions, Storage),
   exactly as the UI tour does today (`.github/workflows/ui-tour.yml`,
   `tools/ui-tour/run-ui-tour.sh`).
2. Creates a realistic family on them: Zuzana and Igor, paired through the real callable, with
   their daughter Lena, plus a second family: Zuzana and Carol, with Carol's son. The names come
   from the testers' real case, so a finding reads like a tester's report.
3. Walks **scenarios**: short stories of what a parent does, through the app's real screens,
   found by string resource and content description (as `AliceOnScreenTest` and the UI tour do),
   and **asserts the outcome** of every step — on the screen *and* in the data the other parent's
   phone receives.
4. Photographs every step, and writes a **report**: per scenario, each step, what was expected,
   what happened, a screenshot, and a verdict (passed, failed, or could not run, with the reason).
5. Ends with a short **exploratory pass**: random taps through the app for a few minutes, which
   fails only on a crash or a frozen screen.

It does not replace the CI jobs or the UI tour. The UI tour photographs and asserts nothing; the
tester asserts. CI gates every pull request; the tester runs only when the owner asks.

## How it is triggered

Only when the owner asks. Same two ways as the UI tour and Regenerate:

- `workflow_dispatch` from the Actions tab once the workflow is on the default branch;
- touching a request file, `.github/virtual-tester-request`, on any branch — including from a
  Claude session when the owner says "run the tester".

It never runs on a pull request or on a push to `main`. A run takes about 30–60 minutes of runner
time; the exact figure is measured on the first run and written into the workflow's header.

## Where the results land

Like the UI tour: a single fresh commit force-pushed to `virtual-tester/<branch, "/" → "-">`
containing `index.html` (the report), the screenshots and a `results.json`, plus the same files
as a workflow artifact. The workflow's summary page lists the failed steps in one table, so the
owner sees the verdict without downloading anything. The workflow itself is green when every
scenario ran (failed steps included) and red only when the run could not start or crashed; the
verdict is the report, not the colour. *(Builder's call whether a failed scenario should also
turn the run red — what matters is that the summary says it plainly.)*

## The people on screen

One process has one app on screen. A scenario therefore has one parent **on screen** (driving
`MainActivity` through the real UI) and the other parent **beside it** (the production data layer
in the same process, as `EmulatorParent` does today). Every two-parent scenario runs **twice with
the roles swapped**, so the proposer's screen and the answerer's screen are both exercised:
Zuzana proposes on screen while Igor answers through the data layer, then Igor answers on screen
what Zuzana proposed through the data layer.

## Coverage — what the scenarios must prove

Each item is a scenario or a step; the builder groups them. Every step asserts both what is drawn
and what the co-parent's side holds afterwards.

### Home
- The today card shows whose day it is, today's events and the next handover, and agrees with
  the calendar for the same day.
- The Requests row counts what waits on this parent; it drops when they answer.
- The co-parent's changes feed shows a new event, an answer and a request.
- The stat tiles (this month's expenses, unread messages) show the right figures and open the
  right tab.

### Calendar
- Create an event (one-off, recurring weekly, all-day, with a reminder, about one child): it
  appears in month, week and day view, on the co-parent's side after a sync, and nowhere in the
  other family.
- A **private** event is visible only to its creator and never reaches the server.
- Edit an event: fields not on the form survive (item 2 of CLAUDE.md "Things that are easy to get
  wrong"); the co-parent sees the edit.
- Delete with Undo: Undo restores it; a delete that stands is a tombstone on the co-parent's side.
- Reschedule by drag in day/week view, and the delete-by-drop target.
- An event that waits for an answer: the co-parent accepts or declines from the inbox; the creator
  sees the answer.
- Swap a day (long-press and "Swap this day"), with a deadline: the co-parent sees it on Home and in
  the inbox, accepts or declines; the grid redraws on both sides. Then a swap with no answer: past
  the deadline, the proposer gets the last-chance button; after the end, "No answer" in History on
  both sides.
- Propose a new custody pattern and a seasonal layer (the Christmas case: Lena with Igor in Prague
  24–25 December, with Zuzana from the 26th): the co-parent accepts or declines; the grid follows.
- Filters and the member filter strip; month paging; Today; Back from a day opened from a month
  cell returns to month.

### Chat
- Send a message; it arrives unread on the other side; the sender's ticks reach delivered and read.
- The unread badge on the tab counts and clears.
- Search finds a message, diacritics ignored ("cas" finds "čas").
- The pause-before-send Undo keeps a message from being sent.
- An attachment (a PDF or an image from the device) uploads, opens for the co-parent, and a
  failed upload shows "Not uploaded yet" until it succeeds.
- A chat card about an event opens the event; one about a request opens the inbox.

### Expenses
- Add an expense (shared, split by the agreed ratio, a child tagged, a receipt photo from the
  device): it shows on both sides; the balance sentence says who owes whom and how much, to the
  crown.
- Propose a split of 40/60 against an agreed 50/50: the co-parent accepts or declines; an expense
  added after acceptance is priced at 40/60, one added before stays at 50/50.
- Two currencies in one month: two summaries, no cross-currency total.
- Delete with Undo.
- **Receipts, as a parent really uses them** (owner's request, 17 October): a parent photographs
  the receipt for a pair of shoes, and the app must read it well, keep it conveniently and share it
  with the co-parent. The tester ships a small set of realistic receipt photos with the test APK
  (fictional shops, no real brand): a Czech shoe shop in CZK, a Slovak shop in EUR, a long
  supermarket receipt with many lines, discounts and VAT, and one photographed at an angle with
  uneven light. For each it runs the real on-device recognition (ML Kit, item 10 — nothing leaves
  the phone) and checks, field by field, what the form is filled with against what the receipt
  says: **total, currency, date, shop name, category**. Then it saves the expense and checks that
  the receipt photo stays attached, opens full size, and that the co-parent sees the same amount,
  date, shop and the photo itself (downloaded as themselves, digest checked). The report shows each
  receipt beside the form it produced, so the owner sees at a glance what was read right and what
  the parent had to correct. A field read wrong is a finding, not a crash.

### Settings, onboarding, pairing (smoke)
- A new parent goes through onboarding, links the co-parent first, and opens on what the link
  brought back.
- The family switcher: switching to the second family changes every tab's content, names and
  colours.

### Isolation (the defect the testers found)
A dedicated scenario with the second family (Zuzana–Carol): for every kind of record — event,
child, pet, expense, chat message, request, swap, custody schedule, document, photo, request
clock — one is created in each family, and the scenario asserts that none of them is visible,
counted, notified or exported in the other family, on screen and in the data. It runs with each
family on screen in turn.

### Visual checks (every screenshot)
On every step, besides the screenshot:
- no crash and no "app not responding";
- the existing accessibility sweep (unnamed or sub-48 dp icon-only controls);
- **money is never cut off**: an amount, a total or a balance sentence is not ellipsized
  (CLAUDE.md design item 15);
- no text node is clipped by its container, where Compose semantics can tell.

A visual finding does not stop a scenario; it is listed in the report beside the step.

### Variants
The main run is light theme, Czech, font 1.0 — the testers' own setting. The owner may ask for
more variants the way the UI tour takes them (a `variants:` line in the request file): dark,
Russian, font 1.3, Slovak once the app has it.

## The exploratory pass

After the scenarios, a few minutes of seeded random input (taps, scrolls, Back, typing) across the
signed-in app with the family loaded, the seed printed in the report so a crash can be replayed.
It fails only on a crash or a frozen screen, and records the last screenshots before one.

## What it is not

- Not a CI gate, and not run on pull requests.
- Not two screens at once: the co-parent is the data layer (two emulators in one job was
  considered and rejected for runner cost and flakiness).
- Not real push delivery (no emulator exists for FCM): pushes are asserted as queued documents
  addressed to the right parent, as the e2e job does.
- Not a performance or load test.
- No new production features. Any test hook added to the app is debug-only.

## Anti-goals

- A tester that is green while a parent would be stuck. A step that only checks "no crash" where
  an outcome could be checked is a defect in the tester.
- Flaky verdicts. Every wait is bounded, the Compose clock is handled as `AliceOnScreenTest`
  handles it, and a step that could not run says why instead of failing someone else's step.
- A report only a developer can read. The report is written for the owner, in plain sentences.

## Constraints

- Builds on the existing harness: `EmulatorParent`, `AliceOnScreenTest`, `UiTourDriver`,
  `UiTourCamera`, `UiTourSeed`, `tools/ui-tour/run-ui-tour.sh`; new code goes beside them in
  `app/src/androidTest/.../e2e/` (or a sibling package) and `tools/`.
- Same rules as the e2e job: no `google-services.json`; tests skip themselves without their host
  argument and a tester-specific switch, so neither the `instrumented` nor the `e2e` job runs them
  (`tools/e2e/run-two-parent-tests.sh` excludes them by name, as it does the UI tour).
- `tools/e2e/coverage.json` and `tools/check-e2e-coverage.js` are unaffected unless a new shared
  surface appears (none should).
- The repository's language rules: code, comments and the workflow in English; the report's UI
  strings are what the app shows in the variant's language; the report's own prose in English.
- Ready to run before Wednesday 21 October 2026. If not every scenario is ready, the run ships with
  what is ready and the report lists what is not covered yet.

## What happens with what it finds

Not part of this design, but the reason for it: after the first run, Claude triages the report,
the owner decides what to fix before Wednesday, and fixes go through the normal workflow. A
finding that turns out to be a tester defect is fixed in the tester.

## Decisions taken after the written review

The reviewer's questions were answered by Claude, not the owner; each is listed for the owner to
overturn.

1. **Order of work for Wednesday.** Isolation first; then the core outcome on each tab (an event
   and who sees it, a swap with a deadline and its "No answer", a message, an expense and the
   40/60 split); then everything else (drag, OCR, failed upload, the exploratory pass). The role
   swap is part of the core, not deferred.
2. **How a change reaches the parent on screen.** Each cross-parent step first waits, bounded, for
   the app's own live listeners. Only where the app depends on a push or the periodic sync tick
   (neither exists on the emulator) does the tester force a sync, as `SyncWorker` would — and the
   report says, step by step, which of the two delivered the change. A step that needed a forced
   sync where a listener should have done it is a finding.
3. **The "No answer" path.** The deadline picker is exercised on screen in its own step (the
   chosen deadline shows on both sides). The expiry itself runs on a short clock started through
   the data layer and the request-clock steps run directly, as `TwoParentRequestDeadlinesTest`
   does — no time-injection hook in the app.
4. **Pickers and the camera.** The system file picker and camera are stubbed with
   Espresso-Intents returning a file shipped with the test APK; what the app does with the file is
   tested, the system's own screens are not (the Wednesday testers cover those).
5. **Known limitations.** A third verdict, **known**, for steps that hit a limitation already
   recorded in the repository: a list beside the scenarios naming the step and the CLAUDE.md or
   ROADMAP entry. Known steps are shown, not counted as failures.

## Left to the builder

- How scenarios are expressed (a small step DSL over `UiTourDriver`, or plain test methods with a
  reporting wrapper), and how the report is generated (extend `tools/ui-tour/gallery.js` or a new
  script).
- How the role swap is implemented (parameterised test class, or two classes sharing a body).
- The exploratory pass's duration and input mix, within "a few minutes".
- Which sample receipt image and attachment files ship with the test APK.
