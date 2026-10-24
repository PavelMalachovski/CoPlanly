A day-swap offer in the Requests inbox: which days, who offers them to whom, the note, where it stands, and the months the days fall in.

Source: `ChangeRequestsScreen.kt` → `DaySwapCard`, with `MiniMonth` (`SwapMonths`).

## Anatomy
A default Material card (`surface-container-highest`, `radius-md`). Title: the day, a range ("Fri 31 Oct – Sun 2 Nov") or a list of non-consecutive days, in `title-medium`. A sentence that counts the days and names both people ("Sam is offering you 3 days"), the note, the status in `label-medium`, then a `MiniMonth` with each swapped day in the wash of whom it would go to.

## States
- **Offered to you** — Accept (filled) and Decline (text); a multi-day offer can be answered day by day.
- **Offered by you** — **Withdraw request** only. Never answer buttons on your own offer: the server refuses them.
- **Past its deadline** — "No answer" chip beside the title; no answers shown.
- An agreed day stands until another agreement replaces it; a declined or withdrawn change leaves the day where it was agreed.

## Consumer provides
`title`, `sentence`, `note`, `status`, `incoming`, `noAnswer`, `month` (a `MiniMonth`), `banner`, `onAccept`/`onDecline`/`onWithdraw`.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
