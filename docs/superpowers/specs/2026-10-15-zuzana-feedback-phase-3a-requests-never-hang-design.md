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
| **No answer, last chance possible** | deadline passed, no answer, the button still available (§4.4) | the card stays in "Waiting for <name>": "No answer", unchanged calendar, the button (§6.4) | the card moves to "Waiting for <proposer>", without answer buttons: "You didn't answer by the deadline. <proposer> can give you a last chance until <time>." — the next move is the proposer's |
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
- **The cap is the first affected moment**: the earliest moment, today or later, that the request
  would change.
  - Day swap: 00:00 of the earliest day in the offer.
  - Change request: the earlier of the event's current and proposed start that is still ahead. A
    change to a recurring series, whose stored start is its long-past first occurrence, therefore
    takes the proposed start, or has no cap.
  - Event awaiting acceptance: its start.
  - Custody proposal: 00:00 of the first day `CustodyPatternDiff` reports as changing, today or
    later.
  - Nothing ahead: no cap.
- **The owner's rule is that a near cap compresses the chain** rather than refusing it. With
  `L = cap − 24 h`, the latest deadline that still leaves a full last chance:
  - **48 hours or more to the cap**: the four options, each ending no later than `L`. An option
    that would end later reads `L`'s time instead ("By Fri, 20:00"), and options that land on the
    same time merge into one chip. The default stays "3 days", compressed.
  - **2 to 48 hours to the cap**: no options. One fixed line, "Answer by <time>", where the time
    is halfway from now to the cap, rounded down to the quarter hour. A last chance, if given,
    ends 1 hour before the cap, and is not offered when less than an hour would be left (§4.4).
  - **Under 2 hours**: no deadline. The request goes out as today, and the form says "Too soon for
    a deadline — it goes out as a plain request".

### 4.3 Reminders

- **How many**, by the length of the window (sent → deadline): 24 hours or more → 3, at one third
  and two thirds of the window and 2 hours before the deadline; 6 to 24 hours → 2, at the midpoint
  and 1 hour before; under 6 hours → 1, at the midpoint.
- **Quiet hours.** A reminder falling between 21:00 and 08:00 on the answering parent's own clock
  moves to 08:00; if that would be after the deadline, it moves back to 20:30 the evening before; if
  that is before the request was sent, it is dropped. Two reminders that land on the same time
  become one.
- **The answering phone plans them**, in its own time zone, when it first receives the clock. Only
  that phone knows the zone: no profile stores one. It writes the count that survived
  (`remindersPlanned`) and their times (`reminderTimesMillis`) together with `deliveredAtMillis`
  (§5.1). So the proposer's send form can only say "up to 3 reminders, none at night", and the
  proposer's card reads the real plan once the request is delivered ("Reminders: 1 of 2 · next …").
- A reminder is posted only while the request is still Open: answered, withdrawn, or past the
  deadline means it is silently skipped.
- **The reminder is a local notification** on the answering parent's phone, not a push (§5.3). Each
  one shown is reported back (`remindersShown`), and that count is what the proposer sees.

### 4.4 The deadline, the last chance, the end

- **At the deadline**, if unanswered:
  - the proposer's phone shows a notification, "<name> didn't answer by the deadline", and the card
    shows "No answer" with "Give a last chance — 24 hours" and "Once. <name> will have until <time>.";
  - the answering parent's card moves to "Waiting for <proposer>" without its buttons (§4.1);
  - day-swap markers of this offer disappear from the calendar on both phones; nothing else in the
    calendar moves, because nothing was ever accepted.
- **The button** is available for 24 hours after the deadline, and never later than 1 hour before
  the cap. The proposer's decision must not hang either: a pending custody proposal blocks the other
  parent's own proposals (`COPARENT_PROPOSAL_WAITING`) until it ends, so the end comes at most 48
  hours after the deadline. Pressing it once:
  - sets the window to min(24 hours, cap − 1 hour − now);
  - sends the answering parent a push, "Last chance to answer";
  - brings the card back to their "Waiting for your answer", under the banner "Last chance · … left";
  - posts a chat card "Last chance: <request>" to the thread.

  The button never appears twice for one request. There are no further reminders during the last
  chance: the push is the reminder.
