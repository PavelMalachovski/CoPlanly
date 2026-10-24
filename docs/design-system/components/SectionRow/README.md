A list row: icon, title, an optional status line, and at most one trailing control.

Source: `DesignSystem.kt` → `SectionRow`.

## Anatomy
Min height 56px, `space-l` padding, `space-m` gaps. Icon `icon-standard` in `primary`; title `body-large` `on-surface`; supporting `body-small` `on-surface-variant` with an optional 6px status dot.

## Consumer provides
`title`; optional `icon`, `supporting`, `supportingDot`, `trailing` (one control: a PillChip, a switch, a chevron), `onClick`, `destructive`.

## Rules
- **At most one** trailing control. Expand actions from the row (Google Calendar's actions) instead of stacking buttons.
- From font scale 1.3 a trailing chip moves under the text (`stackTrailingAtLargeFont`) rather than squeezing the title to a word per line.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
