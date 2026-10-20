# Agreements and billing — decisions so far (project 2)

Status: notes from the owner's conversation of 17 October 2026, to be turned into a full design
after the virtual tester (`2026-10-17-virtual-tester-design.md`). Everything here was agreed by
the owner unless marked as Claude's call. Source: Zuzana's real messages with her co-parent
(Christmas, an unanswered "Daj mi vedieť?", a handover an hour late, "you were late 4 times"),
and her answer about shared expenses.

## The product thesis

Separated parents pay for an app that helps them agree, keep money straight, and turn emotional
back-and-forth into a formal, factual record.

## 1. A question with a deadline, from a chat message

- Long-press on one's own message → "Waiting for an answer until …" with a deadline.
- The co-parent sees it in Requests and on Home ("Zuzana is waiting for an answer until Friday"),
  with the phase 3a reminders (no nights, sensible frequency), delivered/read marks.
- No answer by the deadline → "No answer" in History and in the export.
- The answer is ordinary text attached to the question. When the question was about days, the
  answer offers "Make it an agreement", which opens a ready proposal in the calendar for the asker
  to confirm in one tap.

## 2. Handover: time, place, check-in

- The schedule gains a handover time and place ("Friday 17:00, at school"). Owner decision
  overriding CLAUDE.md design item 20's "a handover is a day and a person".
- "I'm here" / "Picked up" buttons record the exact time. Location is attached once, only if the
  person pressing chooses to; never tracked.
- "Running late by 10/20/30 min" (Claude's guess, not objected to): an immediate push to the other
  parent, recorded.
- A handover log, both sides, who was on time and by how much; in the export.

## 3. Holidays: rotation rules and history

- Per holiday a rule: alternate by year, always with one parent, or split the day.
- Holidays offered from the parent's country (Christmas Eve/25th/26th separately, New Year,
  Easter), school breaks (autumn, spring, summer — summer split in halves or weeks), special days
  (the child's birthday — default: alternate by year; Mother's Day with the mother, Father's Day
  with the father), and custom recurring days (e.g. a name day).
- A history by year of who had the child. Past years can be entered by hand and become fact only
  once the other parent confirms (Claude's call).

## 4. Expenses: receipts, month summary, child support

- Quick capture: photograph a receipt, the amount fills in (on-device OCR exists).
- Month summary: who paid what, who owes whom, with "QR to pay" (Czech QR Platba / Slovak PAY by
  square in the parent's banking app) and "Settled".
- **Private expense**: visible only to its author, in their archive and their export, not in the
  shared balance until they share it. (Zuzana: "not necessarily for the other to see, but
  extractable.")
- **Child support**: recorded, never moved through the app (owner decision: no money through
  CoPlanly). "5,000 Kč monthly by the 15th"; the recipient confirms "received" in one tap,
  otherwise a reminder; a yearly log.
- School fees from EduPage become expenses with "who paid?" (ties into the planned EduPage import,
  ROADMAP phase 4).

## 5. The family subscription

- One subscription per family, 139 Kč a month, which either parent may pay, or the two may split
  50/50 (two half purchases in Google Play that together unlock the family).
- Trial: 14 days counted from when the **second** parent joins.
- **Answering and reading are always free**: the non-paying parent can always read the calendar,
  chat, and answer requests and deadline questions. Paid: creating deadline questions, the handover
  log, holiday history, child support, the court export.
- When a subscription ends, data and history stay readable and exportable; only the paid actions
  stop. No parent can cut the other off.

## Not now

- A calendar or chat redesign: the owner has no remarks today; polish waits for the testers'
  findings.
- An iPhone and Mac version: after these features ship.
