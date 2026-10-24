A small rounded label: status ("Agreed", "Queued"), a category marker, or a compact action ("Review").

Source: `presentation/common/DesignSystem.kt` → `PillChip`.

## Anatomy
Pill (`radius-pill`), `space-s`×`space-m` padding, optional 6px leading dot, optional `icon-inline` icon, `label-medium` text that ellipsises. Without a `container` it is outlined in `outline`.

## Consumer provides
`label`; optional `container`, `contentColor`, `leadingDot`, `icon`, `onClick`, `selected`.

## Rules
- An interactive chip reserves a 48px touch target **outside** the drawn pill; the pill itself stays ~28px tall.
- A label on a solid parent colour uses `parent-*-chip` with `on-chip` — never white on the full hue (4.35:1 on pink).
- Status is never colour alone: the word carries it, the dot repeats it.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
