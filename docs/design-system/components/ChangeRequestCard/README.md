One change request in the Requests inbox: the event, its current and proposed time, the note, its status, and the actions its reader may take.

Source: `presentation/changerequests/ChangeRequestsScreen.kt` → `ChangeRequestCard`.

## Anatomy
A `surface-container` card (`radius-md`, `space-l` padding, `space-s` gaps; `secondary-container` while a chat card has linked to it). Title in `title-medium` with a status `PillChip` in `on-surface-variant`. **Current → Proposed** in two columns: `label-small` captions, the current time `body-medium`, the proposed one `body-medium-emphasized`, a `primary` arrow between. The note in quotes, `body-small`. Then the deadline line, then actions.

## States
- **Waiting for your answer** — Accept (filled) and Decline (outlined), each half the width.
- **Waiting for {co-parent}** — one outlined **Withdraw request**, no confirmation.
- **Queued** beats the status: a row this phone has not delivered shows **Queued** with a clock icon, never "Pending" or "Accepted".
- **Past its deadline** — the "No answer" chip replaces the status, and the answer buttons go.
- **History** — the status alone: Accepted, Declined, Withdrawn.

## Consumer provides
`title`, `current`, `proposed` (formatted on the reader's clock), `status`, `queued`, `note`, `deadline`, `incoming`, `highlighted`, `banner` (an ATTENTION `InlineBanner` for a last chance), `onAccept`/`onDecline`/`onWithdraw`.

## Rules
- The inbox has three sections in this order: *Waiting for your answer*, *Waiting for {name}*, *History* — headed by `GroupLabel`.
- Never print a status the app cannot know: "Sent" only once the write landed.
- Names, never "Mom"/"Dad".

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