- **The end.** When the last chance runs out, or the button's own time runs out unused, the
  request is "No answer" for good. The proposer's phone, from a job at that moment or on its next
  start:
  1. writes the closure into the clock — `outcome: "no_answer"` and `closedAtMillis` (§5.1);
  2. shows "No answer from <name>";
  3. posts a chat card "No answer: <request>", which pushes nobody (§4.6);
  4. withdraws a custody proposal, so it stops blocking the other parent. It does this in a
     transaction that first checks it is still the same pending proposal (its `proposedAt`), in the
     clock's family (§5.6).

  A change request, a day swap and an event keep their pending records. The closure in the clock is
  what every new build reads (§5.5), and "No answer" stays in History even when the record is gone.
- **An answer after "No answer"** cannot be given on this build: the buttons are gone. A co-parent
  on an older build can still answer a change request, a swap or an event (§5.5). Such an answer
  counts — it is an explicit yes or no. History shows it as the answer it is, and the chat shows the
  order: the "No answer" card, then the answer's card.

### 4.5 Delivered and opened

- **Delivered**: the answering parent's phone records when it first receives the request's clock,
  plans and schedules the reminders, and writes their plan with it (§4.3). Until then the proposer's
  card claims nothing beyond "Sent".
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
  Every chat message is pushed today (`onChatMessageCreated` → `notifyOfChatMessage`), cards
  included. That would send the last chance twice and push "No answer" to the parent who was just
  told nothing more comes. So `notifyOfChatMessage` skips these two kinds: a functions change and
  a deploy, with a test.
- **The family switcher's dot** (design item 13): `OtherFamiliesSignals` reads each other family's
  clocks too. One more listener per other family, shared and bounded like the others, so a
  request in History never lights it.
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
| `deliveredAtMillis`, `remindersPlanned`, `reminderTimesMillis` | answering parent's phone, once, together | §4.3, §4.5: the plan in its own zone, 0–3 times |
| `seenAtMillis` | answering parent's phone, once | §4.5 |
| `remindersShown` | answering parent's phone, +1 each | up to `remindersPlanned` |
| `lastChanceAtMillis`, `lastChanceUntilMillis` | proposer, once | §4.4 |
| `outcome`, `closedAtMillis` | proposer, once | the end of §4.4: `"no_answer"` and when |

- **The id is derived** from the request, so both phones find it without asking:
  `swap_<proposerUid>_<groupId>`, `change_<proposerUid>_<id>`, `event_<proposerUid>_<id>`,
  `proposal_<proposerUid>_<familyId>_<proposedAt>`. The proposer's uid is in the id because the
  other parent can see the rest. The rules bind it to the caller (§5.2), and a phone ignores a clock
  whose proposer is not the request's author. Without it, the other parent could create a request's
  clock first and strip its deadline, or fake an early "No answer".
- **New: a one-day swap offer also carries a `groupId`.** Today `DayOverrideTransition.offer`
  writes none for a single day. Older builds are unaffected: `notifyPartnerOfSwap` picks the push
  type by the number of days, not by the group.
