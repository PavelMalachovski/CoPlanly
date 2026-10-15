# Tester feedback, phase 3a: no request is left hanging

October 2026. Phase 3 began as "day swaps" (the tester's items 5–7: a small calendar on an
incoming swap, answering by tapping the day, an exchange of days for days). Asked how such an
exchange goes today, the tester described something else first: the swap is not what hurts, the
**silence** is. So phase 3 is three parts, built in this order, each with its own document:

- **3a — no request is left hanging** (this document): a deadline, reminders, a seen mark, a last
  chance, and an honest "No answer".
- **3b — answering is easy**: the request's days on a small calendar, conflicts checked on the
  answering parent's phone, an answer per day.
- **3c — the exchange**: give my days for theirs, accept part, counter-propose.

The owner approved the screens on the canvas "CoPlanly — ответ на запрос"
(https://claude.ai/artifact/Y9Fg9n8AazKxFwAynvFKgi, private to the owner; Russian copy, the demo
family Alice, Bob and Emma). Artboards 1–6 are §6.1–§6.6 below. The English below is the base
string; the canvas has the Russian.

## 1. Intent

The tester co-parents through two apps today and sent the owner real threads. What she said, in
short:

- **The worst part is not knowing.** She proposes something (a weekend, a swap, a pick-up time)
  and the other parent does not react, so she does not know whether he counts on it. She worries,
  calls, writes "let me know?". It happens in both directions, but in her family she plans and he
  answers, so everything waits on his answer.
- **She asked for**: reminders to whoever has to answer ("say, three"), a deadline the proposer
  sets, a clear outcome when it passes ("he didn't approve it"), and then a "last chance" she can
  send from inside the app.
- **Answering is effort.** The other parent has to check their own week before saying yes. Ideally
  the app would tell them "no conflict" or "then swap the next weekend too" — possibly with AI.
  That is phase 3b, and AI is a separate, later decision (§9).

