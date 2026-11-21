# EduPage: an event's details from the school's messages

Owner request, 21 November 2026: an imported school event should say what to prepare (skates, warm
clothes, a fee), not only its name and date. Checked against one live Czech school's timeline
(23 events, 67 messages to the class and the school since late August).

## What the school sends

- An EduPage event carries a name, a date and lesson periods, never a description.
- What to bring and when to meet is written in messages to the class's parents (`sprava`) and on
  the class noticeboard (`nastenka`), often in one digest that covers several events.
- A fee is its own item (`payments`): "Byl zveřejněn poplatek za Svítící minigolf 19.11.2026." with
  the plan's name in `data.strReplaces["{payment_plan}"]`, no amount. A withdrawn fee is a different
  item ("… byl odstraněn").
- Nothing links a message to an event. The only link is the text.
- The timeline's `datefrom` filters by **creation time**; the import read 7 days back, so a new
  connection missed every event created earlier, and a message's details would vanish a week after
  it was sent.

## Owner decisions

- Better a missing detail than somebody else's: attach only on an exact match.
- Start with exact matching, no inflection, no AI.

## Approaches

1. **Exact name in the text, whole message unless it is a digest** — picked (owner, 21 November).
2. Fuzzy match (word stems, inflection) — would find "bruslení na Sofijském náměstí", but would also
   attach the wrong one; refused by the first decision.
3. A model summarising the thread — no AI in the client; a server call would be a new legal
   assessment (item 36 of CLAUDE.md). Not now.

## Design

- **Which items:** `sprava` and `nastenka` whose audience (`user`, year prefix ignored) is one of the
  child's `childGroups` or all parents of the school (`Rodic*`); never a message to this parent alone
  (it may be a private exchange with a teacher, and the event is shared with the co-parent). `payments`
  whose `ls` is the "published" string (7814 as recorded); any other `ls` is ignored.
- **When it is about the event:** sent on a day before the event's day; its folded text (lower case,
  no diacritics, whitespace collapsed) contains the event's whole folded name; and, when another
  imported event of this child has the same name, also the event's date (`d.m.`, `d. m.`, with or
  without the year, leading zeros optional). A fee matches on its plan name the same way, and a date
  in the plan name must be the event's.
- **What is attached:** a message that names no date but the event's own goes whole; one that names
  other dates (a digest) gives only the paragraph (blank-line separated, else the line) that holds the
  name. Each message is headed by the date it was sent (`d.M.yyyy`), oldest first; a fee is the
  school's own sentence. Language-neutral: nothing of ours is written into the record. Capped at 2,000
  characters.
- **Where:** `SchoolEvent.description` (null today for EduPage), built in `EdupageTimelineParser`, so
  the planner's fingerprint picks up a new message and rewrites only the description, as for Bakaláři.
- **Read further back:** `TIMELINE_LOOKBACK_DAYS` 120, so a new connection imports events created
  earlier and a detail stays while its event is ahead.
- **Shared like the event:** the description goes to the co-parent with the event. The privacy policy
  and DPIA R16 say that messages to the class may be quoted into an imported event's description.

## Left to the builder

- Exact paragraph splitting rules and the cap's cut point (cut at a paragraph, else a word).
- Tests from the recorded shapes with names replaced.

## Not covered (said in the device check)

- A detail written with the name inflected ("na Sofijském náměstí") is not found.
- A message older than 120 days drops out, and its event loses the detail.