- **The states of §4.1 are derived** on each phone, from the clock, the request's own record and the
  phone's clock. Only the end is stored: the clock's `outcome`, written by the proposer. A record can
  disappear or be replaced (a withdrawn proposal, a swap's date offered again), and an orphaned
  clock without `outcome` shows nothing: its request was superseded. The pure decision lives in one
  place, `domain/changerequests/` (`RequestClock`), which every screen, the reminder worker and the
  inbox read.
- **Written in the same action as the request**, after its own write; offline, Firestore queues both.
  A request whose clock never arrives behaves as one without a clock.

### 5.2 `firestore.rules`

- **Read**: the two `participants`, with a list query keyed on `participants` and `familyId`.
- **Create**: by the proposer only, with the caller's uid as the id's second segment, naming as
  responder their live co-parent in the named family. `answerByMillis` must be after the request
  time and at most 31 days after it. No field of the table written later may be present.
- **Update**:
  - the responder may add `deliveredAtMillis`, `remindersPlanned` (0–3) and `reminderTimesMillis`
    (that many numbers, between creation and the deadline) once, together; and `seenAtMillis` once.
    Neither time may be in the future;
  - the responder may raise `remindersShown` by one, never above `remindersPlanned`;
  - the proposer may add `lastChanceAtMillis` and `lastChanceUntilMillis` once, only after
    `answerByMillis`. The start must be within a few minutes of the request time; the end must be at
    most 24 hours after the start and not after `capMillis`;
  - the proposer may add `outcome` (`"no_answer"` only) and `closedAtMillis` once, only after
    `answerByMillis`;
  - nothing else changes.
- **Delete**: nobody; account deletion runs as admin (§7).
- The rules do **not** refuse a late answer in `change_requests`, the custody document or `events`:
  that would add a document read to three hot rules for an edge that only an older build reaches
  (§5.5).

### 5.3 Reminders come from the answering parent's phone

- A local notification, scheduled by WorkManager per clock (unique work, `REPLACE`, as
  `EventReminderScheduler` does for events). It is scheduled when the phone first receives the clock,
  which is also when it writes `deliveredAtMillis`.
- **The phone learns of a clock** through the requests source's listener while the app runs, and
  through `SyncWorker` while it doesn't. Every push addressed to the signed-in account already asks
  for a sync (`CoPlanlyMessagingService` → `SyncWorker.syncNow`), so the clock arrives with the
  request's push. What is new is only that the sync pass reads the family's clocks and schedules
  their reminders.
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

### 5.4 One new push type, and one new push field

`request_last_chance`, client-produced, addressed to the answering parent and carrying the family
(`PushPayload.FAMILY_ID`). It needs all four places (CLAUDE.md item 15) plus `PushRouting`:
- `PushPayload`;
- the rules' allow-list;
- `PushNotifier.PUSH_TEXT`;
- the five `push_strings.xml`;
- `PushRouting`: it opens the Requests inbox on that card, on the **schedule** channel.

**`answerBy` on the request pushes the proposer's phone queues** — the day-swap offers,
`change_request_created` and `custody_proposal_proposed`: the deadline as epoch-millis text.
- The push is queued before the clock is written, so this field is how the first notification can
  say "Answer by <time>." (§6.2). `PushNotifier` adds that sentence when the field is present.
- `notification_queue`'s create rule bounds it like the other keys (`isValidLength(…, 0, 20)`), and
  an older build ignores it.
- **An event awaiting acceptance has no push of its own.** `EventRepositoryImpl` queues none; it
  reaches the co-parent through its chat card's `chat_message` push. So its deadline appears on its
  card and in its reminders, not in that first notification.

### 5.5 A co-parent on an older build

- **What they see.** They see and answer requests as today and never see a deadline. Their app
  never writes delivered or opened, so the proposer's card claims neither (§4.5). The last-chance
  push is dropped by their `PushNotifier`, as any unknown type is. The chat cards fall back to
  plain text.
- **A late answer.** A proposal withdrawn at the end (§4.4) is gone for them too. A change
  request, a day swap or an event stays pending in its record, so they can still answer it late.
  That answer takes effect and History shows it as the answer (§4.4). This is accepted for the
  update window rather than enforced in the rules (§5.2).
- **Why a change request is not closed in its record.** The end could set it `CANCELLED`, but
  `ChangeRequestRepositoryImpl.updateStatus` rewrites the whole document from Room with `set()`, and
  `changeRequestStatusMove` lets the requester set `CANCELLED` from any status. A last-minute
  answer not yet on the proposer's phone would be overwritten: the event moved, the request
  cancelled. It would also push `change_request_cancelled` to the parent who was just told nothing
  more comes.

### 5.6 The family is the clock's, never the one on screen

Every background step takes the family from the clock (`familyId`), because the switcher may show
another family by then (CLAUDE.md items 38 and 41):
- the reminder worker;
- the proposer's deadline and end notifications;
- the end's chat card and proposal withdrawal.

Three calls act on the family on screen today and need a family-explicit form for this:
`CustodyModelRepository.withdrawProposal` and `decideProposal` (both through `currentPair()`), and
`ActivityAnnouncer.announce` (the thread of `users/{me}.partnerId`). Calling them as they are from a
job would withdraw or post in the wrong family.

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
  - "By <date, time>. <name> gets up to <n> reminders, none at night." (<n> is what fits the
    window; the answering phone plans the real times, §4.3.)
  - "No answer by then — you'll be told and can give a last chance of 24 hours. Nothing changes in
    the calendar without an answer."
- **A near cap** (2–48 hours) and **too soon** (under 2 hours) replace the chips with one line (§4.2).

### 6.2 The answering parent's card (artboard 2)

- **The notification.** The request's own push adds "Answer by <time>." (the `answerBy` field,
  §5.4). An event awaiting acceptance has no push of its own, so it shows its deadline on the card.
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
- **Late answers.** A late answer from an older build reads as the answer it is: the answer times
  in the records are naive wall-clock strings, which cannot be compared honestly with a deadline.
  The chat keeps the order (§4.4).

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
- **Ops.** This ships with a rules deploy (the new collection, the push type and field) and a
  functions deploy (`notifyOfChatMessage` skipping the two cards, account deletion). Until the
  functions are deployed, the two new chat cards are pushed like any other message. Add both to the
  owner's deploy list (`docs/ROADMAP.md`, "Yours only").