From the threads (the owner's reading and mine, not her words): conflicts are mostly the child's
activities (a weekday swap means deciding who takes her to training), dates get confused in text
("the dinner is on Tuesday, the training on Thursday"), and the worst moment — a handover an hour
late with no reply — is about a handover, not a request. That last one is out of scope here (§9).

## 2. Goals, non-goals, anti-goals

Goals:
- Every request has a deadline the proposer chose, and both parents can see it.
- The parent who must answer is reminded, at sensible hours, without the proposer having to chase.
- The proposer always knows where the request stands: delivered, opened, reminded, answered, or
  not answered by the deadline.
- When the deadline passes the outcome is explicit — "No answer" — and nothing changes in the
  calendar. The proposer may give one last chance of 24 hours.
- "No answer" is its own outcome in the history, the chat and the export, never shown as "Declined".

Non-goals:
- No change to *what* a request asks or how it is answered (accept/decline stay as they are; per-day
  answers are 3b).
- No reminders for anything that is not a request (events' own reminders stay as they are).
- No server-side scheduler: the reminders come from the answering parent's phone (§5.3).
- No AI.

Anti-goals (a failure even if it works):
- **Silence changing where the child is.** A deadline passing never accepts anything.
- **A claim the app cannot back** (design item 8): no "reminded 3 times" unless the other phone
  reported showing them, no "hasn't opened it" from a phone that cannot report opening.
- **Nagging.** No reminder between 21:00 and 08:00 on the answering parent's clock, no reminder
  after an answer, no push when the other parent opens a request, nothing more after the last chance.
- **Breaking a co-parent on an older build.** Their app must keep reading and answering requests as
  today (§5.5).

## 3. The owner's decisions (15 October 2026)

1. One mechanism for **every** request that waits on the other parent: a day swap (one offer, one
   group of days), a change to an event, an event created for the other parent's days (acceptance
   pending), and a custody proposal (pattern, seasonal layers, a child's schedule, a plan-cited
   proposal — all one `proposal` on the custody document).
2. **Deadline**: the proposer picks it when sending — "Tomorrow", "3 days", "A week" or a date.
   Default 3 days. The whole chain, last chance included, ends before the first day the request
   changes; a request about tomorrow compresses it.
3. **Three reminders**, spread by the app across the window, the last shortly before the deadline,
   none at night.
4. **At the deadline** without an answer: the request is "No answer", the calendar is unchanged, and
   the proposer is told and offered a button, **"Give a last chance — 24 hours"**. It is the
   proposer's choice. If they press it and there is still no answer after 24 hours, the request is
   "No answer" for good. If they don't press it, it stays "No answer".
5. **The seen mark is shown**: the proposer sees when the other parent opened the request.
6. The six screens on the canvas, as drawn.

## 4. The behaviour

### 4.1 States

A request's clock (§5.1) and its own record together put it in exactly one state:

| State | When | Proposer sees | Answering parent sees |
| --- | --- | --- | --- |
| **Open** | sent, not answered, before the deadline | "Waiting for <name>" with deadline, delivered/opened, reminders (§6.3) | "Waiting for your answer" with "Answer by … · … left" (§6.2) |
| **No answer, last chance possible** | deadline passed, no answer, the button still available | the card stays in "Waiting for <name>": "No answer", unchanged calendar, the button (§6.4) | the card is in History as "No answer"; no answer buttons |
| **Last chance** | the proposer pressed the button; 24 hours not yet up | the card says "Last chance until …" | back in "Waiting for your answer", with the last-chance banner (§6.5) |
| **No answer** (final) | the last chance ran out, or the button was never pressed and is no longer available | History: "No answer" (§6.6) | History: "No answer" |
| **Answered** | accepted or declined, as today | History, as today | History, as today |
| **Withdrawn** | a change request withdrawn by its author, as today | History, as today | History, as today |

A request sent before this feature, or by a co-parent's older build, has no clock and behaves
exactly as today: open until answered.

### 4.2 Choosing the deadline

- Options: **Tomorrow** (now + 24 h), **3 days** (now + 72 h, default), **A week** (now + 7 days),
  **Pick a date…** (that date at 20:00). Every deadline is rounded up to the next quarter hour, so
  it reads "by Tue 13 Oct, 10:00", in the reader's clock format (`shortTime()`, R-9).
- **The first affected moment** caps it: for a day swap, 00:00 of the earliest day in the offer; for
  a change request, the earlier of the event's current and proposed start; for an event awaiting
  acceptance, its start; for a custody proposal, 00:00 of the date it takes effect, if that is in
  the future, otherwise no cap.
- An option is offered only if it ends at least 24 hours before the cap, so the last chance still
  fits. Options that don't are shown disabled.
- **Urgent requests.** When less than 25 hours remain before the cap, the options are replaced by
  one fixed line: "Answer by <cap − 1 h> — before it starts". No last chance (§4.4). When less than
  2 hours remain, the request is sent without a deadline, as today, and the form says "Too soon for
  a deadline — it goes out as a plain request".

### 4.3 Reminders

- **How many**, by the length of the window (sent → deadline): 24 hours or more → 3, at one third
  and two thirds of the window and 2 hours before the deadline; 6 to 24 hours → 2, at the midpoint
  and 1 hour before; under 6 hours → 1, at the midpoint.
- **Quiet hours.** A reminder falling between 21:00 and 08:00 on the answering parent's own clock
  moves to 08:00; if that would be after the deadline, it moves back to 20:30 the evening before; if
  that is before the request was sent, it is dropped. Two reminders that land on the same time
  become one. The count the proposer sees ("of 3") is the number that survived, decided once, when
  the request is sent (§5.1, `remindersPlanned`).
- A reminder is posted only while the request is still Open: answered, withdrawn, or past the
  deadline means it is silently skipped.
- **The reminder is a local notification** on the answering parent's phone, not a push (§5.3). Each
  one shown is reported back (`remindersShown`), and that count is what the proposer sees.

### 4.4 The deadline, the last chance, the end

- **At the deadline**, if unanswered:
  - the proposer's phone shows a notification, "<name> didn't answer by the deadline", and the card
    shows "No answer" with "Give a last chance — 24 hours" and "Once. <name> will have until <time>.";
  - the answering parent's card moves to History as "No answer" and loses its buttons;
  - day-swap markers of this offer disappear from the calendar on both phones; nothing else in the
    calendar moves, because nothing was ever accepted.
- **The button** is available from the deadline until 1 hour before the cap. With no cap, it is
  available for 7 days. Pressing it once:
  - sets the window to min(24 hours, cap − now);
  - sends the answering parent a push, "Last chance to answer";
  - brings the card back to their "Waiting for your answer", under the banner "Last chance · … left";
  - posts a chat card "Last chance: <request>" to the thread.

  The button never appears twice for one request. There are no further reminders during the last
  chance: the push is the reminder.
- **The end.** When the last chance runs out, or the button's own time runs out unused, the
  request is "No answer" for good:
  - the proposer's phone shows "No answer from <name>" and posts a chat card "No answer: <request>";
  - where the record allows it, the request is closed in its own record too, so a co-parent on an
    older build stops being asked: a change request becomes `CANCELLED` (its author may set that
    today), a custody proposal is withdrawn (`CustodyModelRepository.withdrawProposal`, today
    without a caller). A day swap and an event keep their pending records (§5.5);
  - "No answer" is shown in every History and printed by the export through the chat card.
- **An answer after "No answer"** cannot be given on this build: the buttons are gone. A co-parent
  on an older build can still answer a swap or an event (§5.5). Such an answer counts — it is an
  explicit yes or no — and History shows it as "Accepted after the deadline" / "Declined after the
  deadline".

### 4.5 Delivered and opened

- **Delivered**: the answering parent's phone records when it first receives the request's clock
  and schedules the reminders. Until then the proposer's card claims nothing beyond "Sent".
- **Opened**: the answering parent's phone records the first time the request is shown to them —
  its card on screen in "Waiting for your answer", Home's pop-up for it, or a tap on its
  notification. The proposer sees "<name> opened it · <time>", and, once delivered but not opened,
  "<name> hasn't opened it yet". Opening sends no push.
- Both are written once and never changed.

### 4.6 Where it shows

- **Requests inbox** (`ChangeRequestsScreen`): the cards and the sections of §4.1, drawn as in §6.
- **Home** (`RequestsRow`, Home's pop-up for a swap or proposal): the count is `RequestInbox`'s, so a
  request in History stops counting. The pop-up appears only for an Open or Last-chance request.
- **Calendar**: the banner counts as Home does; a swap's pending markers are drawn only while it is
  Open or under a last chance.
- **Chat**: two new activity kinds, "Last chance" and "No answer" (`ActivityKind`), each with a
  plain-text fallback, so an older build shows the sentence. No card per reminder or per opening.
- **Export**: nothing new — the chat cards are already part of the record (CLAUDE.md item 26).

## 5. Where it lives

### 5.1 A separate document per request: `request_deadlines/{id}`

The clock cannot go into the request's own record:
- `change_requests` updates may touch only `status`, `respondedAt` and `familyId`;
- the custody document's swap and proposal writes are `hasOnly` lists;
- an older build rewrites a whole change request when it answers, so a new key would make its
  answer write fail the new rules or be dropped by its `set()`.

So each request gets one small document beside it, which an older build never reads or writes:

| Field | Written by | Meaning |
| --- | --- | --- |
| `kind` | proposer, at create | `swap`, `change`, `event` or `proposal` |
| `ref` | proposer, at create | the request it times: the swap's `groupId`, the change request's id, the event's id, the proposal's `proposedAt` |
| `familyId` | proposer, at create | the family on screen when the request was made |
| `proposerUid`, `responderUid`, `participants` | proposer, at create | the two parents; `participants` is both, for the list query |
| `createdAtMillis` | proposer, at create | the server's time of creation |
| `answerByMillis` | proposer, at create | the deadline (§4.2) |
| `capMillis` | proposer, at create | the first affected moment, or absent |
| `remindersPlanned` | proposer, at create | 0–3 (§4.3) |
| `deliveredAtMillis` | answering parent's phone, once | §4.5 |
| `seenAtMillis` | answering parent's phone, once | §4.5 |
| `remindersShown` | answering parent's phone, +1 each | up to `remindersPlanned` |
| `lastChanceAtMillis`, `lastChanceUntilMillis` | proposer, once | §4.4 |

- **The id is derived** from the request, so both phones find it without asking: `swap_<groupId>`,
  `change_<id>`, `event_<id>`, `proposal_<familyId>_<proposedAt>`. A swap made by this build always
  carries a `groupId`, even for one day.
- **The states of §4.1 are derived** on each phone, from the clock, the request's own record and the
  phone's clock — never stored. The pure decision lives in one place, `domain/changerequests/`
  (`RequestClock`), which every screen, the reminder worker and the inbox read.
- **Written in the same action as the request**, after its own write; offline, Firestore queues both.
  A request whose clock never arrives behaves as one without a clock.

### 5.2 `firestore.rules`

- **Read**: the two `participants`, with a list query keyed on `participants` and `familyId`.
- **Create**: by the proposer only, naming as responder their live co-parent in the named family.
  `answerByMillis` must be after the request time and at most 31 days after it; no
  delivered/seen/reminder/last-chance field may be present.
- **Update**:
  - the responder may add `deliveredAtMillis` and `seenAtMillis`, each once, each not in the
    future;
  - the responder may raise `remindersShown` by one, never above `remindersPlanned`;
  - the proposer may add `lastChanceAtMillis` and `lastChanceUntilMillis` once, only after
    `answerByMillis`. The start must be within a few minutes of the request time; the end must be at
    most 24 hours after the start and not after `capMillis`.
  - Nothing else changes.
- **Delete**: nobody; account deletion runs as admin (§7).
- The rules do **not** refuse a late answer in `change_requests`, the custody document or `events`:
  that would add a document read to three hot rules for an edge that only an older build reaches
  (§5.5).

### 5.3 Reminders come from the answering parent's phone

- A local notification, scheduled by WorkManager per clock (unique work, `REPLACE`, as
  `EventReminderScheduler` does for events). It is scheduled when the phone first receives the clock,
  which is also when it writes `deliveredAtMillis`.
- **The phone learns of a clock** through the requests source's listener while the app runs, and
  through `SyncWorker`'s periodic pass while it doesn't. New: handling a request's own push
  (`day_swap_offered` and the rest) also asks for a sync (`SyncRequester`), so the clock arrives
  with it rather than on the next tick — today a push only posts its notification.
- **When a reminder fires** the worker re-reads the request and its clock and posts only while the
  request is Open, then reports `remindersShown`.
- **The proposer's notifications** ("didn't answer by the deadline", "No answer") are local too, on
  the proposer's phone, which knows both times from the clock it wrote. Each is re-checked when it
  fires.
- **The last chance is a push** (§5.4), because it must reach a phone that may not be syncing.
- Why not a server scheduler:
  - quiet hours need the answering parent's own clock, which the server does not know;
  - "reminded 2 of 3" must be what the other phone showed, not what a server meant to send;
  - no new scheduled function to deploy.

  The cost: a phone that never syncs gets no reminders. That phone also missed the request's own
  push, and the proposer's card says so: no "delivered".

### 5.4 One new push type

`request_last_chance`, client-produced, addressed to the answering parent and carrying the family
(`PushPayload.FAMILY_ID`). It needs all four places (CLAUDE.md item 15) plus `PushRouting`:
- `PushPayload`;
- the rules' allow-list;
- `PushNotifier.PUSH_TEXT`;
- the five `push_strings.xml`;
- `PushRouting`: it opens the Requests inbox on that card, on the **schedule** channel.

### 5.5 A co-parent on an older build

- **What they see.** They see and answer requests as today and never see a deadline. Their app
  never writes delivered or opened, so the proposer's card claims neither (§4.5). The last-chance
  push is dropped by their `PushNotifier`, as any unknown type is. The chat cards fall back to
  plain text.
- **A late answer.** A change request or proposal closed at the end (§4.4) is closed for them too.
  A day swap or an event stays pending in its record, so they can still accept it late. That
  answer takes effect and History says "after the deadline". This is accepted for the update window
  rather than enforced in the rules (§5.2).

## 6. The screens (the canvas)

### 6.1 Sending — "Answer by" (artboard 1)

- **Where.** A block "Answer by" with four chips appears in all four forms, between the request's
  own content and its send button:
  - `DaySwapSheet`;
  - `RequestChangeScreen`;
  - the event form, only while `EventAcceptanceTransition.required` says the event will wait;
  - each custody proposal's submit.
- **Shared composable.** One composable for all four (`AnswerByField`), so the options, the cap and
  the wording live in one place.
- **Helper lines** under the chips:
  - "By <date, time>. <name> gets <n> reminders, none at night."
  - "No answer by then — you'll be told and can give a last chance of 24 hours. Nothing changes in
    the calendar without an answer."
- **Urgent and too-soon cases** replace the chips with one line (§4.2).

### 6.2 The answering parent's card (artboard 2)

- **The notification.** The request's existing notification adds "Answer by <time>."
- **The card line.** The card adds one line, with a clock icon, in `primary`: "Answer by <date,
  time> · <left>". "<left>" reads "3 days", "16 h" or "45 min".
- **Unchanged.** Everything else on the card — the creator's words, the mini month, the buttons —
  is as today.

### 6.3 The proposer's card while it is open (artboard 3)

- **A status block** on the card in "Waiting for <name>", up to three rows:
  - "<name> opened it · <time>", or "<name> hasn't opened it yet" once delivered (an eye icon);
  - "Reminders: <shown> of <planned> · next <time>" (a bell icon);
  - "Answer by <time> · <left>" (a clock icon).
- **Before delivery** the block shows only "Sent · Answer by …".
- **No new withdraw.** A day swap, an event and a proposal still have none (CLAUDE.md item 40).

### 6.4 The deadline passed (artboard 4)

- **Status chip.** The proposer's card gets the chip "No answer", outlined and neutral — not the
  error colour, which belongs to "Declined".
- **Under it**, in order:
  - "Deadline passed <time>";
  - the opened and reminders rows;
  - "Nothing has changed in the calendar: <what stays whose>";
  - the filled button "Give a last chance — 24 hours";
  - "Once. <name> will have until <time>."

### 6.5 The last chance (artboard 5)

- **The push**: "Last chance to answer" — "<name> is waiting for your answer about <request> until
  <time>."
- **The banner.** The card returns to "Waiting for your answer" under an `InlineBanner` in the
  ATTENTION tone: "Last chance · <left> left" — "Answer by <time>. Without an answer the request
  closes as “No answer”."

### 6.6 History (artboard 6)

- **The "No answer" row** has the neutral outlined chip and a meta line: "Deadline <date> · last
  chance until <time> · opened <date> · <n> reminders · calendar unchanged".
- **Contrast.** "Declined" and "Accepted" keep today's chips.
- **Late answers.** A late answer from an older build reads "Accepted after the deadline" /
  "Declined after the deadline".

## 7. Privacy and the legal documents

- **What is new** is the deadline, and that the answering parent's app records when it received a
  request, when it was opened, and how many reminders it showed. The other parent sees the opened
  time — as they see a chat message's read mark.
- **Documents.**
  - `PRIVACY-POLICY.md` gains that paragraph; regenerate `web/privacy/`.
  - `RECORDS-OF-PROCESSING.md` adds it to the requests' processing.
  - `DPIA.md` adds the risk that an "opened" mark is used to pressure the other parent. The
    mitigations: no push on opening, no reminders at night, no reminders after the last chance.
  - `DATA-SAFETY.md` needs no new data type: this is app functionality on content already declared.
- **Retention**: as long as the request, i.e. the account (the policy's general rule).
- **Account deletion** removes every `request_deadlines` document naming the departing uid in
  `participants`. Add the collection to `deleteAccountDataImpl` and to the three places
  `web/README.md` says must agree.

## 8. Testing

- **JVM**:
  - `RequestClock`: every state of §4.1 at each boundary, the cap, urgent and too-soon, the last
    chance's window and expiry, a late answer.
  - The reminder plan: counts by window, quiet hours moved and dropped, merges, time zones.
  - The deadline options: disabled options, rounding.
  - `RequestInbox` and `RequestHistory` placing each state, and "after the deadline".
- **Rules** (`firestore-tests/rules/request-deadlines.test.js`):
  - create, read, update and delete for the proposer, the responder and a stranger;
  - each field written once, by its writer only;
  - `remindersShown` +1 within `remindersPlanned`;
  - the last chance only after the deadline, within 24 hours and the cap;
  - a list query without `participants` refused.
- **Two phones** (e2e):
  - a request with a deadline: delivered and opened written by Bob's phone and read on Alice's;
  - the last-chance push read back from `notification_queue` addressed to Bob;
  - Bob's answer under the last chance closes it.

  `tools/e2e/coverage.json` maps `request_deadlines` and `request_last_chance` to it. A deadline
  a minute out keeps the wait bounded.
- **Pushes**: `PushRoutingTest` and `PushNotificationTest` cover the new type.
- **Wire**: a `WireContract` for `request_deadlines`, with hand-written fixtures (one without the
  optional fields, one with a field from a newer build).
- **Screenshots**:
  - `AnswerByField` (default, urgent, too soon);
  - the card's status block (sent, delivered, opened, reminders);
  - the "No answer" card with its button;
  - the last-chance banner;
  - the History row.

  Baselines through the Regenerate workflow.
- **UI tour**: the seed gives Bob's pending swap a deadline; shots of artboards 2 and 3.
- **Device checklist**: a new section for what only phones show:
  - reminders at the planned times;
  - quiet hours on a phone set to another time zone;
  - both proposer notifications;
  - the last-chance push;
  - the opened mark arriving;
  - an older build's co-parent answering.

  `tools/manual-test-plan.js` maps the new files to it.

## 9. Out of scope, and why

- **3b and 3c**: their own documents, after this ships.
- **AI** suggestions: the owner's later decision. Calendar data does not go to a model today
  (CLAUDE.md item 36); sending it needs a new legal assessment.
- **Handover notices** ("on my way", "running late", a 24–48 hour rule for changes): about a
  handover, not a request. CLAUDE.md design item 20 makes a parent-started handover flow an owner
  decision first.
- **"Who had the children last Christmas"**: the holiday history is MON-20's ground.
- **Server-side reminders**, and a rules-level refusal of late answers (§5.2, §5.5).

## 10. Calls I made (the owner may overrule any)

1. The four deadline options, the 20:00 time for a picked date, rounding to the quarter hour.
2. The 24-hour room for a last chance when offering options; urgent mode under 25 hours; no deadline
   under 2 hours.
3. The reminder times by window, and quiet hours 21:00–08:00 on the answering parent's clock.
4. The button's availability (until 1 hour before the cap, or 7 days without one), and a last chance
   shorter than 24 hours when the cap is nearer.
5. No reminders during the last chance.
6. "Delivered" as the condition for showing "hasn't opened it yet" and the reminders count.
7. A change request closed as `CANCELLED` and a proposal withdrawn at the final "No answer"; a swap
   and an event left pending in their records.
8. Late answers from an older build count, and are labelled "after the deadline".
9. Reminders as local notifications on the answering phone, the proposer's notifications local too,
   and one push type for the last chance.
10. Two chat cards, "Last chance" and "No answer", and no card per reminder or opening.

## 11. Left to the builder

The file layout, the composable split, how `SyncWorker` reads clocks, the WorkManager details,
the exact copy within the meanings above (all five locales), and thresholds within ±25 % where a
test or a device shows a reason — recorded in the plan.
