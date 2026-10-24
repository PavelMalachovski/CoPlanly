One chat message: a bubble on the sender's side, grouped into runs, with its time and an honest delivery receipt inside.

Source: `presentation/chat/MessagesList.kt` → `MessageItem`, `BubbleMeta`, `DeliveryReceipt`; corners from `theme/Shape.kt` → `chatBubbleShape`.

## Anatomy
- **Mine**: end-aligned, `primary` fill, `on-primary` text. **Theirs**: start-aligned, `surface-container-high` (a step above the day separator's `surface-container`), `on-surface` text.
- At most 300px wide, `space-s` × `space-m` padding, `body-medium` hyphenated text.
- **Corners**: `radius-bubble` (18) on the far side; on the sender's side 6px between bubbles of one run, and the run's last bubble alone takes the 4px tail. A new run starts `space-s` lower.
- **Meta** inside, bottom-end: time in `label-small` and, on my messages, a 13px receipt — schedule (sending), check (sent), done-all (delivered), done-all at full strength (read). On a mine bubble the time and unread receipts sit at 78 % so **read** is visibly brighter.
- A bubble that opens something (an event, the inbox) carries a chevron and is a button.
- A failed send says so **under** the bubble in `error`: "Not sent — tap to retry", a 48px target. Error red is never drawn on the primary fill.

## Consumer provides
`text`, `time` (on the reader's clock), `mine`, `status` (`sending|sent|delivered|read|error`), `startsGroup`, `endsGroup`, `onOpen`, `onRetry`.

## Rules
- Receipts are derived from the conversation's read marks — never print a sync time the app does not track.
- Receipts are never a parent colour; pink and blue are people, not states.
- Activity cards ("Swimming lesson moved to …") are worded on the reading phone, in its language.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
