# Tester feedback, phase 2: who is who, what the co-parent wrote, where the answers are

October 2026. Zuzana, a tester who co-parents with two people in the app, walked through it with
the owner and left eleven notes. Phases 0 and 1 fix the defects among them (a cross-family leak of
child records, the wrong co-parent's name after a family switch, a second family that appeared
late, Google Calendar dropping off, a chat card that opened the wrong screen). This document is
phase 2: three things that work as built and still did not make sense to her. Phase 3 (day swaps)
has its own document.

The mockups the owner chose from are on the canvas "Mini calendar mockups"
(https://claude.ai/artifact/9c9uRUdoTX792ubTgyE7te, private to the owner): row 1 is item 2,
row 2 item 3, row 3 item 1. The copy in them is Russian; the English below is the base string.

## 1. Intent

- **Item 1.** She was adding a calendar friend and could not tell how a friend differs from a
  co-parent. Nothing on the friend screens says what a friend can and cannot see, or that the
  access ends; the redeem field on the parent's own screen even uses the pairing button's words.
- **Item 2.** The owner created an event for her ("Swimming", on her days). It reached her as a
  card under "Events waiting on you" with a title, a date and a time — and not the description he
  had written for her, and nothing that showed where in her weeks it falls. She wants to see his
  words and, ideally, a small calendar.
- **Item 3.** She wanted to see which of her requests the owner had already accepted or declined.
  The inbox does hold them, mixed in with the pending ones, and Home links to the inbox only while
  something waits on *her*, so a parent waiting on the other one has no way in from Home at all.

## 2. Goals, non-goals, anti-goals

Goals:
- A parent adding a friend knows, before sharing a code, exactly what that friend will and will
  not see, and for how long.
- A parent asked to accept an event reads the creator's description as their words, and sees the
  event's day in a month of custody colours.
- From Home, in one tap, a parent sees what waits on whom and how every past request ended.

Non-goals:
- No change to what a friend can technically read (`firestore.rules` stays as it is).
- No new field on events: the "comment" **is** `Event.description` (owner decision).
- No change to the push for a new event: it stays "%1$s added “%2$s”", without the description.
  A push shows on the lock screen and a description can be private (decision in chat, not
  challenged).
- No answering from the history; no search or filters in it.
- No day-swap redesign (phase 3), though the mini calendar is built to be reused there.

Anti-goals (would be a failure even if it works):
- Any screen saying "Mom" or "Dad", or colouring a parent with a raw `CoPlanlyColors.MomPink` /
  `DadBlue` (CLAUDE.md design item 12): every parent label goes through `ParentNames`, every hue
  through `ParentColors`.
- A comparison table that promises something the rules do not enforce, or hides something they
  allow (design item 8: no affordance may promise a feature that doesn't exist).
- A history that disagrees with what the two phones did — it must be derived from the stored
  records, never from a new stored "history" list.

## 3. Constraints

- **Order.** Build after the phase 1 branch that makes names and colours follow the selected
  family (`ParentsSource`, `HomeViewModel`) has merged: item 3 edits the same `HomeViewModel` and
  `HomeScreen`, and every name below is the selected family's co-parent.
- **No new Room column, collection or push type, and one new Firestore field.** Items 2 and 3 are
  derived from data both phones already hold: `change_requests`, `events.acceptance` /
  `acceptedBy` / `acceptedAt`, `custody_models.dayOverridesJson`, and the custody document's
  `lastDecision`. The friend's calendar (§4.4) reads events the rules already admit and adds only
  `parentNames` to a `calendar_friends` grant, which the callable writes as admin. So no schema
  bump, no wire-contract fixture and no rules change; `tools/e2e/coverage.json` gains the friend
  calendar's e2e test.
- **Strings** in all five locales in the same commit, English in sentence case;
  `node tools/check-invariants.js` must pass. Text from a ViewModel is a `UiText`.
- **Shared primitives** (CLAUDE.md design item 1): `SectionGroup` / `SectionRow` / `GroupLabel`
  (with heading semantics) / `EmptyState` / `InlineBanner`; icon sizes from `IconSizes`, spacing
  from `Spacing`, corners from `MaterialTheme.shapes` / `CoPlanlyCorners`. No literal hex.
- **Screenshot baselines** are recorded only by the Regenerate workflow (touch
  `.github/regenerate-request` on the branch). Every new or changed component below gets a
  screenshot test; a sheet's body is split out of the sheet so a node capture sees it.
- No Android SDK in cloud sessions: Kotlin is proved by CI on the pull request.

## 4. Item 1 — "Family friends", and what they see

### 4.1 Naming (Settings → Family)

| Row | Title | Supporting line |
| --- | --- | --- |
| Pairing | **Co-parent** (was "Co-parent pairing") | Your child's other parent: shared calendar, custody schedule, expenses and chat |
| Friends | **Family friends** (was "Friend with calendar access") | A grandparent, nanny or friend who only views the calendar, for as long as you choose |
| Professionals | unchanged | unchanged |

The Friends screen's title follows the row ("Family friends"). The professional row already says
what it reads and on what condition.

### 4.2 The Friends screen (a parent's view)

Top to bottom:
1. One line of intro: "A family friend is a grandparent, nanny or friend who needs to know where
   the children are. They only view the calendar and can't change anything."
2. **The comparison table**, always shown on a parent's view (owner: "it answers her question"):

   | | Co-parent | Family friend |
   | --- | --- | --- |
   | Calendar | views and changes | only views |
   | Custody schedule | yes | no |
   | Expenses | yes | no |
   | Chat | yes | no |
   | Children's records | yes | no |
   | How long | while you are linked | a week, 30 or 90 days |

   Every cell must match the rules as they are: a friend reads `events` only (the last disjunct of
   the `events` read rule), never a private event, never `custody_models`, `expenses`,
   `conversations`/`messages` or `child_info`; the invite offers exactly the three durations
   `FriendInviteSheet` offers today. If the durations change, the table changes with them — the
   builder should derive the "How long" cell from the same list the chips use, not repeat it.
3. The friends list, or the existing empty state, then the "Invite a family friend" row.
4. **The redeem field moves into its own row**: "I have a code from the parents" / "If you were
   invited as a family friend". Tapping it expands the code field in place; its button reads
   "Open the family calendar" (new string), no longer `pairing_link_accounts`. *(My call: expand
   in place rather than a dialog — one fewer surface.)* The row is shown where the field is shown
   today (a parent's view with no friends yet); a person already holding a grant keeps today's
   "My profile" branch untouched.

### 4.3 The invite sheet

Above the duration chips, two lines with a check and a cross icon:
- "**Will see** the family's events in the calendar, except private ones. View only — they can't
  change anything."
- "**Won't see** the custody schedule, expenses, chat or children's records."

Below the code (drawn with `InviteCodeText`): "They enter it in their own CoPlanly: Settings →
Family friends → I have a code from the parents." Actions: **Share code** (filled; the Android
share sheet with a text naming the sender, the code and that path — one string with arguments)
and **Done**. Today the sheet has no share action at all.

### 4.4 The family friend's calendar (owner decision: built in this phase)

**Today a friend has no calendar at all.** The rules let a friend read the family's events (the
last disjunct of the `events` read rule, `isCalendarFriendOf`), but nothing in the app issues that
read: a friend who redeems a code sees only "My profile" (the `myGrants` branch of
`FriendsScreen`). `docs/ROADMAP.md` (MON-18) records it — "a friend's app shows their grant and
their profile, nothing else … Building the friend's view on the same pieces is now a small
change". Without it, §4.2's table and §4.3's "Will see" would promise what does not exist
(design item 8), so the owner chose to build it now.

- **Where it opens.** The friend's own view of "Family friends" lists **one row per live grant**:
  title "Family calendar", supporting "%1$s and %2$s · access until %3$s" (the parents' first
  names, §below, and the expiry), tap → the friend calendar for that grant. A friend of two
  families sees two rows (L-5). Redeeming a code (§4.2's "Open the family calendar") opens the
  calendar of the grant just made, so the button keeps its promise. "My profile" stays below.
- **What it shows.** A read-only agenda built from the professional's pieces — the list-shaped
  four-week agenda with paging (`ProfessionalAgenda.days`, `ProfessionalCalendarScreen`'s layout),
  not the month grid: per day, each shared event's title and time, "Added by %1$s" when the
  parents' names are known, and **"You take part"** with the friend's own colour
  (`CoPlanlyColors.FriendTeal`) on events whose `friendParticipates` is this friend. No custody
  colours and no "whose day": a friend cannot read `custody_models`, and the table in §4.2 says
  "Custody schedule: no". No member chips: a friend cannot read `child_info`, so a child's
  reference cannot be named. Tapping an event shows its details read-only (title, time,
  description, takes part) — no Edit, no Delete, no Request change.
- **Data.** A Firestore listener per open grant, with the query the rules are pinned to serve
  (`firestore-tests/rules/friend-calendar.test.js`, "serves the family-scoped query a friend client
  must run instead"): `whereEqualTo("familyId", grant.familyId)` and
  `whereIn("createdByFirebaseUid", grant.familyParents)`, mapped as the professional's events are
  (`ProfessionalEvents.from`: private and deleted events dropped, recurrences expanded for the
  window). **Nothing is written to Room**: a friend's phone keeps no copy of somebody else's
  family, as for a professional (CLAUDE.md item 29). Offline, Firestore's own cache answers; a
  listener failure shows `ErrorState` with Retry, never an empty agenda posing as "no events".
- **When access ends.** The grant listener the screen already has drops an expired or revoked
  grant from the list. An open calendar whose grant has ended — the rule refuses the read at
  `request.time`, so the listener fails with `PERMISSION_DENIED` — shows "Your access to this
  calendar has ended" (an `EmptyState`), not the last events it saw.
- **The parents' names.** `acceptCalendarFriendInvitation` copies the two parents' first names onto
  the grant as `parentNames` (`{uid: firstName}`), as `acceptProfessionalInvitation` already does
  for a professional; the client mapper reads the field with `get(...) as? Map<*, *>`. A grant
  made before this has no names: its row reads "Family calendar · access until %1$s" and events
  carry no "Added by" — *(my call: no backfill; the feature has only closed-test users)*.
- **Tests.** Unit: the friend calendar ViewModel (loading, events, access ended, failure, retry)
  and the event mapping (a private and a deleted event absent, a recurrence expanded, "You take
  part"). Functions: `parentNames` on a friend grant. Two-phone e2e (`app/src/androidTest/.../e2e/`,
  registered in `tools/e2e/coverage.json` under `calendar_friends` and `events`): a friend reads
  the family's events through the production data source; a private event and the inviter's other
  family's event are absent; after the parent revokes, the read is refused. Screenshot: an agenda
  day with a "You take part" event. UI tour: the friend's family list and calendar. Rules: no
  change (already pinned).

## 5. Item 2 — the creator's words and a mini calendar

### 5.1 The card under "Events waiting on you" (`AwaitingEventCard`)

Top to bottom (mockup "A · Мини-месяц"):
1. Title (`titleMedium`), then "Wed, 21 Oct · 16:00–17:30" — start and end through
   `LocalizedDates` (`shortTime`, the reader's 12/24-hour clock); start only when there is no end.
2. "%1$s added this to your days" (existing `event_acceptance_offered_to_you`).
3. **The creator's words**, when `description` is not blank: the creator's initial on a disc in
   their `ParentColors.text` tone, and a quote bubble headed by their name
   (`ParentNames.labelForUid(createdByFirebaseUid)`) holding the description, up to 6 lines, then
   ellipsis. Blank description: nothing (no "no comment" placeholder).
4. **The mini month** (§5.3) of the event's start month, the event's start day ringed.
5. The existing note "It stays out of both calendars until they answer." and Accept / Decline.

The same card shows to the creator under "Waiting for <name>" (item 3), with Withdraw instead of
Accept/Decline, so they see what they sent.

### 5.2 The form hint

In `AddEditEventScreen`, while the chosen owner is the co-parent's slot and the event would be
created pending (`EventAcceptanceTransition.required(...)` is true for the form's current state —
paired, not private, owner ≠ the creator's slot), the description field's supporting text says
"%1$s will see this when answering" with the co-parent's name. Otherwise no supporting text, as
today.

### 5.3 `MiniMonth` — the one small calendar

A new shared composable in `presentation/common/` (phase 3 will reuse it for day swaps, so it is
built for that from the start, owner decision "one calendar, not two"):

- **Input:** a `YearMonth`; per date, whose custody day it is (slot or none) — computed by the
  caller through `CustodyResolver`, the one custody lookup (CLAUDE.md "UX/UI overhaul" item 4);
  today; a set of **marked dates** with a mark kind (`NEW_EVENT` now: a 2 dp ring in
  `colorScheme.primary`; phase 3 will add its own kinds); and per date whether there is at least
  one visible event (a dot).
- **Drawing:** the month grid's own fill rules, not a new look — the base layer (weekend grey)
  and the custody overlay at `ParentColors.custodyTintAlpha` exactly as `DayCellFills` decides
  them for `MonthView`, borrowed days from the neighbouring months at the borrowed-cell scale,
  today as `MonthView` marks it, the first day of the week as `MonthView` chooses it. Reuse
  `DayCellFills`; do not re-derive the layers. Dots in the colour `MonthView` gives the day's
  first event. No handover diagonal, no holidays, no swap arrows (too small to read).
- **Legend** under the grid: each parent's name with a swatch of their wash, and the mark's
  meaning ("New event"). Names through `ParentNames`.
- **Size:** fills the card's width, cells about 34 dp high; day numbers in `labelMedium`; grows
  with font scale (no fixed heights that clip at 200 %).
- **Not interactive** in this phase. Semantics: the grid is one node whose content description
  reads the month, the marked day, whose day it is and the mark ("October 2026. Wednesday
  21 October, Zuzana's day: new event."); individual cells are not focusable.
- The inbox ViewModel supplies its inputs: custody per date for each awaiting event's month and
  the month's visible events (for dots), computed off the main thread.

## 6. Item 3 — requests on Home, and a history

### 6.1 Home

- **The "Requests" row is always there while the account is paired** (unpaired: no row). It
  replaces today's "Awaiting your answer" row, in the same place (after the contacts group).
  Title "Requests", icon `SwapHoriz`, trailing: the number waiting on this parent when above zero
  (as today), else a chevron. Tap: the inbox.
- **Its supporting line** is a pure function of the inbox's own data, parts joined by " · ":
  1. "%d waiting for your answer" (plural) — when above zero;
  2. "%d waiting for %s" (plural, the co-parent's name) — when this parent waits on them;
  3. the most recent answer the co-parent gave to one of this parent's requests in the last
     7 days: "%1$s %2$s accepted your request" / "…declined your request", with a relative day
     ("yesterday", a weekday, or a date) worded as the app words relative days elsewhere.
  With no part: "No requests yet".
- **The co-parent's feed** ("%1$s changed") also lists their **answers to this parent's
  requests**, in time order with the rest and inside the same `MAX_ITEMS` cap:
  - a change request of mine they accepted / declined: "Accepted your new time for %1$s" /
    "Declined your new time for %1$s";
  - a day swap of mine they decided (one row per swap group): "Accepted your day swap: %1$s" /
    "Declined your day swap: %1$s" (a date, or the first date and a count);
  - an event I created for them that they accepted / declined: "Accepted %1$s" / "Declined %1$s".
  Each row's marker shows the outcome: a check or a cross on a neutral tonal disc
  (`surfaceVariant` / `onSurfaceVariant`), not a parent colour. *(My call: the mockup's green and
  red discs would need new theme roles, regenerated contrast schemes and their tests, and red
  already means "holiday" and "error" in this app; the icon's shape carries the outcome, which
  also holds for a reader who cannot tell red from green.)* Tapping one opens the inbox at History.
- **"All requests and answers"**, a last row of the feed group whenever the group has items:
  opens the inbox scrolled to History.

### 6.2 The inbox (`ChangeRequestsScreen`), in sections

Title "Requests" (was "Change requests" — the screen holds more than event times now; the
route keeps its name). Three sections, each with a `GroupLabel` heading, the first two hidden
when empty:

1. **Waiting for your answer** — what waits on this parent, in today's order: the co-parent's
   custody proposal, events waiting on you (§5.1 cards), day swaps offered to you, incoming
   change requests. Answering works exactly as today.
2. **Waiting for %1$s** (the co-parent's name) — what this parent sent and the co-parent has not
   answered: my pending change requests (with Withdraw, as today), events I created for them
   (§5.1 card, creator's view), my day-swap offers, my custody proposal. *(My call: no new
   Withdraw in this phase. Only change requests can be withdrawn in the UI today.
   `CustodyModelRepository.withdrawProposal` has no caller and tells the co-parent nothing, and
   `EventAcceptanceTransition.cancel` has no caller and would delete the event, so either would be
   a silent change on the other phone — a separate decision, not a layout one.)*
3. **History** — everything answered or withdrawn, newest first by the time of the answer:
   - change requests ACCEPTED / DECLINED / CANCELLED (`respondedAt`; for a CANCELLED one without
     it, `createdAt`);
   - day swaps ACCEPTED / DECLINED, one row per group (`decidedAt`), all dates, past ones too —
     unlike the "waiting" sections, History is not limited to today onward;
   - events created for the other parent and ACCEPTED / DECLINED (`acceptedAt`);
   - the custody proposal's `lastDecision`, when the document holds one (only the latest is
     stored; older ones stay in the chat's activity cards).
   Each row: what it was ("New time for %1$s", "Day swap: %1$s", the event's title, "New custody
   schedule"), what was proposed when that applies ("Thu, 16 Oct, 18:30"), the outcome on the
   status chip the inbox already draws — one neutral colour for every outcome, as today, with the
   check / cross / undo icon in it — (Accepted / Declined / Withdrawn), and who answered when: "Your request ·
   %1$s answered %2$s" / "%1$s's request · you answered %2$s" / "You withdrew it %1$s". Rows are
   not interactive. *(My call: no cap, no paging — a family's history is short; revisit if a
   device shows otherwise.)*
- When all three are empty: the existing `EmptyState` "No change requests yet", reworded "No
  requests yet".
- **Route:** `change_requests` gains an optional `section` argument; `history` scrolls History's
  heading into view once the data has loaded (the same gating as today's highlight). The highlight
  by event id keeps working.
- A pure `RequestHistory` builder (domain or presentation, not in the composable) merges the four
  sources into rows, so it is tested on the JVM.

### 6.3 Two families: the family on screen only

The tester co-parents in two families, so every surface in this phase answers for **the family on
screen** — the co-parent `CoParentOnScreen` resolves (phase 1) — and nothing else *(my call)*:

- the inbox's three sections and History, the Home "Requests" row's line and count, and the
  feed's answer items list a change request only when its other party (`requestedBy` /
  `requestedTo`) is that co-parent; an event only when its `familyId` is that family's (an event
  with no `familyId` only while the account has one co-parent); day swaps and the custody proposal
  already come from that family's document (`CustodyModelRepository.currentPair()`);
- today's inbox and Home count mix both families' requests and events; this narrows them, which is
  the switcher's model: another family's waiting items are what its **dot** says (CLAUDE.md design
  item 13, `OtherFamiliesSignals`), and switching shows them;
- so "Waiting for %1$s" names one person, every History row's other party is that person, and the
  mini month colours an event with its own family's custody days.

A request or event from a family that has ended (an ex-partner) shows in no family's inbox — it
is not answerable any more, and the export keeps the record.

## 7. Verification

- **JVM unit tests:** the Home supporting line (every combination, plural forms, the 7-day
  window); the history builder (each source, ordering, group collapsing, withdrawn vs answered,
  both directions); the feed's answer items in `HomeViewModelTest`; the `MiniMonth` grid layout
  (first day of week, borrowed days, February in a leap year) as a pure function; the form hint's
  condition.
- **Screenshot tests** (new, baselines via Regenerate): `MiniMonth` in the standard variant matrix;
  the awaiting-event card with and without a description; a History group with each outcome; the
  Friends comparison table; the invite sheet body. German at 2.0× is where the table and the
  history rows will wrap first — nothing may clip (design item 15's spirit; no money here).
- **UI tour:** a shot of the inbox with History, the Friends screen and the invite sheet
  (`UiTourTest`; `UiTourSeed` gains an answered request and an answered swap).
- **Device checklist:** a new box under the change-request section for the Home row's line in
  each state, the History after an accept, a decline and a withdraw on the other phone, and the
  awaiting card's description and mini month; one under the friends section for the table and
  the share action.

## 8. Decisions

Made by the owner in the conversation:
- The comment is the event's existing "Description" field.
- The awaiting card gets a mini month (option A over a week strip or a day timeline).
- Home as in the mockup "Главная · строка «Запросы»": the always-present Requests row, answers in
  the co-parent's feed, "All requests and answers".
- The comparison table on the Friends screen answers the tester's question.
- A family friend gets a read-only calendar in this phase (§4.4), so the table and the invite tell
  the truth.

My calls (check while reading):
- The push for a new event stays without the description.
- The inbox is sections, not tabs; History lists every kind of request, uncapped.
- "Friend with calendar access" becomes "Family friends", "Co-parent pairing" becomes "Co-parent",
  "Change requests" becomes "Requests".
- The form hint under Description when the event goes to the co-parent.
- The redeem field expands in place behind "I have a code from the parents".
- "All requests and answers" shows only when the feed has items (the Requests row is always there
  anyway).
- The mini month has event dots (as in the chosen mockup) and no handover diagonal, holidays or
  swap arrows.
- The friend's calendar is the professional's list agenda, not a month grid; no custody colours,
  no member chips, read-only details; older grants without `parentNames` are not backfilled.
- With two families, every surface here shows the family on screen only (§6.3).
- No new Withdraw actions (§6.2): only change requests can be withdrawn, as today.
- Outcome markers and chips stay neutral with a check / cross / undo icon, not green and red
  (§6.1, §6.2).

Left to the builder: the composable split and file placement, which ViewModel computes the mini
month's inputs, the exact `MAX_ITEMS` interplay, detekt budgets, and wording polish in the four
translations.
