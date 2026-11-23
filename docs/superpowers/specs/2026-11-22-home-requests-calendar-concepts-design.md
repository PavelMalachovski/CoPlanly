# Home "Requests" and a roomier calendar — design concepts

Date: 22 November 2026. Status: concepts for review on a Design canvas; no code yet.

## Why

- **Chat is hard to read.** About thirty kinds of activity card (events, expenses, swaps, custody
  and split proposals, deadlines, settle-up, payments, stickers) are posted into the parents'
  thread, so the co-parent's own messages are hard to find. And every card looks the same.
- **The calendar grid is too small.** On a phone the month grid gets about 46 % of the height
  between the status bar and the bottom bar; seven layers sit above it and the Today card below
  it, so a cell's pill shows three letters ("Par…").

## Goals

1. The chat thread shows the two people's messages only.
2. What used to arrive as cards lives on Home, in one block called **Requests** (replacing
   "Updates" and "Open requests"), coloured by **status**.
3. The calendar gives the grid the height; what is not the grid moves to Home or behind a tap.
4. Three alternatives each for the calendar and Home, so the owner can compare.

## Non-goals and anti-goals

- No implementation, string or schema change in this step.
- **Cards stay in the data.** The court-record export reads `messages`, cards included
  (a custody proposal's plan citation, item 32), and messages are immutable. The chat screen only
  stops drawing them. Not drawing ≠ not writing.
- **No colour per request type.** Pink/blue/any chosen hue are people, teal is a calendar friend,
  grey is the weekend. Status colours are the theme's roles; the type is told by its icon.
- No score between parents (item 47 / second audit).

## Decided with the owner

- Home approach **A**: one Requests block — waiting for you first (actionable), then waiting for
  the co-parent, then recent; its count in the heading; "See all" opens the inbox. "Open
  requests" goes.
- Status colours: waiting for you = accent (primary/attention), waiting for the co-parent =
  neutral, accepted = success green, declined / no answer / withdrawn = muted.
- "Your family calendar / Together, for Emma and Leo" moves from the calendar to Home, under the
  wordmark.
- Three alternatives for each screen (option "б", extended to three).

## Approaches

### Calendar

- **K1 "Tidy" (recommended).** Grid stays in its white card. The children's avatars in the brand
  row become the member filter ("Show for" strip goes; keeps the owner's rule that the filter is
  visible over the grid). One bar: "November ▾" (jump to date), ‹ ›, M/W/D segment, ⇄, +. Legend
  behind "?". Today card and request banners move to Home. Grid ≈ 70 %, 2–3 readable pills.
- **K2 "Full bleed".** No card; tiles edge to edge down to the bottom bar. One top bar: "November
  ▾", avatar filter, "+", overflow (Week/Day, Filters, Swap days, Legend). Grid ≈ 80 %, 3–4 pills.
  Costs a tap for M/W/D and ⇄; departs from November's "+ in the month bar".
- **K3 "Month + agenda".** Small month (~40 %) with no text in cells — custody wash, number, event
  dots; below it a scrolling agenda of the chosen day/week with full titles, time, child and whose
  day. Replaces the Today card. Reverses November's pills-in-cells decision.

### Home

- **G1 "Action cards" (recommended).** Waiting-for-you items as cards with Decline/Accept inline;
  below, compact rows "Waiting for Bob" and "Recent". Tall when 3+ wait.
- **G2 "Status feed".** Every request is an even row with a status stripe on the left and a status
  label; answering is a tap into the inbox.
- **G3 "Today first".** A large Today card (whose day, next event, handover) leads; Requests is one
  summary line with coloured counts ("2 for you · 1 Bob · 3 new") that expands in place; week and
  money below.

All three Home variants carry the header and replace Updates + Open requests. One "after" chat
screen (messages only) accompanies them.

## Chosen after review on the canvas (22 November 2026)

The owner iterated K1 and G1 on the canvas; these are the directions, with the changes below.

### Calendar — K1, revised

- **Top row:** "November 2026 ▾" in place of the wordmark (21 px, the year lighter and in
  `on-surface-variant`; tap = jump to date), "?" (what the colours mean) beside it, then the
  children's avatars as the member filter and the gear. **This reverses "the month title omits the
  current year"** (design item 5) — the full year, never an abbreviated "’26", which reads as a day
  next to a grid holding a 26. From font scale 1.3 the title steps down, then the avatars and gear
  take a line under it (the existing month-title rule).
- **Card bar:** M | W | D segment on the left (letters, each 40 px, spoken as Month/Week/Day),
  then a text **Today** button (never an icon — the icon was not understood), ⇄ swap days and
  "+". No ‹ › arrows: months page by swipe.
- No "Your family calendar" header, no "Show for" strip, no legend row, no Today card, no request
  banners. Grid ≈ 77 % of the height between status and bottom bar.
- Pills: up to two, two lines each, hyphenated at syllables (`hyphens: auto`), never broken
  mid-letter. A 7-column phone cell is ~47 px wide, so long titles still end in an ellipsis.

### Home — G1, revised

1. Wordmark row, then "Your family calendar / Together, for Emma and Leo".
2. **Requests** heading with "N for you" and **See all · N new** (the count of informational
   items not yet seen).
3. Action cards for what waits on this parent, Decline/Accept inline.
4. **Waiting for {co-parent}** rows with Withdraw.
5. **Money**: one card, "Spent in {month}" and the balance sentence; opens Expenses. Never cut
   off; the two figures stack from font scale 1.3.

**Every "Waiting for you" card carries Details** (owner request): a sheet over Home that shows
the request on a mini month of the reader's own calendar — a swap's days marked in the colour of
whom they go to (`SwapMark`), a schedule proposal as the next weeks drawn in the proposed pattern
with each changed day ringed — and offers Decline / Accept again. It is for answering later.

**The pop-ups stay** (owner decision): one per visit, as `HomeAskQueue` orders them today. **The
schedule proposal's pop-up becomes graphical like the day swap's**: a mini month with a
Proposed | Now switch, changed days ringed in the colour of whom they go to, the proposer's note
and the deadline. No count of nights or days per parent (no score between the parents).

There is **no "Recent" section**: it duplicated the inbox that "See all" opens. Informational items
(added, accepted, declined) live only there; the "N new" count keeps them from going unseen.

### Chat

- The thread shows messages, attachments and **stickers** (a sticker stays a chat item, owner
  decision). Every other activity card is no longer drawn in the thread; it stays in `messages`
  for the export.
- **Settle up moves out of the chat** (owner decision): the payer's payment code (QR Platba / PAY by
  square / EPC), the IBAN, "I paid" and the payee's "Money arrived" go to Expenses' summary header and
  the Home Money card. The `SETTLE_UP_*` cards keep being written for the record.

## What is left to the canvas

Exact spacing, copy and the fixture family (Alice, Bob, Emma, Leo, Max — the UI tour's) follow
the current design system: lavender ground, white cards with hairline, indigo primary, Onest,
the parent palette pink/blue.
