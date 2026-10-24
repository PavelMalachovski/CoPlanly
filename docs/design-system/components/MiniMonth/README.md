The one small calendar: a month from Monday with the weekend base, the custody wash, event dots and today's disc — shown on the card of a request that waits for an answer.

Source: `presentation/calendar/components/MiniMonth.kt`, layers from `presentation/calendar/DayCellFills.kt`. Web rendition is hand-written and simplified.

## Layers, bottom to top
1. **Base** — `weekend` on Saturday and Sunday, in every row, borrowed days included.
2. **Overlay** — the custody wash `parent-*-wash` (whose day it is), the holiday tint or today.
3. **School vacation** — a 2px `outline` line along the cell's bottom edge. Never a fill, never a hue.
4. **Marks** — event dots in the owner's `parent-*` fill, ringed in the day's base so they hold 3:1; today's number on a `primary` disc; a holiday's number in `holiday-red`.

## Consumer provides
`days`, `startOffset` (Monday = 0), `custody(day) → 'pink'|'blue'|…`, `events {day: [colour]}`, `today`, `holidays`, `vacation`.

## Rules
- The weekend is a base layer, never a competing fill — don't put it ahead of custody.
- A custody band runs to the grid's edge; borrowed days carry it at 60 % strength but refuse everything you would act on.
- Don't draw a second small calendar.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
