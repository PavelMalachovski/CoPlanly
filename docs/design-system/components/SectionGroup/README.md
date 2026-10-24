One tonal container per run of rows — the app's answer to "double surfaces": rows sit in a single `surface-container` block with hairline dividers, never a Card per row.

Source: `DesignSystem.kt` → `SectionGroup` + `SectionRow`.

## Consumer provides
`SectionRow` children. In Compose the dividers are the scope's `Divider()` that the caller places; this web rendition inserts them between children.

## Rules
- Fill `surface-container`, corner `radius-md`, dividers `outline-variant` at 40 %.
- Settings is exactly four groups, in this order: **Family, Sync, App, Account**.
- Destructive rows (sign out, unpair) are a red SectionRow at the bottom of the screen, which then confirms — not a filled error button.
- Don't nest a Card inside a group, and don't wrap each row in `Card { ListItem }`.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