## 8. Testing

- **JVM**:
  - `RequestClock`: every state of §4.1 at each boundary, the cap, the last chance's window and
    the button's expiry, the stored `outcome`, an orphaned clock, a clock whose proposer is not the
    request's author (ignored), and a late answer.
  - The reminder plan: counts by window, quiet hours moved and dropped, merges, time zones.
  - The deadline options in all three bands of §4.2: compressed and merged chips, the midpoint
    deadline, too soon, rounding; and the cap of each kind, including a recurring series and a
    proposal through `CustodyPatternDiff`.
  - `RequestInbox` and `RequestHistory` placing each state.
  - `OtherFamiliesSignals`: a request in History does not light the dot.
- **Functions**: `notifyOfChatMessage` pushes nothing for the two new card kinds, and still pushes
  every other message.
- **Rules** (`firestore-tests/rules/request-deadlines.test.js`):
  - create, read, update and delete for the proposer, the responder and a stranger;
  - an id whose uid segment is not the caller's, refused;
  - each field written once, by its writer only, the responder's plan as one write;
  - the closure only after the deadline, and `outcome` only `"no_answer"`;
  - `answerBy` on a push bounded, and `request_last_chance` in the allow-list;
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
  - `AnswerByField` (default, compressed chips, the one-line deadline, too soon);
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
2. How a near cap compresses the chain: options capped 24 hours before it, a midpoint deadline
   between 2 and 48 hours, no deadline under 2 hours.
3. The reminder times by window, quiet hours 21:00–08:00 on the answering parent's clock, and the
   answering phone planning them.
4. The button available for 24 hours after the deadline (never later than 1 hour before the cap),
   so the end comes at most 48 hours after the deadline; a last chance shorter than 24 hours when
   the cap is nearer.
5. No reminders during the last chance.
6. "Delivered" as the condition for showing "hasn't opened it yet" and the reminders count.
7. While a last chance is still possible, the answering parent's card waits under "Waiting for
   <proposer>", without buttons.
8. At the end only a custody proposal is withdrawn; a change request, a swap and an event keep
   their records, and the clock stores the outcome.
9. Late answers from an older build count, and show as the answer itself.
10. Reminders as local notifications on the answering phone, the proposer's notifications local
    too, one push type for the last chance, and an `answerBy` field on the request pushes.
11. Two chat cards, "Last chance" and "No answer", pushed to nobody (a functions change); no card
    per reminder or opening.
12. The switcher's dot reads the other families' clocks (one more listener each).
13. The proposer's uid in the clock's id.

## 11. Left to the builder

The file layout, the composable split, how `SyncWorker` reads clocks, the WorkManager details,
the exact copy within the meanings above (all five locales), and thresholds within ±25 % where a
test or a device shows a reason — recorded in the plan.
