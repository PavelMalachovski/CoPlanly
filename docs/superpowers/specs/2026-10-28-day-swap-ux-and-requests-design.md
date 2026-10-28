# Day swaps understood the first time, and every request in one place

Status: PR 1 (the swap flow, boards SW-1 to SW-6, and Home's "you") implemented in
PavelMalachovski/CoPlanly#155; PR 2 (Requests, SW-7) to follow. Boards on the "Обмен днями" page of
the October 2026 audit canvas (https://claude.ai/artifact/SCCWCHGRERphDjRDvfoAqa). Owner decisions
of 28 October 2026. One deviation from SW-2: a month cell's mark names the co-parent by initial
("→ P"), because a whole name did not fit the cell.

## Intent

A day swap ("шахматы", in the owner's words) is the most frequent thing two separated parents ask
each other, and today neither of them can tell, from the screen, which way a day goes. The owner
found three faults while using it:

- the direction of each day is not visible while choosing, before sending, or in the notification;
- after paging to the next month inside one selection, tapping a day opens Day view instead of
  selecting it;
- the co-parent's notification ("Your co-parent proposes swapping 3 days") says nothing a parent
  could act on without opening the app.

The goal is that a parent who has never seen the feature proposes a swap, and the other parent
answers it, without a wrong turn. The second part, a separate pull request, gathers every request
that needs both parents' word on Home's **Requests** row and in the inbox.

## Goals

1. The way to start a swap is visible on the month grid.
2. While choosing, every chosen day shows whom it would go to, in that parent's colour and in
   words; the choice survives paging between months, and a tap only selects.
3. Before sending, the parent sees the days split by direction and a small month of the result,
   and the button names the co-parent.
4. After sending, the parent is told it went, the days stay as they are, and the grid marks the
   days that wait, the same way on both phones.
5. The co-parent's push names the person, the direction and the dates.
6. The co-parent answers with one main pair of buttons; answering day by day is a separate mode.
7. (PR 2) Home's Requests row and the inbox include every proposal that waits on this parent:
   the child support proposal, a professional's access waiting for this parent's consent, and
   parenting plan answers of the co-parent this parent has not agreed to or answered.

## Non-goals

- No change to how a swap is stored (`DayOverride`, groups, `earlier`), to the rules for writing
  it, or to the deadline clock (item 42).
- No answer from inside the notification: a parent decides about the children looking at the
  calendar (board SW-5).
- No change to Day view's "Swap this day" beyond its strip's wording (SW-4).
- The custody pattern proposal flow is not redesigned here.

## Anti-goals (a failure even if it works)

- A colour alone carrying direction: every mark also says it in words ("→ Лане" / "→ вам"), and
  the hatch differs in lightness, not only hue (design item 6, AA on the wash).
- A swap's preview leaking into a neighbouring month's borrowed cells (design item 10: a borrowed
  cell takes no proposal preview and no swap marks).
- A push that a co-parent on an older build cannot word: new fields only add to the sentence; the
  old fields stay and an older build still words the old sentence.
- A selection lost by paging, or a tap during selection that opens Day view.

## Constraints

- Design refresh items 1, 5, 6, 8, 10, 12, 15 and 22 of CLAUDE.md; the calendar header keeps its
  fixed four controls, so the new swap button goes in the legend row over the grid.
- Item 15 ("a push carries a type, never a sentence"): new data rides as **fields** on the existing
  types `day_swap_offered` / `day_swap_group_offered`, so the four-place rule for types does not
  apply, but `firestore.rules`' `notification_queue` create rule must bound each new field.
- Strings in all five locales; Russian and Ukrainian say "проживание"/"проживання".
- One push per offer already holds: a mixed selection is one group (`offerDaySwapForDates`), and the
  repository announces once.

## Approaches considered

**Notification.** (A) carry the dates in the push as two fields and word the sentence on the
receiving phone; (B) carry only the direction counts. The owner chose **A**: the push is the
one place a parent decides whether to open the app, and dates are what decide it. Cost: a rules
change and its tests; an older build ignores the fields and words the old sentence.

**Selection state.** (A) keep the selection in `CalendarScreen` and fix the tap routing;
(B) move it into `CalendarViewModel`. Chosen: **A**, the state already lives there
(`swapSelection`, `rememberSaveable` to add) and only the gesture routing is wrong. Moving it buys
nothing the screen does not already have.

**Pull requests.** One for the swap flow (goals 1–6), one for Requests (goal 7), owner decision.

## The design, as decided

### PR 1 — the swap flow (boards SW-1 to SW-6)

**1. Start (SW-1).** An icon-only outlined button "⇄" at the end of the legend row over the month
grid (`CustodyLegend`'s row), 44 dp, content description and long-press tooltip "Swap days"
(new string, five locales). Shown only while a co-parent is linked, as the long press is now
(`CalendarScreen.kt:277-291`). It enters selection mode with nothing selected. The long press on
a day stays and enters selection mode with that day selected.

**2. Selection mode (SW-2).**
- The calendar header is replaced, while selecting, by a bar "Choose days to swap" with a close
  button (✕, cancels). Back cancels too, as now.
- A tap on an in-month day toggles it (`SwapSelection.toggled`); dragging after a long press still
  extends a run. **A tap on a borrowed day pages to that day's month and selects it there** — this
  is the fix for the reported bug (`MonthView.kt`'s `combinedClickable` sends a borrowed day to
  `onDayClick` today). Day view never opens while selecting.
- Paging months keeps the selection (it lives in `CalendarScreen`; make it `rememberSaveable` with a
  `LocalDate` set saver so rotation keeps it too). The tab-tap reset (`CalendarTabTaps`) still
  clears it.
- Each selected in-month day draws a **swap preview** over its `DayCellFills` layers: a diagonal
  hatch in the colour of the parent who would get the day, a 2 dp ring in that parent's deep tone,
  and a small filled pill at the bottom, "→ Lana" / "→ you" (receiver's first name or "you", design
  item 22's `labelOrYou`). The day number keeps AA on the hatch (measure it in
  `ParentColorsTest`'s style).
- The bottom bar replaces "N days / Tap more days…" with one line per direction that has days:
  "**Lana** — 2 days: Fri 6, Sat 7" and "**You** — 1 day: Sat 14", each with the receiver's colour
  dot; buttons "Cancel" and "Next". Next opens the sheet. Days with no custody recorded are left
  out with the existing note.
- The month grid's maximum of 14 days stays (`SwapSelection`).

**3. Review sheet (SW-3).** `DaySwapSheet` becomes:
- Title "Swap with {name}".
- Group "You give {name}" with day chips, and group "You take" with day chips, chips in the
  receiver's wash with the receiver's deep ring. A group with no days is not drawn.
- A `MiniMonth` per month the days fall in, each day marked as on the co-parent's card today
  (`swapMiniMonths`, `MiniMonthMark.SWAP_DAY`: wash of whom it would go to), and the caption
  "This is how it will look if {name} accepts. Nothing changes until they answer."
- Note field "Message to {name} (optional)", the `AnswerByField`, and buttons "Change days"
  (text, back to selection, keeping it) and "Send to {name}" (filled).
- Day view's single-day entry ("Swap this day") opens the same sheet for one day.

**4. After sending (SW-4).**
- A snackbar "Sent to {name}. The days stay as they are until they answer." with action
  "Withdraw" (calls the existing withdraw, item 40). Failure snackbars stay as they are.
- Pending days on the grid replace the grey "⇄" arrows (`MonthView.kt:755-773`) with the pending
  form of the swap preview: a lighter hatch in the receiver's colour, a dashed 2 dp outline in
  the receiver's deep tone, and the outlined pill "→ Lana" / "→ you". Drawn on both phones, from
  the same `DayOverride` state, in-month cells only.
- The legend gains "Waiting for an answer" (a swatch of the pending form) while any day in view
  waits.
- Day view's `DaySwapStrip` wording names the direction: "You asked {name} for this day. Waiting
  for an answer until {deadline}." / "You offered this day to {name}. …", with "Withdraw".

**5. Push (SW-5).**
- Two new fields on `day_swap_offered` and `day_swap_group_offered`, written by the proposer's
  phone when it announces the offer: `datesToYou` and `datesToSender`, each a comma-separated list
  of ISO dates, from the reader's (the addressee's) point of view. Up to 14 dates × 11 characters;
  `firestore.rules` bounds each to 0–160 characters, beside the existing keys, and
  `firestore-tests` gets cases for both (accepted at the bound, refused over it).
- `PushNotifier` words, when either field is present:
  - only `datesToYou`: title "{name} offers you {n} day(s)", body "{dates} will be with you.";
  - only `datesToSender`: title "{name} asks for {n} of your days", body "{dates} would go to
    {name}.";
  - both: title "{name} proposes a day swap", body "You: {dates}. {name}: {dates}.";
  - then the existing "Answer by {time}." sentence.
  Dates are formatted on the receiving phone in its locale ("Fri 6 Nov"), consecutive days of one
  month may be joined ("Fri 6 and Sat 7 November" is fine; no range syntax is required).
  Without the fields (an older sender) the old wording stays.
- `PushPayload` documents both fields; the e2e coverage map's `day_swap_*` entries read the fields
  back from `notification_queue` (item 15's "don't cover a push by a count alone").

**6. Answering (SW-6).** Home's swap pop-up (`AwaitingDialogs`) and the inbox's `DaySwapCard`:
- Heading "{name} proposes a day swap" (or "offers you N days" / "asks for N of your days", as the
  push); groups "You" and "{name}" with day chips as in the sheet; the mini months; the note in a
  quote block attributed to the proposer; the deadline line.
- Main buttons through `AnswerButtons`: "Decline" and "Accept swap" ("Accept" when one day).
- A text button "Answer day by day" (shown at two days or more) switches the card into the per-day
  mode: one row per day ("Fri 6 Nov → you") with a Yes/No segmented control, "Back" and "Send
  answer". The per-day chips are no longer drawn beside the main buttons.
- Deadline rules of item 42 (`hidesAnswers`, decline-only) apply to both modes unchanged.
- **Day by day is a partial answer** (owner decision, 28 October 2026). "Send answer" is enabled
  once at least one day is marked; the days left unmarked keep waiting. After a partial answer the
  card, its groups, the mini months and the main buttons show and decide only the days still
  waiting (`decideSwapDays` already writes one run per answer).

**7. Home says "you" to the reader** (owner request, 28 October 2026). The handover hero's
headline and the children-today lines name the reader as "you" when the slot is theirs
(`ParentNames.isReader`), as design item 22 already does for the hero's current line and the week:
new strings `home_handover_hero_today_you`, `home_handover_hero_tomorrow_you`, plurals
`home_handover_hero_in_days_you` and `home_child_with_parent_today_you`, in five locales
(RU "Передача сегодня, дальше — у вас", "Сегодня %1$s с вами").

**Builder decisions settled with the owner.**
- Swap pushes also carry `actorName` (already an allowed key) so the receiving phone names the
  proposer without a profile read.
- `offerDaySwapForDates` returns the offer's group id, so the snackbar's Withdraw withdraws exactly
  what was sent.
- The selection bar carries month arrows, so paging while selecting needs no swipe.

### PR 2 — Requests gather everything (board SW-7)

`Waiting` (domain/changerequests/RequestInbox.kt) gains three kinds, each read for the family on
screen through `RequestsSource` (item 40):

- **Child support proposal** waiting on this parent: `SupportAgreement` with a `proposal` whose
  `proposedBy` is the co-parent (`support_agreements/{familyId}`, item 49). Row: "Child support" —
  "{name} proposes {amount} a month, on the {day}, from {month}". Opens Family → Money → Child
  support.
- **Professional access waiting for this parent's consent**: a `ProfessionalGrant` in
  `WAITING_FOR_YOU` for the family on screen (item 29). Row: "Professional access" — "{role}
  {name} — your consent is needed". Opens Professionals.
- **Parenting plan**: questions where the co-parent has an answer and this parent has neither an
  answer nor an agreement tick (`ParentingPlanComparison` status `ONLY_THEIRS`), and questions both
  answered whose agreement lapsed because the co-parent edited their answer (status `OPEN` with a
  stale tick). One row for all of them: "Parenting plan" — "{name} answered {n} questions — compare
  and agree". Opens the plan.

**The sender's side** (owner decision). The sender's own support proposal and a professional
invitation still waiting for the co-parent's consent appear under *Waiting for {co-parent}* with
**Withdraw**, as the split does. History is unchanged.

**The family switcher** (design item 13) lights its dot for the three new kinds waiting in another
family: new `FamilySignal` kinds and per-family listeners in `OtherFamiliesSignals`, every read by
document id (`support_agreements/{id}`, `parenting_plans/{id}`, `professional_grants` keyed by
family), never a query across families.

All three count in `Waiting.count` (Home's row) and are listed in the inbox's *Waiting for your
answer* section as compact rows that open their own screens; the calendar banner's
`countWithoutProposal` does **not** include them (they are not about the calendar), as with the
split. Home's one-ask-per-visit queue (`HomeAskQueue`) does not pop up for them; they wait on the
row. No new push types.

## Left to the builder

- Exact Compose structure of the preview layer (likely a new `DayCellFill` field or a separate
  overlay drawn after the custody layers in `MonthView`), keeping `DayCellFillsTest` style tests.
- Date-list formatting helper for chips, bar, push and cards (one function, locale-aware, tested).
- Whether the Requests data for support/professional/plan joins `RequestsSource` directly or
  through small sources per kind; listeners must be scoped to the family on screen and shared, not
  per composable.
- Tests: unit tests for selection routing (borrowed tap pages and selects), the push wording
  matrix (three directions × with/without deadline × old sender), `Waiting.count`, and screenshot
  tests for the selected and pending cell, the bar, the sheet and the card (run Regenerate).
- CLAUDE.md design item 22 (or a new item 23) records the swap anatomy and the Requests rule;
  DEVICE-CHECKLIST gains a two-phone check of the push wording.
